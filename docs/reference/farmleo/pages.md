# Reference site — page inventory

Source: reference site 1 — Farmleo web dashboard, mapped 2026-10-21 (text notes only).
Used **only** for fields, workflows and data relationships. Our UI is an original design; no
branding, copy or assets from the reference are reused.

Legend: `*` = required in the reference form.

---

## 1. Livestock (`/animals`) — main screen

**Layout:** filter bar → animal list (left) → detail panel (right).

**List filters:** search (tag, national ID, breed, notes, status) · species · group · sort (date added /
age oldest first) · status (Active, Sold, Dead, Transferred).
**Header actions:** Add Animal · CSV template download · Upload CSV (bulk import).
**List row:** tag, status badge, sex, name, national ID, age (computed), species, group,
"Add record" menu, ⋮ menu (Edit, Copy tag, Delete). Multi-select + "Delete selected".

### 1a. Add / Edit Animal form
| Field | Type | Notes |
|---|---|---|
| Ear tag number* | text | unique |
| Animal name | text | optional |
| National ID | text | optional |
| Date of birth* | date | |
| Gender* | Male / Female | |
| Species | Cow, Buffalo, Goat, Sheep, Pig, Poultry | |
| Status | Active, Sold, Dead, Transferred | |
| Dam tag / Sire tag | text | stored as tag text; can later link to an animal record |
| Breed | text | |
| Color | text | |
| Birth weight | number + kg/lb | |
| Acquisition type | Born / Purchased | |
| Acquisition date | date | |
| Notes | textarea | |
| Photo | image gallery | from detail panel |

### 1b. Detail panel — tabs
- **Overview:** age, name, born, breed, national ID, date added, latest weight + "weight trend",
  group, parents (dam/sire, linked or tag-only), offspring list, color, notes.
- **Health (n):** list of health records + Add.
- **Breeding (n):** females only — list of breeding events + Add.

### 1c. "Add record" menu (per animal)
Health record · Weight record · Feed record · Yield record.

**Health record:** date · type (Illness, Vaccination, Deworming, Injury) · description ·
treatment/medicine · vet name · next action date (drives reminders) · notes.

**Weight record:** date · weight · unit (kg/lb) · method (scale, tape, estimate) · notes.
(Reports also show "average BCS" → body condition score is captured somewhere; we add it here.)

**Breeding record (one record covers the whole cycle):**
- Service: date · method (Natural / AI) · heat observed (Not recorded, Clear, Weak/unclear, Silent) ·
  sire ID/name · bull tag · AI technician · semen batch ID · bull exposure start/end.
- Pregnancy: status (Unknown, Pregnant, Not pregnant) · check date · check method (Ultrasound,
  Palpation, Blood test) · check result (Confirmed / Not confirmed) · expected due date.
- Birth: birth date · outcome (Live, Stillborn, Aborted, Unknown) · ease (Unassisted, Assisted,
  Difficult, Cesarean) · calf sex · calf birth weight · notes.

**Feed record:** see §5. **Yield record:** see §6 (Milk only offered for female dairy animals —
"strict eligibility checks").

### 1d. Breeding & Lineage view
- **Tree:** parents → selected animal → siblings and offspring.
- **Risks:** "mate risk checker" — pick an active opposite-sex animal of the same species and check
  for shared ancestors (inbreeding warning).
- **Birth history:** births recorded for this dam.

---

## 2. Farm View (`/farm-view`)
- **Overview tab:** upcoming health actions (from next-action dates) · expected due dates (from
  breeding records) · animals by species counts · recently added (last 6).
- **Groups tab:** list of groups (plan limit shown) · Create Group.
  - Group form: name* · primary location · target outcome (Growth/Fattening, Maintenance, Recovery,
    Gestation, Other) · manager name · notes · member picker (search, sex, status, sort).
  - Purpose: batch health work and bulk record entry.

## 3. Reports (`/reports`)
Filters: species · period (Today, Yesterday, 1/2/6 months, 1 year, This month, Custom).
Export: PDF, CSV.
- **Overview:** total animals, active herd, females, average age.
- **Health:** health events (90d), illness cases (90d), vaccinations (12m), upcoming actions (30d).
- **Breeding:** breedings (12m), conception rate (12m), AI rate (12m), due in 60 days.
- **Growth:** weighed (60d), average weight, average daily gain (90d), average BCS.

