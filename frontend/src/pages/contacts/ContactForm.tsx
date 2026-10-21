import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Dialog, Screen, Switch, TopBar } from '../../components/ui'
import { contactBody, ContactFields, contactProblem, emptyContact, LoadState, useRecords } from '../../components/records'
import { useStore } from '../../data/store'
import { useLoad, type ContactInput } from '../../data/records'
import { errorMessage } from '../../lib/api'

export default function ContactForm() {
  const { id } = useParams()
  const { rec, isOwner } = useRecords()
  const { notify } = useStore()
  const nav = useNavigate()
  const existing = useLoad(id ? () => rec.contact(id) : null, [rec, id])
  const [c, setC] = useState<ContactInput>(emptyContact())
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)

  useEffect(() => {
    const e = existing.data
    if (e) setC({ name: e.name, roles: e.roles, phone: e.phone ?? '', village: e.village ?? '', milkRate: e.milkRate ?? null, payEvery: e.payEvery ?? null, notes: e.notes ?? '', active: e.active })
  }, [existing.data])

  const save = async () => {
    const p = contactProblem(c)
    if (p) { notify(p); return }
    setBusy(true)
    try {
      // Workers don't see rates, so they never send one (the saved rate is kept).
      const body = contactBody(c)
      if (!isOwner) body.milkRate = existing.data?.milkRate ?? null
      await rec.saveContact(body, id ? Number(id) : undefined)
      notify('Contact saved')
      nav('/settings/contacts')
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  const remove = async () => {
    setConfirm(false)
    try { await rec.deleteContact(Number(id)); notify('Contact deleted'); nav('/settings/contacts') } catch (e) { notify(errorMessage(e)) }
  }

  return (
    <Screen>
      <TopBar title={id ? 'Edit contact' : 'Add contact'} close="/settings/contacts" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        {id && <LoadState loading={existing.loading} error={existing.error} onRetry={existing.reload} empty={!existing.data} />}
        {(!id || existing.data) && (
          <>
            <ContactFields value={c} onChange={setC} showMoney={isOwner} />
            <div className="flex min-h-12 items-center justify-between rounded-xl bg-white px-3.5">
              <span className="text-[15px]">Active <span className="text-[13px] text-ink-3">· shows in dropdowns</span></span>
              <Switch label="Active" checked={c.active} onChange={(v) => setC({ ...c, active: v })} />
            </div>
            {id && isOwner && <button type="button" onClick={() => setConfirm(true)} className="h-12 rounded-xl border border-line bg-white font-semibold text-danger">Delete contact</button>}
          </>
        )}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <button type="button" className="btn" disabled={busy || (Boolean(id) && !existing.data)} onClick={save}>{busy ? 'Saving…' : 'Save contact'}</button>
      </div>
      {confirm && (
        <Dialog title="Delete this contact?" onClose={() => setConfirm(false)}>
          <p className="m-0 mb-5 text-[15px] text-ink-2">If they are on any sale or health record, make them inactive instead.</p>
          <div className="grid grid-cols-2 gap-3">
            <button type="button" onClick={() => setConfirm(false)} className="h-12 rounded-xl border border-line bg-white font-semibold">Cancel</button>
            <button type="button" onClick={remove} className="h-12 rounded-xl bg-danger font-semibold text-white">Delete</button>
          </div>
        </Dialog>
      )}
    </Screen>
  )
}
