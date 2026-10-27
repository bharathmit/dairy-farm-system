# API contract — Money & team (Canvas 5)

Base path `/api/v1/farms/{farmId}`. **Everything here is owner only** (403 for workers), except that workers keep
recording the source records (milk sales, purchases, feedings…) exactly as before. Amounts in ₹ (2 decimals).
`AnimalRef` = `{id, tag, name}`, `ContactRef` = `{id, name}`, `MemberRef` = `{id, name}`.

## Storage (Flyway V6)
- `finance_entry` (schema.md §11): direction INCOME|EXPENSE · category · amount · entry_date · entry_time ·
  status PAID|PENDING · payment_method CASH|UPI|BANK_TRANSFER|CARD|CHEQUE|OTHER · counterparty (text) ·
  contact_id · member_id (wages) · period_from / period_to · allocation: alloc_kind FARM|GROUP|ANIMAL, group_id, animal_id ·
  quantity · unit · source_type · source_id · source_part (e.g. TECH_FEE / BULL_FEE) · notes · auto BOOLEAN.
  UNIQUE (farm_id, source_type, source_id, source_part) WHERE source_type IS NOT NULL.
- Categories — INCOME: MILK_SALES, ANIMAL_SALES, MANURE_SALES, OTHER_INCOME · EXPENSE: FEED, LABOUR, VET (vet & medicine),
  BREEDING (AI/bull fees, pregnancy checks), SEMEN, EQUIPMENT, UTILITIES, TRANSPORT, ANIMAL_PURCHASE, OTHER_EXPENSE.
- `attendance` (farm_id, member_id, work_date, status PRESENT|HALF|ABSENT, hours, notes) UNIQUE (member_id, work_date).
- Equipment = `item` (item_type EQUIPMENT) with `details`: `{brand, model, serviceEveryDays, serviceTask, reminderDays,
  purchaseDate, purchaseCost}`; a service = `item_txn` SERVICE (cost, supplier_contact_id = mechanic, notes,
  `details`/new columns for `workDone: ROUTINE|LINER_CHANGE|REPAIR|PARTS|OTHER`, payment_method, paid).
- `procurement_note` (farm_id, item_id, title, quantity, unit, expected_price, needed_by, supplier_contact_id,
  status PLANNED|ORDERED|RECEIVED|CANCELLED, ordered_on, received_on, paid_amount, purchase_txn_id, source
  MANUAL|LOW_STOCK|FEED_NEED, notes).
- Farm settings gain `leaveDaysPerMonth` (default 4) for monthly wages.

## Auto posting (finance entries kept in step with their source)
Each source writes/updates/deletes its own entry (auto = true) in the same transaction; amount 0 or null → no entry.
A V6 backfill creates entries for existing records.
| Source | Entry |
|---|---|
| Milk/manure sale (`production_record`, farm row with income) | INCOME MILK_SALES / MANURE_SALES / OTHER_INCOME; status from `paid`; counterparty = buyer; qty L |
| Item purchase (`item_txn` PURCHASE, incl. opening semen stock) | EXPENSE FEED / SEMEN / VET (medicine) / EQUIPMENT by item type; status from `paid`; supplier |
| Health record `cost` | EXPENSE VET, allocated to the animal (batch: one per animal) |
| Breeding fees | EXPENSE BREEDING, one per part: technician fee, bull fee (status from `feePaid`), pregnancy-check cost; allocated to the cow |
| Animal purchase price / exit price on SOLD | EXPENSE ANIMAL_PURCHASE / INCOME ANIMAL_SALES, allocated to the animal |
| Equipment service cost | EXPENSE EQUIPMENT |
| Wage payment | EXPENSE LABOUR (created by `POST /labour/payments`) |
Auto entries can only change `status`, `paymentMethod`, `notes` here (and are deleted with their source); the
source's own paid flag is updated too when it has one. Manual entries are fully editable.

