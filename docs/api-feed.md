# API contract — Feed & ration (Canvas 4)

Base path `/api/v1/farms/{farmId}`. Same conventions as the other contracts: any member reads and records;
**owner only** = item create/edit/delete, formulas, ration rules, every `DELETE`; **money hidden from workers**
(costs, prices, ₹ totals come back `null`; a worker's save with a blank money field keeps the saved value).
Quantities in kg (unit from the item). `ItemRef` = `{id, name, category, unit}`.

## Storage (Flyway V5)
- Feed items reuse `item` (item_type FEED). `category`: CONCENTRATE_INGREDIENT | GREEN_FODDER | DRY_FODDER | SILAGE |
  MINERAL | MIX. `details` JSONB: `dmPct, cpPct, tdnPct, caPct, pPct` (as-fed, per kg), BIS quality values
  `fatPct, fibrePct, aiaPct` (acid insoluble ash) and `aflatoxinPpb` (as fed), `maxInclusionPct`, `excludedReason`.
  **Max inclusion 0 % = excluded** (blocked in every formula and ration). The older `excluded` flag is still honoured on
  read; a request with `excluded: true` is saved as max inclusion 0. Responses give `maxInclusionPct: 0` for excluded items.
- Stock moves reuse `item_txn`: PURCHASE (cost, supplier), USAGE (feeding; animal/group allocation), PRODUCE
  (own fodder harvest, or a concentrate batch), ADJUSTMENT (±). V5 adds `item_txn.payment_method`
  (CASH|UPI|BANK_TRANSFER|CARD|CHEQUE|OTHER), `item_txn.paid BOOLEAN`, `item_txn.session` (AM|PM),
  `item_txn.batch_id UUID` (rows saved together).
- New `feed_formula` (farm_id, name, mix_item_id → item MIX, batch_kg, targets JSONB, notes, active) and
  `feed_formula_line` (formula_id, item_id, kg, sort_order).
- New `ration_rule` (farm_id, category = feeding group, effective_from DATE, params JSONB) — V5 had
  CALF|HEIFER|MILKING|DRY|BULL; V8 moved it to the 10 feeding groups below,
  UNIQUE (farm_id, category, effective_from). The rule in force on a date = latest effective_from ≤ date;
  no row = the defaults below.
- Category MIX is shown as **"Mix targets"**: the item holds the nutrient targets (as fed) its formula is compared
  against. Its stock comes from batches, purchases (ready-made feed) and harvest/adjustment moves, and is used by
  feeding; the item page shows stock, Buy, Add / adjust, cost, use limits, reorder and supplier like other items.
- Purchases are stored for the Finance module (Canvas 6) to post as FEED expenses; no finance rows yet.

## Feeding groups (V8, `feed/FeedGroups`)
Each active animal is placed in one group every day, checked in this order. The group picks the ration rule and is
shown as a badge on animals (for shed grouping and feed management). Groups are calculated, not stored.
| Group | Who |
|---|---|
| FRESH — Fresh cow | milking, days 1–21 after calving |
| EARLY — Early lactation | milking, days 22–100 |
| MID — Mid lactation | milking, days 101–200 (also a milking cow with no calving date) |
| LATE — Late lactation | milking, day 201 until dry-off |
| CLOSE_UP — Close-up | confirmed pregnant dry cow / heifer, ≤ 21 days to `expected_due_date` (or overdue) |
| STEAM_UP — Steaming-up | confirmed pregnant dry cow / heifer, 22–60 days to due date |
| DRY — Dry & mature heifers | other dry cows; heifers ready for first service (age ≥ breeding setting `heiferFirstServiceMonths` or recorded weight ≥ `heiferFirstServiceKg`; estimates ignored) |
| HEIFER — Growing heifers | female young stock 9 months until ready for first service |
| CALF — Calves | under 9 months; male calves; a CALF with no birth date |
| BULL — Bulls | category BULL |
Pregnancy = latest breeding record with `pregnancy_status` PREGNANT and no `calving_date`. A milking cow stays in her
lactation group even when pregnant. V8 copied existing rules: MILKING → FRESH, EARLY, MID, LATE; DRY → CLOSE_UP, STEAM_UP.

## Ration rule (per feeding group) — TMR, dry-matter based
```
RationParams = {
  dmiEarlyPct: 3.75, dmiMidPct: 3.25, dmiLatePct: 2.75,         // DMI % of BW for milking cows by stage (EARLY ≤ 100 DIM, MID ≤ 200, LATE after)
  dmiAge3to6Pct: 3.00, dmiAge6to12Pct: 2.75, dmiAge12to24Pct: 2.50,  // HEIFER / CALF by age from date_of_birth (12+ months uses the last)
  dmIntakePct: 3.25,                                             // base DMI %: other categories, no calving date, calves < 3 months, no birth date
  greenDmPct: 45, dryDmPct: 15, concDmPct: 40,                   // TMR shares of DMI (guide: green 35–45, dry 15–25, concentrate 25–40; sum 100)
  concMode: MILK|DMI,                                            // MILK: concentrate = base + milk yesterday × perLitre; DMI: = DMI × concDmPct ÷ its DM %
  concBaseKg: 1.5, concPerLitreKg: 0.4, maxConcKg: 10,           // cap applies in both modes
  concItemId,                                                    // usually the MIX item; null = first MIX item
  green: [{itemId, sharePct, fixedKg?}], dry: [{itemId, sharePct, fixedKg?}],  // sharePct = % of that part's DM (non-fixed rows sum 100)
  limits: [{itemId, maxKgPerDay, stage: ANY|EARLY|MID|LATE}],
  waterBaseL: 30, waterPerLitreL: 4,                             // water L/day = base + milk × perLitre
  cpPct: 14, tdnPct: 65, caPct: 0.6, pPct: 0.4 }                 // nutrient need as % of DMI
```
Calculation: DMI = BW × DMI %. Concentrate fresh kg by `concMode`, capped; its DM = kg × the mix's DM % (from the
formula when the item has none). DM left = DMI − concentrate DM, split green : dry by greenDmPct : dryDmPct. In each part,
`fixedKg` rows (optional feeds such as hydroponic fodder 7 kg, azolla 1 kg) are fed first and their DM comes off the part;
they are skipped when the item is inactive or excluded. The DM left is split by share and each item's fresh kg = DM ÷ the
item's DM %. Items without a DM % use green 30 %, dry 90 %, concentrate 80 %. Old keys `bwPct`, `greenSharePct` are ignored.

