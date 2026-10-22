# API contract — Breeding & calves (Canvas 3)

Shared contract for backend and frontend. Base path `/api/v1/farms/{farmId}`. Same conventions as
`docs/api-daily-records.md`: dates `YYYY-MM-DD`, times `HH:mm`, any member may read and record,
**owner only** = `PUT /breeding/settings` and every `DELETE`, **money hidden from workers** (all cost / fee /
price fields `null` for a WORKER; a worker's save with a blank money field keeps the saved value).
`AnimalRef` = `{id, tag, name}`. `ContactRef` = `{id, name}`.

## Storage (Flyway V4)
- `farm_event` HEAT rows (sub_type MILD|MODERATE|STRONG|SILENT, `event_time`, `details.signs[]`) and
  CALF_FEEDING rows (`details`: milkAm, milkPm, starterKg, waterL, colostrumLitres, colostrumAt; `batch_id` for group saves).
- New `breeding_record` (schema.md §7) plus costs: `technician_contact_id`, `technician_fee`,
  `bull_owner_contact_id`, `bull_fee`, `semen_cost` (straws × cost per straw at save), `fee_paid`,
  `preg_check_cost`, `preg_checked_by_contact_id`, `colostrum_litres`, `retained_placenta`, `lactation_number`.
  A calving with no recorded service still gets a row (service_date null, method UNKNOWN).
- New `item` + `item_txn` (schema.md §8–9), used for SEMEN now (PURCHASE, USAGE, ADJUSTMENT). An AI service
  writes one USAGE txn (source_type BREEDING, source_id = record id); editing/deleting the service fixes it.
- `animal.lactation_number INT NOT NULL DEFAULT 0` (opening value on the animal form; +1 on each live/stillborn calving).
- `farm.breeding_settings JSONB` (null = defaults below).
Fees are stored for the Finance module (Canvas 6) to post as BREEDING expenses; no finance rows yet.

## Settings
`GET /breeding/settings` (defaults filled in) · `PUT /breeding/settings` (owner)
```
{ gestationDays: {COW:283, BUFFALO:310, GOAT:150, SHEEP:150},
  heatCycleDays: 21, voluntaryWaitDays: 45, pdAfterDays: 45, dryOffDaysBeforeDue: 60,
  targetDaysOpen: 90, sellDaysOpen: 150, sellFailedServices: 3,
  heiferFirstServiceMonths: 14, heiferFirstServiceKg: 250,
  colostrumHours: 2,
  calfTaper: [{fromDay:0,toDay:14,milkLitres:3.0,starter:"—"}, {fromDay:15,toDay:30,milkLitres:3.0,starter:"offer free"},
              {fromDay:31,toDay:60,milkLitres:3.5,starter:"0.3–0.6 kg"}, {fromDay:61,toDay:80,milkLitres:2.0,starter:"0.7–1.0 kg"},
              {fromDay:81,toDay:90,milkLitres:1.0,starter:"≥ 1 kg"}],
  weanMinDays: 90, weanStarterKg: 1.0, weanStarterDays: 3, maleSellFromDay: 30, maleSellToDay: 90 }
```

## Cow breeding status (used everywhere)
Breedable = active females, category HEIFER/MILKING/DRY (heifers from heiferFirstServiceMonths − 1).
```
CowStatus = { animal: AnimalRef, breed, category, lactationNumber,
  status: OPEN | SERVED | PREGNANT | DRY_PREGNANT | OVERDUE | HEIFER_NOT_SERVED,
  lastCalvingDate, daysOpen,                 // since last calving (null for heifers)
  lastHeatDate, nextHeatExpected,            // last heat (or service) + heatCycleDays
  lastServiceDate, currentServiceId,         // open service = latest without a NOT_PREGNANT result or calving
  pdDueDate, expectedDueDate, dryOffDate, daysToDue,
  servicesThisLactation, failedServices,     // failed = NOT_PREGNANT check, or a new heat/service ≥ 18 d after it
  heatsMissed, heatsExpected,                // 21-d cycles since voluntaryWaitDays after calving (or since first-service age) with no heat or service in ±3 d
  spendThisLactation,                        // semen + technician + bull + PD costs since last calving (owner only)
  suggestion: { action: OK | HEAT_DUE | FIRST_SERVICE_DUE | PD_DUE | DRY_OFF_DUE | CALVING_DUE | VET_CHECK | CONSIDER_SELLING,
                title, reasons: [string] } }
```
Suggestion rules (first match): overdue to calve → CALVING_DUE; dry-off date ≤ today+3 and not DRY → DRY_OFF_DUE;
pdDueDate ≤ today → PD_DUE; daysOpen > sellDaysOpen or failedServices ≥ sellFailedServices → CONSIDER_SELLING;
heatsMissed ≥ 1 with daysOpen > voluntaryWaitDays+21, or failedServices ≥ 2 → VET_CHECK; heifer of age and
not served → FIRST_SERVICE_DUE; nextHeatExpected within ±2 d → HEAT_DUE; else OK. Reasons are short sentences, e.g.
"2 failed services", "Not pregnant by day 150", "Still giving 12.5 L/day" (avg of last 7 days, if any milk).

## Endpoints
| Method | Path | Returns / notes |
|---|---|---|
| GET | `/breeding/overview` | `{date, breedable, heatWatchCount, calendar:[{date, kind: HEAT\|HEAT_EXPECTED\|PD\|DRY_OFF\|CALVING, animal}], groups:{overdue, dueIn7, open, served, pregnant, dry: [CowStatus]}}` (calendar = today..+13) |
| GET | `/breeding/heat-watch` | `[{status: CowStatus, level: HIGH\|MEDIUM\|LOW, confidencePct, reason}]` HIGH = heat expected ±2 d; MEDIUM = past wait period with no heat seen; LOW = heifer of service age |
| GET | `/breeding/animals/{animalId}/history` | `History` (below) — the open-cow review / breeding history page |
| POST | `/breeding/heats` | body `{animalId, date, time, signs:[MOUNTING, RESTLESS, MUCUS, SWELLING, CHIN_RESTING, BELLOWING, LOW_APPETITE], intensity: MILD\|MODERATE\|STRONG\|SILENT, notes}` → 201 `{heat: Heat, bestServiceAt (date-time, first seen + 12 h), nextExpected}` |
| GET | `/breeding/heats?animalId=&from=&to=` | `[Heat]` newest first. `Heat = {id, animal, date, time, signs, intensity, notes, serviceId}` |
| DELETE | `/breeding/heats/{id}` | owner |
| POST | `/breeding/services` | `ServiceRequest` → 201 `BreedingRecord` |
| GET | `/breeding/services?animalId=&from=&to=` | `[BreedingRecord]` newest first |
| GET / PUT / DELETE | `/breeding/services/{id}` | DELETE owner; restores straws |
| POST | `/breeding/services/{id}/pregnancy-check` | `{date, method: ULTRASOUND\|PALPATION\|BLOOD, result: PREGNANT\|NOT_PREGNANT, checkedByContactId, cost, expectedDueDate?}` → `BreedingRecord` |
| POST | `/breeding/calvings` | `CalvingRequest` → 201 `{record: BreedingRecord, calves: [AnimalRef]}` |
| PUT | `/breeding/services/{id}/colostrum` | `{givenAt (HH:mm), litres}` → `BreedingRecord` |
| GET | `/semen` | `{totals:{straws, low, expiring30}, batches:[SemenBatch]}` (empty batches last) |
| POST / PUT | `/semen`, `/semen/{id}` | `{bullName, bullCode, breed, supplierContactId, batchNo, expiryDate, costPerStraw, sexed, straws (POST: opening purchase), notes}` |
| POST | `/semen/{id}/adjust` | `{quantity (±int), reason}` → `SemenBatch` |
| DELETE | `/semen/{id}` | owner; 409 if used on a service |
| GET | `/calves?sex=FEMALE\|MALE&minDays=&maxDays=` | `CalfList` (calves = category CALF, active) |
| GET | `/calves/{animalId}/feeding?date=` | `{calf: CalfItem, date, record: CalfFeeding\|null, plan: TaperStep, taper:[TaperStep], recent:[CalfFeeding]}` |
| PUT | `/calves/{animalId}/feeding/{date}` | `{milkAm, milkPm, starterKg, waterL, colostrumLitres, colostrumAt, notes}` → same as GET |
| GET | `/calves/feeding-sheet?date=&session=AM\|PM&sex=&maxDays=&groupId=` | `{date, session, calves:[{animal, sex, ageDays, planLitres, litres, starterKg, colostrum}]}` (`colostrum` true under 3 days) |
| PUT | `/calves/feeding-sheet` | `{date, session, entries:[{animalId, litres, starterKg}]}` → sheet (litres go to milkAm/milkPm, or colostrumLitres under 3 days) |
| GET | `/animals/{animalId}/family` | `Family` |
| GET | `/animals/{animalId}/mate-check?sireAnimalId=&semenItemId=` | `{risk: HIGH\|MEDIUM\|NONE, relation, sharedAncestors:[string], knownPct, text}` |

```
ServiceRequest = { animalId, date, time, heatId, method: NATURAL|AI|ET,
  semenItemId, straws (default 1),                       // AI/ET; 400 if not enough straws or expired
  sireAnimalId, sireTag, bullExposureStart, bullExposureEnd,   // NATURAL (herd bull or outside tag)
  technicianContactId (role AI_TECH or VET), technicianFee, bullOwnerContactId, bullFee, feePaid,
  expectedDueDate (default date + gestationDays[species]), notes }
BreedingRecord = { id, animal: AnimalRef, serviceDate, serviceTime, method, heatId,
  semen: {itemId, bullName, bullCode, breed} | null, straws, sire: AnimalRef|null, sireTag,
  bullExposureStart, bullExposureEnd, technician: ContactRef|null, technicianFee, bullOwner: ContactRef|null, bullFee,
  semenCost, totalCost, feePaid, expectedDueDate, pdDueDate, dryOffDate,
  pregnancyStatus: UNKNOWN|PREGNANT|NOT_PREGNANT,
  pregCheck: {date, method, result, checkedBy: ContactRef|null, cost} | null,
  calving: {date, time, outcome, ease, calves:[{animal: AnimalRef|null, sex, birthWeightKg}], colostrumGivenAt, colostrumLitres,
            colostrumDueBy (date-time), retainedPlacenta} | null,
  lactationNumber, notes }
CalvingRequest = { damId, serviceId (optional; default = her open/pregnant service), date, time,
  outcome: LIVE|STILLBORN|ABORTED, ease: EASY|ASSISTED|HARD|VET,
  calves: [{sex: FEMALE|MALE, birthWeightKg, tag, name, createRecord (default true when LIVE)}],   // 1–3
  colostrumGivenAt, colostrumLitres, retainedPlacenta, notes }
  // Effects: calf animals created (CALF, BORN, dob = date, dam = damId, sire from the service: herd bull id or
  // semen bullCode as sireTag, breed = dam breed unless the semen breed differs → "<dam> × <semen>");
  // dam: lastCalvingDate = date, category MILKING (unless ABORTED), lactationNumber + 1 (LIVE/STILLBORN);
  // retainedPlacenta → a HEALTH ILLNESS record "Retained placenta" with next action today.
  // 409 if a calf tag already exists; suggest next free tag via GET /animals/next-tag?prefix= (add if missing).
History = { status: CowStatus, milkPerDay, // 7-day average or null
  current: { lactationNumber, calvedOn, events: [{date, kind: CALVING|HEAT|HEAT_MISSED|SERVICE|PD|DRY_OFF|EXPECTED_HEAT,
             title, detail, cost, serviceId}] },   // newest first
  lactations: [{ number, calvedOn, nextCalvedOn, services, daysOpen, calvingIntervalDays, spend, conceived }],  // newest first, excluding current
  totals: { avgDaysOpen, servicesPerConception, calvings, totalSpend } }
SemenBatch = { id, bullName, bullCode, breed, supplier: ContactRef|null, batchNo, expiryDate, costPerStraw, sexed,
  straws, low (straws < 5), expiringSoon (≤ 30 d), usedUpOn, notes }
CalfList = { counts: {all, female, male, shown}, milkTodayLitres, needAction, calves: [CalfItem] }
CalfItem = { animal: AnimalRef, sex, breed, dam: AnimalRef|null, ageDays, weightKg,
  colostrum: {given, dueBy} , milkLitresPerDay, starterKgPerDay,    // last recorded day
  plan: TaperStep, weightTargetKg|null, weaning: {ready, inDays}|null, maleSell: {fromDay, toDay, daysLeft}|null,
  dewormDue: [string], badges: [{text, tone: ok|warn|danger|info}] }
TaperStep = {fromDay, toDay, milkLitres, starter}
CalfFeeding = { date, milkAm, milkPm, starterKg, waterL, colostrumLitres, colostrumAt, notes }
Family = { animal: AnimalRef + {species, breed, sex, dateOfBirth, category}, pedigreeKnownPct,
  parents: {dam: Node|null, sire: Node|null}, grandparents: {damDam, damSire, sireDam, sireSire: Node|null},
  siblings: [{animal: AnimalRef, kind: FULL|HALF_DAM|HALF_SIRE}],
  offspring: [{animal: AnimalRef|null, sex, birthDate, sire: string, ease, birthWeightKg, status}],
  avgCalvingIntervalDays }
Node = { animal: AnimalRef|null, label }   // label = tag/code when not on the farm
```

## Backend notes (as built)
- `GET /animals/next-tag?prefix=` → `{tag}`: next number after the highest tag with that prefix, same zero padding
  (VD-021 → VD-022). Without `prefix` the most used prefix is taken; an unused prefix starts at 001.
- `GET /calves` and `GET /calves/feeding-sheet` both take `minDays` and `maxDays` (age in days, inclusive), plus `sex`
  (and `groupId` for the sheet). Calves without a date of birth drop out when an age filter is set.
- `/calves` `counts.all`, `counts.female`, `counts.male` are unfiltered totals of active calves; `counts.shown` is the
  filtered count (= `calves.length`).
- `bullOwnerContactId` may be any contact of the farm (any role). `technicianContactId` and `checkedByContactId`
  must have role AI_TECH or VET.
- `POST /breeding/heats` `remindService` is **not built** (left for the alerts module); `bestServiceAt` is returned so
  the app can show the time.
- Status rules: a service counts as failed when its check is NOT_PREGNANT, or (unchecked) a heat or another service is
  recorded 18+ days after it. Heats are "seen" within ±3 days of the expected date; the expected dates start at
  calving + `voluntaryWaitDays` (heifers: first-service age) and re-anchor on every heat or service seen.
- Calving: `calves` items with `createRecord` false (default for STILLBORN) are kept only on the record (`animal` null).
  A second calving on or before her last calving date is 400; a tag already on the farm is 409.
- Service edits re-write the straw usage (old straws go back first); expired semen or too few straws is 400.
  `DELETE /semen/{id}` is 409 once the batch was used on a service.
- `HeatSaved.bestServiceAt` uses the time seen, else the farm's morning milking time, + 12 h.
