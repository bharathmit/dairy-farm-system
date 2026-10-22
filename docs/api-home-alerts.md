# API contract — Home, alerts & reports (Canvas 6)

Base path `/api/v1/farms/{farmId}`. Any member reads home, checklist and the alerts visible to them (workers see
only `workerVisible` alerts and no money); **owner only** = alert rules, health schedules, reports, cull advisor,
compare cows, dismissing rule alerts. `AnimalRef` = `{id, tag, name}`, `MemberRef` = `{id, name}`.

## Storage (Flyway V7)
- `alert` (schema.md §12): source RULE|REMINDER|PROCUREMENT · rule_code · category REPRODUCTION|CALF|HEALTH|MILK|FEED|FINANCE_OPS ·
  severity HIGH|MEDIUM|INFO · title · detail · animal_id · related_type / related_id · due_date · due_time ·
  worker_visible · status OPEN|DONE|DISMISSED · snoozed_until (timestamptz) · done_by (member) · done_at ·
  repeat NONE|DAILY|WEEKLY|MONTHLY (reminders) · reminder_type HEALTH|BREED|FEED|VET|OTHER · details JSONB `{why, facts:[{label, value}]}`.
  UNIQUE (farm_id, rule_code, animal_id, related_type, related_id, due_date) WHERE source = 'RULE'.
- `farm.alert_rules` JSONB: `{<CODE>: {enabled, workerVisible, params}}` (null = defaults).
- `health_schedule` (farm_id, type DEWORMING|VACCINATION, name, applies_to CALVES_HEIFERS|ALL|MILKING, medicine,
  steps JSONB `[{label, atAgeDays, repeatEveryDays, untilAgeDays}]`, alert_days_before, worker_visible, active).
  Health records gain `details.scheduleId` (+ step) when recorded from a schedule alert.
- `checklist_task` (farm_id, name, repeat_daily, worker_visible, active, sort_order) and
  `checklist_tick` (farm_id, task_key, tick_date, member_id, ticked_at) UNIQUE (farm_id, task_key, tick_date).
- `farm_event` BARN_READING rows: `details.temperatureC, humidityPct, thi`.
- `animal.cull_keep_until DATE` ("Keep anyway" for 90 days).

## Alert rules (codes, defaults, where the numbers live)
Shared numbers are stored once: rules marked ⇄ read and write the existing setting.
| Code | Category · severity | Default params |
|---|---|---|
| HEAT_EXPECTED | REPRODUCTION · INFO | ⇄ breeding `heatCycleDays` 21 |
| PD_DUE | REPRODUCTION · HIGH when overdue | ⇄ breeding `pdAfterDays` 45 |
| CALVING_SOON | REPRODUCTION · MEDIUM | `daysBefore` 7 |
| NO_HEAT_AFTER_CALVING | REPRODUCTION · MEDIUM | `days` 60 |
| LONG_CALVING_INTERVAL | REPRODUCTION · INFO | `days` 400 |
| COLOSTRUM_DUE | CALF · HIGH | ⇄ breeding `colostrumHours` 2 |
| WEANING | CALF · INFO | ⇄ breeding `weanMinDays` 90, `weanStarterKg` 1 |
| HEIFER_FIRST_AI | CALF · INFO | ⇄ breeding `heiferFirstServiceKg` 250, `heiferFirstServiceMonths` 14 |
| WEIGHT_OVERDUE | CALF · MEDIUM | `everyDays` 7 (calves & heifers) |
| BCS_RANGE | HEALTH · MEDIUM | `min` 2.5, `max` 4.0 |
| WITHDRAWAL | HEALTH · HIGH | always on |
| HEALTH_FOLLOW_UP | HEALTH · MEDIUM | open health next actions |
| SCHEDULE_DUE | HEALTH · MEDIUM | from health schedules (`alert_days_before`) |
| YIELD_DROP | MILK · MEDIUM | ⇄ milk grading `dropAlertPct` 20 (session vs her 7-record avg) |
| LOW_FAT | MILK · INFO | `minFatPct` 3.5 |
| HIGH_SCC | MILK · MEDIUM | `maxSccK` 200 |
| DRY_OFF_DUE | MILK · MEDIUM | ⇄ breeding `dryOffDaysBeforeDue` 60 |
| FEED_REORDER | FEED · MEDIUM | feed StockItem REORDER |
| SEMEN_LOW | FINANCE_OPS · MEDIUM | `minStraws` 5 |
| REPLACEMENT_AGE | FINANCE_OPS · INFO | `ageYears` 8 |
| LOW_MARGIN | FINANCE_OPS · HIGH (owner only) | ⇄ farm `marginAlertPct` 30 (last 7 days) |
| EQUIPMENT_SERVICE | FINANCE_OPS · MEDIUM | equipment due within `reminderDays` |
| PROCUREMENT_DUE | FINANCE_OPS · INFO | planned/ordered notes needed within 2 days |
Generation: a daily job at 05:00 farm time plus `POST /alerts/refresh`, and lazily when alerts are read and the
farm's last run is over 1 h old (and after saves that affect rules is fine but optional). Each run upserts current
hits and auto-closes OPEN rule alerts whose condition no longer holds (status DONE, done_by null, `autoClosed: true`).

