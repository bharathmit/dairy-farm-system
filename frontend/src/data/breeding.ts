// Breeding & calves: API shapes and calls. Contract: docs/api-breeding.md.
import { api } from '../lib/api'
import type { ContactRef } from './records'

export interface AnimalRef { id: number; tag: string; name?: string | null }

export type BreedingStatus = 'OPEN' | 'SERVED' | 'PREGNANT' | 'DRY_PREGNANT' | 'OVERDUE' | 'HEIFER_NOT_SERVED'
export type SuggestionAction = 'OK' | 'HEAT_DUE' | 'FIRST_SERVICE_DUE' | 'PD_DUE' | 'DRY_OFF_DUE' | 'CALVING_DUE' | 'VET_CHECK' | 'CONSIDER_SELLING'
export interface CowStatus {
  animal: AnimalRef; breed?: string | null; category: string; lactationNumber?: number | null
  status: BreedingStatus
  lastCalvingDate?: string | null; daysOpen?: number | null
  lastHeatDate?: string | null; nextHeatExpected?: string | null
  lastServiceDate?: string | null; currentServiceId?: number | null
  pdDueDate?: string | null; expectedDueDate?: string | null; dryOffDate?: string | null; daysToDue?: number | null
  servicesThisLactation: number; failedServices: number; heatsMissed: number; heatsExpected?: number | null
  spendThisLactation?: number | null
  suggestion: { action: SuggestionAction; title: string; reasons: string[] }
}
export type CalendarKind = 'HEAT' | 'HEAT_EXPECTED' | 'PD' | 'DRY_OFF' | 'CALVING'
export interface Overview {
  date: string; breedable: number; heatWatchCount: number
  calendar: { date: string; kind: CalendarKind; animal: AnimalRef }[]
  groups: { overdue: CowStatus[]; dueIn7: CowStatus[]; open: CowStatus[]; served: CowStatus[]; pregnant: CowStatus[]; dry: CowStatus[] }
}
export interface HeatWatchItem { status: CowStatus; level: 'HIGH' | 'MEDIUM' | 'LOW'; confidencePct: number; reason: string }

export type HeatSign = 'MOUNTING' | 'RESTLESS' | 'MUCUS' | 'SWELLING' | 'CHIN_RESTING' | 'BELLOWING' | 'LOW_APPETITE'
export type Intensity = 'MILD' | 'MODERATE' | 'STRONG' | 'SILENT'
export interface Heat { id: number; animal: AnimalRef; date: string; time?: string | null; signs: HeatSign[]; intensity: Intensity; notes?: string | null; serviceId?: number | null }

