# Progress

Status: **First release built — all 6 modules (backend + UI) on `main`, deployed (Cloudflare + Render + Supabase).** Now: testing, bug fixing and user acceptance (docs/uat.md). Next release: docs/backlog.md.
UI design canvas: https://claude.ai/artifact/KXtE8vB41BQHR9UAfsQ6UT (24 mobile screens); snapshot in docs/ui-design/module-1/. Tables and backend come after the UI is final.

- [x] Module 1 UI design reviewed (owner comments applied: email-only login + Google, create account, update password, logo + footer on all pages, milk rate page, member DOB/sex/password, team filter)
- [x] Module 1 UI app → `frontend/` (React 18 + Vite + TS + Tailwind), all 24 screens, in-memory store
- [x] Deployed: frontend on Cloudflare Workers, backend on Render, database/auth/storage on Supabase (docs/deploy.md)
- [x] Module 1 table design (tables 1–5, schema v4.2) and Spring Boot backend for Modules 1–4

- [x] Reference site 1 (Farmleo) mapped → docs/reference/farmleo/pages.md, incl. all popups
- [x] Alert requirements captured → docs/requirements/alerts.md
- [x] Reference site 2 (DairyMate) mapped → docs/reference/dairymate/pages.md
- [x] Draft schema v3 (30 tables)
- [x] Consolidated schema v4 (13 tables) → docs/schema.md; coverage → docs/requirements/coverage.md
- [x] Draft API v3 → docs/api.md
- [x] Owner: keep all requirements (procurement, barn THI, etc.)
- [x] Module 0 (part) — owner approved tables 1–5 (schema v4.2) for Modules 1–4
- [ ] Module 0 (rest) — tables 6–13 and open questions in alerts.md
- [x] Module 1 — scaffolding: `/backend` Maven project, Flyway V1, Supabase JWT, `GET /me`, problem+json errors, Swagger, CI
- [x] Module 2 — farms, settings, my profile and preferences, team members with owner-set password or email invite
- [x] Module 3 — animals: add/edit, filters, status and exit, tag lookup, parents by id or tag, photos (Supabase Storage), CSV template/import/export
- [x] Module 4 — groups: add/edit/delete, set animals, manager, animal counts
- [ ] api.md: Modules 5–15 rows still drafts
- [x] Frontend: in-memory store replaced with API calls + Supabase sign-in (checked end to end in a browser)
- [ ] Open: Google sign-in (needs Google OAuth in Supabase); milk/flags/days-in-milk fields wait for later modules
- [x] Daily records backend (Canvas 2): Flyway V3 (`contact`, `production_record`, `farm_event`, `farm.milk_grading`); contacts; milk day/trend/grades/herd sheet/one cow/sales with withdrawal check; expected yield by lactation day and A–D grade rules; weight/height/BCS with gain per day; health records, batch, follow-ups, withdrawals. Contract: docs/api-daily-records.md. 69 backend tests pass.
- [x] Breeding & calves backend (Canvas 3): Flyway V4 (`breeding_record`, `item`, `item_txn`, `animal.lactation_number`, `farm.breeding_settings`); breeding settings; status per female (open/served/pregnant/overdue, days open, missed heats, failed services, spend, keep-or-sell suggestion); overview + 14-day calendar; heat watch; breeding history by lactation; heats; services with AI technician/bull fees and semen straws from stock; pregnancy checks; calving (creates calves, starts the new lactation, retained placenta → health follow-up); colostrum; semen stock; calves list, feeding (one calf or group sheet); family tree, mate check, next free tag. Contract: docs/api-breeding.md. 82 backend tests pass.
- [x] Feed & ration backend (Canvas 4): Flyway V5 (`feed_formula`, `feed_formula_line`, `ration_rule`, feed columns on `item_txn`); feed items with nutrients, max inclusion, exclusion, reorder level, lead time and supplier; stock by purchase, harvest and adjustment; feeding sheet per session for the farm, a group or an animal (plan vs actual, warnings); concentrate formulas (cost/kg, nutrients vs target, excluded blocked, over-max warned), batch preview and preparation (ingredients out, mix in, mix cost); ration per animal (body weight source, milk yesterday, lactation stage limits, concentrate cap, nutrient check), ration summary, farm feed need with shortfalls; ration rules with effective dates. Breeding: pregnancy check at least `pdMinDays` (28) after service. Contract: docs/api-feed.md. 88 backend tests pass.
- [x] Feed purchases posted to Finance (Canvas 5)
- [x] Procurement notes from feed shortfalls (Canvas 5) and procurement alerts (Canvas 6)
- [x] Money & team backend (Canvas 5): Flyway V6 (`finance_entry`, `attendance`, `procurement_note`, service columns on `item_txn`) and V6.1 backfill; automatic finance entries kept in step with milk sales, purchases (feed, semen, procurement), health costs, breeding fees and pregnancy checks, animal purchase/sale prices and equipment services; finance summary (margin, pending, categories), manual entries, P&L for the farm and per animal (head-day feed split); workers, weekly attendance, wages due (daily/monthly, leave allowance, advances) and payments; equipment with service schedule; procurement notes with low-stock suggestions, order, receive and plan-from-feed-need. All owner only. Contract: docs/api-money.md. 95 backend tests pass.
- [x] Home, alerts & reports backend (Canvas 6): Flyway V7 (`alert`, `health_schedule`, `checklist_task`, `checklist_tick`, `farm.alert_rules`, `animal.cull_keep_until`); alert engine with 23 rules (reproduction, calves, health incl. deworming/vaccination schedules, milk, feed, finance & operations), daily 05:00 job + refresh + lazy refresh, auto-close when resolved, worker visibility; reminders with repeat, done/dismiss/snooze; alert rules editor (shared numbers written to breeding settings, milk grading and farm settings); health schedules with defaults; home by role; daily checklist (auto ticks from milk, feeding and barn readings; custom tasks); barn readings with THI; reports summary + JSON/CSV (milk, cows, breeding, feed, health, P&L); cull advisor (score, verdict, keep 90 d, mark dry); compare cows. Contract: docs/api-home-alerts.md. 104 backend tests pass.
- [x] Feed: ration switched to TMR (dry-matter based) — DMI by stage/age, DM shares per category, concentrate by milk or DMI, optional fixed-kg green items (hydroponic, azolla), water. 106 backend tests pass.
- [x] Claude Code setup: CLAUDE.md, .claude/settings.json, slash commands /plan, /check, /wrap-up.
- [x] Feed: BIS grade check for concentrate formulas (IS 2052:2023 Type I / II, dry-basis limits incl. moisture, Ca, P, aflatoxin B1); feed items gain fat, fibre, acid insoluble ash and aflatoxin; "Excluded" toggle replaced by max inclusion 0 %. Typical as-fed values filled for farm 1 ingredients (aflatoxin left blank — needs a lab or supplier figure). 109 backend tests pass.
- [ ] Next release items → docs/backlog.md

