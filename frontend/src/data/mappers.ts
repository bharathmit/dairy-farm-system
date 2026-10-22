// Converts between the API's shapes (UPPER_CASE values, numeric ids) and the UI's
// (display values, string ids). Pages only ever see the UI types.
import type { Animal, AnimalStatus, Category, Farm, Group, Member, Role, Settings, Sex, Species } from './types'

// ---------- API shapes ----------

export interface ApiFarmSummary { id: number; name: string; village?: string; district?: string; state?: string; role: string; memberId: number; animalCount: number }
export interface ApiFarm { id: number; name: string; village?: string; district?: string; state?: string; country?: string; role: string }
export interface ApiMe { id: string; fullName?: string; email?: string; phone?: string; preferences: { theme: string; language: string }; farms: ApiFarmSummary[] }
export interface ApiNotifications { enabled: boolean; health: boolean; breeding: boolean; checklist: boolean; leadDays: number; reminderTime: string; digest: boolean; digestTime: string; quietHoursStart: string; quietHoursEnd: string }
export interface ApiSettings { milkRate?: number; morningMilking: string; eveningMilking: string; marginAlertPct: number; leaveDaysPerMonth?: number; notifications: ApiNotifications }
export interface ApiParent { id?: number; tag: string; name?: string }
export interface ApiAnimal {
  id: number; tag: string; name?: string; nationalId?: string; species: string; breed?: string; sex: string
  dateOfBirth?: string; colour?: string; birthWeightKg?: number; category: string; status: string
  groupId?: number; dam?: ApiParent; sire?: ApiParent; acquisitionType: string; acquisitionDate?: string
  purchasePrice?: number; exitDate?: string; exitReason?: string; exitPrice?: number; exitParty?: string
  lastCalvingDate?: string; currentWeightKg?: number; notes?: string; photos: { id: string; url?: string }[]
}
export interface ApiGroup { id: number; name: string; species: string; location?: string; purpose: string; managerMemberId?: number; colour?: string; notes?: string }
export interface ApiMember {
  id: number; name: string; role: string; email?: string; phone?: string; dateOfBirth?: string; sex?: string
  wageType: string; wageRate?: number; joinedOn?: string; leftOn?: string; active: boolean; loginType: string
  signIn: string; me: boolean; notice?: string
}
export interface ApiPage<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number }
export interface ImportResult { dryRun: boolean; rows: number; ready: number; created: number; duplicates: string[]; errors: string[] }

// ---------- value helpers ----------

/** "MILKING" → "Milking" */
const title = (s: string) => s.charAt(0) + s.slice(1).toLowerCase()
const up = (s: string) => s.toUpperCase()
const str = (n?: number) => (n === undefined || n === null ? undefined : String(n))
const num = (s?: string) => (s ? Number(s) : undefined)

const PURPOSES: Record<string, string> = {
  GROWTH: 'Growth / fattening', MAINTENANCE: 'Maintenance', RECOVERY: 'Recovery', GESTATION: 'Gestation', OTHER: 'Other',
}
const LEADS: Record<number, string> = { 0: 'Same day', 1: '1 day before', 3: '3 days', 7: '7 days' }

const DEFAULT_COLOUR = '#1F5C3A'

function to12h(t: string) {
  const [h, m] = t.split(':').map(Number)
  if (Number.isNaN(h)) return t
  const hour = ((h + 11) % 12) + 1
  return `${hour}${m ? `:${String(m).padStart(2, '0')}` : ''} ${h < 12 ? 'AM' : 'PM'}`
}

function initials(name: string) {
  const words = name.replace(/[^\p{L}\p{N} ]/gu, ' ').split(/\s+/).filter(Boolean)
  return (words.length > 1 ? words[0][0] + words[1][0] : name.slice(0, 2)).toUpperCase()
}

// ---------- farms and settings ----------

export function toFarm(f: ApiFarmSummary | ApiFarm, animals?: number): Farm {
  return {
    id: String(f.id), name: f.name, village: f.village, district: f.district, state: f.state,
    country: 'country' in f ? f.country : undefined, role: title(f.role) as Role,
    animals: animals ?? ('animalCount' in f ? f.animalCount : 0), initials: initials(f.name),
  }
}

export function toSettings(s: ApiSettings, prefs: ApiMe['preferences']): Settings {
  const n = s.notifications
  return {
    theme: title(prefs.theme) as Settings['theme'],
    language: prefs.language === 'TA' ? 'Tamil' : 'English',
    milkRate: s.milkRate ?? 0,
    morningMilking: s.morningMilking,
    eveningMilking: s.eveningMilking,
    marginAlertPct: s.marginAlertPct,
    leaveDaysPerMonth: s.leaveDaysPerMonth ?? 4,
    notify: {
      enabled: n.enabled, health: n.health, breeding: n.breeding, checklist: n.checklist,
      lead: LEADS[n.leadDays] ?? `${n.leadDays} days`, reminderTime: n.reminderTime, digest: n.digest,
      digestTime: n.digestTime, quietHours: `${to12h(n.quietHoursStart)} – ${to12h(n.quietHoursEnd)}`,
    },
  }
}

