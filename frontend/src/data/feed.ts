// Feed & ration: API shapes and calls. Contract: docs/api-feed.md.
import { api } from '../lib/api'
import type { ContactRef } from './records'
import type { AnimalRef } from './breeding'

export type FeedCategory = 'CONCENTRATE_INGREDIENT' | 'GREEN_FODDER' | 'DRY_FODDER' | 'SILAGE' | 'MINERAL' | 'MIX'
export type Section = 'GREEN' | 'DRY' | 'CONCENTRATE' | 'OTHER'
export type RuleCategory = 'MILKING' | 'DRY' | 'HEIFER' | 'CALF' | 'BULL'
export type StockStatus = 'OK' | 'LOW' | 'REORDER' | 'PREPARE' | 'EXCLUDED' | 'UNUSED'
export type NutrientStatus = 'OK' | 'LOW' | 'HIGH' | 'UNKNOWN'
export type Target = 'FARM' | 'GROUP' | 'ANIMAL'
export type PaymentMethod = 'CASH' | 'UPI' | 'BANK_TRANSFER' | 'CARD' | 'CHEQUE' | 'OTHER'
export type Ses = 'AM' | 'PM'

export interface ItemRef { id: number; name: string; category: FeedCategory; unit: string }
export interface FeedItem {
  id: number; name: string; category: FeedCategory; unit: string; costPerUnit?: number | null
  dmPct?: number | null; cpPct?: number | null; tdnPct?: number | null; caPct?: number | null; pPct?: number | null
  /** BIS quality values, as fed: fat, fibre, acid insoluble ash (%), aflatoxin B1 (ppb) */
  fatPct?: number | null; fibrePct?: number | null; aiaPct?: number | null; aflatoxinPpb?: number | null
  /** 0 = excluded (blocked in every formula and ration) */
  maxInclusionPct?: number | null; excluded: boolean; excludedReason?: string | null
  reorderLevel?: number | null; leadTimeDays?: number | null; supplier?: ContactRef | null; notes?: string | null; active: boolean
}
export interface StockItem {
  item: ItemRef; stock: number; needPerDay?: number | null; daysLeft?: number | null; status: StockStatus; barPct?: number | null
  lastPrice?: number | null; lastPurchaseDate?: string | null
}
export interface SessionInfo { logged: boolean; kg?: number | null; plannedKg?: number | null; time?: string | null; by?: string | null }
export interface Totals { greenKg: number; dryKg: number; concentrateKg: number; costPerDay?: number | null }
export interface FeedOverview {
  date: string; sessions: { AM: SessionInfo; PM: SessionInfo }
  needPerDay: { green: number; dry: number; concentrate: number }; plannedCostPerDay?: number | null
  reorderCount: number; items: StockItem[]
}
export interface Move {
  id: number; date: string; time?: string | null; type: string; quantity: number; cost?: number | null; costPerUnit?: number | null
  supplier?: ContactRef | null; paymentMethod?: PaymentMethod | null; paid?: boolean | null; session?: Ses | null
  target?: { kind: Target; id?: number | null; label: string } | null; sourceType?: string | null; notes?: string | null
}
export interface SheetLine { item: ItemRef; section: Section; plannedKg?: number | null; actualKg?: number | null; stock?: number | null }
export interface FeedingSheet {
  date: string; session: Ses; target: Target; label: string; animals: number; lines: SheetLine[]
  plannedKg?: number | null; actualKg?: number | null; saved: boolean; warnings: string[]
}
export interface FormulaLine { item: ItemRef; kg: number; pct?: number | null; cost?: number | null; overMax?: boolean; maxInclusionPct?: number | null }
export interface Nutrient { name: string; mixPct?: number | null; targetPct?: number | null; status: NutrientStatus }
export type BisGrade = 'TYPE_I' | 'TYPE_II'
export type BisStatus = 'PASS' | 'FAIL' | 'UNKNOWN'
export interface BisParam { name: string; value?: number | null; limit: number; kind: 'MIN' | 'MAX'; unit: '%' | 'ppb'; status: BisStatus }
export interface BisCheck { grade: BisGrade; label: string; status: 'PASS' | 'FAIL' | 'INCOMPLETE'; params: BisParam[] }
export interface FormulaTargets {
  dmPct?: number | null; cpPct?: number | null; tdnPct?: number | null; caPct?: number | null; pPct?: number | null; costMin?: number | null; costMax?: number | null
  bisGrade?: BisGrade | null
}
export interface Formula {
  id: number; name: string; mixItem: ItemRef; batchKg: number; totalKg: number; costPerKg?: number | null; batchCost?: number | null
  targets: FormulaTargets; lines: FormulaLine[]; nutrients: Nutrient[]; bis?: BisCheck | null; warnings: string[]; notes?: string | null
}
export interface BatchPreview {
  formulaId: number; batchKg: number; lines: { item: ItemRef; needKg: number; stock: number; shortKg: number }[]
  shortCount: number; batchCost?: number | null; costPerKg?: number | null; mixStockBefore: number; mixStockAfter: number; extraDays?: number | null
}
export interface AnimalRation {
  animal: AnimalRef & { category: string; breed?: string | null }; date: string
  bodyWeightKg: number; weightSource: 'MEASURED' | 'ANIMAL' | 'ESTIMATE'; weighedOn?: string | null
  milkYesterday?: number | null; daysInMilk?: number | null; stage?: string | null
  /** Fresh kg; targetKg = dry matter intake (kg DM/day), dmiPct = % of body weight; waterL = litres/day */
  totals: { greenKg: number; dryKg: number; concentrateKg: number; totalKg: number; targetKg: number; dmiPct: number; waterL: number }; explain: string
  capped: boolean; lines: { section: Section; item: ItemRef | null; kg: number; timing?: string | null }[]
  ingredients: { item: ItemRef; kgPerDay: number; limitKg?: number | null; over: boolean }[]
  costPerDay?: number | null; nutrients: { name: string; ration?: number | null; need?: number | null; unit: 'kg' | 'g'; status: NutrientStatus }[]
  rule: { category: RuleCategory; effectiveFrom?: string | null }
}
export interface RationSummary { date: string; farm: Totals; categories: { category: RuleCategory; animals: number; totals: Totals }[] }
export interface NeedRow { section?: Section; item: ItemRef; needPerDay: number; stock: number; daysLeft?: number | null; overLeadTime: boolean }
export interface FeedNeed {
  animals: number; perDay: Totals; costPerMonth?: number | null; concentrateCostPerDay?: number | null; concentrateCostPerMonth?: number | null
  rows: NeedRow[]; ingredientRows: NeedRow[]
  shortfalls: { item: ItemRef; quantityKg: number; neededBy?: string | null; expectedPrice?: number | null }[]
}
/** fixedKg: fed at that fresh kg (e.g. hydroponic fodder, azolla); its DM comes off the part first */
export interface ItemShare { itemId: number; sharePct: number; fixedKg?: number | null }
export interface Limit { itemId: number; maxKgPerDay: number; stage: 'ANY' | 'EARLY' | 'MID' | 'LATE' }
export interface RationParams {
  /** MILK = base + milk × per litre; DMI = DMI × concentrate DM share ÷ its DM */
  concMode: 'MILK' | 'DMI'; concBaseKg: number; concPerLitreKg: number; maxConcKg: number; concItemId?: number | null
  greenDmPct: number; dryDmPct: number; concDmPct: number; green: ItemShare[]; dry: ItemShare[]; limits: Limit[]
  dmIntakePct: number; dmiEarlyPct: number; dmiMidPct: number; dmiLatePct: number
  dmiAge3to6Pct: number; dmiAge6to12Pct: number; dmiAge12to24Pct: number; waterBaseL: number; waterPerLitreL: number
  cpPct: number; tdnPct: number; caPct: number; pPct: number
}
export interface RuleVersion { effectiveFrom: string; params: RationParams }
export interface RationRule { category: RuleCategory; current: RuleVersion; upcoming: RuleVersion[]; history: RuleVersion[] }

