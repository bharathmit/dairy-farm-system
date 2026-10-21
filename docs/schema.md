# Database schema — DRAFT v2 for review

Database: Supabase Postgres, migrations by **Flyway** in the Spring Boot app.
Access only through Spring Boot (single DB role, no row-level security). Roles enforced in Spring Security.

Tags: **[REF]** seen on the reference site · **[DAIRY]** from your dairy requirements ·
**[ALERT]** needed by `docs/requirements/alerts.md`.

## Decisions applied (2026-10-21)
- **Species:** all six kept — COW, BUFFALO, GOAT, SHEEP, PIG, POULTRY.
- **Currency/units:** INR only; kg and litres only. No currency or unit columns on records.
- **Milk:** both per-animal and farm-total entries (`yield_record.animal_id` nullable).
- **Multi-farm:** every business table has `farm_id`; a user can belong to several farms with a role per farm.

## Conventions
- PK `id BIGINT GENERATED ALWAYS AS IDENTITY` (except `app_user` = Supabase auth UUID).
- All business tables: `farm_id` (FK, indexed), `created_at`, `updated_at`, `created_by`.
- Unique keys are per farm, e.g. `UNIQUE (farm_id, tag)`.
- Enums as `VARCHAR` + `CHECK` (mapped to Java enums). Money `NUMERIC(12,2)` INR.
- Animals are never hard-deleted once they have records; status changes instead.

---

## A. Farm & users
| # | Table | Key columns |
|---|---|---|
| 1 | `app_user` | id uuid (= JWT `sub`), full_name, email, active |
| 2 | `farm` [REF] | name, country, district/village, created_by |
| 3 | `farm_member` | farm_id, user_id, role `OWNER`/`WORKER`, active · UNIQUE(farm_id,user_id) |
| 4 | `farm_settings` [REF] | farm_id (1:1), reminders on/off, health/due reminders, lead days (0/1/3/7), reminder time, theme |

## B. Herd
| # | Table | Key columns |
|---|---|---|
| 5 | `animal_group` [REF] | name, **species** (groups are single-species), location, target_outcome (GROWTH, MAINTENANCE, RECOVERY, GESTATION, OTHER), manager_name, colour, notes |
| 6 | `animal` [REF+DAIRY] | tag, name, national_id, species (read-only after create), breed, sex, date_of_birth, colour, birth_weight_kg, **category** (CALF, HEIFER, MILKING, DRY, BULL) [DAIRY], status (ACTIVE, SOLD, DEAD, TRANSFERRED), exit_date / exit_reason / exit_price [DAIRY], acquisition_type (BORN, PURCHASED), acquisition_date, purchase_price [DAIRY], dam_id / sire_id (FK, nullable), dam_tag / sire_tag (text), group_id, notes |
| 7 | `animal_photo` [REF] | animal_id, storage_path, sort_order (max 3) |
| 8 | `body_measurement` [REF+ALERT] | animal_id, record_date, weight_kg, **height_cm** [ALERT], method (SCALE, TAPE, ESTIMATE), bcs 1.0–5.0 (optional), notes |

Lineage (tree, siblings, offspring, pedigree %, common ancestors, mate-risk) is computed from
`dam_id`/`sire_id` with a recursive query — no table.

## C. Reproduction
| # | Table | Key columns |
|---|---|---|
| 9 | `heat_record` [ALERT] | animal_id, heat_date, intensity (CLEAR, WEAK, SILENT), served (bool), notes — needed for "heat expected day 21" and "no heat 60 days after calving" |
| 10 | `breeding_record` [REF+DAIRY] | dam_id, heat_record_id (nullable), service_date, method (NATURAL, AI), program (NATURAL, AI, EMBRYO_TRANSFER), sire_id / sire_tag, **semen_batch_id** FK [ALERT], ai_technician, bull_exposure_start/end, pregnancy_status (UNKNOWN, PREGNANT, NOT_PREGNANT), preg_check_date / method (ULTRASOUND, PALPATION, BLOOD) / result (CONFIRMED, NOT_CONFIRMED), expected_due_date (auto = service + species gestation, editable), dry_off_date [DAIRY], calving_date + **calving_time** [ALERT], calving_outcome (LIVE, STILLBORN, ABORTED, UNKNOWN), calving_ease (UNASSISTED, ASSISTED, DIFFICULT, CESAREAN), calf_sex, calf_birth_weight_kg, calf_id FK [DAIRY], **colostrum_given_at** [ALERT], notes |
| 11 | `semen_batch` [ALERT] | bull_name / code, breed, supplier, batch_no, straws_received, straws_used (updated on each AI), cost_per_straw, expiry_date, storage (tank) |
| 12 | `species_gestation` | species, gestation_days (cow 283, buffalo 310, goat/sheep 150, pig 114) — reference data |

