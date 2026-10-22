package com.dairyfarm.animal.csv;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dairyfarm.finance.FinancePoster;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import com.dairyfarm.animal.Animal;
import com.dairyfarm.animal.AnimalEnums.Acquisition;
import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.animal.AnimalEnums.Status;
import com.dairyfarm.animal.AnimalImporter;
import com.dairyfarm.animal.AnimalRepository;
import com.dairyfarm.animal.AnimalRequest;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.common.Sex;
import com.dairyfarm.common.Species;
import com.dairyfarm.farm.FarmService;
import com.dairyfarm.group.AnimalGroup;
import com.dairyfarm.group.AnimalGroupRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/** Herd CSV: template, export and import. Column names match the app's template. */
@Service
public class AnimalCsvService {

    private FinancePoster finance;

    /** Keeps automatic finance entries in step with these records. */
    @Autowired
    void setFinance(FinancePoster finance) {
        this.finance = finance;
    }

    public static final List<String> COLUMNS = List.of(
            "tag_number", "animal_name", "dob", "sex", "species", "status", "national_id", "dam_tag", "sire_tag",
            "breed", "color", "birth_weight", "category", "acquisition_type", "acquisition_date", "purchase_price",
            "notes", "group", "exit_date");
    static final List<String> REQUIRED = List.of("tag_number", "sex", "species");
    static final int MAX_ROWS = 2000;
    static final long MAX_BYTES = 2L * 1024 * 1024;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"));

    private final AnimalRepository animals;
    private final AnimalGroupRepository groups;
    private final AnimalImporter importer;
    private final Validator validator;

    public AnimalCsvService(AnimalRepository animals, AnimalGroupRepository groups, AnimalImporter importer,
                            Validator validator) {
        this.animals = animals;
        this.groups = groups;
        this.importer = importer;
        this.validator = validator;
    }

    public record ImportResult(boolean dryRun, int rows, int ready, int created, List<String> duplicates,
                               List<String> errors) {
    }

    // ---------- template and export ----------

    public String template() {
        return write(List.of(List.of("VD-021", "Lakshmi", "2021-08-14", "female", "cow", "active", "", "D-12", "",
                "HF", "Black & white", "", "milking", "purchased", "2023-02-10", "65000", "", "", "")));
    }

    @Transactional(readOnly = true)
    public String export(Long farmId) {
        List<Animal> all = animals.findByFarmIdOrderByTagAsc(farmId);
        Map<Long, Animal> byId = all.stream().collect(Collectors.toMap(Animal::getId, Function.identity()));
        Map<Long, String> groupNames = groups.findByFarmIdOrderByNameAsc(farmId).stream()
                .collect(Collectors.toMap(AnimalGroup::getId, AnimalGroup::getName));
        List<List<String>> rows = new ArrayList<>();
        for (Animal a : all) {
            rows.add(List.of(
                    a.getTag(), str(a.getName()), str(a.getDateOfBirth()), lower(a.getSex()), lower(a.getSpecies()),
                    lower(a.getStatus()), str(a.getNationalId()),
                    parentTag(a.getDamId(), a.getDamTag(), byId), parentTag(a.getSireId(), a.getSireTag(), byId),
                    str(a.getBreed()), str(a.getColour()), str(a.getBirthWeightKg()), lower(a.getCategory()),
                    lower(a.getAcquisitionType()), str(a.getAcquisitionDate()), str(a.getPurchasePrice()),
                    str(a.getNotes()), str(groupNames.get(a.getGroupId())), str(a.getExitDate())));
        }
        return write(rows);
    }

    private static String write(List<List<String>> rows) {
        var out = new StringWriter();
        try (var printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(COLUMNS.toArray(String[]::new)).get())) {
            for (List<String> row : rows) {
                printer.printRecord(row.stream().map(AnimalCsvService::safeCell).toList());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toString();
    }

    /** Stops spreadsheet apps from running a cell as a formula. */
    private static String safeCell(String v) {
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            return "'" + v;
        }
        return v;
    }

    // ---------- import ----------