Defaults (FRESH/EARLY/MID/LATE use MILKING; CLOSE_UP/STEAM_UP use DRY): MILKING {MILK, conc 1.5 + 0.4/L max 10, 45/15/40, base DMI 3.25, water 30 + 4/L, CP 14, TDN 65} ·
DRY {DMI, max 3, 45/25/30, DMI 2.00, CP 12, TDN 60} · HEIFER {DMI, base 1.0 max 2, 45/25/30, DMI 2.50, water 20, CP 14, TDN 62} ·
CALF {DMI, base 0.5 max 1.5, 40/20/40, DMI 3.00, water 10, CP 18, TDN 70} · BULL {DMI, base 2 max 3, 45/25/30, DMI 2.00, CP 10, TDN 58}.
Stage and age bands default to 3.75/3.25/2.75 and 3.00/2.75/2.50 in every category. Default green/dry lists are
empty: totals still show; item lines appear once the owner picks items.
Body weight = latest weight record, else animal current weight, else estimate (CALF 60, HEIFER 250, MILKING 400,
DRY 420, BULL 500) with `weightSource: MEASURED|ANIMAL|ESTIMATE`. Milk yesterday = cow's AM+PM day total.
Cost/day = Σ kg × item cost per kg. Concentrate ingredient kg/day = concentrate kg × formula line share.

## BIS grade (IS 2052:2023, `feed/BisGrade`)
`targets.bisGrade` (TYPE_I | TYPE_II | null) checks the mix against Table 1. Type III was removed (Amendment 4, 2021).
| Parameter | Type I | Type II |
|---|---|---|
| Moisture, max (as fed = 100 − mix DM) | 11 % | 11 % |
| Crude protein, min | 22 % | 20 % |
| Crude fat, min | 4.0 % | 3.0 % |
| Crude fibre, max | 10 % | 12 % |
| Acid insoluble ash, max | 2.5 % | 3.0 % |
| Calcium / total phosphorus, min | 0.8 / 0.5 % | 0.8 / 0.5 % |
| Aflatoxin B1, max | 20 ppb | 20 ppb |
All limits except moisture are **dry basis**: mix value = kg-weighted as-fed average ÷ mix DM × 100. A parameter is
UNKNOWN when any ingredient lacks that value (or DM); overall INCOMPLETE when nothing fails but something is unknown.
The editor computes the same check live (`bisCheck` in `src/data/feed.ts`).

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
| GET | `/feed/groups?date=` | `[{animalId, group}]` — each active animal's feeding group |
| GET | `/feed/need?days=7` | `FeedNeed` |
| GET | `/feed/ration-rules` | `[{category, current: {effectiveFrom, params: RationParams}, upcoming: […], history: […]}]` |
| PUT | `/feed/ration-rules/{category}` | owner; `{effectiveFrom, params}` → saves a version (same date replaces it) |

