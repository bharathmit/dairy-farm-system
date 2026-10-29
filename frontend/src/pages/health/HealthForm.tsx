import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Dialog, Screen, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { AnimalSelect, LoadState, useRecords } from '../../components/records'
import { useStore } from '../../data/store'
import { numText, todayIso, useLoad } from '../../data/records'
import { errorMessage } from '../../lib/api'
import HealthFields, { draftBody, emptyDraft, type HealthDraft } from './HealthFields'

export default function HealthForm() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const { rec, isOwner } = useRecords()
  const { notify } = useStore()
  const nav = useNavigate()
  const existing = useLoad(id ? () => rec.healthRecord(id) : null, [rec, id])
  const [animalId, setAnimalId] = useState(params.get('animal') ?? '')
  const [d, setD] = useState<HealthDraft>(() => {
    const t = params.get('type') as HealthDraft['type'] | null
    const base = emptyDraft(todayIso(), t && ['ILLNESS', 'TREATMENT', 'VACCINATION', 'DEWORMING', 'INJURY'].includes(t) ? t : undefined)
    return { ...base, medicine: params.get('medicine') ?? '' }
  })
  // Opened from a deworming / vaccination schedule alert: the record closes that dose.
  const schedule = params.get('schedule') ? { scheduleId: Number(params.get('schedule')), scheduleStep: params.get('step') ?? undefined } : {}
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const set = (p: Partial<HealthDraft>) => setD((cur) => ({ ...cur, ...p }))
  const back = params.get('back') || '/health'

  useEffect(() => {
    const r = existing.data
    if (!r) return
    setAnimalId(String(r.animalId))
    setD({
      date: r.date, type: r.type, symptoms: r.symptoms ?? '', diagnosis: r.diagnosis ?? '', medicine: r.medicine ?? '', dose: r.dose ?? '',
      route: r.route ?? '', vet: r.vet ? String(r.vet.id) : '', cost: numText(r.cost), milkDays: numText(r.milkWithdrawalDays),
      meatDays: numText(r.meatWithdrawalDays), nextDate: r.nextActionDate ?? '', nextAction: r.nextAction ?? '', notes: r.notes ?? '',
    })
  }, [existing.data])

  const save = async () => {
    if (!animalId) { notify('Choose an animal'); return }
    setBusy(true)
    try {
      await rec.saveHealth({ ...draftBody(d, isOwner), ...schedule, animalId: Number(animalId) }, id)
      notify('Health record saved')
      nav(back, { replace: true })
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  const remove = async () => {
    try { await rec.deleteHealth(Number(id)); notify('Health record deleted'); nav(back, { replace: true }) } catch (e) { notify(errorMessage(e)) }
  }
  const toggleDone = async () => {
    if (!existing.data) return
    try { existing.setData(await rec.followUp(existing.data.id, !existing.data.nextActionDone)); notify(existing.data.nextActionDone ? 'Follow-up reopened' : 'Follow-up done') } catch (e) { notify(errorMessage(e)) }
  }

  return (
    <Screen>
      <TopBar title={id ? 'Health record' : 'Add health record'} close={back} />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        {id && <LoadState loading={existing.loading} error={existing.error} onRetry={existing.reload} empty={!existing.data} />}
        {(!id || existing.data) && (
          <>
            <div>
              <label className="lab" htmlFor="hf-an">Animal *</label>
              <div className="flex gap-2">
                <div className="flex-1"><AnimalSelect id="hf-an" value={animalId} onChange={setAnimalId} /></div>
                <Link to="/animals/scan" aria-label="Scan ear tag" className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[10px] border border-line bg-white"><Icon name="scan" /></Link>
              </div>
            </div>
            <div><label className="lab" htmlFor="hf-dt">Date</label><input className="inp" id="hf-dt" type="date" max={todayIso()} value={d.date} onChange={(e) => e.target.value && set({ date: e.target.value })} /></div>
            {existing.data?.nextActionDate && (
              <button type="button" onClick={toggleDone} className="flex min-h-12 items-center justify-between rounded-xl border-0 bg-info-soft px-3.5 text-left text-sm text-info-text">
                <span>Follow-up: <b>{existing.data.nextAction || 'next action'}</b> · {existing.data.nextActionDone ? 'done' : 'open'}</span>
                <span className="font-semibold">{existing.data.nextActionDone ? 'Reopen' : 'Mark done'}</span>
              </button>
            )}
            <HealthFields d={d} set={set} showMoney={isOwner} />
            {id && isOwner && <button type="button" onClick={() => setConfirm(true)} className="h-12 rounded-xl border border-line bg-white font-semibold text-danger">Delete record</button>}
          </>
        )}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <button type="button" className="btn" disabled={busy || (Boolean(id) && !existing.data)} onClick={save}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
      {confirm && (
        <Dialog title="Delete this record?" onClose={() => setConfirm(false)}>
          <p className="m-0 mb-5 text-[15px] text-ink-2">Its withdrawal and follow-up are removed too.</p>
          <div className="grid grid-cols-2 gap-3">
            <button type="button" onClick={() => setConfirm(false)} className="h-12 rounded-xl border border-line bg-white font-semibold">Cancel</button>
            <button type="button" onClick={remove} className="h-12 rounded-xl bg-danger font-semibold text-white">Delete</button>
          </div>
        </Dialog>
      )}
    </Screen>
  )
}
