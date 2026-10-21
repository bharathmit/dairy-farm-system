# Dairy Farm — frontend

React 18 + Vite + TypeScript + Tailwind CSS. Mobile-first; on wide screens the app sits in a phone-width column.

```bash
npm install
npm run dev     # http://localhost:5173
npm run build
npm run lint
```

## Layout

```
src/
  App.tsx                 routes + auth/farm guards
  data/types.ts           UI data shapes (mirror the planned API)
  data/seed.ts            sample herd, groups, team, settings
  data/store.tsx          in-memory store (swap for API calls later)
  components/ui.tsx       Screen, TopBar, ActionBar, Footer, Sheet, Dialog, Segmented, Chips, Switch, PasswordInput, BottomNav
  components/icons.tsx    stroke icons
  components/AccountButton.tsx  account menu sheet + logout dialog
  pages/auth/             Login, CreateAccount, ForgotPassword, UpdatePassword, FarmPicker
  pages/settings/         SettingsHome, Farm, Team, MemberForm, Notifications, Preferences, MilkRate
  pages/animals/          AnimalList (+filters), AnimalForm, AnimalProfile (+status sheet), AnimalImport, ScanTag
  pages/groups/           GroupList, GroupForm, GroupDetail
```

Data is in memory only and resets on reload; any email/password signs in. Deployment: see `../docs/deploy.md`.
