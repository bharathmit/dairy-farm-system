// Daily records (milk, weight & BCS, health, contacts): API shapes and calls.
// Contract: docs/api-daily-records.md. Values come back exactly as the API sends them.
import { useCallback, useEffect, useRef, useState } from 'react'
import { api, errorMessage } from '../lib/api'

// ---------- shapes ----------

export type ContactRole = 'BUYER' | 'VET' | 'AI_TECH' | 'SUPPLIER' | 'OTHER'
export type PayEvery = 'DELIVERY' | 'WEEK' | 'TEN_DAYS' | 'FORTNIGHT' | 'MONTH'
export interface Contact {
  id: number; name: string; roles: ContactRole[]; phone?: string; village?: string
  milkRate?: number | null; payEvery?: PayEvery | null; notes?: string; active: boolean
}
export type ContactInput = Omit<Contact, 'id'>
export interface ContactRef { id: number; name: string }

export interface BreedPeak { breed: string; peakLitres: number; peakDay: number }
export interface Check { enabled: boolean; limit: number }
export interface GradingRules {
  basis: 'OWN_LACTATION' | 'BREED'
  breeds: BreedPeak[]; otherBreeds: BreedPeak
  startPct: number; fallPctPerMonth: number; firstCalverPct: number; lactationDays: number
  bandA: number; bandB: number; bandC: number
  minFatPct: Check; minSnfPct: Check; maxSccK: Check
  dropAlertPct: number
}

export type Session = 'AM' | 'PM'
export type Grade = 'A' | 'B' | 'C' | 'D'
export interface CowDay {
  date: string; animalId: number; tag: string; name?: string
  am?: number | null; pm?: number | null; total?: number | null; withheld: boolean
  daysInMilk?: number | null; expectedLitres?: number | null; pctOfExpected?: number | null
  grade?: Grade | null; qualityIssues?: string[]
  fatPct?: number | null; snfPct?: number | null; proteinPct?: number | null; density?: number | null; scc?: number | null
  notes?: string | null
}
export interface SessionTotal { litres?: number | null; cows?: number | null; withheldLitres?: number | null }
export interface DaySummary {
  date: string; am: SessionTotal; pm: SessionTotal; totalLitres?: number | null
  yesterday: { amLitres?: number | null; pmLitres?: number | null; totalLitres?: number | null }
  change: { amPct?: number | null; pmPct?: number | null; totalPct?: number | null }
  expectedLitres?: number | null; milkingCows: number; cows: CowDay[]
}
export interface TrendDay { date: string; amLitres: number; pmLitres: number; totalLitres: number; withheldLitres: number }
export interface SheetCow {
  animalId: number; tag: string; name?: string; groupId?: number | null
  litres?: number | null; lastLitres?: number | null; usualLitres?: number | null
  inWithdrawal: boolean; withdrawalUntil?: string | null
}
export interface MilkSheet {
  date: string; session: Session; sessionLitres?: number | null; otherSessionLitres?: number | null
  dayLitres?: number | null; withheldLitres?: number | null; cows: SheetCow[]
}
export interface Expectation {
  daysInMilk: number; expectedLitres: number; peakLitres: number; peakDay: number
  source: 'OWN_LACTATION' | 'BREED' | 'FIRST_CALVER'; pastLactation: boolean
}
export interface CowDayForm {
  animal: { id: number; tag: string; name?: string; breed?: string; lastCalvingDate?: string | null }
  day: CowDay; expectation: Expectation | null
  lastAm?: number | null; lastPm?: number | null; inWithdrawal: boolean; withdrawalUntil?: string | null
}
export type Product = 'MILK' | 'MANURE' | 'OTHER'
export interface Sale {
  id: number; date: string; session: Session | 'DAY'; product: Product
  quantity?: number | null; cowLitres?: number | null; withheldLitres?: number | null
  soldQty?: number | null; otherUseQty?: number | null; rate?: number | null; incomeAmount?: number | null
  buyer?: ContactRef | null; fatPct?: number | null; snfPct?: number | null; paid?: boolean | null; notes?: string | null
}

export type Method = 'SCALE' | 'TAPE' | 'ESTIMATE'
export interface Measurement {
  id: number; animalId: number; date: string; method: Method
  weightKg?: number | null; heightCm?: number | null; heartGirthCm?: number | null; bcs?: number | null
  notes?: string | null; gainKg?: number | null; adgKg?: number | null
}
export interface Growth {
  animal: { id: number; tag: string; name?: string; category: string; dateOfBirth?: string | null }
  latest?: { date: string; weightKg?: number | null; heightCm?: number | null; bcs?: number | null } | null
  adg90dKg?: number | null; items: Measurement[]
}

