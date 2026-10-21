# Build plan

Stack: React 18 + Vite + TypeScript + Tailwind (frontend) · Spring Boot 3 / Java 21, JPA/Hibernate,
Flyway, Spring Security OAuth2 resource server (backend) · Supabase Postgres + Supabase Auth.
Deployment (OpenShift) is out of scope for now; backend stays container-ready.

Repo layout
```
/frontend   React app (Supabase Auth login → calls backend only)
/backend    Spring Boot app (all business logic + DB access)
/docs       reference/pages.md, schema.md, api.md
PLAN.md     this file
PROGRESS.md checklist — every session reads it first
```

Rules for every session
1. Read `PROGRESS.md`, then do the **first unchecked module only**.
2. Work on a branch `module/<n>-<name>`, open a PR, never push to `main` directly.
3. Small, targeted changes; backend tests (JUnit + Testcontainers Postgres) must pass.
4. Update `PROGRESS.md` (tick items, add notes/blockers) in the same PR.
5. Stop and ask the owner before anything not in this plan.

Modules
0. Design docs approved (schema, API)
1. Scaffolding — Maven project, Vite project, Flyway baseline, Supabase JWT security, `/me`, CI build
2. Users & settings — app_user, roles, farm_settings
3. Animals — CRUD, list filters, status/exit, photo, CSV import/export
4. Groups — CRUD, members
5. Lineage — tree, siblings/offspring, mate-risk check
6. Weight records + growth metrics
7. Health records — per animal, batch by group, reminders
8. Breeding — heat/service/pregnancy/calving, calf creation, due dates
9. Feed — feed items, records, stock, summary
10. Yield — milk AM/PM per cow, batch session entry, summary
11. Finance — ledger, auto entries from feed/yield/health, summary
12. Dashboard & reports — KPIs, P&L, PDF/CSV export
13. Phase 2 — health schedules, labour, procurement
