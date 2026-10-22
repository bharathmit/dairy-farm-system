// Home, checklist, alerts, schedules and reports: API shapes and calls. Contract: docs/api-home-alerts.md.
import { api } from '../lib/api'
import type { AnimalRef } from './breeding'
import type { MemberRef } from './money'

export type AlertCategory = 'REPRODUCTION' | 'CALF' | 'HEALTH' | 'MILK' | 'FEED' | 'FINANCE_OPS'
export type Severity = 'HIGH' | 'MEDIUM' | 'INFO'
export type AlertStatus = 'OPEN' | 'DONE' | 'DISMISSED'
export type Repeat = 'NONE' | 'DAILY' | 'WEEKLY' | 'MONTHLY'
export type ReminderType = 'HEALTH' | 'BREED' | 'FEED' | 'VET' | 'OTHER'
export type ActionKind = 'LOG_HEAT' | 'LOG_SERVICE' | 'LOG_PD' | 'LOG_CALVING' | 'LOG_WEIGHT' | 'LOG_HEALTH'
  | 'OPEN_FEED' | 'OPEN_PROCUREMENT' | 'OPEN_EQUIPMENT' | 'NONE'

export interface Alert {
  id: number; source: 'RULE' | 'REMINDER' | 'PROCUREMENT'; ruleCode?: string | null
  category: AlertCategory; severity: Severity; title: string; detail?: string | null
  animal?: (AnimalRef & { category?: string | null; breed?: string | null }) | null
  dueDate?: string | null; dueTime?: string | null; overdueDays?: number | null
  status: AlertStatus; snoozedUntil?: string | null; workerVisible: boolean; repeat?: Repeat | null; type?: ReminderType | null
  why?: string | null; facts?: { label: string; value: string }[] | null
  action?: { kind: ActionKind; label: string; params?: Record<string, string | number | null> | null } | null
  doneBy?: MemberRef | null; doneAt?: string | null; autoClosed?: boolean | null; notes?: string | null
}
export interface AlertsPage {
  counts: { all: number; byCategory: Partial<Record<AlertCategory, number>>; bySeverity: Partial<Record<Severity, number>>; overdue: number }
  overdue: Alert[]; today: Alert[]; thisWeek: Alert[]; later: Alert[]
}
export interface Rule { code: string; category: AlertCategory; title: string; enabled: boolean; workerVisible: boolean; params: Record<string, number | null>; alwaysOn?: boolean }

export type TaskStatus = 'DONE' | 'DUE' | 'LATE' | 'PENDING'
export interface HomeTask { key: string; title: string; status: TaskStatus; detail?: string | null; time?: string | null; link?: { kind: string; id?: number | string | null } | null }
export type BarnLevel = 'NONE' | 'MILD' | 'MODERATE' | 'SEVERE' | 'EMERGENCY'
export interface Barn { date: string; time?: string | null; temperatureC: number; humidityPct: number; thi: number; level: BarnLevel; advice?: string | null; yieldEffect?: string | null }
export interface Home {
  role: 'OWNER' | 'WORKER'; greeting: string; date: string
  tiles: {
    milkToday?: number | null; milkChangePct?: number | null; milkCompareNote?: string | null
    milkingCows: number; dryCows: number; herd: number; alertsOpen: number; alertsOverdue: number
    marginMonthPct?: number | null; marginAlertPct?: number | null
  }
  tasks: HomeTask[]; checklist: { done: number; total: number }; barn?: Barn | null
  topProducers?: { animal: AnimalRef; avgLitres7d: number }[] | null
  upcomingCalvings?: { animal: AnimalRef; dueDate: string; days: number; note?: string | null }[] | null
  healthReminders?: Alert[] | null
  feedingReminders?: { title: string; detail?: string | null; status: TaskStatus }[] | null
}
export interface Task {
  key: string; name: string; builtIn: boolean; done: boolean; doneBy?: MemberRef | null; doneAt?: string | null
  detail?: string | null; auto?: boolean; workerVisible?: boolean; link?: { kind: string; id?: number | string | null } | null
  id?: number | null; repeatDaily?: boolean
}
export interface Checklist { date: string; done: number; total: number; builtIn: Task[]; custom: Task[] }

