# Database schema — v4 (consolidated) — for review

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
id uuid (= JWT `sub`) · full_name · email · phone.

## 2. `farm`
name · village · district · state · country · latitude · longitude ·
**settings** JSONB: theme, language, reminders on/off, reminder lead days (0/1/3/7), reminder time,
morning digest time, quiet hours, milk rate ₹/L, margin alert % (feed cost vs milk income).

## 3. `farm_member` — owners and workers
farm_id · user_id (nullable — a worker can exist without a login) · name · phone ·
role (OWNER, WORKER) · wage_type (NONE, DAILY, MONTHLY) · wage_rate · joined_on · active.
UNIQUE (farm_id, user_id). A farm can have more than one OWNER.

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
| exit_date, exit_reason, exit_price | sale, death, cull reason |
| photo_paths text[] | up to 3 Supabase Storage paths |
| notes | |

## 6. `farm_event` — everything observed on a date
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
Indexes: (farm_id, animal_id, event_type, event_date); partial indexes on open next_action_date and
active withdrawal dates.

## 7. `breeding_record` — one row per service → pregnancy → calving cycle
dam_id · heat_event_id (→ `farm_event` HEAT) · service_date · method (NATURAL, AI, ET) ·
sire_id / sire_tag · semen_item_id (→ `item` SEMEN) · ai_technician · bull_exposure_start/end ·
pregnancy_status (UNKNOWN, PREGNANT, NOT_PREGNANT) · preg_check_date / method (ULTRASOUND, PALPATION,
BLOOD) / result · expected_due_date (auto from gestation days, editable) · dry_off_date ·
calving_date, calving_time · calving_outcome (LIVE, STILLBORN, ABORTED, UNKNOWN) · calving_ease
(UNASSISTED, ASSISTED, DIFFICULT, CESAREAN) · calf_sex · calf_birth_weight_kg · calf_id (→ `animal`) ·
colostrum_given_at · notes.
Kept separate because one row is updated over ~10 months and many alerts read its dates.

## 8. `item` — anything stocked or owned
| Column | Notes |
|---|---|
| item_type | FEED, SEMEN, MEDICINE, EQUIPMENT, OTHER |
| name, category | FEED: CONCENTRATE, GREEN_FODDER, DRY_FODDER, SILAGE, MINERAL · MEDICINE: VACCINE, DEWORMER, ANTIBIOTIC … |
| unit | KG, L, STRAW, DOSE, NOS |
| cost_per_unit, reorder_level, lead_time_days | low-stock alert |
| supplier, supplier_contact | |
| batch_no, expiry_date | semen and medicine expiry |
| service_every_days, next_service_date | equipment maintenance |
| details JSONB | SEMEN: bull name/code, breed · MEDICINE: default milk/meat withdrawal days · EQUIPMENT: purchase date/cost |
| active | |

## 9. `item_txn` — stock movements
item_id · txn_type (PURCHASE, USAGE, ADJUSTMENT, SERVICE) · txn_date · txn_time · quantity · cost ·
supplier · allocation: species / group_id / animal_id (all optional = whole farm) ·
source_type / source_id (e.g. the breeding record that used a straw) · notes.
Stock = purchases + adjustments − usage. A PURCHASE or SERVICE with a cost creates a finance entry.

## 10. `production_record` — milk and other produce
record_date · session (AM, PM, DAY) · product (MILK, MANURE, MEAT, EGGS, WOOL) · species ·
animal_id (null = farm total) · group_id · quantity · unit (L, KG) · fat_pct · snf_pct · protein_pct ·
density · scc · income_amount · buyer · notes.
Herd milking sheet = one request saving AM and PM rows for every milking cow.
Milk grade A–D is calculated. MILK per animal only for females in MILKING category; a sale is
blocked while that cow's milk withdrawal is active.

## 11. `finance_entry`
direction (EXPENSE, INCOME) · category (FEED, MEDICINE, VET, LABOUR, EQUIPMENT, SEMEN, TRANSPORT,
UTILITIES, ANIMAL_PURCHASE, OTHER_EXPENSE; MILK_SALES, ANIMAL_SALES, MANURE_SALES, OTHER_INCOME) ·
amount · entry_date · entry_time · **status (PAID, PENDING)** · payment_method (CASH, UPI,
BANK_TRANSFER, CARD, CHEQUE, OTHER) · counterparty · member_id (wages) · period_from / period_to
(wage week) · allocation species / group_id / animal_id · quantity · unit ·
source_type / source_id (ITEM_TXN, PRODUCTION, EVENT, ANIMAL, MANUAL) · notes.

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

---

## Calculated, not stored
Age · days in milk · lactation stage · days open · calving interval · pregnancy %, conception rate,
AI rate · lineage tree, siblings, offspring, pedigree %, mate risk · average daily gain · stock on hand
and days of stock left · milk grade · income over feed cost · per-animal and farm P&L · ROI ranking ·
cull-advisor scores · health risk score · THI · dashboards, report drill-downs, compare cows, search.

## Trade-offs to accept
- JSONB `details` is checked in Java, not by database constraints.
- `farm_event` grows fastest; it is indexed by farm, animal, type and date and can be partitioned by year later.
- If daily labour attendance turns out heavy, it can move to its own table without affecting others.
