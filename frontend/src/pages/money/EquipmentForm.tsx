import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Dialog, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { OwnerOnlyScreen, useMoney } from '../../components/money'
import { useStore } from '../../data/store'
import { decimal, shortDate, todayIso, toNum, useLoad } from '../../data/records'
import { rupees, workDoneLabel } from '../../data/money'
import { errorMessage } from '../../lib/api'

export default function EquipmentForm() {
  const { id } = useParams()
  const { mo, isOwner } = useMoney()
  const { notify } = useStore()
  const nav = useNavigate()
  const existing = useLoad(id && isOwner ? async () => (await mo.equipment()).items.find((x) => String(x.id) === id) ?? null : null, [mo, id, isOwner])
  const services = useLoad(id && isOwner ? () => mo.services(id) : null, [mo, id, isOwner])
  const [f, setF] = useState({ name: '', brand: '', model: '', serviceEveryDays: '', serviceTask: '', reminderDays: '3', purchaseDate: '', purchaseCost: '', notes: '' })
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const e = existing.data
  useEffect(() => {
    if (!e) return
    setF({
      name: e.name, brand: e.brand ?? '', model: e.model ?? '', serviceEveryDays: e.serviceEveryDays != null ? String(e.serviceEveryDays) : '',
      serviceTask: e.serviceTask ?? '', reminderDays: e.reminderDays != null ? String(e.reminderDays) : '3', purchaseDate: e.purchaseDate ?? '',
      purchaseCost: e.purchaseCost != null ? String(e.purchaseCost) : '', notes: e.notes ?? '',
    })
  }, [e])
  if (!isOwner) return <OwnerOnlyScreen title="Equipment" back="/money" />
  const set = (p: Partial<typeof f>) => setF((x) => ({ ...x, ...p }))

  const save = async () => {
    if (!f.name.trim()) { notify('Enter a name'); return }
    setBusy(true)
    try {
      await mo.saveEquipment({
        name: f.name.trim(), brand: f.brand.trim() || null, model: f.model.trim() || null,
        serviceEveryDays: toNum(f.serviceEveryDays) ?? null, serviceTask: f.serviceTask.trim() || null, reminderDays: toNum(f.reminderDays) ?? null,
        purchaseDate: f.purchaseDate || null, purchaseCost: toNum(f.purchaseCost) ?? null, notes: f.notes.trim() || null,
      }, id)
      notify(`${f.name.trim()} saved`)
      nav('/money/equipment')
    } catch (err) { notify(errorMessage(err)) } finally { setBusy(false) }
  }
  const remove = async () => {
    if (!e) return
    try { await mo.deleteEquipment(e.id); notify(`${e.name} deleted`); nav('/money/equipment') } catch (err) { notify(errorMessage(err)) } finally { setConfirm(false) }
  }

  return (
    <Screen>
      <TopBar title={id ? 'Edit equipment' : 'Add equipment'} close="/money/equipment" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        {id && <LoadState loading={existing.loading} error={existing.error} onRetry={existing.reload} empty={!e} />}
        <div><label className="lab" htmlFor="eq-name">Name *</label><input className="inp" id="eq-name" placeholder="e.g. Milking machine" value={f.name} onChange={(x) => set({ name: x.target.value })} /></div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="eq-brand">Brand</label><input className="inp" id="eq-brand" value={f.brand} onChange={(x) => set({ brand: x.target.value })} /></div>
          <div><label className="lab" htmlFor="eq-model">Model</label><input className="inp" id="eq-model" value={f.model} onChange={(x) => set({ model: x.target.value })} /></div>
        </div>
        <div className="rounded-[14px] bg-white p-3.5">
          <b className="text-[15px]">Service schedule</b>
          <div className="mt-3"><label className="lab" htmlFor="eq-task">What is done</label><input className="inp" id="eq-task" placeholder="e.g. Blade sharpen, gasket check" value={f.serviceTask} onChange={(x) => set({ serviceTask: x.target.value })} /></div>
          <div className="mt-3 grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="eq-every">Every (days)</label><input className="inp" id="eq-every" inputMode="numeric" value={f.serviceEveryDays} onChange={(x) => set({ serviceEveryDays: x.target.value.replace(/\D/g, '') })} /></div>
            <div><label className="lab" htmlFor="eq-rem">Remind (days before)</label><input className="inp" id="eq-rem" inputMode="numeric" value={f.reminderDays} onChange={(x) => set({ reminderDays: x.target.value.replace(/\D/g, '') })} /></div>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="eq-pd">Bought on</label><input className="inp" id="eq-pd" type="date" max={todayIso()} value={f.purchaseDate} onChange={(x) => set({ purchaseDate: x.target.value })} /></div>
          <div><label className="lab" htmlFor="eq-pc">Price ₹</label><input className="inp" id="eq-pc" inputMode="decimal" value={f.purchaseCost} onChange={(x) => set({ purchaseCost: decimal(x.target.value) })} /></div>
        </div>
        <div><label className="lab" htmlFor="eq-notes">Notes</label><input className="inp" id="eq-notes" placeholder="Optional" value={f.notes} onChange={(x) => set({ notes: x.target.value })} /></div>
        {id && e && (
          <div className="rounded-[14px] bg-white p-3.5">
            <div className="flex items-baseline justify-between"><b className="text-[15px]">Service history</b><Link to={`/money/equipment/${e.id}/service`} className="text-sm font-semibold">+ Log service</Link></div>
            {services.data?.length === 0 && <div className="mt-2 text-sm text-ink-3">No services logged yet.</div>}
            {services.data?.map((s) => (
              <div key={s.id} className="flex items-center justify-between gap-2 border-t border-line-faint py-2 text-sm first-of-type:border-t-0">
                <span className="min-w-0"><b>{shortDate(s.date)}</b> · {workDoneLabel(s.workDone)}<span className="block truncate text-[13px] text-ink-3">{s.mechanic?.name ?? s.mechanicName ?? ''}{s.notes ? ` · ${s.notes}` : ''}</span></span>
                <b>{rupees(s.cost)}</b>
              </div>
            ))}
          </div>
        )}
        {id && e && <button type="button" className="btn2 h-12 text-danger" onClick={() => setConfirm(true)}>Delete equipment</button>}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <button type="button" className="btn" disabled={busy || (Boolean(id) && !e)} onClick={save}>{busy ? 'Saving…' : 'Save equipment'}</button>
      </div>
      {confirm && (
        <Dialog title={`Delete ${e?.name}?`} onClose={() => setConfirm(false)}>
          <p className="mt-0 text-[15px] text-ink-2">Its service history is removed too. Service costs already in Finance stay.</p>
          <div className="mt-4 grid grid-cols-2 gap-2.5">
            <button type="button" onClick={() => setConfirm(false)} className="h-12 rounded-xl border border-line bg-white font-semibold">Cancel</button>
            <button type="button" onClick={remove} className="h-12 rounded-xl bg-danger font-semibold text-white">Delete</button>
          </div>
        </Dialog>
      )}
    </Screen>
  )
}
