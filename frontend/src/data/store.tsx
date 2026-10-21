/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { seedAnimals, seedFarms, seedGroups, seedMembers, seedSettings } from './seed'
import type { Animal, Farm, Group, Member, Settings, User } from './types'

// In-memory store for the clickable UI. Everything resets on page reload.
// Each action maps to a future REST call (see docs/api.md).

interface Store {
  user: User | null
  farm: Farm | null
  farms: Farm[]
  animals: Animal[]
  groups: Group[]
  members: Member[]
  settings: Settings
  toast: string | null
  signIn: (email: string) => void
  signUp: (name: string, email: string, farmName: string) => void
  signOut: () => void
  chooseFarm: (id: string) => void
  addFarm: (name: string) => void
  updateFarm: (patch: Partial<Farm>) => void
  saveAnimal: (a: Animal) => void
  deleteAnimal: (id: string) => void
  importAnimals: (rows: Animal[]) => void
  saveGroup: (g: Group, memberIds: string[]) => void
  deleteGroup: (id: string) => void
  saveMember: (m: Member) => void
  updateSettings: (patch: Partial<Settings>) => void
  notify: (text: string) => void
}

const Ctx = createContext<Store | null>(null)

let seq = 100
export const newId = (p: string) => `${p}${++seq}`

export function StoreProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [farms, setFarms] = useState<Farm[]>(seedFarms)
  const [farmId, setFarmId] = useState<string | null>(null)
  const [animals, setAnimals] = useState<Animal[]>(seedAnimals)
  const [groups, setGroups] = useState<Group[]>(seedGroups)
  const [members, setMembers] = useState<Member[]>(seedMembers)
  const [settings, setSettings] = useState<Settings>(seedSettings)
  const [toast, setToast] = useState<string | null>(null)

  const notify = useCallback((text: string) => {
    setToast(text)
    window.setTimeout(() => setToast((t) => (t === text ? null : t)), 2200)
  }, [])

  const store = useMemo<Store>(() => ({
    user,
    farm: farms.find((f) => f.id === farmId) ?? null,
    farms,
    animals,
    groups,
    members,
    settings,
    toast,
    notify,
    signIn: (email) => setUser({ name: email.split('@')[0] || 'Bharath', email }),
    signUp: (name, email, farmName) => {
      setUser({ name, email })
      const f: Farm = { id: newId('f'), name: farmName, role: 'Owner', animals: 0, initials: farmName.slice(0, 2).toUpperCase() }
      setFarms((fs) => [f, ...fs])
    },
    signOut: () => { setUser(null); setFarmId(null) },
    chooseFarm: (id) => setFarmId(id),
    addFarm: (name) => setFarms((fs) => [...fs, { id: newId('f'), name, role: 'Owner', animals: 0, initials: name.slice(0, 2).toUpperCase() }]),
    updateFarm: (patch) => setFarms((fs) => fs.map((f) => (f.id === farmId ? { ...f, ...patch } : f))),
    saveAnimal: (a) => setAnimals((as) => (as.some((x) => x.id === a.id) ? as.map((x) => (x.id === a.id ? a : x)) : [a, ...as])),
    deleteAnimal: (id) => setAnimals((as) => as.filter((x) => x.id !== id)),
    importAnimals: (rows) => setAnimals((as) => [...rows, ...as]),
    saveGroup: (g, memberIds) => {
      setGroups((gs) => (gs.some((x) => x.id === g.id) ? gs.map((x) => (x.id === g.id ? g : x)) : [...gs, g]))
      setAnimals((as) => as.map((a) => {
        if (memberIds.includes(a.id)) return { ...a, groupId: g.id }
        if (a.groupId === g.id) return { ...a, groupId: undefined }
        return a
      }))
    },
    deleteGroup: (id) => {
      setGroups((gs) => gs.filter((g) => g.id !== id))
      setAnimals((as) => as.map((a) => (a.groupId === id ? { ...a, groupId: undefined } : a)))
    },
    saveMember: (m) => setMembers((ms) => (ms.some((x) => x.id === m.id) ? ms.map((x) => (x.id === m.id ? m : x)) : [...ms, m])),
    updateSettings: (patch) => setSettings((s) => ({ ...s, ...patch })),
  }), [user, farms, farmId, animals, groups, members, settings, toast, notify])

  return <Ctx.Provider value={store}>{children}</Ctx.Provider>
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
