# Database schema — DRAFT for review

Database: Supabase Postgres. Managed by **Flyway** migrations in the Spring Boot app.
Access: Spring Boot only (single DB role). No row-level security; roles enforced in Spring Security.

Tags: **[REF]** = seen on the reference site · **[DAIRY]** = added from the Villupuram dairy
requirements (not on the reference site).

Conventions
- PK `id BIGINT GENERATED ALWAYS AS IDENTITY` (except `app_user`, which uses the Supabase auth UUID).
- Every table has `created_at`, `updated_at` (timestamptz), `created_by` (uuid → app_user).
- Enums stored as `VARCHAR` + `CHECK` constraint (easy to change via migration; maps to Java enums).
- Units fixed to metric: kg, litres. Currency fixed to INR (`NUMERIC(12,2)`).
- Soft delete via `status` for animals; hard delete for records only by Owner.

---

## Phase 1 — core (reference parity + dairy essentials)

### 1. `app_user`
| Column | Type | Notes |
|---|---|---|
| id | uuid PK | = Supabase `auth.users.id` (JWT `sub`) |
| full_name | varchar(120) | |
| email | varchar(200) | copied from JWT |
| role | varchar(10) | `OWNER`, `WORKER` |
| active | boolean | |

### 2. `farm_settings` (single row) [REF]
farm_name · country · reminder_enabled · health_reminders · due_date_reminders ·
reminder_lead_days (0/1/3/7) · reminder_time · theme default.

### 3. `animal_group` [REF]
| Column | Type | Notes |
|---|---|---|
| name | varchar(80) unique | |
| location | varchar(120) | shed / paddock |
| target_outcome | varchar(20) | GROWTH, MAINTENANCE, RECOVERY, GESTATION, OTHER |
| manager_name | varchar(80) | |
| notes | text | |

### 4. `animal` [REF + DAIRY]
| Column | Type | Notes |
|---|---|---|
| tag | varchar(30) unique not null | ear tag; also used for QR code [DAIRY] |
| name | varchar(60) | |
| national_id | varchar(40) | |
| species | varchar(10) | COW, BUFFALO (+ GOAT, SHEEP if wanted) |
| breed | varchar(40) | HF, Jersey, HF×Jersey … |
| sex | varchar(6) | MALE, FEMALE |
| date_of_birth | date not null | |
| colour | varchar(40) | |
| birth_weight_kg | numeric(6,2) | |
| category | varchar(10) | **[DAIRY]** CALF, HEIFER, MILKING, DRY, BULL — set manually, suggested from breeding events |
| status | varchar(12) | ACTIVE, SOLD, DEAD, TRANSFERRED |
| exit_date / exit_reason / exit_price | date / varchar / numeric | **[DAIRY]** sale/death/cull details (cull reason supports early culling) |
| acquisition_type | varchar(10) | BORN, PURCHASED |
| acquisition_date / purchase_price | date / numeric | purchase_price **[DAIRY]** for per-animal P&L |
| dam_id / sire_id | FK → animal, nullable | linked parent |
| dam_tag / sire_tag | varchar(30) | parent tag when not in herd (e.g. AI bull) |
| group_id | FK → animal_group, nullable | |
| photo_url | varchar(300) | Supabase Storage path |
| notes | text | |

Lineage, siblings, offspring and the mate-risk check are computed from `dam_id`/`sire_id`
(recursive CTE), so no extra table.

### 5. `weight_record` [REF]
animal_id FK · record_date · weight_kg · method (SCALE, TAPE, ESTIMATE) · bcs numeric(2,1) (1–5) · notes.

### 6. `health_record` [REF]
| Column | Type | Notes |
|---|---|---|
| animal_id | FK not null | group batch entry = one row per member |
| record_date | date | |
| type | varchar(12) | ILLNESS, VACCINATION, DEWORMING, INJURY, TREATMENT |
| description | text | |
| medicine | varchar(120) | |
| vet_name | varchar(80) | |
| cost | numeric(12,2) | **[DAIRY]** → auto finance entry "Vet & Medicine" |
| next_action_date | date | drives reminders |
| next_action_done | boolean | **[DAIRY]** so reminders can be closed |
| notes | text | |

