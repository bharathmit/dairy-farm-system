# Requirement coverage — schema v4.1 (13 tables)

Tables: `app_user` · `farm` · `farm_member` · `animal_group` · `animal` · `farm_event` ·
`breeding_record` · `item` · `item_txn` · `production_record` · `finance_entry` · `alert` · `config`.
"Calc" = calculated by the backend from the listed tables, not stored.

## A. Platform
| Requirement | Table(s) | How |
|---|---|---|
| Login with Supabase Auth | `app_user` | id = JWT subject |
| Multiple farms per user | `farm`, `farm_member` | every table carries `farm_id`; API under `/farms/{farmId}` |
| Owner / Worker roles | `farm_member.role` | Spring Security checks role per farm |
| Workers without a login | `farm_member.user_id` null | owner records on their behalf |
| Team invite | `farm_member` | add member with email → links on first login |
| All six species | `animal.species`, `animal_group.species` | |
| INR, kg, litres only | all money/quantity columns | no currency/unit-conversion columns |
| Settings (theme, language, reminders, digest, quiet hours, milk rate, margin %, location) | `farm.settings`, `farm.latitude/longitude` | |

## B. Alerts (your list — 30 alerts)
All alerts are rows in `alert`; thresholds and day counts are `config` rows (ALERT_RULE), so they can be
changed without code. `config.worker_visible` decides whether workers see them.

| Alert | Data read from | Rule |
|---|---|---|
| Heat expected | `farm_event` HEAT | last heat + 21 days |
| Pregnancy check due | `breeding_record` | AI service_date + 45 days, no check yet |
| Calving due soon | `breeding_record.expected_due_date` | due − 7 days |
| Calving overdue | `breeding_record` | due date passed, no calving_date |
| No heat after calving | `breeding_record.calving_date`, `farm_event` HEAT | 60 days, no heat since |
| Long calving interval | `breeding_record` calving dates | > 400 days between calvings |
| Colostrum due | `breeding_record.calving_time`, `colostrum_given_at` | within 2 h of calving |
| Calf milk taper | `animal` (category CALF), `farm_event` CALF_FEEDING | monthly |
| Growth below target | `farm_event` MEASUREMENT, `config` GROWTH_TARGET | weight/height below target for age & breed |
| Weight entry overdue | `farm_event` MEASUREMENT | calves/heifers without a weekly record |
| Weaning reminder | `animal.date_of_birth`, `farm_event` CALF_FEEDING | ≥ 90 days and starter ≥ 1 kg/day |
| Deworming overdue (2–8 weeks) | `config` HEALTH_SCHEDULE, `farm_event` HEALTH | dose missing in window |
| Heifer first AI due | `animal`, `farm_event` MEASUREMENT, `breeding_record` | 250–280 kg or 15 months, no service |
| Deworming due — day 15 | `config` HEALTH_SCHEDULE | DOB + 15 |
| Deworming due — day 30 | `config` HEALTH_SCHEDULE | DOB + 30 |
| Vaccination due | `config` HEALTH_SCHEDULE, `farm_event` HEALTH | per schedule |
| Withdrawal active — don't sell milk | `farm_event.milk_withdrawal_until` | today ≤ until; also blocks milk sale |
| BCS too low / too high | `farm_event.bcs` | < 2.5 / > 4.0 |
| Milk yield drop > 20% | `production_record` | today vs cow's recent average |
| Fat % below 3.5 | `production_record.fat_pct` | per entry |
| High SCC | `production_record.scc` | weekly, above threshold |
| Dry-off due | `breeding_record.expected_due_date`, last calving | X days before calving or after 8 months' lactation |
| Male calf sell reminder | `animal` | MALE, 30–90 days, ACTIVE |
| Cow replacement due | `animal.date_of_birth` | age 8 years |
| Semen stock low | `item` SEMEN, `item_txn` | < 5 straws after use |
| Semen expiry soon | `item.expiry_date` | 30 days before |
| High production cost vs milk income | `finance_entry`, `production_record` | weekly, above `farm.settings` margin % |
| Labour payment pending | `finance_entry` LABOUR status PENDING | weekly |
| Equipment maintenance due | `item.next_service_date` | on/before date |

## C. Reference site 1 (Farmleo)
| Feature | Table(s) |
|---|---|
| Animal list, search, filters, sort | `animal`, `animal_group` |
| Add/edit animal (all fields), species locked | `animal` |
| CSV import/export with 15 columns | `animal` |
| Up to 3 photos | `animal.photo_paths` |
| Status sold/dead/transferred + exit details | `animal.status`, `exit_*` |
| Health record, next-action date, mark done | `farm_event` HEALTH |
| Weight record, method, BCS, weight trend, ADG | `farm_event` MEASUREMENT (+ calc) |
| Breeding record (service, AI/ET, pregnancy check, birth, calf) | `breeding_record` |
| Expected due date auto-filled | `breeding_record` + `config` GESTATION |
| Lineage tree, siblings, offspring, pedigree %, mate-risk | `animal.dam_id/sire_id` (calc) |
| Groups per species, members, colour, batch health | `animal_group`, `animal.group_id`, `farm_event.batch_id` |
| Feed records (usage, purchase, adjustment), stock, days left | `item` FEED, `item_txn` |
| Yield records (milk, manure, meat), farm-level or per animal | `production_record` |
| Finance ledger, auto entries from feed/yield | `finance_entry.source_type/source_id` |
| Farm view: upcoming health actions, due dates, species mix | `alert`, `breeding_record`, `animal` |
| Reports + KPI drill-downs, PDF/CSV | calc |
| Notification settings | `farm.settings` |

