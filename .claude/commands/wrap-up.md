---
description: Finish a task — update PROGRESS.md, update docs if the API changed, commit and push to main
---
1. Make sure `/check` passed in this session (run it if not).
2. Update `PROGRESS.md`: tick or add one concise line for what was done; add decisions/blockers if any.
3. If an endpoint, request/response field or calculation rule changed, update the matching `docs/api-*.md`
   (and the "Domain rules" section of CLAUDE.md if a core rule changed).
4. `git add` only the files changed for this task; commit with a short summary line and bullet details.
5. `git fetch origin main`, rebase if needed, push to `main` (this deploys — confirm with me first),
   then reply with the commit hash and a 2–3 line summary.
