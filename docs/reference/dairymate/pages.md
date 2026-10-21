# Reference site 2 — DairyMate (app.dairyfarmmanager.com)

Mapped 2026-10-21, text notes only. Functional reference; our UI is an original design.
The site is a Flutter web app, cows only, INR, with a sidebar of 15 sections.
Nothing was saved or deleted during mapping (forms were opened and closed).

---

## 1. Home (dashboard)
- **KPI cards:** today's milk (L, % vs yesterday) · total cows · milking now · alerts · net profit (₹).
- **Tabs:** Today's Barn / Farm Stats (milk production trend 7D / 30D / 90D).
- **Daily checklist:** built-in daily tasks (log milk yield, record finances, verify feed inventory,
  check herd heat stress) ticked off per day, plus **Add custom task**. Shows "n tasks remaining".
- **Farm advisor:** short rule-based tips (heat stress, feed-to-profit ratio, upcoming gestation events).
- **Barn comfort & heat advisor:** THI (temperature-humidity index) from temperature and humidity,
  severity band and advice; settings to enter barn temperature/humidity.
- **Heat alert banner:** count of cows flagged by Heat Watch (high / medium / low) → opens Heat Watch.
- **Top producers:** cows ranked by litres.
- **Feed inventory:** stock bars per feed.
- **ROI cow ranking:** link to lifetime-profit ranking.
- **Herd performance:** donut of cows classified Keep / Monitor / Dry-off / Cull.
- **Header:** global search (cows, records, transactions; Ctrl K), notifications bell, theme toggle,
  account menu (subscription, log out).

## 2. Cows
- **List:** columns tag, name, status, breed, DIM/stage, actions (edit, view profile). Search by name/tag/breed.
- **Filter:** status (Milking, Dry, Pregnant, Calves, Bulls), **High SCC**, **In withdrawal**,
  lactation stage (Early 0–100 DIM, Mid 100–200, Late 200+).
- **Import herd:** CSV template download + upload; tag number and name required.
- **Add / Edit cow:** tag number* · name* · breed (quick chips: Holstein, Jersey, Guernsey, Ayrshire,
  Brown Swiss, Sahiwal, Gir, Tharparkar, Red Sindhi, Crossbreed, or free text) · purchase cost ·
  weight (kg) · colour/marks · status (Milking, Dry, Pregnant, Calf, Bull, Sold) ·
  calving date — label changes by status: Milking → "milking start / calving date", Dry →
  "last calving date", hidden for Pregnant/Calf/Bull · sire name (optional) · mother cow (pick from herd).
  **No date-of-birth field.**
- **Cow profile:** header (name, status, tag, breed) · breed, weight, colour, purchase cost, last calving,
  **days open** · **health risk score** (0–100, e.g. "2/100 Healthy") · herd lineage pedigree ·
  **lactation progress** (day N of 305, calving → peak → end) · weight tracking (+ log weight:
  weight, date, notes) · bottom actions: Log milk · Health · Breeding · Share.

## 3. Milk
- **Quality overview:** count of records by milk grade A / B / C / D.
- **Filters:** search by cow, All / Today / 7 days / 30 days / Custom; 7-day trend chart (herd or per cow
  "cow curve").
- **Records table:** date · cow · morning (L) · evening (L) · total (L) · quality · edit.
- **Log milk production** offers two modes:
  - **Herd milking sheet** (fastest): one screen for all milking cows, date picker, morning and evening
    quantity per cow with −/+ buttons, **pre-filled from the last record**, a per-cow baseline,
    **scan ear tag** to jump to a cow, "Confirm all N cows".
  - **Single cow log:** cow · date · morning L · evening L (−1 / −0.5 / +0.5 / +1 steppers) ·
    optional **milk quality**: fat %, protein %, SNF %, density (g/mL), SCC (k cells/mL).

## 4. Feed
- Tabs: **Feed stocks** / **Herd feeding**; banner "incomplete feed logs today".
- **Feed purchase:** feed name* · quantity* · unit (kg) · cost per unit · purchase date · supplier ·
  optional reorder alert: reorder level, lead time (days), supplier contact.
  Note: logging a purchase **auto-creates an expense in Finance**.
- **Feed herd / log feeding:** blocked until stock exists ("purchase feed first") — feeding consumes stock.

## 5. Calendar
Month view with filters All / Heat / Breeding / Calving / Health; events listed for the selected day.

## 6. Health
- **Herd health overview:** at risk · critical · in withdrawal · events per month · "% healthy".
- **Tabs:** All / Withdrawal / Vaccine / Deworm / Batch (batch = treat many cows at once).
- **Add health record:** cow · problem/symptoms · diagnosis · medicine given · veterinarian · cost ·
  "milk withdrawal required?" → withdrawal period (days, default 3).

## 7. Breed
List of breeding records + **Add breeding record:** cow · status (Open, Pregnant, Calved) ·
method (AI, Natural, ET) · bull name · heat date · breeding date · expected calving · notes.

## 8. Heat Watch
- Banner: cows flagged with confidence (high / medium / low).
- Per cow card with reason, e.g. "calved 90 days ago and not yet bred — watch for heat; silent heats are
  common in early lactation".
- **Log heat signs:** cow · observed signs (multi-select: mounting other cows, restlessness/pacing,
  mucus discharge, vulvar swelling, chin resting on others, bellowing, reduced appetite) ·
  intensity (Mild, Moderate, Strong) · notes · "Save & schedule AI reminder" — **insemination reminder
  12 hours after the observation**.

## 9. Cull Advisor
- Counts: cull flags · dry-offs · monitor · healthy.
- Per cow: **production, reproduction and health scores (0–100)** · analysis text · **lifetime P&L** ·
  action menu (mark as sold, mark as dry). Groups: Cull / Dry-off / Watch closely / Performing well.

## 10. Finance
- Totals: income · expenses · net profit. Tabs Income / Expenses. Periods: All, Today, 7D, 30D, Month,
  Quarter, Year, Custom. Timeline grouped by day.
- **Insights:** income over feed cost (IOFC), category breakdown.
- **Add transaction:** income/expense toggle · date (Today / Yesterday / pick) · amount with ±₹1/10/100
  steppers · category · notes.
  Income: Milk sales, Cow sales, Manure sales, Other. Expense: Feed, Medicine, Labour, Equipment,
  Vet fees, Other.
  Milk sales entries can be generated from milk records (e.g. "herd milk sales: n cows, x L").

## 11. Reminders
Manual reminders: name* · notes · date · time · type (Health, Breed, Feed, Veterinarian, Other) · cow.

## 12. ROI Ranking
Cows ranked by **net lifetime profit** (needs milk data).

## 13. Reports
Milk production · cow performance · breeding · feed consumption · health records · profit & loss —
each with **PDF**, **CSV** and share.

## 14. Compare Cows
Pick two cows → average L/day, 7-day milk trend, days in milk, lactation stage, status, health records
count/cost, breeding status side by side.

## 15. Settings
- Subscription, share report (to vet/accountant/cooperative), integrations (parlour/IoT, DHIA) — not needed.
- **Team management:** invite worker.
- Theme · language · currency (INR) · **milk rate (cooperative) ₹/L** · **margin alert threshold**
  (alert when feed cost exceeds X % of milk price) · **farm location** (coordinates).
- **Notifications:** milking alerts · daily task reminder · morning digest (time) · anomaly & health
  alerts (mastitis risk, top producer missing) · learned morning/evening milking times (from last 30
  days) · quiet hours.
- Demo data wipe (not touched).
