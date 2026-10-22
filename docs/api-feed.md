# API contract — Feed & ration (Canvas 4)

Base path `/api/v1/farms/{farmId}`. Same conventions as the other contracts: any member reads and records;
**owner only** = item create/edit/delete, formulas, ration rules, every `DELETE`; **money hidden from workers**
(costs, prices, ₹ totals come back `null`; a worker's save with a blank money field keeps the saved value).
Quantities in kg (unit from the item). `ItemRef` = `{id, name, category, unit}`.

## Storage (Flyway V5)
- Feed items reuse `item` (item_type FEED). `category`: CONCENTRATE_INGREDIENT | GREEN_FODDER | DRY_FODDER | SILAGE |
  MINERAL | MIX. `details` JSONB: `dmPct, cpPct, tdnPct, caPct, pPct` (as-fed, per kg), `maxInclusionPct`,
  `excluded`, `excludedReason`.
- Stock moves reuse `item_txn`: PURCHASE (cost, supplier), USAGE (feeding; animal/group allocation), PRODUCE
  (own fodder harvest, or a concentrate batch), ADJUSTMENT (±). V5 adds `item_txn.payment_method`
  (CASH|UPI|BANK_TRANSFER|CARD|CHEQUE|OTHER), `item_txn.paid BOOLEAN`, `item_txn.session` (AM|PM),
  `item_txn.batch_id UUID` (rows saved together).
- New `feed_formula` (farm_id, name, mix_item_id → item MIX, batch_kg, targets JSONB, notes, active) and
  `feed_formula_line` (formula_id, item_id, kg, sort_order).
- New `ration_rule` (farm_id, category CALF|HEIFER|MILKING|DRY|BULL, effective_from DATE, params JSONB),
  UNIQUE (farm_id, category, effective_from). The rule in force on a date = latest effective_from ≤ date;
  no row = the defaults below.
- Purchases are stored for the Finance module (Canvas 6) to post as FEED expenses; no finance rows yet.

## Ration rule (per category)
```
RationParams = { bwPct: 10,                       // total fresh feed/day = body weight × bwPct %
  concBaseKg: 1.5, concPerLitreKg: 0.4, maxConcKg: 10,   // concentrate = base + milk yesterday × perLitre, capped
  concItemId,                                      // usually the MIX item; null = first MIX item
  greenSharePct: 70,                               // of (total − concentrate); the rest is dry fodder
  green: [{itemId, sharePct}], dry: [{itemId, sharePct}],   // split of the green / dry totals (sum 100)
  limits: [{itemId, maxKgPerDay, stage: ANY|EARLY|MID|LATE}],   // EARLY ≤ 100 DIM, MID ≤ 200, LATE after
  dmIntakePct: 3.0, cpPct: 14, tdnPct: 65, caPct: 0.6, pPct: 0.4 }   // nutrient need: DM = BW × dmIntakePct %; CP/TDN/Ca/P = % of DM
```
Defaults: MILKING as above · DRY {concBaseKg 1.5, concPerLitreKg 0, maxConcKg 3, cpPct 12, tdnPct 60} ·
HEIFER {concBaseKg 1.0, perLitre 0, maxConcKg 2, dmIntakePct 2.5, cpPct 14, tdnPct 62} ·
CALF {concBaseKg 0.5, perLitre 0, maxConcKg 1.5, dmIntakePct 2.5, cpPct 18, tdnPct 70} ·
BULL {bwPct 8, concBaseKg 2, perLitre 0, maxConcKg 3, cpPct 10, tdnPct 58}; others as MILKING. Default green/dry lists are
empty: totals still show; item lines appear once the owner picks items.
Body weight = latest weight record, else animal current weight, else estimate (CALF 60, HEIFER 250, MILKING 400,
DRY 420, BULL 500) with `weightSource: MEASURED|ANIMAL|ESTIMATE`. Milk yesterday = cow's AM+PM day total.
Cost/day = Σ kg × item cost per kg. Concentrate ingredient kg/day = concentrate kg × formula line share.

## Endpoints
| Method | Path | Returns / notes |
|---|---|---|
| GET | `/feed/overview?date=` | `{date, sessions:{AM:Session, PM:Session}, needPerDay:{green, dry, concentrate}, plannedCostPerDay, reorderCount, items:[StockItem]}` |
| GET | `/feed/items?category=&includeInactive=` | `[FeedItem]` |
| GET / POST / PUT / DELETE | `/feed/items`, `/feed/items/{id}` | `FeedItem`; DELETE 409 if it has stock moves or is in a formula/rule (make inactive) |
| GET | `/feed/items/{id}/moves?from=&to=` | `[Move]` newest first |
| POST | `/feed/items/{id}/stock` | `{type: HARVEST\|ADJUSTMENT, date, quantity (± for ADJUSTMENT), notes}` → `StockItem` (HARVEST = PRODUCE of own fodder) |
| POST | `/feed/purchases` | `{itemId, date, quantity, costPerUnit \| totalAmount, supplierContactId, paymentMethod, paid, notes}` → 201 `{move: Move, item: StockItem}`; item cost per kg = this price |
| GET | `/feed/purchases?from=&to=&itemId=` | `[Move]` |
| GET | `/feed/feeding-sheet?date=&session=AM\|PM&target=FARM\|GROUP\|ANIMAL&groupId=&animalId=` | `FeedingSheet` |
| PUT | `/feed/feeding-sheet` | `{date, session, time, target, groupId, animalId, lines:[{itemId, kg}], notes}` → `FeedingSheet`; replaces that session's rows for the same target |
| GET | `/feed/formulas` · GET/POST/PUT/DELETE `/feed/formulas/{id}` | `Formula`; 400 if a line uses an excluded item; over-max-inclusion saves with a warning |
| GET | `/feed/formulas/{id}/batch-preview?batchKg=` | `BatchPreview` |
| POST | `/feed/formulas/{id}/batches` | `{date, batchKg, preparedByMemberId, allowShort}` → 201 `{preview: BatchPreview, mix: StockItem}`; 400 when short unless `allowShort`; one USAGE per ingredient + one PRODUCE for the mix (same batch_id, source_type FORMULA_BATCH); mix cost per kg = batch cost ÷ batch kg |
| GET | `/feed/ration?animalId=&date=` | `AnimalRation` |
| GET | `/feed/ration/summary?date=` | `{date, farm: Totals, categories:[{category, animals, totals: Totals}]}` |
| GET | `/feed/need?days=7` | `FeedNeed` |
| GET | `/feed/ration-rules` | `[{category, current: {effectiveFrom, params: RationParams}, upcoming: […], history: […]}]` |
| PUT | `/feed/ration-rules/{category}` | owner; `{effectiveFrom, params}` → saves a version (same date replaces it) |

```
FeedItem = { id, name, category, unit, costPerUnit, dmPct, cpPct, tdnPct, caPct, pPct, maxInclusionPct,
  excluded, excludedReason, reorderLevel, leadTimeDays, supplier: ContactRef|null, notes, active }
StockItem = { item: ItemRef, stock, needPerDay, daysLeft, status: OK|LOW|REORDER|PREPARE|EXCLUDED|UNUSED, barPct, lastPrice, lastPurchaseDate }
  // REORDER: daysLeft ≤ leadTimeDays or stock < reorderLevel; LOW: daysLeft ≤ leadTimeDays + 4; PREPARE: a MIX that is REORDER/LOW; barPct = min(100, daysLeft/30×100)
Session = { logged, kg, plannedKg, time, by }       // by = member name who saved
Move = { id, date, time, type, quantity, cost, costPerUnit, supplier: ContactRef|null, paymentMethod, paid, session,
  target: {kind: FARM|GROUP|ANIMAL, id, label}, sourceType, notes }
FeedingSheet = { date, session, target, label, animals, lines: [{item: ItemRef, section: GREEN|DRY|CONCENTRATE|OTHER,
  plannedKg, actualKg, stock}], plannedKg, actualKg, saved, warnings: [string] }
  // planned = half of the day plan for the target's animals; actualKg = saved value or null; warnings e.g. "Sorghum green 8 kg under plan", "Stock left 25 kg"
Formula = { id, name, mixItem: ItemRef, batchKg, totalKg, costPerKg, batchCost, targets: {dmPct, cpPct, tdnPct, caPct, pPct, costMin, costMax},
  lines: [{item: ItemRef, kg, pct, cost, overMax, maxInclusionPct}],
  nutrients: [{name: DM|CP|TDN|Ca|P, mixPct, targetPct, status: OK|LOW|HIGH|UNKNOWN}],   // ±10 % of target = OK
  warnings: [string], notes }
BatchPreview = { formulaId, batchKg, lines: [{item: ItemRef, needKg, stock, shortKg}], shortCount, batchCost, costPerKg,
  mixStockBefore, mixStockAfter, extraDays }
AnimalRation = { animal: AnimalRef + {category, breed}, date, bodyWeightKg, weightSource, weighedOn, milkYesterday, daysInMilk, stage,
  totals: {greenKg, dryKg, concentrateKg, totalKg, targetKg}, explain: string,   // "body weight × 10 % = 45 kg · concentrate = 1.5 + 18.5 L × 0.4"
  capped, lines: [{section, item: ItemRef|null, kg, timing}], ingredients: [{item: ItemRef, kgPerDay, limitKg, over}],
  costPerDay, nutrients: [{name, ration, need, unit: kg|g, status}], rule: {category, effectiveFrom} }
Totals = { greenKg, dryKg, concentrateKg, costPerDay }
FeedNeed = { animals, perDay: Totals, costPerMonth, concentrateCostPerDay, concentrateCostPerMonth,
  rows: [{section, item: ItemRef, needPerDay, stock, daysLeft, overLeadTime}],
  ingredientRows: [{item: ItemRef, needPerDay, stock, daysLeft, overLeadTime}],    // concentrate need × formula share
  shortfalls: [{item: ItemRef, quantityKg, neededBy, expectedPrice}] }             // within `days`
```

## Breeding change (same round)
`BreedingSettings` gains `pdMinDays` (default 28). `POST /breeding/services/{id}/pregnancy-check` returns 400
"A pregnancy check needs at least 28 days after service — earliest {date}." when `date < serviceDate + pdMinDays`;
the check date can't be in the future either. The UI sets the date input's `min` to that earliest date and shows
"Earliest check: {date} (28 days after service)".

## Backend notes (as built)
1. `GET /feed/items/{id}` returns `FeedItem` plus `stock: StockItem` (the same object as in the overview). In
   lists `stock` is `null`.
2. Workers may call `POST /feed/items/{id}/stock` (harvest or adjust), `POST /feed/purchases`, `PUT /feed/feeding-sheet`,
   `GET .../batch-preview` and `POST /feed/formulas/{id}/batches`. Item create/edit, formulas, ration rules and
   every `DELETE` are owner-only.
3. Formula request: `{name, mixItemId, batchKg, targets:{dmPct, cpPct, tdnPct, caPct, pPct, costMin, costMax},
   lines:[{itemId, kg, sortOrder}], notes, active}`. Lines are stored in `sortOrder` order (missing = last).
   The mix item must be category MIX; an excluded ingredient → 400
   "{name} can't be added. It is marked Excluded — {reason}."; over max inclusion → saved, listed in `warnings`.
4. Prepare batch: missing `preparedByMemberId` = the caller's own team member. The name is kept in the move notes
   ("Batch of {formula} · prepared by {name}"). Short stock → 400 "Not enough stock: {item} is short by {n} kg …"
   unless `allowShort`.
5. A feeding-sheet save replaces **all** of that session's rows for the target: lines left out (or 0 kg) are removed.
   An excluded item → 400. Without `time`, the save time (farm clock) is used.
6. `daysLeft` is a number of days (1 decimal), or `null` when the need is 0. `barPct` is always returned
   (need 0: 100 with stock, else 0). Lead time defaults to 2 days when not set.
7. Costs (`costPerDay`, `plannedCostPerDay`, totals) are `null` when any planned part has no item picked or no
   price yet, rather than a partial sum.
8. Ration rules are stored with the category defaults filled in. Shares (green, dry) must add up to 100 %; green
   items must be GREEN_FODDER/SILAGE, dry items DRY_FODDER/SILAGE, `concItemId` a MIX or concentrate ingredient.
   `PUT /feed/ration-rules/{category}` returns the full `[RuleSet]` list. `current.effectiveFrom` is absent/null
   when the defaults apply.
9. Planned kg on the feeding sheet = half of the day's plan per item, 1 decimal; ration parts with no item picked
   are not on the sheet and add the warning "Some ration parts have no feed items picked yet …".
10. Supplier contacts (`supplierContactId` on items and purchases) must have the role SUPPLIER. A purchase without a
    supplier uses the item's supplier.
11. Animals planned: active cows and buffalo in categories CALF, HEIFER, MILKING, DRY, BULL.
