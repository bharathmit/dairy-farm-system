import { useState } from 'react'
import { Link } from 'react-router-dom'
import { BottomNav, Footer, Screen, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { LoadState } from '../../components/records'
import { StockRow, Tile, useFeed } from '../../components/feed'
import { useStore } from '../../data/store'
import { dayLabel, todayIso, useLoad } from '../../data/records'
import { inr0, time12 } from '../../data/breeding'
import { kg, STOCK_FILTERS, type SessionInfo, type Ses, type StockItem } from '../../data/feed'

/** Stock on hand chips: Reorder items first (instead of All), then the category groups. */
const isReorder = (s: StockItem) => s.status === 'REORDER' || s.status === 'PREPARE'
const HOME_FILTERS: { key: string; label: string; match: (s: StockItem) => boolean }[] = [
  { key: 'REORDER', label: 'Reorder items', match: isReorder },
  ...STOCK_FILTERS.filter((x) => x.key !== 'ALL').map((x) => ({ key: x.key, label: x.label, match: (s: StockItem) => x.match(s.item.category) })),
]

function SessionRow({ ses, info }: { ses: Ses; info?: SessionInfo }) {
  const label = ses === 'AM' ? '6 AM feeding' : '6 PM feeding'
  const to = `/feed/log?session=${ses}`
  if (info?.logged) {
    return (
      <Link to={to} className="flex items-center gap-3 rounded-xl bg-white px-3.5 py-3 text-ink no-underline hover:text-ink">
        <span className="h-2.5 w-2.5 rounded-full bg-brand" /><span className="flex-1 text-[15px]"><b>{label}</b> · done · {kg(info.kg)} kg</span>
        <span className="text-[13px] text-ink-3">{[time12(info.time), info.by].filter(Boolean).join(' · ')}</span>
      </Link>
    )
  }
  return (
    <Link to={to} className="flex items-center gap-3 rounded-xl bg-warn-soft px-3.5 py-3 text-warn-text no-underline hover:text-warn-text">
      <span className="h-2.5 w-2.5 rounded-full bg-[#C2410C]" /><span className="flex-1 text-[15px]"><b>{label}</b> · not logged yet</span>
      <span className="font-bold text-warn-ink">Log</span>
    </Link>
  )
}

export default function FeedHome() {
  const { fd, isOwner } = useFeed()
  const { farm } = useStore()
  const today = todayIso()
  const ov = useLoad(() => fd.overview(today), [fd, today])
  const [filter, setFilter] = useState('REORDER')
  const o = ov.data
  const items = o?.items ?? []
  const f = HOME_FILTERS.find((x) => x.key === filter) ?? HOME_FILTERS[0]
  const shown = items.filter(f.match)

  return (
    <Screen>
      <TopBar title="Feed" back="/home" right={isOwner ? <Link to="/feed/items/new" className="flex h-10 items-center rounded-[10px] border border-line bg-white px-3.5 text-sm font-semibold text-ink no-underline">+ Item</Link> : undefined} />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <div className="text-sm text-ink-3">Today · {dayLabel(today)} · {farm?.name}</div>
        <LoadState loading={ov.loading} error={ov.error} onRetry={ov.reload} empty={!o} />
        {o && (
          <>
            <div className="flex flex-col gap-2"><SessionRow ses="AM" info={o.sessions.AM} /><SessionRow ses="PM" info={o.sessions.PM} /></div>
            <div className="grid grid-cols-3 gap-2.5">
              <Tile k="Green / day" v={`${kg(o.needPerDay.green, 0)} kg`} to="/feed/need" />
              <Tile k="Dry / day" v={`${kg(o.needPerDay.dry, 0)} kg`} to="/feed/need" />
              <Tile k="Concentrate" v={`${kg(o.needPerDay.concentrate, 0)} kg`} to="/feed/need" />
            </div>
            <div className="-mt-1 flex items-center justify-between text-[13px] text-ink-3">
              <span>{isOwner && o.plannedCostPerDay != null ? `Planned cost today ${inr0(o.plannedCostPerDay)}` : 'From the ration plan'}</span>
              <Link to="/feed/ration" className="font-semibold">Ration plan</Link>
            </div>
          </>
        )}
        <div className="grid grid-cols-3 gap-2">
          <Link to="/feed/buy" className="flex h-[50px] items-center justify-center gap-1.5 rounded-xl border border-line bg-white text-[15px] font-semibold text-ink no-underline"><Icon name="download" size={18} />Buy</Link>
          <Link to="/feed/log" className="flex h-[50px] items-center justify-center gap-1.5 rounded-xl bg-brand text-[15px] font-semibold text-white no-underline hover:text-white"><Icon name="leaf" size={18} color="#fff" />Feed</Link>
          <Link to="/feed/batch" className="flex h-[50px] items-center justify-center gap-1.5 rounded-xl border border-line bg-white text-[15px] font-semibold text-ink no-underline"><Icon name="list" size={18} />Batch</Link>
        </div>
        <div className="grid grid-cols-2 gap-2 text-sm">
          <Link to="/feed/formulas" className="flex h-11 items-center justify-center rounded-xl bg-white font-semibold no-underline">Concentrate formulas</Link>
          <Link to="/feed/ration-rules" className="flex h-11 items-center justify-center rounded-xl bg-white font-semibold no-underline">Ration rules</Link>
        </div>

        {o && (
          <>
            <div className="mt-1.5 flex items-center"><b className="flex-1 text-[15px]">Stock on hand</b>{o.reorderCount > 0 && <span className="badge bg-danger-soft text-danger-text">{o.reorderCount} to reorder</span>}</div>
            <div className="flex gap-2 overflow-x-auto pb-1">
              {HOME_FILTERS.map((x) => (
                <button key={x.key} type="button" aria-pressed={filter === x.key} onClick={() => setFilter(x.key)} className={`chip ${filter === x.key ? 'chip-on' : ''}`}>
                  {x.label} {items.filter(x.match).length}
                </button>
              ))}
            </div>
            {shown.length > 0 ? (
              <div className="overflow-hidden rounded-[14px] bg-white">{shown.map((s, i) => <StockRow key={s.item.id} s={s} first={i === 0} />)}</div>
            ) : (
              <div className="rounded-[14px] bg-white px-4 py-5 text-center text-sm text-ink-3">
                {items.length ? (filter === 'REORDER' ? 'Nothing to reorder right now.' : 'No items in this group.') : <>No feed items yet. {isOwner && <Link to="/feed/items/new" className="font-semibold">Add the first one</Link>}</>}
              </div>
            )}
            <div className="text-xs text-ink-3">Bar = days of stock left against the planned daily need (full = 30 days or more).</div>
          </>
        )}
      </div>
      <Footer className="mt-auto" />
      <BottomNav active="more" />
    </Screen>
  )
}
