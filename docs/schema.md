# Database schema — v4.2

**Status:** tables 1–5 (`app_user`, `farm`, `farm_member`, `animal_group`, `animal`) are approved and
built (Flyway `V1`, `V2`). **Daily records (Flyway `V3`) built:** `contact` (new, table 14),
`production_record` (table 10), `farm_event` (table 6, MEASUREMENT and HEALTH used so far) and
`farm.milk_grading`. Tables 7–9 and 11–13 are still drafts for their modules.
v4.2 changes: `app_user.preferences`; farm settings now hold milking times; `farm_member` gains email,
date of birth, sex, left_on and login type; `animal` gains exit_party, last_calving_date and
current_weight_kg.

v3 had 30 tables. v4 covers the same requirements with **13 tables**.
How each requirement maps to these tables: `docs/requirements/coverage.md`.

Database: Supabase Postgres, migrations by Flyway, accessed only through Spring Boot (no RLS).

## How the table count was reduced
| Idea | Merged | Into |
|---|---|---|
| One table for dated, per-animal (or farm-level) observations | weight/height/BCS, heat signs, health records, calf feeding, barn readings, labour attendance | `farm_event` |
| One inventory with transactions | feed items, semen straws, medicines, equipment + feed records, semen use, equipment service | `item` + `item_txn` |
| One table for small configuration lists | alert rules, health schedules, growth targets, gestation days, daily-task templates | `config` |
| One "to-do" table | automatic alerts, manual reminders, daily checklist ticks, procurement notes, heat-sign AI reminders | `alert` |
| Settings stored on the farm | farm settings | `farm.settings` (JSONB) |
| Workers are farm members | worker | `farm_member` (login optional) |
| Photos stored on the animal | animal photos | `animal.photo_paths` (max 3) |

Type-specific fields that are rarely filtered on are kept in a `details` JSONB column; fields that alerts,
reports or filters query are real columns with indexes. Java validates `details` per type
(Hibernate 6 maps JSONB natively).

## Conventions
- PK `id BIGINT GENERATED ALWAYS AS IDENTITY` (except `app_user.id` = Supabase auth UUID).
- Every business table has `farm_id` (FK, indexed), `created_at`, `updated_at`, `created_by`.
- Enums are `VARCHAR` + `CHECK`; money `NUMERIC(12,2)` INR; units kg and litres only.
- Species: COW, BUFFALO, GOAT, SHEEP, PIG, POULTRY.

---

## 1. `app_user`
id uuid (= JWT `sub`) · full_name · email · phone · **preferences** JSONB (theme LIGHT/DARK/SYSTEM, language EN/TA — per person, not per farm).

## 2. `farm`
name · village · district · state · country · latitude · longitude ·
**settings** JSONB: milk rate ₹/L, morning and evening milking times, margin alert % (feed cost vs milk
income), notifications (on/off, health, breeding, checklist, lead days 0/1/3/7, reminder time, morning
digest + time, quiet hours start/end). Theme and language moved to `app_user.preferences`.
**milk_grading** JSONB (NULL = defaults): basis (OWN_LACTATION / BREED), breed peaks (litres, peak day),
start %, fall % per month, first-calver %, lactation days, grade bands A/B/C, quality checks (min fat,
min SNF, max SCC) and the sudden-drop alert %. See `docs/api-daily-records.md` for the calculation.

## 3. `farm_member` — owners and workers
farm_id · user_id (nullable — a worker can exist without a login) · name · email · phone ·
date_of_birth · sex · role (OWNER, WORKER) · wage_type (NONE, DAILY, MONTHLY) · wage_rate · joined_on ·
left_on · active · login_type (NONE, PASSWORD = owner set the password, INVITE = emailed invite, SELF) ·
invited_at. UNIQUE (farm_id, user_id) and (farm_id, lower(email)). A farm can have more than one OWNER
and always keeps at least one active OWNER. A member added by email is linked to the account with that
email on first sign-in (only when login_type is PASSWORD or INVITE).