    /**
     * Adds every valid row. Tags already on the farm (or repeated in the file) are skipped; rows with
     * problems are listed and skipped. With dryRun the same work is done and then rolled back, so the
     * preview matches what a real import would do.
     */
    @Transactional
    public ImportResult importCsv(Long farmId, MultipartFile file, boolean dryRun) {
        List<CSVRecord> records = read(file);
        Set<String> existing = new HashSet<>(animals.findTagsLowerCase(farmId));
        Map<String, AnimalGroup> groupsByName = new HashMap<>();
        groups.findByFarmIdOrderByNameAsc(farmId).forEach(g -> groupsByName.put(g.getName().toLowerCase(Locale.ROOT), g));

        List<String> duplicates = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int created = 0;
        for (CSVRecord r : records) {
            long line = r.getRecordNumber() + 1;
            if (isBlankRow(r)) {
                continue;
            }
            String tag = get(r, "tag_number");
            if (!tag.isEmpty() && existing.contains(tag.toLowerCase(Locale.ROOT))) {
                duplicates.add(tag);
                continue;
            }
            try {
                Row row = parse(r, groupsByName);
                validate(row.request());
                importer.add(farmId, row.request(), row.status(), row.exitDate());
                existing.add(tag.toLowerCase(Locale.ROOT));
                created++;
            } catch (ApiException e) {
                errors.add("Row " + line + ": " + e.getMessage());
            }
        }
        importer.linkParents(farmId);
        if (dryRun) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        finance.sync(farmId);
        return new ImportResult(dryRun, (int) records.stream().filter(r -> !isBlankRow(r)).count(), created,
                dryRun ? 0 : created, duplicates, errors);
    }

