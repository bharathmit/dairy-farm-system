import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Screen, Segmented, TopBar } from '../../components/ui'
import { AnimalSelect } from '../../components/records'
import { isBreedable, useBreeding } from '../../components/breeding'
import { useStore } from '../../data/store'
import { addDays, dayLabel, shortDate, todayIso, useLoad } from '../../data/records'
import { HEAT_SIGNS, INTENSITIES, time12, type HeatSign, type Intensity } from '../../data/breeding'
import { errorMessage } from '../../lib/api'

const nowTime = () => { const d = new Date(); return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` }

export default function HeatForm() {
  const [params] = useSearchParams()
  const { br } = useBreeding()
  const { notify } = useStore()
  const nav = useNavigate()
  const [animalId, setAnimalId] = useState(params.get('animal') ?? '')
  const [date, setDate] = useState(todayIso())
  const [time, setTime] = useState(nowTime())
  const [signs, setSigns] = useState<HeatSign[]>([])
  const [intensity, setIntensity] = useState<Intensity>('MODERATE')
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const settings = useLoad(() => br.settings(), [br])
  const last = useLoad(animalId ? () => br.heats({ animalId }) : null, [br, animalId])
  const cycle = settings.data?.heatCycleDays ?? 21

  const best = useMemo(() => {
    const [h, m] = time.split(':').map(Number)
    if (Number.isNaN(h)) return null
    const nh = h + 12
    return { day: nh >= 24 ? addDays(date, 1) : date, time: `${String(nh % 24).padStart(2, '0')}:${String(m).padStart(2, '0')}` }
  }, [date, time])
  const toggle = (s: HeatSign) => setSigns((cur) => (cur.includes(s) ? cur.filter((x) => x !== s) : [...cur, s]))

  const save = async (thenService: boolean) => {
    if (!animalId) { notify('Choose a cow'); return }
    if (!signs.length && intensity !== 'SILENT') { notify('Pick the signs you saw (or Silent)'); return }
    setBusy(true)
    try {
      const res = await br.addHeat({ animalId: Number(animalId), date, time, signs, intensity, notes: notes.trim() || undefined })
      notify(res.bestServiceAt ? 'Heat saved · inseminate about 12 h after first seen' : 'Heat saved')
      nav(thenService ? `/breeding/service/new?animal=${animalId}&heat=${res.heat.id}` : '/breeding/heat-watch', { replace: true })
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  const lastHeat = last.data?.[0]

  return (
    <Screen>
      <TopBar title="Log heat signs" close="/breeding/heat-watch" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <div>
          <label className="lab" htmlFor="hs-cow">Cow *</label>
          <AnimalSelect id="hs-cow" value={animalId} onChange={setAnimalId} filter={isBreedable} placeholder="Choose a cow or heifer" />
          {lastHeat && <div className="hint">Last heat {shortDate(lastHeat.date)}</div>}
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="hs-dt">Date</label><input className="inp" id="hs-dt" type="date" max={todayIso()} value={date} onChange={(e) => e.target.value && setDate(e.target.value)} /></div>
          <div><label className="lab" htmlFor="hs-tm">Time seen</label><input className="inp" id="hs-tm" type="time" value={time} onChange={(e) => setTime(e.target.value)} /></div>
        </div>
        <fieldset className="m-0 rounded-[14px] border-0 bg-white p-3.5">
          <legend className="float-left mb-2.5 w-full p-0 text-[15px] font-bold">Signs seen <span className="text-[13px] font-medium text-ink-3">· pick all that apply</span></legend>
          <div className="clear-both grid grid-cols-2 gap-2.5">
            {HEAT_SIGNS.map((s) => (
              <label key={s.value} className={`flex min-h-12 cursor-pointer items-center gap-2.5 rounded-[10px] border px-3 text-[15px] ${signs.includes(s.value) ? 'border-brand bg-brand-soft' : 'border-line'}`}>
                <input type="checkbox" checked={signs.includes(s.value)} onChange={() => toggle(s.value)} className="h-5 w-5 accent-brand" />{s.label}
              </label>
            ))}
          </div>
        </fieldset>
        <div>
          <div className="lab">Intensity</div>
          <Segmented label="Intensity" options={INTENSITIES} value={intensity} onChange={setIntensity} />
          <div className="hint">Silent = found by vet or heat patch, no visible signs.</div>
        </div>
        <div><label className="lab" htmlFor="hs-nt">Notes</label><textarea className="inp h-[84px] resize-none py-3" id="hs-nt" placeholder="Optional — e.g. standing heat seen by Murugan" value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
        {best && (
          <div className="flex gap-2.5 rounded-xl bg-info-soft px-3.5 py-3 text-sm text-info-text">
            <span>Best time to inseminate: <b>{time12(best.time)} {best.day === todayIso() ? 'today' : dayLabel(best.day)}</b> (12 h after first seen). Next heat expected <b>{shortDate(addDays(date, cycle))}</b> if not bred.</span>
          </div>
        )}
      </div>
      <div className="sticky bottom-0 mt-auto flex flex-col gap-2 border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <button type="button" className="btn" disabled={busy} onClick={() => save(true)}>{busy ? 'Saving…' : 'Save & record service'}</button>
        <button type="button" className="h-11 border-0 bg-transparent text-sm font-semibold text-brand" disabled={busy} onClick={() => save(false)}>Save only</button>
      </div>
    </Screen>
  )
}
