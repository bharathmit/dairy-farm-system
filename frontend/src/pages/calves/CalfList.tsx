import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { BottomNav, Footer, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { useBreeding } from '../../components/breeding'
import { L, useLoad } from '../../data/records'
import { TONE_CLASS, type CalfItem } from '../../data/breeding'

type Sex = 'FEMALE' | 'MALE' | 'ALL'
const BANDS = [
  { key: 'm1', label: '0–1 m', from: 0, to: 30 },
  { key: 'm2', label: '1–2 m', from: 31, to: 60 },
  { key: 'm3', label: '2–3 m', from: 61, to: 90 },
  { key: 'm4', label: '3 m +', from: 91, to: undefined },
] as const
type Band = (typeof BANDS)[number]['key']

/** Query string for the group feeding sheet with the same filter. */
function filterQuery(sex: Sex, bands: Band[]) {
  const sel = BANDS.filter((b) => bands.includes(b.key))
  const min = sel.length ? Math.min(...sel.map((b) => b.from)) : undefined
  const max = sel.some((b) => b.to === undefined) ? undefined : sel.length ? Math.max(...sel.map((b) => b.to as number)) : undefined
  return { sex: sex === 'ALL' ? undefined : sex, minDays: min, maxDays: max }
}

function CalfCard({ c }: { c: CalfItem }) {
  const pct = c.weightTargetKg && c.weightKg ? Math.round((c.weightKg / c.weightTargetKg - 1) * 100) : null
  return (
    <div className="rounded-[14px] bg-white p-3.5">
      <div className="flex items-center gap-2">
        <Link to={`/animals/${c.animal.id}`} className="text-base font-bold underline underline-offset-[3px]">{c.animal.tag}{c.animal.name ? ` ${c.animal.name}` : ''}</Link>
        <span className="flex-1 truncate text-ink-2">{c.sex === 'FEMALE' ? '♀' : '♂'}{c.dam ? ` · dam ${c.dam.name || c.dam.tag}` : ''}{c.breed ? ` · ${c.breed}` : ''}</span>
        <span className="font-bold">{c.ageDays} d</span>
      </div>
      <Link to={`/calves/feeding?animal=${c.animal.id}`} className="mt-2.5 flex flex-col gap-2.5 text-ink no-underline hover:text-ink">
        {c.badges.length > 0 && <div className="flex flex-wrap gap-1.5">{c.badges.map((b) => <span key={b.text} className={`badge ${TONE_CLASS[b.tone]}`}>{b.text}</span>)}</div>}
        <div className="grid grid-cols-3 gap-2 text-[13px] text-ink-3">
          <span>Milk<b className="block text-[15px] text-ink">{L(c.milkLitresPerDay)} L/d</b></span>
          <span>Starter<b className="block text-[15px] text-ink">{L(c.starterKgPerDay)} kg/d</b></span>
          <span>Weight<b className="block text-[15px] text-ink">{c.weightKg != null ? `${c.weightKg} kg` : '—'}</b></span>
        </div>
        {pct !== null && (
          <div>
            <div className="mb-1 flex justify-between text-xs font-semibold text-ink-3"><span>vs target {c.weightTargetKg} kg</span><span className={pct < 0 ? 'text-warn-ink' : 'text-brand'}>{pct > 0 ? '+' : ''}{pct} %</span></div>
            <div className="h-1.5 rounded bg-line-faint"><div className={`h-1.5 rounded ${pct < 0 ? 'bg-warn-ink' : 'bg-brand'}`} style={{ width: `${Math.min(100, Math.max(5, 100 + pct))}%` }} /></div>
          </div>
        )}
        {c.maleSell && <div className="text-xs text-ink-3">{c.maleSell.daysLeft} days left in sell window ({c.maleSell.fromDay}–{c.maleSell.toDay} d)</div>}
        <div className="flex justify-end text-[13px] font-semibold text-brand">Log feeding ›</div>
      </Link>
    </div>
  )
}

export default function CalfList() {
  const { br } = useBreeding()
  const [params, setParams] = useSearchParams()
  const sex = (params.get('sex') as Sex) || 'FEMALE'
  const bands = (params.get('age') ?? 'm1,m2,m3').split(',').filter(Boolean) as Band[]
  const fq = filterQuery(sex, bands)
  const res = useLoad(() => br.calves(fq), [br, fq.sex, fq.minDays, fq.maxDays])
  const set = (p: Record<string, string>) => setParams((cur) => { const n = new URLSearchParams(cur); Object.entries(p).forEach(([k, v]) => n.set(k, v)); return n }, { replace: true })
  const toggleBand = (k: Band) => set({ age: (bands.includes(k) ? bands.filter((b) => b !== k) : [...bands, k]).join(',') })
  const d = res.data
  // The API filters one age range; drop calves in an unticked month inside it.
  const calves = useMemo(() => (d?.calves ?? []).filter((c) => !bands.length || BANDS.some((b) => bands.includes(b.key) && c.ageDays >= b.from && (b.to === undefined || c.ageDays <= b.to))), [d, bands])
  const hidden = d ? d.counts.all - calves.length : 0
  const groupQ = new URLSearchParams(Object.entries(fq).filter(([, v]) => v !== undefined).map(([k, v]) => [k, String(v)])).toString()
  const who = `${sex === 'FEMALE' ? 'female' : sex === 'MALE' ? 'male' : ''} calves`.trim()

  return (
    <Screen wide>
      <TopBar title="Calves" back="/breeding" right={<Link to="/calves/feeding" className="flex h-10 items-center rounded-[10px] bg-brand px-3.5 text-sm font-semibold text-white no-underline hover:text-white">+ Feeding</Link>} />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <div className="grid grid-cols-3 gap-2.5">
          <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Shown</div><div className="mt-0.5 text-xl font-bold">{d ? calves.length : '—'}</div></div>
          <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Milk today</div><div className="mt-0.5 text-xl font-bold">{d ? `${L(d.milkTodayLitres ?? 0)} L` : '—'}</div></div>
          <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Need action</div><div className={`mt-0.5 text-xl font-bold ${d?.needAction ? 'text-danger' : ''}`}>{d ? d.needAction : '—'}</div></div>
        </div>
        <div>
          <div className="mb-1.5 text-xs font-bold text-ink-3">Sex</div>
          <div className="flex gap-2">
            {([['FEMALE', `Female${d ? ` ${d.counts.female}` : ''}`], ['MALE', `Male${d ? ` ${d.counts.male}` : ''}`], ['ALL', `All${d ? ` ${d.counts.all}` : ''}`]] as [Sex, string][]).map(([v, l]) => (
              <button key={v} type="button" aria-pressed={sex === v} onClick={() => set({ sex: v })} className={`chip ${sex === v ? 'chip-on' : ''}`}>{l}</button>
            ))}
          </div>
          <div className="mb-1.5 mt-2.5 text-xs font-bold text-ink-3">Age</div>
          <div className="flex gap-2 overflow-x-auto pb-1">
            {BANDS.map((b) => <button key={b.key} type="button" aria-pressed={bands.includes(b.key)} onClick={() => toggleBand(b.key)} className={`chip ${bands.includes(b.key) ? 'chip-on' : ''}`}>{b.label}</button>)}
          </div>
        </div>
        {calves.length > 0 && (
          <div className="flex items-center gap-2.5 rounded-xl bg-white px-3.5 py-2.5">
            <span className="flex-1 text-sm"><b>{calves.length} {who}</b> in this filter</span>
            <Link to={`/calves/feeding/group?${groupQ}`} className="flex h-10 items-center rounded-[10px] border border-brand px-3 text-sm font-semibold text-brand no-underline">Feed all {calves.length}</Link>
          </div>
        )}
        <LoadState loading={res.loading} error={res.error} onRetry={res.reload} empty={!d} />
        {calves.map((c) => <CalfCard key={c.animal.id} c={c} />)}
        {d && calves.length === 0 && <div className="rounded-[14px] bg-white px-4 py-5 text-center text-sm text-ink-3">No calves in this filter. Calves are added when you <Link to="/breeding/calving" className="font-semibold">record a calving</Link> or add an animal in category Calf.</div>}
        {d && hidden > 0 && <div className="text-center text-xs text-ink-3">{hidden} other calf{hidden === 1 ? '' : 'ves'} hidden by the filter · <button type="button" onClick={() => set({ sex: 'ALL', age: 'm1,m2,m3,m4' })} className="border-0 bg-transparent p-0 font-semibold text-brand">Show all</button></div>}
      </div>
      <Footer className="mt-auto" />
      <BottomNav active="more" />
    </Screen>
  )
}
