import { useMemo, useRef, useState, type PointerEvent } from 'react'
import { Link } from 'react-router-dom'
import { BottomNav, Footer, Screen, TopBar } from '../../components/ui'
import { GradeBadge, LoadState, useRecords } from '../../components/records'
import { addDays, dayLabel, L, pct, shortDate, todayIso, useLoad, weekdayShort, type Grade, type TrendDay } from '../../data/records'

const RANGES = [
  { key: '7', label: '7 days' }, { key: '30', label: '30 days' },
  { key: 'm', label: 'Month' }, { key: 'c', label: 'Custom' },
] as const
type RangeKey = (typeof RANGES)[number]['key']
const MAX_DAYS = 365
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

function Tile({ k, v, sub, change, muted }: { k: string; v: string; sub: string; change?: number | null; muted?: boolean }) {
  return (
    <div className="rounded-xl bg-white p-3">
      <div className="text-xs font-semibold text-ink-3">{k}</div>
      <div className={`mt-0.5 text-xl font-bold ${muted ? 'text-ink-3' : ''}`}>{v}</div>
      <div className="mt-1 text-xs leading-snug text-ink-3">{sub}{change !== undefined && change !== null && <><br /><b className={change < 0 ? 'text-danger' : 'text-brand'}>{pct(change)}</b></>}</div>
    </div>
  )
}

function GradeCounts({ g }: { g?: Partial<Record<Grade, number>> | null }) {
  return (
    <span className="flex gap-1.5">
      {(['A', 'B', 'C', 'D'] as const).map((k) => <span key={k} className={`badge font-bold ${k === 'A' ? 'bg-brand-soft text-brand' : k === 'B' ? 'bg-info-soft text-info-text' : k === 'C' ? 'bg-warn-soft text-warn-ink' : 'bg-danger-soft text-danger-text'}`}>{k} {g?.[k] ?? 0}</span>)}
    </span>
  )
}

const daysBetween = (a: string, b: string) => Math.round((Date.parse(b) - Date.parse(a)) / 86400000)

interface Point { label: string; tip: string; value: number }

/** Average litres per day for each month (days with milk only). */
function byMonth(days: TrendDay[]): Point[] {
  const m = new Map<string, { sum: number; n: number }>()
  for (const d of days) {
    const k = d.date.slice(0, 7)
    const e = m.get(k) ?? { sum: 0, n: 0 }
    if (d.totalLitres > 0) { e.sum += d.totalLitres; e.n++ }
    m.set(k, e)
  }
  return [...m.entries()].map(([k, e]) => {
    const name = `${MONTHS[Number(k.slice(5, 7)) - 1]} ${k.slice(2, 4)}`
    const avg = e.n ? e.sum / e.n : 0
    return { label: name, tip: `${name} · avg ${L(avg)} L/day`, value: avg }
  })
}

