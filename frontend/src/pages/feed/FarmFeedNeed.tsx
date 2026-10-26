import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { moneyApi } from '../../data/money'
import { errorMessage } from '../../lib/api'
import { Footer, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { Tile, useFeed } from '../../components/feed'
import { useStore } from '../../data/store'
import { shortDate, useLoad } from '../../data/records'
import { inr0 } from '../../data/breeding'
import { kg, type NeedRow, type Section } from '../../data/feed'

const SECTION_NAME: Record<Section, string> = { GREEN: 'Green fodder', DRY: 'Dry fodder', CONCENTRATE: 'Concentrate', OTHER: 'Other' }

function Rows({ rows }: { rows: NeedRow[] }) {
  return (
    <>
      {rows.map((r) => (
        <Link key={r.item.id} to={`/feed/items/${r.item.id}`} className={`grid min-h-11 grid-cols-[1.6fr_.8fr_.8fr_.6fr] items-center gap-1 border-t border-line-faint px-3 text-sm text-ink no-underline hover:text-ink ${r.overLeadTime ? 'bg-danger-soft' : ''}`}>
          <span>{r.item.name}</span><span className="text-right">{kg(r.needPerDay)}</span><span className="text-right">{kg(r.stock, 0)}</span>
          <b className={`text-right ${r.overLeadTime ? 'text-danger-text' : ''}`}>{r.daysLeft != null ? kg(r.daysLeft, 1) : '—'}</b>
        </Link>
      ))}
    </>
  )
}

export default function FarmFeedNeed() {
  const { fd, isOwner, farmId } = useFeed()
  const nav = useNavigate()
  const { notify } = useStore()
  const [making, setMaking] = useState(false)
  const makeNotes = async () => {
    setMaking(true)
    try {
      const made = await moneyApi(farmId).fromFeedNeed(7)
      notify(made.length ? `${made.length} procurement note${made.length === 1 ? '' : 's'} added` : 'Already planned — nothing new to add')
      nav('/money/procurement')
    } catch (e) { notify(errorMessage(e)) } finally { setMaking(false) }
  }
  const { farm } = useStore()
  const res = useLoad(() => fd.need(7), [fd])
  const n = res.data
  const sections: Section[] = ['GREEN', 'DRY', 'CONCENTRATE', 'OTHER']
  const head = <div className="grid min-h-9 grid-cols-[1.6fr_.8fr_.8fr_.6fr] items-center gap-1 px-3 text-xs font-bold text-ink-3"><span>Item</span><span className="text-right">Need/day</span><span className="text-right">Stock</span><span className="text-right">Days</span></div>

  return (
    <Screen>
      <TopBar title="Farm feed need" back="/feed" />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <LoadState loading={res.loading} error={res.error} onRetry={res.reload} empty={!n} />
        {n && (
          <>
            <div className="text-sm text-ink-3">{farm?.name} · {n.animals} animal{n.animals === 1 ? '' : 's'} · from the ration plan</div>
            <div className="grid grid-cols-3 gap-2.5">
              <Tile k="Green / day" v={`${kg(n.perDay.greenKg, 0)} kg`} /><Tile k="Dry / day" v={`${kg(n.perDay.dryKg, 0)} kg`} /><Tile k="Concentrate" v={`${kg(n.perDay.concentrateKg, 0)} kg`} />
            </div>
            {isOwner && (
              <div className="grid grid-cols-2 gap-2.5">
                <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Feed cost / day</div><div className="mt-0.5 text-xl font-bold">{inr0(n.perDay.costPerDay)}</div>{n.concentrateCostPerDay != null && <div className="text-xs text-ink-3">Concentrate alone {inr0(n.concentrateCostPerDay)}</div>}</div>
                <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Feed cost / month</div><div className="mt-0.5 text-xl font-bold">{inr0(n.costPerMonth)}</div>{n.concentrateCostPerMonth != null && <div className="text-xs text-ink-3">Concentrate alone {inr0(n.concentrateCostPerMonth)}</div>}</div>
              </div>
            )}
            <div className="overflow-hidden rounded-[14px] bg-white">
              {head}
              {sections.map((s) => {
                const rows = n.rows.filter((r) => (r.section ?? 'OTHER') === s)
                if (!rows.length) return null
                return <div key={s}><div className="bg-[#F7F7F3] px-3 pb-1 pt-2.5 text-xs font-bold uppercase tracking-[.06em] text-ink-3">{SECTION_NAME[s]}</div><Rows rows={rows} /></div>
              })}
              {n.ingredientRows.length > 0 && (
                <details className="group">
                  <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-2 bg-[#F7F7F3] px-3 text-xs font-bold uppercase tracking-[.06em] text-ink-3 [&::-webkit-details-marker]:hidden">
                    <span>Concentrate ingredients (for batches) · {n.ingredientRows.length}{n.ingredientRows.some((r) => r.overLeadTime) && <span className="ml-1.5 normal-case tracking-normal text-danger-text">· {n.ingredientRows.filter((r) => r.overLeadTime).length} low</span>}</span>
                    <span className="normal-case tracking-normal text-brand"><span className="group-open:hidden">Show</span><span className="hidden group-open:inline">Hide</span></span>
                  </summary>
                  <Rows rows={n.ingredientRows} />
                </details>
              )}
              {!n.rows.length && !n.ingredientRows.length && <div className="border-t border-line-faint px-4 py-5 text-center text-sm text-ink-3">No items in the ration plan yet. <Link to="/feed/ration-rules" className="font-semibold">Pick items in the ration rules</Link></div>}
            </div>
            <div className="text-xs text-ink-3">Ingredient need = concentrate need × formula share. Red = days left ≤ lead time.</div>
            {n.shortfalls.length > 0 && (
              <div className="flex flex-col gap-2 rounded-xl bg-warn-soft px-3.5 py-3 text-sm text-warn-text">
                <b className="text-warn-ink">{n.shortfalls.length} shortfall{n.shortfalls.length === 1 ? '' : 's'} in the next 7 days</b>
                <span>{n.shortfalls.map((s) => `${s.item.name} ~${kg(s.quantityKg, 0)} kg${s.neededBy ? ` by ${shortDate(s.neededBy)}` : ''}${isOwner && s.expectedPrice != null ? ` (${inr0(s.expectedPrice)})` : ''}`).join(' · ')}</span>
                <div className="flex flex-wrap gap-2">{n.shortfalls.slice(0, 3).map((s) => <Link key={s.item.id} to={`/feed/buy?item=${s.item.id}`} className="flex h-10 items-center rounded-[10px] border border-line bg-white px-3 text-sm font-semibold text-ink no-underline">Buy {s.item.name}</Link>)}</div>
                {isOwner && <button type="button" disabled={making} onClick={makeNotes} className="btn2 h-11">{making ? 'Adding…' : 'Create procurement notes'}</button>}
                {isOwner && <span className="text-xs">Adds a planned note per shortfall: item, quantity, expected price, needed by.</span>}
              </div>
            )}
          </>
        )}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
