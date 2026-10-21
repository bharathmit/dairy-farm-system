# Alert requirements (from owner, 2026-10-21)

The system generates alerts automatically (daily job + on record save).
**Owner sees all alerts. Worker sees health and feeding reminders only.**
All day counts and thresholds are stored in `alert_rule` so they can be changed without code.

Columns: trigger as given by the owner · data the alert needs · visibility (O = Owner, W = Worker too).

## 🔴 Reproduction
| Alert | Trigger | Data needed | Vis |
|---|---|---|---|
| Heat expected | 21 days after last heat | `breeding_event` heat date | O |
| Pregnancy check due | 45 days after AI | service date, method | O |
| Calving due soon | 7 days before expected date | expected due date | O |
| Calving overdue | past expected date, no calving recorded | due date, calving date | O |
| No heat after calving | 60 days post-calving, no heat recorded | calving date, heat dates | O |
| Long calving interval | > 400 days between calvings | calving history | O |

## 🐄 Calf & heifer growth
| Alert | Trigger | Data needed | Vis |
|---|---|---|---|
| Colostrum due | at calving — give within 2 hours | calving time, colostrum given (time) | O, W |
| Calf milk taper reminder | monthly | calf feeding plan | O, W |
| Growth below target | weight and height below age- or breed-specific target | weight **and height** record, `growth_target` table | O |
| Weight entry overdue | no weekly weight + height record (calves/heifers) | last measurement date | O, W |
| Weaning reminder | from 90 days of age, once eating ≥ 1 kg calf starter/day | age, daily starter intake | O, W |
| Deworming overdue | 2–8 weeks of age, not done | health record (deworming) | O, W |
| Heifer first AI due | weight 250–280 kg **or** age 15 months | weight, age, no service yet | O |

## 💉 Health
| Alert | Trigger | Data needed | Vis |
|---|---|---|---|
| Deworming due — calf day 15 | 15 days from birth | DOB, schedule | O, W |
| Deworming due — calf day 30 | 30 days from birth | DOB, schedule | O, W |
| Vaccination due | per schedule | `health_schedule` | O, W |
| Withdrawal period active — do NOT sell milk | after treatment, until withdrawal end | medicine, **withdrawal days** → end date | O, W |
| BCS too low | < 2.5 (monthly weight entry) | BCS | O, W |
| BCS too high | > 4.0 (monthly weight entry) | BCS | O, W |

## 🥛 Milk production
| Alert | Trigger | Data needed | Vis |
|---|---|---|---|
| Sudden yield drop | > 20% drop, same day (vs cow's recent average) | per-cow milk records | O |
| Low fat % | < 3.5 — quality penalty risk | fat % (daily) | O |
| High SCC — mastitis risk | weekly SCC above threshold | **SCC** (weekly) | O, W |
| Dry-off due | 3 months before calving **or** after 8 months of lactation | due date, last calving date | O |

## 💰 Financial & operations
| Alert | Trigger | Data needed | Vis |
|---|---|---|---|
| Male calf sell reminder | age 30–90 days, still active | sex, DOB, status | O |
| Cow replacement due | age 8 years | DOB | O |
| Semen stock low | < 5 straws (checked on use) | **semen inventory** | O |
| Semen expiry soon | 30 days before expiry | semen batch expiry | O |
| High production cost vs milk income | weekly check | finance totals | O |
| Labour payment pending | weekly | **labour wages/payments** | O |
| Equipment maintenance due | as scheduled | **equipment + maintenance schedule** | O |

## Questions
1. Which alerts count as **"feeding reminders"** for workers? I marked colostrum, milk taper,
   weaning and weight-entry-overdue as worker-visible. Please confirm.
2. **Dry-off:** you wrote 3 months before calving; the common practice is about 60 days. Keep 90 as the
   default (it's configurable either way)?
3. **Deworming:** the reference schedule earlier was 15d → 30d → every 2 months to 6 m → every 3 months
   to 1 y → yearly. The alert list says "2 to 8 weeks of age". Treat the 15/30-day doses as the
   2–8 week window?
4. **SCC threshold:** what value should count as "high" (e.g. > 200,000 cells/ml)?
5. **Fat % threshold:** same 3.5 for HF and Jersey, or separate per breed?
6. **Growth targets:** do you have target weight/height by age and breed, or should I propose a table?
7. **Alert delivery:** in-app list + bell only, or also push/WhatsApp/SMS later?