### 7. `breeding_record` [REF + DAIRY]
One row per service → pregnancy → calving cycle (as on the reference site).
| Column | Type | Notes |
|---|---|---|
| dam_id | FK → animal (female) | |
| heat_date | date | **[DAIRY]** heat detection |
| heat_observed | varchar(10) | NOT_RECORDED, CLEAR, WEAK, SILENT |
| service_date | date | |
| method | varchar(8) | NATURAL, AI |
| sire_id / sire_tag | FK / varchar | bull in herd or external bull / semen bull |
| ai_technician · semen_batch | varchar | |
| bull_exposure_start / end | date | natural service |
| pregnancy_status | varchar(12) | UNKNOWN, PREGNANT, NOT_PREGNANT |
| preg_check_date · preg_check_method · preg_check_result | date / varchar / varchar | ULTRASOUND, PALPATION, BLOOD; CONFIRMED / NOT_CONFIRMED |
| expected_due_date | date | default service_date + 283 days (cow) |
| dry_off_date | date | **[DAIRY]** planned ~60 days before due |
| calving_date | date | |
| calving_outcome | varchar(10) | LIVE, STILLBORN, ABORTED, UNKNOWN |
| calving_ease | varchar(10) | UNASSISTED, ASSISTED, DIFFICULT, CESAREAN |
| calf_sex · calf_birth_weight_kg | varchar / numeric | |
| calf_id | FK → animal, nullable | **[DAIRY]** creates/link the calf record |
| notes | text | |

### 8. `feed_item` (feed master) [DAIRY]
name unique · category (CONCENTRATE, GREEN_FODDER, DRY_FODDER, SILAGE, MINERAL, OTHER) · unit (KG) ·
current_cost_per_unit · reorder_level · active.
(The reference uses free-text feed items; a master list is needed for stock levels and your
feed_master design. Detailed ration calculation stays in your Excel template.)

### 9. `feed_record` [REF]
| Column | Type | Notes |
|---|---|---|
| record_date · record_time | date / time | |
| type | varchar(10) | USAGE, PURCHASE, ADJUSTMENT |
| feed_item_id | FK | |
| quantity · unit | numeric / varchar | |
| cost | numeric(12,2) | purchase → auto finance expense "Feed" |
| supplier | varchar(80) | |
| scope | varchar(6) | FARM, GROUP, ANIMAL |
| group_id / animal_id | FK nullable | per scope |
| notes | text | |

Stock on hand = Σ purchase + Σ adjustment − Σ usage per item. Days of stock left = stock ÷ average
daily usage (last 30 days).

### 10. `yield_record` [REF + DAIRY]
| Column | Type | Notes |
|---|---|---|
| record_date | date | |
| session | varchar(4) | **[DAIRY]** AM, PM (6 AM / 6 PM milking) |
| yield_type | varchar(8) | MILK, MANURE, MEAT |
| scope · group_id · animal_id | | milk normally per animal; manure per farm |
| quantity · unit | numeric / varchar | litres for milk, kg otherwise |
| fat_pct · snf_pct | numeric(4,2) | **[DAIRY]** optional milk quality (pricing) |
| income_amount · buyer | numeric / varchar | → auto finance income "Milk/Manure sales" |
| notes | text | |
Constraint: MILK only for FEMALE animals in category MILKING.

### 11. `finance_entry` [REF]
| Column | Type | Notes |
|---|---|---|
| direction | varchar(7) | EXPENSE, INCOME |
| amount | numeric(12,2) | INR |
| category | varchar(20) | FEED, LABOUR, VET_MEDICINE, EQUIPMENT, TRANSPORT, UTILITIES, ANIMAL_PURCHASE, OTHER_EXPENSE; MILK_SALES, ANIMAL_SALES, MANURE_SALES, OTHER_INCOME |
| entry_date · entry_time | date / time | |
| counterparty | varchar(80) | |
| payment_method | varchar(14) | CASH, BANK_TRANSFER, UPI, CARD, CHEQUE, OTHER |
| scope · group_id · animal_id | | allocation for per-animal P&L |
| quantity · unit | | |
| source_type · source_id | varchar / bigint | FEED, YIELD, HEALTH, ANIMAL, MANUAL — auto entries are edited via their source |
| notes | text | |

**Phase 1 total: 11 tables.**

---

## Phase 2 — dairy extensions (not on reference site)
12. `health_schedule` — protocol templates: deworming (15d → 30d → every 2 months to 6 m → every
    3 months to 1 y → yearly) and vaccination (FMD at 4 months + booster, twice yearly). Generates
    `next_action_date` reminders automatically by age.
13. `worker` + `labour_log` — workers, attendance, wages (→ finance "Labour").
14. `procurement_note` — supplier notes and planned purchases.

Reminders, dashboard KPIs and reports are **queries/views**, not tables.

---

## Questions for you
1. **Species:** cows (+ buffalo) only, or keep goat/sheep too?
2. **Currency/units:** OK to fix INR + kg/litres and drop multi-currency?
3. **Milk recording:** per cow per session (AM/PM), or a farm total per session?
4. **Farm:** single farm only, or should the schema support more farms later (adds `farm_id` everywhere)?
5. **Phase 2 tables:** keep, change or drop any?
