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
