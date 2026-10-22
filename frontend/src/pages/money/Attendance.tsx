import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Screen, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { LoadState } from '../../components/records'
import { OwnerOnlyScreen, useMoney } from '../../components/money'
import { useStore } from '../../data/store'
import { addDays, decimal, todayIso, toNum, useLoad, weekdayShort } from '../../data/records'
import { mondayOf, rangeLabel, rupees, type DayStatus } from '../../data/money'
import { errorMessage } from '../../lib/api'

type Cell = { status: DayStatus | null; hours: string }
const NEXT: Record<string, DayStatus | null> = { null: 'PRESENT', PRESENT: 'HALF', HALF: 'ABSENT', ABSENT: null }
const LOOK: Record<string, { t: string; c: string; a: string }> = {
  PRESENT: { t: 'P', c: 'border-brand bg-brand text-white', a: 'present' },
  HALF: { t: '½', c: 'border-warn-ink bg-warn-soft text-warn-ink', a: 'half day' },
  ABSENT: { t: 'A', c: 'border-danger bg-danger-soft text-danger-text', a: 'absent' },
  null: { t: '–', c: 'border-line bg-white text-ink-3', a: 'not marked' },
}

export default function Attendance() {
  const { mo, isOwner } = useMoney()
  const { notify } = useStore()
  const nav = useNavigate()
  const [params, setParams] = useSearchParams()
  const week = mondayOf(params.get('week') ?? todayIso())
  const data = useLoad(isOwner ? () => mo.attendance(week) : null, [mo, week, isOwner])
  const [cells, setCells] = useState<Record<string, Cell>>({})
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    if (!data.data) return
    const c: Record<string, Cell> = {}
    data.data.rows.forEach((r) => r.days.forEach((d) => { c[`${r.member.id}|${d.date}`] = { status: d.status ?? null, hours: d.hours != null ? String(d.hours) : '' } }))
    setCells(c)
  }, [data.data])

  if (!isOwner) return <OwnerOnlyScreen title="Attendance" back="/money/workers" />
  const w = data.data
  const dates = Array.from({ length: 7 }, (_, i) => addDays(week, i))
  const today = todayIso()
  const go = (d: number) => { const n = new URLSearchParams(params); n.set('week', addDays(week, d * 7)); setParams(n, { replace: true }) }
  const cell = (m: number, d: string): Cell => cells[`${m}|${d}`] ?? { status: null, hours: '' }
  const setCell = (m: number, d: string, p: Partial<Cell>) => setCells((c) => {
    const cur = c[`${m}|${d}`] ?? { status: null, hours: '' }
    const next = { ...cur, ...p }
    if (p.status !== undefined && !cur.hours && next.status) next.hours = next.status === 'PRESENT' ? '8' : next.status === 'HALF' ? '4' : ''
    if (p.status === null || p.status === 'ABSENT') next.hours = ''
    return { ...c, [`${m}|${d}`]: next }
  })
  const rowTotals = (m: number, rate: number, wageType: string) => {
    let present = 0; let hours = 0
    dates.forEach((d) => { const x = cell(m, d); if (x.status === 'PRESENT') present += 1; else if (x.status === 'HALF') present += 0.5; hours += toNum(x.hours) ?? 0 })
    const amount = wageType === 'DAILY' ? present * rate : (rate / 30.4) * present
    return { present, hours, amount }
  }
  const save = async () => {
    if (!w) return
    const entries = w.rows.flatMap((r) => dates.map((d) => {
      const x = cell(r.member.id, d)
      return { memberId: r.member.id, date: d, status: x.status, hours: toNum(x.hours) ?? null }
    }))
    setBusy(true)
    try { await mo.saveAttendance(entries); notify('Attendance saved'); nav('/money/workers') } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  const totals = w ? w.rows.reduce((s, r) => { const t = rowTotals(r.member.id, r.wageRate, r.wageType); return { d: s.d + t.present, h: s.h + t.hours, a: s.a + t.amount } }, { d: 0, h: 0, a: 0 }) : null
  const nav7 = 'flex h-11 w-11 items-center justify-center rounded-[10px] border border-line bg-white disabled:opacity-40'

  return (
    <Screen>
      <TopBar title="Attendance" back="/money/workers" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <div className="flex items-center gap-2">
          <button type="button" aria-label="Previous week" className={nav7} onClick={() => go(-1)}><Icon name="back" size={18} /></button>
          <b className="flex-1 text-center text-base">{rangeLabel(week, addDays(week, 6))}</b>
          <button type="button" aria-label="Next week" className={nav7} disabled={addDays(week, 7) > today} onClick={() => go(1)}><Icon name="chevron" size={18} /></button>
        </div>
        <div className="flex flex-wrap gap-3.5 text-[13px] text-ink-2">
          {(['PRESENT', 'HALF', 'ABSENT'] as const).map((s) => <span key={s} className="inline-flex items-center gap-1.5"><span className={`inline-flex h-[22px] w-[22px] items-center justify-center rounded-md border text-xs font-bold ${LOOK[s].c}`}>{LOOK[s].t}</span>{s === 'PRESENT' ? 'Present' : s === 'HALF' ? 'Half day' : 'Absent'}</span>)}
          <span className="text-ink-3">Tap to cycle</span>
        </div>
        <LoadState loading={data.loading} error={data.error} onRetry={data.reload} empty={!w} />
        {w && w.rows.length === 0 && <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">No workers with a daily or monthly wage. Set wages on the Team page.</div>}
        {w?.rows.map((r) => {
          const t = rowTotals(r.member.id, r.wageRate, r.wageType)
          return (
            <div key={r.member.id} className="rounded-[14px] bg-white p-3.5">
              <div className="flex items-baseline justify-between"><b className="text-base">{r.member.name}</b><span className="text-[13px] text-ink-3">{r.wageType === 'DAILY' ? `Daily · ${rupees(r.wageRate)}` : `Monthly · ${rupees(r.wageRate)}`}</span></div>
              <div className="mt-3 grid grid-cols-7 gap-1">
                {dates.map((d) => <span key={`l${d}`} className={`text-center text-xs font-bold ${d === today ? 'text-brand' : 'text-ink-3'}`}>{weekdayShort(d)}</span>)}
                {dates.map((d) => {
                  const x = cell(r.member.id, d); const look = LOOK[String(x.status)]
                  return <button key={`s${d}`} type="button" disabled={d > today} aria-label={`${weekdayShort(d)} ${look.a}`} onClick={() => setCell(r.member.id, d, { status: NEXT[String(x.status)] })}
                    className={`h-11 rounded-[10px] border p-0 text-[15px] font-bold disabled:opacity-40 ${look.c}`}>{look.t}</button>
                })}
                {dates.map((d) => {
                  const x = cell(r.member.id, d)
                  return <input key={`h${d}`} aria-label={`${weekdayShort(d)} hours`} inputMode="decimal" placeholder="h" disabled={d > today || x.status === 'ABSENT'} value={x.hours}
                    onChange={(e) => setCell(r.member.id, d, { hours: decimal(e.target.value) })} className="h-9 w-full rounded-lg border border-line p-0 text-center text-sm disabled:bg-ground" />
                })}
              </div>
              <div className="mt-3 flex items-center justify-between border-t border-line-faint pt-2.5 text-[15px]">
                <span>{t.present} day{t.present === 1 ? '' : 's'} · {t.hours} h</span>
                <b>{r.wageType === 'DAILY' ? `Wage ${rupees(t.amount)}` : `Accrued ≈ ${rupees(t.amount)}`}</b>
              </div>
              {r.wageType === 'MONTHLY' && <div className="mt-1 text-xs text-ink-3">Monthly pay ÷ days in month; absences beyond the allowed leave days are deducted when paying.</div>}
            </div>
          )
        })}
        {totals && w && w.rows.length > 0 && (
          <div className="flex justify-between rounded-xl bg-brand-soft px-3.5 py-3 text-[15px]"><span><b>Week total</b> · {totals.d} days · {totals.h} h</span><b>{rupees(totals.a)}</b></div>
        )}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <button type="button" className="btn" disabled={busy || !w || w.rows.length === 0} onClick={save}>{busy ? 'Saving…' : 'Save attendance'}</button>
      </div>
    </Screen>
  )
}
