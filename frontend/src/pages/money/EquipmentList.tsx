import { Link } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { OwnerOnlyScreen, Stat, useMoney } from '../../components/money'
import { shortDate, useLoad } from '../../data/records'
import type { Equipment } from '../../data/money'

const statusBadge = (e: Equipment) => {
  if (e.status === 'OVERDUE') return { t: `Overdue ${Math.abs(e.daysTo ?? 0)} d`, c: 'bg-danger-soft text-danger-text', card: 'outline outline-2 outline-danger' }
  if (e.status === 'DUE_SOON') return { t: `Due in ${e.daysTo ?? 0} d`, c: 'bg-warn-soft text-warn-ink', card: '' }
  return { t: e.daysTo != null ? `OK · ${e.daysTo} d` : 'No schedule', c: 'bg-brand-soft text-brand', card: '' }
}

export default function EquipmentList() {
  const { mo, isOwner } = useMoney()
  const list = useLoad(isOwner ? () => mo.equipment() : null, [mo, isOwner])
  if (!isOwner) return <OwnerOnlyScreen title="Equipment" back="/money" />
  const d = list.data
  return (
    <Screen>
      <TopBar title="Equipment" back="/money" right={<Link to="/money/equipment/new" className="flex h-10 items-center rounded-[10px] bg-brand px-3.5 text-sm font-semibold text-white no-underline hover:text-white">+ Add</Link>} />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <LoadState loading={list.loading} error={list.error} onRetry={list.reload} empty={!d} />
        {d && (
          <>
            <div className="grid grid-cols-3 gap-2.5">
              <Stat k="Items" v={d.counts.items} />
              <Stat k="Overdue" v={<span className={d.counts.overdue ? 'text-danger' : ''}>{d.counts.overdue}</span>} />
              <Stat k="Due 7 d" v={<span className={d.counts.dueSoon ? 'text-warn-ink' : ''}>{d.counts.dueSoon}</span>} />
            </div>
            {d.items.length === 0 && <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">No equipment yet. Add the milking machine, chaff cutter, cans or pump to get service reminders.</div>}
            {d.items.map((e) => {
              const b = statusBadge(e)
              return (
                <div key={e.id} className={`rounded-[14px] bg-white p-3.5 ${b.card}`}>
                  <Link to={`/money/equipment/${e.id}`} className="flex items-center gap-2 text-ink no-underline hover:text-ink">
                    <span className="min-w-0 flex-1"><b className="text-base">{e.name}</b>
                      <span className="block text-[13px] text-ink-3">{e.serviceTask || 'Service'}{e.serviceEveryDays ? ` every ${e.serviceEveryDays} days` : ''}{e.brand || e.model ? ` · ${[e.brand, e.model].filter(Boolean).join(' ')}` : ''}</span></span>
                    <span className={`badge ${b.c}`}>{b.t}</span>
                  </Link>
                  <div className="mt-2.5 grid grid-cols-2 gap-2">
                    <div className="rounded-[10px] bg-ground px-2.5 py-2"><span className="block text-xs font-semibold text-ink-3">Last service</span><b>{e.lastService ? shortDate(e.lastService) : '—'}</b></div>
                    <div className="rounded-[10px] bg-ground px-2.5 py-2"><span className="block text-xs font-semibold text-ink-3">Next service</span><b className={e.status === 'OVERDUE' ? 'text-danger-text' : ''}>{e.nextService ? shortDate(e.nextService) : '—'}</b></div>
                  </div>
                  <Link to={`/money/equipment/${e.id}/service`} className={`mt-2.5 h-11 text-sm ${e.status === 'OK' ? 'btn2' : 'btn'}`}>Log service</Link>
                </div>
              )
            })}
          </>
        )}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
