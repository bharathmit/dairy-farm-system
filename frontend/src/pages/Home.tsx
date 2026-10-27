import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { BottomNav, Footer, LogoMark, Screen } from '../components/ui'
import { Icon } from '../components/icons'
import AccountButton from '../components/AccountButton'
import { LoadState } from '../components/records'
import { alertDetail, Badge, BarnCard, BarnSheet, ProgressRing, useAlertCount, useAlerts } from '../components/alerts'
import { useStore } from '../data/store'
import { dayLabel, L, shortDate, todayIso, useLoad } from '../data/records'
import { changePct } from '../data/money'
import { TodayMoney } from '../components/money'
import { SEVERITY_DOT, taskLink, type HomeTask, type TaskStatus } from '../data/alerts'

const DOT: Record<TaskStatus, string> = { DONE: '#1F5C3A', DUE: '#C2410C', LATE: '#A3271B', PENDING: '#C9D0C6' }

function TaskRow({ t, first }: { t: HomeTask; first?: boolean }) {
  return (
    <Link to={taskLink(t)} className={`flex min-h-12 items-center gap-3 py-1.5 text-ink no-underline hover:text-ink ${first ? '' : 'border-t border-line-faint'}`}>
      <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: DOT[t.status] }} />
      <span className="flex-1 text-[15px]"><b>{t.title}</b>{t.detail ? ` · ${t.detail}` : ''}</span>
      {t.status === 'LATE' ? <Badge className="bg-danger-soft text-danger-text">{t.time ?? 'Late'}</Badge>
        : t.status === 'DUE' ? <span className="text-sm font-bold text-warn-ink">{t.time ?? 'Start'}</span>
          : t.time ? <span className="text-[13px] text-ink-3">{t.time}</span> : null}
    </Link>
  )
}

function SectionHead({ title, children }: { title: string; children?: ReactNode }) {
  return <div className="mb-2 mt-[18px] flex items-center justify-between"><b className="text-base">{title}</b>{children}</div>
}
const headLink = 'text-sm font-semibold no-underline'

function greetingWord() {
  const h = new Date().getHours()
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'
}