export type HealthType = 'ILLNESS' | 'TREATMENT' | 'VACCINATION' | 'DEWORMING' | 'INJURY'
export interface HealthRecord {
  id: number; animalId: number; tag: string; name?: string; date: string; type: HealthType
  symptoms?: string | null; diagnosis?: string | null; medicine?: string | null; dose?: string | null; route?: string | null
  vet?: ContactRef | null; cost?: number | null
  milkWithdrawalDays?: number | null; meatWithdrawalDays?: number | null
  milkWithdrawalUntil?: string | null; meatWithdrawalUntil?: string | null
  nextActionDate?: string | null; nextAction?: string | null; nextActionDone: boolean
  batchId?: string | null; notes?: string | null
}
export interface HealthInput {
  animalId?: number; date: string; type: HealthType
  symptoms?: string; diagnosis?: string; medicine?: string; dose?: string; route?: string
  vetContactId?: number; cost?: number; milkWithdrawalDays?: number; meatWithdrawalDays?: number
  nextActionDate?: string; nextAction?: string; notes?: string
}
export interface HealthOverview {
  inWithdrawal: number; openFollowUps: number; thisMonth: number
  dueFollowUps: HealthRecord[]; recent: HealthRecord[]
}
export interface Withdrawal { record: HealthRecord; endsAt: string; discardedLitres?: number | null }

export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number }

// ---------- labels ----------

export const CONTACT_ROLES: { value: ContactRole; label: string }[] = [
  { value: 'BUYER', label: 'Milk buyer' }, { value: 'VET', label: 'Vet' }, { value: 'AI_TECH', label: 'AI technician' },
  { value: 'SUPPLIER', label: 'Supplier' }, { value: 'OTHER', label: 'Other' },
]
export const ROLE_SHORT: Record<ContactRole, string> = { BUYER: 'Buyer', VET: 'Vet', AI_TECH: 'AI tech', SUPPLIER: 'Supplier', OTHER: 'Other' }
export const PAY_EVERY: { value: PayEvery; label: string }[] = [
  { value: 'DELIVERY', label: 'On delivery' }, { value: 'WEEK', label: 'Week' }, { value: 'TEN_DAYS', label: '10 days' },
  { value: 'FORTNIGHT', label: 'Fortnight' }, { value: 'MONTH', label: 'Month' },
]
export const HEALTH_TYPES: { value: HealthType; label: string }[] = [
  { value: 'ILLNESS', label: 'Illness' }, { value: 'TREATMENT', label: 'Treatment' }, { value: 'VACCINATION', label: 'Vaccination' },
  { value: 'DEWORMING', label: 'Deworming' }, { value: 'INJURY', label: 'Injury' },
]
export const healthLabel = (t: HealthType) => HEALTH_TYPES.find((h) => h.value === t)?.label ?? t
export const HEALTH_TONE: Record<HealthType, string> = {
  ILLNESS: 'bg-danger-soft text-danger-text', TREATMENT: 'bg-danger-soft text-danger-text',
  VACCINATION: 'bg-info-soft text-info-text', DEWORMING: 'bg-brand-soft text-brand', INJURY: 'bg-warn-soft text-warn-ink',
}
export const GRADE_TONE: Record<Grade, string> = {
  A: 'bg-brand-soft text-brand', B: 'bg-info-soft text-info-text', C: 'bg-warn-soft text-warn-ink', D: 'bg-danger-soft text-danger-text',
}
export const METHODS: { value: Method; label: string }[] = [
  { value: 'SCALE', label: 'Scale' }, { value: 'TAPE', label: 'Tape' }, { value: 'ESTIMATE', label: 'Estimate' },
]

// ---------- dates & numbers ----------

