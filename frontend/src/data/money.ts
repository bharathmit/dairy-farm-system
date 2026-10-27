// Money & team (finance, P&L, labour, equipment, procurement): API shapes and calls.
// Contract: docs/api-money.md. Everything here is owner only.
import { api } from '../lib/api'
import type { ContactRef, Page } from './records'
import type { AnimalRef } from './breeding'
import type { PaymentMethod } from './feed'

export type Direction = 'INCOME' | 'EXPENSE'
export type IncomeCategory = 'MILK_SALES' | 'ANIMAL_SALES' | 'MANURE_SALES' | 'OTHER_INCOME'
export type ExpenseCategory = 'FEED' | 'LABOUR' | 'VET' | 'BREEDING' | 'SEMEN' | 'EQUIPMENT' | 'UTILITIES' | 'TRANSPORT' | 'ANIMAL_PURCHASE' | 'OTHER_EXPENSE'
export type FinCategory = IncomeCategory | ExpenseCategory
export type EntryStatus = 'PAID' | 'PENDING'
export type AllocKind = 'FARM' | 'GROUP' | 'ANIMAL'
export interface MemberRef { id: number; name: string }

export interface Summary {
  from: string; to: string; income: number; expense: number; net: number; marginPct?: number | null
  marginAlertPct: number; nearAlert: boolean
  previous: { income: number; expense: number; net: number }
  incomeChangePct?: number | null; expenseChangePct?: number | null
  pending: { count: number; amount: number }
  incomeByCategory: { category: FinCategory; amount: number }[]
  expenseByCategory: { category: FinCategory; amount: number }[]
  feedPctOfMilkIncome?: number | null
}
export interface Entry {
  id: number; direction: Direction; category: FinCategory; amount: number; date: string; time?: string | null
  status: EntryStatus; paymentMethod?: PaymentMethod | null; counterparty?: string | null; contact?: ContactRef | null
  member?: MemberRef | null; periodFrom?: string | null; periodTo?: string | null
  alloc?: { kind: AllocKind; group?: { id: number; name: string } | null; animal?: AnimalRef | null } | null
  quantity?: number | null; unit?: string | null; source?: { type: string; id: number; label?: string | null } | null
  auto: boolean; notes?: string | null
}
/** One day for the Home money panels: feed fed cost, milk income, other income, other (non-feed) expenses. */
export interface DailyMoney { date: string; feedCost: number; milkIncome: number; otherIncome: number; otherExpense: number }
export type MoneyKind = 'feed' | 'milk' | 'milk-margin' | 'other-margin'
export const MONEY_KINDS: Record<MoneyKind, { title: string; short: string; value: (d: DailyMoney) => number; note: string }> = {
  feed: { title: 'Feed cost', short: "Today's feed cost", value: (d) => d.feedCost, note: 'Feed logged as fed × each item\'s cost per kg' },
  milk: { title: 'Milk sale income', short: 'Milk sale income', value: (d) => d.milkIncome, note: 'Milk sales added to Finance' },
  'milk-margin': { title: 'Milk income − feed cost', short: 'Milk vs feed margin', value: (d) => d.milkIncome - d.feedCost, note: 'Milk sale income minus feed fed' },
  'other-margin': { title: 'Other income − other expenses', short: 'Other income vs expenses', value: (d) => d.otherIncome - d.otherExpense, note: 'Manure, animal and other sales minus labour, vet, breeding and other non-feed costs' },
}
export const isMoneyKind = (k?: string): k is MoneyKind => !!k && k in MONEY_KINDS

