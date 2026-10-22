import { Link } from 'react-router-dom'
import { BottomNav, Footer, Screen, TopBar } from '../../components/ui'
import { Icon, type IconName } from '../../components/icons'
import { LoadState } from '../../components/records'
import { CowRow, useBreeding } from '../../components/breeding'
import { CALENDAR_TONE, type CowStatus } from '../../data/breeding'
import { dayLabel, todayIso, useLoad } from '../../data/records'
import { useStore } from '../../data/store'

const QUICK: { to: string; icon: IconName; color: string; label: string }[] = [
  { to: '/breeding/heat-watch', icon: 'heart', color: '#A3271B', label: 'Heat watch' },
  { to: '/breeding/pregnancy-check', icon: 'check', color: '#1F4E87', label: 'Preg. check' },
  { to: '/breeding/calving', icon: 'leaf', color: '#1F5C3A', label: 'Calving' },
  { to: '/breeding/semen', icon: 'drop', color: '#7A4B0E', label: 'Semen' },
]

function Group({ title, list, tone, count, showMoney, note }: { title: string; list: CowStatus[]; tone?: string; count: string; showMoney: boolean; note?: string }) {
  if (!list.length) return null
  return (
    <div className="overflow-hidden rounded-[14px] bg-white">
      <div className={`flex items-center gap-2.5 px-3.5 py-3 text-[15px] font-bold ${tone ?? ''}`}><span className="flex-1">{title}</span><span className={`rounded-[10px] px-2 py-0.5 text-xs font-bold ${count}`}>{list.length}</span></div>
      {list.map((s) => <CowRow key={s.animal.id} s={s} showMoney={showMoney} />)}
      {note && <div className="border-t border-line-faint px-3.5 pb-3 pt-2 text-xs text-ink-3">{note}</div>}
    </div>
  )
}

export default function BreedingHome() {
  const { br, isOwner } = useBreeding()
  const { farm } = useStore()
  const ov = useLoad(() => br.overview(), [br])
  const o = ov.data
  const today = todayIso()
  const days = o ? [...new Set(o.calendar.map((c) => c.date))].sort() : []

  return (
    <Screen>
      <TopBar title="Breeding" back="/home" right={<Link to="/breeding/service/new" className="flex h-10 items-center rounded-[10px] bg-brand px-3.5 text-sm font-semibold text-white no-underline hover:text-white">+ Service</Link>} />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <div className="text-sm text-ink-3">{farm?.name}{o ? ` · ${o.breedable} breedable` : ''} · {dayLabel(today)}</div>
        <div className="grid grid-cols-4 gap-2">
          {QUICK.map((q) => (
            <Link key={q.to} to={q.to} className="relative flex min-h-[84px] flex-col items-start gap-1.5 rounded-[14px] bg-white p-3 text-ink no-underline hover:text-ink">
              <span className="flex h-9 w-9 items-center justify-center rounded-[10px] bg-ground"><Icon name={q.icon} size={20} color={q.color} /></span>
              <span className="text-sm font-semibold leading-tight">{q.label}</span>
              {q.to === '/breeding/heat-watch' && o && o.heatWatchCount > 0 && <span className="absolute right-2 top-2 rounded-lg bg-danger-soft px-1.5 text-[11px] font-bold text-danger-text">{o.heatWatchCount}</span>}
            </Link>
          ))}
        </div>
        <Link to="/calves" className="flex items-center gap-3 rounded-[14px] bg-white px-3.5 py-3 text-ink no-underline hover:text-ink">
          <Icon name="leaf" color="#1F5C3A" /><span className="flex-1 text-[15px] font-semibold">Calves</span><Icon name="chevron" size={18} color="#8A968D" />
        </Link>

        <LoadState loading={ov.loading} error={ov.error} onRetry={ov.reload} empty={!o} />
        {o && (
          <>
            <div>
              <div className="mb-2 flex items-baseline justify-between"><b className="text-[15px]">Next 14 days</b></div>
              {days.length === 0 ? <div className="rounded-xl bg-white px-3.5 py-3 text-sm text-ink-3">Nothing due in the next 14 days.</div> : (
                <div className="flex gap-2 overflow-x-auto pb-1">
                  {days.map((d) => (
                    <div key={d} className={`w-[72px] shrink-0 rounded-xl bg-white px-1.5 py-2 text-center ${d === today ? 'outline outline-2 outline-brand' : ''}`}>
                      <div className="text-xs font-semibold text-ink-3">{dayLabel(d).split(' ')[0]}</div>
                      <div className="text-lg font-bold">{Number(d.slice(8, 10))}</div>
                      {o.calendar.filter((c) => c.date === d).slice(0, 3).map((c, i) => (
                        <Link key={i} to={`/breeding/animals/${c.animal.id}`} className={`mt-1 block truncate rounded-md px-1 py-0.5 text-[11px] font-bold no-underline ${CALENDAR_TONE[c.kind].cls}`} title={`${CALENDAR_TONE[c.kind].label} · ${c.animal.tag}`}>
                          {CALENDAR_TONE[c.kind].label} {c.animal.tag}
                        </Link>
                      ))}
                    </div>
                  ))}
                </div>
              )}
            </div>
            <div className="-mb-1.5 text-xs font-bold uppercase tracking-[.06em] text-ink-3">By status</div>
            <Group title="Overdue to calve" list={o.groups.overdue} tone="bg-danger-soft text-danger-text" count="bg-white" showMoney={isOwner} />
            <Group title="Due in 7 days" list={o.groups.dueIn7} tone="bg-warn-soft text-warn-ink" count="bg-white" showMoney={isOwner} />
            <Group title="Open / not bred" list={o.groups.open} count="bg-ground text-ink-2" showMoney={isOwner}
              note={`Tap a cow for days open, missed heats, failed services${isOwner ? ', spend' : ''} and a keep-or-sell suggestion.`} />
            <Group title="Served · awaiting check" list={o.groups.served} count="bg-ground text-ink-2" showMoney={isOwner} />
            <Group title="Pregnant" list={o.groups.pregnant} count="bg-brand-soft text-brand" showMoney={isOwner} />
            <Group title="Dry" list={o.groups.dry} count="bg-ground text-ink-2" showMoney={isOwner} />
            {Object.values(o.groups).every((g) => g.length === 0) && (
              <div className="rounded-[14px] bg-white px-4 py-5 text-center text-sm text-ink-3">No breedable females yet. Add heifers or cows in the <Link to="/animals" className="font-semibold">herd</Link>.</div>
            )}
          </>
        )}
      </div>
      <Footer className="mt-auto" />
      <BottomNav active="home" />
    </Screen>
  )
}
