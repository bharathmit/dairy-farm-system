---
description: Run backend tests and frontend lint/build; report only failures
---
Run the checks for the parts changed in this session (both if unsure):

- Backend (from `backend/`): if `TEST_DATABASE_URL` is set use `mvn -q -B test`, otherwise `mvn -q -B verify` (Docker).
- Frontend (from `frontend/`): `npm run lint` and `npm run build` (run `npm ci` first if `node_modules` is missing).

Report pass/fail per part. For failures show only the failing test names and the key assertion/error lines,
then propose the smallest fix. Don't change expected values in tests without explaining why the new value is right.