    private List<CSVRecord> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a CSV file to import.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("The file is too big. Import up to " + MAX_ROWS + " animals (2 MB) at a time.");
        }
        var format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true)
                .setTrim(true).setIgnoreEmptyLines(true).setAllowMissingColumnNames(true).get();
        try (Reader reader = new InputStreamReader(new BomStripper(file.getInputStream()), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, format)) {
            Set<String> headers = parser.getHeaderMap().keySet().stream()
                    .map(h -> h.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            List<String> missing = REQUIRED.stream().filter(c -> !headers.contains(c)).toList();
            if (!missing.isEmpty()) {
                throw ApiException.badRequest("Missing column" + (missing.size() > 1 ? "s" : "") + ": "
                        + String.join(", ", missing) + ". Start from the template.");
            }
            List<CSVRecord> records = parser.getRecords();
            if (records.size() > MAX_ROWS) {
                throw ApiException.badRequest("Import up to " + MAX_ROWS + " animals at a time.");
            }
            return records;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw ApiException.badRequest("Couldn't read that file as CSV. Save it as CSV (UTF-8) and try again.");
        }
    }

    private record Row(AnimalRequest request, Status status, LocalDate exitDate) {
    }

    private Row parse(CSVRecord r, Map<String, AnimalGroup> groupsByName) {
        List<String> missing = REQUIRED.stream().filter(c -> get(r, c).isEmpty()).toList();
        if (!missing.isEmpty()) {
            throw ApiException.badRequest(String.join(", ", missing) + (missing.size() > 1 ? " are" : " is") + " missing");
        }
        Sex sex = switch (get(r, "sex").toLowerCase(Locale.ROOT)) {
            case "female", "f" -> Sex.FEMALE;
            case "male", "m" -> Sex.MALE;
            default -> throw ApiException.badRequest("sex must be female or male");
        };
        Species species = parseEnum(Species.class, get(r, "species"), "species");
        LocalDate dob = date(r, "dob");

        String categoryText = get(r, "category");
        Category category = categoryText.isEmpty() ? defaultCategory(sex, dob)
                : parseEnum(Category.class, categoryText, "category");

        String statusText = get(r, "status");
        Status status = statusText.isEmpty() ? Status.ACTIVE : parseEnum(Status.class, statusText, "status");
        LocalDate exitDate = date(r, "exit_date");
        if (status != Status.ACTIVE && exitDate == null) {
            throw ApiException.badRequest("add exit_date for a " + status.name().toLowerCase() + " animal");
        }

        String acq = get(r, "acquisition_type").toLowerCase(Locale.ROOT);
        Acquisition acquisition = acq.startsWith("born") ? Acquisition.BORN
                : acq.isEmpty() || acq.startsWith("purchase") || acq.startsWith("bought") ? Acquisition.PURCHASED
                : null;
        if (acquisition == null) {
            throw ApiException.badRequest("acquisition_type must be born or purchased");
        }

        Long groupId = null;
        String groupName = get(r, "group");
        if (!groupName.isEmpty()) {
            AnimalGroup g = groupsByName.get(groupName.toLowerCase(Locale.ROOT));
            if (g == null) {
                throw ApiException.badRequest("no group called \"" + groupName + "\"");
            }
            groupId = status == Status.ACTIVE ? g.getId() : null;
        }

        var req = new AnimalRequest(get(r, "tag_number"), nullIfEmpty(get(r, "animal_name")),
                nullIfEmpty(get(r, "national_id")), species, nullIfEmpty(get(r, "breed")), sex, dob,
                nullIfEmpty(get(r, "color")), number(r, "birth_weight"), category, groupId,
                null, nullIfEmpty(get(r, "dam_tag")), null, nullIfEmpty(get(r, "sire_tag")),
                acquisition, date(r, "acquisition_date"), number(r, "purchase_price"), null, null,
                nullIfEmpty(get(r, "notes")), null);
        return new Row(req, status, exitDate);
    }

    private void validate(AnimalRequest req) {
        Set<ConstraintViolation<AnimalRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            var v = violations.stream()
                    .sorted((a, b) -> a.getPropertyPath().toString().compareTo(b.getPropertyPath().toString()))
                    .findFirst().get();
            throw ApiException.badRequest(columnFor(v.getPropertyPath().toString()) + ": " + v.getMessage());
        }
    }

    /** No category given: young animals are calves, otherwise females are milking and males bulls. */
    private static Category defaultCategory(Sex sex, LocalDate dob) {
        if (dob != null && dob.isAfter(LocalDate.now(FarmService.FARM_ZONE).minusYears(1))) {
            return Category.CALF;
        }
        return sex == Sex.FEMALE ? Category.MILKING : Category.BULL;
    }

    private static String columnFor(String property) {
        return switch (property) {
            case "tag" -> "tag_number";
            case "name" -> "animal_name";
            case "dateOfBirth" -> "dob";
            case "birthWeightKg" -> "birth_weight";
            case "acquisitionDate" -> "acquisition_date";
            case "purchasePrice" -> "purchase_price";
            case "colour" -> "color";
            case "nationalId" -> "national_id";
            case "damTag" -> "dam_tag";
            case "sireTag" -> "sire_tag";
            default -> property;
        };
    }

    // ---------- cell helpers ----------

    private static String get(CSVRecord r, String column) {
        if (!r.isMapped(column) || !r.isSet(column)) {
            return "";
        }
        String v = r.get(column);
        if (v == null) {
            return "";
        }
        v = v.trim();
        return v.startsWith("'") ? v.substring(1).trim() : v;
    }

    private static boolean isBlankRow(CSVRecord r) {
        for (String v : r) {
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static LocalDate date(CSVRecord r, String column) {
        String v = get(r, column);
        if (v.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw ApiException.badRequest(column + " \"" + v + "\" isn't a date (use YYYY-MM-DD)");
    }

    private static BigDecimal number(CSVRecord r, String column) {
        String v = get(r, column).replace(",", "").replace("₹", "").trim();
        if (v.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(column + " \"" + get(r, column) + "\" isn't a number");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String column) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            String allowed = java.util.Arrays.stream(type.getEnumConstants())
                    .map(c -> c.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
            throw ApiException.badRequest("unknown " + column + " \"" + value + "\" (use " + allowed + ")");
        }
    }

    private static String nullIfEmpty(String v) {
        return v.isEmpty() ? null : v;
    }

    private static String str(Object v) {
        return v == null ? "" : v instanceof BigDecimal b ? b.stripTrailingZeros().toPlainString() : v.toString();
    }

    private static String lower(Enum<?> e) {
        return e == null ? "" : e.name().toLowerCase(Locale.ROOT);
    }

    private static String parentTag(Long id, String tag, Map<Long, Animal> byId) {
        if (id != null && byId.containsKey(id)) {
            return byId.get(id).getTag();
        }
        return str(tag);
    }
}
