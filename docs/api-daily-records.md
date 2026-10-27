# API contract — Daily records (Canvas 2)

Shared contract for the backend and frontend work on milk, weight/BCS, health and contacts.
Base path: `/api/v1/farms/{farmId}`. Dates are `YYYY-MM-DD`; litres/kg are numbers with 1–2 decimals.
Any farm member may read and record. **Owner only:** `PUT /milk/grading`, every `DELETE`.
**Money is hidden from workers** (cost, rate, incomeAmount, milkRate come back `null` for a WORKER).
When a worker saves a record with a hidden money field left `null` (contact milkRate, health cost, sale rate),
the saved value is kept.
Errors are problem+json as before (`detail`, and `errors.{field}` for validation).

## Contacts (buyers, vets, AI technicians, suppliers)
| Method | Path | Notes |
|---|---|---|
| GET | `/contacts?role=BUYER\|VET\|AI_TECH\|SUPPLIER\|OTHER&active=true` | both filters optional, sorted by name |
| GET | `/contacts/{id}` | |
| POST | `/contacts` | 201; 409 if the name already exists |
| PUT | `/contacts/{id}` | |
| DELETE | `/contacts/{id}` | owner; 409 "used on records — make it inactive instead" |

`Contact` = `{id, name, roles: [BUYER|VET|AI_TECH|SUPPLIER|OTHER], phone, village, milkRate, payEvery: DELIVERY|WEEK|TEN_DAYS|FORTNIGHT|MONTH, notes, active}`.
Request is the same without `id` (`name`, `roles` required). `ContactRef` = `{id, name}`.

## Milk grade rules
`GET /milk/grading` → rules with defaults filled in. `PUT /milk/grading` (owner) → saves and returns them.
```
{ basis: OWN_LACTATION|BREED,
  breeds: [{breed:"HF", peakLitres:18, peakDay:50}, …], otherBreeds: {breed:"Other", peakLitres:10, peakDay:50},
  startPct: 70, fallPctPerMonth: 8, firstCalverPct: 75, lactationDays: 305,
  bandA: 100, bandB: 90, bandC: 75,
  minFatPct: {enabled:true, limit:3.2}, minSnfPct: {enabled:true, limit:8.0}, maxSccK: {enabled:true, limit:200},
  dropAlertPct: 20 }
```
Expected litres/day: day 1 = startPct % of peak, rising in a straight line to the peak on peakDay, then
× (1 − fallPctPerMonth/100) per 30.4 days. Peak = cow's best day in her previous lactation (basis OWN_LACTATION,
when known) else breed value (× firstCalverPct % for a first-calver: aged under 30 months at calving).
Days in milk = date − animal.lastCalvingDate + 1. After lactationDays → no grade (`pastLactation`).
Grade from actual day total ÷ expected: A ≥ bandA, B ≥ bandB, C ≥ bandC, else D; each failed quality check
drops one grade. Only days with both AM and PM logged are graded; withheld days are not graded.

## Milk
`Session` = `AM | PM` (farm totals/sales may also use `DAY`).

`CowDay` = `{date, animalId, tag, name, am, pm, total, withheld, daysInMilk, expectedLitres, pctOfExpected,
grade: A|B|C|D|null, qualityIssues: [string], fatPct, snfPct, proteinPct, density, scc, notes}`