export type ScheduleType = 'DEWORMING' | 'VACCINATION'
export type AppliesTo = 'CALVES_HEIFERS' | 'ALL' | 'MILKING'
export interface ScheduleStep { label: string; atAgeDays?: number | null; repeatEveryDays?: number | null; untilAgeDays?: number | null }
export interface Schedule { id: number; type: ScheduleType; name: string; appliesTo: AppliesTo; medicine?: string | null; steps: ScheduleStep[]; alertDaysBefore: number; workerVisible: boolean; active: boolean }

export type ReportType = 'milk' | 'cows' | 'breeding' | 'feed' | 'health' | 'pnl'
export interface ReportSummary { milkLitres: number; milkChangePct?: number | null; netProfit?: number | null; income?: number | null; feedCostPerLitre?: number | null; cull: { cull: number; monitor: number } }
export interface ReportData { title: string; period: string; columns: { key: string; label: string }[]; rows: Record<string, string | number | null>[]; totals?: Record<string, string | number | null> | null }
export type Verdict = 'KEEP' | 'MONITOR' | 'DRY_OFF' | 'CULL'
export interface CullRow {
  animal: AnimalRef & { breed?: string | null; ageYears?: number | null; lactationNumber?: number | null; daysInMilk?: number | null; category?: string | null }
  verdict: Verdict; score: number; production: number; reproduction: number; health: number; reasons: string[]
  lifetimeNet?: number | null; last90Net?: number | null; keepUntil?: string | null
}
export interface CowCard {
  animal: AnimalRef; breed?: string | null; status?: string | null; daysInMilk?: number | null; stage?: string | null
  litres7d: (number | null)[]; avgLitres?: number | null; trendPct?: number | null; fat30d?: number | null
  health: { count: number; cost?: number | null }; activeIssue?: string | null; breeding?: string | null; lifetimeNet?: number | null
}

// ---------- labels ----------

export const CATEGORY_LABEL: Record<AlertCategory, string> = {
  REPRODUCTION: 'Reproduction', CALF: 'Calves', HEALTH: 'Health', MILK: 'Milk', FEED: 'Feed', FINANCE_OPS: 'Finance & ops',
}
export const CATEGORY_TONE: Record<AlertCategory, string> = {
  REPRODUCTION: 'bg-[#F1EEF8] text-[#4B3A7A]', CALF: 'bg-warn-soft text-warn-ink', HEALTH: 'bg-danger-soft text-danger-text',
  MILK: 'bg-info-soft text-info-text', FEED: 'bg-brand-soft text-brand', FINANCE_OPS: 'bg-ground text-ink-2',
}
export const SEVERITY_LABEL: Record<Severity, string> = { HIGH: 'High', MEDIUM: 'Medium', INFO: 'Info' }
export const SEVERITY_TONE: Record<Severity, string> = { HIGH: 'bg-danger-soft text-danger-text', MEDIUM: 'bg-warn-soft text-warn-ink', INFO: 'bg-info-soft text-info-text' }
export const SEVERITY_DOT: Record<Severity, string> = { HIGH: '#A3271B', MEDIUM: '#C2410C', INFO: '#1F4E87' }
export const REMINDER_TYPES: { value: ReminderType; label: string }[] = [
  { value: 'HEALTH', label: 'Health' }, { value: 'BREED', label: 'Breed' }, { value: 'FEED', label: 'Feed' },
  { value: 'VET', label: 'Vet' }, { value: 'OTHER', label: 'Other' },
]
export const REPEATS: { value: Repeat; label: string }[] = [
  { value: 'NONE', label: 'Does not repeat' }, { value: 'DAILY', label: 'Daily' }, { value: 'WEEKLY', label: 'Weekly' }, { value: 'MONTHLY', label: 'Monthly' },
]
export const BARN_LABEL: Record<BarnLevel, string> = {
  NONE: 'No heat stress', MILD: 'Mild stress', MODERATE: 'Moderate stress', SEVERE: 'Severe stress', EMERGENCY: 'Emergency',
}
export const VERDICT_LABEL: Record<Verdict, string> = { KEEP: 'Keep', MONITOR: 'Monitor', DRY_OFF: 'Dry-off', CULL: 'Cull' }
export const VERDICT_TONE: Record<Verdict, string> = {
  KEEP: 'bg-brand-soft text-brand', MONITOR: 'bg-warn-soft text-warn-ink', DRY_OFF: 'bg-info-soft text-info-text', CULL: 'bg-danger-soft text-danger-text',
}
export const REPORTS: { type: ReportType; title: string; sub: string }[] = [
  { type: 'milk', title: 'Milk production', sub: 'Per cow, AM/PM, fat/SNF' },
  { type: 'cows', title: 'Cow performance', sub: 'L/day, DIM, lifetime P&L' },
  { type: 'breeding', title: 'Breeding', sub: 'Heats, AI, conception, calvings' },
  { type: 'feed', title: 'Feed consumption', sub: 'kg used, stock, cost per L' },
  { type: 'health', title: 'Health records', sub: 'Treatments, vaccines, cost' },
  { type: 'pnl', title: 'Profit & loss', sub: 'Income, expenses, margin %' },
]

