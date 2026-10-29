import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { StoreProvider, useStore } from './data/store'
import { Toast } from './components/ui'
import Login from './pages/auth/Login'
import CreateAccount from './pages/auth/CreateAccount'
import ForgotPassword from './pages/auth/ForgotPassword'
import UpdatePassword from './pages/auth/UpdatePassword'
import FarmPicker from './pages/auth/FarmPicker'
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
import Home from './pages/Home'
import MilkHome from './pages/milk/MilkHome'
import MilkSheet from './pages/milk/MilkSheet'
import MilkSingle from './pages/milk/MilkSingle'
import MilkSale from './pages/milk/MilkSale'
import GradeRules from './pages/milk/GradeRules'
import WeightLog from './pages/weight/WeightLog'
import WeightTrend from './pages/weight/WeightTrend'
import HealthHome from './pages/health/HealthHome'
import HealthForm from './pages/health/HealthForm'
import HealthBatch from './pages/health/HealthBatch'
import WithdrawalList from './pages/health/WithdrawalList'
import Contacts from './pages/contacts/Contacts'
import ContactForm from './pages/contacts/ContactForm'
import BreedingHome from './pages/breeding/BreedingHome'
import HeatWatch from './pages/breeding/HeatWatch'
import HeatForm from './pages/breeding/HeatForm'
import ServiceForm from './pages/breeding/ServiceForm'
import PregnancyCheck from './pages/breeding/PregnancyCheck'
import BreedingHistory from './pages/breeding/BreedingHistory'
import Calving from './pages/breeding/Calving'
import SemenStock from './pages/breeding/SemenStock'
import FamilyTree from './pages/breeding/FamilyTree'
import CalfList from './pages/calves/CalfList'
import CalfFeeding from './pages/calves/CalfFeeding'
import CalfFeedingGroup from './pages/calves/CalfFeedingGroup'
import FeedHome from './pages/feed/FeedHome'
import FeedItems from './pages/feed/FeedItems'
import FeedItemForm from './pages/feed/FeedItemForm'
import FeedPurchase from './pages/feed/FeedPurchase'
import FeedUsage from './pages/feed/FeedUsage'
import FormulaList from './pages/feed/FormulaList'
import FormulaEditor from './pages/feed/FormulaEditor'
import PrepareBatch from './pages/feed/PrepareBatch'
import RationPlanner from './pages/feed/RationPlanner'
import RationRules from './pages/feed/RationRules'
import RationRuleEditor from './pages/feed/RationRuleEditor'
import FarmFeedNeed from './pages/feed/FarmFeedNeed'
import FinanceHome from './pages/money/FinanceHome'
import TransactionForm from './pages/money/TransactionForm'
import ProfitLoss from './pages/money/ProfitLoss'
import MoneyTrend from './pages/money/MoneyTrend'
import EntryList from './pages/money/EntryList'
import Workers from './pages/money/Workers'
import Attendance from './pages/money/Attendance'
import PayWages from './pages/money/PayWages'
import EquipmentList from './pages/money/EquipmentList'
import EquipmentForm from './pages/money/EquipmentForm'
import EquipmentService from './pages/money/EquipmentService'
import Procurement from './pages/money/Procurement'
import Checklist from './pages/alerts/Checklist'
import AlertsList from './pages/alerts/AlertsList'
import AlertDetail from './pages/alerts/AlertDetail'
import ReminderForm from './pages/alerts/ReminderForm'
import AlertRules from './pages/alerts/AlertRules'
import HealthSchedules from './pages/alerts/HealthSchedules'
import Reports from './pages/reports/Reports'
import ReportView from './pages/reports/ReportView'
import CullAdvisor from './pages/reports/CullAdvisor'
import CompareCows from './pages/reports/CompareCows'

function Loading() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-2 px-8 text-center text-ink-3" role="status">
      <div className="text-base font-semibold text-ink">Loading…</div>
      <div className="text-sm">The first visit after a quiet spell can take up to a minute while the server wakes up.</div>
    </div>
  )
}

function RequireAuth() {
  const { ready, user } = useStore()
  const loc = useLocation()
  if (!ready) return <Loading />
  return user ? <Outlet /> : <Navigate to="/login" replace state={{ from: loc.pathname }} />
}

