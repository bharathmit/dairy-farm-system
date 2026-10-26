import type { ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { AnimalSelect, LoadState } from '../../components/records'
import { OwnerOnlyScreen } from '../../components/money'
import { useAlerts } from '../../components/alerts'
import type { CowCard } from '../../data/alerts'
import { rupees } from '../../data/money'
import { L, useLoad, weekdayShort } from '../../data/records'
import type { Animal } from '../../data/types'

const A_COL = '#1F5C3A'
const B_COL = '#C2410C'
const isFemale = (a: Animal) => a.sex === 'Female' && a.category !== 'Calf'

function Chart({ a, b, days }: { a: CowCard; b: CowCard; days: string[] }) {
  const W = 326; const H = 130; const P = 8
  const vals = [...a.litres7d, ...b.litres7d].filter((v): v is number => v != null)
  const max = Math.max(1, ...vals) * 1.1
  const x = (i: number) => P + (i * (W - 2 * P)) / Math.max(1, days.length - 1)
  const y = (v: number) => H - P - (v / max) * (H - 2 * P)
  const line = (s: (number | null)[]) => s.map((v, i) => (v == null ? null : `${x(i)},${y(v)}`)).filter(Boolean).join(' ')
  return (
    <div>
      <svg width="100%" viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`Litres per day over ${days.length} days: ${a.animal.tag} average ${L(a.avgLitres)}, ${b.animal.tag} average ${L(b.avgLitres)}`}>
        <line x1="0" y1={H - P} x2={W} y2={H - P} stroke="#E4E6DF" />
        <polyline points={line(a.litres7d)} fill="none" stroke={A_COL} strokeWidth="2.5" />
        <polyline points={line(b.litres7d)} fill="none" stroke={B_COL} strokeWidth="2.5" strokeDasharray="6 4" />
      </svg>
      <div className="flex justify-between text-xs text-ink-3">{days.map((d) => <span key={d}>{weekdayShort(d)}</span>)}</div>
      <div className="mt-2 flex gap-4 text-[13px]">
        <span className="flex items-center gap-1.5"><span className="inline-block h-[3px] w-5 rounded" style={{ background: A_COL }} />{a.animal.tag} {a.animal.name}</span>
        <span className="flex items-center gap-1.5"><span className="inline-block h-[3px] w-5 rounded" style={{ background: `repeating-linear-gradient(90deg, ${B_COL} 0 6px, transparent 6px 10px)` }} />{b.animal.tag} {b.animal.name}</span>
      </div>
    </div>
  )
}

/** Two cows side by side: 7-day yield, stage, quality, health, breeding and profit. */
export default function CompareCows() {
  const { al, isOwner } = useAlerts()
  const [params, setParams] = useSearchParams()
  const aId = params.get('a') ?? ''
  const bId = params.get('b') ?? ''
  const cmp = useLoad(isOwner && aId && bId && aId !== bId ? () => al.compare(aId, bId) : null, [al, isOwner, aId, bId])
  if (!isOwner) return <OwnerOnlyScreen title="Compare cows" />
  const set = (k: 'a' | 'b', v: string) => { const q = new URLSearchParams(params); if (v) q.set(k, v); else q.delete(k); setParams(q, { replace: true }) }
  const d = cmp.data

  const rows: [string, (c: CowCard) => ReactNode][] = [
    ['Avg L/day', (c) => <b>{L(c.avgLitres)}</b>],
    ['7-day trend', (c) => (c.trendPct == null ? '—' : <span className={c.trendPct < -10 ? 'font-semibold text-danger' : ''}>{c.trendPct > 0 ? '+' : c.trendPct < 0 ? '−' : ''}{Math.abs(Math.round(c.trendPct))} %{Math.abs(c.trendPct) < 5 ? ' · steady' : ''}</span>)],
    ['Days in milk', (c) => c.daysInMilk ?? '—'],
    ['Lactation stage', (c) => c.stage ?? '—'],
    ['Status', (c) => c.status ?? '—'],
    ['Breed', (c) => c.breed ?? '—'],
    ['Fat % (30 d)', (c) => (c.fat30d == null ? '—' : c.fat30d.toFixed(1))],
    ['Health records', (c) => `${c.health.count}${c.health.cost != null ? ` · ${rupees(c.health.cost)}` : ''}`],
    ['Active issue', (c) => (c.activeIssue ? <span className="font-semibold text-danger-text">{c.activeIssue}</span> : '—')],
    ['Breeding', (c) => c.breeding ?? '—'],
    ['Lifetime P&L', (c) => (c.lifetimeNet == null ? '—' : <b className={c.lifetimeNet < 0 ? 'text-danger' : 'text-brand'}>{rupees(c.lifetimeNet, true)}</b>)],
  ]

  return (
    <Screen wide>
      <TopBar title="Compare cows" back="/reports" />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="cc-a">Cow A</label><AnimalSelect id="cc-a" value={aId} onChange={(v) => set('a', v)} filter={isFemale} placeholder="Choose" /></div>
          <div><label className="lab" htmlFor="cc-b">Cow B</label><AnimalSelect id="cc-b" value={bId} onChange={(v) => set('b', v)} filter={isFemale} placeholder="Choose" /></div>
        </div>
        {(!aId || !bId) && <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">Choose two cows to compare.</div>}
        {aId && aId === bId && <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">Choose two different cows.</div>}
        <LoadState loading={cmp.loading} error={cmp.error} onRetry={cmp.reload} empty={!d} />
        {d && (
          <>
            <div className="rounded-[14px] bg-white p-3.5">
              <b className="text-[15px]">Litres per day · {d.days.length} days</b>
              <div className="mt-2"><Chart a={d.a} b={d.b} days={d.days} /></div>
            </div>
            <div className="overflow-hidden rounded-[14px] bg-white">
              <div className="grid grid-cols-[1.1fr_1fr_1fr] gap-2 bg-ground px-3.5 py-2.5 text-[13px] font-bold">
                <span />
                <span style={{ color: A_COL }}>{d.a.animal.tag} {d.a.animal.name}</span>
                <span style={{ color: B_COL }}>{d.b.animal.tag} {d.b.animal.name}</span>
              </div>
              {rows.map(([label, f]) => (
                <div key={label} className="grid min-h-11 grid-cols-[1.1fr_1fr_1fr] items-center gap-2 border-t border-line-faint px-3.5 py-1.5 text-sm">
                  <span className="text-ink-3">{label}</span><span>{f(d.a)}</span><span>{f(d.b)}</span>
                </div>
              ))}
            </div>
            <div className="grid grid-cols-2 gap-2.5">
              <Link to={`/animals/${d.a.animal.id}`} className="btn2 h-12 text-[15px]">Open {d.a.animal.name || d.a.animal.tag}</Link>
              <Link to={`/animals/${d.b.animal.id}`} className="btn2 h-12 text-[15px]">Open {d.b.animal.name || d.b.animal.tag}</Link>
            </div>
          </>
        )}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