export interface PnlAnimal { rank: number; animal: AnimalRef & { category?: string; breed?: string | null; lactationNumber?: number | null; ageDays?: number | null }; net: number; note?: string | null }
export interface Pnl {
  from?: string | null; to?: string | null
  farm: { income: number; expense: number; net: number; byCategory: { category: FinCategory; amount: number }[] }
  animals: PnlAnimal[]
}
export type PnlKey = 'MILK' | 'FEED' | 'HEALTH' | 'BREEDING' | 'PURCHASE' | 'SALE' | 'OTHER'
export interface AnimalPnl {
  animal: AnimalRef & { category?: string; breed?: string | null }; from?: string | null; to?: string | null
  onFarmDays?: number | null; milkLitres?: number | null; lines: { key: PnlKey; label: string; amount: number }[]
  net: number; perDay?: number | null; costPerLitre?: number | null; marginPct?: number | null; rank?: number | null; of?: number | null
}
export type WageType = 'DAILY' | 'MONTHLY'
export interface Worker {
  member: MemberRef; wageType: WageType; wageRate: number; hasLogin: boolean
  week: { presentDays: number; hours: number }; dueNow: number; pendingNote?: string | null
  lastPaid?: { periodTo: string; amount: number } | null
}
export interface WorkersPage { week: { from: string; to: string }; workers: Worker[]; totals: { workers: number; dueNow: number; paidThisMonth: number } }
export type DayStatus = 'PRESENT' | 'HALF' | 'ABSENT'
export interface AttendanceDay { date: string; status?: DayStatus | null; hours?: number | null }
export interface AttendanceRow { member: MemberRef; wageType: WageType; wageRate: number; days: AttendanceDay[]; presentDays: number; hours: number; amount: number }
export interface AttendanceWeek { week: { from: string; to: string }; rows: AttendanceRow[]; total: { days: number; hours: number; amount: number } }
export interface WageDue {
  member: MemberRef; from: string; to: string; presentDays: number; halfDays: number; absentDays: number
  rate: number; gross: number; advances: number; amountDue: number; explain?: string | null
}
export type EquipStatus = 'OVERDUE' | 'DUE_SOON' | 'OK'
export interface Equipment {
  id: number; name: string; brand?: string | null; model?: string | null; serviceEveryDays?: number | null; serviceTask?: string | null
  lastService?: string | null; nextService?: string | null; status: EquipStatus; daysTo?: number | null
  purchaseDate?: string | null; purchaseCost?: number | null; notes?: string | null; reminderDays?: number | null
}
export type WorkDone = 'ROUTINE' | 'LINER_CHANGE' | 'REPAIR' | 'PARTS' | 'OTHER'
export interface Service { id: number; date: string; workDone: WorkDone; cost?: number | null; paymentMethod?: PaymentMethod | null; paid?: boolean | null; mechanic?: ContactRef | null; mechanicName?: string | null; notes?: string | null }
export type NoteStatus = 'PLANNED' | 'ORDERED' | 'RECEIVED' | 'CANCELLED'
export interface Suggestion { item: { id: number; name: string; type: string }; stock: number; unit: string; reorderLevel?: number | null; daysLeft?: number | null; reason: string }
export interface Note {
  id: number; item?: { id: number; name: string; type: string } | null; title: string; quantity?: number | null; unit?: string | null
  expectedPrice?: number | null; neededBy?: string | null; supplier?: ContactRef | null; status: NoteStatus
  orderedOn?: string | null; receivedOn?: string | null; paidAmount?: number | null; source: 'MANUAL' | 'LOW_STOCK' | 'FEED_NEED'; notes?: string | null
}
export interface ProcurementPage { counts: { all: number; planned: number; ordered: number; received: number }; suggestions: Suggestion[]; notes: Note[] }

// ---------- labels ----------

export const INCOME_CATEGORIES: { value: IncomeCategory; label: string }[] = [
  { value: 'MILK_SALES', label: 'Milk sales' }, { value: 'ANIMAL_SALES', label: 'Animal sales' },
  { value: 'MANURE_SALES', label: 'Manure' }, { value: 'OTHER_INCOME', label: 'Other' },
]
export const EXPENSE_CATEGORIES: { value: ExpenseCategory; label: string }[] = [
  { value: 'FEED', label: 'Feed' }, { value: 'LABOUR', label: 'Labour' }, { value: 'VET', label: 'Vet & medicine' },
  { value: 'BREEDING', label: 'Breeding' }, { value: 'SEMEN', label: 'Semen' }, { value: 'EQUIPMENT', label: 'Equipment' },
  { value: 'UTILITIES', label: 'Utilities' }, { value: 'TRANSPORT', label: 'Transport' },
  { value: 'ANIMAL_PURCHASE', label: 'Animal purchase' }, { value: 'OTHER_EXPENSE', label: 'Other' },
]
export const finCategoryLabel = (c?: string | null) =>
  [...INCOME_CATEGORIES, ...EXPENSE_CATEGORIES].find((x) => x.value === c)?.label ?? c ?? ''
