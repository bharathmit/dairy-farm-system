/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { Session } from '@supabase/supabase-js'
import { supabase, googleEnabled } from '../lib/supabase'
import { api, errorMessage } from '../lib/api'
import {
  fromAnimal, fromGroup, fromMember, fromPreferencesPatch, fromSettingsPatch, toAnimal, toFarm, toGroup, toMember,
  toSettings, type ApiAnimal, type ApiFarm, type ApiGroup, type ApiMe, type ApiMember, type ApiPage, type ApiSettings,
  type ImportResult,
} from './mappers'
import type { Animal, AnimalStatus, Farm, Group, Member, Settings, User } from './types'

// The app's data for the signed-in person and the chosen farm. Sign-in goes through Supabase;
// everything else through the backend API (docs/api.md). Actions report failures with a toast
// and resolve to null/false, so pages only decide where to go next.

export type { ImportResult } from './mappers'

interface Store {
  /** False until the saved sign-in (if any) has been checked. */
  ready: boolean
  /** True while the chosen farm's data is loading. */
  loading: boolean
  user: User | null
  farm: Farm | null
  farms: Farm[]
  animals: Animal[]
  groups: Group[]
  members: Member[]
  settings: Settings
  toast: string | null
  notify: (text: string) => void

  signIn: (email: string, password: string) => Promise<string | null>
  signInWithGoogle: () => Promise<void>
  signUp: (p: { name: string; email: string; phone?: string; farmName: string; password: string }) => Promise<'signed-in' | 'confirm-email' | null>
  sendPasswordReset: (email: string) => Promise<boolean>
  updatePassword: (password: string) => Promise<boolean>
  signOut: () => Promise<void>

  chooseFarm: (id: string) => void
  addFarm: (name: string) => Promise<boolean>
  updateFarm: (patch: Partial<Farm>) => Promise<boolean>
  updateSettings: (patch: Partial<Settings>) => Promise<boolean>

  saveAnimal: (a: Animal) => Promise<Animal | null>
  changeAnimalStatus: (id: string, status: AnimalStatus, exit?: Animal['exit']) => Promise<boolean>
  moveAnimal: (id: string, groupId?: string) => Promise<boolean>
  deleteAnimal: (id: string) => Promise<boolean>
  addPhoto: (animalId: string, file: File) => Promise<boolean>
  checkImport: (file: File) => Promise<ImportResult | null>
  runImport: (file: File) => Promise<ImportResult | null>
  downloadExport: () => Promise<void>
  downloadTemplate: () => Promise<void>

  saveGroup: (g: Group, animalIds: string[]) => Promise<Group | null>
  deleteGroup: (id: string) => Promise<boolean>

  saveMember: (m: Member, password?: string) => Promise<boolean>
  /** Reload animals and groups (after a server-side change such as a calving). */
  refreshHerd: () => Promise<void>
  setMemberActive: (id: string, active: boolean) => Promise<boolean>
}

const Ctx = createContext<Store | null>(null)

const FARM_KEY = 'dairy.farmId'
const PENDING_FARM_KEY = 'dairy.pendingFarm'

/** Shown until the farm's own settings arrive. */
const DEFAULT_FARM_SETTINGS: ApiSettings = {
  morningMilking: '06:00', eveningMilking: '18:00', marginAlertPct: 30, leaveDaysPerMonth: 4,
  notifications: { enabled: true, health: true, breeding: true, checklist: true, leadDays: 1, reminderTime: '08:00', digest: true, digestTime: '07:00', quietHoursStart: '22:00', quietHoursEnd: '05:00' },
}
const DEFAULT_PREFERENCES = { theme: 'SYSTEM', language: 'EN' }

let seq = 0
/** Temporary id for a new item before the server assigns one. */
export const newId = (p: string) => `new-${p}${++seq}`

const local = {
  get: (k: string) => { try { return localStorage.getItem(k) } catch { return null } },
  set: (k: string, v: string | null) => { try { if (v === null) localStorage.removeItem(k); else localStorage.setItem(k, v) } catch { /* private mode */ } },
}

