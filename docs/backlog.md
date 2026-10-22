# Backlog — next release

Items agreed or found during the first release (Canvases 1–6), not built yet. Grouped by area; each line says
what is missing today. Pick items into a release plan before starting.

## Platform & security
- [ ] **Row-level security in Supabase** (owner deferred). Today all access goes through Spring Boot with the
      service connection; RLS would add a second line of defence per `farm_id`.
- [ ] **Google sign-in.** The button is shown and says "isn't turned on yet"; needs Google OAuth set up in Supabase
      and the redirect URLs.
- [ ] **Push notifications** (phone) for alerts and reminders. Notification settings are saved but nothing is sent;
      the daily digest email is not sent either.
- [ ] **Hosting:** Render free sleeps after 15 min (first request ~1 min). Decide paid plan / Cloud Run / OpenShift.

## Daily records (Canvas 2)
- [ ] **Weight from heart girth** for the tape method (today the user types the kg read from the tape).
- [ ] **Medicine from stock** on health records (item type MEDICINE, usage moves, default withdrawal days from the item).
- [ ] **"At risk" health score** on the health home (tile left out).

## Breeding & calves (Canvas 3)
- [ ] **Breeding settings screen** for the values not on the alert rules page: gestation days per species,
      voluntary wait, target / sell days open, sell after N failed services, calf taper plan, weaning and male-calf
      sell window. Today they use the defaults (API exists: `PUT /breeding/settings`).
- [ ] **"Mark for sale"** from breeding history and the cull advisor (today they open the animal page).
- [ ] **Insemination reminder 12 h after a heat** ("Save & remind" on heat signs).
- [ ] **Calf growth targets** ("vs target" bar, `weightTargetKg`) and deworm badges on the calves list from health schedules.

## Feed & ration (Canvas 4)
- [ ] **Own fodder without a harvest entry** drives stock below zero; consider a daily harvest default per fodder
      item or a "cut today" prompt on the feeding sheet.

## Money & team (Canvas 5)
- [ ] **Equipment purchase cost** posted to Finance as an EQUIPMENT expense (stored, not posted).
- [ ] **Group feed split by group history** in per-animal P&L (today: the animal's current group).
- [ ] **Workers mark their own attendance** (today owner only).

## Home, alerts & reports (Canvas 6)
- [ ] **Rule tests for feed and finance & ops rules** (reproduction, calves, health and milk are covered).
- [ ] **Direct cull actions** (sold / dry) from the cull advisor without leaving the page.

## Housekeeping
- [ ] `docs/api.md`: older draft rows for Modules 5–15 — the per-module contracts (`api-*.md`) are the source of truth.
- [ ] Open questions in `docs/requirements/alerts.md` (SCC > 200k, dry-off 60 vs 90 days) — confirm with the vet.