## D. Reference site 2 (DairyMate)
| Feature | Table(s) |
|---|---|
| Cow status Milking/Dry/Pregnant/Calf/Bull/Sold | `animal.category` + `animal.status` + `breeding_record.pregnancy_status` |
| Indian breeds quick-pick | `config` (BREED list) or front-end list |
| Purchase cost, weight, mother cow, sire name | `animal`, `farm_event` MEASUREMENT |
| Filters: high SCC, in withdrawal, lactation stage | `production_record`, `farm_event` (calc) |
| Herd milking sheet, pre-filled, ear-tag scan | `production_record` (batch save), `animal.tag` |
| Single-cow milk with fat, protein, SNF, density, SCC | `production_record` |
| Milk grade A–D, 7-day trend, cow curve | calc |
| Health: symptoms, diagnosis, medicine, vet, cost, withdrawal days | `farm_event` HEALTH (+ `item` medicine defaults) |
| Health overview (at risk, withdrawal, events/month), health risk score | calc |
| Breeding: Open/Pregnant/Calved, AI/Natural/ET, heat date | `breeding_record` |
| Heat Watch: flagged cows, heat signs, intensity, AI reminder 12 h | `farm_event` HEAT, `alert` HEAT_SIGN |
| Calendar (heat, breeding, calving, health) | `farm_event`, `breeding_record`, `alert` |
| Feed purchase with reorder level, lead time, supplier contact | `item`, `item_txn` |
| Feeding consumes stock | `item_txn` USAGE |
| Cull advisor scores, lifetime P&L, ROI ranking, compare cows | calc |
| Finance income/expense categories, IOFC | `finance_entry` (+ calc) |
| Manual reminders (type, date, time, cow) | `alert` MANUAL |
| Daily checklist + custom tasks | `config` DAILY_TASK + `alert` CHECKLIST |
| Barn temperature/humidity, THI advice | `farm_event` BARN_READING |
| Milk rate, margin alert %, farm location, digest, quiet hours | `farm.settings` |
| Global search | calc over `animal`, `farm_event`, `finance_entry` |

## E. Your dairy requirements (Villupuram)
| Requirement | Table(s) |
|---|---|
| Heat detection and AI date tracking | `farm_event` HEAT, `breeding_record` |
| Calving / delivery tracking, calf record creation | `breeding_record`, `animal` |
| Deworming and vaccination reminders (your schedule) | `config` HEALTH_SCHEDULE → `alert` |
| Feed planning and cost control (feed master, cost log) | `item` FEED, `item_txn`, `finance_entry`, ration planner (section F) |
| Stock / inventory (feed, semen, medicine) | `item`, `item_txn` |
| P&L — whole farm and per animal | `finance_entry` allocation + `production_record` (calc) |
| Pedigree and history | `animal` parents, `farm_event`, `breeding_record` |
| Labour management (workers, attendance, wages, payment pending) | `farm_member`, `farm_event` ATTENDANCE, `finance_entry` LABOUR |
| Procurement notes | `alert` PROCUREMENT |
| Equipment and maintenance | `item` EQUIPMENT, `item_txn` SERVICE |
| Calf rearing (colostrum, milk, starter, weaning) | `breeding_record.colostrum_given_at`, `farm_event` CALF_FEEDING |
| Herd categories (calf, heifer, milking, dry), replacement and culling | `animal.category`, `exit_reason` |
| Milk AM/PM per cow and farm total | `production_record` |
| QR scan / cow ID search → action forms | `animal.tag` |
| Owner full access, Worker daily entries | `farm_member.role` |

## F. Ration calculation (from your feed design and Excel template)
| Requirement | Table(s) | How |
|---|---|---|
| Feed master with nutrient values and cost | `item` FEED (`details` nutrients) | cost/kg from latest purchase |
| Concentrate formula, 100 kg batch (maize, sorghum, groundnut/coconut/gingelly cake, ulundhu chunni, rice/wheat bran, molasses, salt, soda, mineral mix) | `config` FEED_FORMULA | lines in kg, live 100 kg total check |
| Nutrient-target-driven ingredient control | `config` FEED_FORMULA targets + `item` nutrients | calc: nutrient totals vs targets, warnings |
| Cottonseed cake excluded | `item.details.excluded` | blocked in formulas |
| Gingelly cake stage-wise daily limits | `config` RATION_RULE limits | warning when exceeded |
| Cost summary per batch and per kg (~₹21–27/kg) | `item_txn` FORMULA_BATCH rows | calc |
| Batch preparation updates stock | `item_txn` USAGE + PRODUCE | mix becomes its own stock item |
| BW × 10 % split green / dry / concentrate | `config` RATION_RULE + latest weight | calc per animal |
| Concentrate = 1.5 kg + milk L × 0.4 kg | `config` RATION_RULE + `production_record` | calc |
| Green mix 25 kg (protein fodder 8, grass 12, sorghum/maize 5) and dry 10 kg (straw 6, stalk 4) | `config` RATION_RULE green/dry lines | calc |
| Max concentrate per category (category baseline sheet) | `config` RATION_RULE maxConcKg | calc + warning |
| Daily farm totals and cost per day | calc over all active animals | |
| Feeding at 6 AM / 6 PM (green before milking, concentrate + dry after) | `item_txn` USAGE with time | checklist tasks in `config` DAILY_TASK |
| Planned vs actual feeding, days of stock left, reorder | `item_txn`, `item.reorder_level`, `alert` | calc + alert |

## Not covered by tables (handled elsewhere)
- Subscriptions, mobile-app promotion, IoT/parlour and DHIA integrations from the reference sites — out of scope.
