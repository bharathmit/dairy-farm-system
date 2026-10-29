import { useState } from 'react'
import { Footer, Screen, Segmented, Sheet, TopBar } from '../../components/ui'
import { ContactSelect, LoadState } from '../../components/records'
import { ChoiceChips, OwnerOnlyScreen, PaymentChips, useMoney } from '../../components/money'
import { useStore } from '../../data/store'
import { decimal, numText, shortDate, todayIso, toNum, useLoad } from '../../data/records'
import { rupees, type Note, type NoteStatus, type Suggestion } from '../../data/money'
import type { PaymentMethod } from '../../data/feed'
import { errorMessage } from '../../lib/api'

type Filter = 'ALL' | NoteStatus
const TONE: Record<NoteStatus, string> = {
  PLANNED: 'bg-ground text-ink-2', ORDERED: 'bg-info-soft text-info-text', RECEIVED: 'bg-brand-soft text-brand', CANCELLED: 'bg-ground text-ink-3',
}
const LABEL: Record<NoteStatus, string> = { PLANNED: 'Planned', ORDERED: 'Ordered', RECEIVED: 'Received', CANCELLED: 'Cancelled' }

interface Draft { id?: number; itemId?: number | null; itemName?: string; title: string; quantity: string; unit: string; expectedPrice: string; neededBy: string; supplier: string; notes: string; source: Note['source'] }
const fromNote = (n: Note): Draft => ({
  id: n.id, itemId: n.item?.id ?? null, itemName: n.item?.name, title: n.title, quantity: numText(n.quantity), unit: n.unit ?? 'KG',
  expectedPrice: numText(n.expectedPrice), neededBy: n.neededBy ?? '', supplier: n.supplier ? String(n.supplier.id) : '', notes: n.notes ?? '', source: n.source,
})
const fromSuggestion = (s: Suggestion): Draft => ({
  itemId: s.item.id, itemName: s.item.name, title: s.item.name, quantity: s.reorderLevel ? String(Math.max(0, Math.ceil(s.reorderLevel * 2 - s.stock))) : '',
  unit: s.unit, expectedPrice: '', neededBy: '', supplier: '', notes: s.reason, source: 'LOW_STOCK',
})
const blank = (): Draft => ({ title: '', quantity: '', unit: 'KG', expectedPrice: '', neededBy: '', supplier: '', notes: '', source: 'MANUAL' })

