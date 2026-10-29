import { Link, useSearchParams } from 'react-router-dom'
import { BottomNav, Footer, Screen } from '../../components/ui'
import { Icon } from '../../components/icons'
import { LoadState } from '../../components/records'
import { AlertCard, alertDetail, refreshAlertCount, useAlerts } from '../../components/alerts'
import { useStore } from '../../data/store'
import {
  CATEGORY_LABEL, dueText, SEVERITY_DOT, SEVERITY_LABEL, type Alert, type AlertCategory, type AlertsPage, type Severity,
} from '../../data/alerts'
import { todayIso, useLoad } from '../../data/records'
import { errorMessage } from '../../lib/api'

const CATS: AlertCategory[] = ['REPRODUCTION', 'CALF', 'HEALTH', 'MILK', 'FEED', 'FINANCE_OPS']
const SEVS: Severity[] = ['HIGH', 'MEDIUM', 'INFO']

/** Open alerts grouped by when they are due, with category and severity filters. */
export default function AlertsList() {
  const { al, farmId, isOwner } = useAlerts()
  const { notify } = useStore()
  const [params, setParams] = useSearchParams()
  const category = (params.get('category') as AlertCategory) || undefined
  const severity = (params.get('severity') as Severity) || undefined
  const page = useLoad(() => al.alerts({ category, severity }), [al, category, severity])
  // Counts for the chips come from the unfiltered list.
  const all = useLoad(() => (category || severity ? al.alerts() : Promise.resolve(null)), [al, category, severity])
  const counts = (category || severity ? all.data : page.data)?.counts
  const today = todayIso()

  const set = (k: string, v?: string) => {
    const p = new URLSearchParams(params)
    if (v) p.set(k, v); else p.delete(k)
    setParams(p, { replace: true })
  }
  const remove = (id: number) => {
    page.setData((cur) => {
      if (!cur) return cur
      const drop = (l: Alert[]) => l.filter((a) => a.id !== id)
      return { ...cur, overdue: drop(cur.overdue), today: drop(cur.today), thisWeek: drop(cur.thisWeek), later: drop(cur.later) } as AlertsPage
    })
  }
  const refresh = async () => {
    try { const r = await al.refresh(); notify(`Alerts checked · ${r.created} new, ${r.closed} closed`); refreshAlertCount(farmId); void page.reload(); void all.reload() } catch (e) { notify(errorMessage(e)) }
  }

  const p = page.data
  const section = (title: string, list: Alert[], tone?: string) => list.length > 0 && (
    <>
      <div className={`mb-2 mt-4 text-xs font-bold uppercase tracking-[.06em] ${tone ?? 'text-ink-3'}`}>{title} · {list.length}</div>
      <div className="flex flex-col gap-2.5">{list.map((a) => <AlertCard key={a.id} a={a} today={today} onChanged={() => remove(a.id)} />)}</div>
    </>
  )
  const compact = (title: string, list: Alert[]) => list.length > 0 && (
    <>
      <div className="mb-2 mt-4 text-xs font-bold uppercase tracking-[.06em] text-ink-3">{title} · {list.length}</div>
      <div className="rounded-[14px] bg-white px-3.5">
        {list.map((a, i) => (
          <Link key={a.id} to={`/alerts/${a.id}`} className={`flex min-h-14 items-center gap-2.5 text-ink no-underline hover:text-ink ${i ? 'border-t border-line-faint' : ''}`}>
            <span className="h-2 w-2 shrink-0 rounded-full" style={{ background: SEVERITY_DOT[a.severity] }} />
            <span className="flex-1 py-1.5"><b>{a.title}</b>{a.animal ? ` · ${a.animal.tag}${a.animal.name ? ` ${a.animal.name}` : ''}` : ''}<br /><span className="text-[13px] text-ink-3">{CATEGORY_LABEL[a.category]}{alertDetail(a.detail, a.animal) ? ` · ${alertDetail(a.detail, a.animal)}` : ''}</span></span>
            <span className="text-[13px] text-ink-3">{dueText(a, today)}</span>
          </Link>
        ))}
      </div>
    </>
  )
  const empty = p && !p.overdue.length && !p.today.length && !p.thisWeek.length && !p.later.length

  return (
    <Screen wide>
      <div className="flex items-center gap-2 px-4 pb-1.5 pt-3.5">
        <h1 className="m-0 flex-1 text-2xl font-bold">Alerts</h1>
        <button type="button" aria-label="Check for new alerts" onClick={refresh} className="flex h-11 w-11 items-center justify-center rounded-xl border-0 bg-transparent"><Icon name="check" /></button>
        {isOwner && <Link to="/settings/alert-rules" aria-label="Alert rules" className="flex h-11 w-11 items-center justify-center rounded-xl"><Icon name="filter" /></Link>}
        <Link to="/reminders/new" className="flex h-10 items-center rounded-[10px] bg-brand px-3.5 text-sm font-semibold text-white no-underline hover:text-white">+ Reminder</Link>
      </div>

      <div role="group" aria-label="Category" className="flex gap-2 overflow-x-auto px-4 pb-1 pt-2">
        <button type="button" aria-pressed={!category} onClick={() => set('category')} className={`chip ${!category ? 'chip-on' : ''}`}>All{counts ? ` ${counts.all}` : ''}</button>
        {CATS.filter((c) => !counts || counts.byCategory[c] || category === c).map((c) => (
          <button key={c} type="button" aria-pressed={category === c} onClick={() => set('category', category === c ? undefined : c)} className={`chip ${category === c ? 'chip-on' : ''}`}>
            {CATEGORY_LABEL[c]}{counts?.byCategory[c] ? ` ${counts.byCategory[c]}` : ''}
          </button>
        ))}
      </div>
      <div role="group" aria-label="Severity" className="flex gap-2 overflow-x-auto px-4 pb-1 pt-1.5">
        {SEVS.map((s) => (
          <button key={s} type="button" aria-pressed={severity === s} onClick={() => set('severity', severity === s ? undefined : s)} className={`chip flex h-8 items-center text-[13px] ${severity === s ? 'chip-on' : ''}`}>
            <span className="mr-1.5 inline-block h-2 w-2 rounded-full" style={{ background: SEVERITY_DOT[s] }} />{SEVERITY_LABEL[s]}{counts?.bySeverity[s] ? ` ${counts.bySeverity[s]}` : ''}
          </button>
        ))}
      </div>

      <div className="px-4 pb-4">
        <div className="mt-2"><LoadState loading={page.loading} error={page.error} onRetry={page.reload} empty={!p} /></div>
        {p && (
          <>
            {section('Overdue', p.overdue, 'text-danger-text')}
            {section('Today', p.today)}
            {compact('This week', p.thisWeek)}
            {compact('Later', p.later)}
            {empty && (
              <div className="mt-4 rounded-[14px] bg-white p-5 text-center">
                <Icon name="check" color="#1F5C3A" size={28} />
                <div className="mt-1 font-semibold">{category || severity ? 'Nothing open in this filter' : 'All clear — no open alerts'}</div>
                <div className="mt-1 text-sm text-ink-3">Alerts are checked every morning and when you open this page.</div>
              </div>
            )}
          </>
        )}
      </div>
      <Footer className="mt-auto" />
      <BottomNav active="more" />
    </Screen>
  )
}
