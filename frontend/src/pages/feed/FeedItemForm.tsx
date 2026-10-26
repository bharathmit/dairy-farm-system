import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Dialog, Screen, Segmented, TopBar } from '../../components/ui'
import { ContactSelect, LoadState } from '../../components/records'
import { useFeed } from '../../components/feed'
import { useStore } from '../../data/store'
import { decimal, numText, shortDate, todayIso, toNum, useLoad } from '../../data/records'
import { inr0 } from '../../data/breeding'
import { FEED_CATEGORIES, kg, type FeedCategory, type FeedItem } from '../../data/feed'
import { errorMessage } from '../../lib/api'

interface Draft {
  name: string; category: FeedCategory | ''; unit: string; costPerUnit: string
  dmPct: string; cpPct: string; tdnPct: string; caPct: string; pPct: string
  fatPct: string; fibrePct: string; aiaPct: string; aflatoxinPpb: string
  maxInclusionPct: string; excludedReason: string; reorderLevel: string; leadTimeDays: string; supplier: string; notes: string; active: boolean
}
const empty: Draft = {
  name: '', category: '', unit: 'KG', costPerUnit: '', dmPct: '', cpPct: '', tdnPct: '', caPct: '', pPct: '',
  fatPct: '', fibrePct: '', aiaPct: '', aflatoxinPpb: '', maxInclusionPct: '', excludedReason: '', reorderLevel: '', leadTimeDays: '', supplier: '', notes: '', active: true,
}
const fromItem = (i: FeedItem): Draft => ({
  name: i.name, category: i.category, unit: i.unit || 'KG', costPerUnit: numText(i.costPerUnit),
  dmPct: numText(i.dmPct), cpPct: numText(i.cpPct), tdnPct: numText(i.tdnPct), caPct: numText(i.caPct), pPct: numText(i.pPct),
  fatPct: numText(i.fatPct), fibrePct: numText(i.fibrePct), aiaPct: numText(i.aiaPct), aflatoxinPpb: numText(i.aflatoxinPpb),
  maxInclusionPct: i.excluded ? '0' : numText(i.maxInclusionPct), excludedReason: i.excludedReason ?? '',
  reorderLevel: numText(i.reorderLevel), leadTimeDays: numText(i.leadTimeDays), supplier: i.supplier ? String(i.supplier.id) : '', notes: i.notes ?? '', active: i.active,
})
/** Nutrients and IS 2052 quality values, as fed (% = kg per 100 kg). The formula converts to dry basis for the BIS check. */
const NUTRIENTS = [['dmPct', 'DM %'], ['cpPct', 'CP %'], ['tdnPct', 'TDN %'], ['caPct', 'Ca %'], ['pPct', 'P %'],
  ['fatPct', 'Crude fat %'], ['fibrePct', 'Crude fibre %'], ['aiaPct', 'Acid insol. ash %'], ['aflatoxinPpb', 'Aflatoxin B1 ppb']] as const

