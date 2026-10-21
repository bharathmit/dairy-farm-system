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
  const [pct, setPct] = useState(settings.marginAlertPct)

  const rateNum = Number(rate) || 0
  const income = Math.round(LITRES_PER_DAY * DAYS * rateNum)
  const totalExp = EXPENSES.reduce((s, [, v]) => s + v, 0)
  const margin = income - totalExp
  const marginPct = income > 0 ? Math.round((margin / income) * 100) : 0
  const below = marginPct < pct

  const save = async () => {
    if (!(rateNum > 0)) { notify('Enter a milk rate above ₹0'); return }
    if (!(await updateSettings({ milkRate: rateNum, morningMilking: morning, eveningMilking: evening, marginAlertPct: pct }))) return
    notify('Milk rate & margins saved')
    nav('/settings')
  }

  return (
    <Screen>
      <TopBar title="Milk rate & margins" back="/settings" />

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

      <div className="sec">Margin alert threshold</div>
      <div className="px-4">
        <label className="lab" htmlFor="ma">Alert me when margin falls below</label>
        <div className="flex items-center gap-3">
          <input id="ma" type="range" min={0} max={60} value={pct} onChange={(e) => setPct(Number(e.target.value))} className="h-11 flex-1 accent-brand" aria-valuetext={`${pct} percent`} />
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
