import { Link } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { LoadState, useRecords } from '../../components/records'
import { dateTimeLabel, L, shortDate, useLoad, type Withdrawal } from '../../data/records'

function remaining(w: Withdrawal) {
  const ms = new Date(w.endsAt).getTime() - Date.now()
  const days = Math.ceil(ms / 86400000)
  const startMs = new Date(w.record.date).getTime()
  const total = new Date(w.endsAt).getTime() - startMs
  const done = total > 0 ? Math.min(100, Math.max(0, ((Date.now() - startMs) / total) * 100)) : 100
  return { days, done }
}

export default function WithdrawalList() {
  const { rec } = useRecords()
  const wd = useLoad(() => rec.withdrawals(), [rec])
  const active = wd.data?.active ?? []
  const ended = wd.data?.ended ?? []
  return (
    <Screen>
      <TopBar title="Milk withdrawal" back="/health" />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <div className="flex items-start gap-3 rounded-xl bg-danger-soft p-3.5 text-danger-text">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#8E2216" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="shrink-0"><path d="M12 3c3 4 6 6.5 6 10a6 6 0 0 1-12 0c0-3.5 3-6 6-10z" /><path d="M5 5l14 14" /></svg>
          <div><b className="text-base">Do not sell milk from these animals</b><div className="mt-0.5 text-sm">Milk them separately and discard. Not counted in dairy sale totals.</div></div>
        </div>
        <LoadState loading={wd.loading} error={wd.error} onRetry={wd.reload} empty={!wd.data} />
        {wd.data && (
          <>
            <div className="flex items-baseline justify-between"><b className="text-[15px]">In withdrawal now</b><span className="text-[13px] text-ink-3">{active.length} {active.length === 1 ? 'animal' : 'animals'}</span></div>
            {active.length === 0 && <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">No animal is in milk withdrawal. All milk can be sold.</div>}
            {active.map((w) => {
              const { days, done } = remaining(w)
              const r = w.record
              return (
                <Link key={r.id} to={`/health/${r.id}?back=/health/withdrawals`} className="block rounded-[14px] bg-white p-3.5 text-ink no-underline hover:text-ink">
                  <div className="flex items-center gap-2"><b className="flex-1 text-base">{r.tag}{r.name ? ` ${r.name}` : ''}</b>
                    <span className={`badge font-bold ${days <= 1 ? 'bg-warn-soft text-warn-ink' : 'bg-danger-soft text-danger-text'}`}>{days <= 1 ? 'ends within a day' : `${days} days left`}</span></div>
                  <div className="mt-1 text-[13px] text-ink-3">{[[r.medicine, r.dose].filter(Boolean).join(' '), r.diagnosis, `started ${shortDate(r.date)}`].filter(Boolean).join(' · ')}</div>
                  <div className="mt-2.5 flex justify-between text-sm"><span>Ends</span><b>{dateTimeLabel(w.endsAt)}</b></div>
                  <div className="mt-2 h-1.5 overflow-hidden rounded bg-[#F1F2ED]"><div className={`h-1.5 ${days <= 1 ? 'bg-[#C2410C]' : 'bg-danger'}`} style={{ width: `${done}%` }} /></div>
                  <div className="mt-2 text-[13px] font-semibold text-warn-ink">Milk discarded so far: {L(w.discardedLitres ?? 0)} L</div>
                </Link>
              )
            })}
            <div className="mt-1 flex items-baseline justify-between"><b className="text-[15px]">Ended recently</b><span className="text-[13px] text-ink-3">last 14 days</span></div>
            <div className="overflow-hidden rounded-[14px] bg-white">
              {ended.length === 0 && <div className="px-3.5 py-4 text-sm text-ink-3">None.</div>}
              {ended.map((w, i) => (
                <Link key={w.record.id} to={`/health/${w.record.id}?back=/health/withdrawals`} className={`flex min-h-[52px] items-center gap-3 px-3.5 py-2.5 text-ink no-underline hover:text-ink ${i ? 'border-t border-line-faint' : ''}`}>
                  <span className="h-2.5 w-2.5 shrink-0 rounded-full bg-brand" />
                  <span className="flex-1 text-[15px]"><b>{w.record.tag}</b> {w.record.name}<div className="text-[13px] text-ink-3">{w.record.medicine || 'Treatment'} · {L(w.discardedLitres ?? 0)} L discarded</div></span>
                  <span className="text-[13px] text-ink-3">ended {shortDate(w.endsAt)}</span>
                </Link>
              ))}
            </div>
          </>
        )}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
