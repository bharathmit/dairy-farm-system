import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { useFeed } from '../../components/feed'
import { useLoad } from '../../data/records'
import { categoryLabel, inr2, STOCK_FILTERS } from '../../data/feed'

export default function FeedItems() {
  const { fd, isOwner } = useFeed()
  const res = useLoad(() => fd.items({ includeInactive: true }), [fd])
  const [filter, setFilter] = useState('ALL')
  const f = STOCK_FILTERS.find((x) => x.key === filter) ?? STOCK_FILTERS[0]
  const list = (res.data ?? []).filter((i) => f.match(i.category)).sort((a, b) => Number(b.active) - Number(a.active) || a.name.localeCompare(b.name))
  return (
    <Screen wide>
      <TopBar title="Feed items" back="/settings" right={isOwner ? <Link to="/feed/items/new" className="flex h-10 items-center rounded-[10px] bg-brand px-3 text-sm font-semibold text-white no-underline hover:text-white">+ Item</Link> : undefined} />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <div className="flex gap-2 overflow-x-auto pb-1">
          {STOCK_FILTERS.map((x) => <button key={x.key} type="button" aria-pressed={filter === x.key} onClick={() => setFilter(x.key)} className={`chip ${filter === x.key ? 'chip-on' : ''}`}>{x.label}</button>)}
        </div>
        <LoadState loading={res.loading} error={res.error} onRetry={res.reload} empty={!res.data} />
        {list.length > 0 && (
          <div className="overflow-hidden rounded-[14px] bg-white">
            {list.map((i, n) => (
              <Link key={i.id} to={`/feed/items/${i.id}`} className={`flex items-center gap-2 px-3.5 py-3 text-ink no-underline hover:text-ink ${n ? 'border-t border-line-faint' : ''} ${i.active ? '' : 'opacity-60'}`}>
                <span className="min-w-0 flex-1"><b>{i.name}</b><div className="text-[13px] text-ink-3">{[categoryLabel(i.category), isOwner && i.costPerUnit != null && `${inr2(i.costPerUnit)}/${i.unit.toLowerCase()}`, i.supplier?.name, !i.active && 'inactive'].filter(Boolean).join(' · ')}</div></span>
                {i.excluded && <span className="badge bg-ground text-ink-2">Excluded</span>}
              </Link>
            ))}
          </div>
        )}
        {res.data && list.length === 0 && <div className="rounded-[14px] bg-white px-4 py-5 text-center text-sm text-ink-3">No feed items here yet.</div>}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