function YieldChart({ points, title }: { points: Point[]; title: string }) {
  const [hover, setHover] = useState<number | null>(null)
  const ref = useRef<SVGSVGElement>(null)
  const n = points.length
  if (!n) return null
  const w = 340, h = 180, left = 34, right = 8, top = 10, bottom = 150
  const rawMax = Math.max(1, ...points.map((p) => p.value))
  const stepY = rawMax <= 20 ? 5 : rawMax <= 100 ? 20 : rawMax <= 250 ? 50 : 100
  const max = Math.ceil(rawMax / stepY) * stepY
  const ticks = Array.from({ length: max / stepY + 1 }, (_, i) => i * stepY)
  const x = (i: number) => (n === 1 ? (left + w - right) / 2 : left + (i * (w - left - right)) / (n - 1))
  const y = (v: number) => bottom - (v / max) * (bottom - top)
  const line = points.map((p, i) => `${x(i)},${y(p.value)}`).join(' ')
  const avg = points.reduce((s, p) => s + p.value, 0) / n
  const every = Math.max(1, Math.ceil(n / 6))

  const pick = (e: PointerEvent<SVGSVGElement>) => {
    const r = ref.current?.getBoundingClientRect()
    if (!r) return
    const px = ((e.clientX - r.left) / r.width) * w
    const i = n === 1 ? 0 : Math.round(((px - left) / (w - left - right)) * (n - 1))
    setHover(Math.min(n - 1, Math.max(0, i)))
  }
  const hp = hover === null ? null : points[hover]

  return (
    <div className="mt-2.5 rounded-[14px] bg-white p-3.5">
      <div className="flex items-baseline justify-between"><b className="text-[15px]">{title}</b><span className="text-[13px] text-ink-3">avg {L(avg, 0)} L</span></div>
      <div className="mt-1 h-5 text-[13px] font-semibold text-brand" aria-live="polite">{hp ? `${hp.tip}` : <span className="font-normal text-ink-3">Tap or hover a point to see litres</span>}</div>
      <svg ref={ref} width="100%" viewBox={`0 0 ${w} ${h}`} role="img" aria-label={`${title}: ${n} points, average ${avg.toFixed(0)} litres`}
        className="block touch-none select-none" onPointerMove={pick} onPointerDown={pick} onPointerLeave={() => setHover(null)}>
        {ticks.map((t) => (
          <g key={t}>
            <line x1={left} y1={y(t)} x2={w - right} y2={y(t)} stroke={t === 0 ? '#CDD2C8' : '#EEF0EA'} />
            <text x={left - 5} y={y(t) + 3.5} textAnchor="end" fontSize="10" fill="#56655B">{t}</text>
          </g>
        ))}
        <text x={2} y={top - 1} fontSize="9" fill="#56655B">L</text>
        <polyline points={line} fill="none" stroke="#1F5C3A" strokeWidth="2" strokeLinejoin="round" />
        {n <= 40 && points.map((p, i) => <circle key={i} cx={x(i)} cy={y(p.value)} r="2.5" fill="#1F5C3A" />)}
        {points.map((p, i) => (i % every === 0 || i === n - 1) && (n - 1 - i >= every / 2 || i === n - 1)
          ? <text key={`l${i}`} x={x(i)} y={h - 14} textAnchor="middle" fontSize="10" fill="#56655B">{p.label}</text> : null)}
        {hp && hover !== null && (
          <g>
            <line x1={x(hover)} y1={top} x2={x(hover)} y2={bottom} stroke="#1F5C3A" strokeDasharray="3 3" />
            <circle cx={x(hover)} cy={y(hp.value)} r="5" fill="#fff" stroke="#1F5C3A" strokeWidth="2.5" />
          </g>
        )}
      </svg>
    </div>
  )
}

