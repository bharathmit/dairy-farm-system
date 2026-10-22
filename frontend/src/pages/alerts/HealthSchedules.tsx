import { useState } from 'react'
import { Screen, Segmented, Sheet, Switch, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { LoadState } from '../../components/records'
import { OwnerOnlyScreen } from '../../components/money'
import { useAlerts } from '../../components/alerts'
import { useStore } from '../../data/store'
import type { AppliesTo, Schedule, ScheduleStep, ScheduleType } from '../../data/alerts'
import { decimal, useLoad } from '../../data/records'
import { errorMessage } from '../../lib/api'

const APPLIES: { value: AppliesTo; label: string }[] = [
  { value: 'CALVES_HEIFERS', label: 'Calves & heifers' }, { value: 'ALL', label: 'All animals' }, { value: 'MILKING', label: 'Milking cows' },
]
type Unit = 'd' | 'm'
const toDays = (v: string, u: Unit) => { const n = Number(v); return v.trim() === '' || !Number.isFinite(n) ? null : Math.round(u === 'm' ? n * 30 : n) }
const fromDays = (d?: number | null): { v: string; u: Unit } => (d == null ? { v: '', u: 'd' } : d >= 60 && d % 30 === 0 ? { v: String(d / 30), u: 'm' } : { v: String(d), u: 'd' })
const age = (d?: number | null) => (d == null ? '' : d >= 365 && d % 365 === 0 ? `${d / 365} y` : d >= 60 && d % 30 === 0 ? `${d / 30} months` : `day ${d}`)

/** "Day 15" / "Every 2 months until 6 months" / "Every 6 months". */
function stepText(s: ScheduleStep) {
  const every = s.repeatEveryDays ? `Every ${age(s.repeatEveryDays).replace('day ', '')}${s.repeatEveryDays < 60 ? ' days' : ''}` : ''
  const at = s.atAgeDays != null ? (s.atAgeDays >= 60 ? `At ${age(s.atAgeDays)}` : `Day ${s.atAgeDays}`) : ''
  const until = s.untilAgeDays ? ` until ${age(s.untilAgeDays).replace('day ', '')}` : ''
  return [at, every].filter(Boolean).join(', then ') + until
}

interface StepDraft { label: string; at: string; atU: Unit; every: string; everyU: Unit; until: string; untilU: Unit }
const stepDraft = (s?: ScheduleStep): StepDraft => {
  const a = fromDays(s?.atAgeDays); const e = fromDays(s?.repeatEveryDays); const u = fromDays(s?.untilAgeDays)
  return { label: s?.label ?? '', at: a.v, atU: a.u, every: e.v, everyU: e.u, until: u.v, untilU: u.u }
}

function UnitInput({ id, label, value, unit, onValue, onUnit }: { id: string; label: string; value: string; unit: Unit; onValue: (v: string) => void; onUnit: (u: Unit) => void }) {
  return (
    <div>
      <label className="lab" htmlFor={id}>{label}</label>
      <div className="flex gap-1.5">
        <input className="inp min-w-0 flex-1" id={id} inputMode="decimal" value={value} onChange={(e) => onValue(decimal(e.target.value))} />
        <select className="inp w-[92px]" aria-label={`${label} unit`} value={unit} onChange={(e) => onUnit(e.target.value as Unit)}><option value="d">days</option><option value="m">months</option></select>
      </div>
    </div>
  )
}

function ScheduleSheet({ initial, type, onClose, onSaved }: { initial?: Schedule; type: ScheduleType; onClose: () => void; onSaved: () => void }) {
  const { al } = useAlerts()
  const { notify } = useStore()
  const [t, setT] = useState<ScheduleType>(initial?.type ?? type)
  const [name, setName] = useState(initial?.name ?? '')
  const [applies, setApplies] = useState<AppliesTo>(initial?.appliesTo ?? 'CALVES_HEIFERS')
  const [medicine, setMedicine] = useState(initial?.medicine ?? '')
  const [steps, setSteps] = useState<StepDraft[]>(initial?.steps.length ? initial.steps.map(stepDraft) : [stepDraft()])
  const [before, setBefore] = useState(String(initial?.alertDaysBefore ?? 2))
  const [workers, setWorkers] = useState(initial?.workerVisible ?? true)
  const [active, setActive] = useState(initial?.active ?? true)
  const [busy, setBusy] = useState(false)
  const setStep = (i: number, p: Partial<StepDraft>) => setSteps((s) => s.map((x, j) => (j === i ? { ...x, ...p } : x)))

  const save = async () => {
    if (!name.trim()) { notify('Enter a name'); return }
    const body = {
      type: t, name: name.trim(), appliesTo: applies, medicine: medicine.trim() || null,
      steps: steps.map((s, i) => ({ label: s.label.trim() || `Dose ${i + 1}`, atAgeDays: toDays(s.at, s.atU), repeatEveryDays: toDays(s.every, s.everyU), untilAgeDays: toDays(s.until, s.untilU) }))
        .filter((s) => s.atAgeDays != null || s.repeatEveryDays != null),
      alertDaysBefore: Number(before) || 0, workerVisible: workers, active,
    }
    if (!body.steps.length) { notify('Add at least one dose with an age or a repeat'); return }
    setBusy(true)
    try { await al.saveSchedule(body, initial?.id); notify('Schedule saved'); onSaved() } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  const remove = async () => {
    if (!initial) return
    try { await al.deleteSchedule(initial.id); notify('Schedule deleted'); onSaved() } catch (e) { notify(errorMessage(e)) }
  }

  return (
    <Sheet label={initial ? 'Edit schedule' : 'Add schedule'} onClose={onClose} tall>
      <div className="flex flex-col gap-3 overflow-y-auto px-4 pb-6">
        <h2 className="m-0 text-xl font-bold">{initial ? 'Edit schedule' : 'Add schedule'}</h2>
        <Segmented label="Type" options={[{ value: 'DEWORMING', label: 'Deworming' }, { value: 'VACCINATION', label: 'Vaccination' }]} value={t} onChange={setT} />
        <div><label className="lab" htmlFor="hs-name">Name *</label><input className="inp" id="hs-name" placeholder={t === 'VACCINATION' ? 'e.g. FMD vaccination' : 'e.g. Calf deworming'} value={name} onChange={(e) => setName(e.target.value)} /></div>
        <div><label className="lab" htmlFor="hs-app">Applies to</label><select className="inp" id="hs-app" value={applies} onChange={(e) => setApplies(e.target.value as AppliesTo)}>{APPLIES.map((a) => <option key={a.value} value={a.value}>{a.label}</option>)}</select></div>
        <div><label className="lab" htmlFor="hs-med">Medicine / vaccine</label><input className="inp" id="hs-med" placeholder="e.g. Piperazine" value={medicine} onChange={(e) => setMedicine(e.target.value)} /></div>
        {steps.map((s, i) => (
          <div key={i} className="rounded-[14px] bg-ground p-3">
            <div className="flex items-center"><b className="flex-1">Dose {i + 1}</b>{steps.length > 1 && <button type="button" aria-label={`Remove dose ${i + 1}`} onClick={() => setSteps((x) => x.filter((_, j) => j !== i))} className="flex h-9 w-9 items-center justify-center border-0 bg-transparent"><Icon name="close" size={18} /></button>}</div>
            <div className="mt-2 flex flex-col gap-2.5">
              <div><label className="lab" htmlFor={`hs-l${i}`}>Label</label><input className="inp" id={`hs-l${i}`} placeholder="e.g. Booster" value={s.label} onChange={(e) => setStep(i, { label: e.target.value })} /></div>
              <UnitInput id={`hs-a${i}`} label="Start at age" value={s.at} unit={s.atU} onValue={(v) => setStep(i, { at: v })} onUnit={(u) => setStep(i, { atU: u })} />
              <UnitInput id={`hs-e${i}`} label="Repeat every (blank = once)" value={s.every} unit={s.everyU} onValue={(v) => setStep(i, { every: v })} onUnit={(u) => setStep(i, { everyU: u })} />
              <UnitInput id={`hs-u${i}`} label="Until age (blank = no end)" value={s.until} unit={s.untilU} onValue={(v) => setStep(i, { until: v })} onUnit={(u) => setStep(i, { untilU: u })} />
            </div>
          </div>
        ))}
        <button type="button" className="btn2 h-11 text-sm" onClick={() => setSteps((x) => [...x, stepDraft()])}>+ Add dose</button>
        <div><label className="lab" htmlFor="hs-b">Alert days before each dose</label><input className="inp" id="hs-b" inputMode="numeric" value={before} onChange={(e) => setBefore(e.target.value.replace(/\D/g, ''))} /></div>
        <div className="flex items-center gap-3"><span className="flex-1 font-semibold">Workers see</span><Switch checked={workers} onChange={setWorkers} label="Workers see" /></div>
        <div className="flex items-center gap-3"><span className="flex-1 font-semibold">On</span><Switch checked={active} onChange={setActive} label="Schedule on" /></div>
        <button type="button" className="btn" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save schedule'}</button>
        {initial && <button type="button" onClick={remove} className="h-11 border-0 bg-transparent font-semibold text-danger">Delete schedule</button>}
      </div>
    </Sheet>
  )
}

/** Deworming and vaccination schedules that raise SCHEDULE_DUE alerts. */
export default function HealthSchedules() {
  const { al, isOwner } = useAlerts()
  const list = useLoad(() => al.schedules(), [al])
  const [type, setType] = useState<ScheduleType>('DEWORMING')
  const [editing, setEditing] = useState<Schedule | 'new' | null>(null)
  if (!isOwner) return <OwnerOnlyScreen title="Health schedules" back="/alerts" />
  const shown = (list.data ?? []).filter((s) => s.type === type)

  return (
    <Screen>
      <TopBar title="Health schedules" back="/settings" />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <Segmented label="Schedule type" options={[{ value: 'DEWORMING', label: 'Deworming' }, { value: 'VACCINATION', label: 'Vaccination' }]} value={type} onChange={setType} />
        <LoadState loading={list.loading} error={list.error} onRetry={list.reload} empty={!list.data} />
        {list.data && !shown.length && <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">No {type === 'DEWORMING' ? 'deworming' : 'vaccination'} schedule yet.</div>}
        {shown.map((s) => (
          <button key={s.id} type="button" onClick={() => setEditing(s)} className={`rounded-[14px] border-0 bg-white p-3.5 text-left ${s.active ? '' : 'opacity-60'}`}>
            <div className="flex items-center gap-2"><b className="flex-1 text-base">{s.name}</b><span className="text-xs font-semibold text-ink-3">{s.active ? 'On' : 'Off'}{s.workerVisible ? ' · workers see' : ''}</span></div>
            <div className="mt-0.5 text-[13px] text-ink-3">{APPLIES.find((a) => a.value === s.appliesTo)?.label}{s.medicine ? ` · ${s.medicine}` : ''} · alerts {s.alertDaysBefore} d before</div>
            <ol className="m-0 mt-2 list-none p-0">
              {s.steps.map((st, i) => (
                <li key={i} className="flex items-center gap-2.5 border-t border-line-faint py-2 text-sm first:border-t-0">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-brand-soft text-xs font-bold text-brand">{i + 1}</span>
                  <span className="flex-1"><b>{stepText(st)}</b>{st.label ? <span className="text-ink-3"> · {st.label}</span> : null}</span>
                </li>
              ))}
            </ol>
          </button>
        ))}
        <button type="button" className="btn2" onClick={() => setEditing('new')}>+ Add {type === 'DEWORMING' ? 'deworming' : 'vaccination'} schedule</button>
        <p className="m-0 text-[13px] text-ink-3">Counted from each animal's date of birth. A dose recorded from the alert closes it; due doses also appear on Health and in Alerts.</p>
      </div>
      {editing && <ScheduleSheet initial={editing === 'new' ? undefined : editing} type={type} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); void list.reload() }} />}
    </Screen>
  )
}
