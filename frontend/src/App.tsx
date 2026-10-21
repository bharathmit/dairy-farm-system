import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { StoreProvider, useStore } from './data/store'
import { Toast } from './components/ui'
import Login from './pages/auth/Login'
import CreateAccount from './pages/auth/CreateAccount'
import ForgotPassword from './pages/auth/ForgotPassword'
import UpdatePassword from './pages/auth/UpdatePassword'
import FarmPicker from './pages/auth/FarmPicker'
import Placeholder from './pages/Placeholder'
import SettingsHome from './pages/settings/SettingsHome'
import SettingsFarm from './pages/settings/SettingsFarm'
import SettingsTeam from './pages/settings/SettingsTeam'
import MemberForm from './pages/settings/MemberForm'
import SettingsNotifications from './pages/settings/SettingsNotifications'
import SettingsPreferences from './pages/settings/SettingsPreferences'
import SettingsMilkRate from './pages/settings/SettingsMilkRate'
import AnimalList from './pages/animals/AnimalList'
import AnimalForm from './pages/animals/AnimalForm'
import AnimalProfile from './pages/animals/AnimalProfile'
import AnimalImport from './pages/animals/AnimalImport'
import ScanTag from './pages/animals/ScanTag'
import GroupList from './pages/groups/GroupList'
import GroupForm from './pages/groups/GroupForm'
import GroupDetail from './pages/groups/GroupDetail'

function RequireAuth() {
  const { user } = useStore()
  const loc = useLocation()
  return user ? <Outlet /> : <Navigate to="/login" replace state={{ from: loc.pathname }} />
}

function RequireFarm() {
  const { farm } = useStore()
  return farm ? <Outlet /> : <Navigate to="/farms" replace />
}

function GlobalToast() {
  const { toast } = useStore()
  return <Toast text={toast} />
}

export default function App() {
  return (
    <StoreProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<CreateAccount />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<UpdatePassword />} />
          <Route element={<RequireAuth />}>
            <Route path="/farms" element={<FarmPicker />} />
            <Route element={<RequireFarm />}>
              <Route path="/home" element={<Placeholder title="Home" tab="home" />} />
              <Route path="/alerts" element={<Placeholder title="Alerts" tab="alerts" />} />
              <Route path="/animals" element={<AnimalList />} />
              <Route path="/animals/new" element={<AnimalForm />} />
              <Route path="/animals/import" element={<AnimalImport />} />
              <Route path="/animals/scan" element={<ScanTag />} />
              <Route path="/animals/:id" element={<AnimalProfile />} />
              <Route path="/animals/:id/edit" element={<AnimalForm />} />
              <Route path="/groups" element={<GroupList />} />
              <Route path="/groups/new" element={<GroupForm />} />
              <Route path="/groups/:id" element={<GroupDetail />} />
              <Route path="/groups/:id/edit" element={<GroupForm />} />
              <Route path="/settings" element={<SettingsHome />} />
              <Route path="/settings/farm" element={<SettingsFarm />} />
              <Route path="/settings/team" element={<SettingsTeam />} />
              <Route path="/settings/team/new" element={<MemberForm />} />
              <Route path="/settings/team/:id" element={<MemberForm />} />
              <Route path="/settings/notifications" element={<SettingsNotifications />} />
              <Route path="/settings/preferences" element={<SettingsPreferences />} />
              <Route path="/settings/milk-rate" element={<SettingsMilkRate />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/animals" replace />} />
        </Routes>
        <GlobalToast />
      </BrowserRouter>
    </StoreProvider>
  )
}