| Method | Path | Returns |
|---|---|---|
| GET | `/milk/day?date=` | `DaySummary` (MilkHome; call with yesterday for the cow table) |
| GET | `/milk/trend?from=&to=` | `[{date, amLitres, pmLitres, totalLitres, withheldLitres}]` (one per day, zero-filled) |
| GET | `/milk/grades?from=&to=&session=` | `{A, B, C, D}` counts of graded cow-days; with `session=AM\|PM` counts graded cow-sessions (session litres vs half the expected day, that session's quality; withheld not graded) |
| GET | `/milk/sheet?date=&session=AM\|PM&groupId=` | `Sheet` (groupId optional) |
| PUT | `/milk/sheet` | body `{date, session, entries:[{animalId, litres}]}`; `litres:null` = skipped (removes that record). 409 when that session's milk is sold. Returns `Sheet` |
| GET | `/milk/cows/{animalId}/days/{date}` | `CowDayForm` (MilkSingle) |
| PUT | `/milk/cows/{animalId}/days/{date}` | body `{am, pm, fatPct, snfPct, proteinPct, density, scc, notes, sessions:[AM\|PM]}`; only listed sessions are saved (none/empty = both); null am/pm removes a listed session. 409 when a listed session is sold. Returns `CowDayForm` |
| GET | `/milk/cows/{animalId}?from=&to=` | `[CowDay]` newest first |
| GET | `/milk/sales?from=&to=` | `[Sale]` newest first |
| PUT | `/milk/sales` | upsert by (date, session, product). Milk: 409 when mixing a DAY sale with an AM/PM sale on the same date. Returns `Sale` |
| DELETE | `/milk/sales/{id}` | owner |

```
DaySummary = { date,
  am: {litres, cows, withheldLitres}, pm: {litres, cows, withheldLitres}, totalLitres,
  yesterday: {amLitres, pmLitres, totalLitres},
  change: {amPct, pmPct, totalPct},          // null when either side is missing
  expectedLitres,                            // sum of expected for milking cows with a calving date
  milkingCows,                               // active female MILKING animals
  cows: [CowDay] }                           // milking cows plus any cow with a record that day
Sheet = { date, session, saved, sessionLitres, otherSessionLitres, dayLitres, withheldLitres,
  cows: [{animalId, tag, name, groupId, litres, lastLitres, usualLitres, inWithdrawal, withdrawalUntil, stage: FRESH|EARLY|MID|LATE}],
  sold }   // sold = a milk sale exists for this date+session (or DAY): the log is locked
  // saved = any cow record exists for this date+session (tells "skipped" from "not logged yet");
  // litres = saved value or null; lastLitres = her last record for this session before date;
  // usualLitres = average of her last 7 records for this session (frontend warns at −dropAlertPct %)
CowDayForm = { animal: {id, tag, name, breed, lastCalvingDate},
  day: CowDay, expectation: {daysInMilk, expectedLitres, peakLitres, peakDay, source: OWN_LACTATION|BREED|FIRST_CALVER, pastLactation} | null,
  lastAm, lastPm, inWithdrawal, withdrawalUntil, amSold, pmSold }
Sale = { id, date, session, product: MILK|MANURE|OTHER, quantity, cowLitres, withheldLitres,
  soldQty, otherUseQty, rate, incomeAmount, buyer: ContactRef|null, fatPct, snfPct, paid, notes, discount }
```
Sale request = `{date, session, product (default MILK), quantity (null = use cow-logged litres for that session/day),
soldQty, buyerContactId, rate (default buyer's milkRate, then farm milkRate), fatPct, snfPct, paid, notes, discount}`.
OTHER produce: soldQty = number of units (also the total), rate = unit price, incomeAmount = units × price − discount
(400 when the discount is more than units × price); unit NOS. Milk rate max ₹1000/L. discount is null for workers.
400 when soldQty > quantity − withheld litres.
Cow milk rules: animal must be an active female in category MILKING (400 otherwise). A cow record is
`withheld` when the session time (farm settings morning/evening milking time) is before an active
`milkWithdrawalUntil`.

## Weight, height & BCS
| Method | Path | Notes |
|---|---|---|
| GET | `/animals/{animalId}/measurements` | `Growth` |
| POST | `/animals/{animalId}/measurements` | 201 `Measurement`; updates the animal's current weight when it is the latest |
| PUT | `/measurements/{id}` | |
| DELETE | `/measurements/{id}` | owner |
```
Measurement = {id, animalId, date, method: SCALE|TAPE|ESTIMATE, weightKg, heightCm, heartGirthCm, bcs, notes,
               gainKg, adgKg}   // gain/ADG vs the previous record with a weight
Growth = { animal: {id, tag, name, category, dateOfBirth}, latest: {date, weightKg, heightCm, bcs},
           adg90dKg, items: [Measurement] }   // newest first
```
Request = `{date, method, weightKg, heightCm, heartGirthCm, bcs (1–5, step 0.25), notes}`; at least one of
weight/height/BCS.

## Health
| Method | Path | Notes |
|---|---|---|
| GET | `/health/overview` | `{inWithdrawal, openFollowUps, thisMonth, dueFollowUps:[HealthRecord], recent:[HealthRecord]}` (due = open follow-ups up to today+7, overdue included; recent = last 10) |
| GET | `/health/records?animalId=&type=&from=&to=&page=&size=` | `PageResponse<HealthRecord>` newest first |
| GET | `/health/records/{id}` | |
| POST | `/health/records` | 201 |
| POST | `/health/records/batch` | body = request + `animalIds:[…]` → `{batchId, count, records}` |
| PUT | `/health/records/{id}` | |
| PUT | `/health/records/{id}/follow-up` | `{done: true|false}` |
| DELETE | `/health/records/{id}` | owner |
| GET | `/health/withdrawals` | `{active:[Withdrawal], ended:[Withdrawal]}` (ended = last 14 days) |
```
HealthRecord = { id, animalId, tag, name, date, type: ILLNESS|TREATMENT|VACCINATION|DEWORMING|INJURY,
  symptoms, diagnosis, medicine, dose, route, vet: ContactRef|null, cost,
  milkWithdrawalDays, meatWithdrawalDays, milkWithdrawalUntil (ISO date-time), meatWithdrawalUntil (date),
  nextActionDate, nextAction, nextActionDone, batchId, notes }
Withdrawal = { record: HealthRecord, endsAt, discardedLitres }
```
Request = `{animalId, date, type, symptoms, diagnosis, medicine, dose, route, vetContactId (must have role VET),
cost, milkWithdrawalDays, meatWithdrawalDays, nextActionDate, nextAction, notes}`.
milkWithdrawalUntil = (date + milkWithdrawalDays) at the farm's evening milking time (Asia/Kolkata);
meatWithdrawalUntil = date + meatWithdrawalDays. 0 or null days = no withdrawal.
