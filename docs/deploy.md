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