export type BreedMethod = 'NATURAL' | 'AI' | 'ET'
export type PdMethod = 'ULTRASOUND' | 'PALPATION' | 'BLOOD'
export type PregStatus = 'UNKNOWN' | 'PREGNANT' | 'NOT_PREGNANT'
export type Outcome = 'LIVE' | 'STILLBORN' | 'ABORTED'
export type Ease = 'EASY' | 'ASSISTED' | 'HARD' | 'VET'
export interface BreedingRecord {
  id: number; animal: AnimalRef; serviceDate?: string | null; serviceTime?: string | null; method: BreedMethod | 'UNKNOWN'; heatId?: number | null
  semen?: { itemId: number; bullName: string; bullCode?: string | null; breed?: string | null } | null; straws?: number | null
  sire?: AnimalRef | null; sireTag?: string | null; bullExposureStart?: string | null; bullExposureEnd?: string | null
  technician?: ContactRef | null; technicianFee?: number | null; bullOwner?: ContactRef | null; bullFee?: number | null
  semenCost?: number | null; totalCost?: number | null; feePaid?: boolean | null
  expectedDueDate?: string | null; pdDueDate?: string | null; dryOffDate?: string | null
  pregnancyStatus: PregStatus
  pregCheck?: { date: string; method: PdMethod; result: 'PREGNANT' | 'NOT_PREGNANT'; checkedBy?: ContactRef | null; cost?: number | null } | null
  calving?: {
    date: string; time?: string | null; outcome: Outcome; ease: Ease
    calves: { animal: AnimalRef | null; sex: 'FEMALE' | 'MALE'; birthWeightKg?: number | null }[]
    colostrumGivenAt?: string | null; colostrumLitres?: number | null; colostrumDueBy?: string | null; retainedPlacenta?: boolean | null
  } | null
  lactationNumber?: number | null; notes?: string | null
}
export interface HistoryEvent { date: string; kind: 'CALVING' | 'HEAT' | 'HEAT_MISSED' | 'SERVICE' | 'PD' | 'DRY_OFF' | 'EXPECTED_HEAT'; title: string; detail?: string | null; cost?: number | null; serviceId?: number | null }
export interface Lactation { number: number; calvedOn?: string | null; nextCalvedOn?: string | null; services: number; daysOpen?: number | null; calvingIntervalDays?: number | null; spend?: number | null; conceived: boolean }
export interface History {
  status: CowStatus; milkPerDay?: number | null
  current: { lactationNumber?: number | null; calvedOn?: string | null; events: HistoryEvent[] }
  lactations: Lactation[]
  totals: { avgDaysOpen?: number | null; servicesPerConception?: number | null; calvings: number; totalSpend?: number | null }
}
export interface SemenBatch {
  id: number; bullName: string; bullCode?: string | null; breed?: string | null; supplier?: ContactRef | null
  batchNo?: string | null; expiryDate?: string | null; costPerStraw?: number | null; sexed?: boolean | null
  straws: number; low: boolean; expiringSoon: boolean; usedUpOn?: string | null; notes?: string | null
}
export interface TaperStep { fromDay: number; toDay: number; milkLitres: number; starter: string }
export type Tone = 'ok' | 'warn' | 'danger' | 'info'
export interface CalfItem {
  animal: AnimalRef; sex: 'FEMALE' | 'MALE'; breed?: string | null; dam?: AnimalRef | null; ageDays: number; weightKg?: number | null
  colostrum: { given: boolean; dueBy?: string | null }
  milkLitresPerDay?: number | null; starterKgPerDay?: number | null
  plan?: TaperStep | null; weightTargetKg?: number | null
  weaning?: { ready: boolean; inDays?: number | null } | null
  maleSell?: { fromDay: number; toDay: number; daysLeft: number } | null
  dewormDue?: string[]; badges: { text: string; tone: Tone }[]
}
export interface CalfList { counts: { all: number; female: number; male: number; shown: number }; milkTodayLitres?: number | null; needAction: number; calves: CalfItem[] }
export interface CalfFeeding { date: string; milkAm?: number | null; milkPm?: number | null; starterKg?: number | null; waterL?: number | null; colostrumLitres?: number | null; colostrumAt?: string | null; notes?: string | null }
export interface CalfFeedingForm { calf: CalfItem; date: string; record: CalfFeeding | null; plan?: TaperStep | null; taper: TaperStep[]; recent: CalfFeeding[] }
export interface FeedingSheet { date: string; session: 'AM' | 'PM'; calves: { animal: AnimalRef; sex: 'FEMALE' | 'MALE'; ageDays: number; planLitres?: number | null; litres?: number | null; starterKg?: number | null; colostrum: boolean }[] }
export interface FamilyNode { animal: AnimalRef | null; label: string }
export interface Family {
  animal: AnimalRef & { species?: string; breed?: string | null; sex?: string; dateOfBirth?: string | null; category?: string }
  pedigreeKnownPct: number
  parents: { dam: FamilyNode | null; sire: FamilyNode | null }
  grandparents: { damDam: FamilyNode | null; damSire: FamilyNode | null; sireDam: FamilyNode | null; sireSire: FamilyNode | null }
  siblings: { animal: AnimalRef; kind: 'FULL' | 'HALF_DAM' | 'HALF_SIRE' }[]
  offspring: { animal: AnimalRef | null; sex: 'FEMALE' | 'MALE'; birthDate: string; sire?: string | null; ease?: Ease | null; birthWeightKg?: number | null; status?: string | null }[]
  avgCalvingIntervalDays?: number | null
}
export interface MateCheck { risk: 'HIGH' | 'MEDIUM' | 'NONE'; relation?: string | null; sharedAncestors: string[]; knownPct: number; text: string }
export interface BreedingSettings {
  gestationDays: Record<string, number>; heatCycleDays: number; voluntaryWaitDays: number; pdAfterDays: number; dryOffDaysBeforeDue: number
  targetDaysOpen: number; sellDaysOpen: number; sellFailedServices: number; heiferFirstServiceMonths: number; heiferFirstServiceKg: number
  colostrumHours: number; calfTaper: TaperStep[]; weanMinDays: number; weanStarterKg: number; weanStarterDays: number; maleSellFromDay: number; maleSellToDay: number
}

// ---------- labels ----------

