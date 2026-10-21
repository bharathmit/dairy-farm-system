# Progress

Status: **Waiting for owner review of docs/schema.md and docs/api.md (Module 0).**

- [x] Reference site mapped → docs/reference/pages.md (2026-10-21)
- [x] Draft schema → docs/schema.md
- [x] Draft API → docs/api.md
- [ ] Module 0 — owner approves schema + API (answer questions at end of schema.md)
- [ ] Module 1 — Scaffolding
- [ ] Module 2 — Users & settings
- [ ] Module 3 — Animals
- [ ] Module 4 — Groups
- [ ] Module 5 — Lineage
- [ ] Module 6 — Weight
- [ ] Module 7 — Health
- [ ] Module 8 — Breeding
- [ ] Module 9 — Feed
- [ ] Module 10 — Yield
- [ ] Module 11 — Finance
- [ ] Module 12 — Dashboard & reports
- [ ] Module 13 — Phase 2 extensions

## Decisions
- DB: Supabase Postgres; access only through Spring Boot; no RLS; roles in Spring Security.
- Auth: Supabase Auth JWT, validated by Spring as OAuth 2.0 resource server.
- Roles: OWNER, WORKER.
- Deployment: OpenShift later, not now.
- Reference site is a functional reference only; UI is our own design.

## Notes / blockers
- (none)