// ---------- labels ----------

export const FEED_CATEGORIES: { value: FeedCategory; label: string; short: string }[] = [
  { value: 'CONCENTRATE_INGREDIENT', label: 'Concentrate ingredient', short: 'Concentrate ingr.' },
  { value: 'GREEN_FODDER', label: 'Green fodder', short: 'Green fodder' },
  { value: 'DRY_FODDER', label: 'Dry fodder', short: 'Dry fodder' },
  { value: 'SILAGE', label: 'Silage', short: 'Silage' },
  { value: 'MINERAL', label: 'Mineral', short: 'Mineral' },
  { value: 'MIX', label: 'Mix targets', short: 'Mix targets' },
]
export const categoryLabel = (c?: string | null) => FEED_CATEGORIES.find((x) => x.value === c)?.short ?? c ?? ''
/** Stock list filter chips: Green / Dry / Concentrate (ingredients) / Mineral / Mix targets. */
export const STOCK_FILTERS: { key: string; label: string; match: (c: FeedCategory) => boolean }[] = [
  { key: 'ALL', label: 'All', match: () => true },
  { key: 'GREEN', label: 'Green', match: (c) => c === 'GREEN_FODDER' || c === 'SILAGE' },
  { key: 'DRY', label: 'Dry', match: (c) => c === 'DRY_FODDER' },
  { key: 'CONC', label: 'Concentrate', match: (c) => c === 'CONCENTRATE_INGREDIENT' },
  { key: 'MINERAL', label: 'Mineral', match: (c) => c === 'MINERAL' },
  { key: 'MIX', label: 'Mix targets', match: (c) => c === 'MIX' },
]
export const SECTION_LABEL: Record<Section, string> = {
  GREEN: 'Before milking · green', DRY: 'After milking · dry', CONCENTRATE: 'After milking · concentrate', OTHER: 'Other',
}
export const STATUS_BADGE: Partial<Record<StockStatus, { text: string; cls: string }>> = {
  REORDER: { text: 'Reorder', cls: 'bg-danger-soft text-danger-text' },
  PREPARE: { text: 'Prepare', cls: 'bg-danger-soft text-danger-text' },
  LOW: { text: 'Low', cls: 'bg-warn-soft text-warn-ink' },
  EXCLUDED: { text: 'Excluded', cls: 'bg-ground text-ink-2' },
  UNUSED: { text: 'Not in plan', cls: 'bg-ground text-ink-2' },
}
export const NUTRIENT_TONE: Record<NutrientStatus, { text: string; cls: string }> = {
  OK: { text: 'OK', cls: 'bg-brand-soft text-brand' }, LOW: { text: 'Low', cls: 'bg-warn-soft text-warn-ink' },
  HIGH: { text: 'High', cls: 'bg-info-soft text-info-text' }, UNKNOWN: { text: '—', cls: 'bg-ground text-ink-3' },
}
export const RULE_CATEGORIES: { value: RuleCategory; label: string }[] = [
  { value: 'MILKING', label: 'Milking' }, { value: 'DRY', label: 'Dry' }, { value: 'HEIFER', label: 'Heifer' },
  { value: 'CALF', label: 'Calf' }, { value: 'BULL', label: 'Bull' },
]
export const ruleLabel = (c: RuleCategory | string) => RULE_CATEGORIES.find((x) => x.value === c)?.label ?? c
export const PAYMENT_METHODS: { value: PaymentMethod; label: string }[] = [
  { value: 'UPI', label: 'UPI' }, { value: 'CASH', label: 'Cash' }, { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'CARD', label: 'Card' }, { value: 'CHEQUE', label: 'Cheque' }, { value: 'OTHER', label: 'Other' },
]
/**
 * IS 2052:2023 compounded cattle feed, Table 1 (Type III was removed in 2021). All limits except moisture are on a
 * moisture-free (dry matter) basis. Same rules as backend feed/BisGrade.
 */