function RequireFarm() {
  const { farm, loading } = useStore()
  if (!farm) return <Navigate to="/farms" replace />
  // Pages read the farm's data when they open (e.g. edit forms), so wait for it.
  return loading ? <Loading /> : <Outlet />
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
              <Route path="/home" element={<Home />} />
              <Route path="/alerts" element={<AlertsList />} />
              <Route path="/alerts/:id" element={<AlertDetail />} />
              <Route path="/reminders/new" element={<ReminderForm />} />
              <Route path="/reminders/:id" element={<ReminderForm />} />
              <Route path="/checklist" element={<Checklist />} />
              <Route path="/reports" element={<Reports />} />
              <Route path="/reports/view/:type" element={<ReportView />} />
              <Route path="/reports/cull" element={<CullAdvisor />} />
              <Route path="/reports/compare" element={<CompareCows />} />
              <Route path="/settings/alert-rules" element={<AlertRules />} />
              <Route path="/settings/health-schedules" element={<HealthSchedules />} />
              <Route path="/animals" element={<AnimalList />} />
              <Route path="/animals/new" element={<AnimalForm />} />
              <Route path="/animals/import" element={<AnimalImport />} />
              <Route path="/animals/scan" element={<ScanTag />} />
              <Route path="/animals/:id" element={<AnimalProfile />} />
              <Route path="/animals/:id/edit" element={<AnimalForm />} />
              <Route path="/animals/:id/growth" element={<WeightTrend />} />
              <Route path="/animals/:id/family" element={<FamilyTree />} />
              <Route path="/breeding" element={<BreedingHome />} />
              <Route path="/breeding/heat-watch" element={<HeatWatch />} />
              <Route path="/breeding/heat/new" element={<HeatForm />} />
              <Route path="/breeding/service/new" element={<ServiceForm />} />
              <Route path="/breeding/service/:id" element={<ServiceForm />} />
              <Route path="/breeding/pregnancy-check" element={<PregnancyCheck />} />
              <Route path="/breeding/calving" element={<Calving />} />
              <Route path="/breeding/semen" element={<SemenStock />} />
              <Route path="/breeding/animals/:id" element={<BreedingHistory />} />
              <Route path="/calves" element={<CalfList />} />
              <Route path="/calves/feeding" element={<CalfFeeding />} />
              <Route path="/calves/feeding/group" element={<CalfFeedingGroup />} />
              <Route path="/feed" element={<FeedHome />} />
              <Route path="/feed/items" element={<FeedItems />} />
              <Route path="/feed/items/new" element={<FeedItemForm />} />
              <Route path="/feed/items/:id" element={<FeedItemForm />} />
              <Route path="/feed/buy" element={<FeedPurchase />} />
              <Route path="/feed/log" element={<FeedUsage />} />
              <Route path="/feed/formulas" element={<FormulaList />} />
              <Route path="/feed/formulas/new" element={<FormulaEditor />} />
              <Route path="/feed/formulas/:id" element={<FormulaEditor />} />
              <Route path="/feed/batch" element={<PrepareBatch />} />
              <Route path="/feed/ration" element={<RationPlanner />} />
              <Route path="/feed/ration-rules" element={<RationRules />} />
              <Route path="/feed/ration-rules/:category" element={<RationRuleEditor />} />
              <Route path="/feed/need" element={<FarmFeedNeed />} />
              <Route path="/money" element={<FinanceHome />} />
              <Route path="/money/entries" element={<EntryList />} />
              <Route path="/money/entries/new" element={<TransactionForm />} />
              <Route path="/money/entries/:id" element={<TransactionForm />} />
              <Route path="/money/pnl" element={<ProfitLoss />} />
              <Route path="/money/trend/:kind" element={<MoneyTrend />} />
              <Route path="/money/workers" element={<Workers />} />
              <Route path="/money/attendance" element={<Attendance />} />
              <Route path="/money/pay" element={<PayWages />} />
              <Route path="/money/equipment" element={<EquipmentList />} />
              <Route path="/money/equipment/new" element={<EquipmentForm />} />
              <Route path="/money/equipment/:id" element={<EquipmentForm />} />
              <Route path="/money/equipment/:id/service" element={<EquipmentService />} />
              <Route path="/money/service" element={<EquipmentService />} />
              <Route path="/money/procurement" element={<Procurement />} />
              <Route path="/milk" element={<MilkHome />} />
              <Route path="/milk/sheet" element={<MilkSheet />} />
              <Route path="/milk/cow" element={<MilkSingle />} />
              <Route path="/milk/sale" element={<MilkSale />} />
              <Route path="/weight/new" element={<WeightLog />} />
              <Route path="/health" element={<HealthHome />} />
              <Route path="/health/new" element={<HealthForm />} />
              <Route path="/health/batch" element={<HealthBatch />} />
              <Route path="/health/withdrawals" element={<WithdrawalList />} />
              <Route path="/health/:id" element={<HealthForm />} />
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
              <Route path="/settings/milk-grading" element={<GradeRules />} />
              <Route path="/settings/contacts" element={<Contacts />} />
              <Route path="/settings/contacts/new" element={<ContactForm />} />
              <Route path="/settings/contacts/:id" element={<ContactForm />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/home" replace />} />
        </Routes>
        <GlobalToast />
      </BrowserRouter>
    </StoreProvider>
  )
}
