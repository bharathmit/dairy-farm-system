# Deploying the UI (Cloudflare Workers static site, free)

The frontend is a static React build. Cloudflare serves it from a Worker with static assets
(`frontend/wrangler.jsonc`); static requests are free and unlimited. It rebuilds on every push.

## One-time setup

1. Sign in at <https://dash.cloudflare.com> → **Workers & Pages** → **Create application**.
2. Choose **Import a repository** (Continue with GitHub), authorise GitHub and pick
   `bharathmit/dairy-farm-system`.
3. Settings:

   | Setting | Value |
   |---|---|
   | Project / Worker name | `dairy-farm` (must match `name` in `frontend/wrangler.jsonc`) |
   | Branch | `main` |
   | Root directory (advanced) | `frontend` |
   | Build command | `npm run build` |
   | Deploy command | `npx wrangler deploy` |
   | Build variable | `NODE_VERSION` = `22` |

4. **Create and deploy.** The site goes live at `https://dairy-farm.<your-subdomain>.workers.dev`.

Unknown paths return `index.html` (`not_found_handling: single-page-application`), so React Router
handles deep links such as `/animals/a1`.

### If the import fails

**"Can't get repository for project with no associated Git installation"** means Cloudflare's GitHub
app can't read the repo yet.

1. Open <https://github.com/settings/installations> (GitHub → Settings → Applications → Installed GitHub Apps).
   - **Cloudflare Workers and Pages** is listed → **Configure** → **Repository access** → **All
     repositories**, or **Only select repositories** with `dairy-farm-system` → **Save**.
   - Not listed → in Cloudflare start **Create application → Import a repository** and click
     **Connect GitHub / + Add account**; install the app on your account with access to
     `dairy-farm-system` → **Install & Authorize**.
2. Delete any half-made `dairy-farm` project left by the failed attempt (open it → **Settings** →
   **Delete**); a project created without a Git connection keeps showing the error.
3. Run **Create application → Import a repository** again with the settings above.

**Fallback without Git** (no automatic redeploys; connect Git later under the Worker's
**Settings → Build**):

```bash
cd frontend
npm install && npm run build
npx wrangler login      # signs in to Cloudflare in the browser
npx wrangler deploy     # publishes dist/ and prints the workers.dev address
```

## Backend API (Render, free)

`render.yaml` at the repo root describes the API service (Docker, Singapore, free plan).

1. Sign in at <https://dashboard.render.com> → **New → Blueprint** → connect GitHub and pick
   `bharathmit/dairy-farm-system`.
2. Render reads `render.yaml` and asks for these values (the frontend address comes from the
   Cloudflare step above, e.g. `https://dairy-farm.<your-subdomain>.workers.dev`):

   | Variable | Where to get it |
   |---|---|
   | `DB_URL` | Supabase → **Connect** → **Session pooler**; enter as `jdbc:postgresql://<host>:5432/postgres?sslmode=require` |
   | `DB_PASSWORD` | The database password set when the Supabase project was created (reset under **Project Settings → Database**) |
   | `SUPABASE_SERVICE_ROLE_KEY` | Supabase → **Project Settings → API Keys → Secret keys** (`sb_secret_…`; create one if none is listed), or the legacy `service_role` key. Server only — never in the frontend, GitHub or chat |
   | `CORS_ORIGINS` | The frontend address, no trailing slash; add `,http://localhost:5173` for local testing |
   | `INVITE_REDIRECT_URL` | The frontend address + `/reset-password` |

   No frontend address yet? Enter `http://localhost:5173` and `http://localhost:5173/reset-password`
   and change them later under the service → **Environment** (Render restarts it on save).
3. **Apply.** The API comes up at `https://dairy-farm-api.onrender.com` (health: `/actuator/health`,
   docs: `/swagger-ui`). Later commits to `main` deploy after CI passes.

The free instance sleeps after 15 minutes without requests; the next request wakes it in about a minute.

Then in Supabase → **Authentication → URL Configuration**, add `<frontend address>/reset-password`
to **Redirect URLs**, so invite and password-reset emails can open it.

## Run locally

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
npm run build    # production build into dist/
```

## Current state

- Data is sample data held in memory. It resets on reload. Any email and password signs in.
- The backend API runs on Render (above); connecting the frontend to it, through a `VITE_API_URL`
  variable, is the next step.
