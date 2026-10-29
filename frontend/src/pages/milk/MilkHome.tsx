import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { GradeBadge, LoadState, useRecords } from '../../components/records'
import { LineChart, RangePicker } from '../../components/charts'
import { byMonth, dayTick, monthName, useRange, type Point } from '../../data/charts'
import { addDays, dayLabel, L, pct, shortDate, todayIso, useLoad, type CowDay, type Grade } from '../../data/records'


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

/** Why a cow-day has no grade (null when graded). */
function noGradeReason(c: CowDay): string | null {
  if (c.grade) return null
  if (c.withheld) return 'Milk withdrawal — not graded'
  if (c.am == null || c.pm == null) return `${c.am == null ? 'Morning' : 'Evening'} not logged — grade needs both sessions`
  if (c.daysInMilk == null) return 'No calving date — add it on the animal to get an expected yield'
  if (c.expectedLitres == null) return 'Past the end of lactation — due for dry-off, not graded'
  return 'No expected yield to compare with'
}

const COLS = 'grid-cols-[1.5fr_.65fr_.65fr_.75fr_.75fr_.5fr]'

export default function MilkHome() {
  const { rec } = useRecords()
  const today = todayIso()
  const yesterday = addDays(today, -1)
  // Month view: average L/day for each of the last 12 months.
  const r = useRange('7')
  const { from, to, ok, monthly } = r

  const day = useLoad(() => rec.day(today), [rec, today])
  const yday = useLoad(() => rec.day(yesterday), [rec, yesterday])
  const amG = useLoad(() => rec.grades(today, today, 'AM'), [rec, today])
  const pmG = useLoad(() => rec.grades(today, today, 'PM'), [rec, today])
  const trend = useLoad(ok ? () => rec.trend(from, to) : null, [rec, from, to, ok])
  const grades = useLoad(ok ? () => rec.grades(from, to) : null, [rec, from, to, ok])

  const points = useMemo<Point[]>(() => {
    const t = trend.data ?? []
    if (monthly) return byMonth(t, (d) => d.totalLitres, true).map((m) => ({ label: monthName(m.month), tip: `${monthName(m.month)} · avg ${L(m.value)} L/day`, value: m.value }))
    return t.map((d) => ({ label: dayTick(d.date, t.length <= 7), tip: `${shortDate(d.date)} · ${L(d.totalLitres)} L`, value: d.totalLitres }))
  }, [trend.data, monthly])
  const avg = points.length ? points.reduce((s, p) => s + p.value, 0) / points.length : 0
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

        <div className="mt-4"><RangePicker r={r} id="mh" /></div>
        {ok && <LoadState loading={trend.loading} error={trend.error} onRetry={trend.reload} empty={!trend.data} />}
        {ok && trend.data && <LineChart points={points} unit="L" right={`avg ${L(avg, 0)} L`} title={monthly ? 'Average litres per day, by month' : 'Herd litres per day'} />}

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
          <div className={`grid min-h-10 ${COLS} items-center gap-1 px-3 text-xs font-bold text-ink-3`}><span>Cow · yesterday</span><span>AM</span><span>PM</span><span>Total</span><span>Expected</span><span>Grade</span></div>
          <LoadState loading={yday.loading} error={yday.error} onRetry={yday.reload} empty={!yday.data} />
          {yday.data && cows.length === 0 && <div className="border-t border-line-faint px-3 py-4 text-sm text-ink-3">No milk logged yesterday.</div>}
          {cows.map((c) => {
            const why = noGradeReason(c)
            return (
              <Link key={c.animalId} to={`/milk/cow?animal=${c.animalId}&date=${yesterday}`} className="block border-t border-line-faint px-3 py-2 text-sm text-ink no-underline hover:text-ink">
                <span className={`grid min-h-9 ${COLS} items-center gap-1`}>
                  <span className="truncate"><b>{c.tag}</b> {c.name}</span><span>{L(c.am)}</span><span>{L(c.pm)}</span><b>{L(c.total)}</b><span className="text-ink-3">{L(c.expectedLitres)}</span><span><GradeBadge grade={c.grade} /></span>
                </span>
                {why && <span className="block text-xs font-semibold text-danger">{why}</span>}
              </Link>
            )
          })}
          {cows.length > 0 && yday.data && (
            <div className={`grid min-h-[52px] ${COLS} items-center gap-1 border-t border-line-faint bg-[#F7F7F3] px-3 text-sm`}>
              <b>Farm total</b><span>{L(yday.data.am.litres)}</span><span>{L(yday.data.pm.litres)}</span><b>{L(yday.data.totalLitres)}</b><b className="text-ink-2">{L(cows.reduce((s, c) => s + (c.expectedLitres ?? 0), 0))}</b><span />
            </div>
          )}
        </div>
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