export const WORK_DONE: { value: WorkDone; label: string }[] = [
  { value: 'ROUTINE', label: 'Routine service' }, { value: 'LINER_CHANGE', label: 'Liner change' },
  { value: 'REPAIR', label: 'Repair' }, { value: 'PARTS', label: 'Parts replaced' }, { value: 'OTHER', label: 'Other' },
]
export const workDoneLabel = (w?: string | null) => WORK_DONE.find((x) => x.value === w)?.label ?? w ?? ''
/** Where an auto entry came from, and the page that shows it. */
export function sourceLink(s?: Entry['source'], e?: Entry): { label: string; to?: string } | null {
  if (!s) return null
  const label = s.label || s.type.toLowerCase().replace(/_/g, ' ')
  switch (s.type) {
    case 'PRODUCTION': return { label: label || 'milk sale', to: `/milk/sale?sale=${s.id}${e?.date ? `&date=${e.date}` : ''}` }
    case 'ITEM_TXN': return { label: label || 'purchase', to: '/feed' }
    case 'EVENT': case 'HEALTH': return { label: label || 'health record', to: `/health/${s.id}` }
    case 'BREEDING': return { label: label || 'breeding record', to: `/breeding/service/${s.id}` }
    case 'ANIMAL': return { label: label || 'animal', to: `/animals/${s.id}` }
    case 'EQUIPMENT_SERVICE': case 'SERVICE': return { label: label || 'equipment service', to: '/money/equipment' }
    case 'WAGE': case 'LABOUR': return { label: label || 'wage payment', to: e?.member ? '/money/workers' : undefined }
    default: return { label }
  }
}

/** "₹1,48,600" (no decimals) with a sign when asked. */
export const rupees = (n?: number | null, sign = false) => {
  if (n === null || n === undefined) return '—'
  const s = `₹${Math.round(Math.abs(n)).toLocaleString('en-IN')}`
  return n < 0 ? `−${s}` : sign && n > 0 ? `+${s}` : s
}
/** "+6 %" / "−3 %" */
export const changePct = (n?: number | null) => (n === null || n === undefined ? '' : `${n > 0 ? '+' : n < 0 ? '−' : ''}${Math.abs(Math.round(n))} %`)

// ---------- dates ----------

const pad = (n: number) => String(n).padStart(2, '0')
const iso = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const parse = (s: string) => { const [y, m, d] = s.slice(0, 10).split('-').map(Number); return new Date(y, m - 1, d) }
export type Period = 'MONTH' | 'LAST_MONTH' | 'QUARTER' | 'YEAR' | 'CUSTOM'
export function periodRange(p: Period, today = new Date()): { from: string; to: string } {
  const y = today.getFullYear(); const m = today.getMonth()
  switch (p) {
    case 'LAST_MONTH': return { from: iso(new Date(y, m - 1, 1)), to: iso(new Date(y, m, 0)) }
    case 'QUARTER': { const q = Math.floor(m / 3) * 3; return { from: iso(new Date(y, q, 1)), to: iso(today) } }
    case 'YEAR': return { from: iso(new Date(y, 0, 1)), to: iso(today) }
    default: return { from: iso(new Date(y, m, 1)), to: iso(today) }
  }
}
/** Monday of the week holding the date. */
export function mondayOf(s: string) { const d = parse(s); const wd = (d.getDay() + 6) % 7; d.setDate(d.getDate() - wd); return iso(d) }
export const rangeLabel = (from: string, to: string) => {
  const a = parse(from); const b = parse(to)
  const sameMonth = a.getMonth() === b.getMonth() && a.getFullYear() === b.getFullYear()
  const left = a.toLocaleDateString('en-GB', sameMonth ? { day: 'numeric' } : { day: 'numeric', month: 'short' })
  return `${left} – ${b.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}`
}

