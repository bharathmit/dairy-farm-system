# Build plan

Stack: React 18 + Vite + TypeScript + Tailwind (frontend) · Spring Boot 3 / Java 21, JPA/Hibernate,
Flyway, Spring Security OAuth2 resource server (backend) · Supabase Postgres + Supabase Auth.
Deployment (OpenShift) is out of scope for now; backend stays container-ready.

Repo layout
```
/frontend   React app (Supabase Auth login → calls backend only)
/backend    Spring Boot app (all business logic + DB access)
/docs       reference/<site>/pages.md (one folder per reference site),
            requirements/alerts.md, schema.md, api.md
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
0. Design docs approved (reference sites, alerts, schema, API)
1. Scaffolding — Maven + Vite projects, Flyway baseline, Supabase JWT security, `/me`, CI build
2. Farms, members, roles, settings (multi-farm)
3. Animals — CRUD, filters, status/exit, photos, CSV import/export
4. Groups — per species, members
5. Lineage — tree, siblings/offspring, pedigree %, mate-risk
6. Measurements — weight, height, BCS, trend
7. Health — records, withdrawal periods, schedules, batch by group
8. Reproduction — heat, breeding/AI, semen stock, pregnancy, calving, calf creation
9. Calf rearing — colostrum, calf feeding, growth targets, weaning
10. Feed — feed items with nutrients, records, stock, summary, concentrate formula + batch preparation, ration planner
11. Yield — milk AM/PM per cow + farm total, fat/SNF/SCC, batch session entry
12. Finance — ledger, auto entries, summary
13. Labour, equipment, procurement
14. Alerts — rules, daily job, on-save checks, worker visibility, bell + list
15. Dashboard & reports — KPIs, drill-downs, P&L, PDF/CSV export
