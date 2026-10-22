import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { LoadState } from '../../components/records'
import { OwnerOnlyScreen } from '../../components/money'
import { useAlerts } from '../../components/alerts'
import { useStore } from '../../data/store'
import { REPORTS, type ReportType } from '../../data/alerts'
import { addDays, todayIso, useLoad } from '../../data/records'

const cell = (v: string | number | null | undefined) => (v === null || v === undefined ? '—' : typeof v === 'number' ? v.toLocaleString('en-IN', { maximumFractionDigits: 2 }) : v)

/** Printable report table; the browser's print dialog saves it as PDF. */
export default function ReportView() {
  const { type } = useParams()
  const [params] = useSearchParams()
  const { al, isOwner } = useAlerts()
  const { farm } = useStore()
  const nav = useNavigate()
  const from = params.get('from') ?? addDays(todayIso(), -29)
  const to = params.get('to') ?? todayIso()
  const t = (REPORTS.find((r) => r.type === type)?.type ?? 'milk') as ReportType
  const rep = useLoad(isOwner ? () => al.report(t, from, to) : null, [al, isOwner, t, from, to])
  if (!isOwner) return <OwnerOnlyScreen title="Report" />
  const r = rep.data

  return (
    <div className="mx-auto min-h-[100dvh] max-w-[1000px] bg-white p-4 text-ink print:p-0">
      <div className="mb-4 flex items-center gap-2 print:hidden">
        <button type="button" onClick={() => nav(-1)} className="h-11 rounded-xl border border-line bg-white px-4 font-semibold">Back</button>
        <div className="flex-1" />
        <button type="button" disabled={!r} onClick={() => window.print()} className="h-11 rounded-xl bg-brand px-4 font-semibold text-white">Print / Save PDF</button>
      </div>
      <LoadState loading={rep.loading} error={rep.error} onRetry={rep.reload} empty={!r} />
      {r && (
        <>
          <h1 className="m-0 text-2xl font-bold">{r.title}</h1>
          <div className="mb-4 mt-1 text-sm text-ink-3">{farm?.name} · {r.period}</div>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr>{r.columns.map((c) => <th key={c.key} className="border-b-2 border-ink px-2 py-1.5 text-left font-bold">{c.label}</th>)}</tr>
              </thead>
              <tbody>
                {r.rows.length === 0 && <tr><td colSpan={r.columns.length} className="px-2 py-3 text-ink-3">No records in this period.</td></tr>}
                {r.rows.map((row, i) => (
                  <tr key={i} className="border-b border-line-faint">{r.columns.map((c) => <td key={c.key} className={`px-2 py-1.5 ${typeof row[c.key] === 'number' ? 'text-right tabular-nums' : ''}`}>{cell(row[c.key])}</td>)}</tr>
                ))}
              </tbody>
              {r.totals && (
                <tfoot>
                  <tr>{r.columns.map((c, i) => <td key={c.key} className={`border-t-2 border-ink px-2 py-1.5 font-bold ${typeof r.totals?.[c.key] === 'number' ? 'text-right tabular-nums' : ''}`}>{i === 0 && r.totals?.[c.key] == null ? 'Total' : r.totals?.[c.key] == null ? '' : cell(r.totals[c.key])}</td>)}</tr>
                </tfoot>
              )}
            </table>
          </div>
          <div className="mt-6 text-xs text-ink-3">Printed {new Date().toLocaleString('en-GB')} · © 2026 Dairy Farm</div>
        </>
      )}
    </div>
  )
}
