package com.dairyfarm.animal;

import static com.dairyfarm.support.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.dairyfarm.support.Api;
import com.dairyfarm.support.IntegrationTest;
import com.dairyfarm.support.TestTokens.TestUser;

class AnimalCsvTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Bharath");
    private final TestUser worker = TestUser.random("Murugan");
    private long farmId;

    private static final String HEADER = "tag_number,animal_name,dob,sex,species,status,national_id,dam_tag,sire_tag,"
            + "breed,color,birth_weight,category,acquisition_type,acquisition_date,purchase_price,notes\n";

    @BeforeEach
    void setUp() throws Exception {
        farmId = Api.createFarm(mvc, json, owner, "Villupuram Dairy");
        Api.addWorker(jdbc, farmId, worker);
    }

    private String animals() {
        return "/api/v1/farms/" + farmId + "/animals";
    }

    private static MockMultipartFile csv(String body) {
        return new MockMultipartFile("file", "herd.csv", "text/csv", body.getBytes(StandardCharsets.UTF_8));
    }

    private long count() {
        return jdbc.queryForObject("select count(*) from animal where farm_id = ?", Long.class, farmId);
    }

    @Test
    void templateHasTheAppColumns() throws Exception {
        String body = mvc.perform(get(animals() + "/import/template").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"herd-template.csv\""))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).startsWith("﻿tag_number,animal_name,dob,sex,species,status");
        assertThat(body).contains("VD-021,Lakshmi,2021-08-14,female,cow");
    }

    @Test
    void dryRunChecksWithoutSavingThenImportSaves() throws Exception {
        jdbc.update("insert into animal (farm_id, tag, species, sex, category, acquisition_type) values (?, 'VD-001', 'COW', 'FEMALE', 'MILKING', 'PURCHASED')", farmId);
        String file = "﻿" + HEADER
                + "VD-019,,2026-09-28,female,cow,active,,VD-021,,Jersey,,28,,born,,,\n"
                + "VD-021,Lakshmi,14/08/2021,Female,Cow,,,D-12,AI HF-221,HF,Black & white,,milking,purchased,2023-02-10,\"65,000\",\n"
                + "vd-001,Dup,,female,cow,,,,,,,,,,,,\n"
                + "VD-022,,,male,cow,,,,,,,,milking,,,,\n"
                + "VD-023,,,female,camel,,,,,,,,,,,,\n"
                + "VD-024,,,female,cow,sold,,,,,,,,,,,\n"
                + "VD-021,Again,,female,cow,,,,,,,,,,,,\n"
                + ",,,,,,,,,,,,,,,,\n";

        mvc.perform(multipart(animals() + "/import").file(csv(file)).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.rows").value(7))
                .andExpect(jsonPath("$.ready").value(2))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.duplicates", contains("vd-001", "VD-021")))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0]").value("Row 5: Milking animals must be female."))
                .andExpect(jsonPath("$.errors[1]").value("Row 6: unknown species \"camel\" (use cow, buffalo, goat, sheep, pig, poultry)"))
                .andExpect(jsonPath("$.errors[2]").value("Row 7: add exit_date for a sold animal"));
        assertThat(count()).isEqualTo(1);

        mvc.perform(multipart(animals() + "/import").file(csv(file)).param("dryRun", "false").with(as(owner)))
                .andExpect(jsonPath("$.created").value(2));
        assertThat(count()).isEqualTo(3);

        Long damId = jdbc.queryForObject("select id from animal where tag = 'VD-021'", Long.class);
        assertThat(jdbc.queryForObject("select dam_id from animal where tag = 'VD-019'", Long.class)).isEqualTo(damId);
        assertThat(jdbc.queryForObject("select category from animal where tag = 'VD-019'", String.class)).isEqualTo("CALF");
        assertThat(jdbc.queryForObject("select purchase_price from animal where tag = 'VD-021'", java.math.BigDecimal.class))
                .isEqualByComparingTo("65000");
    }

    @Test
    void rejectsFilesWithoutTheRequiredColumns() throws Exception {
        mvc.perform(multipart(animals() + "/import").file(csv("tag,name\nVD-1,Lakshmi\n")).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Missing columns: tag_number, sex, species. Start from the template."));
    }

    @Test
    void exportRoundTripsAndNeutralisesFormulas() throws Exception {
        String file = HEADER
                + "VD-021,Lakshmi,2021-08-14,female,cow,,,D-12,,HF,,,milking,purchased,2023-02-10,65000,=HYPERLINK(\"x\")\n"
                + "VD-030,,2019-01-01,female,cow,dead,,,,,,,dry,purchased,,,\n";
        mvc.perform(multipart(animals() + "/import").file(csv(file.replace("dead,", "active,"))).param("dryRun", "false").with(as(owner)))
                .andExpect(jsonPath("$.created").value(2));

        String out = mvc.perform(get(animals() + "/export.csv").with(as(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(out).contains("VD-021,Lakshmi,2021-08-14,female,cow,active,,D-12,,HF,,,milking,purchased,2023-02-10,65000,\"'=HYPERLINK(\"\"x\"\")\"");

        mvc.perform(get(animals() + "/export.csv").with(as(worker))).andExpect(status().isForbidden());
    }
}