```
FeedItem = { id, name, category, unit, costPerUnit, dmPct, cpPct, tdnPct, caPct, pPct, fatPct, fibrePct, aiaPct, aflatoxinPpb,
  maxInclusionPct, excluded, excludedReason, reorderLevel, leadTimeDays, supplier: ContactRef|null, notes, active }
StockItem = { item: ItemRef, stock, needPerDay, daysLeft, status: OK|LOW|REORDER|PREPARE|EXCLUDED|UNUSED, barPct, lastPrice, lastPurchaseDate }
  // REORDER: daysLeft ≤ leadTimeDays or stock < reorderLevel; LOW: daysLeft ≤ leadTimeDays + 4; PREPARE: a MIX that is REORDER/LOW; barPct = min(100, daysLeft/30×100)
Session = { logged, kg, plannedKg, time, by }       // by = member name who saved
Move = { id, date, time, type, quantity, cost, costPerUnit, supplier: ContactRef|null, paymentMethod, paid, session,
  target: {kind: FARM|GROUP|ANIMAL, id, label}, sourceType, notes }
FeedingSheet = { date, session, target, label, animals, lines: [{item: ItemRef, section: GREEN|DRY|CONCENTRATE|OTHER,
  plannedKg, actualKg, stock}], plannedKg, actualKg, saved, warnings: [string] }
  // planned = half of the day plan for the target's animals; actualKg = saved value or null; warnings e.g. "Sorghum green 8 kg under plan", "Stock left 25 kg"
Formula = { id, name, mixItem: ItemRef, batchKg, totalKg, costPerKg, batchCost, targets: {dmPct, cpPct, tdnPct, caPct, pPct, costMin, costMax, bisGrade},
  lines: [{item: ItemRef, kg, pct, cost, overMax, maxInclusionPct}],
  nutrients: [{name: DM|CP|TDN|Ca|P|Fat|Fibre|AIA|Aflatoxin, mixPct, targetPct, status: OK|LOW|HIGH|UNKNOWN}],   // ±10 % of target = OK
  // targetPct = the Mix targets item's value (as fed; Aflatoxin in ppb); for DM–P a missing value falls back to targets.*
  bis: null | {grade: TYPE_I|TYPE_II, label, status: PASS|FAIL|INCOMPLETE,
        params: [{name, value, limit, kind: MIN|MAX, unit: %|ppb, status: PASS|FAIL|UNKNOWN}]},
  warnings: [string], notes }
BatchPreview = { formulaId, batchKg, lines: [{item: ItemRef, needKg, stock, shortKg}], shortCount, batchCost, costPerKg,
  mixStockBefore, mixStockAfter, extraDays }
AnimalRation = { animal: AnimalRef + {category, breed}, date, bodyWeightKg, weightSource, weighedOn, milkYesterday, daysInMilk, stage,
  totals: {greenKg, dryKg, concentrateKg, totalKg, targetKg, dmiPct, waterL}, explain: string,   // fresh kg; targetKg = DMI kg DM
                                   // "DMI 450 kg × 3.75 % (early lactation) = 16.9 kg DM · concentrate 1.5 + 18 L × 0.4 = 8.7 kg · DM split …"
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
8. Ration rules are stored with the group defaults filled in; `{category}` and `categories[].category` are feeding groups,
   listed in the order above. Shares (green, dry) must add up to 100 %; green
   items must be GREEN_FODDER/SILAGE, dry items DRY_FODDER/SILAGE, `concItemId` a MIX or concentrate ingredient.
   `PUT /feed/ration-rules/{category}` returns the full `[RuleSet]` list. `current.effectiveFrom` is absent/null
   when the defaults apply.
9. Planned kg on the feeding sheet = half of the day's plan per item, 1 decimal; ration parts with no item picked
   are not on the sheet and add the warning "Some ration parts have no feed items picked yet …".
10. Supplier contacts (`supplierContactId` on items and purchases) must have the role SUPPLIER. A purchase without a
    supplier uses the item's supplier.
11. Animals planned: active cows and buffalo in categories CALF, HEIFER, MILKING, DRY, BULL.
