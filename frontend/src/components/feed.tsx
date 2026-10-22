/* eslint-disable react-refresh/only-export-components */
import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useStore } from '../data/store'
import { categoryLabel, days, feedApi, kg, STATUS_BADGE, type FeedItem, type StockItem } from '../data/feed'

/** Feed API calls for the chosen farm, and whether money and owner actions are shown. */
export function useFeed() {
  const { farm } = useStore()
  const farmId = farm?.id ?? ''
  const fd = useMemo(() => feedApi(farmId), [farmId])
  return { fd, farmId, isOwner: farm?.role === 'Owner' }
}

const barColor = (s: StockItem) => (s.status === 'REORDER' || s.status === 'PREPARE' ? 'bg-danger' : s.status === 'LOW' ? 'bg-[#C2410C]' : 'bg-brand')
const daysColor = (s: StockItem) => (s.status === 'REORDER' || s.status === 'PREPARE' ? 'text-danger-text' : s.status === 'LOW' ? 'text-warn-ink' : 'text-ink')

/** One stock line: name, status badge, stock and use, days left with a bar. */
export function StockRow({ s, first }: { s: StockItem; first?: boolean }) {
  const badge = STATUS_BADGE[s.status]
  const unit = s.item.unit.toLowerCase()
  const use = s.status === 'EXCLUDED' ? 'not used' : s.needPerDay ? `${kg(s.needPerDay)} ${unit}/day` : 'not in plan'
  return (
    <Link to={`/feed/items/${s.item.id}`} className={`block px-3.5 py-3 text-ink no-underline hover:text-ink ${first ? '' : 'border-t border-line-faint'}`}>
      <div className="flex items-center gap-2"><b className="flex-1 text-[15px]">{s.item.name}</b>{badge && <span className={`badge ${badge.cls}`}>{badge.text}</span>}</div>
      <div className="mt-0.5 flex justify-between gap-2 text-[13px] text-ink-3">
        <span>{categoryLabel(s.item.category)} · {kg(s.stock)} {unit} · {use}</span>
        {s.status !== 'EXCLUDED' && s.daysLeft != null && <b className={daysColor(s)}>{days(s.daysLeft)}</b>}
      </div>
      {s.status !== 'EXCLUDED' && s.barPct != null && (
        <div className="mt-2 h-2 overflow-hidden rounded bg-line-faint"><div className={`h-full ${barColor(s)}`} style={{ width: `${Math.max(2, Math.min(100, s.barPct))}%` }} /></div>
      )}
    </Link>
  )
}

/** Select of active feed items, optionally limited to some categories. */
export function FeedItemSelect({ id, items, value, onChange, filter, placeholder = 'Choose an item' }: {
  id: string; items: FeedItem[] | null; value: string; onChange: (v: string) => void; filter?: (i: FeedItem) => boolean; placeholder?: string
}) {
  const list = (items ?? []).filter((i) => (i.active && (!filter || filter(i))) || String(i.id) === value)
  return (
    <select className="inp" id={id} value={value} onChange={(e) => onChange(e.target.value)}>
      <option value="">{items ? (list.length ? placeholder : 'No feed items yet') : 'Loading…'}</option>
      {list.map((i) => <option key={i.id} value={i.id} disabled={i.excluded}>{i.name}{i.excluded ? ' · excluded' : ''}</option>)}
    </select>
  )
}

/** Small stat tile. */
export function Tile({ k, v, tone = '', to }: { k: string; v: string; tone?: string; to?: string }) {
  const body = <><div className="text-xs font-semibold text-ink-3">{k}</div><div className={`mt-0.5 text-xl font-bold ${tone}`}>{v}</div></>
  return to
    ? <Link to={to} className="rounded-xl bg-white p-3 text-ink no-underline hover:text-ink">{body}</Link>
    : <div className="rounded-xl bg-white p-3">{body}</div>
}