/** Only the farm-wide parts of a settings change, in API form. */
export function fromSettingsPatch(p: Partial<Settings>): Partial<ApiSettings> | null {
  const out: Record<string, unknown> = {}
  if (p.milkRate !== undefined) out.milkRate = p.milkRate
  if (p.morningMilking !== undefined) out.morningMilking = p.morningMilking
  if (p.eveningMilking !== undefined) out.eveningMilking = p.eveningMilking
  if (p.marginAlertPct !== undefined) out.marginAlertPct = p.marginAlertPct
  if (p.leaveDaysPerMonth !== undefined) out.leaveDaysPerMonth = p.leaveDaysPerMonth
  if (p.notify) {
    const n = p.notify
    const lead = Number(Object.entries(LEADS).find(([, v]) => v === n.lead)?.[0] ?? 1)
    out.notifications = {
      enabled: n.enabled, health: n.health, breeding: n.breeding, checklist: n.checklist,
      leadDays: lead, reminderTime: n.reminderTime, digest: n.digest, digestTime: n.digestTime,
    }
  }
  return Object.keys(out).length ? (out as Partial<ApiSettings>) : null
}

export function fromPreferencesPatch(p: Partial<Settings>) {
  if (p.theme === undefined && p.language === undefined) return null
  return {
    theme: p.theme ? up(p.theme) : undefined,
    language: p.language ? (p.language === 'Tamil' ? 'TA' : 'EN') : undefined,
  }
}

// ---------- animals ----------

export function toAnimal(a: ApiAnimal): Animal {
  return {
    id: String(a.id), tag: a.tag, name: a.name, nationalId: a.nationalId,
    species: title(a.species) as Species, sex: title(a.sex) as Sex, breed: a.breed,
    category: title(a.category) as Category, color: a.colour, groupId: str(a.groupId),
    source: a.acquisitionType === 'BORN' ? 'Born on farm' : 'Purchased',
    dob: a.dateOfBirth, birthWeightKg: a.birthWeightKg, purchaseDate: a.acquisitionDate,
    purchasePrice: a.purchasePrice, lastCalving: a.lastCalvingDate, weightKg: a.currentWeightKg,
    dam: a.dam?.tag, sire: a.sire?.tag, notes: a.notes, status: title(a.status) as AnimalStatus,
    flags: [],
    exit: a.exitDate ? { date: a.exitDate, price: a.exitPrice, reason: a.exitReason ?? '', buyer: a.exitParty } : undefined,
    photos: a.photos,
  }
}

export function fromAnimal(a: Animal) {
  const purchased = a.source === 'Purchased'
  return {
    tag: a.tag, name: a.name, nationalId: a.nationalId || undefined, species: up(a.species), breed: a.breed || undefined,
    sex: up(a.sex), dateOfBirth: a.dob, colour: a.color || undefined, birthWeightKg: a.birthWeightKg,
    category: up(a.category), groupId: num(a.groupId), damTag: a.dam?.trim() || undefined,
    sireTag: a.sire?.trim() || undefined, acquisitionType: purchased ? 'PURCHASED' : 'BORN',
    acquisitionDate: purchased ? a.purchaseDate : undefined, purchasePrice: purchased ? a.purchasePrice : undefined,
    lastCalvingDate: a.lastCalving, currentWeightKg: a.weightKg, notes: a.notes || undefined,
  }
}

// ---------- groups ----------

export function toGroup(g: ApiGroup): Group {
  return {
    id: String(g.id), name: g.name, species: title(g.species) as Species, location: g.location,
    purpose: PURPOSES[g.purpose] ?? 'Other', managerId: str(g.managerMemberId), color: g.colour ?? DEFAULT_COLOUR,
    notes: g.notes,
  }
}

export function fromGroup(g: Group) {
  const purpose = Object.entries(PURPOSES).find(([, v]) => v === g.purpose)?.[0] ?? 'OTHER'
  return {
    name: g.name, species: up(g.species), location: g.location, purpose, managerMemberId: num(g.managerId),
    colour: g.color, notes: g.notes,
  }
}

// ---------- team ----------

export function toMember(m: ApiMember): Member {
  return {
    id: String(m.id), name: m.name, role: title(m.role) as Role, dob: m.dateOfBirth,
    sex: m.sex ? (title(m.sex) as Sex) : undefined, phone: m.phone, email: m.email,
    canSignIn: m.signIn !== 'NONE', wageType: title(m.wageType) as Member['wageType'], wage: m.wageRate,
    joined: m.joinedOn, active: m.active,
    inactiveSince: m.leftOn ? new Date(m.leftOn).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' }) : undefined,
    isMe: m.me, loginType: m.loginType,
  }
}

/** Decides the sign-in change for the API: null keeps the current sign-in. */
export function fromMember(m: Member, existing: Member | undefined, password?: string) {
  let login: string | null = null
  if (!existing) login = m.canSignIn ? 'PASSWORD' : 'NONE'
  else if (!m.canSignIn && existing.canSignIn) login = 'NONE'
  else if (m.canSignIn && (!existing.canSignIn || password)) login = 'PASSWORD'
  return {
    name: m.name, role: up(m.role), email: m.email?.trim() || undefined, phone: m.phone?.trim() || undefined,
    dateOfBirth: m.dob, sex: m.sex ? up(m.sex) : undefined, wageType: up(m.wageType),
    wageRate: m.wageType === 'None' ? undefined : m.wage, joinedOn: m.joined, login,
    password: login === 'PASSWORD' && password ? password : undefined,
  }
}
