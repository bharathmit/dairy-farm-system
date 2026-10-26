import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { BottomNav, Footer, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { BarRow, ChoiceChips, OwnerNote, OwnerOnlyScreen, PERIODS, Stat, useMoney } from '../../components/money'
import { useStore } from '../../data/store'
import { dayLabel, todayIso, useLoad } from '../../data/records'
import { changePct, finCategoryLabel, periodRange, rangeLabel, rupees, type Entry, type EntryStatus, type Period } from '../../data/money'

function EntryRow({ e, first }: { e: Entry; first: boolean }) {
  const income = e.direction === 'INCOME'
  const sub = [e.alloc?.animal ? `${e.alloc.animal.tag}${e.alloc.animal.name ? ` ${e.alloc.animal.name}` : ''}` : null,
    e.member?.name, e.counterparty ?? e.contact?.name, e.periodFrom && e.periodTo ? `${dayLabel(e.periodFrom).slice(4)} – ${dayLabel(e.periodTo).slice(4)}` : null,
    e.notes].filter(Boolean).join(' · ')
  const badge = e.status === 'PENDING'
    ? { t: 'Pending', c: 'bg-warn-soft text-warn-ink' }
    : e.auto ? { t: `Auto${e.source?.type ? ` · ${e.source.type === 'PRODUCTION' ? 'milk' : e.source.type === 'ITEM_TXN' ? 'stock' : e.source.type.toLowerCase().replace(/_/g, ' ')}` : ''}`, c: 'bg-info-soft text-info-text' }
      : { t: 'Manual', c: 'bg-ground text-ink-2' }
  return (
    <Link to={`/money/entries/${e.id}`} className={`flex min-h-[60px] items-center gap-2.5 px-3 py-2 text-ink no-underline hover:text-ink ${first ? '' : 'border-t border-line-faint'}`}>
      <span className="min-w-0 flex-1"><b className="text-[15px]">{finCategoryLabel(e.category)}</b>{sub && <span className="block truncate text-[13px] text-ink-3">{sub}</span>}</span>
      <span className="text-right"><b className={`block ${income ? 'text-brand' : 'text-danger'}`}>{income ? '+' : '−'}{rupees(e.amount)}</b><span className={`badge ${badge.c}`}>{badge.t}</span></span>
    </Link>
  )
}

export default function FinanceHome() {
  const { mo, isOwner } = useMoney()
  const { farm } = useStore()
  const [params, setParams] = useSearchParams()
  const period = (params.get('p') as Period) || 'MONTH'
  const [custom, setCustom] = useState({ from: params.get('from') ?? periodRange('MONTH').from, to: params.get('to') ?? todayIso() })
  const range = useMemo(() => (period === 'CUSTOM' ? custom : periodRange(period)), [period, custom])
  const status = (params.get('status') as EntryStatus) || undefined
  const sum = useLoad(isOwner ? () => mo.summary(range.from, range.to) : null, [mo, range.from, range.to, isOwner])
  const [size, setSize] = useState(30)
  const entries = useLoad(isOwner ? () => mo.entries({ from: range.from, to: range.to, status, size }) : null, [mo, range.from, range.to, status, size, isOwner])

  if (!isOwner) return <OwnerOnlyScreen title="Finance" />
  const s = sum.data
  const setP = (p: Period) => { const n = new URLSearchParams(params); n.set('p', p); setParams(n, { replace: true }) }
  const setStatus = (v?: EntryStatus) => { const n = new URLSearchParams(params); if (v) n.set('status', v); else n.delete('status'); setParams(n, { replace: true }) }
  const inMax = Math.max(0, ...(s?.incomeByCategory ?? []).map((c) => c.amount))
  const outMax = Math.max(0, ...(s?.expenseByCategory ?? []).map((c) => c.amount))
  const list = entries.data?.items ?? []
  const byDay: [string, Entry[]][] = []
  for (const e of list) {
    const last = byDay[byDay.length - 1]
    if (last && last[0] === e.date) last[1].push(e); else byDay.push([e.date, [e]])
  }

  return (
    <Screen wide>
      <TopBar title="Finance" back="/home" right={<Link to="/money/entries/new" className="flex h-10 items-center rounded-[10px] bg-brand px-3.5 text-sm font-semibold text-white no-underline hover:text-white">+ Add</Link>} />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <div>
          <OwnerNote />
          <ChoiceChips label="Period" options={PERIODS} value={period} onChange={setP} />
          {period === 'CUSTOM' && (
            <div className="mt-2 grid grid-cols-2 gap-2">
              <div><label className="lab" htmlFor="fh-from">From</label><input className="inp" id="fh-from" type="date" value={custom.from} max={custom.to} onChange={(e) => e.target.value && setCustom((c) => ({ ...c, from: e.target.value }))} /></div>
              <div><label className="lab" htmlFor="fh-to">To</label><input className="inp" id="fh-to" type="date" value={custom.to} min={custom.from} max={todayIso()} onChange={(e) => e.target.value && setCustom((c) => ({ ...c, to: e.target.value }))} /></div>
            </div>
          )}
          <div className="mt-2.5 text-sm text-ink-3">{rangeLabel(range.from, range.to)} · {farm?.name}</div>
        </div>

        <LoadState loading={sum.loading} error={sum.error} onRetry={sum.reload} empty={!s} />
        {s && (
          <>
            <div className="grid grid-cols-2 gap-2.5 md:grid-cols-4">
              <Stat k="Income" v={<span className="text-brand">{rupees(s.income)}</span>} sub={s.incomeChangePct != null ? `${changePct(s.incomeChangePct)} vs previous` : 'no earlier period'} />
              <Stat k="Expense" v={<span className="text-danger">{rupees(s.expense)}</span>} sub={s.expenseChangePct != null ? `${changePct(s.expenseChangePct)} vs previous` : 'no earlier period'} />
              <Stat k="Net" v={rupees(s.net)} sub="income − expense" />
              {s.marginPct != null && s.marginPct < s.marginAlertPct
                ? <Stat k="Margin" v={`${s.marginPct.toFixed(1)} %`} sub={`Below ${s.marginAlertPct} % alert`} tone="text-danger-text" bg="bg-danger-soft" />
                : s.nearAlert
                  ? <Stat k="Margin" v={`${s.marginPct?.toFixed(1) ?? '—'} %`} sub={`Near ${s.marginAlertPct} % alert`} tone="text-warn-ink" bg="bg-warn-soft" />
                  : <Stat k="Margin" v={s.marginPct != null ? `${s.marginPct.toFixed(1)} %` : '—'} sub={`alert below ${s.marginAlertPct} %`} />}
            </div>
            {s.pending.count > 0 && (
              <button type="button" onClick={() => setStatus(status ? undefined : 'PENDING')} className="flex items-center gap-3 rounded-xl border-0 bg-warn-soft px-3.5 py-3 text-left text-warn-text">
                <span className="flex-1 text-[15px]"><b>{s.pending.count} pending payment{s.pending.count === 1 ? '' : 's'}</b> · {rupees(s.pending.amount)}</span>
                <span className="font-bold text-warn-ink">{status === 'PENDING' ? 'Show all' : 'View'}</span>
              </button>
            )}
            <div className="flex gap-2">
              <Link to="/money/pnl" className="btn2 h-11 flex-1 text-sm">Profit per animal</Link>
              <Link to="/money/workers" className="btn2 h-11 flex-1 text-sm">Workers &amp; wages</Link>
            </div>
            <div className="flex gap-2">
              <Link to="/money/equipment" className="btn2 h-11 flex-1 text-sm">Equipment</Link>
              <Link to="/money/procurement" className="btn2 h-11 flex-1 text-sm">Procurement</Link>
            </div>

            <div className="flex flex-col gap-1.5 rounded-[14px] bg-white p-3.5">
              <div className="flex items-baseline justify-between"><b className="text-[15px]">Income by category</b><span className="text-[13px] text-ink-3">{rupees(s.income)}</span></div>
              {s.incomeByCategory.length === 0 && <div className="text-sm text-ink-3">No income in this period.</div>}
              {s.incomeByCategory.map((c) => <BarRow key={c.category} label={finCategoryLabel(c.category)} amount={c.amount} max={inMax} tone="in" />)}
              <div className="mt-2.5 flex items-baseline justify-between"><b className="text-[15px]">Expense by category</b><span className="text-[13px] text-ink-3">{rupees(s.expense)}</span></div>
              {s.expenseByCategory.length === 0 && <div className="text-sm text-ink-3">No expenses in this period.</div>}
              {s.expenseByCategory.map((c) => <BarRow key={c.category} label={finCategoryLabel(c.category)} amount={c.amount} max={outMax} tone="out" />)}
              {s.feedPctOfMilkIncome != null && <div className="mt-1 text-xs text-ink-3">Feed is {s.feedPctOfMilkIncome.toFixed(1)} % of milk income</div>}
            </div>
          </>
        )}

        <div className="mt-1 flex items-center"><b className="flex-1 text-[15px]">{status === 'PENDING' ? 'Pending entries' : 'Entries'}</b><span className="text-[13px] text-ink-3">newest first</span></div>
        <LoadState loading={entries.loading} error={entries.error} onRetry={entries.reload} empty={!entries.data} />
        {entries.data && list.length === 0 && <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">No entries in this period. Milk sales, purchases, vet costs and wages appear here on their own; add anything else with + Add.</div>}
        {byDay.length > 0 && (
          <div className="overflow-hidden rounded-[14px] bg-white">
            {byDay.map(([day, es]) => (
              <div key={day}>
                <div className="bg-[#F7F7F3] px-3 py-2 text-xs font-bold text-ink-3">{day === todayIso() ? `Today · ${dayLabel(day)}` : dayLabel(day)}</div>
                {es.map((e, i) => <EntryRow key={e.id} e={e} first={i === 0} />)}
              </div>
            ))}
          </div>
        )}
        {entries.data && entries.data.totalItems > list.length && (
          <button type="button" className="btn2 h-11 text-sm" onClick={() => setSize((n) => n + 30)}>Show more ({entries.data.totalItems - list.length} left)</button>
        )}
      </div>
      <Footer className="mt-auto" />
      <BottomNav active="more" />
    </Screen>
  )
}
