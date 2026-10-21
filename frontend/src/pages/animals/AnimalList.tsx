import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { BottomNav, Chips, Footer, LogoMark, Screen, Sheet } from '../../components/ui'
import { Icon } from '../../components/icons'
import AccountButton from '../../components/AccountButton'
import { ageText, CATEGORIES, daysInMilk, SPECIES, toneClass, useStore } from '../../data/store'
import type { Animal, AnimalStatus, Category, Species } from '../../data/types'
import { breedInitials } from './shared'

const STATUSES: AnimalStatus[] = ['Active', 'Sold', 'Dead', 'Transferred']
const ATTENTION = ['Pregnant', 'In milk withdrawal', 'High SCC', 'Alerts due'] as const
type Attention = (typeof ATTENTION)[number]
const STAGES = ['Early 0–100 d', 'Mid 100–200 d', 'Late 200+ d'] as const
type Stage = (typeof STAGES)[number]
const SORTS = ['Tag A–Z', 'Newest added', 'Oldest first', 'Milk yield'] as const
type Sort = (typeof SORTS)[number]

interface Filters {
  species: Species[]
  category: Category[]
  status: AnimalStatus[]
  attention: Attention[]
  stage: Stage[]
  group: string // '' all, 'none', or group id
  sort: Sort
}

const DEFAULT: Filters = { species: [], category: [], status: ['Active'], attention: [], stage: [], group: '', sort: 'Tag A–Z' }

const toggle = <T,>(list: T[], v: T) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v])

function matchAttention(a: Animal, at: Attention) {
  const text = a.flags.map((f) => f.text.toLowerCase()).join(' | ')
  if (at === 'Pregnant') return text.includes('pregnant')
  if (at === 'In milk withdrawal') return text.includes('withdrawal')
  if (at === 'High SCC') return text.includes('scc')
  return a.flags.length > 0
}

function matchStage(a: Animal, s: Stage) {
  const d = daysInMilk(a.lastCalving)
  if (a.category !== 'Milking' || d === undefined) return false
  if (s === 'Early 0–100 d') return d <= 100
  if (s === 'Mid 100–200 d') return d > 100 && d <= 200
  return d > 200
}

function applyFilters(animals: Animal[], f: Filters) {
  const out = animals.filter((a) =>
    (!f.species.length || f.species.includes(a.species)) &&
    (!f.category.length || f.category.includes(a.category)) &&
    (!f.status.length || f.status.includes(a.status)) &&
    (!f.attention.length || f.attention.some((x) => matchAttention(a, x))) &&
    (!f.stage.length || f.stage.some((x) => matchStage(a, x))) &&
    (f.group === '' || (f.group === 'none' ? !a.groupId : a.groupId === f.group)),
  )
  const byTag = (x: Animal, y: Animal) => x.tag.localeCompare(y.tag, undefined, { numeric: true })
  if (f.sort === 'Tag A–Z') out.sort(byTag)
  if (f.sort === 'Oldest first') out.sort((x, y) => (x.dob ?? '9').localeCompare(y.dob ?? '9'))
  if (f.sort === 'Newest added') out.sort((x, y) => byTag(y, x))
  if (f.sort === 'Milk yield') out.sort((x, y) => (y.milkYesterday ?? -1) - (x.milkYesterday ?? -1))
  return out
}

function isFiltered(f: Filters) {
  return JSON.stringify(f) !== JSON.stringify(DEFAULT)
}

