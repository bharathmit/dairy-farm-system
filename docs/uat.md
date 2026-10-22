# User acceptance testing (first release)

- **Checklist:** https://claude.ai/artifact/Q7QYpwkVFCbURTY4W82LEb (59 checks across every module, owner and worker).
  Mark each check Pass / Fail / Blocked on the phone and add a note for anything wrong.
- **App under test:** https://dairy-farm-system.bharathkumar-feb14.workers.dev (backend on Render; first open after
  idle takes ~1 minute while the free server wakes).
- **Bug fixing:** failed checks and their notes are read back from the checklist, fixed on `main` with a regression
  test, and the check is re-tested after the deploy.
- **Worker checks:** sign in as a team member created under Settings → Team with a password.
- Items that are agreed but not in this release are in `docs/backlog.md`; they are not UAT failures.
