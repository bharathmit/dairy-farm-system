import { useState } from 'react'
import { Link } from 'react-router-dom'
import { BottomNav, Footer, Screen, TopBar } from '../../components/ui'
import { LoadState, useRecords } from '../../components/records'
import { HEALTH_TONE, healthLabel, shortDate, todayIso, useLoad, type HealthRecord, type HealthType } from '../../data/records'

type Filter = 'ALL' | 'FOLLOW' | HealthType
const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: 'All' }, { value: 'FOLLOW', label: 'Follow-ups' }, { value: 'TREATMENT', label: 'Treatment' },
  { value: 'ILLNESS', label: 'Illness' }, { value: 'VACCINATION', label: 'Vaccination' }, { value: 'DEWORMING', label: 'Deworming' }, { value: 'INJURY', label: 'Injury' },
]

export function RecordRow({ r, first }: { r: HealthRecord; first?: boolean }) {
  const detail = [r.diagnosis || r.symptoms, [r.medicine, r.dose].filter(Boolean).join(' '), shortDate(r.date)].filter(Boolean).join(' · ')
  return (
    <Link to={`/health/${r.id}`} className={`block px-3.5 py-3 text-ink no-underline hover:text-ink ${first ? '' : 'border-t border-line-faint'}`}>
      <div className="flex items-center gap-2"><b className="flex-1 text-[15px]">{r.tag}{r.name ? ` ${r.name}` : ''}</b><span className={`badge font-bold ${HEALTH_TONE[r.type]}`}>{healthLabel(r.type)}</span></div>
      <div className="mt-1 text-[13px] text-ink-3">{detail}</div>
      {r.nextActionDate
        ? <div className={`mt-1 text-[13px] ${r.nextActionDone ? 'text-ink-3' : 'font-semibold text-info-text'}`}>{r.nextActionDone ? 'Done: ' : 'Next: '}{r.nextAction || 'follow-up'} · {shortDate(r.nextActionDate)}</div>
        : <div className="mt-1 text-[13px] text-ink-3">No follow-up</div>}
    </Link>
  )
}