export const HEAT_SIGNS: { value: HeatSign; label: string }[] = [
  { value: 'MOUNTING', label: 'Mounting / standing' }, { value: 'RESTLESS', label: 'Restless' }, { value: 'MUCUS', label: 'Clear mucus' },
  { value: 'SWELLING', label: 'Vulvar swelling' }, { value: 'CHIN_RESTING', label: 'Chin resting' }, { value: 'BELLOWING', label: 'Bellowing' },
  { value: 'LOW_APPETITE', label: 'Low appetite' },
]
export const INTENSITIES: { value: Intensity; label: string }[] = [
  { value: 'MILD', label: 'Mild' }, { value: 'MODERATE', label: 'Moderate' }, { value: 'STRONG', label: 'Strong' }, { value: 'SILENT', label: 'Silent' },
]
export const METHODS: { value: BreedMethod; label: string }[] = [{ value: 'NATURAL', label: 'Natural' }, { value: 'AI', label: 'AI' }, { value: 'ET', label: 'ET' }]
export const PD_METHODS: { value: PdMethod; label: string }[] = [{ value: 'ULTRASOUND', label: 'Ultrasound' }, { value: 'PALPATION', label: 'Palpation' }, { value: 'BLOOD', label: 'Blood' }]
export const OUTCOMES: { value: Outcome; label: string }[] = [{ value: 'LIVE', label: 'Live' }, { value: 'STILLBORN', label: 'Stillborn' }, { value: 'ABORTED', label: 'Aborted' }]
export const EASES: { value: Ease; label: string }[] = [{ value: 'EASY', label: 'Easy' }, { value: 'ASSISTED', label: 'Assisted' }, { value: 'HARD', label: 'Hard' }, { value: 'VET', label: 'Vet' }]
export const methodLabel = (m?: string | null) => (m === 'NATURAL' ? 'Natural' : m === 'AI' ? 'AI' : m === 'ET' ? 'ET' : 'Unknown')
export const intensityLabel = (i: Intensity) => INTENSITIES.find((x) => x.value === i)?.label ?? i
export const signLabel = (s: HeatSign) => HEAT_SIGNS.find((x) => x.value === s)?.label ?? s

export const STATUS_LABEL: Record<BreedingStatus, string> = {
  OPEN: 'Open', SERVED: 'Served', PREGNANT: 'Pregnant', DRY_PREGNANT: 'Dry · pregnant', OVERDUE: 'Overdue to calve', HEIFER_NOT_SERVED: 'Heifer · not served',
}
export const STATUS_TONE: Record<BreedingStatus, string> = {
  OPEN: 'bg-danger-soft text-danger-text', SERVED: 'bg-info-soft text-info-text', PREGNANT: 'bg-brand-soft text-brand',
  DRY_PREGNANT: 'bg-ground text-ink-2', OVERDUE: 'bg-danger-soft text-danger-text', HEIFER_NOT_SERVED: 'bg-info-soft text-info-text',
}
export const SUGGESTION_TONE: Record<SuggestionAction, string> = {
  OK: 'bg-brand-soft text-brand', HEAT_DUE: 'bg-warn-soft text-warn-ink', FIRST_SERVICE_DUE: 'bg-info-soft text-info-text',
  PD_DUE: 'bg-info-soft text-info-text', DRY_OFF_DUE: 'bg-warn-soft text-warn-ink', CALVING_DUE: 'bg-warn-soft text-warn-ink',
  VET_CHECK: 'bg-danger-soft text-danger-text', CONSIDER_SELLING: 'bg-danger-soft text-danger-text',
}
export const SUGGESTION_SHORT: Record<SuggestionAction, string> = {
  OK: 'On track', HEAT_DUE: 'Heat due', FIRST_SERVICE_DUE: 'First AI due', PD_DUE: 'PD due', DRY_OFF_DUE: 'Dry-off due',
  CALVING_DUE: 'Calving due', VET_CHECK: 'Vet check', CONSIDER_SELLING: 'Consider selling',
}
export const CALENDAR_TONE: Record<CalendarKind, { label: string; cls: string }> = {
  HEAT: { label: 'Heat', cls: 'bg-danger-soft text-danger-text' }, HEAT_EXPECTED: { label: 'Heat?', cls: 'bg-brand-soft text-brand' },
  PD: { label: 'PD', cls: 'bg-info-soft text-info-text' }, DRY_OFF: { label: 'Dry-off', cls: 'bg-ground text-ink-2' },
  CALVING: { label: 'Calving', cls: 'bg-warn-soft text-warn-ink' },
}
export const TONE_CLASS: Record<Tone, string> = {
  ok: 'bg-brand-soft text-brand', warn: 'bg-warn-soft text-warn-ink', danger: 'bg-danger-soft text-danger-text', info: 'bg-info-soft text-info-text',
}
export const refLabel = (a?: AnimalRef | null) => (a ? `${a.tag}${a.name ? ` ${a.name}` : ''}` : '—')
export const inr0 = (n?: number | null) => (n === null || n === undefined ? '—' : `₹${Math.round(n).toLocaleString('en-IN')}`)
export const time12 = (t?: string | null) => {
  if (!t) return ''
  const [h, m] = t.split(':').map(Number)
  return `${((h + 11) % 12) + 1}:${String(m).padStart(2, '0')} ${h < 12 ? 'AM' : 'PM'}`
}

