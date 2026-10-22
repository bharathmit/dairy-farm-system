import { Fragment, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { LoadState } from '../../components/records'
import { Badge, refreshAlertCount, SnoozeSheet, useAlerts } from '../../components/alerts'
import { useStore } from '../../data/store'
import {
  actionLink, CATEGORY_LABEL, CATEGORY_TONE, REMINDER_TYPES, REPEATS, SEVERITY_LABEL, SEVERITY_TONE,
} from '../../data/alerts'
import { dateTimeLabel, shortDate, useLoad } from '../../data/records'
import { errorMessage } from '../../lib/api'

/** One alert: why it was raised, the facts behind it and what to do. */
export default function AlertDetail() {
  const { id } = useParams()
  const { al, farmId, isOwner } = useAlerts()
  const { notify } = useStore()
  const nav = useNavigate()
  const alert = useLoad(() => al.alert(id ?? ''), [al, id])
  const [snooze, setSnooze] = useState(false)
  const a = alert.data
  const link = a ? actionLink(a) : null
  const closed = a && a.status !== 'OPEN'

  const act = async (fn: () => Promise<unknown>, msg: string) => {
    try { await fn(); notify(msg); refreshAlertCount(farmId); nav('/alerts') } catch (e) { notify(errorMessage(e)) }
  }
  const canDismiss = a && (a.source !== 'RULE' || isOwner) && a.ruleCode !== 'WITHDRAWAL'

  return (
    <Screen>
      <TopBar title="Alert" back="/alerts" right={a && !closed ? (
        <button type="button" aria-label="Snooze" onClick={() => setSnooze(true)} className="flex h-11 w-11 items-center justify-center border-0 bg-transparent"><Icon name="bell" /></button>
      ) : undefined} />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <LoadState loading={alert.loading} error={alert.error} onRetry={alert.reload} empty={!a} />
        {a && (
          <>
            <div>
              <div className="flex flex-wrap gap-1.5">
                <Badge className={SEVERITY_TONE[a.severity]}>{SEVERITY_LABEL[a.severity]}</Badge>
                <Badge className={CATEGORY_TONE[a.category]}>{CATEGORY_LABEL[a.category]}</Badge>
                {(a.overdueDays ?? 0) > 0 && <Badge className="bg-danger-soft text-danger-text">{a.overdueDays} d overdue</Badge>}
                {closed && <Badge className="bg-ground text-ink-2">{a.status === 'DONE' ? (a.autoClosed ? 'Resolved' : 'Done') : 'Dismissed'}</Badge>}
              </div>
              <h2 className="m-0 mt-2.5 text-2xl font-bold">{a.title}</h2>
              <div className="mt-1 text-sm text-ink-3">
                {a.source === 'REMINDER' ? 'Reminder' : a.source === 'PROCUREMENT' ? 'Procurement' : 'Raised by the daily check'}
                {a.dueDate ? ` · due ${shortDate(a.dueDate)}${a.dueTime ? ` ${a.dueTime.slice(0, 5)}` : ''}` : ''}
                {a.snoozedUntil ? ` · snoozed until ${dateTimeLabel(a.snoozedUntil)}` : ''}
              </div>
            </div>

            {a.animal && (
              <Link to={`/animals/${a.animal.id}`} className="flex items-center gap-3 rounded-[14px] bg-white p-3.5 text-ink no-underline hover:text-ink">
                <span className="flex h-[52px] w-[52px] shrink-0 items-center justify-center rounded-xl bg-brand-soft text-[13px] font-bold text-brand">{a.animal.breed?.slice(0, 3) ?? 'Cow'}</span>
                <span className="flex-1"><b className="text-base">{a.animal.tag}</b> {a.animal.name}<br /><span className="text-[13px] text-ink-3">{[a.animal.category, a.animal.breed].filter(Boolean).join(' · ')}</span></span>
                <Icon name="chevron" size={20} color="#56655B" />
              </Link>
            )}

            <div className="rounded-[14px] bg-white p-3.5">
              <div className="mb-2 text-xs font-bold uppercase tracking-[.06em] text-ink-3">{a.source === 'REMINDER' ? 'Reminder' : 'Why you see this'}</div>
              <div className="text-[15px] leading-snug">{a.why || a.detail || a.notes || 'No details.'}</div>
              {a.source === 'REMINDER' && a.notes && a.notes !== a.why && <div className="mt-2 text-sm text-ink-2">{a.notes}</div>}
              {!!a.facts?.length && (
                <dl className="mb-0 mt-3 grid grid-cols-[120px_minmax(0,1fr)] gap-y-2 text-sm">
                  {a.facts.map((f, i) => <Fragment key={i}><dt className="text-ink-3">{f.label}</dt><dd className="m-0 font-bold">{f.value}</dd></Fragment>)}
                </dl>
              )}
              {a.source === 'REMINDER' && (
                <div className="mt-3 text-sm text-ink-3">{REMINDER_TYPES.find((t) => t.value === a.type)?.label ?? 'Other'} · {REPEATS.find((r) => r.value === (a.repeat ?? 'NONE'))?.label}</div>
              )}
              {a.source === 'RULE' && isOwner && <Link to="/settings/alert-rules" className="mt-1 inline-flex min-h-11 items-center text-sm font-semibold no-underline">Change this rule</Link>}
              {a.source === 'REMINDER' && !closed && <Link to={`/reminders/${a.id}`} className="mt-1 inline-flex min-h-11 items-center text-sm font-semibold no-underline">Edit reminder</Link>}
            </div>

            <div className="rounded-[14px] bg-white p-3.5">
              <div className="mb-2 text-xs font-bold uppercase tracking-[.06em] text-ink-3">Who sees it</div>
              <div className="text-sm">{a.workerVisible ? 'Owners and workers' : 'Owner only'}</div>
              {a.doneBy && <div className="mt-1 text-sm text-ink-3">Done by {a.doneBy.name}{a.doneAt ? ` · ${dateTimeLabel(a.doneAt)}` : ''}</div>}
            </div>
          </>
        )}
      </div>
      {a && !closed && (
        <div className="sticky bottom-0 mt-auto flex flex-col gap-2.5 border-t border-line-soft bg-white px-4 pb-3 pt-3.5">
          {link && a.action && <Link to={link} className="btn">{a.action.label}</Link>}
          <div className="grid grid-cols-2 gap-2.5">
            <button type="button" className="btn2" onClick={() => act(() => al.done(a.id), 'Marked done')}>{a.ruleCode === 'WITHDRAWAL' ? 'Seen' : 'Mark done'}</button>
            {canDismiss
              ? <button type="button" className="btn2 text-danger" onClick={() => act(() => al.dismiss(a.id), 'Dismissed')}>Dismiss</button>
              : <button type="button" className="btn2" onClick={() => setSnooze(true)}>Snooze</button>}
          </div>
        </div>
      )}
      <Footer />
      {snooze && a && <SnoozeSheet onClose={() => setSnooze(false)} onPick={(until) => { setSnooze(false); void act(() => al.snooze(a.id, until), 'Snoozed') }} />}
    </Screen>
  )
}