function saveFile(filename: string, blob: Blob) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export function StoreProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false)
  const [doneFor, setDoneFor] = useState<string | null>(null)
  const [me, setMe] = useState<ApiMe | null>(null)
  const [farms, setFarms] = useState<Farm[]>([])
  const [farmId, setFarmId] = useState<string | null>(null)
  const [animals, setAnimals] = useState<Animal[]>([])
  const [groups, setGroups] = useState<Group[]>([])
  const [members, setMembers] = useState<Member[]>([])
  const [farmSettings, setFarmSettings] = useState<ApiSettings | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const loadedFor = useRef<string | null>(null)

  const notify = useCallback((text: string) => {
    setToast(text)
    window.setTimeout(() => setToast((t) => (t === text ? null : t)), 2600)
  }, [])

  const fail = useCallback((e: unknown) => { notify(errorMessage(e)) }, [notify])

  const farm = farms.find((f) => f.id === farmId) ?? null
  const loading = Boolean(farm) && doneFor !== farmId
  const base = farmId ? `/farms/${farmId}` : ''

  // ---------- loading ----------

  // Supabase can report one sign-in several times (e.g. INITIAL_SESSION and SIGNED_IN after the
  // email link), so concurrent calls share one load instead of each creating the pending farm.
  const meLoad = useRef<Promise<ApiMe> | null>(null)
  const fetchMe = useCallback(async () => {
    let profile = await api.get<ApiMe>('/me')
    const pending = local.get(PENDING_FARM_KEY)
    if (pending && profile.farms.length === 0) {
      local.set(PENDING_FARM_KEY, null) // claim it before creating, so it's used once
      await api.post('/farms', { name: pending })
      profile = await api.get<ApiMe>('/me')
    }
    local.set(PENDING_FARM_KEY, null)
    return profile
  }, [])

  const loadMe = useCallback(async () => {
    if (!meLoad.current) meLoad.current = fetchMe().finally(() => { meLoad.current = null })
    const profile = await meLoad.current
    setMe(profile)
    const list = profile.farms.map((f) => toFarm(f))
    setFarms(list)
    const saved = local.get(FARM_KEY)
    setFarmId((cur) => cur ?? (list.some((f) => f.id === saved) ? saved : list.length === 1 ? list[0].id : null))
    return profile
  }, [fetchMe])

  const loadAnimals = useCallback(async (fid: string) => {
    const all: Animal[] = []
    for (let page = 0; ; page++) {
      const res = await api.get<ApiPage<ApiAnimal>>(`/farms/${fid}/animals/details?size=200&page=${page}`)
      all.push(...res.items.map(toAnimal))
      if (page + 1 >= res.totalPages) break
    }
    setAnimals(all)
    setFarms((fs) => fs.map((f) => (f.id === fid ? { ...f, animals: all.filter((a) => a.status === 'Active').length } : f)))
  }, [])

  const loadGroups = useCallback(async (fid: string) => {
    setGroups((await api.get<ApiGroup[]>(`/farms/${fid}/groups`)).map(toGroup))
  }, [])

  const loadMembers = useCallback(async (fid: string, owner: boolean) => {
    setMembers(owner ? (await api.get<ApiMember[]>(`/farms/${fid}/members`)).map(toMember) : [])
  }, [])

  // Sign-in state: on load, after sign-in, after the email link, and on sign-out.
  useEffect(() => {
    let cancelled = false
    const onSession = async (session: Session | null) => {
      if (!session) {
        setMe(null); setFarms([]); setFarmId(null); setAnimals([]); setGroups([]); setMembers([]); setFarmSettings(null)
        loadedFor.current = null
        if (!cancelled) setReady(true)
        return
      }
      try {
        await loadMe()
      } catch (e) {
        fail(e)
      } finally {
        if (!cancelled) setReady(true)
      }
    }
    const { data } = supabase.auth.onAuthStateChange((event, session) => {
      if (event === 'INITIAL_SESSION' || event === 'SIGNED_IN' || event === 'SIGNED_OUT') {
        // Defer: Supabase asks not to await other auth calls inside this callback.
        window.setTimeout(() => { void onSession(session) }, 0)
      }
    })
    return () => { cancelled = true; data.subscription.unsubscribe() }
  }, [loadMe, fail])

  // The chosen farm's data.
  useEffect(() => {
    if (!farmId || !farm || loadedFor.current === farmId) return
    loadedFor.current = farmId
    local.set(FARM_KEY, farmId)
    setDoneFor(null)
    setAnimals([]); setGroups([]); setMembers([]); setFarmSettings(null)
    Promise.all([
      api.get<ApiSettings>(`/farms/${farmId}/settings`).then(setFarmSettings),
      loadAnimals(farmId),
      loadGroups(farmId),
      loadMembers(farmId, farm.role === 'Owner'),
    ]).catch(fail).finally(() => setDoneFor(farmId))
  }, [farmId, farm, loadAnimals, loadGroups, loadMembers, fail])

  const settings = useMemo<Settings>(
    () => toSettings(farmSettings ?? DEFAULT_FARM_SETTINGS, me?.preferences ?? DEFAULT_PREFERENCES),
    [me, farmSettings])

  const user = useMemo<User | null>(() => (me ? { name: me.fullName || me.email?.split('@')[0] || 'You', email: me.email ?? '' } : null), [me])

  // ---------- the store ----------

  const value = useMemo<Store>(() => {
    const refreshHerd = async () => { if (farmId) await Promise.all([loadAnimals(farmId), loadGroups(farmId)]) }

    return {
      ready, loading, user, farm, farms, animals, groups, members, settings, toast, notify,
      refreshHerd,

      // ----- sign-in -----
      signIn: async (email, password) => {
        const { error } = await supabase.auth.signInWithPassword({ email, password })
        if (!error) return null
        return error.message === 'Invalid login credentials' ? 'Wrong email or password.' : error.message
      },
      signInWithGoogle: async () => {
        if (!(await googleEnabled())) {
          notify("Google sign-in isn't turned on yet. Use your email and password.")
          return
        }
        const { error } = await supabase.auth.signInWithOAuth({ provider: 'google', options: { redirectTo: `${window.location.origin}/farms` } })
        if (error) notify(error.message)
      },
      signUp: async ({ name, email, phone, farmName, password }) => {
        local.set(PENDING_FARM_KEY, farmName)
        const { data, error } = await supabase.auth.signUp({
          email, password,
          options: { data: { full_name: name, phone }, emailRedirectTo: `${window.location.origin}/farms` },
        })
        if (error) { local.set(PENDING_FARM_KEY, null); notify(error.message); return null }
        return data.session ? 'signed-in' : 'confirm-email'
      },
      sendPasswordReset: async (email) => {
        const { error } = await supabase.auth.resetPasswordForEmail(email, { redirectTo: `${window.location.origin}/reset-password` })
        if (error) { notify(error.message); return false }
        return true
      },
      updatePassword: async (password) => {
        const { error } = await supabase.auth.updateUser({ password })
        if (error) { notify(error.message); return false }
        return true
      },
      signOut: async () => {
        local.set(FARM_KEY, null)
        await supabase.auth.signOut()
      },

      // ----- farm and settings -----
      chooseFarm: (id) => setFarmId(id),
      addFarm: async (name) => {
        try {
          const f = await api.post<ApiFarm>('/farms', { name })
          await loadMe()
          setFarmId(String(f.id))
          return true
        } catch (e) { fail(e); return false }
      },
      updateFarm: async (patch) => {
        if (!farm) return false
        try {
          const f = await api.put<ApiFarm>(base, {
            name: patch.name ?? farm.name, village: patch.village ?? farm.village, district: patch.district ?? farm.district,
            state: patch.state ?? farm.state, country: patch.country ?? farm.country,
          })
          setFarms((fs) => fs.map((x) => (x.id === farm.id ? toFarm(f, x.animals) : x)))
          return true
        } catch (e) { fail(e); return false }
      },
      updateSettings: async (patch) => {
        try {
          const prefs = fromPreferencesPatch(patch)
          if (prefs) {
            const saved = await api.put<ApiMe['preferences']>('/me/preferences', prefs)
            setMe((m) => (m ? { ...m, preferences: saved } : m))
          }
          const farmPatch = fromSettingsPatch(patch)
          if (farmPatch && farmId) setFarmSettings(await api.put<ApiSettings>(`${base}/settings`, farmPatch))
          return true
        } catch (e) { fail(e); return false }
      },

      // ----- animals -----
      saveAnimal: async (a) => {
        try {
          const body = fromAnimal(a)
          const exists = animals.some((x) => x.id === a.id)
          const saved = exists
            ? await api.put<ApiAnimal>(`${base}/animals/${a.id}`, body)
            : await api.post<ApiAnimal>(`${base}/animals`, body)
          await refreshHerd()
          return toAnimal(saved)
        } catch (e) { fail(e); return null }
      },
      changeAnimalStatus: async (id, status, exit) => {
        try {
          await api.patch(`${base}/animals/${id}/status`, {
            status: status.toUpperCase(), date: exit?.date, price: exit?.price, reason: exit?.reason, party: exit?.buyer,
          })
          await refreshHerd()
          return true
        } catch (e) { fail(e); return false }
      },
      moveAnimal: async (id, groupId) => {
        try {
          await api.patch(`${base}/animals/${id}/group`, { groupId: groupId ? Number(groupId) : null })
          await refreshHerd()
          return true
        } catch (e) { fail(e); return false }
      },
      deleteAnimal: async (id) => {
        try {
          await api.del(`${base}/animals/${id}`)
          await refreshHerd()
          return true
        } catch (e) { fail(e); return false }
      },
      addPhoto: async (animalId, file) => {
        try {
          const form = new FormData()
          form.append('file', file)
          const saved = toAnimal(await api.post<ApiAnimal>(`${base}/animals/${animalId}/photos`, form))
          setAnimals((as) => as.map((x) => (x.id === saved.id ? saved : x)))
          return true
        } catch (e) { fail(e); return false }
      },
      checkImport: async (file) => {
        try {
          const form = new FormData()
          form.append('file', file)
          return await api.post<ImportResult>(`${base}/animals/import?dryRun=true`, form)
        } catch (e) { fail(e); return null }
      },
      runImport: async (file) => {
        try {
          const form = new FormData()
          form.append('file', file)
          const res = await api.post<ImportResult>(`${base}/animals/import?dryRun=false`, form)
          await refreshHerd()
          return res
        } catch (e) { fail(e); return null }
      },
      downloadExport: async () => {
        try { saveFile(`herd-${new Date().toISOString().slice(0, 10)}.csv`, await api.blob(`${base}/animals/export.csv`)) } catch (e) { fail(e) }
      },
      downloadTemplate: async () => {
        try { saveFile('herd-template.csv', await api.blob(`${base}/animals/import/template`)) } catch (e) { fail(e) }
      },

      // ----- groups -----
      saveGroup: async (g, animalIds) => {
        try {
          const exists = groups.some((x) => x.id === g.id)
          const saved = exists
            ? await api.put<ApiGroup>(`${base}/groups/${g.id}`, fromGroup(g))
            : await api.post<ApiGroup>(`${base}/groups`, fromGroup(g))
          await api.put(`${base}/groups/${saved.id}/animals`, { animalIds: animalIds.map(Number) })
          await refreshHerd()
          return toGroup(saved)
        } catch (e) { fail(e); return null }
      },
      deleteGroup: async (id) => {
        try {
          await api.del(`${base}/groups/${id}`)
          await refreshHerd()
          return true
        } catch (e) { fail(e); return false }
      },

      // ----- team -----
      saveMember: async (m, password) => {
        try {
          const existing = members.find((x) => x.id === m.id)
          const body = fromMember(m, existing, password)
          const saved = existing
            ? await api.put<ApiMember>(`${base}/members/${m.id}`, body)
            : await api.post<ApiMember>(`${base}/members`, body)
          if (farmId) await loadMembers(farmId, true)
          if (saved.notice) notify(saved.notice)
          return true
        } catch (e) { fail(e); return false }
      },
      setMemberActive: async (id, active) => {
        try {
          await api.patch(`${base}/members/${id}/status`, { active })
          if (farmId) await loadMembers(farmId, true)
          return true
        } catch (e) { fail(e); return false }
      },
    }
  }, [ready, loading, user, farm, farms, animals, groups, members, settings, toast, notify, fail, base, farmId,
    loadMe, loadAnimals, loadGroups, loadMembers])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useStore() {
  const s = useContext(Ctx)
  if (!s) throw new Error('useStore outside StoreProvider')
  return s
}

