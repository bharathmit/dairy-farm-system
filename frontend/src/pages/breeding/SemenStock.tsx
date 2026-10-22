import { useState } from 'react'
import { Footer, Screen, Sheet, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { ContactSelect, LoadState } from '../../components/records'
import { useBreeding } from '../../components/breeding'
import { useStore } from '../../data/store'
import { decimal, numText, shortDate, toNum, useLoad } from '../../data/records'
import type { SemenBatch } from '../../data/breeding'
import { errorMessage } from '../../lib/api'

interface Draft { bullName: string; bullCode: string; breed: string; supplier: string; batchNo: string; expiryDate: string; costPerStraw: string; sexed: boolean; straws: string; notes: string }
const empty: Draft = { bullName: '', bullCode: '', breed: '', supplier: '', batchNo: '', expiryDate: '', costPerStraw: '', sexed: false, straws: '', notes: '' }

function BatchSheet({ batch, onClose, onSaved }: { batch?: SemenBatch; onClose: () => void; onSaved: () => void }) {
  const { br, isOwner } = useBreeding()
  const { notify } = useStore()
  const [d, setD] = useState<Draft>(batch ? {
    bullName: batch.bullName, bullCode: batch.bullCode ?? '', breed: batch.breed ?? '', supplier: batch.supplier ? String(batch.supplier.id) : '',
    batchNo: batch.batchNo ?? '', expiryDate: batch.expiryDate ?? '', costPerStraw: numText(batch.costPerStraw), sexed: Boolean(batch.sexed), straws: '', notes: batch.notes ?? '',
  } : empty)
  const [adjust, setAdjust] = useState('')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (p: Partial<Draft>) => setD((c) => ({ ...c, ...p }))
  const save = async () => {
    if (!d.bullName.trim()) { notify('Enter the bull name'); return }
    setBusy(true)
    try {
      await br.saveSemen({
        bullName: d.bullName.trim(), bullCode: d.bullCode.trim() || undefined, breed: d.breed.trim() || undefined,
        supplierContactId: d.supplier ? Number(d.supplier) : undefined, batchNo: d.batchNo.trim() || undefined, expiryDate: d.expiryDate || undefined,
        costPerStraw: isOwner ? toNum(d.costPerStraw) ?? null : null, sexed: d.sexed, straws: batch ? undefined : toNum(d.straws) ?? 0, notes: d.notes.trim() || undefined,
      }, batch?.id)
      const q = toNum(adjust.replace(/[^\d-]/g, ''))
      if (batch && q) await br.adjustSemen(batch.id, q, reason.trim() || (q > 0 ? 'Purchase' : 'Correction'))
      notify('Semen batch saved'); onSaved()
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  const remove = async () => {
    if (!batch) return
    try { await br.deleteSemen(batch.id); notify('Batch deleted'); onSaved() } catch (e) { notify(errorMessage(e)) }
  }
  return (
    <Sheet label={batch ? 'Edit semen batch' : 'Add semen batch'} onClose={onClose} tall>
      <div className="flex items-center justify-between px-4 pb-2"><h2 className="m-0 text-xl font-bold">{batch ? batch.bullName : 'Add semen batch'}</h2>
        <button type="button" aria-label="Close" onClick={onClose} className="flex h-11 w-11 items-center justify-center"><Icon name="close" /></button></div>
      <div className="flex flex-col gap-3 overflow-y-auto bg-ground px-4 py-3">
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="sm-name">Bull name *</label><input className="inp" id="sm-name" value={d.bullName} onChange={(e) => set({ bullName: e.target.value })} /></div>
          <div><label className="lab" htmlFor="sm-code">Bull code</label><input className="inp" id="sm-code" value={d.bullCode} onChange={(e) => set({ bullCode: e.target.value })} /></div>
          <div><label className="lab" htmlFor="sm-breed">Breed</label><input className="inp" id="sm-breed" value={d.breed} onChange={(e) => set({ breed: e.target.value })} /></div>
          <div><label className="lab" htmlFor="sm-batch">Batch no.</label><input className="inp" id="sm-batch" value={d.batchNo} onChange={(e) => set({ batchNo: e.target.value })} /></div>
          <div><label className="lab" htmlFor="sm-exp">Expiry date</label><input className="inp" id="sm-exp" type="date" value={d.expiryDate} onChange={(e) => set({ expiryDate: e.target.value })} /></div>
          {isOwner && <div><label className="lab" htmlFor="sm-cost">Cost ₹ / straw</label><input className="inp" id="sm-cost" inputMode="decimal" value={d.costPerStraw} onChange={(e) => set({ costPerStraw: decimal(e.target.value) })} /></div>}
          {!batch && <div><label className="lab" htmlFor="sm-straws">Straws bought</label><input className="inp" id="sm-straws" inputMode="numeric" value={d.straws} onChange={(e) => set({ straws: e.target.value.replace(/\D/g, '') })} /></div>}
        </div>
        <div><label className="lab" htmlFor="sm-sup">Supplier</label><ContactSelect id="sm-sup" role="SUPPLIER" value={d.supplier} onChange={(v) => set({ supplier: v })} newLabel="New supplier" /></div>
        <label className="flex min-h-11 items-center gap-2.5 text-[15px]"><input type="checkbox" checked={d.sexed} onChange={(e) => set({ sexed: e.target.checked })} className="h-5 w-5 accent-brand" />Sexed semen (female)</label>
        {batch && (
          <div className="flex flex-col gap-2 rounded-[14px] bg-white p-3.5">
            <b className="text-[15px]">Adjust stock · now {batch.straws}</b>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="lab" htmlFor="sm-adj">Add / remove straws</label><input className="inp" id="sm-adj" inputMode="numeric" placeholder="+10 or -1" value={adjust} onChange={(e) => setAdjust(e.target.value.replace(/[^\d+-]/g, ''))} /></div>
              <div><label className="lab" htmlFor="sm-why">Reason</label><input className="inp" id="sm-why" placeholder="Purchase, damaged…" value={reason} onChange={(e) => setReason(e.target.value)} /></div>
            </div>
          </div>
        )}
        <div><label className="lab" htmlFor="sm-notes">Notes</label><input className="inp" id="sm-notes" placeholder="e.g. can ID, refill date" value={d.notes} onChange={(e) => set({ notes: e.target.value })} /></div>
        {batch && isOwner && <button type="button" onClick={remove} className="h-11 rounded-xl border border-line bg-white text-sm font-semibold text-danger">Delete batch</button>}
      </div>
      <div className="border-t border-line-soft bg-white p-4"><button type="button" className="btn" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save batch'}</button></div>
    </Sheet>
  )
}

export default function SemenStock() {
  const { br, isOwner } = useBreeding()
  const res = useLoad(() => br.semen(), [br])
  const [breed, setBreed] = useState('ALL')
  const [editing, setEditing] = useState<SemenBatch | 'new' | null>(null)
  const t = res.data?.totals
  const batches = res.data?.batches ?? []
  const breeds = [...new Set(batches.map((b) => b.breed).filter(Boolean))] as string[]
  const list = breed === 'ALL' ? batches : breed === 'EMPTY' ? batches.filter((b) => b.straws === 0) : batches.filter((b) => b.breed === breed)

  return (
    <Screen>
      <TopBar title="Semen stock" back="/breeding" right={<button type="button" onClick={() => setEditing('new')} className="flex h-10 items-center rounded-[10px] border-0 bg-brand px-3 text-sm font-semibold text-white">+ Add batch</button>} />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <LoadState loading={res.loading} error={res.error} onRetry={res.reload} empty={!res.data} />
        {t && (
          <div className="grid grid-cols-3 gap-2.5">
            <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Straws</div><div className="mt-0.5 text-xl font-bold">{t.straws}</div></div>
            <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Low (&lt; 5)</div><div className={`mt-0.5 text-xl font-bold ${t.low ? 'text-danger' : ''}`}>{t.low}</div></div>
            <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Expiring 30 d</div><div className={`mt-0.5 text-xl font-bold ${t.expiring30 ? 'text-warn-ink' : ''}`}>{t.expiring30}</div></div>
          </div>
        )}
        {batches.length > 0 && (
          <div className="flex gap-2 overflow-x-auto pb-1">
            {['ALL', ...breeds, 'EMPTY'].map((b) => <button key={b} type="button" aria-pressed={breed === b} onClick={() => setBreed(b)} className={`chip ${breed === b ? 'chip-on' : ''}`}>{b === 'ALL' ? `All ${batches.length}` : b === 'EMPTY' ? 'Empty' : b}</button>)}
          </div>
        )}
        {list.map((b) => (
          <button key={b.id} type="button" onClick={() => setEditing(b)} className={`flex items-center gap-3 rounded-[14px] border-0 bg-white p-3.5 text-left ${b.straws === 0 ? 'opacity-60' : ''}`}>
            <span className={`flex w-14 shrink-0 flex-col items-center rounded-xl py-1.5 ${b.low ? 'bg-danger-soft text-danger-text' : 'bg-brand-soft text-brand'}`}><b className="text-xl">{b.straws}</b><span className="text-[11px] font-semibold">straws</span></span>
            <span className="min-w-0 flex-1">
              <b>{b.bullName}</b> <span className="text-ink-3">{b.breed ? `· ${b.breed}` : ''}</span>
              <div className="text-[13px] text-ink-3">{[b.bullCode && `Code ${b.bullCode}`, b.supplier?.name].filter(Boolean).join(' · ')}</div>
              <div className="text-[13px] text-ink-3">{b.straws === 0 && b.usedUpOn ? `Used up ${shortDate(b.usedUpOn)} · history kept` : [b.expiryDate && `Expires ${shortDate(b.expiryDate)} ${b.expiryDate.slice(0, 4)}`, isOwner && b.costPerStraw != null && `₹${b.costPerStraw}/straw`, b.sexed && 'sexed (female)'].filter(Boolean).join(' · ')}</div>
              {(b.low || b.expiringSoon) && b.straws > 0 && <div className="mt-1 flex gap-1.5">{b.low && <span className="badge bg-danger-soft text-danger-text">Low stock</span>}{b.expiringSoon && <span className="badge bg-warn-soft text-warn-ink">Expires soon</span>}</div>}
            </span>
          </button>
        ))}
        {res.data && batches.length === 0 && <div className="rounded-[14px] bg-white px-4 py-5 text-center text-sm text-ink-3">No semen batches yet. <button type="button" onClick={() => setEditing('new')} className="border-0 bg-transparent p-0 font-semibold text-brand">Add the first one</button></div>}
        <div className="text-xs leading-relaxed text-ink-3">Straws drop each time an AI service is saved.</div>
      </div>
      <Footer className="mt-auto" />
      {editing && <BatchSheet batch={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); void res.reload() }} />}
    </Screen>
  )
}
