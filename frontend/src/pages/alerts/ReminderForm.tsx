import { useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Dialog, Screen, Switch, TopBar } from '../../components/ui'
import { AnimalSelect, LoadState } from '../../components/records'
import { refreshAlertCount, useAlerts } from '../../components/alerts'
import { ChoiceChips } from '../../components/money'
import { useStore } from '../../data/store'
import { REMINDER_TYPES, REPEATS, type ReminderType, type Repeat } from '../../data/alerts'
import { todayIso, useLoad } from '../../data/records'
import { errorMessage } from '../../lib/api'

/** New or edited reminder: a manual alert with an optional cow, repeat and worker visibility. */
export default function ReminderForm() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const { al, farmId } = useAlerts()
  const { notify } = useStore()
  const nav = useNavigate()
  const existing = useLoad(id ? () => al.alert(id) : null, [al, id])
  const [title, setTitle] = useState('')
  const [notes, setNotes] = useState('')
  const [date, setDate] = useState(todayIso())
  const [time, setTime] = useState('09:00')
  const [type, setType] = useState<ReminderType>('OTHER')
  const [animalId, setAnimalId] = useState(params.get('animal') ?? '')
  const [workers, setWorkers] = useState(true)
  const [repeat, setRepeat] = useState<Repeat>('NONE')
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)

  useEffect(() => {
    const a = existing.data
    if (!a) return
    setTitle(a.title); setNotes(a.notes ?? a.detail ?? ''); setDate(a.dueDate ?? todayIso()); setTime(a.dueTime?.slice(0, 5) ?? '')
    setType(a.type ?? 'OTHER'); setAnimalId(a.animal ? String(a.animal.id) : ''); setWorkers(a.workerVisible); setRepeat(a.repeat ?? 'NONE')
  }, [existing.data])

  const save = async () => {
    if (!title.trim()) { notify('Enter a title'); return }
    setBusy(true)
    try {
      const a = await al.saveReminder({
        title: title.trim(), notes: notes.trim() || null, date, time: time || null, type,
        animalId: animalId ? Number(animalId) : null, workerVisible: workers, repeat,
      }, id)
      notify('Reminder saved'); refreshAlertCount(farmId); nav(`/alerts/${a.id}`, { replace: true })
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  const remove = async () => {
    try { await al.deleteReminder(Number(id)); notify('Reminder deleted'); refreshAlertCount(farmId); nav('/alerts') } catch (e) { notify(errorMessage(e)) }
  }

  return (
    <Screen>
      <TopBar title={id ? 'Edit reminder' : 'New reminder'} close={id ? `/alerts/${id}` : '/alerts'} />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        {id && <LoadState loading={existing.loading} error={existing.error} onRetry={existing.reload} empty={!existing.data} />}
        <div><label className="lab" htmlFor="rm-title">Title *</label><input className="inp" id="rm-title" placeholder="e.g. Vet visit — check Malar's udder" value={title} onChange={(e) => setTitle(e.target.value)} /></div>
        <div><label className="lab" htmlFor="rm-notes">Notes</label><textarea className="inp h-[76px] resize-none py-3" id="rm-notes" placeholder="Optional" value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="rm-date">Date</label><input className="inp" id="rm-date" type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} /></div>
          <div><label className="lab" htmlFor="rm-time">Time</label><input className="inp" id="rm-time" type="time" value={time} onChange={(e) => setTime(e.target.value)} /></div>
        </div>
        <div><span className="lab">Type</span><ChoiceChips label="Type" options={REMINDER_TYPES} value={type} onChange={setType} wrap /></div>
        <div><label className="lab" htmlFor="rm-cow">Cow (optional)</label><AnimalSelect id="rm-cow" value={animalId} onChange={setAnimalId} placeholder="None — whole farm" /></div>
        <div className="flex items-center gap-3 rounded-[14px] bg-white p-3.5">
          <div className="flex-1"><b>Visible to workers</b><div className="text-[13px] text-ink-3">Shows on workers' home screen</div></div>
          <Switch checked={workers} onChange={setWorkers} label="Visible to workers" />
        </div>
        <div><label className="lab" htmlFor="rm-rep">Repeat</label>
          <select className="inp" id="rm-rep" value={repeat} onChange={(e) => setRepeat(e.target.value as Repeat)}>{REPEATS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}</select>
        </div>
        {id && <button type="button" onClick={() => setConfirm(true)} className="h-12 rounded-xl border border-line bg-white font-semibold text-danger">Delete reminder</button>}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-4 pt-3.5">
        <button type="button" className="btn" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save reminder'}</button>
      </div>
      {confirm && (
        <Dialog title="Delete this reminder?" onClose={() => setConfirm(false)}>
          <div className="mt-4 grid grid-cols-2 gap-2.5">
            <button type="button" onClick={() => setConfirm(false)} className="h-12 rounded-xl border border-line bg-white font-semibold">Cancel</button>
            <button type="button" onClick={remove} className="h-12 rounded-xl bg-danger font-semibold text-white">Delete</button>
          </div>
        </Dialog>
      )}
    </Screen>
  )
}