export default function HealthHome() {
  const { rec } = useRecords()
  const today = todayIso()
  const ov = useLoad(() => rec.healthOverview(), [rec])
  const wd = useLoad(() => rec.withdrawals(), [rec])
  const [filter, setFilter] = useState<Filter>('ALL')
  const typed = filter !== 'ALL' && filter !== 'FOLLOW'
  const list = useLoad(typed ? () => rec.healthRecords({ type: filter as HealthType, size: 30 }) : null, [rec, filter])

  const o = ov.data
  const active = wd.data?.active ?? []
  const records = filter === 'ALL' ? o?.recent ?? [] : filter === 'FOLLOW' ? o?.dueFollowUps ?? [] : list.data?.items ?? []
  const stat = (k: string, v: number | undefined, tone: string, to?: string) => {
    const body = <><div className="text-xs font-semibold text-ink-3">{k}</div><div className={`mt-0.5 text-xl font-bold ${tone}`}>{v ?? '—'}</div></>
    return to ? <Link to={to} className="rounded-xl bg-white p-2.5 text-ink no-underline">{body}</Link> : <div className="rounded-xl bg-white p-2.5">{body}</div>
  }

  return (
    <Screen>
      <TopBar title="Health" back="/home" right={<>
        <Link to="/health/batch" className="mr-1.5 flex h-10 items-center rounded-[10px] border border-brand px-3 text-sm font-semibold text-brand no-underline">Batch</Link>
        <Link to="/health/new" className="flex h-10 items-center rounded-[10px] bg-brand px-3 text-sm font-semibold text-white no-underline hover:text-white">+ Record</Link>
      </>} />
      <div className="px-4 pb-4">
        <LoadState loading={ov.loading} error={ov.error} onRetry={ov.reload} empty={!o} />
        <div className="grid grid-cols-3 gap-2">
          {stat('Withdrawal', o?.inWithdrawal, 'text-warn-ink', '/health/withdrawals')}
          <button type="button" onClick={() => setFilter('FOLLOW')} className="rounded-xl border-0 bg-white p-2.5 text-left"><div className="text-xs font-semibold text-ink-3">Follow-ups</div><div className="mt-0.5 text-xl font-bold text-info-text">{o?.openFollowUps ?? '—'}</div></button>
          {stat('This month', o?.thisMonth, '')}
        </div>

        {active.length > 0 && (
          <Link to="/health/withdrawals" className="mt-3 flex items-center gap-3 rounded-xl bg-danger-soft px-3.5 py-3 text-danger-text no-underline hover:text-danger-text">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#8E2216" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 3c3 4 6 6.5 6 10a6 6 0 0 1-12 0c0-3.5 3-6 6-10z" /><path d="M5 5l14 14" /></svg>
            <span className="flex-1 text-[15px]"><b>{active.length} {active.length === 1 ? 'animal' : 'animals'} in milk withdrawal</b><br /><span className="text-[13px]">Do not sell milk · {active.slice(0, 4).map((w) => w.record.tag).join(', ')}{active.length > 4 ? '…' : ''}</span></span>
            <span className="font-bold">View</span>
          </Link>
        )}

        {o && o.dueFollowUps.length > 0 && filter === 'ALL' && (
          <>
            <div className="mt-4 flex items-baseline justify-between"><b className="text-[15px]">Follow-ups due</b><span className="text-[13px] text-ink-3">next 7 days</span></div>
            <div className="mt-2 flex flex-col gap-2">
              {o.dueFollowUps.slice(0, 5).map((r) => {
                const late = (r.nextActionDate ?? '') < today
                const now = r.nextActionDate === today
                return (
                  <Link key={r.id} to={`/health/${r.id}`} className={`flex min-h-11 items-center gap-3 rounded-xl px-3.5 py-3 no-underline ${late || now ? 'bg-warn-soft text-[#4A3210] hover:text-[#4A3210]' : 'bg-white text-ink hover:text-ink'}`}>
                    <span className={`h-2.5 w-2.5 rounded-full ${late || now ? 'bg-[#C2410C]' : 'bg-info-text'}`} />
                    <span className="flex-1 text-[15px]"><b>{r.tag}</b> {r.name} · {r.nextAction || 'follow-up'}</span>
                    <span className={`text-[13px] ${late || now ? 'font-bold text-warn-ink' : 'text-ink-3'}`}>{late ? 'Overdue' : now ? 'Today' : shortDate(r.nextActionDate)}</span>
                  </Link>
                )
              })}
            </div>
          </>
        )}

        <div className="mt-4 flex gap-2 overflow-x-auto pb-1">
          {FILTERS.map((f) => <button key={f.value} type="button" aria-pressed={filter === f.value} onClick={() => setFilter(f.value)} className={`chip ${filter === f.value ? 'chip-on' : ''}`}>{f.label}</button>)}
        </div>
        <div className="mt-3 flex items-baseline justify-between"><b className="text-[15px]">{filter === 'ALL' ? 'Recent records' : filter === 'FOLLOW' ? 'Open follow-ups' : `${FILTERS.find((f) => f.value === filter)?.label} records`}</b></div>
        {typed && <LoadState loading={list.loading} error={list.error} onRetry={list.reload} empty={!list.data} />}
        <div className="mt-2 overflow-hidden rounded-[14px] bg-white">
          {records.map((r, i) => <RecordRow key={r.id} r={r} first={i === 0} />)}
          {records.length === 0 && (o || list.data) && (
            <div className="px-3.5 py-5 text-center text-sm text-ink-3">No records yet. <Link to="/health/new" className="font-semibold">Add a health record</Link></div>
          )}
        </div>
      </div>
      <Footer className="mt-auto" />
      <BottomNav active="home" />
    </Screen>
  )
}
