import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Footer, Screen, Segmented, TopBar } from '../../components/ui'
import { LoadState, useRecords } from '../../components/records'
import { ageText, useStore } from '../../data/store'
import { L, METHODS, shortDate, useLoad, type Measurement } from '../../data/records'
import { errorMessage } from '../../lib/api'

type Metric = 'weightKg' | 'heightCm' | 'bcs'
const METRICS: { value: Metric; label: string; unit: string }[] = [
  { value: 'weightKg', label: 'Weight', unit: 'kg' }, { value: 'heightCm', label: 'Height', unit: 'cm' }, { value: 'bcs', label: 'BCS', unit: '' },
]

function Chart({ items, metric, unit }: { items: Measurement[]; metric: Metric; unit: string }) {
  const pts = items.filter((m) => m[metric] != null).slice().reverse()
  if (pts.length < 2) return <div className="py-6 text-center text-sm text-ink-3">Add at least two records to see a trend.</div>
  const vals = pts.map((m) => m[metric] as number)
  const min = Math.min(...vals), max = Math.max(...vals)
  const span = max - min || 1
  const t0 = new Date(pts[0].date).getTime(), t1 = new Date(pts[pts.length - 1].date).getTime() || t0 + 1
  const w = 326, h = 150
  const x = (d: string) => 10 + ((new Date(d).getTime() - t0) / Math.max(1, t1 - t0)) * (w - 20)
  const y = (v: number) => 135 - ((v - min) / span) * 110
  const last = pts[pts.length - 1]
  return (
    <>
      <svg width="100%" viewBox={`0 0 ${w} ${h}`} role="img" aria-label={`${metric === 'bcs' ? 'BCS' : metric === 'weightKg' ? 'Weight' : 'Height'} from ${vals[0]} to ${vals[vals.length - 1]} ${unit}`} className="mt-1.5 block">
        <line x1="0" y1="146" x2={w} y2="146" stroke="#E4E6DF" />
        <polyline points={pts.map((m) => `${x(m.date)},${y(m[metric] as number)}`).join(' ')} fill="none" stroke="#1F5C3A" strokeWidth="2.5" />
        <circle cx={x(last.date)} cy={y(last[metric] as number)} r="4" fill="#1F5C3A" />
        <text x="4" y="14" fontSize="11" fill="#56655B" fontFamily="Figtree, sans-serif">{max}{unit && ` ${unit}`}</text>
        <text x="4" y="132" fontSize="11" fill="#56655B" fontFamily="Figtree, sans-serif">{min}{unit && ` ${unit}`}</text>
      </svg>
      <div className="flex justify-between text-xs text-ink-3"><span>{shortDate(pts[0].date)}</span><span>{shortDate(last.date)}</span></div>
    </>
  )
}

export default function WeightTrend() {
  const { id = '' } = useParams()
  const { rec, isOwner } = useRecords()
  const { animals, notify } = useStore()
  const g = useLoad(() => rec.growth(id), [rec, id])
  const [metric, setMetric] = useState<Metric>('weightKg')
  const a = animals.find((x) => x.id === id)
  const m = METRICS.find((x) => x.value === metric)!
  const items = g.data?.items ?? []
  const latest = g.data?.latest

  const remove = async (item: Measurement) => {
    try { await rec.deleteMeasurement(item.id); notify('Record deleted'); void g.reload() } catch (e) { notify(errorMessage(e)) }
  }

  const title = g.data ? `${g.data.animal.tag}${g.data.animal.name ? ` ${g.data.animal.name}` : ''}` : a?.tag ?? 'Growth'
  return (
    <Screen>
      <TopBar title={`Growth · ${title}`} back={`/animals/${id}`} />
      <div className="px-4 pb-4">
        <div className="-mt-1 mb-2 text-[13px] text-ink-3">{[a?.category, a?.breed, ageText(a?.dob)].filter(Boolean).join(' · ')}</div>
        <Segmented label="Measure" value={metric} onChange={setMetric} options={METRICS} />
        <LoadState loading={g.loading} error={g.error} onRetry={g.reload} empty={!g.data} />
        {g.data && (
          <>
            <div className="mt-3 grid grid-cols-3 gap-2.5">
              <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Now</div><div className="mt-0.5 text-[19px] font-bold">{latest?.[metric] != null ? `${metric === 'bcs' ? latest[metric] : L(latest[metric] as number, 0)}${m.unit && ` ${m.unit}`}` : '—'}</div></div>
              <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Gain 90 d</div><div className="mt-0.5 text-[19px] font-bold">{g.data.adg90dKg != null ? `${g.data.adg90dKg.toFixed(2)} kg/d` : '—'}</div></div>
              <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Records</div><div className="mt-0.5 text-[19px] font-bold">{items.length}</div></div>
            </div>
            <div className="mt-3 rounded-[14px] bg-white p-3.5"><div className="text-[13px] text-ink-3">{m.label}{m.unit && ` (${m.unit})`}</div><Chart items={items} metric={metric} unit={m.unit} /></div>

            <div className="mb-2 mt-4 flex items-baseline justify-between"><b className="text-[15px]">History</b><Link to={`/weight/new?animal=${id}`} className="text-sm font-semibold">+ Add</Link></div>
            <div className="overflow-hidden rounded-[14px] bg-white">
              <div className="grid min-h-10 grid-cols-[1fr_1fr_.8fr_.6fr_.9fr] items-center px-3 text-xs font-bold text-ink-3"><span>Date</span><span>Weight</span><span>Height</span><span>BCS</span><span>Method</span></div>
              {items.length === 0 && <div className="border-t border-line-faint px-3 py-4 text-sm text-ink-3">No records yet. <Link to={`/weight/new?animal=${id}`} className="font-semibold">Add the first one</Link>.</div>}
              {items.map((r) => (
                <div key={r.id} className="border-t border-line-faint">
                  <Link to={`/weight/new?animal=${id}&id=${r.id}`} className="grid min-h-[50px] grid-cols-[1fr_1fr_.8fr_.6fr_.9fr] items-center px-3 text-sm text-ink no-underline hover:text-ink">
                    <span>{shortDate(r.date)}</span><b>{r.weightKg != null ? `${L(r.weightKg, 0)} kg` : '—'}</b><span>{r.heightCm ?? '—'}</span><span>{r.bcs ?? '—'}</span><span>{METHODS.find((x) => x.value === r.method)?.label}</span>
                  </Link>
                  {(r.adgKg != null || isOwner) && (
                    <div className="-mt-2 flex items-center justify-between px-3 pb-1.5 text-xs text-ink-3">
                      <span>{r.adgKg != null ? `${r.gainKg != null && r.gainKg >= 0 ? '+' : ''}${L(r.gainKg)} kg · ${r.adgKg.toFixed(2)} kg/day` : ''}</span>
                      {isOwner && <button type="button" onClick={() => remove(r)} className="h-8 border-0 bg-transparent text-xs font-semibold text-danger">Delete</button>}
                    </div>
                  )}
                </div>
              ))}
            </div>
            <div className="mt-2.5 text-xs leading-normal text-ink-3">Weigh calves and heifers every week to track growth.</div>
          </>
        )}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
