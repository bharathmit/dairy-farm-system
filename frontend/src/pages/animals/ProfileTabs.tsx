import { Link } from 'react-router-dom'
import { GradeBadge, LoadState, useRecords } from '../../components/records'
import { addDays, L, shortDate, todayIso, useLoad } from '../../data/records'
import { RecordRow } from '../health/HealthHome'

/** Animal profile → Health tab. */
export function HealthTab({ animalId }: { animalId: string }) {
  const { rec } = useRecords()
  const res = useLoad(() => rec.healthRecords({ animalId, size: 50 }), [rec, animalId])
  const items = res.data?.items ?? []
  return (
    <div className="mx-4 mb-3">
      <div className="mb-2 flex items-baseline justify-between"><b className="text-[15px]">Health records</b><Link to={`/health/new?animal=${animalId}&back=/animals/${animalId}`} className="text-sm font-semibold">+ Add</Link></div>
      <LoadState loading={res.loading} error={res.error} onRetry={res.reload} empty={!res.data} />
      {res.data && (
        <div className="overflow-hidden rounded-[14px] bg-white">
          {items.length === 0 && <div className="px-3.5 py-4 text-sm text-ink-3">No health records yet.</div>}
          {items.map((r, i) => <RecordRow key={r.id} r={r} first={i === 0} />)}
        </div>
      )}
    </div>
  )
}

/** Animal profile → Milk tab: last 30 days. */
export function MilkTab({ animalId, canLog }: { animalId: string; canLog: boolean }) {
  const { rec } = useRecords()
  const to = todayIso()
  const res = useLoad(() => rec.cowHistory(animalId, addDays(to, -29), to), [rec, animalId, to])
  const days = res.data ?? []
  const totals = days.filter((d) => d.total != null)
  const avg = totals.length ? totals.reduce((s, d) => s + (d.total ?? 0), 0) / totals.length : undefined
  return (
    <div className="mx-4 mb-3">
      <div className="mb-2 flex items-baseline justify-between"><b className="text-[15px]">Milk · 30 days{avg !== undefined ? ` · avg ${L(avg)} L/day` : ''}</b>{canLog && <Link to={`/milk/cow?animal=${animalId}`} className="text-sm font-semibold">+ Log</Link>}</div>
      <LoadState loading={res.loading} error={res.error} onRetry={res.reload} empty={!res.data} />
      {res.data && (
        <div className="overflow-hidden rounded-[14px] bg-white">
          <div className="grid min-h-10 grid-cols-[1fr_.7fr_.7fr_.8fr_.9fr_.5fr] items-center px-3 text-xs font-bold text-ink-3"><span>Date</span><span>AM</span><span>PM</span><span>Total</span><span>Expected</span><span /></div>
          {days.length === 0 && <div className="border-t border-line-faint px-3 py-4 text-sm text-ink-3">No milk logged in the last 30 days.</div>}
          {days.map((d) => (
            <Link key={d.date} to={`/milk/cow?animal=${animalId}&date=${d.date}`} className="grid min-h-12 grid-cols-[1fr_.7fr_.7fr_.8fr_.9fr_.5fr] items-center border-t border-line-faint px-3 text-sm text-ink no-underline hover:text-ink">
              <span>{shortDate(d.date)}{d.withheld && <span className="ml-1 text-xs text-danger-text">✕</span>}</span><span>{L(d.am)}</span><span>{L(d.pm)}</span><b>{L(d.total)}</b><span className="text-ink-3">{L(d.expectedLitres)}</span><span><GradeBadge grade={d.grade} /></span>
            </Link>
          ))}
        </div>
      )}
      {days.some((d) => d.withheld) && <div className="mt-1.5 text-xs text-ink-3">✕ milk discarded (withdrawal)</div>}
    </div>
  )
}