/** Where an alert's main button goes (forms get the animal and any extra params prefilled). */
export function actionLink(a: Alert): string | null {
  const act = a.action
  if (!act || act.kind === 'NONE') return null
  const p = act.params ?? {}
  const animal = a.animal?.id
  const qs = (o: Record<string, string | number | null | undefined>) => {
    const s = new URLSearchParams()
    Object.entries(o).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') s.set(k, String(v)) })
    const t = s.toString()
    return t ? `?${t}` : ''
  }
  switch (act.kind) {
    case 'LOG_HEAT': return `/breeding/heat/new${qs({ animal })}`
    case 'LOG_SERVICE': return `/breeding/service/new${qs({ animal, back: '/alerts' })}`
    case 'LOG_PD': return `/breeding/pregnancy-check${qs({ animal, service: p.serviceId, back: '/alerts' })}`
    case 'LOG_CALVING': return `/breeding/calving${qs({ animal })}`
    case 'LOG_WEIGHT': return `/weight/new${qs({ animal })}`
    case 'LOG_HEALTH': return `/health/new${qs({ animal, type: p.type, medicine: p.medicine, schedule: p.scheduleId, step: p.step, back: '/alerts' })}`
    case 'OPEN_FEED': return p.itemId ? `/feed/buy${qs({ item: p.itemId })}` : '/feed'
    case 'OPEN_PROCUREMENT': return '/money/procurement'
    case 'OPEN_EQUIPMENT': return p.equipmentId ? `/money/equipment/${p.equipmentId}/service` : '/money/equipment'
    default: return null
  }
}

/** Where a home task or checklist row goes. */
export function taskLink(t: { key?: string; link?: { kind: string; id?: number | string | null } | null }): string {
  const k = t.link?.kind ?? t.key ?? ''
  switch (k) {
    case 'MILK_AM': return '/milk/sheet?session=AM'
    case 'MILK_PM': return '/milk/sheet?session=PM'
    case 'FEED_AM': return '/feed/log?session=AM'
    case 'FEED_PM': return '/feed/log?session=PM'
    case 'FEED_STOCK': return '/feed'
    case 'HEAT_STRESS': return '/home'
    case 'ALERT': return t.link?.id ? `/alerts/${t.link.id}` : '/alerts'
    case 'CHECKLIST': return '/checklist'
    case 'ANIMAL': return t.link?.id ? `/animals/${t.link.id}` : '/animals'
    default: return '/alerts'
  }
}

/** "3 d overdue" / "due today" / "Sun 25 Oct". */
export function dueText(a: Alert, today: string): string {
  if (a.overdueDays && a.overdueDays > 0) return `${a.overdueDays} d overdue`
  if (!a.dueDate) return ''
  if (a.dueDate === today) return a.dueTime ? `today ${a.dueTime.slice(0, 5)}` : 'due today'
  const d = new Date(`${a.dueDate}T00:00:00`)
  return d.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })
}

// ---------- calls ----------

const q = (params: Record<string, string | number | boolean | undefined | null>) => {
  const s = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') s.set(k, String(v)) })
  const t = s.toString()
  return t ? `?${t}` : ''
}

