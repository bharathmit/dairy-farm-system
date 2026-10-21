# UI design — Module 1 (login, logout, settings, animals, groups)

Mobile-first screens (390 px wide). Source of truth while reviewing is the Design canvas:
https://claude.ai/artifact/KXtE8vB41BQHR9UAfsQ6UT — these files are a snapshot of it (2026-10-21).
Each `*.dc.html` file is one screen; `canvas.json` is the layout of the canvas.

| Area | Screens |
|---|---|
| Login & logout | Main (login: email + password, Google), CreateAccount, ForgotPassword, UpdatePassword, FarmPicker, AccountMenu, LogoutConfirm |
| Settings | SettingsHome, SettingsFarm, SettingsTeam, SettingsInvite, SettingsNotifications, SettingsPreferences (theme & language), SettingsMilkRate (milk rate, milking times, margin alert) |
| Animals | AnimalList, AnimalFilters, AnimalForm, AnimalProfile, AnimalStatus, AnimalImport, ScanTag |
| Groups | GroupList, GroupForm, GroupDetail |

Owner changes captured in this snapshot:
- Sign-in is email + password or Google; phone OTP removed.
- New screens: Create account, Update password (from reset link), Milk rate & margins.
- Margin alert = alert when total margin (milk income − all inputs and expenses) falls below X % (default 30 %), checked weekly.
- Milking times (morning / evening) set in Milk rate & margins.
- Team screen has All / Active / Inactive filter.
