# Progress

Status: **UI-first. Module 1 UI built as a clickable React app (in-memory sample data). Next: table design + backend for Module 1.**
UI design canvas: https://claude.ai/artifact/KXtE8vB41BQHR9UAfsQ6UT (24 mobile screens); snapshot in docs/ui-design/module-1/. Tables and backend come after the UI is final.

- [x] Module 1 UI design reviewed (owner comments applied: email-only login + Google, create account, update password, logo + footer on all pages, milk rate page, member DOB/sex/password, team filter)
- [x] Module 1 UI app → `frontend/` (React 18 + Vite + TS + Tailwind), all 24 screens, in-memory store
- [ ] Deploy UI to Cloudflare Pages (steps in docs/deploy.md — owner connects the repo)
- [ ] Module 1 table design → then Spring Boot backend, replace in-memory store with API calls

- [x] Reference site 1 (Farmleo) mapped → docs/reference/farmleo/pages.md, incl. all popups
- [x] Alert requirements captured → docs/requirements/alerts.md
- [x] Reference site 2 (DairyMate) mapped → docs/reference/dairymate/pages.md
- [x] Draft schema v3 (30 tables)
- [x] Consolidated schema v4 (13 tables) → docs/schema.md; coverage → docs/requirements/coverage.md
- [x] Draft API v3 → docs/api.md
- [x] Owner: keep all requirements (procurement, barn THI, etc.)
- [ ] api.md to be aligned with v4 table names after schema approval
- [x] Module 0 (part) — owner approved tables 1–5 (schema v4.2) for Modules 1–4
- [ ] Module 0 (rest) — tables 6–13 and open questions in alerts.md
- [x] Module 1 — scaffolding: `/backend` Maven project, Flyway V1, Supabase JWT, `GET /me`, problem+json errors, Swagger, CI (`module/1-scaffolding`)
- [x] Module 2 — farms, settings, my profile and preferences, team members with owner-set password or email invite (`module/2-farms`)
- [x] Module 3 — animals: add/edit, filters, status and exit, tag lookup, parents by id or tag, photos (Supabase Storage), CSV template/import/export (`module/3-animals`)
- [ ] Modules 4–15 — see PLAN.md

## Decisions
- DB: Supabase Postgres; access only through Spring Boot; no RLS; roles in Spring Security.
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
- Frontend hosting: Cloudflare Pages free plan. Backend hosting (later, to decide): Render free (sleeps after 15 min, 512 MB) or Google Cloud Run free tier (card needed); OpenShift remains the long-term target.

## Notes / blockers
- Module 1 UI is a clickable React app on sample data (`module/1-ui`); it is not wired to the API yet.
- Tests use Testcontainers in CI; locally they can use an existing Postgres via `TEST_DATABASE_URL`.