## 4. `animal_group`
name · species (one species per group) · location · target_outcome (GROWTH, MAINTENANCE, RECOVERY,
GESTATION, OTHER) · manager_member_id · colour · notes.

## 5. `animal`
| Column | Notes |
|---|---|
| tag (UNIQUE per farm), name, national_id | tag is also the QR / ear-tag scan key |
| species, breed, sex | species read-only after create |
| date_of_birth | required when acquisition_type = BORN, optional for purchased adults |
| colour, birth_weight_kg | |
| category | CALF, HEIFER, MILKING, DRY, BULL, OTHER (non-dairy) |
| status | ACTIVE, SOLD, DEAD, TRANSFERRED |
| group_id | one group at a time |
| dam_id, sire_id / dam_tag, sire_tag | linked parent or tag-only parent (e.g. AI bull) |
| acquisition_type, acquisition_date, purchase_price | BORN, PURCHASED |
| exit_date, exit_reason, exit_price, exit_party | sale, death, cull reason; buyer or "transferred to" |
| photo_paths text[] | up to 3 Supabase Storage paths |
| last_calving_date, current_weight_kg | opening values from the animal form; kept current by the breeding and measurement modules |
| notes | |

## 6. `farm_event` — everything observed on a date (built in V3)
Built now: event types MEASUREMENT (sub_type SCALE/TAPE/ESTIMATE; details.heartGirthCm) and HEALTH
(sub_type ILLNESS/TREATMENT/VACCINATION/DEWORMING/INJURY; details: symptoms, diagnosis, medicine, dose,
route, milk/meat withdrawal days). The vet is `contact_id` (→ `contact`) instead of details.vet_name;
`milk_withdrawal_until` is a timestamptz (treatment date + days, at the evening milking time). `item_id`
and `member_id` come with the stock and labour modules.
| Column | Notes |
|---|---|
| animal_id | nullable (farm-level events) |
| member_id | nullable (labour attendance) |
| batch_id uuid | same value for a batch treatment of a whole group |
| event_type | MEASUREMENT, HEAT, HEALTH, CALF_FEEDING, BARN_READING, ATTENDANCE |
| sub_type | HEALTH: ILLNESS, VACCINATION, DEWORMING, INJURY, TREATMENT · HEAT: MILD, MODERATE, STRONG, SILENT · MEASUREMENT: SCALE, TAPE, ESTIMATE |
| event_date, event_time | |
| weight_kg, height_cm, bcs | measurements (BCS 1.0–5.0) |
| cost | health cost → finance entry |
| item_id | medicine/vaccine used (from `item`) |
| next_action_date, next_action_done | follow-up reminders |
| milk_withdrawal_until, meat_withdrawal_until | "do not sell" check |
| details JSONB | HEAT: signs[], served · HEALTH: symptoms, diagnosis, medicine, dose, vet_name, schedule_code · CALF_FEEDING: milk_litres, starter_kg, colostrum · BARN_READING: temperature_c, humidity_pct, thi · ATTENDANCE: present, hours |
| notes | |
HEAT and CALF_FEEDING rows are used from V4 (one CALF_FEEDING row per calf per day, unique).
Indexes: (farm_id, animal_id, event_type, event_date); partial indexes on open next_action_date and
active withdrawal dates.

