# Dairy Farm — backend

Spring Boot 4.1 · Java 21 · Jackson 3 · JPA/Hibernate · Flyway · Supabase Postgres + Supabase Auth.
The React app signs in with Supabase and calls this API with the Supabase access token.

## Run locally

```bash
cd backend
export DB_URL=jdbc:postgresql://localhost:5432/dairy DB_USER=postgres DB_PASSWORD=postgres
export SUPABASE_URL=https://<project-ref>.supabase.co
mvn spring-boot:run          # http://localhost:8080, docs at /swagger-ui
```

Flyway creates the tables on start-up (`src/main/resources/db/migration`).

## Configuration

| Variable | Required | What it is |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | yes | Postgres connection. For Supabase use the **Session pooler** string from *Project Settings → Database*, e.g. `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require`, user `postgres.<project-ref>` |
| `SUPABASE_URL` | yes | Project URL, e.g. `https://abcd1234.supabase.co`. Tokens are checked against its signing keys (JWKS). |
| `SUPABASE_JWT_SECRET` | no | Only for projects still on the legacy shared JWT secret (HS256). |
| `SUPABASE_SERVICE_ROLE_KEY` | for logins and photos | Server-side secret. Needed to create worker logins, send invites and store photos. Never put it in the frontend. |
| `SUPABASE_PHOTO_BUCKET` | no | Storage bucket for animal photos (default `animal-photos`). |
| `INVITE_REDIRECT_URL` | no | Frontend page the invite email opens, e.g. `https://<site>/reset-password`. |
| `CORS_ORIGINS` | no | Comma-separated frontend origins (default `http://localhost:5173`). |

## Supabase project setup (one time)

1. **Authentication → Providers → Email**: keep *Confirm email* on. Team members added by email are
   linked to an account by its email address, so the address must be verified.
2. **Storage → New bucket** `animal-photos`, **private**. The API uploads with the service key and
   returns short-lived signed links.
3. **Authentication → URL configuration**: add the frontend URL to the redirect list (for invites
   and password reset).

## API

Base path `/api/v1`; farm data lives under `/api/v1/farms/{farmId}/…`. Every call needs
`Authorization: Bearer <Supabase access token>`. Owners can do everything; workers can read the herd,
groups and settings and add photos. Errors are `application/problem+json` with a plain-language
`detail` (and `errors` per field for validation). Full list: `docs/api.md` and `/swagger-ui`.

| Area | Endpoints |
|---|---|
| Me | `GET /me`, `PUT /me`, `PUT /me/preferences` |
| Farms & settings | `GET/POST /farms`, `GET/PUT /farms/{farmId}`, `GET/PUT /farms/{farmId}/settings` |
| Team | `/members` — add with no sign-in, an owner-set password, or an email invite |
| Animals | `/animals` — search, summary, by tag, status, group, photos, CSV template/import/export |
| Groups | `/groups` — CRUD and `PUT /groups/{id}/animals` |

## Tests

```bash
mvn verify                                   # starts Postgres with Testcontainers (needs Docker)
TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/dairy_test mvn verify   # or use an existing empty database
```

## Container

`docker build -t dairy-farm-backend backend/` — runs as a non-root user on port 8080 (OpenShift-ready).