## Endpoints
| Method | Path | Returns / notes |
|---|---|---|
| GET | `/home` | `Home` (shape by role) |
| GET | `/checklist?date=` | `{date, done, total, builtIn:[Task], custom:[Task]}` |
| PUT | `/checklist/{taskKey}?date=` | `{done: true\|false}` → same as GET (built-in auto tasks can't be unticked) |
| POST / PUT / DELETE | `/checklist/tasks`, `/checklist/tasks/{id}` | owner; `{name, repeatDaily, workerVisible}` |
| POST | `/barn-readings` | `{date, time, temperatureC, humidityPct}` → 201 `Barn` |
| GET | `/barn-readings/latest` | `Barn` or 204 |
| GET | `/alerts?category=&severity=&status=OPEN&animalId=` | `{counts:{all, byCategory:{…}, bySeverity:{…}, overdue}, overdue:[Alert], today:[Alert], thisWeek:[Alert], later:[Alert]}` (snoozed hidden until due) |
| GET | `/alerts/{id}` | `Alert` |
| POST | `/alerts/{id}/done` · `/alerts/{id}/dismiss` · `/alerts/{id}/snooze` `{until}` | → `Alert` (dismiss of RULE alerts owner only; a repeating reminder creates its next occurrence when done) |
| POST | `/alerts/refresh` | → `{created, closed}` |
| POST / PUT / DELETE | `/reminders`, `/reminders/{id}` | `{title, notes, date, time, type, animalId, workerVisible, repeat}` → `Alert` |
| GET / PUT | `/alerts/rules` | owner; `[Rule]` (PUT takes the same list; ⇄ params are written to their settings) |
| GET / POST / PUT / DELETE | `/health-schedules`, `/health-schedules/{id}` | owner writes; `Schedule`. Defaults (code): calf deworming day 15, day 30, every 60 d to 180 d, every 90 d to 365 d, yearly after; FMD at 120 d then every 180 d |
| GET | `/reports/summary?from=&to=` | owner; `{milkLitres, milkChangePct, netProfit, income, feedCostPerLitre, cull:{cull, monitor}}` |
| GET | `/reports/{type}.csv?from=&to=` | owner; type = milk \| cows \| breeding \| feed \| health \| pnl; `text/csv` download |
| GET | `/reports/{type}?from=&to=` | owner; same data as JSON `{title, period, columns:[{key,label}], rows:[{…}], totals:{…}}` (the UI prints it as PDF) |
| GET | `/reports/cull-advisor` | owner; `{counts:{keep, monitor, dryOff, cull}, cows:[CullRow]}` |
| POST | `/reports/cull-advisor/{animalId}/keep` | owner; sets keep-until today+90 → `CullRow` |
| GET | `/reports/compare?a=&b=` | owner; `{a: CowCard, b: CowCard, days:[date]}` |

```
Home = { role, greeting (name), date,
  tiles: { milkToday, milkChangePct, milkCompareNote, milkingCows, dryCows, herd, alertsOpen, alertsOverdue,
           marginMonthPct, marginAlertPct },                       // margin owner only
  tasks: [{key, title, status: DONE|DUE|LATE|PENDING, detail, time, link: {kind, id}}],   // milking AM/PM, feeding AM/PM, top 3 due alerts
  checklist: {done, total}, barn: Barn|null,
  topProducers: [{animal, avgLitres7d}] (owner), upcomingCalvings: [{animal, dueDate, days, note}],
  healthReminders: [Alert] (worker), feedingReminders: [{title, detail, status}] (worker) }
Task = { key, name, builtIn, done, doneBy: MemberRef|null, doneAt, detail, auto, workerVisible, link }
  // built-in keys: MILK_AM, MILK_PM, FEED_AM, FEED_PM (auto-done when recorded), FEED_STOCK, HEAT_STRESS (auto when a barn reading exists today)
Barn = { date, time, temperatureC, humidityPct, thi, level: NONE|MILD|MODERATE|SEVERE|EMERGENCY, advice, yieldEffect }
  // THI = (1.8T+32) − (0.55 − 0.0055·RH)(1.8T − 26); <72 none, 72–78 mild–moderate, 79–88 severe, >88 emergency
Alert = { id, source, ruleCode, category, severity, title, detail, animal: AnimalRef + {category, breed}|null,
  dueDate, dueTime, overdueDays, status, snoozedUntil, workerVisible, repeat, type,
  why, facts:[{label, value}], action: {kind: LOG_HEAT|LOG_SERVICE|LOG_PD|LOG_CALVING|LOG_WEIGHT|LOG_HEALTH|OPEN_FEED|OPEN_PROCUREMENT|OPEN_EQUIPMENT|NONE, label, params},
  doneBy, doneAt, autoClosed }
Rule = { code, category, title, enabled, workerVisible, params:{…}, alwaysOn }
Schedule = { id, type, name, appliesTo, medicine, steps, alertDaysBefore, workerVisible, active }
CullRow = { animal: AnimalRef + {breed, ageYears, lactationNumber, daysInMilk, category}, verdict: KEEP|MONITOR|DRY_OFF|CULL,
  score, production, reproduction, health, reasons:[string], lifetimeNet, last90Net, keepUntil }
  // production = 90-d avg vs expected yield (milk grading) → 0–100; reproduction = pregnant 100, else falls with days open
  // past target and failed services; health = 100 − treatments/cost/SCC/BCS penalties in 90 d; age past REPLACEMENT_AGE −15.
  // score = 0.45 P + 0.35 R + 0.2 H. Verdict: pregnant with dry-off due → DRY_OFF; score < 40 → CULL; < 65 or an open
  // HEALTH/MILK alert → MONITOR; else KEEP. keepUntil in future → never CULL.
CowCard = { animal, breed, status, daysInMilk, stage, litres7d:[number|null], avgLitres, trendPct, fat30d,
  health:{count, cost}, activeIssue, breeding: string, lifetimeNet }
```