// ---------- helpers ----------

export const TODAY = new Date()

export function ageText(dob?: string) {
  if (!dob) return ''
  const d = new Date(dob)
  const days = Math.floor((TODAY.getTime() - d.getTime()) / 86400000)
  if (days < 60) return `${days} d`
  const months = Math.floor(days / 30.44)
  if (months < 24) return `${months} m`
  const y = Math.floor(months / 12)
  const m = months % 12
  return m ? `${y} y ${m} m` : `${y} y`
}

export function daysInMilk(lastCalving?: string) {
  if (!lastCalving) return undefined
  return Math.floor((TODAY.getTime() - new Date(lastCalving).getTime()) / 86400000)
}

export function fmtDate(iso?: string) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

export const inr = (n?: number) => (n === undefined || Number.isNaN(n) ? '—' : `₹${n.toLocaleString('en-IN')}`)

export const toneClass: Record<Animal['flags'][number]['tone'], string> = {
  green: 'bg-brand-soft text-brand',
  red: 'bg-danger-soft text-danger-text',
  amber: 'bg-warn-soft text-warn-ink',
  blue: 'bg-info-soft text-info-text',
}

export const SPECIES = ['Cow', 'Buffalo', 'Goat', 'Sheep', 'Pig', 'Poultry'] as const
export const CATEGORIES = ['Milking', 'Dry', 'Heifer', 'Calf', 'Bull'] as const
