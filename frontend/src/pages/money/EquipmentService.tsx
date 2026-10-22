import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Screen, Segmented, TopBar } from '../../components/ui'
import { ContactSelect, LoadState } from '../../components/records'
import { AmountInput, ChoiceChips, OwnerOnlyScreen, PaymentChips, useMoney } from '../../components/money'
import { useStore } from '../../data/store'
import { addDays, shortDate, todayIso, toNum, useLoad } from '../../data/records'
import { rupees, WORK_DONE, type WorkDone } from '../../data/money'
import type { PaymentMethod } from '../../data/feed'
import { errorMessage } from '../../lib/api'

export default function EquipmentService() {
  const { id = '' } = useParams()
  const { mo, isOwner } = useMoney()
  const { notify } = useStore()
  const nav = useNavigate()
  const list = useLoad(isOwner ? () => mo.equipment() : null, [mo, isOwner])
  const [equipId, setEquipId] = useState(id)
  const [date, setDate] = useState(todayIso())
  const [work, setWork] = useState<WorkDone>('ROUTINE')
  const [cost, setCost] = useState('')
  const [method, setMethod] = useState<PaymentMethod>('CASH')
  const [paid, setPaid] = useState(true)
  const [mech, setMech] = useState('')
  const [mechName, setMechName] = useState('')
  const [notes, setNotes] = useState('')
  const [nextOverride, setNextOverride] = useState('')
  const [busy, setBusy] = useState(false)
  if (!isOwner) return <OwnerOnlyScreen title="Log service" back="/money" />
  const e = list.data?.items.find((x) => String(x.id) === equipId)
  const autoNext = e?.serviceEveryDays ? addDays(date, e.serviceEveryDays) : ''
  const next = nextOverride || autoNext
  const amt = toNum(cost)

  const save = async () => {
    if (!e) { notify('Choose the equipment'); return }
    setBusy(true)
    try {
      await mo.logService(e.id, {
        date, workDone: work, cost: amt ?? null, paymentMethod: method, paid,
        mechanicContactId: mech ? Number(mech) : null, mechanicName: mech ? null : mechName.trim() || null,
        notes: notes.trim() || null, nextServiceDate: nextOverride || null,
      })
      notify(`Service saved for ${e.name}`)
      nav('/money/equipment')
    } catch (err) { notify(errorMessage(err)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title="Log service" close="/money/equipment" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <LoadState loading={list.loading} error={list.error} onRetry={list.reload} empty={!list.data} />
        <div><label className="lab" htmlFor="es-eq">Equipment *</label>
          <select className="inp" id="es-eq" value={equipId} onChange={(x) => setEquipId(x.target.value)}>
            <option value="">Choose equipment</option>
            {list.data?.items.map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}
          </select>
          {e?.nextService && <div className={`hint ${e.status === 'OVERDUE' ? 'font-semibold text-danger-text' : ''}`}>Was due {shortDate(e.nextService)}{e.status === 'OVERDUE' ? ` · ${Math.abs(e.daysTo ?? 0)} days overdue` : ''}</div>}
        </div>
        <div><label className="lab" htmlFor="es-dt">Service date</label><input className="inp" id="es-dt" type="date" max={todayIso()} value={date} onChange={(x) => x.target.value && setDate(x.target.value)} /></div>
        <div><span className="lab">Work done</span><ChoiceChips label="Work done" wrap options={WORK_DONE} value={work} onChange={setWork} /></div>
        <AmountInput id="es-cost" label="Cost (₹)" value={cost} onChange={setCost} quick={[]} />
        <div><span className="lab">Paid by</span><PaymentChips value={method} onChange={setMethod} /></div>
        <Segmented label="Payment status" value={paid ? 'PAID' : 'PENDING'} onChange={(v) => setPaid(v === 'PAID')} options={[{ value: 'PAID', label: 'Paid' }, { value: 'PENDING', label: 'Pending' }]} />
        <div><label className="lab" htmlFor="es-mech">Mechanic</label><ContactSelect id="es-mech" role="SUPPLIER" value={mech} onChange={setMech} newLabel="New mechanic" />
          {!mech && <input className="inp mt-2" aria-label="Mechanic name (not in contacts)" placeholder="Or type a name" value={mechName} onChange={(x) => setMechName(x.target.value)} />}</div>
        {amt ? <div className="rounded-xl bg-brand-soft px-3.5 py-3 text-sm">Creates an <b>Equipment</b> expense of {rupees(amt)}{paid ? '' : ' (pending)'}.</div> : null}
        <div><label className="lab" htmlFor="es-notes">Notes</label><input className="inp" id="es-notes" placeholder="e.g. vacuum pump oil topped up" value={notes} onChange={(x) => setNotes(x.target.value)} /></div>
        <div className="rounded-[14px] bg-white p-3.5">
          <div className="flex items-baseline justify-between"><b className="text-[15px]">Next service</b><b>{next ? shortDate(next) : '—'}</b></div>
          <div className="hint mt-1">{e?.serviceEveryDays ? `Auto: every ${e.serviceEveryDays} days${e.reminderDays ? ` · reminder ${e.reminderDays} days before` : ''}` : 'Set "every N days" on the equipment for automatic dates.'}</div>
          <label className="lab mt-3" htmlFor="es-next">Change date</label><input className="inp" id="es-next" type="date" min={date} value={nextOverride} onChange={(x) => setNextOverride(x.target.value)} />
        </div>
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <button type="button" className="btn" disabled={busy || !e} onClick={save}>{busy ? 'Saving…' : 'Save service'}</button>
      </div>
    </Screen>
  )
}