## Branches
- `main` only (design docs, `frontend/`, `backend/`); pushes to `main` deploy both apps.

## Decisions
- DB: Supabase Postgres; access only through Spring Boot; RLS deferred (backlog); roles in Spring Security.
- Auth: Supabase Auth JWT, validated by Spring as OAuth 2.0 resource server.
- Roles per farm: OWNER, WORKER. Workers see health + feeding alerts only.
- Multi-farm: farm_id on every business table; API under /farms/{farmId}.
- Species: all six. Currency INR only; units kg and litres only.
- Milk: both per-animal and farm-total entries.
- Deployment: OpenShift later, not now.
- Auth: email + password and Google sign-in (no phone OTP).
- Worker logins: both owner-set password and email invite (owner's choice per member).
- Backend: Spring Boot 4.1 (Spring Framework 7, Jackson 3, Hibernate 7), owner request.
- JWT: checked against the project's JWKS; legacy HS256 secret supported via SUPABASE_JWT_SECRET.
- Margin alert: total margin below X % (default 30 %), weekly; milking times stored in farm settings.
- Reference sites are functional references only (text notes, no screenshots); UI is our own design.
- All records are saved online immediately; no offline mode or later sync.
- Team members: owners set member passwords manually; no invites.
- Hosting: frontend on Cloudflare Workers (static assets, free); backend on Render free (sleeps after 15 min, 512 MB); OpenShift remains the long-term target.
- Concentrate quality: IS 2052:2023 Type I and II only (Type III removed by BIS in 2021); a formula picks its grade. Max inclusion 0 % = excluded.

## Notes / blockers
- Before first deploy: create the private `animal-photos` bucket and keep email confirmation on (see backend/README.md).
- Tests use Testcontainers in CI; locally they can use an existing Postgres via `TEST_DATABASE_URL`.