function NoteSheet({ draft, onClose, onSaved }: { draft: Draft; onClose: () => void; onSaved: () => void }) {
  const { mo } = useMoney()
  const { notify } = useStore()
  const [d, setD] = useState(draft)
  const [busy, setBusy] = useState(false)
  const set = (p: Partial<Draft>) => setD((x) => ({ ...x, ...p }))
  const save = async () => {
    if (!d.title.trim()) { notify('Enter what to buy'); return }
    setBusy(true)
    try {
      await mo.saveNote({
        itemId: d.itemId ?? null, title: d.title.trim(), quantity: toNum(d.quantity) ?? null, unit: d.unit || null, expectedPrice: toNum(d.expectedPrice) ?? null,
        neededBy: d.neededBy || null, supplierContactId: d.supplier ? Number(d.supplier) : null, notes: d.notes.trim() || null, source: d.source,
      }, d.id)
      notify(d.id ? 'Note updated' : 'Added to procurement'); onSaved()
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  const remove = async () => {
    if (!d.id) return
    setBusy(true)
    try { await mo.deleteNote(d.id); notify('Note deleted'); onSaved() } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  return (
    <Sheet label={d.id ? 'Edit note' : 'Add note'} onClose={onClose} tall>
      <div className="flex items-center justify-between px-4 pb-2"><h2 className="m-0 text-xl font-bold">{d.id ? 'Edit note' : 'Procurement note'}</h2></div>
      <div className="flex flex-col gap-3 overflow-y-auto bg-ground px-4 py-3">
        <div><label className="lab" htmlFor="pn-title">What to buy *</label><input className="inp" id="pn-title" value={d.title} onChange={(e) => set({ title: e.target.value })} />
          {d.itemName && <div className="hint">Stock item: {d.itemName} — receiving adds it to stock.</div>}</div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="pn-q">Quantity</label><input className="inp" id="pn-q" inputMode="decimal" value={d.quantity} onChange={(e) => set({ quantity: decimal(e.target.value) })} /></div>
          <div><label className="lab" htmlFor="pn-u">Unit</label>
            <select className="inp" id="pn-u" value={d.unit} onChange={(e) => set({ unit: e.target.value })}>{['KG', 'L', 'STRAW', 'DOSE', 'NOS'].map((u) => <option key={u} value={u}>{u.toLowerCase()}</option>)}</select></div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="pn-p">Expected ₹</label><input className="inp" id="pn-p" inputMode="decimal" value={d.expectedPrice} onChange={(e) => set({ expectedPrice: decimal(e.target.value) })} /></div>
          <div><label className="lab" htmlFor="pn-by">Needed by</label><input className="inp" id="pn-by" type="date" value={d.neededBy} onChange={(e) => set({ neededBy: e.target.value })} /></div>
        </div>
        <div><label className="lab" htmlFor="pn-sup">Supplier</label><ContactSelect id="pn-sup" role="SUPPLIER" value={d.supplier} onChange={(v) => set({ supplier: v })} newLabel="New supplier" /></div>
        <div><label className="lab" htmlFor="pn-notes">Notes</label><input className="inp" id="pn-notes" value={d.notes} onChange={(e) => set({ notes: e.target.value })} /></div>
        {d.id && <button type="button" className="btn2 h-11 text-sm text-danger" disabled={busy} onClick={remove}>Delete note</button>}
      </div>
      <div className="border-t border-line-soft bg-white p-4"><button type="button" className="btn" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save note'}</button></div>
    </Sheet>
  )
}

function ReceiveSheet({ note, onClose, onSaved }: { note: Note; onClose: () => void; onSaved: () => void }) {
  const { mo } = useMoney()
  const { notify } = useStore()
  const [qty, setQty] = useState(numText(note.quantity))
  const [total, setTotal] = useState(numText(note.expectedPrice))
  const [on, setOn] = useState(todayIso())
  const [method, setMethod] = useState<PaymentMethod>('UPI')
  const [paid, setPaid] = useState(true)
  const [busy, setBusy] = useState(false)
  const save = async () => {
    setBusy(true)
    try {
      await mo.receive(note.id, { receivedOn: on, quantity: toNum(qty) ?? null, totalAmount: toNum(total) ?? null, paymentMethod: method, paid })
      notify(`${note.title} received`); onSaved()
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  return (
    <Sheet label="Mark received" onClose={onClose}>
      <div className="flex items-center justify-between px-4 pb-2"><h2 className="m-0 text-xl font-bold">Received · {note.title}</h2></div>
      <div className="flex flex-col gap-3 overflow-y-auto bg-ground px-4 py-3">
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="rc-q">Quantity ({(note.unit ?? 'kg').toLowerCase()})</label><input className="inp" id="rc-q" inputMode="decimal" value={qty} onChange={(e) => setQty(decimal(e.target.value))} /></div>
          <div><label className="lab" htmlFor="rc-t">Total ₹</label><input className="inp" id="rc-t" inputMode="decimal" value={total} onChange={(e) => setTotal(decimal(e.target.value))} /></div>
        </div>
        <div><label className="lab" htmlFor="rc-on">Received on</label><input className="inp" id="rc-on" type="date" max={todayIso()} value={on} onChange={(e) => e.target.value && setOn(e.target.value)} /></div>
        <div><span className="lab">Payment</span><PaymentChips value={method} onChange={setMethod} /></div>
        <Segmented label="Payment status" value={paid ? 'PAID' : 'PENDING'} onChange={(v) => setPaid(v === 'PAID')} options={[{ value: 'PAID', label: 'Paid' }, { value: 'PENDING', label: 'Pending' }]} />
        <div className="rounded-xl bg-brand-soft px-3.5 py-3 text-sm">{note.item ? `Adds ${qty || '…'} ${(note.unit ?? 'kg').toLowerCase()} of ${note.item.name} to stock and an expense to Finance.` : 'Adds an expense to Finance.'}</div>
      </div>
      <div className="border-t border-line-soft bg-white p-4"><button type="button" className="btn" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Mark received'}</button></div>
    </Sheet>
  )
}

export default function Procurement() {
  const { mo, isOwner } = useMoney()
  const { notify } = useStore()
  const [filter, setFilter] = useState<Filter>('ALL')
  const page = useLoad(isOwner ? () => mo.procurement() : null, [mo, isOwner])
  const [draft, setDraft] = useState<Draft | null>(null)
  const [receiving, setReceiving] = useState<Note | null>(null)
  if (!isOwner) return <OwnerOnlyScreen title="Procurement" back="/money" />
  const p = page.data
  const notes = (p?.notes ?? []).filter((n) => filter === 'ALL' || n.status === filter)
  const order = async (n: Note) => {
    try { await mo.order(n.id, todayIso()); notify(`${n.title} marked ordered`); void page.reload() } catch (e) { notify(errorMessage(e)) }
  }
  const done = () => { setDraft(null); setReceiving(null); void page.reload() }

  return (
    <Screen>
      <TopBar title="Procurement" back="/money" right={<button type="button" onClick={() => setDraft(blank())} className="flex h-10 items-center rounded-[10px] border-0 bg-brand px-3.5 text-sm font-semibold text-white">+ Add note</button>} />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <LoadState loading={page.loading} error={page.error} onRetry={page.reload} empty={!p} />
        {p && p.suggestions.length > 0 && (
          <div className="overflow-hidden rounded-[14px] bg-white">
            <div className="bg-warn-soft px-3.5 py-2.5 text-sm font-bold text-warn-ink">Suggested from low stock</div>
            {p.suggestions.map((s) => (
              <div key={`${s.item.type}${s.item.id}`} className="flex items-center gap-2.5 border-t border-line-faint px-3.5 py-2.5">
                <span className="min-w-0 flex-1"><b className="text-[15px]">{s.item.name}</b> <span className="text-[13px] text-ink-3">· {numText(s.stock)} {s.unit.toLowerCase()} left</span>
                  <span className="block text-[13px] text-ink-3">{s.reason}</span></span>
                <button type="button" onClick={() => setDraft(fromSuggestion(s))} className="h-10 rounded-[10px] border border-line bg-white px-3 text-sm font-semibold">+ Add</button>
              </div>
            ))}
          </div>
        )}
        {p && (
          <ChoiceChips label="Status" value={filter} onChange={setFilter} options={[
            { value: 'ALL', label: `All ${p.counts.all}` }, { value: 'PLANNED', label: `Planned ${p.counts.planned}` },
            { value: 'ORDERED', label: `Ordered ${p.counts.ordered}` }, { value: 'RECEIVED', label: `Received ${p.counts.received}` },
          ]} />
        )}
        {p && notes.length === 0 && <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">{filter === 'ALL' ? 'No notes yet. Add one, or use the suggestions above and the feed-need page.' : 'Nothing here.'}</div>}
        {notes.map((n) => (
          <div key={n.id} className={`rounded-[14px] bg-white p-3.5 ${n.status === 'RECEIVED' || n.status === 'CANCELLED' ? 'opacity-75' : ''}`}>
            <div className="flex items-center gap-2"><b className="flex-1 text-base">{n.title}</b><span className={`badge ${TONE[n.status]}`}>{LABEL[n.status]}</span></div>
            <div className="mt-0.5 text-[13px] text-ink-3">
              {n.status === 'RECEIVED' ? `Received ${shortDate(n.receivedOn)}${n.paidAmount != null ? ` · paid ${rupees(n.paidAmount)}` : ''}`
                : [n.supplier ? `Supplier: ${n.supplier.name}` : null, n.orderedOn ? `ordered ${shortDate(n.orderedOn)}` : null, n.notes].filter(Boolean).join(' · ')}
            </div>
            {n.status !== 'RECEIVED' && n.status !== 'CANCELLED' && (
              <>
                <div className="mt-2.5 grid grid-cols-3 gap-2">
                  <div className="rounded-[10px] bg-ground px-2.5 py-2"><span className="block text-xs font-semibold text-ink-3">Qty</span><b>{n.quantity != null ? `${numText(n.quantity)} ${(n.unit ?? '').toLowerCase()}` : '—'}</b></div>
                  <div className="rounded-[10px] bg-ground px-2.5 py-2"><span className="block text-xs font-semibold text-ink-3">Expected</span><b>{rupees(n.expectedPrice)}</b></div>
                  <div className="rounded-[10px] bg-ground px-2.5 py-2"><span className="block text-xs font-semibold text-ink-3">Needed by</span><b>{n.neededBy ? shortDate(n.neededBy) : '—'}</b></div>
                </div>
                <div className="mt-2.5 flex gap-2">
                  <button type="button" className="btn2 h-11 flex-1 text-sm" onClick={() => setDraft(fromNote(n))}>Edit</button>
                  {n.status === 'PLANNED'
                    ? <button type="button" className="btn h-11 flex-1 text-sm" onClick={() => order(n)}>Mark ordered</button>
                    : <button type="button" className="btn h-11 flex-1 text-sm" onClick={() => setReceiving(n)}>Mark received</button>}
                </div>
                {n.status === 'ORDERED' && n.item && <div className="mt-1.5 text-xs text-ink-3">Received → adds stock and an expense</div>}
              </>
            )}
          </div>
        ))}
      </div>
      <Footer className="mt-auto" />
      {draft && <NoteSheet draft={draft} onClose={() => setDraft(null)} onSaved={done} />}
      {receiving && <ReceiveSheet note={receiving} onClose={() => setReceiving(null)} onSaved={done} />}
    </Screen>
  )
}
