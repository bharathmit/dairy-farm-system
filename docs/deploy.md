# Deploying the UI (Cloudflare Pages, free)

The frontend is a static React build, so it runs on Cloudflare Pages' free plan
(unlimited bandwidth, commercial use allowed). Pages rebuilds on every push.

## One-time setup

1. Sign in at <https://dash.cloudflare.com> (free account).
2. **Workers & Pages → Create → Pages → Connect to Git**, authorise GitHub and pick
   `bharathmit/dairy-farm-system`.
3. Build settings:

   | Setting | Value |
   |---|---|
   | Production branch | `main` |
   | Framework preset | `React (Vite)` |
   | Root directory | `frontend` |
   | Build command | `npm run build` |
   | Build output directory | `dist` |
   | Environment variable | `NODE_VERSION` = `22` |

4. **Save and Deploy.** The site goes live at `https://<project>.pages.dev`.
   Pull requests get their own preview URL automatically.

There is no `404.html`, so Pages serves `index.html` for every path and React Router
handles deep links such as `/animals/a1`.

## Backend API (Render, free)

`render.yaml` at the repo root describes the API service (Docker, Singapore, free plan).

1. Sign in at <https://dashboard.render.com> → **New → Blueprint** → connect GitHub and pick
   `bharathmit/dairy-farm-system`.
2. Render reads `render.yaml` and asks for the secret values:
   `DB_URL` (Supabase → Connect → Session pooler, as `jdbc:postgresql://…:5432/postgres?sslmode=require`),
   `DB_PASSWORD`, `SUPABASE_SERVICE_ROLE_KEY`, `CORS_ORIGINS` (the Pages URL) and `INVITE_REDIRECT_URL`.
3. **Apply.** The API comes up at `https://dairy-farm-api.onrender.com` (health: `/actuator/health`,
   docs: `/swagger-ui`). Later commits to `main` deploy after CI passes.

The free instance sleeps after 15 minutes without requests; the next request wakes it in about a minute.

## Run locally

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
npm run build    # production build into dist/
```

## Current state

- Data is sample data held in memory. It resets on reload. Any email and password signs in.
- When the backend exists, the Spring Boot API will be hosted separately (options noted in
  PROGRESS.md) and the frontend will call it through an `VITE_API_URL` variable.
