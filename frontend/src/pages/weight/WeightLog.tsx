import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Screen, Segmented, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { AnimalSelect, DateNav, useRecords } from '../../components/records'
import { ageText, useStore } from '../../data/store'
import { decimal, L, METHODS, numText, shortDate, todayIso, toNum, useLoad, type Method } from '../../data/records'
import { errorMessage } from '../../lib/api'

const days = (a: string, b: string) => Math.round((new Date(b).getTime() - new Date(a).getTime()) / 86400000)

export default function WeightLog() {
  const { rec } = useRecords()
  const { animals, notify } = useStore()
  const nav = useNavigate()
  const [params, setParams] = useSearchParams()
  const animalId = params.get('animal') ?? ''
  const editId = Number(params.get('id')) || undefined
  const growth = useLoad(animalId ? () => rec.growth(animalId) : null, [rec, animalId])
  const animal = animals.find((a) => a.id === animalId)

  const [date, setDate] = useState(todayIso())
  const [method, setMethod] = useState<Method>('SCALE')
  const [girth, setGirth] = useState('')
  const [weight, setWeight] = useState('')
  const [height, setHeight] = useState('')
  const [useBcs, setUseBcs] = useState(true)
  const [bcs, setBcs] = useState(3)
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)

  const editing = editId ? growth.data?.items.find((m) => m.id === editId) : undefined
  useEffect(() => {
    if (!editing) return
    setDate(editing.date); setMethod(editing.method); setGirth(numText(editing.heartGirthCm)); setWeight(numText(editing.weightKg))
    setHeight(numText(editing.heightCm)); setUseBcs(editing.bcs != null); setBcs(editing.bcs ?? 3); setNotes(editing.notes ?? '')
  }, [editing])

  const prev = growth.data?.items.find((m) => m.weightKg != null && m.id !== editId && m.date <= date)
  const w = toNum(weight)
  const gap = prev ? days(prev.date, date) : 0
  const gain = prev && w !== undefined && prev.weightKg != null ? w - prev.weightKg : undefined
  const young = animal && (animal.category === 'Calf' || animal.category === 'Heifer')
  const bcsLow = useBcs && bcs < 2.5
  const bcsHigh = useBcs && bcs > 4

  const save = async (next: boolean) => {
    if (!animalId) { notify('Choose an animal'); return }
    const body = { date, method, weightKg: w ?? null, heightCm: toNum(height) ?? null, heartGirthCm: toNum(girth) ?? null, bcs: useBcs ? bcs : null, notes: notes.trim() || null }
    if (body.weightKg === null && body.heightCm === null && body.bcs === null) { notify('Enter weight, height or BCS'); return }
    setBusy(true)
    try {
      if (editId) await rec.updateMeasurement(editId, body)
      else await rec.addMeasurement(animalId, body)
      notify(`Saved for ${animal?.tag ?? 'the animal'}`)
      if (next) {
        setWeight(''); setGirth(''); setHeight(''); setNotes('')
        const p = new URLSearchParams(); setParams(p, { replace: true })
      } else nav(`/animals/${animalId}/growth`, { replace: true })
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title={editId ? 'Edit weight record' : 'Weight & condition'} close={animalId ? `/animals/${animalId}/growth` : '/home'} />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <div>
          <label className="lab" htmlFor="wl-an">Animal *</label>
          <AnimalSelect id="wl-an" value={animalId} onChange={(v) => setParams(v ? { animal: v } : {}, { replace: true })} />
          {animal && (
            <div className="hint">{[animal.category, ageText(animal.dob)].filter(Boolean).join(' · ')}{prev ? ` · last ${L(prev.weightKg, 0)} kg on ${shortDate(prev.date)}` : growth.data ? ' · first record' : ''}</div>
          )}
          {growth.error && <div className="hint text-danger">{growth.error}</div>}
        </div>
        <DateNav id="wl-date" value={date} onChange={setDate} />
        <div><span className="lab">Method</span><Segmented label="Method" value={method} onChange={setMethod} options={METHODS} /></div>
        <div className="rounded-[14px] bg-white p-3.5">
          <div className="grid grid-cols-2 gap-3">
            {method === 'TAPE' && <div><label className="lab" htmlFor="wl-girth">Heart girth (cm)</label><input className="inp" id="wl-girth" inputMode="decimal" value={girth} onChange={(e) => setGirth(decimal(e.target.value))} /></div>}
            <div className={method === 'TAPE' ? '' : 'col-span-2'}><label className="lab" htmlFor="wl-wt">Weight (kg)</label><input className="inp" id="wl-wt" inputMode="decimal" value={weight} onChange={(e) => setWeight(decimal(e.target.value))} /></div>
          </div>
          {method === 'TAPE' && <div className="hint">Read the weight from the tape's kg scale.</div>}
          <div className="mt-3"><label className="lab" htmlFor="wl-ht">Height at withers (cm)</label><input className="inp" id="wl-ht" inputMode="decimal" value={height} onChange={(e) => setHeight(decimal(e.target.value))} />
            {young && <div className="hint">For calves and heifers — tracks growth.</div>}</div>
        </div>
        <div className="rounded-[14px] bg-white p-3.5">
          <div className="flex items-baseline justify-between">
            <label className="flex items-center gap-2 font-bold"><input type="checkbox" checked={useBcs} onChange={(e) => setUseBcs(e.target.checked)} className="h-5 w-5 accent-brand" />Body condition score</label>
            <span className="text-[22px] font-bold">{useBcs ? bcs.toFixed(2).replace(/0$/, '') : '—'}</span>
          </div>
          <input type="range" min={1} max={5} step={0.25} value={bcs} disabled={!useBcs} onChange={(e) => setBcs(Number(e.target.value))} aria-label="Body condition score" className="h-11 w-full accent-brand" />
          <div className="flex justify-between text-xs text-ink-3"><span>1 Thin</span><span>3 Ideal</span><span>5 Fat</span></div>
          {(bcsLow || bcsHigh) && <div className="mt-2 text-xs font-semibold text-danger">{bcsLow ? 'Below 2.5 — too thin' : 'Above 4.0 — too fat'}; check her feed.</div>}
        </div>
        {gain !== undefined && gap > 0 && (
          <div className="flex items-center gap-3 rounded-xl bg-brand-soft px-3.5 py-3 text-sm text-[#1C3A28]">
            <Icon name="arrow" size={20} color="#1F5C3A" />
            {gain >= 0 ? '+' : '−'}{L(Math.abs(gain))} kg in {gap} days · {(gain / gap).toFixed(2)} kg/day
          </div>
        )}
        <div><label className="lab" htmlFor="wl-notes">Notes</label><input className="inp" id="wl-notes" placeholder="Optional" value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
      </div>
      <div className="sticky bottom-0 mt-auto flex gap-2.5 border-t border-line-soft bg-ground px-4 pb-6 pt-3.5">
        {!editId && <button type="button" className="btn2" disabled={busy} onClick={() => save(true)}>Save &amp; next</button>}
        <button type="button" className="btn" disabled={busy} onClick={() => save(false)}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </Screen>
  )
}