function FiltersSheet({ initial, onApply, onClose }: { initial: Filters; onApply: (f: Filters) => void; onClose: () => void }) {
  const { animals, groups } = useStore()
  const [f, setF] = useState<Filters>(initial)
  const count = applyFilters(animals, f).length
  const h = 'mb-2 mt-4 text-sm font-bold'
  const sel = 'mt-1.5 block h-[46px] w-full rounded-[10px] border border-line bg-white px-2.5 text-[15px]'
  return (
    <Sheet label="Filters" onClose={onClose} tall>
      <div className="flex items-center px-4 pb-1 pt-2">
        <span className="mr-2.5"><LogoMark /></span>
        <h2 className="m-0 flex-1 text-xl font-bold">Filters</h2>
        <button type="button" onClick={() => setF({ ...DEFAULT, status: [] })} className="h-11 px-2.5 text-[15px] font-semibold text-brand">Clear all</button>
      </div>
      <div className="flex-1 overflow-y-auto px-4 pb-2">
        <div className={h}>Species</div>
        <Chips label="Species" options={SPECIES.map((s) => ({ value: s, label: s }))} value={f.species} onToggle={(v) => setF({ ...f, species: toggle(f.species, v) })} />
        <div className={h}>Category</div>
        <Chips label="Category" options={CATEGORIES.map((s) => ({ value: s, label: s }))} value={f.category} onToggle={(v) => setF({ ...f, category: toggle(f.category, v) })} />
        <div className={h}>Status</div>
        <Chips label="Status" options={STATUSES.map((s) => ({ value: s, label: s }))} value={f.status} onToggle={(v) => setF({ ...f, status: toggle(f.status, v) })} />
        <div className={h}>Needs attention</div>
        <Chips label="Needs attention" options={ATTENTION.map((s) => ({ value: s, label: s }))} value={f.attention} onToggle={(v) => setF({ ...f, attention: toggle(f.attention, v) })} />
        <div className={h}>Lactation stage</div>
        <Chips label="Lactation stage" options={STAGES.map((s) => ({ value: s, label: s }))} value={f.stage} onToggle={(v) => setF({ ...f, stage: toggle(f.stage, v) })} />
        <div className={h}>Group &amp; sort</div>
        <div className="grid grid-cols-2 gap-2.5">
          <label className="text-[13px] font-semibold text-ink-2">Group
            <select className={sel} value={f.group} onChange={(e) => setF({ ...f, group: e.target.value })}>
              <option value="">All groups</option>
              {groups.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
              <option value="none">No group</option>
            </select>
          </label>
          <label className="text-[13px] font-semibold text-ink-2">Sort by
            <select className={sel} value={f.sort} onChange={(e) => setF({ ...f, sort: e.target.value as Sort })}>
              {SORTS.map((s) => <option key={s}>{s}</option>)}
            </select>
          </label>
        </div>
      </div>
      <div className="border-t border-line-soft px-4 pb-2.5 pt-3.5">
        <button type="button" className="btn" onClick={() => onApply(f)}>Show {count} {count === 1 ? 'animal' : 'animals'}</button>
      </div>
      <Footer className="pt-0" />
    </Sheet>
  )
}

export default function AnimalList() {
  const { animals, groups } = useStore()
  const [q, setQ] = useState('')
  const [quick, setQuick] = useState<Category | 'All'>('All')
  const [filters, setFilters] = useState<Filters>(DEFAULT)
  const [open, setOpen] = useState(false)

  const active = animals.filter((a) => a.status === 'Active')
  const countOf = (c: Category) => active.filter((a) => a.category === c).length
  const quickOpts: { value: Category | 'All'; label: string }[] = [
    { value: 'All', label: `All ${active.length}` },
    { value: 'Milking', label: `Milking ${countOf('Milking')}` },
    { value: 'Dry', label: `Dry ${countOf('Dry')}` },
    { value: 'Heifer', label: `Heifers ${countOf('Heifer')}` },
    { value: 'Calf', label: `Calves ${countOf('Calf')}` },
  ]

  const list = useMemo(() => {
    const term = q.trim().toLowerCase()
    return applyFilters(animals, filters).filter((a) =>
      (quick === 'All' || a.category === quick) &&
      (!term || [a.tag, a.name, a.breed].some((v) => v?.toLowerCase().includes(term))),
    )
  }, [animals, filters, quick, q])

  const filtered = isFiltered(filters)

  return (
    <Screen>
      <div className="flex items-center gap-2 px-4 pb-2.5 pt-3.5">
        <span className="mr-0.5"><LogoMark /></span>
        <h1 className="m-0 flex-1 text-2xl font-bold">Herd</h1>
        <Link to="/animals/import" aria-label="Import or export" className="flex h-11 w-11 items-center justify-center rounded-xl"><Icon name="download" /></Link>
        <AccountButton />
      </div>
      <div className="mx-4 grid grid-cols-2 rounded-xl bg-track p-1">
        <Link to="/animals" aria-current="page" className="flex h-[38px] items-center justify-center rounded-[9px] bg-white text-sm font-semibold text-ink no-underline shadow-sm">Animals · {active.length}</Link>
        <Link to="/groups" className="flex h-[38px] items-center justify-center rounded-[9px] text-sm font-semibold text-ink-2 no-underline">Groups · {groups.length}</Link>
      </div>

      <div className="flex gap-2 px-4 pt-3">
        <label className="flex h-[46px] flex-1 items-center gap-2 rounded-xl border border-line bg-white px-3">
          <Icon name="search" size={18} color="#56655B" />
          <input aria-label="Search animals" placeholder="Tag, name, breed…" value={q} onChange={(e) => setQ(e.target.value)} className="min-w-0 flex-1 border-0 bg-transparent text-[15px] outline-none" />
        </label>
        <Link to="/animals/scan" aria-label="Scan ear tag" className="flex h-[46px] w-[46px] items-center justify-center rounded-xl border border-line bg-white"><Icon name="scan" /></Link>
        <button type="button" onClick={() => setOpen(true)} aria-label={filtered ? 'Filters, active' : 'Filters'} className="relative flex h-[46px] w-[46px] items-center justify-center rounded-xl border border-line bg-white">
          <Icon name="filter" />
          {filtered && <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-[#C2410C]" />}
        </button>
      </div>

      <div className="px-4 py-3">
        <Chips wrap={false} label="Category" options={quickOpts} value={[quick]} onToggle={(v) => setQuick(v)} />
      </div>

      <div className="flex flex-1 flex-col gap-2.5 px-4 pb-24">
        {list.length === 0 && (
          <div className="rounded-[14px] bg-white p-6 text-center">
            <div className="text-base font-bold">No animals match</div>
            <p className="m-0 mt-1 text-sm text-ink-3">Try a different search or clear the filters.</p>
            <button type="button" onClick={() => { setQ(''); setQuick('All'); setFilters(DEFAULT) }} className="mt-3 h-11 px-3 font-semibold text-brand">Reset</button>
          </div>
        )}
        {list.map((a) => {
          const dim = a.category === 'Milking' ? daysInMilk(a.lastCalving) : undefined
          const meta = [a.species, a.breed, a.category === 'Calf' ? (a.sex === 'Female' ? '♀' : '♂') : '', ageText(a.dob), a.category, dim !== undefined ? `DIM ${dim}` : ''].filter(Boolean).join(' · ')
          return (
            <Link key={a.id} to={`/animals/${a.id}`} className="flex gap-3 rounded-[14px] bg-white p-3 text-ink no-underline hover:text-ink">
              <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-brand-soft text-[13px] font-bold text-brand">{breedInitials(a.breed)}</div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2"><span className="text-base font-bold">{a.tag}</span><span className="truncate text-ink-2">{a.name ?? '—'}</span></div>
                <div className="mt-0.5 text-[13px] text-ink-3">{meta}</div>
                {(a.flags.length > 0 || a.status !== 'Active') && (
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    {a.status !== 'Active' && <span className="badge bg-track text-ink-2">{a.status}</span>}
                    {a.flags.map((f) => <span key={f.text} className={`badge ${toneClass[f.tone]}`}>{f.text}</span>)}
                  </div>
                )}
              </div>
              {a.milkYesterday !== undefined && a.status === 'Active' && (
                <div className="text-right text-[13px] text-ink-3"><div className="text-base font-bold text-ink">{a.milkYesterday} L</div>yesterday</div>
              )}
            </Link>
          )
        })}
      </div>

      <div className="pointer-events-none sticky bottom-[112px] z-10 -mt-14 flex justify-end px-4">
        <Link to="/animals/new" className="pointer-events-auto flex h-[52px] items-center gap-2 rounded-full bg-brand px-5 font-semibold text-white no-underline shadow-[0_6px_14px_rgba(31,92,58,.3)] hover:text-white">
          <Icon name="plus" size={20} color="#fff" strokeWidth={2.4} />Add animal
        </Link>
      </div>
      <div className="sticky bottom-[72px] z-10 bg-ground"><Footer className="py-2" /></div>
      <BottomNav active="herd" />
      {open && <FiltersSheet initial={filters} onClose={() => setOpen(false)} onApply={(f) => { setFilters(f); setOpen(false) }} />}
    </Screen>
  )
}
