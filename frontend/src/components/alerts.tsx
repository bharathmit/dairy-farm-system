/* eslint-disable react-refresh/only-export-components */
import { useEffect, useMemo, useState, useSyncExternalStore } from 'react'
import { Link } from 'react-router-dom'
import { useStore } from '../data/store'
import {
  actionLink, alertCountSnapshot, alertsApi, BARN_LABEL, CATEGORY_LABEL, CATEGORY_TONE, dueText, invalidateAlertCount,
  loadAlertCount, SEVERITY_LABEL, SEVERITY_TONE, subscribeAlertCount, type Alert, type Barn,
} from '../data/alerts'
import { todayIso, toNum, decimal } from '../data/records'
import { errorMessage } from '../lib/api'
import { Sheet } from './ui'

/** Alerts API for the chosen farm, and whether the caller is an owner. */
export function useAlerts() {
  const { farm } = useStore()
  const farmId = farm?.id ?? ''
  const al = useMemo(() => alertsApi(farmId), [farmId])
  return { al, farmId, isOwner: farm?.role === 'Owner' }
}

/** Open-alert count for the nav badge (cached for a minute across screens). */
export function useAlertCount() {
  const { farm } = useStore()
  const farmId = farm?.id ?? ''
  const snap = useSyncExternalStore(subscribeAlertCount, () => alertCountSnapshot(farmId))
  useEffect(() => { void loadAlertCount(farmId) }, [farmId])
  return snap?.count ?? 0
}

/** Call after changing an alert so the badge refreshes. */
export function refreshAlertCount(farmId: string) {
  invalidateAlertCount()
  void loadAlertCount(farmId, true)
}

export function Badge({ className, children }: { className: string; children: React.ReactNode }) {
  return <span className={`whitespace-nowrap rounded-md px-2 py-0.5 text-xs font-semibold ${className}`}>{children}</span>
}

/** Ring showing done of total. */
export function ProgressRing({ done, total, size = 44 }: { done: number; total: number; size?: number }) {
  const r = size / 2 - 4
  const c = 2 * Math.PI * r
  const frac = total ? Math.min(1, done / total) : 0
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={`Checklist ${done} of ${total} done`}>
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="#E4E6DF" strokeWidth="5" />
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="#1F5C3A" strokeWidth="5" strokeDasharray={`${c * frac} ${c}`}
        transform={`rotate(-90 ${size / 2} ${size / 2})`} strokeLinecap="round" />
    </svg>
  )
}

const BARN_BG: Record<Barn['level'], string> = {
  NONE: 'bg-brand-soft text-[#1C3A28]', MILD: 'bg-warn-soft text-[#4A3210]', MODERATE: 'bg-warn-soft text-[#4A3210]',
  SEVERE: 'bg-danger-soft text-[#5A1A12]', EMERGENCY: 'bg-danger-soft text-[#5A1A12]',
}

/** Barn THI card with the colour scale. */
export function BarnCard({ barn, compact }: { barn: Barn; compact?: boolean }) {
  return (
    <div className={`rounded-[14px] px-4 py-3.5 ${BARN_BG[barn.level]}`}>
      <div className="flex items-center gap-3.5">
        <div className="flex h-16 w-16 shrink-0 flex-col items-center justify-center rounded-[14px] bg-white">
          <span className="text-[11px] font-bold text-warn-ink">THI</span><span className="text-2xl font-bold">{Math.round(barn.thi)}</span>
        </div>
        <div className="flex-1">
          <b className="text-[15px]">{BARN_LABEL[barn.level]}</b>
          <div className="mt-0.5 text-[13px]">{barn.temperatureC} °C · {barn.humidityPct} % humidity{barn.time ? ` · ${barn.time.slice(0, 5)}` : ''}</div>
          {barn.advice && <div className="mt-1 text-[13px]">{barn.advice}{!compact && barn.yieldEffect ? ` ${barn.yieldEffect}` : ''}</div>}
        </div>
      </div>
      {!compact && (
        <>
          <div className="mt-3 flex h-2 overflow-hidden rounded" aria-hidden="true">
            <span className="bg-[#7FA88C]" style={{ flex: 72 }} /><span className="bg-[#D9A55A]" style={{ flex: 7 }} />
            <span className="bg-[#C2410C]" style={{ flex: 10 }} /><span className="bg-[#8E2216]" style={{ flex: 11 }} />
          </div>
          <div className="mt-1 flex justify-between text-[11px] font-semibold"><span>&lt;72 none</span><span>72–78 mild–mod</span><span>79–88 severe</span><span>&gt;88</span></div>
        </>
      )}
    </div>
  )
}

