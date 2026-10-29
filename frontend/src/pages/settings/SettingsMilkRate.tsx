import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ActionBar, Screen, TopBar } from '../../components/ui'
import { inr, useStore } from '../../data/store'

const EXPENSES: [string, number][] = [
  ['Feed & fodder', 60000],
  ['Labour wages', 15000],
  ['Vet & medicine', 4000],
  ['Electricity & water', 3000],
  ['Other expenses', 3000],
]
const FEED_NOTES: [string, string][] = [
  ['Summer heat adjustment', 'In very hot weather cows eat less in the heat of the day and much more in the cool night. In hot seasons switch to a 40/60 split (40 % morning, 60 % evening) so feed doesn’t sit, heat up and spoil in the sun.'],
  ['The 9:00 PM push-up', 'With an equal feed at 6:00 PM, do a push-up later that night (around 9:00–10:00 PM) so the overnight half stays within the cows’ reach while you sleep.'],
  ['Morning clean-up check', 'At 5:00 AM there should still be about 2–4 % of the feed left in the bunk. If it is licked clean, increase the evening portion slightly.'],
]
const LITRES_PER_DAY = 100
const DAYS = 30

function Line({ label, value, className = '' }: { label: string; value: string; className?: string }) {
  return <div className={`flex items-baseline justify-between py-[5px] text-sm text-ink-2 ${className}`}><span>{label}</span><span>{value}</span></div>
}

