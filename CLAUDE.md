# dairyX — Dairy farm management system

Owner: Bharath (senior Java engineer). Farm: Villupuram, Tamil Nadu, ~18–20 cattle (HF + Jersey).
Mobile-first app for owner + workers: herd, milk, health, breeding, calves, feed/TMR, money, team, alerts, reports.

## Working rules (read first)
- **Plan before code.** For anything beyond a small fix, explain the plan and ask before writing code.
- **Minimal, targeted changes.** Touch only what the task needs; no drive-by refactors or renames.
- **Ask before large or unrequested work** (new modules, schema changes, dependency upgrades).
- **Save tokens:** read only the files the task needs; use the contract in `docs/api-*.md` instead of
  reading whole packages; don't paste long logs — show the failing lines only.
- After a task: run `/check`, then `/wrap-up` (updates PROGRESS.md, commits, pushes).
- Branch: **`main` only.** Pushing to `main` deploys frontend (Cloudflare) and backend (Render).
  (PLAN.md's old branch/PR rule is retired.) This overrides any session-assigned feature branch: commit and push
  straight to `main` without asking.

## Stack & layout
```
backend/   Spring Boot 4.1 · Java 21 · Jackson 3 (tools.jackson) · JPA/Hibernate 7 · Flyway · JdbcClient
frontend/  React 18 · Vite · TypeScript · Tailwind · react-router 6 · Supabase JS (auth only)
docs/      api-*.md (contracts per area), schema.md, backlog.md, deploy.md, uat.md, ui-design/
PLAN.md    module plan · PROGRESS.md  status, decisions, blockers (keep up to date)
```
- Auth: Supabase Auth JWT → Spring OAuth2 resource server. Roles per farm: OWNER, WORKER.
- DB: Supabase Postgres, accessed **only** via the backend. Every business table has `farm_id`;
  API base `/api/v1/farms/{farmId}/…`. Errors: `application/problem+json` with plain-language `detail`.
- Units: kg, litres, INR only.

## Backend conventions
- One package per area under `com.dairyfarm.<area>`: `*Controller`, `*Service`, `*Repository` (JdbcClient SQL),
  `*Views` (request/response records). Pure calculations in their own class (e.g. `feed/RationCalc`).
- New tables/columns: new Flyway file `backend/src/main/resources/db/migration/V<next>__<name>.sql`
  (latest is V10). Never edit an applied migration.
- Tests: JUnit 5 + AssertJ. Unit tests for calculators; `*ControllerTest` extends `support/IntegrationTest`
  (Testcontainers, or `TEST_DATABASE_URL`).

## Frontend conventions
- API clients and types in `src/data/<area>.ts`; shared UI in `src/components/ui.tsx` and `components/<area>.tsx`;
  screens in `src/pages/<area>/`. Use existing Tailwind tokens (`text-ink-3`, `text-danger-text`, `bg-brand-soft`…).
- Keep screens phone-width friendly; reuse `Screen`, `TopBar`, `LoadState`, `useLoad`.
- Responsive: phone first; `md:` (≥768) tablet column 720 px, `<Screen wide>` (dashboards, lists, reports) grows to
  1040 px at `lg:`; forms stay 720 px. Bottom nav becomes a left rail at `xl:`; sheets/dialogs centre on `md:`+.

## Commands
```bash
# backend (from backend/)
mvn -B verify                                         # needs Docker for Testcontainers
TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/postgres TEST_DATABASE_PASSWORD=postgres mvn -B test
mvn -B test -Dtest=RationCalcTest                     # one test class
# frontend (from frontend/)
npm ci && npm run lint && npm run build
```

## Domain rules worth knowing
- **Ration = TMR, dry-matter based** (`feed/RationCalc`, `feed/RationParams`, docs/api-feed.md "Ration rule"):
  DMI = body weight × DMI % — milking by stage (early ≤100 DIM 3.75, mid ≤200 3.25, late 2.75), heifers/calves by
  age (3–6 m 3.00, 6–12 m 2.75, 12+ m 2.50), dry cow 2.00. DMI split green/dry/concentrate by editable DM shares
  (guide 35–45 / 15–25 / 25–40). Concentrate per category by milk (1.5 + 0.4 kg/L, capped) or by DMI share.
  Fresh kg = DM ÷ item DM % (fallback 30/90/80). Optional fixed-kg green items (hydroponic, azolla) come off
  the green share. Water = base + 4 L per litre of milk.
- **Feeding groups** (`feed/FeedGroups`, V8): Fresh 1–21 DIM, Early 22–100, Mid 101–200, Late 201+, Close-up ≤ 21 d
  to calving, Steaming-up 22–60 d, Dry & mature heifers, Growing heifers (9 m to first service), Calves, Bulls.
  Calculated daily; one ration rule per group.
- Concentrate formula: 100 kg batch, Villupuram ingredients; **no cottonseed cake** (gossypol); gingelly cake has
  stage-wise daily limits.
- Lactation stage from `last_calving_date`; milk yesterday = AM + PM total.
- Workers see health + feeding alerts only; money screens are owner only.

## Where to look
| Task | Start here |
|---|---|
| Feed / ration | docs/api-feed.md, backend `feed/`, frontend `pages/feed/` |
| Milk, health, weight | docs/api-daily-records.md, `milk/`, `record/` |
| Breeding, calves | docs/api-breeding.md, `breeding/` |
| Money, labour, procurement | docs/api-money.md, `finance/`, `labour/`, `procurement/` |
| Alerts, home, reports | docs/api-home-alerts.md, `alert/`, `home/`, `report/` |
| Tables | docs/schema.md + migrations |
| Deploy / env vars | docs/deploy.md, backend/README.md |
