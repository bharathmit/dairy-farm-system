import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { BottomNav, Footer, Screen, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { LoadState } from '../../components/records'
import { ChoiceChips, OwnerOnlyScreen } from '../../components/money'
import { useAlerts } from '../../components/alerts'
import { useStore } from '../../data/store'
import { REPORTS, type ReportType } from '../../data/alerts'
import { changePct, rangeLabel, rupees } from '../../data/money'
import { addDays, todayIso, useLoad } from '../../data/records'
import { errorMessage } from '../../lib/api'

type ReportPeriod = 'D7' | 'D30' | 'MONTH' | 'QUARTER' | 'CUSTOM'
const PERIODS: { value: ReportPeriod; label: string }[] = [
  { value: 'D7', label: '7 days' }, { value: 'D30', label: '30 days' }, { value: 'MONTH', label: 'This month' },
  { value: 'QUARTER', label: 'Quarter' }, { value: 'CUSTOM', label: 'Custom' },
]
const pad = (n: number) => String(n).padStart(2, '0')
function reportRange(p: ReportPeriod, custom: { from: string; to: string }) {
  const today = todayIso(); const d = new Date()
  switch (p) {
    case 'D7': return { from: addDays(today, -6), to: today }
    case 'D30': return { from: addDays(today, -29), to: today }
    case 'QUARTER': { const q = Math.floor(d.getMonth() / 3) * 3; return { from: `${d.getFullYear()}-${pad(q + 1)}-01`, to: today } }
    case 'CUSTOM': return custom
    default: return { from: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-01`, to: today }
  }
}

/** Owner reports: summary tiles, analysis links and six exports (printable PDF or CSV). */
export default function Reports() {
  const { al, isOwner } = useAlerts()
  const { notify } = useStore()
  const [params, setParams] = useSearchParams()
  const period = (params.get('p') as ReportPeriod) || 'MONTH'
  const [custom, setCustom] = useState({ from: params.get('from') ?? addDays(todayIso(), -29), to: params.get('to') ?? todayIso() })
  const { from, to } = reportRange(period, custom)
  const sum = useLoad(isOwner ? () => al.reportSummary(from, to) : null, [al, isOwner, from, to])
  const [busy, setBusy] = useState<string | null>(null)
  if (!isOwner) return <OwnerOnlyScreen title="Reports" />

  const setPeriod = (p: ReportPeriod) => { const q = new URLSearchParams(params); q.set('p', p); setParams(q, { replace: true }) }
  const csv = async (type: ReportType) => {
    setBusy(type)
    try {
      const blob = await al.reportCsv(type, from, to)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url; a.download = `${type}-${from}-to-${to}.csv`
      document.body.appendChild(a); a.click(); a.remove()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(null) }
  }
  const s = sum.data
  const tile = 'rounded-xl bg-white p-3 text-ink no-underline hover:text-ink'

  return (
    <Screen wide>
      <TopBar title="Reports" back="/home" />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <ChoiceChips label="Period" options={PERIODS} value={period} onChange={setPeriod} />
        {period === 'CUSTOM' && (
          <div className="grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="rp-from">From</label><input className="inp" id="rp-from" type="date" max={custom.to} value={custom.from} onChange={(e) => e.target.value && setCustom((c) => ({ ...c, from: e.target.value }))} /></div>
            <div><label className="lab" htmlFor="rp-to">To</label><input className="inp" id="rp-to" type="date" min={custom.from} max={todayIso()} value={custom.to} onChange={(e) => e.target.value && setCustom((c) => ({ ...c, to: e.target.value }))} /></div>
          </div>
        )}
        <LoadState loading={sum.loading} error={sum.error} onRetry={sum.reload} empty={!s} />
        {s && (
          <div className="grid grid-cols-2 gap-2.5 md:grid-cols-4">
            <Link to="/milk" className={tile}><div className="text-xs font-semibold text-ink-3">Milk</div><div className="mt-0.5 text-xl font-bold">{Math.round(s.milkLitres).toLocaleString('en-IN')} L</div>
              {s.milkChangePct != null && <div className={`text-xs font-semibold ${s.milkChangePct < 0 ? 'text-danger' : 'text-brand'}`}>{changePct(s.milkChangePct)} vs previous</div>}</Link>
            <Link to="/money" className={tile}><div className="text-xs font-semibold text-ink-3">Net profit</div><div className={`mt-0.5 text-xl font-bold ${(s.netProfit ?? 0) < 0 ? 'text-danger' : ''}`}>{rupees(s.netProfit)}</div>
              <div className="text-xs text-ink-3">income {rupees(s.income)}</div></Link>
            <Link to="/feed" className={tile}><div className="text-xs font-semibold text-ink-3">Feed cost / L</div><div className="mt-0.5 text-xl font-bold">{s.feedCostPerLitre != null ? `₹${s.feedCostPerLitre.toFixed(1)}` : '—'}</div></Link>
            <Link to="/reports/cull" className={tile}><div className="text-xs font-semibold text-ink-3">Cull / monitor</div><div className="mt-0.5 text-xl font-bold">{s.cull.cull} / {s.cull.monitor}</div><div className="text-xs font-semibold text-brand">Cull advisor</div></Link>
          </div>
        )}
        <div className="grid grid-cols-2 gap-2.5">
          <Link to="/reports/cull" className="btn2 h-12 gap-2 text-[15px]"><Icon name="list" size={18} />Cull advisor</Link>
          <Link to="/reports/compare" className="btn2 h-12 gap-2 text-[15px]"><Icon name="team" size={18} />Compare cows</Link>
        </div>
        <div className="mt-1 text-xs font-bold uppercase tracking-[.06em] text-ink-3">Reports · {rangeLabel(from, to)}</div>
        <div className="rounded-[14px] bg-white px-3.5">
          {REPORTS.map((r, i) => (
            <div key={r.type} className={`flex min-h-[60px] items-center gap-2 ${i ? 'border-t border-line-faint' : ''}`}>
              <span className="flex-1 py-2"><b className="text-[15px]">{r.title}</b><br /><span className="text-[13px] text-ink-3">{r.sub}</span></span>
              <Link to={`/reports/view/${r.type}?from=${from}&to=${to}`} className="flex h-10 items-center rounded-[10px] border border-line px-3 text-sm font-semibold text-ink no-underline">PDF</Link>
              <button type="button" disabled={busy === r.type} onClick={() => csv(r.type)} className="h-10 rounded-[10px] border border-line bg-white px-3 text-sm font-semibold">{busy === r.type ? '…' : 'CSV'}</button>
            </div>
          ))}
        </div>
        <p className="m-0 text-[13px] text-ink-3">Reports use the period above. PDF opens a printable page — choose "Save as PDF" when printing. Owner only.</p>
      </div>
      <Footer className="mt-auto" />
      <BottomNav active="more" />
    </Screen>
  )
}