// ---------- calls ----------

export const breedingApi = (farmId: string) => {
  const b = `/farms/${farmId}`
  const q = (params: Record<string, string | number | boolean | undefined | null>) => {
    const s = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') s.set(k, String(v)) })
    const t = s.toString()
    return t ? `?${t}` : ''
  }
  return {
    settings: () => api.get<BreedingSettings>(`${b}/breeding/settings`),
    overview: () => api.get<Overview>(`${b}/breeding/overview`),
    heatWatch: () => api.get<HeatWatchItem[]>(`${b}/breeding/heat-watch`),
    history: (animalId: string | number) => api.get<History>(`${b}/breeding/animals/${animalId}/history`),
    addHeat: (body: Record<string, unknown>) => api.post<{ heat: Heat; bestServiceAt?: string | null; nextExpected?: string | null }>(`${b}/breeding/heats`, body),
    heats: (p: { animalId?: string | number; from?: string; to?: string }) => api.get<Heat[]>(`${b}/breeding/heats${q(p)}`),
    deleteHeat: (id: number) => api.del(`${b}/breeding/heats/${id}`),
    services: (p: { animalId?: string | number; from?: string; to?: string }) => api.get<BreedingRecord[]>(`${b}/breeding/services${q(p)}`),
    service: (id: string | number) => api.get<BreedingRecord>(`${b}/breeding/services/${id}`),
    saveService: (body: Record<string, unknown>, id?: string | number) =>
      (id ? api.put<BreedingRecord>(`${b}/breeding/services/${id}`, body) : api.post<BreedingRecord>(`${b}/breeding/services`, body)),
    deleteService: (id: number) => api.del(`${b}/breeding/services/${id}`),
    pregnancyCheck: (id: number, body: Record<string, unknown>) => api.post<BreedingRecord>(`${b}/breeding/services/${id}/pregnancy-check`, body),
    calving: (body: Record<string, unknown>) => api.post<{ record: BreedingRecord; calves: AnimalRef[] }>(`${b}/breeding/calvings`, body),
    colostrum: (id: number, body: { givenAt: string; litres?: number }) => api.put<BreedingRecord>(`${b}/breeding/services/${id}/colostrum`, body),
    semen: () => api.get<{ totals: { straws: number; low: number; expiring30: number }; batches: SemenBatch[] }>(`${b}/semen`),
    saveSemen: (body: Record<string, unknown>, id?: number) => (id ? api.put<SemenBatch>(`${b}/semen/${id}`, body) : api.post<SemenBatch>(`${b}/semen`, body)),
    adjustSemen: (id: number, quantity: number, reason: string) => api.post<SemenBatch>(`${b}/semen/${id}/adjust`, { quantity, reason }),
    deleteSemen: (id: number) => api.del(`${b}/semen/${id}`),
    calves: (p: { sex?: string; minDays?: number; maxDays?: number }) => api.get<CalfList>(`${b}/calves${q(p)}`),
    calfFeeding: (animalId: string | number, date: string) => api.get<CalfFeedingForm>(`${b}/calves/${animalId}/feeding${q({ date })}`),
    saveCalfFeeding: (animalId: string | number, date: string, body: Record<string, unknown>) => api.put<CalfFeedingForm>(`${b}/calves/${animalId}/feeding/${date}`, body),
    feedingSheet: (p: { date: string; session: 'AM' | 'PM'; sex?: string; minDays?: number; maxDays?: number; groupId?: string }) => api.get<FeedingSheet>(`${b}/calves/feeding-sheet${q(p)}`),
    saveFeedingSheet: (body: { date: string; session: 'AM' | 'PM'; entries: { animalId: number; litres: number | null; starterKg: number | null }[] }) =>
      api.put<FeedingSheet>(`${b}/calves/feeding-sheet`, body),
    family: (animalId: string | number) => api.get<Family>(`${b}/animals/${animalId}/family`),
    mateCheck: (animalId: string | number, p: { sireAnimalId?: string | number; semenItemId?: string | number }) => api.get<MateCheck>(`${b}/animals/${animalId}/mate-check${q(p)}`),
    nextTag: (prefix: string) => api.get<{ tag: string }>(`${b}/animals/next-tag${q({ prefix })}`),
  }
}
