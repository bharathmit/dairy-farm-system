# REST API — DRAFT for review

Base path `/api/v1`. JSON. Auth: `Authorization: Bearer <Supabase JWT>`.
Spring Boot validates the JWT (OAuth 2.0 resource server, Supabase JWKS) and loads the role from
`app_user`. Lists are paged (`page`, `size`, `sort`) and filterable by `from`, `to`, `species`,
`groupId`, `animalId` where relevant.

Roles: **O** = Owner only · **O/W** = Owner and Worker. Workers can create daily records and read
animals; they cannot delete, see finances or change settings.

| Area | Method & path | Role |
|---|---|---|
| Me | `GET /me` | O/W |
| Users | `GET/POST/PUT /users`, `PUT /users/{id}/role` | O |
| Animals | `GET /animals` (search, species, group, status, sort) | O/W |
| | `GET /animals/{id}` · `GET /animals/by-tag/{tag}` (QR lookup) | O/W |
| | `POST /animals` · `PUT /animals/{id}` | O |
| | `PATCH /animals/{id}/status` (sold/dead/transferred + exit details) | O |
| | `DELETE /animals/{id}` · `POST /animals/bulk-delete` | O |
| | `POST /animals/import` (CSV) · `GET /animals/import/template` | O |
| | `POST /animals/{id}/photo` | O/W |
| Lineage | `GET /animals/{id}/lineage` (parents, siblings, offspring) | O/W |
| | `GET /animals/{id}/mate-risk?mateId=` (shared ancestors) | O |
| Weight | `GET/POST /animals/{id}/weights` · `PUT/DELETE /weights/{id}` | O/W (delete O) |
| Health | `GET/POST /animals/{id}/health` · `PUT/DELETE /health/{id}` | O/W (delete O) |
| | `POST /groups/{id}/health` (batch for all members) | O/W |
| | `PATCH /health/{id}/done` | O/W |
| Breeding | `GET/POST /animals/{id}/breedings` · `PUT/DELETE /breedings/{id}` | O/W (delete O) |
| | `POST /breedings/{id}/calving` (records birth, creates calf) | O/W |
| Groups | `GET/POST/PUT/DELETE /groups` · `PUT /groups/{id}/members` | O (GET O/W) |
| Feed items | `GET/POST/PUT /feed-items` · `GET /feed-items/stock` | O (GET O/W) |
| Feed | `GET/POST /feed-records` · `PUT/DELETE /feed-records/{id}` | O/W (delete O) |
| | `GET /feed-records/summary` (used, cost, avg/day, days of stock left) | O |
| Yield | `GET/POST /yield-records` · `PUT/DELETE /yield-records/{id}` | O/W (delete O) |
| | `POST /yield-records/batch` (one milking session, all cows) | O/W |
| | `GET /yield-records/summary` | O |
| Finance | `GET/POST /finance-entries` · `PUT/DELETE /finance-entries/{id}` | O |
| | `GET /finance-entries/summary` (income, expense, net, top category) | O |
| Dashboard | `GET /dashboard` (upcoming health actions, due dates, species mix, recent animals, today's milk) | O/W |
| Reports | `GET /reports/summary` (overview, health, breeding, growth KPIs) | O |
| | `GET /reports/pnl?scope=farm|animal&animalId=` | O |
| Export | `GET /export/{animals|feed|yield|finance|report}.csv` · `GET /reports/summary.pdf` | O |
| Settings | `GET/PUT /settings` | O |
| Reminders | `GET /reminders?days=30` | O/W |

Errors: RFC 7807 `application/problem+json`. Validation with Jakarta Bean Validation.
API docs: springdoc OpenAPI at `/swagger-ui`.