/** Sheet to enter a barn temperature / humidity reading. */
export function BarnSheet({ onClose, onSaved }: { onClose: () => void; onSaved: (b: Barn) => void }) {
  const { al } = useAlerts()
  const { notify } = useStore()
  const now = new Date()
  const [temp, setTemp] = useState('')
  const [hum, setHum] = useState('')
  const [busy, setBusy] = useState(false)
  const save = async () => {
    const t = toNum(temp); const h = toNum(hum)
    if (t === undefined || h === undefined) { notify('Enter temperature and humidity'); return }
    setBusy(true)
    try {
      const b = await al.saveBarn({ date: todayIso(), time: `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`, temperatureC: t, humidityPct: h })
      notify('Barn reading saved'); onSaved(b)
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  return (
    <Sheet label="Barn reading" onClose={onClose}>
      <div className="flex flex-col gap-3 px-4 pb-6">
        <h2 className="m-0 text-xl font-bold">Barn reading</h2>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="br-t">Temperature (°C)</label><input className="inp" id="br-t" inputMode="decimal" value={temp} onChange={(e) => setTemp(decimal(e.target.value))} /></div>
          <div><label className="lab" htmlFor="br-h">Humidity (%)</label><input className="inp" id="br-h" inputMode="decimal" value={hum} onChange={(e) => setHum(decimal(e.target.value))} /></div>
        </div>
        <div className="text-xs text-ink-3">Heat stress (THI) is worked out from these two numbers.</div>
        <button type="button" className="btn" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save reading'}</button>
      </div>
    </Sheet>
  )
}

/** Sheet with snooze choices. */
export function SnoozeSheet({ onClose, onPick }: { onClose: () => void; onPick: (until: string) => void }) {
  const at = (hours: number) => new Date(Date.now() + hours * 3600_000).toISOString()
  const tomorrowMorning = () => { const d = new Date(); d.setDate(d.getDate() + 1); d.setHours(6, 0, 0, 0); return d.toISOString() }
  const opts = [
    { label: '2 hours', until: at(2) }, { label: 'Tomorrow 6 AM', until: tomorrowMorning() },
    { label: '3 days', until: at(72) }, { label: '1 week', until: at(168) },
  ]
  return (
    <Sheet label="Snooze" onClose={onClose}>
      <div className="flex flex-col gap-2 px-4 pb-6">
        <h2 className="m-0 mb-1 text-xl font-bold">Snooze until</h2>
        {opts.map((o) => <button key={o.label} type="button" className="btn2" onClick={() => onPick(o.until)}>{o.label}</button>)}
      </div>
    </Sheet>
  )
}

/** One alert card with Done / Snooze / Open (or its action). */
export function AlertCard({ a, onChanged, today = todayIso() }: { a: Alert; onChanged: (a: Alert | null) => void; today?: string }) {
  const { al, farmId } = useAlerts()
  const { notify } = useStore()
  const [snooze, setSnooze] = useState(false)
  const link = actionLink(a)
  const overdue = (a.overdueDays ?? 0) > 0
  const done = async () => {
    try { await al.done(a.id); notify('Marked done'); refreshAlertCount(farmId); onChanged(null) } catch (e) { notify(errorMessage(e)) }
  }
  const doSnooze = async (until: string) => {
    setSnooze(false)
    try { await al.snooze(a.id, until); notify('Snoozed'); refreshAlertCount(farmId); onChanged(null) } catch (e) { notify(errorMessage(e)) }
  }
  return (
    <div className={`rounded-[14px] bg-white px-3.5 py-3 ${overdue ? 'shadow-[inset_0_0_0_1.5px_#E8B4AC]' : ''}`}>
      <div className="flex flex-wrap items-center gap-1.5">
        <Badge className={SEVERITY_TONE[a.severity]}>{SEVERITY_LABEL[a.severity]}</Badge>
        <Badge className={CATEGORY_TONE[a.category]}>{CATEGORY_LABEL[a.category]}</Badge>
        <span className={`ml-auto text-[13px] ${overdue ? 'font-semibold text-danger-text' : 'text-ink-3'}`}>{dueText(a, today)}</span>
      </div>
      <div className="mt-1.5 text-base font-bold">{a.title}</div>
      {(a.animal || a.detail) && (
        <div className="mt-0.5 text-[13px] text-ink-3">
          {a.animal && <b className="text-ink">{a.animal.tag}{a.animal.name ? ` ${a.animal.name}` : ''}</b>}
          {a.animal && a.detail ? ' · ' : ''}{a.detail}
        </div>
      )}
      <div className="mt-2.5 grid grid-cols-[1.6fr_1fr_1fr] gap-2">
        {link && a.action
          ? <Link to={link} className="btn h-11 px-2 text-center text-[13px] leading-tight">{a.action.label}</Link>
          : <button type="button" className="btn h-11 text-sm" onClick={done}>{a.ruleCode === 'WITHDRAWAL' ? 'Seen' : 'Done'}</button>}
        <button type="button" className="btn2 h-11 text-sm" onClick={() => setSnooze(true)}>Snooze</button>
        <Link to={`/alerts/${a.id}`} className="btn2 h-11 text-sm">Open</Link>
      </div>
      {snooze && <SnoozeSheet onClose={() => setSnooze(false)} onPick={doSnooze} />}
    </div>
  )
}