const pad = (n: number) => String(n).padStart(2, '0')
export const isoDate = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
export const todayIso = () => isoDate(new Date())
export function addDays(iso: string, days: number) {
  const [y, m, d] = iso.split('-').map(Number)
  return isoDate(new Date(y, m - 1, d + days))
}
const parse = (iso: string) => { const [y, m, d] = iso.slice(0, 10).split('-').map(Number); return new Date(y, m - 1, d) }
/** "Wed 21 Oct" */
export const dayLabel = (iso: string) => parse(iso).toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })
/** "21 Oct" */
export const shortDate = (iso?: string | null) => (iso ? parse(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' }) : '—')
/** "Sat 24 Oct, 6:00 PM" */
export const dateTimeLabel = (iso?: string | null) => {
  if (!iso) return '—'
  const d = new Date(iso)
  return `${d.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })}, ${d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })}`
}
export const weekdayShort = (iso: string) => parse(iso).toLocaleDateString('en-GB', { weekday: 'short' })

/** "12.5" or "—" */
export const L = (n?: number | null, digits = 1) => (n === null || n === undefined ? '—' : n.toFixed(digits))
export const pct = (n?: number | null) => (n === null || n === undefined ? '' : `${n > 0 ? '+' : n < 0 ? '−' : ''}${Math.abs(n).toFixed(1)} %`)
/** Text field → number (blank → undefined). */
export const toNum = (s: string) => { const t = s.trim(); if (!t) return undefined; const n = Number(t); return Number.isFinite(n) ? n : undefined }
export const numText = (n?: number | null) => (n === null || n === undefined ? '' : String(n))
export const decimal = (s: string) => s.replace(/[^\d.]/g, '')

// ---------- calls ----------

export const recordsApi = (farmId: string) => {
  const b = `/farms/${farmId}`
  const q = (params: Record<string, string | number | boolean | undefined | null>) => {
    const s = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') s.set(k, String(v)) })
    const t = s.toString()
    return t ? `?${t}` : ''
  }
  return {
    contacts: (role?: ContactRole, active?: boolean) => api.get<Contact[]>(`${b}/contacts${q({ role, active })}`),
    contact: (id: string | number) => api.get<Contact>(`${b}/contacts/${id}`),
    saveContact: (c: ContactInput, id?: number) => (id ? api.put<Contact>(`${b}/contacts/${id}`, c) : api.post<Contact>(`${b}/contacts`, c)),
    deleteContact: (id: number) => api.del(`${b}/contacts/${id}`),

    grading: () => api.get<GradingRules>(`${b}/milk/grading`),
    saveGrading: (r: GradingRules) => api.put<GradingRules>(`${b}/milk/grading`, r),

    day: (date: string) => api.get<DaySummary>(`${b}/milk/day${q({ date })}`),
    trend: (from: string, to: string) => api.get<TrendDay[]>(`${b}/milk/trend${q({ from, to })}`),
    grades: (from: string, to: string) => api.get<Record<Grade, number>>(`${b}/milk/grades${q({ from, to })}`),
    sheet: (date: string, session: Session, groupId?: string) => api.get<MilkSheet>(`${b}/milk/sheet${q({ date, session, groupId })}`),
    saveSheet: (date: string, session: Session, entries: { animalId: number; litres: number | null }[]) =>
      api.put<MilkSheet>(`${b}/milk/sheet`, { date, session, entries }),
    cowDay: (animalId: string | number, date: string) => api.get<CowDayForm>(`${b}/milk/cows/${animalId}/days/${date}`),
    saveCowDay: (animalId: string | number, date: string, body: Record<string, unknown>) =>
      api.put<CowDayForm>(`${b}/milk/cows/${animalId}/days/${date}`, body),
    cowHistory: (animalId: string | number, from: string, to: string) => api.get<CowDay[]>(`${b}/milk/cows/${animalId}${q({ from, to })}`),
    sales: (from: string, to: string) => api.get<Sale[]>(`${b}/milk/sales${q({ from, to })}`),
    saveSale: (body: Record<string, unknown>) => api.put<Sale>(`${b}/milk/sales`, body),
    deleteSale: (id: number) => api.del(`${b}/milk/sales/${id}`),

    growth: (animalId: string | number) => api.get<Growth>(`${b}/animals/${animalId}/measurements`),
    addMeasurement: (animalId: string | number, body: Record<string, unknown>) => api.post<Measurement>(`${b}/animals/${animalId}/measurements`, body),
    updateMeasurement: (id: number, body: Record<string, unknown>) => api.put<Measurement>(`${b}/measurements/${id}`, body),
    deleteMeasurement: (id: number) => api.del(`${b}/measurements/${id}`),

    healthOverview: () => api.get<HealthOverview>(`${b}/health/overview`),
    healthRecords: (p: { animalId?: string | number; type?: HealthType; from?: string; to?: string; page?: number; size?: number }) =>
      api.get<Page<HealthRecord>>(`${b}/health/records${q(p)}`),
    healthRecord: (id: string | number) => api.get<HealthRecord>(`${b}/health/records/${id}`),
    saveHealth: (body: HealthInput, id?: string | number) =>
      (id ? api.put<HealthRecord>(`${b}/health/records/${id}`, body) : api.post<HealthRecord>(`${b}/health/records`, body)),
    saveHealthBatch: (body: HealthInput & { animalIds: number[] }) =>
      api.post<{ batchId: string; count: number; records: HealthRecord[] }>(`${b}/health/records/batch`, body),
    followUp: (id: number, done: boolean) => api.put<HealthRecord>(`${b}/health/records/${id}/follow-up`, { done }),
    deleteHealth: (id: number) => api.del(`${b}/health/records/${id}`),
    withdrawals: () => api.get<{ active: Withdrawal[]; ended: Withdrawal[] }>(`${b}/health/withdrawals`),
  }
}

/**
 * Loads data when the page opens (and again when deps change). Returns the data, the API's error
 * message, a loading flag and reload(). Results from an older request are ignored.
 */
export function useLoad<T>(load: (() => Promise<T>) | null, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(Boolean(load))
  const seq = useRef(0)
  const loadRef = useRef(load)
  loadRef.current = load
  const run = useCallback(async () => {
    const fn = loadRef.current
    if (!fn) { setLoading(false); return }
    const mine = ++seq.current
    setLoading(true)
    setError(null)
    try {
      const res = await fn()
      if (mine === seq.current) setData(res)
    } catch (e) {
      if (mine === seq.current) setError(errorMessage(e))
    } finally {
      if (mine === seq.current) setLoading(false)
    }
  }, [])
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void run() }, deps)
  return { data, setData, error, loading, reload: run }
}
