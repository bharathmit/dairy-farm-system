# Progress

Status: **Waiting for owner review of docs/schema.md and docs/api.md (Module 0).**

- [x] Reference site 1 (Farmleo) mapped → docs/reference/farmleo/pages.md, incl. all popups
- [x] Alert requirements captured → docs/requirements/alerts.md
- [x] Reference site 2 (DairyMate) mapped → docs/reference/dairymate/pages.md
- [x] Draft schema v3 (30 tables)
- [x] Consolidated schema v4 (13 tables) → docs/schema.md; coverage → docs/requirements/coverage.md
- [x] Draft API v3 → docs/api.md
- [x] Owner: keep all requirements (procurement, barn THI, etc.)
- [ ] api.md to be aligned with v4 table names after schema approval
- [ ] Module 0 — owner approves schema + API (open questions in schema.md and alerts.md)
- [ ] Modules 1–15 — see PLAN.md (no code until Module 0 is approved)

## Decisions
- DB: Supabase Postgres; access only through Spring Boot; no RLS; roles in Spring Security.
- Auth: Supabase Auth JWT, validated by Spring as OAuth 2.0 resource server.
- Roles per farm: OWNER, WORKER. Workers see health + feeding alerts only.
- Multi-farm: farm_id on every business table; API under /farms/{farmId}.
- Species: all six. Currency INR only; units kg and litres only.
- Milk: both per-animal and farm-total entries.
- Deployment: OpenShift later, not now.
- Reference sites are functional references only (text notes, no screenshots); UI is our own design.

## Notes / blockers
- Owner asked: no code until the design is reviewed.