/** Role-based home: the owner's dashboard or the worker's day. */
export default function Home() {
  const { farm } = useStore()
  const { al, isOwner } = useAlerts()
  const home = useLoad(() => al.home(), [al])
  const open = useAlertCount()
  const [barnSheet, setBarnSheet] = useState(false)
  const h = home.data
  const name = h?.greeting ?? ''
  const title = isOwner ? `${greetingWord()}${name ? `, ${name}` : ''}` : `Vanakkam${name ? `, ${name}` : ''}`
  const sub = `${farm?.name ?? ''} · ${dayLabel(h?.date ?? todayIso())}${isOwner ? '' : ' · Worker'}`

  const barnBlock = h && (
    <>
      <SectionHead title="Barn heat stress">
        <button type="button" onClick={() => setBarnSheet(true)} className="h-9 border-0 bg-transparent p-0 text-sm font-semibold text-brand">{h.barn ? 'Update reading' : 'Add reading'}</button>
      </SectionHead>
      {h.barn ? <BarnCard barn={h.barn} compact={!isOwner} /> : <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">No barn reading today. Add temperature and humidity to see heat stress.</div>}
    </>
  )
  const checklistCard = h && (
    <Link to="/checklist" className="mt-2.5 flex items-center gap-3 rounded-[14px] bg-white p-3.5 text-ink no-underline hover:text-ink">
      <ProgressRing done={h.checklist.done} total={h.checklist.total} />
      <div className="flex-1"><b className="text-[15px]">{isOwner ? 'Daily checklist' : "Today's checklist"}</b><div className="text-[13px] text-ink-3">{h.checklist.done} of {h.checklist.total} done · {Math.max(0, h.checklist.total - h.checklist.done)} remaining</div></div>
      <Icon name="chevron" size={20} color="#56655B" />
    </Link>
  )
  const tile = 'rounded-xl bg-white p-3 text-ink no-underline hover:text-ink'

  return (
    <Screen wide>
      <div className="flex items-center gap-2.5 px-4 pb-1.5 pt-3.5">
        <LogoMark />
        <div className="min-w-0 flex-1"><h1 className="m-0 text-xl font-bold leading-tight">{title}</h1><div className="text-[13px] text-ink-3">{sub}</div></div>
        <Link to="/alerts" aria-label={`Alerts, ${open} open`} className="relative flex h-11 w-11 items-center justify-center rounded-xl">
          <Icon name="bell" />{open > 0 && <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-[#C2410C]" />}
        </Link>
        <AccountButton />
      </div>
      <div className="px-4 pb-4 pt-2">
        <LoadState loading={home.loading} error={home.error} onRetry={home.reload} empty={!h} />
        {h && isOwner && (
          <>
            <div className="grid grid-cols-2 gap-2.5 md:grid-cols-4">
              <Link to="/milk" className={tile}>
                <div className="text-xs font-semibold text-ink-3">Today's milk</div><div className="mt-0.5 text-[22px] font-bold">{L(h.tiles.milkToday)} L</div>
                {h.tiles.milkChangePct != null && <div className={`mt-0.5 text-xs font-semibold ${h.tiles.milkChangePct < 0 ? 'text-danger' : 'text-brand'}`}>{changePct(h.tiles.milkChangePct)} {h.tiles.milkCompareNote || 'vs yesterday'}</div>}
              </Link>
              <Link to="/animals" className={tile}>
                <div className="text-xs font-semibold text-ink-3">Milking cows</div><div className="mt-0.5 text-[22px] font-bold">{h.tiles.milkingCows}</div>
                <div className="mt-0.5 text-xs font-semibold text-ink-3">{h.tiles.dryCows} dry · {h.tiles.herd} in herd</div>
              </Link>
              <Link to="/alerts" className={tile}>
                <div className="text-xs font-semibold text-ink-3">Alerts open</div><div className={`mt-0.5 text-[22px] font-bold ${h.tiles.alertsOpen ? 'text-warn-ink' : ''}`}>{h.tiles.alertsOpen}</div>
                <div className={`mt-0.5 text-xs font-semibold ${h.tiles.alertsOverdue ? 'text-danger-text' : 'text-ink-3'}`}>{h.tiles.alertsOverdue ? `${h.tiles.alertsOverdue} overdue` : 'none overdue'}</div>
              </Link>
              <Link to="/money" className={tile}>
                <div className="text-xs font-semibold text-ink-3">{new Date().toLocaleDateString('en-GB', { month: 'long' })} margin</div>
                <div className="mt-0.5 text-[22px] font-bold">{h.tiles.marginMonthPct != null ? `${Math.round(h.tiles.marginMonthPct)} %` : '—'}</div>
                {h.tiles.marginMonthPct != null && h.tiles.marginAlertPct != null && (
                  <div className={`mt-0.5 text-xs font-semibold ${h.tiles.marginMonthPct < h.tiles.marginAlertPct ? 'text-danger' : 'text-brand'}`}>{h.tiles.marginMonthPct < h.tiles.marginAlertPct ? 'below' : 'above'} {h.tiles.marginAlertPct} % target</div>
                )}
              </Link>
            </div>
            <TodayMoney />

            {/* Laptop: tasks on the left, herd lists on the right. */}
            <div className="lg:grid lg:grid-cols-2 lg:gap-x-4">
            <div>
            <SectionHead title="Today's tasks"><Link to="/checklist" className={headLink}>Checklist</Link></SectionHead>
            <div className="rounded-[14px] bg-white px-3.5 py-1">
              {h.tasks.length === 0 ? <div className="py-3 text-sm text-ink-3">Nothing due today.</div> : h.tasks.map((t, i) => <TaskRow key={`${t.key}-${i}`} t={t} first={i === 0} />)}
            </div>
            {checklistCard}
            {barnBlock}
            </div>
            <div>
            <SectionHead title="Top producers · 7 d avg"><Link to="/reports/compare" className={headLink}>Compare</Link></SectionHead>
            <div className="rounded-[14px] bg-white px-3.5 py-1">
              {!h.topProducers?.length ? <div className="py-3 text-sm text-ink-3">No milk logged in the last 7 days.</div> : h.topProducers.map((p, i, all) => (
                <Link key={p.animal.id} to={`/animals/${p.animal.id}`} className={`flex min-h-12 items-center gap-3 text-ink no-underline hover:text-ink ${i ? 'border-t border-line-faint' : ''}`}>
                  <span className="w-5 font-bold text-ink-3">{i + 1}</span>
                  <span className="min-w-0 flex-1 truncate"><b>{p.animal.tag}</b> {p.animal.name}</span>
                  <span className="h-1.5 w-[90px] rounded bg-brand-soft"><span className="block h-1.5 rounded bg-brand" style={{ width: `${Math.round((p.avgLitres7d / (all[0].avgLitres7d || 1)) * 100)}%` }} /></span>
                  <b className="w-14 text-right">{L(p.avgLitres7d)} L</b>
                </Link>
              ))}
            </div>

            <SectionHead title="Upcoming calvings"><Link to="/breeding" className={headLink}>Breeding</Link></SectionHead>
            <div className="rounded-[14px] bg-white px-3.5 py-1">
              {!h.upcomingCalvings?.length ? <div className="py-3 text-sm text-ink-3">No calvings due soon.</div> : h.upcomingCalvings.map((c, i) => (
                <Link key={c.animal.id} to={`/breeding/animals/${c.animal.id}`} className={`flex min-h-12 items-center gap-3 text-ink no-underline hover:text-ink ${i ? 'border-t border-line-faint' : ''}`}>
                  <span className="flex-1"><b>{c.animal.tag}</b> {c.animal.name}{c.note ? <span className="text-[13px] text-ink-3"> · {c.note}</span> : null}</span>
                  <Badge className={c.days <= 7 ? 'bg-warn-soft text-warn-ink' : 'bg-info-soft text-info-text'}>{shortDate(c.dueDate)} · {c.days} d</Badge>
                </Link>
              ))}
            </div>
            </div>
            </div>
          </>
        )}

        {h && !isOwner && (
          <>
            <div className="flex flex-col gap-2">
              {h.tasks.filter((t) => t.key === 'MILK_AM' || t.key === 'MILK_PM').map((t) => (
                <Link key={t.key} to={taskLink(t)} className={`flex min-h-16 items-center gap-3 rounded-[14px] p-3.5 no-underline ${t.status === 'DONE' ? 'bg-white text-ink hover:text-ink' : 'bg-warn-soft text-[#4A3210] hover:text-[#4A3210]'}`}>
                  <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: DOT[t.status] }} />
                  <span className="flex-1 text-base"><b>{t.title}</b>{t.detail ? <><br /><span className="text-[13px]">{t.detail}</span></> : null}</span>
                  {t.status !== 'DONE' && <span className="flex h-11 items-center rounded-[10px] bg-brand px-4 text-sm font-semibold text-white">Start</span>}
                </Link>
              ))}
            </div>

            <SectionHead title="Health reminders"><Link to="/alerts" className={headLink}>All</Link></SectionHead>
            <div className="rounded-[14px] bg-white px-3.5 py-1">
              {!h.healthReminders?.length ? <div className="py-3 text-sm text-ink-3">No health reminders today.</div> : h.healthReminders.map((a, i) => (
                <Link key={a.id} to={`/alerts/${a.id}`} className={`flex min-h-[52px] items-center gap-3 py-1.5 text-ink no-underline hover:text-ink ${i ? 'border-t border-line-faint' : ''}`}>
                  <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: SEVERITY_DOT[a.severity] }} />
                  <span className="flex-1 text-[15px]"><b>{a.title}</b>{a.animal ? ` · ${a.animal.tag}${a.animal.name ? ` ${a.animal.name}` : ''}` : ''}{alertDetail(a.detail, a.animal) ? <><br /><span className="text-[13px] text-ink-3">{alertDetail(a.detail, a.animal)}</span></> : null}</span>
                  {(a.overdueDays ?? 0) > 0 ? <Badge className="bg-danger-soft text-danger-text">{a.overdueDays} d late</Badge> : <Badge className="bg-warn-soft text-warn-ink">Today</Badge>}
                </Link>
              ))}
            </div>

            <SectionHead title="Feeding reminders" />
            <div className="rounded-[14px] bg-white px-3.5 py-1">
              {h.tasks.filter((t) => t.key === 'FEED_AM' || t.key === 'FEED_PM').map((t, i) => <TaskRow key={t.key} t={t} first={i === 0} />)}
              {(h.feedingReminders ?? []).filter((f) => !/^feed(ing)? (am|pm)\b/i.test(f.title)).map((f, i) => (
                <div key={i} className="flex min-h-12 items-center gap-3 border-t border-line-faint py-1.5">
                  <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: DOT[f.status] }} />
                  <span className="flex-1 text-[15px]"><b>{f.title}</b>{f.detail ? <><br /><span className="text-[13px] text-ink-3">{f.detail}</span></> : null}</span>
                </div>
              ))}
            </div>
            {checklistCard}
            {barnBlock}
          </>
        )}

        <SectionHead title="More" />
        <div className="grid grid-cols-2 gap-2.5 md:grid-cols-4">
          {[
            { to: '/milk', icon: 'drop' as const, color: '#1F5C3A', label: 'Milk' },
            { to: '/health', icon: 'health' as const, color: '#A3271B', label: 'Health' },
            { to: '/breeding', icon: 'heart' as const, color: '#7A4B0E', label: 'Breeding' },
            { to: '/calves', icon: 'leaf' as const, color: '#1F5C3A', label: 'Calves' },
            { to: '/feed', icon: 'leaf' as const, color: '#7A4B0E', label: 'Feed' },
            ...(isOwner ? [
              { to: '/money', icon: 'farm' as const, color: '#1F5C3A', label: 'Money' },
              { to: '/reports', icon: 'list' as const, color: '#1F4E87', label: 'Reports' },
            ] : []),
          ].map((l) => (
            <Link key={l.to} to={l.to} className="flex min-h-12 items-center gap-2 rounded-[14px] bg-white px-3.5 text-ink no-underline hover:text-ink"><Icon name={l.icon} color={l.color} /><b className="flex-1">{l.label}</b><Icon name="chevron" size={18} color="#8A968D" /></Link>
          ))}
        </div>
      </div>
      <Footer className="mt-auto" />
      <BottomNav active="home" />
      {barnSheet && <BarnSheet onClose={() => setBarnSheet(false)} onSaved={(b) => { setBarnSheet(false); home.setData((cur) => (cur ? { ...cur, barn: b } : cur)) }} />}
    </Screen>
  )
}