## 4. Finances (`/finances`)
Ledger of income/expense with automatic entries from feed and yield records.
Filters: species · period · type · category · currency · group · animal. Export CSV.
KPIs: income, expense, net, top expense category.

**Finance form:** direction (Expense/Income) · amount* · currency* · category* · date · time ·
counterparty · payment method (Cash, Bank transfer, Card, Mobile money, Cheque, Other) ·
species / group / animal (allocation) · quantity · unit · notes.
Categories — expense: Equipment, Feed, Labor, Transport, Utilities, Vet & Medicine, Other;
income: Animal sales, Milk sales, Manure sales, Other income (+ egg/wool, not needed for dairy).

## 5. Feed (`/feed`)
Filters: species · period · type · feed category · currency · group · animal. Export.
KPIs: feed used, feed cost, average per day, **days of stock left**.

**Feed form:** date · time · type (Usage, Purchase, Adjustment) · feed item* · feed category
(Concentrate, Forage, Silage, Mineral, Custom) · quantity* · unit* · cost · currency · supplier · notes.
Allocation: whole farm, group, or animal.

## 6. Yield (`/yield`)
Filters: species · period · yield type · currency · group · animal. Export.
KPIs: quantity, income, top yield type.

**Yield form:** date · time · yield type* (Milk, Manure, Meat) · quantity* · unit* · income amount ·
currency · buyer · notes.

## 7. Settings (`/settings`)
- **Personalization:** theme (light/dark) · language · default currency.
- **Notifications:** master switch · health-action reminders · due-date reminders · lead time
  (same day / 1 / 3 / 7 days before) · preferred reminder time.
- **Account:** first/last name, farm name, farm country.
- **Subscription:** not needed (own app).

---

## 8. Full pass — popups and details (2026-10-21)

| Screen / popup | What it adds |
|---|---|
| Animals list | Layout reference |
| CSV format help | Import columns: `tag_number*`, `animal_name`, `dob*` (YYYY-MM-DD), `sex*` (male/female/M/F), `species*`, `status` (default active), `national_id`, `dam_tag`, `sire_tag`, `breed`, `color`, `birth_weight` (≥0), `notes`, `acquisition_type` (born/purchased/bought), `acquisition_date`. Case-insensitive values, plurals accepted. |
| Add animal form | No conditional fields. **Edit animal** = same form, but species is read-only after creation. |
| Row ⋮ menu | Edit · Copy tag · Delete |
| Animal photos | Up to 3 photos per animal (paid tier on reference) |
| Weight trend view | Current weight, change since last, total change, history list (date, weight, method) |
| Animal detail | Tabs: Overview · Health · Breeding (Breeding tab shown for females only) |
| Health record | No conditional fields per type |
| Weight record | "Include BCS" checkbox reveals a BCS slider (default 3.0) |
| Feed record | Unit list on Feed page: kg, lb, bag, ton, liter. Allocation: species / group / animal (all optional) |
| Yield record | Unit depends on type: Milk → Liter/kg; Manure → kg/Bag/Ton. From the Yield page: species* required, group "Farm level entry" or a group, animal optional → **supports both farm-total and per-animal milk** |
| Breeding record | Method AI → "Breeding program" = AI / Embryo transfer. **Expected due date auto-fills** (+283 days for cow). |
| Lineage | Header KPIs: pedigree % complete, last birth, offspring count, common ancestors. Tabs: Tree, Risks (mate picker), Birth history + linked descendants |
| Notifications bell | Recent notifications list, link to notification settings |
| Profile menu | Edit profile · Settings · Log out |
| "Get the app" | Mobile app promo (not needed) |
| Farm View overview | Health actions, expected due dates, species mix, recently added |
| "View all" lists | Search, species, status filters, multi-select, **"Mark all selected as done"** (closes reminders) |
| Groups / Create group | **Group is per species** (species picker first), group colour, member picker filtered to that species |
| Reports + drill-down | Every KPI card opens a drill-down list of the animals behind the number, with its own PDF/CSV export |
| Finances + form | Category list changes with direction (income vs expense) |
| Feed + form | — |
| Yield form (page level) | see 10 |
| Settings | Personalization, Notifications (switches + lead time + time), Account (name, farm name, country; email/account ID/created read-only). Subscription tab skipped (billing). |
