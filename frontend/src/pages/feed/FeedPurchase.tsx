import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Screen, Segmented, TopBar } from '../../components/ui'
import { ContactSelect, LoadState } from '../../components/records'
import { FeedItemSelect, useFeed } from '../../components/feed'
import { useStore } from '../../data/store'
import { decimal, shortDate, todayIso, toNum, useLoad } from '../../data/records'
import { inr0 } from '../../data/breeding'
import { inr2, kg, PAYMENT_METHODS, type PaymentMethod } from '../../data/feed'
import { errorMessage } from '../../lib/api'

export default function FeedPurchase() {
  const [params] = useSearchParams()
  const { fd, isOwner } = useFeed()
  const { notify } = useStore()
  const nav = useNavigate()
  const items = useLoad(() => fd.items(), [fd])
  const ov = useLoad(() => fd.overview(), [fd])
  const [itemId, setItemId] = useState(params.get('item') ?? '')
  const [qty, setQty] = useState('')
  const [date, setDate] = useState(todayIso())
  const [mode, setMode] = useState<'UNIT' | 'TOTAL'>('UNIT')
  const [price, setPrice] = useState('')
  const [total, setTotal] = useState('')
  const [supplier, setSupplier] = useState('')
  const [method, setMethod] = useState<PaymentMethod>('UPI')
  const [paid, setPaid] = useState(true)
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const item = items.data?.find((i) => String(i.id) === itemId)
  const stock = ov.data?.items.find((s) => String(s.item.id) === itemId)
  const unit = (item?.unit ?? 'KG').toLowerCase()
  useEffect(() => { if (item?.supplier && !supplier) setSupplier(String(item.supplier.id)) }, [item, supplier])
  useEffect(() => { if (item && isOwner && !price && item.costPerUnit != null) setPrice(String(item.costPerUnit)) }, [item, isOwner, price])

  const q = toNum(qty)
  const shownTotal = mode === 'UNIT' ? (q && toNum(price) != null ? q * (toNum(price) ?? 0) : undefined) : toNum(total)
  const shownUnit = mode === 'TOTAL' ? (q && toNum(total) != null ? (toNum(total) ?? 0) / q : undefined) : toNum(price)

  const save = async () => {
    if (!item) { notify('Choose the item'); return }
    if (!q) { notify('Enter the quantity'); return }
    setBusy(true)
    try {
      await fd.purchase({
        itemId: item.id, date, quantity: q,
        costPerUnit: isOwner && mode === 'UNIT' ? toNum(price) ?? null : undefined,
        totalAmount: isOwner && mode === 'TOTAL' ? toNum(total) ?? null : undefined,
        supplierContactId: supplier ? Number(supplier) : undefined, paymentMethod: isOwner ? method : undefined, paid: isOwner ? paid : undefined,
        notes: notes.trim() || undefined,
      })
      notify(`${kg(q)} ${unit} of ${item.name} added to stock`)
      nav('/feed', { replace: true })
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title="Buy feed" close="/feed" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <LoadState loading={items.loading} error={items.error} onRetry={items.reload} empty={!items.data} />
        <div><label className="lab" htmlFor="fp-item">Item *</label><FeedItemSelect id="fp-item" items={items.data} value={itemId} onChange={setItemId} />
          {stock && <div className="hint mt-1">{kg(stock.stock)} {unit} in stock{stock.daysLeft != null ? ` · ${kg(stock.daysLeft)} days` : ''}</div>}</div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="fp-q">Quantity ({unit}) *</label><input className="inp" id="fp-q" inputMode="decimal" value={qty} onChange={(e) => setQty(decimal(e.target.value))} /></div>
          <div><label className="lab" htmlFor="fp-dt">Date</label><input className="inp" id="fp-dt" type="date" max={todayIso()} value={date} onChange={(e) => e.target.value && setDate(e.target.value)} /></div>
        </div>
        {isOwner && (
          <div className="rounded-[14px] bg-white p-3.5">
            <span className="lab">Enter price as</span>
            <Segmented label="Enter price as" options={[{ value: 'UNIT', label: `Cost per ${unit}` }, { value: 'TOTAL', label: 'Total amount' }]} value={mode} onChange={setMode} />
            <div className="mt-3 grid grid-cols-2 gap-3">
              {mode === 'UNIT' ? (
                <>
                  <div><label className="lab" htmlFor="fp-pk">₹ per {unit}</label><input className="inp" id="fp-pk" inputMode="decimal" value={price} onChange={(e) => setPrice(decimal(e.target.value))} /></div>
                  <div><span className="lab">Total ₹</span><div className="flex h-12 items-center rounded-[10px] bg-[#F7F7F3] px-3.5 font-semibold">{inr0(shownTotal)}</div></div>
                </>
              ) : (
                <>
                  <div><label className="lab" htmlFor="fp-tt">Total ₹</label><input className="inp" id="fp-tt" inputMode="decimal" value={total} onChange={(e) => setTotal(decimal(e.target.value))} /></div>
                  <div><span className="lab">₹ per {unit}</span><div className="flex h-12 items-center rounded-[10px] bg-[#F7F7F3] px-3.5 font-semibold">{inr2(shownUnit)}</div></div>
                </>
              )}
            </div>
            {stock?.lastPrice != null && <div className="hint">Last bought at {inr2(stock.lastPrice)}/{unit}{stock.lastPurchaseDate ? ` on ${shortDate(stock.lastPurchaseDate)}` : ''}.</div>}
          </div>
        )}
        <div><label className="lab" htmlFor="fp-sup">Supplier</label><ContactSelect id="fp-sup" role="SUPPLIER" value={supplier} onChange={setSupplier} newLabel="New supplier" /></div>
        {isOwner && (
          <div className="grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="fp-pm">Payment</label><select className="inp" id="fp-pm" value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>{PAYMENT_METHODS.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}</select></div>
            <div><label className="lab" htmlFor="fp-ps">Status</label><select className="inp" id="fp-ps" value={paid ? 'PAID' : 'PENDING'} onChange={(e) => setPaid(e.target.value === 'PAID')}><option value="PAID">Paid</option><option value="PENDING">Pending</option></select></div>
          </div>
        )}
        {isOwner && (
          <div className="rounded-xl bg-brand-soft px-3.5 py-3 text-sm">
            <b>Saved for Finance</b><div className="mt-0.5 text-[13px] text-ink-2">Feed expense{shownTotal != null ? ` · ${inr0(shownTotal)}` : ''} added to Finance{paid ? '' : ' as pending'}. The item's cost per {unit} is updated.</div>
          </div>
        )}
        <div><label className="lab" htmlFor="fp-notes">Notes</label><input className="inp" id="fp-notes" placeholder="Bill no., vehicle, etc." value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        {stock && q ? <div className="mb-2.5 flex justify-between text-[15px]"><span>Stock after purchase</span><b>{kg(stock.stock + q)} {unit}</b></div> : null}
        <button type="button" className="btn" disabled={busy || !items.data} onClick={save}>{busy ? 'Saving…' : 'Save purchase'}</button>
      </div>
    </Screen>
  )
}