## 7. `breeding_record` — one row per service → pregnancy → calving cycle (built in V4)
dam_id · lactation_number (the dam's lactation when served) · heat_event_id (→ `farm_event` HEAT) ·
service_date, service_time · method (NATURAL, AI, ET, UNKNOWN = calving with no recorded service) ·
semen_item_id (→ `item` SEMEN), straws · sire_id / sire_tag · bull_exposure_start/end ·
**costs:** technician_contact_id + technician_fee, bull_owner_contact_id + bull_fee, semen_cost (straws × cost per
straw), fee_paid — kept for Finance (Canvas 6) · expected_due_date (service + gestation days, editable) ·
pregnancy_status (UNKNOWN, PREGNANT, NOT_PREGNANT) · preg_check_date / method (ULTRASOUND, PALPATION, BLOOD) /
cost / checked-by contact · calving_date, calving_time · calving_outcome (LIVE, STILLBORN, ABORTED) ·
calving_ease (EASY, ASSISTED, HARD, VET) · calves JSONB `[{animalId, sex, birthWeightKg}]` (twins) ·
colostrum_given_at, colostrum_litres · retained_placenta · notes.
PD due and dry-off dates are calculated from settings, not stored. Breeding status (open, served, pregnant,
overdue), days open, missed heats and failed services are calculated (BreedingStatusCalc).
Kept separate because one row is updated over ~10 months and many alerts read its dates.
`animal.lactation_number` (V4) counts calvings (opening value on the animal form, +1 per live/stillborn calving);
`farm.breeding_settings` JSONB holds gestation, cycle, dry-off, keep-or-sell and calf-rearing rules.

## 8. `item` — anything stocked or owned (built in V4; SEMEN batches and, since V5, FEED items)
| Column | Notes |
|---|---|
| item_type | FEED, SEMEN, MEDICINE, EQUIPMENT, OTHER |
| name, category | FEED: CONCENTRATE_INGREDIENT, GREEN_FODDER, DRY_FODDER, SILAGE, MINERAL, MIX (V5 check; feed names unique per farm) · MEDICINE: VACCINE, DEWORMER, ANTIBIOTIC … |
| unit | KG, L, STRAW, DOSE, NOS |
| cost_per_unit, reorder_level, lead_time_days | low-stock alert |
| supplier, supplier_contact | |
| batch_no, expiry_date | semen and medicine expiry |
| service_every_days, next_service_date | equipment maintenance |
| details JSONB | FEED: **nutrients per kg** (dmPct, cpPct, tdnPct, caPct, pPct), maxInclusionPct, excluded + excludedReason (e.g. cottonseed cake); a prepared concentrate is category MIX · SEMEN: bull name/code, breed · MEDICINE: default milk/meat withdrawal days · EQUIPMENT: purchase date/cost |
| active | |

## 9. `item_txn` — stock movements (built in V4; V5 adds payment_method, paid, session, batch_id)
item_id · txn_type (PURCHASE, USAGE, ADJUSTMENT, SERVICE, **PRODUCE**) · txn_date · txn_time · quantity · cost ·
payment_method · paid · session (AM/PM feeding) · batch_id (rows saved together) · created_by ·
supplier · allocation: species / group_id / animal_id (all optional = whole farm) ·
source_type / source_id (e.g. the breeding record that used a straw) · notes.
Stock = purchases + produced + adjustments − usage. A PURCHASE or SERVICE with a cost creates a finance entry.
**Concentrate batch preparation** = one USAGE row per ingredient + one PRODUCE row for the mix item,
all sharing source_type FORMULA_BATCH; the mix's cost per kg is the ingredient cost ÷ batch kg.
Feeding at 6 AM / 6 PM = USAGE rows (source_type FEEDING, session, txn_time) for the whole farm, a group or an animal;
saving a session again replaces its rows. Own fodder cut on the farm = PRODUCE with source_type HARVEST.

## 10. `production_record` — milk and other produce (built in V3)
As built: product MILK/MANURE/OTHER; per-cow rows are AM/PM only (unique per cow, date, session);
farm totals/sales have animal_id NULL (unique per farm, date, session, product) with sold_qty, rate,
income_amount, buyer_contact_id (→ `contact`), paid. `withheld` marks milk taken during a withdrawal
(re-checked whenever milk or health records change). Species/group columns were not needed.
record_date · session (AM, PM, DAY) · product (MILK, MANURE, MEAT, EGGS, WOOL) · species ·
animal_id (null = farm total) · group_id · quantity · unit (L, KG) · fat_pct · snf_pct · protein_pct ·
density · scc · income_amount · buyer · notes.
Herd milking sheet = one request saving AM and PM rows for every milking cow.
Milk grade A–D is calculated. MILK per animal only for females in MILKING category; a sale is
blocked while that cow's milk withdrawal is active.

## 11. `finance_entry` (built in V6; V6.1 backfills automatic entries)
direction (INCOME, EXPENSE) · category (MILK_SALES, ANIMAL_SALES, MANURE_SALES, OTHER_INCOME; FEED, LABOUR, VET,
BREEDING, SEMEN, EQUIPMENT, UTILITIES, TRANSPORT, ANIMAL_PURCHASE, OTHER_EXPENSE) · amount · entry_date · entry_time ·
**status (PAID, PENDING)** · payment_method · counterparty · contact_id · member_id + period_from / period_to (wages) ·
advance / advance_deducted (wage advances) · alloc_kind (FARM, GROUP, ANIMAL) + group_id / animal_id · quantity · unit ·
source_type (MILK_SALE, ITEM_TXN, HEALTH, BREEDING, ANIMAL) + source_id + source_part · description · auto · notes.
UNIQUE (farm_id, source_type, source_id, source_part). Automatic entries are rewritten from their source
(`FinanceSql`) in the same transaction as every change to it; manual entries (incl. wages) are edited directly.

## 17. `attendance` (built in V6)
member_id · work_date · status (PRESENT, HALF, ABSENT) · hours. UNIQUE (member_id, work_date).

## 18. `procurement_note` (built in V6)
item_id (optional) · title · quantity · unit · expected_price · needed_by · supplier_contact_id · status (PLANNED,
ORDERED, RECEIVED, CANCELLED) · ordered_on · received_on · paid_amount · purchase_txn_id · finance_entry_id ·
source (MANUAL, LOW_STOCK, FEED_NEED) · notes. Equipment is `item` EQUIPMENT (details: brand, model,
serviceEveryDays, serviceTask, reminderDays, purchaseDate, purchaseCost); a service is `item_txn` SERVICE with
V6 columns work_done, mechanic_name, next_service_date.

## 12. `alert` — every to-do in one list
source (RULE, MANUAL, CHECKLIST, HEAT_SIGN, PROCUREMENT) · config_id (rule or checklist template) ·
category (REPRODUCTION, CALF, HEALTH, MILK, FINANCE, FEED, OPERATIONS) · title · notes ·
animal_id · related_type / related_id · due_date · due_time · severity · worker_visible ·
status (OPEN, DONE, DISMISSED) · done_by · done_at · details JSONB (procurement: item, quantity,
expected price, needed by).
UNIQUE (farm_id, source, config_id, animal_id, related_id, due_date) so the daily job never duplicates.

## 13. `config` — small configurable lists (farm_id null = system default)
config_type · code · name · species · breed · params JSONB · worker_visible · sort_order · active.
UNIQUE (farm_id, config_type, code).
| config_type | params example |
|---|---|
| ALERT_RULE | `{"days":21}`, `{"dropPct":20}`, `{"minFat":3.5}`, `{"minStraws":5}` |
| HEALTH_SCHEDULE | `{"type":"DEWORMING","ageDays":15}`, `{"type":"VACCINATION","name":"FMD","ageDays":120,"repeatDays":180}` |
| GROWTH_TARGET | `{"ageDays":180,"weightKg":150,"heightCm":105}` |
| GESTATION | `{"days":283}` per species |
| DAILY_TASK | built-in and custom checklist tasks |
| FEED_FORMULA | concentrate recipe: `{"batchKg":100,"mixItemId":…,"lines":[{"itemId":…,"kg":20}, …],"targets":{"cpPct":…,"tdnPct":…}}` |
| RATION_RULE | per animal category (CALF, HEIFER, MILKING, DRY, BULL): `{"bwPct":10,"split":{"green":…,"dry":…,"conc":…},"concBaseKg":1.5,"concPerLitreKg":0.4,"maxConcKg":…,"green":[{"itemId":…,"kg":8},{"itemId":…,"kg":12},{"itemId":…,"kg":5}],"dry":[{"itemId":…,"kg":6},{"itemId":…,"kg":4}],"limits":[{"itemId":…,"maxKgPerDay":…,"stage":"EARLY"}],"targets":{"cpPct":…,"tdnPct":…},"effectiveFrom":"2026-11-01"}` |

---

## 14. `contact` — buyers, vets, AI technicians, suppliers (built in V3)
name (unique per farm, ignoring case) · roles TEXT[] (BUYER, VET, AI_TECH, SUPPLIER, OTHER) · phone ·
village · milk_rate (buyer's usual ₹/L) · pay_every (DELIVERY, WEEK, TEN_DAYS, FORTNIGHT, MONTH) · notes ·
active. Used by milk sales (Sold to) and health records (Vet); a used contact can only be made inactive.

## 15. `feed_formula` + `feed_formula_line` — concentrate recipes (built in V5)
feed_formula: name (unique per farm) · mix_item_id (→ `item` MIX) · batch_kg · targets JSONB (dmPct, cpPct, tdnPct,
caPct, pPct, costMin, costMax) · notes · active. feed_formula_line: formula_id · item_id · kg · sort_order.
The latest active formula of a mix splits ration concentrate into ingredient need.

## 16. `ration_rule` — ration rule per category, versioned (built in V5)
category (CALF, HEIFER, MILKING, DRY, BULL) · effective_from · params JSONB (bwPct, concBaseKg, concPerLitreKg,
maxConcKg, concItemId, greenSharePct, green[], dry[], limits[], dmIntakePct, cpPct, tdnPct, caPct, pPct).
UNIQUE (farm_id, category, effective_from). No row = defaults in code.

## Ration planning (as built in V5: formulas and rules got their own tables 15–16 instead of `config`)
1. **Ingredients** are `item` FEED rows with nutrient values and cost per kg (updated from purchases).
2. **Concentrate formula** (e.g. your 100 kg Villupuram batch) is a `config` FEED_FORMULA. The planner
   shows ingredient kg, cost per kg of mix, and nutrient totals vs targets; excluded or over-limit
   ingredients are flagged.
3. **Prepare batch** checks ingredient stock and posts `item_txn` USAGE + PRODUCE rows.
4. **Ration rules** per category are `config` RATION_RULE: body weight × 10 % split into green / dry /
   concentrate, concentrate = 1.5 kg + 0.4 kg per litre of milk, the green and dry mixes, max
   concentrate, stage-wise limits (e.g. gingelly cake).
5. **Daily ration** per animal is calculated from latest weight (`farm_event` MEASUREMENT), yesterday's
   milk (`production_record`) and category → kg of each feed, cost per day, nutrient check.
   Group and farm totals (e.g. ~329 kg green, ~86 kg dry, ~110 kg concentrate) are sums.
6. **Plan vs actual:** planned kg compared with `item_txn` USAGE; days of stock left uses the planned
   daily need; low stock creates a procurement alert.
Rule changes keep history through `effectiveFrom`, so old days are still explained by the rule in force.

## Calculated, not stored
Age · days in milk · lactation stage · days open · calving interval · pregnancy %, conception rate,
AI rate · lineage tree, siblings, offspring, pedigree %, mate risk · average daily gain · stock on hand
and days of stock left · milk grade · income over feed cost · per-animal and farm P&L · ROI ranking ·
cull-advisor scores · health risk score · THI · **daily ration, ration cost, nutrient balance, feed need vs stock** · dashboards, report drill-downs, compare cows, search.

## Trade-offs to accept
- JSONB `details` is checked in Java, not by database constraints.
- `farm_event` grows fastest; it is indexed by farm, animal, type and date and can be partitioned by year later.
- If daily labour attendance turns out heavy, it can move to its own table without affecting others.