export default function MilkHome() {
  const { rec } = useRecords()
  const today = todayIso()
  const yesterday = addDays(today, -1)
  const [range, setRange] = useState<RangeKey>('7')
  const [cFrom, setCFrom] = useState(addDays(today, -29))
  const [cTo, setCTo] = useState(today)

  const customDays = daysBetween(cFrom, cTo)
  const customBad = !cFrom || !cTo || customDays < 0 ? 'Pick a start date before the end date.'
    : customDays > MAX_DAYS ? 'Pick at most 1 year.' : cTo > today ? 'The end date can\'t be in the future.' : null
  // Month view: average L/day for each of the last 12 months.
  const firstOfMonth11Back = (() => { const d = new Date(Number(today.slice(0, 4)), Number(today.slice(5, 7)) - 12, 1); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01` })()
  const from = range === '7' ? addDays(today, -6) : range === '30' ? addDays(today, -29) : range === 'm' ? firstOfMonth11Back : cFrom
  const to = range === 'c' ? cTo : today
  const ok = range !== 'c' || !customBad

  const day = useLoad(() => rec.day(today), [rec, today])
  const yday = useLoad(() => rec.day(yesterday), [rec, yesterday])
  const amG = useLoad(() => rec.grades(today, today, 'AM'), [rec, today])
  const pmG = useLoad(() => rec.grades(today, today, 'PM'), [rec, today])
  const trend = useLoad(ok ? () => rec.trend(from, to) : null, [rec, from, to, ok])
  const grades = useLoad(ok ? () => rec.grades(from, to) : null, [rec, from, to, ok])

  const points = useMemo<Point[]>(() => {
    const t = trend.data ?? []
    const monthly = range === 'm' || (range === 'c' && t.length > 92)
    if (monthly) return byMonth(t)
    return t.map((d) => ({
      label: t.length <= 7 ? weekdayShort(d.date) : `${d.date.slice(8, 10)}/${d.date.slice(5, 7)}`,
      tip: `${shortDate(d.date)} · ${L(d.totalLitres)} L`, value: d.totalLitres,
    }))
  }, [trend.data, range])
  const monthly = range === 'm' || (range === 'c' && (trend.data?.length ?? 0) > 92)
  const stats = useMemo(() => {
    const t = trend.data ?? []
    return { total: t.reduce((s, d) => s + d.totalLitres, 0), withheld: t.reduce((s, d) => s + (d.withheldLitres ?? 0), 0) }
  }, [trend.data])

  const d = day.data
  const amDone = d?.am.cows ?? 0
  const pmDone = d?.pm.cows ?? 0
  const cows = (yday.data?.cows ?? []).filter((c) => c.am != null || c.pm != null)

  return (
    <Screen wide>
      <TopBar title="Milk" back="/home" right={<Link to="/milk/sheet" className="flex h-10 items-center rounded-[10px] bg-brand px-3.5 text-sm font-semibold text-white no-underline hover:text-white">+ Log</Link>} />
      <div className="px-4 pb-24">
        <div className="mb-2 text-sm text-ink-3">Today · {dayLabel(today)}</div>
        <LoadState loading={day.loading} error={day.error} onRetry={day.reload} empty={!d} />
        {d && (
          <>
            <div className="grid grid-cols-3 gap-2.5">
              <Tile k="Morning" v={d.am.litres != null ? `${L(d.am.litres)} L` : '—'} muted={d.am.litres == null} sub={`Yday ${L(d.yesterday.amLitres)} L`} change={d.change.amPct} />
              <Tile k="Evening" v={d.pm.litres != null ? `${L(d.pm.litres)} L` : '—'} muted={d.pm.litres == null} sub={`Yday ${L(d.yesterday.pmLitres)} L`} change={d.change.pmPct} />
              <Tile k={pmDone ? 'Day total' : 'Day so far'} v={`${L(d.totalLitres ?? 0)} L`} sub={`Yday ${L(d.yesterday.totalLitres)} L${d.expectedLitres ? ` · exp. ${L(d.expectedLitres, 0)} L` : ''}`} change={pmDone ? d.change.totalPct : undefined} />
            </div>
            <div className="mt-3 flex flex-col gap-2">
              {(['AM', 'PM'] as const).map((s) => {
                const done = s === 'AM' ? amDone : pmDone
                const name = s === 'AM' ? 'Morning' : 'Evening'
                const g = s === 'AM' ? amG.data : pmG.data
                return done ? (
                  <Link key={s} to={`/milk/sheet?session=${s}`} className="flex flex-col gap-2 rounded-xl bg-white px-3.5 py-3 text-ink no-underline hover:text-ink">
                    <span className="flex items-center gap-3"><span className="h-2.5 w-2.5 rounded-full bg-brand" /><span className="flex-1 text-[15px]"><b>{name}</b> · {done} of {d.milkingCows} cows logged</span><span className="text-sm font-semibold text-brand">Edit</span></span>
                    <span className="flex items-center gap-2 pl-[22px] text-[13px] text-ink-3">Quality <GradeCounts g={g} /></span>
                  </Link>
                ) : (
                  <Link key={s} to={`/milk/sheet?session=${s}`} className="flex items-center gap-3 rounded-xl bg-warn-soft px-3.5 py-3 text-[#4A3210] no-underline hover:text-[#4A3210]">
                    <span className="h-2.5 w-2.5 rounded-full bg-[#C2410C]" /><span className="flex-1 text-[15px]"><b>{name}</b> · not logged yet</span><span className="font-bold text-warn-ink">Start</span>
                  </Link>
                )
              })}
              {(d.am.withheldLitres ?? 0) + (d.pm.withheldLitres ?? 0) > 0 && (
                <Link to="/health/withdrawals" className="rounded-xl bg-danger-soft px-3.5 py-2.5 text-sm text-danger-text no-underline">{L((d.am.withheldLitres ?? 0) + (d.pm.withheldLitres ?? 0))} L discarded today (milk withdrawal)</Link>
              )}
            </div>
          </>
        )}

        <div className="mt-4 grid grid-cols-2 gap-2.5">
          <Link to="/milk/cow" className="btn2 h-12 text-[15px]">One cow + quality</Link>
          <Link to="/milk/sale" className="btn2 h-12 text-[15px]">Farm total / sale</Link>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          {RANGES.map((r) => <button key={r.key} type="button" aria-pressed={range === r.key} onClick={() => setRange(r.key)} className={`chip ${range === r.key ? 'chip-on' : ''}`}>{r.label}</button>)}
        </div>
        {range === 'c' && (
          <div className="mt-2.5 grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="mh-from">From</label><input className="inp" id="mh-from" type="date" value={cFrom} max={cTo} onChange={(e) => setCFrom(e.target.value)} /></div>
            <div><label className="lab" htmlFor="mh-to">To</label><input className="inp" id="mh-to" type="date" value={cTo} max={today} onChange={(e) => setCTo(e.target.value)} /></div>
            <div className={`col-span-2 text-xs ${customBad ? 'font-semibold text-danger' : 'text-ink-3'}`}>{customBad ?? `${customDays + 1} days · up to 1 year${customDays + 1 > 92 ? ' · shown as monthly averages' : ''}`}</div>
          </div>
        )}
        {ok && <LoadState loading={trend.loading} error={trend.error} onRetry={trend.reload} empty={!trend.data} />}
        {ok && trend.data && <YieldChart points={points} title={monthly ? 'Average litres per day, by month' : 'Herd litres per day'} />}

        {ok && trend.data && (
          <div className="mt-2.5 rounded-[14px] bg-white p-3.5">
            <div className="flex items-baseline justify-between"><b className="text-[15px]">{shortDate(from)} – {shortDate(to)}</b><Link to="/settings/milk-grading" className="text-[13px] font-semibold">How grades work</Link></div>
            <div className="mt-2.5 grid grid-cols-2 gap-2.5">
              <div className="rounded-xl bg-brand-soft px-3 py-2.5"><div className="text-xs font-semibold text-[#1C3A28]">Total milk yield</div><div className="mt-0.5 text-lg font-bold text-brand">{L(stats.total, 0)} L</div></div>
              <div className={`rounded-xl px-3 py-2.5 ${stats.withheld > 0 ? 'bg-danger-soft' : 'bg-ground'}`}><div className={`text-xs font-semibold ${stats.withheld > 0 ? 'text-danger-text' : 'text-ink-3'}`}>Milk withdrawal</div><div className="mt-0.5 text-lg font-bold">{L(stats.withheld, 1)} L</div><div className="text-xs text-ink-3">discarded</div></div>
            </div>
            <div className="mt-3 flex items-center gap-2 text-sm"><span className="flex-1 font-semibold">Milk quality</span>
              {grades.loading ? <span className="text-ink-3">…</span> : <GradeCounts g={grades.data} />}
            </div>
          </div>
        )}

        <div className="mt-4 overflow-hidden rounded-[14px] bg-white">
          <div className="grid min-h-10 grid-cols-[1.6fr_.8fr_.8fr_.9fr_.5fr] items-center px-3 text-xs font-bold text-ink-3"><span>Cow · yesterday</span><span>AM</span><span>PM</span><span>Total</span><span>Grade</span></div>
          <LoadState loading={yday.loading} error={yday.error} onRetry={yday.reload} empty={!yday.data} />
          {yday.data && cows.length === 0 && <div className="border-t border-line-faint px-3 py-4 text-sm text-ink-3">No milk logged yesterday.</div>}
          {cows.map((c) => (
            <Link key={c.animalId} to={`/milk/cow?animal=${c.animalId}&date=${yesterday}`} className="grid min-h-[52px] grid-cols-[1.6fr_.8fr_.8fr_.9fr_.5fr] items-center border-t border-line-faint px-3 text-sm text-ink no-underline hover:text-ink">
              <span className="truncate"><b>{c.tag}</b> {c.name}</span><span>{L(c.am)}</span><span>{L(c.pm)}</span><b>{L(c.total)}</b><span><GradeBadge grade={c.grade} /></span>
            </Link>
          ))}
          {cows.length > 0 && yday.data && (
            <div className="grid min-h-[52px] grid-cols-[1.6fr_.8fr_.8fr_.9fr_.5fr] items-center border-t border-line-faint bg-[#F7F7F3] px-3 text-sm">
              <b>Farm total</b><span>{L(yday.data.am.litres)}</span><span>{L(yday.data.pm.litres)}</span><b>{L(yday.data.totalLitres)}</b><span />
            </div>
          )}
        </div>
      </div>
      <Footer className="mt-auto" />
      <BottomNav active="home" />
    </Screen>
  )
}