/** Record own fodder cut, or correct stock (+/−). */
function StockBox({ id, unit, isMix, onDone }: { id: string; unit: string; isMix: boolean; onDone: () => void }) {
  const { fd } = useFeed()
  const { notify } = useStore()
  const [type, setType] = useState<'HARVEST' | 'ADJUSTMENT'>('HARVEST')
  const [qty, setQty] = useState('')
  const [date, setDate] = useState(todayIso())
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const save = async () => {
    const q = toNum(qty.replace(/[^\d.-]/g, ''))
    if (!q) { notify('Enter the quantity'); return }
    if (type === 'HARVEST' && q < 0) { notify('A harvest adds stock; use Adjust to remove'); return }
    setBusy(true)
    try {
      await fd.stock(id, { type, date, quantity: q, notes: note.trim() || undefined })
      notify(type === 'HARVEST' ? `${isMix ? 'Stock' : 'Harvest'} of ${q} ${unit} added` : 'Stock corrected'); setQty(''); setNote(''); onDone()
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  return (
    <div className="flex flex-col gap-3 rounded-[14px] bg-white p-3.5">
      <b className="text-[15px]">{isMix ? 'Add opening stock or correct stock' : 'Add own fodder or correct stock'}</b>
      <Segmented label="Stock change" options={[{ value: 'HARVEST', label: isMix ? 'Add stock' : 'Harvest / cut' }, { value: 'ADJUSTMENT', label: 'Adjust ±' }]} value={type} onChange={setType} />
      <div className="grid grid-cols-2 gap-3">
        <div><label className="lab" htmlFor="fi-q">{type === 'HARVEST' ? `Quantity (${unit})` : `+ / − ${unit}`}</label><input className="inp" id="fi-q" inputMode="decimal" placeholder={type === 'HARVEST' ? '200' : '-10'} value={qty} onChange={(e) => setQty(e.target.value.replace(/[^\d.+-]/g, ''))} /></div>
        <div><label className="lab" htmlFor="fi-qd">Date</label><input className="inp" id="fi-qd" type="date" max={todayIso()} value={date} onChange={(e) => e.target.value && setDate(e.target.value)} /></div>
      </div>
      <div><label className="lab" htmlFor="fi-qn">Note</label><input className="inp" id="fi-qn" placeholder={type === 'HARVEST' ? (isMix ? 'e.g. opening stock' : 'e.g. cut from plot 2') : 'e.g. counted, spoilt'} value={note} onChange={(e) => setNote(e.target.value)} /></div>
      <button type="button" className="btn2 h-11" disabled={busy} onClick={save}>{busy ? 'Saving…' : type === 'HARVEST' ? (isMix ? 'Add stock' : 'Add harvest') : 'Save correction'}</button>
    </div>
  )
}

export default function FeedItemForm() {
  const { id } = useParams()
  const { fd, isOwner } = useFeed()
  const { notify } = useStore()
  const nav = useNavigate()
  const existing = useLoad(id ? () => fd.item(id) : null, [fd, id])
  const overview = useLoad(id ? () => fd.overview() : null, [fd, id])
  const moves = useLoad(id ? () => fd.moves(id) : null, [fd, id])
  const [d, setD] = useState<Draft>(empty)
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)
  useEffect(() => { if (existing.data) setD(fromItem(existing.data)) }, [existing.data])
  const set = (p: Partial<Draft>) => setD((c) => ({ ...c, ...p }))
  const ro = !isOwner
  const stock = overview.data?.items.find((s) => String(s.item.id) === id)
  const unit = (d.unit || 'KG').toLowerCase()
  const excluded = d.maxInclusionPct.trim() !== '' && toNum(d.maxInclusionPct) === 0
  /** Mix targets: a formula's target values; stock comes from batches, purchases and adjustments. */
  const isMix = d.category === 'MIX'
  const moisture = toNum(d.dmPct)

  const save = async () => {
    if (!d.name.trim()) { notify('Enter the name'); return }
    if (!d.category) { notify('Pick a category'); return }
    setBusy(true)
    try {
      const saved = await fd.saveItem({
        name: d.name.trim(), category: d.category, unit: d.unit || 'KG', costPerUnit: toNum(d.costPerUnit) ?? null,
        dmPct: toNum(d.dmPct) ?? null, cpPct: toNum(d.cpPct) ?? null, tdnPct: toNum(d.tdnPct) ?? null, caPct: toNum(d.caPct) ?? null, pPct: toNum(d.pPct) ?? null,
        fatPct: toNum(d.fatPct) ?? null, fibrePct: toNum(d.fibrePct) ?? null, aiaPct: toNum(d.aiaPct) ?? null, aflatoxinPpb: toNum(d.aflatoxinPpb) ?? null,
        maxInclusionPct: toNum(d.maxInclusionPct) ?? null, excludedReason: excluded ? d.excludedReason.trim() || null : null,
        reorderLevel: toNum(d.reorderLevel) ?? null, leadTimeDays: toNum(d.leadTimeDays) ?? null,
        supplierContactId: d.supplier ? Number(d.supplier) : null, notes: d.notes.trim() || null, active: d.active,
      }, id)
      notify(`${saved.name} saved`)
      nav('/feed')
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  const remove = async () => {
    if (!existing.data) return
    try { await fd.deleteItem(existing.data.id); notify('Item deleted'); nav('/feed') } catch (e) { setConfirm(false); notify(errorMessage(e)) }
  }

  return (
    <Screen>
      <TopBar title={id ? (existing.data?.name ?? 'Feed item') : 'Feed item'} close="/feed" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <LoadState loading={existing.loading} error={existing.error} onRetry={existing.reload} empty={Boolean(id) && !existing.data} />
        {id && stock && (
          <div className="grid grid-cols-3 gap-2.5">
            <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">In stock</div><div className="mt-0.5 text-lg font-bold">{kg(stock.stock)} {unit}</div></div>
            <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Use / day</div><div className="mt-0.5 text-lg font-bold">{stock.needPerDay ? kg(stock.needPerDay) : '—'}</div></div>
            <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Days left</div><div className="mt-0.5 text-lg font-bold">{stock.daysLeft != null ? kg(stock.daysLeft, 1) : '—'}</div></div>
          </div>
        )}
        {id && <div className="grid grid-cols-2 gap-2"><Link to={`/feed/buy?item=${id}`} className="btn2 h-11 no-underline">Buy</Link><a href="#stock" className="btn2 h-11 no-underline">{isMix ? 'Add / adjust' : 'Harvest / adjust'}</a></div>}
        {ro && !id && <div className="rounded-xl bg-warn-soft px-3.5 py-3 text-sm text-warn-text">Only farm owners can add feed items.</div>}

        <fieldset disabled={ro} className="m-0 flex flex-col gap-3.5 border-0 p-0">
          <div><label className="lab" htmlFor="fi-name">Name *</label><input className="inp" id="fi-name" placeholder="e.g. Gingelly cake" value={d.name} onChange={(e) => set({ name: e.target.value })} /></div>
          <div>
            <span className="lab">Category *</span>
            <div role="group" aria-label="Category" className="flex flex-wrap gap-2">
              {FEED_CATEGORIES.map((c) => <button key={c.value} type="button" aria-pressed={d.category === c.value} onClick={() => set({ category: c.value })} className={`chip h-10 ${d.category === c.value ? 'chip-on' : ''}`}>{c.label}</button>)}
            </div>
            <div className="hint">"Mix targets" = the nutrient targets a concentrate formula is checked against; its stock comes from batches, purchases and adjustments.</div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="fi-unit">Unit</label><select className="inp" id="fi-unit" value={d.unit} onChange={(e) => set({ unit: e.target.value })}><option value="KG">kg</option><option value="L">L</option><option value="NOS">pieces</option></select></div>
            {isOwner && <div><label className="lab" htmlFor="fi-cost">Cost per {unit} (₹)</label><input className="inp" id="fi-cost" inputMode="decimal" value={d.costPerUnit} onChange={(e) => set({ costPerUnit: decimal(e.target.value) })} /><div className="hint mt-1">{isMix ? 'Updates from the latest batch or purchase' : 'Updates from the latest purchase'}</div></div>}
          </div>

          <div className="rounded-[14px] bg-white p-3.5">
            <div className="flex items-center justify-between"><b>Nutrients per 100 kg</b><span className="text-[13px] text-ink-3">as fed · moisture {moisture != null ? `${kg(100 - moisture)} %` : '—'}</span></div>
            <div className="mt-3 grid grid-cols-3 gap-2.5">
              {NUTRIENTS.map(([k, label]) => <div key={k}><label className="lab" htmlFor={`fi-${k}`}>{label}</label><input className="inp" id={`fi-${k}`} inputMode="decimal" value={d[k]} onChange={(e) => set({ [k]: decimal(e.target.value) } as Partial<Draft>)} /></div>)}
            </div>
            <div className="hint">{isMix ? 'Targets for the formula that makes this mix (as fed, like the ingredients). The formula compares its mix against these.' : 'Used for formula and ration nutrient checks and the BIS (IS 2052) grade check. Moisture = 100 − DM %.'}</div>
          </div>

          <div className="rounded-[14px] bg-white p-3.5">
            <b>Use limits</b>
            <div className="mt-3"><label className="lab" htmlFor="fi-max">Max inclusion in a concentrate (%)</label><input className="inp" id="fi-max" inputMode="decimal" placeholder="No limit" value={d.maxInclusionPct} onChange={(e) => set({ maxInclusionPct: decimal(e.target.value) })} />
              <div className="hint">Formula shows a warning above this. <b>0 % = excluded</b>: blocked in every formula and ration.</div></div>
            {excluded && <span className="badge mt-2 inline-block bg-ground text-ink-2">Excluded</span>}
            {excluded && <div className="mt-3"><label className="lab" htmlFor="fi-why">Reason for exclusion</label><input className="inp" id="fi-why" placeholder="e.g. Gossypol (cottonseed cake)" value={d.excludedReason} onChange={(e) => set({ excludedReason: e.target.value })} /></div>}
            <div className="mt-3.5 rounded-[10px] bg-warn-soft px-3 py-2.5 text-[13px] text-warn-text">Daily limits by lactation stage are set in the ration rules. <Link to="/feed/ration-rules" className="font-semibold text-warn-ink">Open ration rules</Link></div>
          </div>

          <div className="rounded-[14px] bg-white p-3.5">
            <b>Reorder</b>
            <div className="mt-3 grid grid-cols-2 gap-3">
              <div><label className="lab" htmlFor="fi-rl">Reorder level ({unit})</label><input className="inp" id="fi-rl" inputMode="decimal" value={d.reorderLevel} onChange={(e) => set({ reorderLevel: decimal(e.target.value) })} /></div>
              <div><label className="lab" htmlFor="fi-lt">Lead time (days)</label><input className="inp" id="fi-lt" inputMode="numeric" value={d.leadTimeDays} onChange={(e) => set({ leadTimeDays: e.target.value.replace(/\D/g, '') })} /></div>
            </div>
            <div className="hint">Alert when stock falls below the level, or days left ≤ lead time.</div>
          </div>

          <div className="rounded-[14px] bg-white p-3.5">
            <b>Supplier</b>
            <div className="mt-3"><label className="lab" htmlFor="fi-sup">Supplier</label><ContactSelect id="fi-sup" role="SUPPLIER" value={d.supplier} onChange={(v) => set({ supplier: v })} newLabel="New supplier" /></div>
          </div>

          <div><label className="lab" htmlFor="fi-notes">Notes</label><input className="inp" id="fi-notes" placeholder="Optional" value={d.notes} onChange={(e) => set({ notes: e.target.value })} /></div>
          {id && <label className="flex min-h-11 items-center gap-2.5 text-[15px]"><input type="checkbox" checked={d.active} onChange={(e) => set({ active: e.target.checked })} className="h-5 w-5 accent-brand" />Active (shows in lists and plans)</label>}
        </fieldset>

        {id && <div id="stock"><StockBox id={id} unit={unit} isMix={isMix} onDone={() => { void overview.reload(); void moves.reload() }} /></div>}

        {id && moves.data && moves.data.length > 0 && (
          <div className="overflow-hidden rounded-[14px] bg-white">
            <div className="px-3.5 pb-1 pt-3"><b className="text-[15px]">Recent stock moves</b></div>
            {moves.data.slice(0, 12).map((m) => (
              <div key={m.id} className="flex items-center gap-2 border-t border-line-faint px-3.5 py-2.5 text-sm">
                <span className="w-14 text-ink-3">{shortDate(m.date)}</span>
                <span className="min-w-0 flex-1">{m.type === 'PURCHASE' ? 'Bought' : m.type === 'USAGE' ? 'Fed' : m.type === 'PRODUCE' ? (m.sourceType === 'FORMULA_BATCH' ? 'Batch made' : isMix ? 'Added' : 'Harvest') : 'Adjusted'}
                  {m.target?.label ? ` · ${m.target.label}` : ''}{m.supplier ? ` · ${m.supplier.name}` : ''}{isOwner && m.cost != null ? ` · ${inr0(m.cost)}` : ''}</span>
                <b className={m.type === 'USAGE' || m.quantity < 0 ? 'text-danger-text' : 'text-brand'}>{m.type === 'USAGE' || m.quantity < 0 ? '−' : '+'}{kg(Math.abs(m.quantity))}</b>
              </div>
            ))}
          </div>
        )}
        {id && isOwner && <button type="button" onClick={() => setConfirm(true)} className="h-12 rounded-xl border border-line bg-white font-semibold text-danger">Delete item</button>}
      </div>
      {isOwner && (
        <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
          <button type="button" className="btn" disabled={busy || (Boolean(id) && !existing.data)} onClick={save}>{busy ? 'Saving…' : 'Save item'}</button>
        </div>
      )}
      {confirm && (
        <Dialog title="Delete this item?" onClose={() => setConfirm(false)}>
          <p className="mb-4 mt-0 text-sm text-ink-2">An item with stock moves or used in a formula can't be deleted — make it inactive instead.</p>
          <div className="grid grid-cols-2 gap-2.5">
            <button type="button" onClick={() => setConfirm(false)} className="h-12 rounded-xl border border-line bg-white font-semibold">Cancel</button>
            <button type="button" onClick={remove} className="h-12 rounded-xl bg-danger font-semibold text-white">Delete</button>
          </div>
        </Dialog>
      )}
    </Screen>
  )
}
