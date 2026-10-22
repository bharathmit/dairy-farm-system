import { Link } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { OwnerOnlyScreen, Stat, useMoney } from '../../components/money'
import { Icon } from '../../components/icons'
import { shortDate, useLoad } from '../../data/records'
import { rangeLabel, rupees } from '../../data/money'

const days = (n: number) => `${Math.floor(n)}${n % 1 ? '½' : ''}`

export default function Workers() {
  const { mo, isOwner } = useMoney()
  const page = useLoad(isOwner ? () => mo.workers() : null, [mo, isOwner])
  if (!isOwner) return <OwnerOnlyScreen title="Workers" back="/money" />
  const p = page.data
  return (
    <Screen>
      <TopBar title="Workers" back="/money" right={<Link to="/settings/team/new" className="flex h-10 items-center rounded-[10px] bg-brand px-3.5 text-sm font-semibold text-white no-underline hover:text-white">+ Add</Link>} />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <LoadState loading={page.loading} error={page.error} onRetry={page.reload} empty={!p} />
        {p && (
          <>
            <div className="text-sm text-ink-3">This week · {rangeLabel(p.week.from, p.week.to)}</div>
            <div className="grid grid-cols-3 gap-2.5">
              <Stat k="Workers" v={p.totals.workers} />
              <Stat k="Due now" v={<span className={p.totals.dueNow ? 'text-warn-ink' : ''}>{rupees(p.totals.dueNow)}</span>} />
              <Stat k="Paid this month" v={rupees(p.totals.paidThisMonth)} />
            </div>
            <Link to="/money/attendance" className="btn2 h-12 gap-2"><Icon name="list" color="#1F5C3A" />Mark this week's attendance</Link>
            {p.workers.length === 0 && (
              <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">No workers with wages yet. Add a team member and set a daily or monthly wage. <Link to="/settings/team" className="font-semibold">Team</Link></div>
            )}
            {p.workers.map((w) => (
              <div key={w.member.id} className="rounded-[14px] bg-white p-3.5">
                <div className="flex items-center gap-3">
                  <span aria-hidden="true" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-brand-soft text-[17px] font-bold text-brand">{w.member.name.slice(0, 1).toUpperCase()}</span>
                  <span className="min-w-0 flex-1"><b className="text-base">{w.member.name}</b>
                    <span className="block text-[13px] text-ink-3">{w.wageType === 'DAILY' ? `Daily wage · ${rupees(w.wageRate)} / day` : `Monthly · ${rupees(w.wageRate)} / month`} · {w.hasLogin ? 'has login' : 'no login'}</span></span>
                  {w.dueNow > 0 ? <span className="badge bg-warn-soft text-warn-ink">Due {rupees(w.dueNow)}</span>
                    : w.lastPaid ? <span className="badge bg-brand-soft text-brand">Paid to {shortDate(w.lastPaid.periodTo)}</span> : null}
                </div>
                <div className="mt-3 grid grid-cols-3 gap-2">
                  <div className="rounded-[10px] bg-ground px-2.5 py-2"><span className="block text-xs font-semibold text-ink-3">Present</span><b>{days(w.week.presentDays)} this week</b></div>
                  <div className="rounded-[10px] bg-ground px-2.5 py-2"><span className="block text-xs font-semibold text-ink-3">Hours</span><b>{w.week.hours} h</b></div>
                  <div className="rounded-[10px] bg-ground px-2.5 py-2"><span className="block text-xs font-semibold text-ink-3">Due</span><b className={w.dueNow ? 'text-warn-ink' : ''}>{rupees(w.dueNow)}</b></div>
                </div>
                {w.pendingNote && <div className="mt-2 text-[13px] text-warn-ink">{w.pendingNote}</div>}
                <div className="mt-2.5 flex gap-2">
                  <Link to="/money/attendance" className="btn2 h-11 flex-1 text-sm">Attendance</Link>
                  <Link to={`/money/pay?member=${w.member.id}`} className={`h-11 flex-1 text-sm ${w.dueNow > 0 ? 'btn' : 'btn2'}`}>{w.dueNow > 0 ? 'Mark paid' : 'Pay / advance'}</Link>
                </div>
              </div>
            ))}
            <div className="flex items-center gap-2 text-[13px] text-ink-3"><Icon name="gear" size={14} color="#56655B" />Wages are owner only. Workers don't see pay.</div>
          </>
        )}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
