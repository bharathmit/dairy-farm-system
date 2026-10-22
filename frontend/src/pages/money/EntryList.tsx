import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { OwnerOnlyScreen, useMoney } from '../../components/money'
import { useStore } from '../../data/store'
import { shortDate, useLoad } from '../../data/records'
import { finCategoryLabel, rupees } from '../../data/money'

/** All entries for one animal (or all), newest first. */
export default function EntryList() {
  const { mo, isOwner } = useMoney()
  const { animals } = useStore()
  const [params] = useSearchParams()
  const animalId = params.get('animal') ?? undefined
  const [size, setSize] = useState(50)
  const list = useLoad(isOwner ? () => mo.entries({ animalId, size }) : null, [mo, animalId, size, isOwner])
  if (!isOwner) return <OwnerOnlyScreen title="Entries" back="/money" />
  const a = animals.find((x) => x.id === animalId)
  const items = list.data?.items ?? []
  const total = items.reduce((s, e) => s + (e.direction === 'INCOME' ? e.amount : -e.amount), 0)
  return (
    <Screen>
      <TopBar title={a ? `Entries · ${a.tag}` : 'Entries'} back={animalId ? `/money/pnl?animal=${animalId}` : '/money'} />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <LoadState loading={list.loading} error={list.error} onRetry={list.reload} empty={!list.data} />
        {list.data && items.length === 0 && <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">No entries{a ? ` for ${a.tag}` : ''} yet. Shared farm costs count in profit per animal but aren't listed here.</div>}
        {items.length > 0 && (
          <div className="overflow-hidden rounded-[14px] bg-white">
            {items.map((e, i) => (
              <Link key={e.id} to={`/money/entries/${e.id}`} className={`flex min-h-14 items-center gap-2.5 px-3 py-2 text-ink no-underline hover:text-ink ${i ? 'border-t border-line-faint' : ''}`}>
                <span className="min-w-0 flex-1"><b className="text-[15px]">{finCategoryLabel(e.category)}</b><span className="block truncate text-[13px] text-ink-3">{shortDate(e.date)}{e.counterparty ? ` · ${e.counterparty}` : ''}{e.notes ? ` · ${e.notes}` : ''}</span></span>
                <span className="text-right"><b className={e.direction === 'INCOME' ? 'text-brand' : 'text-danger'}>{e.direction === 'INCOME' ? '+' : '−'}{rupees(e.amount)}</b>
                  {e.status === 'PENDING' && <span className="badge block bg-warn-soft text-warn-ink">Pending</span>}</span>
              </Link>
            ))}
            <div className="flex justify-between border-t-2 border-ink px-3 py-3 text-[15px]"><b>Total shown</b><b>{rupees(total, true)}</b></div>
          </div>
        )}
        {list.data && list.data.totalItems > items.length && <button type="button" className="btn2 h-11 text-sm" onClick={() => setSize((n) => n + 50)}>Show more</button>}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