export const alertsApi = (farmId: string) => {
  const b = `/farms/${farmId}`
  return {
    home: () => api.get<Home>(`${b}/home`),
    checklist: (date?: string) => api.get<Checklist>(`${b}/checklist${q({ date })}`),
    tick: (key: string, done: boolean, date?: string) => api.put<Checklist>(`${b}/checklist/${encodeURIComponent(key)}${q({ date })}`, { done }),
    saveTask: (body: { name: string; repeatDaily: boolean; workerVisible: boolean }, id?: number) =>
      (id ? api.put<Task>(`${b}/checklist/tasks/${id}`, body) : api.post<Task>(`${b}/checklist/tasks`, body)),
    deleteTask: (id: number) => api.del(`${b}/checklist/tasks/${id}`),
    saveBarn: (body: { date: string; time: string; temperatureC: number; humidityPct: number }) => api.post<Barn>(`${b}/barn-readings`, body),
    alerts: (p: { category?: AlertCategory; severity?: Severity; status?: AlertStatus; animalId?: string | number } = {}) =>
      api.get<AlertsPage>(`${b}/alerts${q({ status: 'OPEN', ...p })}`),
    alert: (id: string | number) => api.get<Alert>(`${b}/alerts/${id}`),
    done: (id: number) => api.post<Alert>(`${b}/alerts/${id}/done`, {}),
    dismiss: (id: number) => api.post<Alert>(`${b}/alerts/${id}/dismiss`, {}),
    snooze: (id: number, until: string) => api.post<Alert>(`${b}/alerts/${id}/snooze`, { until }),
    refresh: () => api.post<{ created: number; closed: number }>(`${b}/alerts/refresh`, {}),
    saveReminder: (body: Record<string, unknown>, id?: string | number) =>
      (id ? api.put<Alert>(`${b}/reminders/${id}`, body) : api.post<Alert>(`${b}/reminders`, body)),
    deleteReminder: (id: number) => api.del(`${b}/reminders/${id}`),
    rules: () => api.get<Rule[]>(`${b}/alerts/rules`),
    saveRules: (rules: Rule[]) => api.put<Rule[]>(`${b}/alerts/rules`, rules),
    schedules: () => api.get<Schedule[]>(`${b}/health-schedules`),
    saveSchedule: (body: Omit<Schedule, 'id'>, id?: number) =>
      (id ? api.put<Schedule>(`${b}/health-schedules/${id}`, body) : api.post<Schedule>(`${b}/health-schedules`, body)),
    deleteSchedule: (id: number) => api.del(`${b}/health-schedules/${id}`),
    reportSummary: (from: string, to: string) => api.get<ReportSummary>(`${b}/reports/summary${q({ from, to })}`),
    report: (type: ReportType, from: string, to: string) => api.get<ReportData>(`${b}/reports/${type}${q({ from, to })}`),
    reportCsv: (type: ReportType, from: string, to: string) => api.blob(`${b}/reports/${type}.csv${q({ from, to })}`),
    cull: () => api.get<{ counts: { keep: number; monitor: number; dryOff: number; cull: number }; cows: CullRow[] }>(`${b}/reports/cull-advisor`),
    keep: (animalId: number) => api.post<CullRow>(`${b}/reports/cull-advisor/${animalId}/keep`, {}),
    compare: (a: string | number, bId: string | number) => api.get<{ a: CowCard; b: CowCard; days: string[] }>(`${b}/reports/compare${q({ a, b: bId })}`),
  }
}

// Open-alert count for the nav badge, shared by every screen and refreshed at most once a minute.
let countCache: { farmId: string; at: number; count: number; overdue: number } | null = null
let inflight: Promise<{ count: number; overdue: number }> | null = null
const listeners = new Set<() => void>()
export function alertCountSnapshot(farmId: string) {
  return countCache && countCache.farmId === farmId ? countCache : null
}
export function subscribeAlertCount(fn: () => void) { listeners.add(fn); return () => { listeners.delete(fn) } }
export function invalidateAlertCount() { countCache = countCache ? { ...countCache, at: 0 } : null }
export async function loadAlertCount(farmId: string, force = false) {
  if (!farmId) return null
  if (!force && countCache && countCache.farmId === farmId && Date.now() - countCache.at < 60_000) return countCache
  if (!inflight) {
    inflight = alertsApi(farmId).alerts().then((p) => ({ count: p.counts.all, overdue: p.counts.overdue }))
      .finally(() => { inflight = null })
  }
  try {
    const r = await inflight
    countCache = { farmId, at: Date.now(), ...r }
    listeners.forEach((l) => l())
    return countCache
  } catch { return null }
}