## Endpoints
| Method | Path | Returns / notes |
|---|---|---|
| GET | `/finance/summary?from=&to=` | `Summary` |
| GET | `/finance/daily?from=&to=` | `[{date, feedCost, milkIncome, otherIncome, otherExpense}]` one per day, zero-filled, max 1 year. feedCost = FEEDING usage × item cost per unit (line cost if set); milkIncome = MILK_SALES entries; otherIncome = other income categories; otherExpense = expenses except FEED (Home money panels) |
| GET | `/finance/entries?from=&to=&direction=&category=&status=&animalId=&page=&size=` | `PageResponse<Entry>` newest first |
| GET / POST / PUT / DELETE | `/finance/entries`, `/finance/entries/{id}` | `Entry`; PUT/DELETE on an auto entry: only status/paymentMethod/notes (DELETE → 409 "Delete it from its source record") |
| GET | `/finance/pnl?scope=FARM\|ANIMAL&from=&to=` | `Pnl` (no dates = lifetime) |
| GET | `/finance/pnl/animals/{animalId}?from=&to=` | `AnimalPnl` |
| GET | `/labour/workers?weekOf=` | `{week:{from,to}, workers:[Worker], totals:{workers, dueNow, paidThisMonth}}` (members with wage type DAILY/MONTHLY, active) |
| GET | `/labour/attendance?weekOf=` | `{week:{from,to}, rows:[{member: MemberRef, wageType, wageRate, days:[{date, status, hours}], presentDays, hours, amount}], total:{days, hours, amount}}` |
| PUT | `/labour/attendance` | `{entries:[{memberId, date, status \| null, hours}]}` → same as GET for that week (`status: null` clears the day) |
| GET | `/labour/workers/{memberId}/due?from=&to=` | `WageDue` (defaults: from = day after last paid period, to = yesterday / end of last week for DAILY, month end for MONTHLY) |
| POST | `/labour/payments` | `{memberId, periodFrom, periodTo, amount, paymentMethod, paidOn, advance (bool), notes}` → 201 `Entry` (LABOUR, PAID; an advance has no period and is deducted from the next due) |
| GET | `/equipment` | `{counts:{items, overdue, dueSoon}, items:[Equipment]}` |
| GET / POST / PUT / DELETE | `/equipment`, `/equipment/{id}` | `Equipment`; request `{name, brand, model, serviceEveryDays, serviceTask, reminderDays, purchaseDate, purchaseCost, notes}` |
| POST | `/equipment/{id}/services` | `{date, workDone, cost, paymentMethod, paid, mechanicContactId, mechanicName, notes, nextServiceDate?}` → 201 `Equipment` |
| GET | `/equipment/{id}/services` | `[Service]` newest first |
| GET | `/procurement?status=` | `{counts:{all, planned, ordered, received}, suggestions:[Suggestion], notes:[Note]}` |
| POST / PUT / DELETE | `/procurement`, `/procurement/{id}` | `Note`; request `{itemId, title, quantity, unit, expectedPrice, neededBy, supplierContactId, notes, source}` |
| POST | `/procurement/{id}/order` | `{orderedOn}` → `Note` |
| POST | `/procurement/{id}/receive` | `{receivedOn, quantity, totalAmount, paymentMethod, paid}` → `Note`; with an item: records an item PURCHASE (stock + auto expense); without: a manual EXPENSE OTHER_EXPENSE |
| POST | `/procurement/from-feed-need?days=7` | creates PLANNED notes (source FEED_NEED) for the feed-need shortfalls not already planned → `[Note]` |