## D. Health
| # | Table | Key columns |
|---|---|---|
| 13 | `health_record` [REF+ALERT] | animal_id, record_date, type (ILLNESS, VACCINATION, DEWORMING, INJURY, TREATMENT), description, medicine, dose, vet_name, cost [DAIRY], **milk_withdrawal_days / milk_withdrawal_until** [ALERT], **meat_withdrawal_until** [ALERT], next_action_date, next_action_done, schedule_id (when created from a schedule), notes |
| 14 | `health_schedule` [DAIRY+ALERT] | name, type (DEWORMING, VACCINATION), species, applies_to (CALF, HEIFER, ADULT, ALL), first_due_age_days, repeat_every_days, until_age_days, active — e.g. deworm day 15, day 30, every 60 days to 6 months … ; FMD vaccine at 4 months + booster, then every 6 months |

## E. Calf rearing [ALERT]
| # | Table | Key columns |
|---|---|---|
| 15 | `calf_feeding_record` | animal_id, record_date, milk_litres, starter_kg, notes — drives milk taper and weaning (≥ 1 kg starter/day from 90 days) |
| 16 | `growth_target` | species, breed, age_days, target_weight_kg, target_height_cm — for "growth below target" |

## F. Feed
| # | Table | Key columns |
|---|---|---|
| 17 | `feed_item` [DAIRY] | name, category (CONCENTRATE, GREEN_FODDER, DRY_FODDER, SILAGE, MINERAL, OTHER), cost_per_kg, reorder_level_kg, active |
| 18 | `feed_record` [REF] | record_date, record_time, type (USAGE, PURCHASE, ADJUSTMENT), feed_item_id, quantity_kg, cost, supplier, species / group_id / animal_id (all optional = farm level), notes |

Stock = purchases + adjustments − usage. Days of stock left = stock ÷ average daily usage (30 days).

## G. Yield
| # | Table | Key columns |
|---|---|---|
| 19 | `yield_record` [REF+DAIRY+ALERT] | record_date, record_time, **session** (AM, PM), species, yield_type (MILK, MANURE, MEAT, EGGS, WOOL), **animal_id nullable** (null = farm total), group_id, quantity (litres for milk, kg otherwise), **fat_pct, snf_pct, scc** [ALERT], income_amount, buyer, notes |

Rules: per-animal MILK only for female animals in MILKING category; farm-total rows have no animal;
a milk sale during an active withdrawal period is blocked with a warning.

## H. Money, labour, equipment
| # | Table | Key columns |
|---|---|---|
| 20 | `finance_entry` [REF] | direction (EXPENSE, INCOME), amount, category, entry_date, entry_time, counterparty, payment_method (CASH, UPI, BANK_TRANSFER, CARD, CHEQUE, OTHER), species / group_id / animal_id, quantity, unit, source_type (FEED, YIELD, HEALTH, ANIMAL, LABOUR, SEMEN, EQUIPMENT, MANUAL), source_id, notes |
| 21 | `worker` [DAIRY] | name, phone, wage_type (DAILY, MONTHLY), wage_rate, user_id (if they log in), active |
| 22 | `labour_record` [DAIRY+ALERT] | worker_id, work_date, present (bool), hours, amount_due, paid (bool), paid_date, finance_entry_id |
| 23 | `equipment` [ALERT] | name (milking machine, chaff cutter, tank …), purchase_date, cost, maintenance_every_days, last_service_date, next_service_date |
| 24 | `equipment_service` [ALERT] | equipment_id, service_date, cost, notes, finance_entry_id |
| 25 | `procurement_note` [DAIRY] | item, supplier, quantity, expected_price, needed_by, status |

## I. Alerts [ALERT]
| # | Table | Key columns |
|---|---|---|
| 26 | `alert_rule` | code (e.g. HEAT_EXPECTED), category (REPRODUCTION, CALF, HEALTH, MILK, FINANCE), name, params JSON (days, thresholds), worker_visible (bool), active |
| 27 | `alert` | rule_id, animal_id (nullable), related_type / related_id, due_date, message, severity, status (OPEN, DONE, DISMISSED), done_by, done_at · UNIQUE(rule_id, animal_id, related_id, due_date) so the daily job never duplicates |

A scheduled Spring job evaluates rules every morning; some rules also run on save (yield drop,
low BCS, semen stock). Workers only see alerts where `worker_visible = true`.

---

**Total: 27 tables** (vs 11 in v1 — growth comes from multi-farm, alerts and your dairy extras).
Dashboards, reports, lineage and P&L are queries, not tables.

## Open questions
1. Is a **farm** owned by one person, or can a farm have more than one Owner?
2. Should Workers be able to log in themselves, or does the Owner enter everything for them
   (then `worker.user_id` stays empty)?
3. `procurement_note` — keep or drop?
4. Plus the alert questions in `docs/requirements/alerts.md`.
