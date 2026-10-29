import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Screen, TopBar } from '../../components/ui'
import { AnimalSelect, DateNav, LitresStepper, LoadState } from '../../components/records'
import { useBreeding } from '../../components/breeding'
import { useStore } from '../../data/store'
import { decimal, L, numText, todayIso, toNum, useLoad } from '../../data/records'
import { errorMessage } from '../../lib/api'

export default function CalfFeeding() {
  const [params, setParams] = useSearchParams()
  const { br } = useBreeding()
  const { notify } = useStore()
  const nav = useNavigate()
  const animalId = params.get('animal') ?? ''
  const date = params.get('date') || todayIso()
  const setParam = (k: string, v: string) => setParams((cur) => { const n = new URLSearchParams(cur); if (v) n.set(k, v); else n.delete(k); return n }, { replace: true })
  const form = useLoad(animalId ? () => br.calfFeeding(animalId, date) : null, [br, animalId, date])
  const [am, setAm] = useState('')
  const [pm, setPm] = useState('')
  const [starter, setStarter] = useState('')
  const [water, setWater] = useState('')
  const [colL, setColL] = useState('')
  const [colAt, setColAt] = useState('')
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)

  const f = form.data
  useEffect(() => {
    const r = f?.record
    const plan = f?.plan?.milkLitres
    setAm(r ? numText(r.milkAm) : plan != null ? (plan / 2).toFixed(1) : '')
    setPm(r ? numText(r.milkPm) : '')
    setStarter(numText(r?.starterKg)); setWater(numText(r?.waterL)); setColL(numText(r?.colostrumLitres)); setColAt(r?.colostrumAt ?? ''); setNotes(r?.notes ?? '')
  }, [f])
  const colostrumAge = f ? f.calf.ageDays < 3 : false
  const dayMilk = (toNum(am) ?? 0) + (toNum(pm) ?? 0) + (toNum(colL) ?? 0)
  const half = f?.plan ? (f.plan.milkLitres / 2).toFixed(1) : null

  const save = async () => {
    if (!animalId) { notify('Choose a calf'); return }
    setBusy(true)
    try {
      await br.saveCalfFeeding(animalId, date, {
        milkAm: toNum(am) ?? null, milkPm: toNum(pm) ?? null, starterKg: toNum(starter) ?? null, waterL: toNum(water) ?? null,
        colostrumLitres: toNum(colL) ?? null, colostrumAt: colAt || null, notes: notes.trim() || null,
      })
      notify('Feeding saved')
      nav('/calves', { replace: true })
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title="Calf feeding" close="/calves" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <div role="group" aria-label="Feeding mode" className="grid grid-cols-2 rounded-xl bg-track p-1">
          <span className="flex h-10 items-center justify-center rounded-[9px] bg-white text-sm font-semibold shadow-sm">One calf</span>
          <Link to="/calves/feeding/group" className="flex h-10 items-center justify-center text-sm font-semibold text-ink-2 no-underline">Group</Link>
        </div>
        <div><label className="lab" htmlFor="cf-calf">Calf *</label><AnimalSelect id="cf-calf" value={animalId} onChange={(v) => setParam('animal', v)} filter={(a) => a.category === 'Calf'} placeholder="Choose a calf" /></div>
        <DateNav id="cf-date" value={date} onChange={(v) => setParam('date', v)} />
        <LoadState loading={form.loading} error={form.error} onRetry={form.reload} empty={!f} />
        {f && (
          <>
            <div className="text-[13px] text-ink-3">{f.calf.sex === 'FEMALE' ? '♀' : '♂'} · {f.calf.ageDays} days{f.calf.weightKg != null ? ` · ${f.calf.weightKg} kg` : ''}</div>
            {colostrumAge && (
              <div className="flex flex-col gap-3 rounded-[14px] bg-warn-soft p-3.5">
                <b className="text-[15px] text-warn-ink">Colostrum feed</b>
                <div className="grid grid-cols-2 gap-3">
                  <div><label className="lab text-[#4A3210]" htmlFor="cf-cat">Given at</label><input className="inp" id="cf-cat" type="time" value={colAt} onChange={(e) => setColAt(e.target.value)} /></div>
                  <div><label className="lab text-[#4A3210]" htmlFor="cf-cl">Litres</label><input className="inp" id="cf-cl" inputMode="decimal" placeholder="2.0" value={colL} onChange={(e) => setColL(decimal(e.target.value))} /></div>
                </div>
                <div className="text-xs text-[#4A3210]">For calves under 3 days.</div>
              </div>
            )}
            <div className="rounded-[14px] bg-white p-3.5">
              <div className="mb-2.5 flex items-center justify-between"><b>Milk — morning</b>{half && <span className="text-[13px] text-ink-3">plan {half} L</span>}</div>
              <LitresStepper label="Morning milk litres" value={am} onChange={setAm} />
            </div>
            <div className="rounded-[14px] bg-white p-3.5">
              <div className="mb-2.5 flex items-center justify-between"><b>Milk — evening</b>{half && <span className="text-[13px] text-ink-3">plan {half} L</span>}</div>
              <LitresStepper label="Evening milk litres" value={pm} onChange={setPm} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="lab" htmlFor="cf-st">Calf starter (kg)</label><input className="inp" id="cf-st" inputMode="decimal" value={starter} onChange={(e) => setStarter(decimal(e.target.value))} /></div>
              <div><label className="lab" htmlFor="cf-w">Water (L)</label><input className="inp" id="cf-w" inputMode="decimal" placeholder="optional" value={water} onChange={(e) => setWater(decimal(e.target.value))} /></div>
            </div>
            {f.taper.length > 0 && (
              <div className="rounded-[14px] bg-white p-3.5">
                <div className="flex items-center gap-2"><b className="flex-1">Suggested taper plan</b><span className="text-xs font-semibold text-ink-3">farm setting</span></div>
                <div className="mt-2">
                  <div className="grid grid-cols-[1.1fr_1fr_1fr] py-2 text-xs font-bold text-ink-3"><span>Age</span><span>Milk L/day</span><span>Starter</span></div>
                  {f.taper.map((t) => {
                    const now = f.calf.ageDays >= t.fromDay && f.calf.ageDays <= t.toDay
                    return (
                      <div key={t.fromDay} className={`grid grid-cols-[1.1fr_1fr_1fr] border-t border-line-faint py-2 text-[13px] ${now ? 'rounded-md bg-brand-soft pl-1.5 font-bold' : ''}`}>
                        <span>{t.fromDay}–{t.toDay} d{now ? ' · now' : ''}</span><span>{L(t.milkLitres)}</span><span>{t.starter}</span>
                      </div>
                    )
                  })}
                </div>
              </div>
            )}
            <div><label className="lab" htmlFor="cf-nt">Notes</label><input className="inp" id="cf-nt" placeholder="Optional — e.g. loose dung, refused feed" value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
          </>
        )}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <div className="mb-2.5 flex justify-between text-[15px]"><span>Day milk</span><b>{L(dayMilk)} L</b></div>
        <button type="button" className="btn" disabled={busy || !f} onClick={save}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </Screen>
  )
}
