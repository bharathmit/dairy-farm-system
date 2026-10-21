# Progress

Status: **Waiting for owner review of docs/schema.md and docs/api.md (Module 0).**

- [x] Reference site 1 (Farmleo) mapped → docs/reference/farmleo/pages.md, incl. all popups
- [x] Alert requirements captured → docs/requirements/alerts.md
- [x] Draft schema v2 (27 tables) → docs/schema.md
- [x] Draft API v2 → docs/api.md
- [ ] Reference site 2 — waiting for owner to open it
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