export default function SettingsMilkRate() {
  const { settings, updateSettings, notify } = useStore()
  const nav = useNavigate()
  const [rate, setRate] = useState(String(settings.milkRate.toFixed(2)))
  const [morning, setMorning] = useState(settings.morningMilking)
  const [evening, setEvening] = useState(settings.eveningMilking)
  const [feedAm, setFeedAm] = useState(settings.morningFeeding)
  const [feedPm, setFeedPm] = useState(settings.eveningFeeding)
  const [pushUps, setPushUps] = useState(settings.feedPushUps)
  const [pct, setPct] = useState(settings.marginAlertPct)
  // Locked by default so scrolling past the slider can't change it.
  const [pctEdit, setPctEdit] = useState(false)

  const rateNum = Number(rate) || 0
  const income = Math.round(LITRES_PER_DAY * DAYS * rateNum)
  const totalExp = EXPENSES.reduce((s, [, v]) => s + v, 0)
  const margin = income - totalExp
  const marginPct = income > 0 ? Math.round((margin / income) * 100) : 0
  const below = marginPct < pct

  const save = async () => {
    if (!(rateNum > 0)) { notify('Enter a milk rate above ₹0'); return }
    if (!(await updateSettings({ milkRate: rateNum, morningMilking: morning, eveningMilking: evening, morningFeeding: feedAm, eveningFeeding: feedPm, feedPushUps: pushUps.filter(Boolean).sort(), marginAlertPct: pct }))) return
    notify('Milking & feeding margins saved')
    nav('/settings')
  }

  return (
    <Screen>
      <TopBar title="Milking & Feeding Margins" back="/settings" />

      <div className="sec">Milk rate</div>
      <div className="px-4">
        <label className="lab" htmlFor="mr">Rate (₹ per litre)</label>
        <input className="inp" id="mr" inputMode="decimal" value={rate} onChange={(e) => setRate(e.target.value.replace(/[^\d.]/g, ''))} />
        <div className="hint">Used when a milk entry has no price — e.g. society / cooperative rate.</div>
      </div>

      <div className="sec">Milking times</div>
      <div className="px-4">
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="mm">Morning</label><input className="inp" id="mm" type="time" value={morning} onChange={(e) => setMorning(e.target.value)} /></div>
          <div><label className="lab" htmlFor="me">Evening</label><input className="inp" id="me" type="time" value={evening} onChange={(e) => setEvening(e.target.value)} /></div>
        </div>
        <div className="hint">Milk entries are recorded against the morning or evening session.</div>
      </div>

      <div className="sec">Feeding times</div>
      <div className="px-4">
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="fa">Morning TMR</label><input className="inp" id="fa" type="time" value={feedAm} onChange={(e) => setFeedAm(e.target.value)} /></div>
          <div><label className="lab" htmlFor="fp">Evening TMR</label><input className="inp" id="fp" type="time" value={feedPm} onChange={(e) => setFeedPm(e.target.value)} /></div>
        </div>
        <div className="hint">Feed fresh TMR as cows come out of milking. Home shows Feeding AM / PM as due at these times.</div>
        <div className="mt-3 grid grid-cols-3 gap-3">
          {pushUps.map((t, i) => (
            <div key={i}><label className="lab" htmlFor={`pu${i}`}>Push-up {i + 1}</label><input className="inp" id={`pu${i}`} type="time" value={t} onChange={(e) => setPushUps(pushUps.map((p, j) => (j === i ? e.target.value : p)))} /></div>
          ))}
        </div>
        <div className="hint">Push feed back to the cows between deliveries — the late-night push-up keeps overnight intake up.</div>
      </div>

      <div className="sec">Milking &amp; feeding notes</div>
      <div className="mx-4 mb-4 space-y-3 rounded-[14px] bg-white p-4 text-sm leading-snug text-ink-2">
        {FEED_NOTES.map(([title, body]) => (
          <div key={title}><div className="font-bold text-ink">{title}</div><div>{body}</div></div>
        ))}
      </div>

      <div className="sec">Margin alert threshold</div>
      <div className="px-4">
        <div className="flex items-center justify-between">
          <label className="lab" htmlFor="ma">Alert me when margin falls below</label>
          <button type="button" className={`chip h-8 ${pctEdit ? 'chip-on' : ''}`} onClick={() => setPctEdit(!pctEdit)} aria-pressed={pctEdit}>{pctEdit ? 'Done' : 'Edit'}</button>
        </div>
        <div className="flex items-center gap-3">
          <input id="ma" type="range" min={0} max={60} value={pct} disabled={!pctEdit} onChange={(e) => setPct(Number(e.target.value))} className={`h-11 flex-1 accent-brand ${pctEdit ? '' : 'pointer-events-none opacity-50'}`} aria-valuetext={`${pct} percent`} />
          <span className="w-12 text-right text-base font-bold">{pct} %</span>
        </div>
        <div className="hint mt-0">Checked weekly. Margin = milk income minus all farm input costs and expenses.</div>
      </div>

      <div className="sec">How it works</div>
      <div className="mx-4 mb-4 rounded-[14px] bg-white p-4">
        <div className="mb-2 text-[13px] text-ink-3">Example month · {LITRES_PER_DAY} L a day at {inr(rateNum)} / L</div>
        <Line label="Milk income" value={inr(income)} className="text-[15px] font-bold text-ink" />
        <div className="mb-0.5 mt-2.5 text-xs font-bold uppercase tracking-[.06em] text-ink-3">Less: inputs &amp; expenses</div>
        {EXPENSES.map(([l, v]) => <Line key={l} label={l} value={inr(v)} />)}
        <Line label="Total expenses" value={inr(totalExp)} className="mt-1 border-t border-line-faint pt-2 font-semibold text-ink" />
        <Line label="Margin" value={`${margin < 0 ? '−' : ''}${inr(Math.abs(margin))} · ${marginPct} %`} className="mt-1.5 border-t border-[#D3D8CF] pt-2.5 text-[15px] font-bold text-ink" />
        {below ? (
          <div className="mt-3 rounded-[10px] bg-warn-soft px-3 py-2.5 text-sm leading-snug text-warn-text">{marginPct} % is below your {pct} % threshold, so you'd get a margin alert that week.</div>
        ) : (
          <div className="mt-3 rounded-[10px] bg-brand-soft px-3 py-2.5 text-sm leading-snug text-[#1C3A28]">{marginPct} % is above your {pct} % threshold, so no alert.</div>
        )}
      </div>

      <ActionBar><button type="button" className="btn" onClick={save}>Save</button></ActionBar>
    </Screen>
  )
}