// ---------- calls ----------

export const moneyApi = (farmId: string) => {
  const b = `/farms/${farmId}`
  const q = (params: Record<string, string | number | boolean | undefined | null>) => {
    const s = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') s.set(k, String(v)) })
    const t = s.toString()
    return t ? `?${t}` : ''
  }
  return {
    summary: (from: string, to: string) => api.get<Summary>(`${b}/finance/summary${q({ from, to })}`),
    daily: (from: string, to: string) => api.get<DailyMoney[]>(`${b}/finance/daily${q({ from, to })}`),
    entries: (p: { from?: string; to?: string; direction?: Direction; category?: string; status?: EntryStatus; animalId?: string | number; page?: number; size?: number }) =>
      api.get<Page<Entry>>(`${b}/finance/entries${q(p)}`),
    entry: (id: string | number) => api.get<Entry>(`${b}/finance/entries/${id}`),
    saveEntry: (body: Record<string, unknown>, id?: string | number) => (id ? api.put<Entry>(`${b}/finance/entries/${id}`, body) : api.post<Entry>(`${b}/finance/entries`, body)),
    deleteEntry: (id: number) => api.del(`${b}/finance/entries/${id}`),
    pnl: (scope: 'FARM' | 'ANIMAL', from?: string, to?: string) => api.get<Pnl>(`${b}/finance/pnl${q({ scope, from, to })}`),
    animalPnl: (id: string | number, from?: string, to?: string) => api.get<AnimalPnl>(`${b}/finance/pnl/animals/${id}${q({ from, to })}`),
    workers: (weekOf?: string) => api.get<WorkersPage>(`${b}/labour/workers${q({ weekOf })}`),
    attendance: (weekOf?: string) => api.get<AttendanceWeek>(`${b}/labour/attendance${q({ weekOf })}`),
    saveAttendance: (entries: { memberId: number; date: string; status: DayStatus | null; hours?: number | null }[]) => api.put<AttendanceWeek>(`${b}/labour/attendance`, { entries }),
    due: (memberId: string | number, from?: string, to?: string) => api.get<WageDue>(`${b}/labour/workers/${memberId}/due${q({ from, to })}`),
    pay: (body: Record<string, unknown>) => api.post<Entry>(`${b}/labour/payments`, body),
    equipment: () => api.get<{ counts: { items: number; overdue: number; dueSoon: number }; items: Equipment[] }>(`${b}/equipment`),
    saveEquipment: (body: Record<string, unknown>, id?: string | number) => (id ? api.put<Equipment>(`${b}/equipment/${id}`, body) : api.post<Equipment>(`${b}/equipment`, body)),
    deleteEquipment: (id: number) => api.del(`${b}/equipment/${id}`),
    services: (id: string | number) => api.get<Service[]>(`${b}/equipment/${id}/services`),
    logService: (id: string | number, body: Record<string, unknown>) => api.post<Equipment>(`${b}/equipment/${id}/services`, body),
    procurement: (status?: NoteStatus) => api.get<ProcurementPage>(`${b}/procurement${q({ status })}`),
    saveNote: (body: Record<string, unknown>, id?: number) => (id ? api.put<Note>(`${b}/procurement/${id}`, body) : api.post<Note>(`${b}/procurement`, body)),
    deleteNote: (id: number) => api.del(`${b}/procurement/${id}`),
    order: (id: number, orderedOn: string) => api.post<Note>(`${b}/procurement/${id}/order`, { orderedOn }),
    receive: (id: number, body: Record<string, unknown>) => api.post<Note>(`${b}/procurement/${id}/receive`, body),
    fromFeedNeed: (days = 7) => api.post<Note[]>(`${b}/procurement/from-feed-need${q({ days })}`, {}),
  }
}