export const BIS_GRADES: { value: BisGrade; label: string; hint: string; cpMin: number; fatMin: number; fibreMax: number; aiaMax: number }[] = [
  { value: 'TYPE_I', label: 'Type I', hint: 'High yielders', cpMin: 22, fatMin: 4.0, fibreMax: 10, aiaMax: 2.5 },
  { value: 'TYPE_II', label: 'Type II', hint: 'Low to medium yielders', cpMin: 20, fatMin: 3.0, fibreMax: 12, aiaMax: 3.0 },
]
/** As-fed mix values (null = some ingredient has no value) → the grade check, dry basis. */
export function bisCheck(grade: BisGrade, m: { dm: number | null; cp: number | null; fat: number | null; fibre: number | null; aia: number | null; ca: number | null; p: number | null; aflatoxin: number | null }): BisCheck {
  const g = BIS_GRADES.find((x) => x.value === grade)!
  const dry = (v: number | null) => (v == null || m.dm == null || m.dm <= 0 ? null : (v * 100) / m.dm)
  const param = (name: string, v: number | null, limit: number, kind: 'MIN' | 'MAX', unit: '%' | 'ppb' = '%'): BisParam => {
    const value = v == null ? null : Math.round(v * 100) / 100
    return { name, value, limit, kind, unit, status: value == null ? 'UNKNOWN' : (kind === 'MIN' ? value >= limit : value <= limit) ? 'PASS' : 'FAIL' }
  }
  const params = [
    param('Moisture', m.dm == null ? null : 100 - m.dm, 11, 'MAX'),
    param('Crude protein', dry(m.cp), g.cpMin, 'MIN'),
    param('Crude fat', dry(m.fat), g.fatMin, 'MIN'),
    param('Crude fibre', dry(m.fibre), g.fibreMax, 'MAX'),
    param('Acid insoluble ash', dry(m.aia), g.aiaMax, 'MAX'),
    param('Calcium', dry(m.ca), 0.8, 'MIN'),
    param('Total phosphorus', dry(m.p), 0.5, 'MIN'),
    param('Aflatoxin B1', dry(m.aflatoxin), 20, 'MAX', 'ppb'),
  ]
  const status = params.some((p) => p.status === 'FAIL') ? 'FAIL' : params.some((p) => p.status === 'UNKNOWN') ? 'INCOMPLETE' : 'PASS'
  return { grade, label: g.label, status, params }
}