```
Summary = { from, to, income, expense, net, marginPct, marginAlertPct, nearAlert (margin < alert + 5),
  previous: {income, expense, net} (same-length period before), incomeChangePct, expenseChangePct,
  pending: {count, amount}, incomeByCategory:[{category, amount}], expenseByCategory:[{category, amount}],
  feedPctOfMilkIncome }
Entry = { id, direction, category, amount, date, time, status, paymentMethod, counterparty, contact: ContactRef|null,
  member: MemberRef|null, periodFrom, periodTo, alloc: {kind, group:{id,name}|null, animal: AnimalRef|null},
  quantity, unit, source: {type, id, label} | null, auto, notes }
Pnl = { from, to, farm: {income, expense, net, byCategory:[…]},
  animals: [{rank, animal: AnimalRef + {category, breed, lactationNumber, ageDays}, net, note}] }   // ranked by net
AnimalPnl = { animal, from, to, onFarmDays, milkLitres, lines: [{key: MILK|FEED|HEALTH|BREEDING|PURCHASE|SALE|OTHER, label, amount}],
  net, perDay, costPerLitre, marginPct, rank, of }
  // milk income = her saleable litres × the farm's average sale rate in the period (fallback farm milk rate);
  // feed = FEED entries allocated to her + her share of farm/group FEED entries split by head-days;
  // health/breeding/other = entries allocated to her; purchase/sale = her prices.
Worker = { member: MemberRef, wageType, wageRate, hasLogin, week: {presentDays, hours}, dueNow, pendingNote, lastPaid: {periodTo, amount}|null }
WageDue = { member, from, to, presentDays, halfDays, absentDays, rate, gross, advances, amountDue, explain }
  // DAILY: (present + ½ half) × rate. MONTHLY: rate ÷ days in month × days in period, minus absences beyond leaveDaysPerMonth × daily rate.
Equipment = { id, name, brand, model, serviceEveryDays, serviceTask, lastService, nextService, status: OVERDUE|DUE_SOON|OK, daysTo, purchaseDate, purchaseCost, notes }
Service = { id, date, workDone, cost, paymentMethod, paid, mechanic: ContactRef|null, mechanicName, notes }
Suggestion = { item: {id, name, type}, stock, unit, reorderLevel, daysLeft, reason }    // feed REORDER items and semen batches with < 5 straws, not already PLANNED/ORDERED
Note = { id, item: {id, name, type}|null, title, quantity, unit, expectedPrice, neededBy, supplier: ContactRef|null,
  status, orderedOn, receivedOn, paidAmount, source, notes }
```

## Backend notes (as built)
1. **Entry request** (manual): `{direction?, category, amount, date, time?, status, paymentMethod, counterparty,
   contactId?, memberId?, periodFrom?, periodTo?, allocKind: FARM|GROUP|ANIMAL, groupId, animalId, quantity?, unit?,
   notes}`. `direction` may be left out (it follows the category); a mismatch is 400. Future dates are 400.
   For an **automatic** entry `PUT` reads only `{status, paymentMethod, notes}`; anything else sent is ignored.
2. **`Entry.source`** = `{type, id, part, label}` with the page to open: `PRODUCTION` (id = sale id), `ITEM_TXN`
   (id = item id), `EQUIPMENT_SERVICE` (id = equipment id), `HEALTH` (health record id), `BREEDING` (service id;
   part TECH_FEE / BULL_FEE / PD), `ANIMAL` (animal id; part PURCHASE / SALE), `LABOUR` (id = member id, for wage
   payments and advances, which are manual entries). `label` is always filled; other manual entries have no `source`
   (the field is left out when null).
3. Paid status written back: milk sale `paid`, purchase/service `paid` + payment method, breeding `feePaid`
   (TECH_FEE and BULL_FEE share it). Health, pregnancy-check and animal-price entries keep their own status.
4. Semen opening stock posts as a SEMEN expense; the semen used on a service (`semenCost`) is counted in the
   cow's P&L (breeding line), not as a second farm expense. Concentrate batches (PRODUCE) post nothing.
5. `GET /equipment/{id}` added; `Equipment.reminderDays` (default 7). Mechanics must be SUPPLIER or OTHER contacts
   (`mechanicName` for someone not saved). DELETE equipment with services → 409.
6. Wages: default due period starts the day after the last paid period (else joining date / first attendance /
   month start); DAILY ends today, MONTHLY at the month end. `dueNow` on workers is due up to today.
   Monthly absences: full day 1, half day ½, beyond `leaveDaysPerMonth` (farm settings, default 4) deducted at
   monthly rate ÷ days in month. Advances: `advance: true` with no period; the next period payment records
   `min(outstanding advances, gross)` as deducted.
7. P&L `onFarmDays` counts from acquisition (or birth, or record creation) to exit or `to`. Milk rate = the farm's
   average MILK sale rate in the period, else the farm milk rate. A SALE line of 0 means not sold.
8. Procurement `receive` with an item posts a PURCHASE (source_type PROCUREMENT) → stock + automatic expense and
   sets the item's cost per unit; without an item → a manual OTHER_EXPENSE entry. Notes can't be set to RECEIVED
   by PUT (use receive). Counts exclude CANCELLED.
