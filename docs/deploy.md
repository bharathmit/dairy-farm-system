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
   | Project / Worker name | `dairy-farm-system` (must match `name` in `frontend/wrangler.jsonc`) |
   | Branch | `main` |
   | Root directory (advanced) | `frontend` |
   | Build command | `npm run build` |
   | Deploy command | `npx wrangler deploy` |
   | Build variable | `NODE_VERSION` = `22` |

4. **Create and deploy.** The site goes live at `https://dairy-farm-system.bharathkumar-feb14.workers.dev`.

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
2. Delete any half-made `dairy-farm-system` project left by the failed attempt (open it → **Settings** →
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

## Run locally

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
npm run build    # production build into dist/
```

## Current state

- Data is sample data held in memory. It resets on reload. Any email and password signs in.
- The backend API lives on `module/2-backend` / `main` (deployed to Render); connecting the
  frontend to it, through a `VITE_API_URL` variable, is the next step.