/** "262.5" without trailing zeros noise, or "—". */
export const kg = (n?: number | null, digits = 1) => (n === null || n === undefined ? '—' : Number(n.toFixed(digits)).toLocaleString('en-IN'))
export const days = (n?: number | null) => (n === null || n === undefined ? '—' : n >= 100 ? '99+ days' : `${n < 10 ? n.toFixed(1).replace(/\.0$/, '') : Math.round(n)} days`)
export const inr2 = (n?: number | null) => (n === null || n === undefined ? '—' : `₹${n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`)

// ---------- calls ----------

export const feedApi = (farmId: string) => {
  const b = `/farms/${farmId}/feed`
  const q = (params: Record<string, string | number | boolean | undefined | null>) => {
    const s = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') s.set(k, String(v)) })
    const t = s.toString()
    return t ? `?${t}` : ''
  }
  return {
    overview: (date?: string) => api.get<FeedOverview>(`${b}/overview${q({ date })}`),
    items: (p: { category?: FeedCategory; includeInactive?: boolean } = {}) => api.get<FeedItem[]>(`${b}/items${q(p)}`),
    item: (id: string | number) => api.get<FeedItem>(`${b}/items/${id}`),
    saveItem: (body: Record<string, unknown>, id?: string | number) => (id ? api.put<FeedItem>(`${b}/items/${id}`, body) : api.post<FeedItem>(`${b}/items`, body)),
    deleteItem: (id: number) => api.del(`${b}/items/${id}`),
    moves: (id: string | number, p: { from?: string; to?: string } = {}) => api.get<Move[]>(`${b}/items/${id}/moves${q(p)}`),
    stock: (id: string | number, body: { type: 'HARVEST' | 'ADJUSTMENT'; date: string; quantity: number; notes?: string }) => api.post<StockItem>(`${b}/items/${id}/stock`, body),
    purchase: (body: Record<string, unknown>) => api.post<{ move: Move; item: StockItem }>(`${b}/purchases`, body),
    purchases: (p: { from?: string; to?: string; itemId?: string | number }) => api.get<Move[]>(`${b}/purchases${q(p)}`),
    sheet: (p: { date: string; session: Ses; target: Target; groupId?: string; animalId?: string }) => api.get<FeedingSheet>(`${b}/feeding-sheet${q(p)}`),
    saveSheet: (body: Record<string, unknown>) => api.put<FeedingSheet>(`${b}/feeding-sheet`, body),
    formulas: () => api.get<Formula[]>(`${b}/formulas`),
    formula: (id: string | number) => api.get<Formula>(`${b}/formulas/${id}`),
    saveFormula: (body: Record<string, unknown>, id?: string | number) => (id ? api.put<Formula>(`${b}/formulas/${id}`, body) : api.post<Formula>(`${b}/formulas`, body)),
    deleteFormula: (id: number) => api.del(`${b}/formulas/${id}`),
    batchPreview: (id: string | number, batchKg: number) => api.get<BatchPreview>(`${b}/formulas/${id}/batch-preview${q({ batchKg })}`),
    prepareBatch: (id: string | number, body: Record<string, unknown>) => api.post<{ preview: BatchPreview; mix: StockItem }>(`${b}/formulas/${id}/batches`, body),
    ration: (animalId: string | number, date?: string) => api.get<AnimalRation>(`${b}/ration${q({ animalId, date })}`),
    summary: (date?: string) => api.get<RationSummary>(`${b}/ration/summary${q({ date })}`),
    need: (d = 7) => api.get<FeedNeed>(`${b}/need${q({ days: d })}`),
    rules: () => api.get<RationRule[]>(`${b}/ration-rules`),
    saveRule: (category: RuleCategory, body: RuleVersion) => api.put<RationRule>(`${b}/ration-rules/${category}`, body),
  }
}
