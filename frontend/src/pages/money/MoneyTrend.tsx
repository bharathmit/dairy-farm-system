import { useMemo } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { LineChart, RangePicker } from '../../components/charts'
import { OwnerOnlyScreen, Stat, useMoney } from '../../components/money'
import { byMonth, dayTick, kFmt, monthName, useRange, type Point } from '../../data/charts'
import { isMoneyKind, MONEY_KINDS, rupees, type DailyMoney, type MoneyKind } from '../../data/money'
import { shortDate, useLoad } from '../../data/records'

const sum = (rows: DailyMoney[], f: (d: DailyMoney) => number) => rows.reduce((s, d) => s + f(d), 0)

/** Chart page behind the four Home money panels: 7 days, 30 days, 12 months or a custom range (max 1 year). */
export default function MoneyTrend() {
  const { mo, isOwner } = useMoney()
  const nav = useNavigate()
  const { kind: raw } = useParams()
  const kind: MoneyKind = isMoneyKind(raw) ? raw : 'feed'
  const m = MONEY_KINDS[kind]
  const margin = kind.endsWith('margin')
  const r = useRange('30')
  const { from, to, ok, monthly } = r

  const data = useLoad(ok && isOwner ? () => mo.daily(from, to) : null, [mo, from, to, ok, isOwner])
  const rows = useMemo(() => data.data ?? [], [data.data])

  const points = useMemo<Point[]>(() => {
    if (monthly) return byMonth(rows, m.value, false).map((x) => ({ label: monthName(x.month), tip: `${monthName(x.month)} · ${rupees(x.value, margin)}`, value: x.value }))
    return rows.map((d) => ({ label: dayTick(d.date, rows.length <= 7), tip: `${shortDate(d.date)} · ${rupees(m.value(d), margin)}`, value: m.value(d) }))
  }, [rows, monthly, m, margin])

  const total = sum(rows, m.value)
  const avg = rows.length ? total / rows.length : 0
  const parts = kind === 'milk-margin'
    ? [{ k: 'Milk sale income', v: sum(rows, (d) => d.milkIncome) }, { k: 'Feed cost (fed)', v: sum(rows, (d) => d.feedCost) }]
    : kind === 'other-margin'
      ? [{ k: 'Other income', v: sum(rows, (d) => d.otherIncome) }, { k: 'Other expenses', v: sum(rows, (d) => d.otherExpense) }]
      : []

  if (!isOwner) return <OwnerOnlyScreen title={m.title} />

  return (
    <Screen wide>
      <TopBar title={m.title} back="/home" />
      <div className="flex flex-col gap-3 px-4 pb-8">
        <label className="sr-only" htmlFor="mt-kind">Show</label>
        <select className="inp" id="mt-kind" value={kind} onChange={(e) => nav(`/money/trend/${e.target.value}`, { replace: true })}>
          {(Object.keys(MONEY_KINDS) as MoneyKind[]).map((k) => <option key={k} value={k}>{MONEY_KINDS[k].title}</option>)}
        </select>
        <RangePicker r={r} monthLabel="12 months" id="mt" />
        {ok && <LoadState loading={data.loading} error={data.error} onRetry={data.reload} empty={!data.data} />}
        {ok && data.data && (
          <>
            <LineChart points={points} unit="₹" fmtAxis={kFmt} right={monthly ? 'total per month' : `avg ${rupees(avg, margin)}/day`}
              title={`${m.title}${monthly ? ', by month' : ', per day'}`} />
            <div className="rounded-[14px] bg-white p-3.5">
              <b className="text-[15px]">{shortDate(from)} – {shortDate(to)}</b>
              <div className="mt-2.5 grid grid-cols-2 gap-2.5 md:grid-cols-4">
                <Stat k={margin ? 'Net' : 'Total'} v={<span className={margin && total < 0 ? 'text-danger' : ''}>{rupees(total, margin)}</span>} bg="bg-brand-soft" />
                <Stat k="Average per day" v={rupees(avg, margin)} bg="bg-ground" />
                {parts.map((p) => <Stat key={p.k} k={p.k} v={rupees(p.v)} bg="bg-ground" />)}
              </div>
              <p className="m-0 mt-2.5 text-xs text-ink-3">{m.note}. <Link to="/money" className="font-semibold">Finance</Link></p>
            </div>
          </>
        )}
      </div>
    </Screen>
  )
}
