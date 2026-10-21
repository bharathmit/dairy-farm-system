import { useState } from 'react'
import { Link } from 'react-router-dom'
import { BottomNav, Footer, Screen, TopBar } from '../../components/ui'
import { GradeBadge, LoadState, useRecords } from '../../components/records'
import { addDays, dayLabel, L, pct, todayIso, useLoad, weekdayShort, type Grade, type TrendDay } from '../../data/records'

const RANGES = [{ key: '7', label: '7 days', days: 7 }, { key: '30', label: '30 days', days: 30 }, { key: 'm', label: 'Month', days: 0 }] as const
type RangeKey = (typeof RANGES)[number]['key']

function Tile({ k, v, sub, change, muted }: { k: string; v: string; sub: string; change?: number | null; muted?: boolean }) {
  return (
    <div className="rounded-xl bg-white p-3">
      <div className="text-xs font-semibold text-ink-3">{k}</div>
      <div className={`mt-0.5 text-xl font-bold ${muted ? 'text-ink-3' : ''}`}>{v}</div>
      <div className="mt-1 text-xs leading-snug text-ink-3">{sub}{change !== undefined && change !== null && <><br /><b className={change < 0 ? 'text-danger' : 'text-brand'}>{pct(change)}</b></>}</div>
    </div>
  )
}

function TrendChart({ days }: { days: TrendDay[] }) {
  if (!days.length) return null
  const max = Math.max(1, ...days.map((d) => d.totalLitres))
  const w = 326, h = 96, n = days.length
  const x = (i: number) => (n === 1 ? w / 2 : 10 + (i * (w - 20)) / (n - 1))
  const y = (v: number) => 88 - (v / max) * 76
  const pts = days.map((d, i) => `${x(i)},${y(d.totalLitres)}`).join(' ')
  const avg = days.reduce((s, d) => s + d.totalLitres, 0) / n
  const labels = n <= 7 ? days.map((d) => weekdayShort(d.date)) : [days[0], days[Math.floor(n / 2)], days[n - 1]].map((d) => d.date.slice(8, 10) + '/' + d.date.slice(5, 7))
  return (
    <div className="mt-2.5 rounded-[14px] bg-white p-3.5">
      <div className="flex items-baseline justify-between"><b className="text-[15px]">Herd litres per day</b><span className="text-[13px] text-ink-3">avg {L(avg, 0)} L</span></div>
      <svg width="100%" viewBox={`0 0 ${w} ${h}`} role="img" aria-label={`Daily herd milk over ${n} days, average ${avg.toFixed(0)} litres`} className="mt-2 block">
        <line x1="0" y1="88" x2={w} y2="88" stroke="#E4E6DF" />
        <polyline points={pts} fill="none" stroke="#1F5C3A" strokeWidth="2.5" />
        <circle cx={x(n - 1)} cy={y(days[n - 1].totalLitres)} r="4" fill="#1F5C3A" />
      </svg>
      <div className="flex justify-between text-xs text-ink-3">{labels.map((l, i) => <span key={i}>{l}</span>)}</div>
    </div>
  )
}

export default function MilkHome() {
  const { rec } = useRecords()
  const today = todayIso()
  const yesterday = addDays(today, -1)
  const [range, setRange] = useState<RangeKey>('7')
  const from = range === 'm' ? today.slice(0, 8) + '01' : addDays(today, -(RANGES.find((r) => r.key === range)!.days - 1))

  const day = useLoad(() => rec.day(today), [rec, today])
  const yday = useLoad(() => rec.day(yesterday), [rec, yesterday])
  const trend = useLoad(() => rec.trend(from, today), [rec, from, today])
  const grades = useLoad(() => rec.grades(addDays(today, -29), today), [rec, today])

  const d = day.data
  const amDone = d?.am.cows ?? 0
  const pmDone = d?.pm.cows ?? 0
  const g = grades.data ?? ({} as Partial<Record<Grade, number>>)
  const cows = (yday.data?.cows ?? []).filter((c) => c.am != null || c.pm != null)

  return (
    <Screen>
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
                return done ? (
                  <Link key={s} to={`/milk/sheet?session=${s}`} className="flex items-center gap-3 rounded-xl bg-white px-3.5 py-3 text-ink no-underline hover:text-ink">
                    <span className="h-2.5 w-2.5 rounded-full bg-brand" /><span className="flex-1 text-[15px]"><b>{name}</b> · {done} of {d.milkingCows} cows logged</span><span className="text-sm font-semibold text-brand">Edit</span>
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

        <div className="mt-4 flex gap-2">
          {RANGES.map((r) => <button key={r.key} type="button" aria-pressed={range === r.key} onClick={() => setRange(r.key)} className={`chip ${range === r.key ? 'chip-on' : ''}`}>{r.label}</button>)}
        </div>
        <LoadState loading={trend.loading} error={trend.error} onRetry={trend.reload} empty={!trend.data} />
        {trend.data && <TrendChart days={trend.data} />}

        <div className="mt-4 flex items-center gap-2">
          <b className="flex-1 text-[15px]">Quality (30 d)</b>
          {(['A', 'B', 'C', 'D'] as const).map((k) => <span key={k} className={`badge font-bold ${k === 'A' ? 'bg-brand-soft text-brand' : k === 'B' ? 'bg-info-soft text-info-text' : k === 'C' ? 'bg-warn-soft text-warn-ink' : 'bg-danger-soft text-danger-text'}`}>{k} {g[k] ?? 0}</span>)}
        </div>
        <Link to="/settings/milk-grading" className="mt-1 inline-block text-[13px] font-semibold">How grades work</Link>

        <div className="mt-2.5 overflow-hidden rounded-[14px] bg-white">
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
