# REST API

**Built (Modules 1–4):** Me, Farms, Settings, Members, Animals, Groups — see `/swagger-ui` on the running
backend for request and response shapes. Everything below them is still a draft for later modules.

Base path `/api/v1`. JSON. Auth: `Authorization: Bearer <Supabase JWT>`.
Spring Boot validates the JWT (OAuth 2.0 resource server, Supabase JWKS).

**Multi-farm:** every farm resource is under `/api/v1/farms/{farmId}/…` (paths below are relative to
that prefix). The backend checks that the caller is a member of `{farmId}` and loads the role from
`farm_member`. Only `/me` and `/farms` sit outside the prefix.
Units are fixed: INR, kg, litres. Lists are paged (`page`, `size`, `sort`) and filterable by `from`, `to`, `species`,
`groupId`, `animalId` where relevant.

Roles: **O** = Owner only · **O/W** = Owner and Worker. Workers can create daily records and read
animals; they cannot delete, see finances or change settings.

| Area | Method & path | Role |
|---|---|---|
| Me | `GET /me` (profile, preferences, farms with role and herd size) · `PUT /me` (name, phone) · `PUT /me/preferences` (theme, language) — outside farm prefix | any |
| Farms | `GET/POST /farms` — outside farm prefix · `GET /farms/{farmId}` · `PUT /farms/{farmId}` | any / O/W / O |
| Settings | `GET /settings` · `PUT /settings` (partial: milk rate, milking times, margin %, notifications) | O/W / O |
| Members | `GET/POST /members` · `GET/PUT/DELETE /members/{id}` · `PATCH /members/{id}/status` (active / left on) · `POST /members/{id}/invite` (resend). Sign-in per member: `login` NONE, PASSWORD (owner sets it) or INVITE (email link) | O |
| Animals | `GET /animals` (q, species, category, status, sex, groupId, noGroup, sort=tag/name/newest/oldest, page, size) · `GET /animals/summary` | O/W |
| | `GET /animals/details` (same filters, full records with parents and photo links — used by the app) | O/W |
| | `GET /animals/{id}` · `GET /animals/by-tag/{tag}` (QR lookup) — prices hidden for workers | O/W |
| | `POST /animals` · `PUT /animals/{id}` | O |
| | `PATCH /animals/{id}/status` (SOLD/DEAD/TRANSFERRED/ACTIVE + date, price, reason, party) · `PATCH /animals/{id}/group` | O |
| | `DELETE /animals/{id}` · `POST /animals/bulk-delete` | O |
| | `GET /animals/import/template` · `POST /animals/import?dryRun=true|false` (CSV) · `GET /animals/export.csv` | O |
| | `POST /animals/{id}/photos` (multipart `file`) | O/W |
| | `DELETE /animals/{id}/photos/{photoId}` | O |
| Groups | `GET /groups` · `GET /groups/{id}` (with animals) | O/W |
| | `POST /groups` · `PUT /groups/{id}` · `PUT /groups/{id}/animals` (set members) · `DELETE /groups/{id}` | O |
| Lineage | `GET /animals/{id}/lineage` (parents, siblings, offspring) | O/W |
| | `GET /animals/{id}/mate-risk?mateId=` (shared ancestors) | O |
| Measurements | `GET/POST /animals/{id}/measurements` (weight, height, BCS) · `PUT/DELETE /measurements/{id}` | O/W (delete O) |
| Heat | `GET/POST /animals/{id}/heats` · `PUT/DELETE /heats/{id}` | O/W (delete O) |
| Health | `GET/POST /animals/{id}/health` · `PUT/DELETE /health/{id}` | O/W (delete O) |
| | `POST /groups/{id}/health` (batch for all members) | O/W |
| | `PATCH /health/{id}/done` | O/W |
| Breeding | `GET/POST /animals/{id}/breedings` · `PUT/DELETE /breedings/{id}` | O/W (delete O) |
| | `POST /breedings/{id}/calving` (records birth, creates calf) | O/W |
| Semen | `GET/POST/PUT /semen-batches` · `GET /semen-batches/stock` | O |
| Health schedules | `GET/POST/PUT/DELETE /health-schedules` | O |
| Calf feeding | `GET/POST /animals/{id}/calf-feeding` · `PUT/DELETE /calf-feeding/{id}` | O/W |
| Growth targets | `GET/PUT /growth-targets` | O |
| Feed items | `GET/POST/PUT /feed-items` · `GET /feed-items/stock` | O (GET O/W) |
| Feed | `GET/POST /feed-records` · `PUT/DELETE /feed-records/{id}` | O/W (delete O) |
| | `GET /feed-records/summary` (used, cost, avg/day, days of stock left) | O |
| Yield | `GET/POST /yield-records` · `PUT/DELETE /yield-records/{id}` | O/W (delete O) |
| | `POST /yield-records/batch` (herd milking sheet: AM + PM for all milking cows) · `GET /yield-records/sheet?date=` (pre-filled from last records) | O/W |
| | `GET /yield-records/summary` | O |
| Finance | `GET/POST /finance-entries` · `PUT/DELETE /finance-entries/{id}` | O |
| | `GET /finance-entries/summary` (income, expense, net, top category) | O |
| Workers & labour | `GET/POST/PUT /workers` · `GET/POST /labour-records` · `PATCH /labour-records/{id}/paid` | O |
| Equipment | `GET/POST/PUT /equipment` · `POST /equipment/{id}/services` | O |
| Procurement | `GET/POST/PUT/DELETE /procurement-notes` | O |
| Alerts | `GET /alerts?status=OPEN&category=` (workers get worker-visible only) · `PATCH /alerts/{id}` (done/dismiss) · `POST /alerts/bulk-done` | O/W |
| | `POST /alerts` (manual reminder: title, notes, type, date/time, animal) | O/W |
| Daily checklist | `GET /daily-tasks?date=` · `PATCH /daily-tasks/{id}/log?date=` (tick/untick) · `POST/PUT /daily-tasks` (custom) | O/W |
| Barn conditions | `POST /barn-conditions` · `GET /barn-conditions/latest` (THI + advice) | O/W |
| Alert rules | `GET /alert-rules` · `PUT /alert-rules/{id}` (thresholds, days, worker visibility) | O |
| Herd analytics | `GET /analytics/cull-advisor` (scores + class) · `GET /analytics/roi-ranking` · `GET /analytics/compare?a=&b=` · `GET /analytics/iofc` | O |
| Search | `GET /search?q=` (animals, records, transactions) | O/W |
| Dashboard | `GET /dashboard` (upcoming health actions, due dates, species mix, recent animals, today's milk) | O/W |
| Reports | `GET /reports/summary` (overview, health, breeding, growth KPIs) | O |
| | `GET /reports/pnl?scope=farm|animal&animalId=` | O |
| | `GET /reports/metrics/{metric}/animals` (drill-down behind a KPI) | O |
| Export | `GET /export/{animals|feed|yield|finance|report}.csv` · `GET /reports/summary.pdf` · drill-down CSV/PDF | O |

Errors: RFC 7807 `application/problem+json`. Validation with Jakarta Bean Validation.
API docs: springdoc OpenAPI at `/swagger-ui`.
