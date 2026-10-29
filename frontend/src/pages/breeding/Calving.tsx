import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Screen, Segmented, TopBar } from '../../components/ui'
import { AnimalSelect } from '../../components/records'
import { useBreeding } from '../../components/breeding'
import { useStore } from '../../data/store'
import { decimal, shortDate, todayIso, toNum, useLoad } from '../../data/records'
import { EASES, OUTCOMES, time12, type Ease, type Outcome } from '../../data/breeding'
import { errorMessage } from '../../lib/api'

interface CalfDraft { sex: 'FEMALE' | 'MALE'; weight: string; tag: string; name: string; create: boolean }
const nowTime = () => { const d = new Date(); return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` }
const prefixOf = (tag: string) => tag.replace(/\d+$/, '')
/** Next tag after the highest one with the same prefix in the herd (the server re-checks). */
function nextTagLocal(tags: string[], prefix: string, skip: string[]) {
  const nums = tags.filter((t) => t.startsWith(prefix)).map((t) => Number(t.slice(prefix.length))).filter(Number.isFinite)
  let n = (nums.length ? Math.max(...nums) : 0) + 1
  const width = tags.find((t) => t.startsWith(prefix))?.slice(prefix.length).length ?? 3
  let tag = `${prefix}${String(n).padStart(width, '0')}`
  while (skip.includes(tag)) { n += 1; tag = `${prefix}${String(n).padStart(width, '0')}` }
  return tag
}

export default function Calving() {
  const [params] = useSearchParams()
  const { br } = useBreeding()
  const { animals, notify, refreshHerd } = useStore()
  const nav = useNavigate()
  const ov = useLoad(() => br.overview(), [br])
  const settings = useLoad(() => br.settings(), [br])
  const [damId, setDamId] = useState(params.get('animal') ?? '')
  const [date, setDate] = useState(todayIso())
  const [time, setTime] = useState(nowTime())
  const [outcome, setOutcome] = useState<Outcome>('LIVE')
  const [ease, setEase] = useState<Ease>('EASY')
  const [calves, setCalves] = useState<CalfDraft[]>([])
  const [colAt, setColAt] = useState('')
  const [colL, setColL] = useState('')
  const [placenta, setPlacenta] = useState(false)
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)

  const dam = animals.find((a) => a.id === damId)
  const status = useMemo(() => {
    const g = ov.data?.groups
    return g ? [...g.overdue, ...g.dueIn7, ...g.pregnant, ...g.dry, ...g.served].find((s) => String(s.animal.id) === damId) : undefined
  }, [ov.data, damId])
  const expecting = useMemo(() => {
    const g = ov.data?.groups
    return new Set(g ? [...g.overdue, ...g.dueIn7, ...g.pregnant, ...g.dry].map((s) => String(s.animal.id)) : [])
  }, [ov.data])
  const tags = useMemo(() => animals.map((a) => a.tag), [animals])
  const newCalf = (list: CalfDraft[]): CalfDraft => ({ sex: 'FEMALE', weight: '', name: '', create: true, tag: dam ? nextTagLocal(tags, prefixOf(dam.tag), list.map((c) => c.tag)) : '' })

  useEffect(() => { setCalves((cur) => (cur.length ? cur.map((c, i) => (i === 0 && !c.tag && dam ? { ...c, tag: nextTagLocal(tags, prefixOf(dam.tag), []) } : c)) : [newCalf([])])) },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [damId, dam?.tag])
  const setCalf = (i: number, p: Partial<CalfDraft>) => setCalves((cur) => cur.map((c, j) => (j === i ? { ...c, ...p } : c)))
  const colHours = settings.data?.colostrumHours ?? 2
  const dueBy = (() => { const [h, m] = time.split(':').map(Number); if (Number.isNaN(h)) return ''; const t = h + colHours; return `${String(t % 24).padStart(2, '0')}:${String(m).padStart(2, '0')}` })()
  const live = outcome === 'LIVE'
  const created = calves.filter((c) => live && c.create)

  const save = async () => {
    if (!damId) { notify('Choose the dam'); return }
    if (live && calves.some((c) => c.create && !c.tag.trim())) { notify('Enter a tag for each calf'); return }
    setBusy(true)
    try {
      const res = await br.calving({
        damId: Number(damId), date, time, outcome, ease,
        calves: outcome === 'ABORTED' ? [] : calves.map((c) => ({ sex: c.sex, birthWeightKg: toNum(c.weight), tag: c.tag.trim() || undefined, name: c.name.trim() || undefined, createRecord: live && c.create })),
        colostrumGivenAt: live && colAt ? colAt : undefined, colostrumLitres: live ? toNum(colL) : undefined, retainedPlacenta: placenta, notes: notes.trim() || undefined,
      })
      await refreshHerd()
      notify(res.calves.length ? `Calving saved · ${res.calves.map((c) => c.tag).join(', ')} added` : 'Calving saved')
      nav(res.calves.length ? '/calves' : '/breeding', { replace: true })
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title="Record calving" close="/breeding" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <div>
          <label className="lab" htmlFor="cv-dam">Dam *</label>
          <AnimalSelect id="cv-dam" value={damId} onChange={setDamId} placeholder="Choose the cow"
            filter={(a) => a.sex === 'Female' && (expecting.has(a.id) || ['Heifer', 'Milking', 'Dry'].includes(a.category))} />
          {status?.expectedDueDate && <div className="hint">Due {shortDate(status.expectedDueDate)}{status.lastServiceDate ? ` · served ${shortDate(status.lastServiceDate)}` : ''}</div>}
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="cv-dt">Calving date *</label><input className="inp" id="cv-dt" type="date" max={todayIso()} value={date} onChange={(e) => e.target.value && setDate(e.target.value)} /></div>
          <div><label className="lab" htmlFor="cv-tm">Time *</label><input className="inp" id="cv-tm" type="time" value={time} onChange={(e) => setTime(e.target.value)} /></div>
        </div>
        <div><div className="lab">Outcome</div><Segmented label="Outcome" options={OUTCOMES} value={outcome} onChange={setOutcome} /></div>
        <div><div className="lab">Calving ease</div><Segmented label="Calving ease" options={EASES} value={ease} onChange={setEase} /></div>

        {outcome !== 'ABORTED' && calves.map((c, i) => (
          <div key={i} className="flex flex-col gap-3 rounded-[14px] bg-white p-3.5">
            <div className="flex items-center text-[15px] font-bold"><span className="flex-1">{calves.length > 1 ? `Calf ${i + 1}` : 'Calf'}</span>
              {i === calves.length - 1 && calves.length < 3 && <button type="button" onClick={() => setCalves((cur) => [...cur, newCalf(cur)])} className="h-8 border-0 bg-transparent text-[13px] font-semibold text-brand">+ Twin</button>}
              {i > 0 && <button type="button" onClick={() => setCalves((cur) => cur.filter((_, j) => j !== i))} className="h-8 border-0 bg-transparent text-[13px] font-semibold text-danger">Remove</button>}
            </div>
            <div><div className="lab">Sex</div><Segmented label={`Calf ${i + 1} sex`} options={[{ value: 'FEMALE', label: 'Female' }, { value: 'MALE', label: 'Male' }]} value={c.sex} onChange={(v) => setCalf(i, { sex: v })} /></div>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="lab" htmlFor={`cv-bw${i}`}>Birth weight (kg)</label><input className="inp" id={`cv-bw${i}`} inputMode="decimal" value={c.weight} onChange={(e) => setCalf(i, { weight: decimal(e.target.value) })} /></div>
              {live && <div><label className="lab" htmlFor={`cv-tg${i}`}>Calf tag</label><input className="inp" id={`cv-tg${i}`} value={c.tag} onChange={(e) => setCalf(i, { tag: e.target.value })} /></div>}
            </div>
            {live && <div className="text-xs text-ink-3">Next free tag suggested. Sire is filled from the service record.</div>}
            {live && <label className="flex min-h-12 items-center gap-2.5 text-[15px] font-semibold"><input type="checkbox" checked={c.create} onChange={(e) => setCalf(i, { create: e.target.checked })} className="h-[22px] w-[22px] accent-brand" />Create calf record</label>}
          </div>
        ))}

        {live && (
          <div className="flex flex-col gap-3 rounded-[14px] bg-warn-soft p-3.5">
            <div className="flex items-center gap-2 text-[15px] font-bold text-warn-ink"><span className="flex-1">Colostrum within {colHours} hours</span>{dueBy && <span className="text-[13px]">by {time12(dueBy)}</span>}</div>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="lab text-[#4A3210]" htmlFor="cv-cg">Given at</label><input className="inp" id="cv-cg" type="time" value={colAt} onChange={(e) => setColAt(e.target.value)} /></div>
              <div><label className="lab text-[#4A3210]" htmlFor="cv-cl">Litres</label><input className="inp" id="cv-cl" inputMode="decimal" placeholder="2.0" value={colL} onChange={(e) => setColL(decimal(e.target.value))} /></div>
            </div>
            {!colAt && <div className="text-[13px] font-semibold text-[#4A3210]">Not given yet — record it here or from the calf's feeding page.</div>}
          </div>
        )}

        <div className="flex flex-col gap-2 rounded-[14px] bg-white p-3.5">
          <div className="text-[15px] font-bold">Dam after calving</div>
          {dam && outcome !== 'ABORTED' && (
            <div className="flex items-center gap-2.5 text-[15px]"><span className="rounded-lg bg-ground px-2.5 py-1.5 font-semibold">{dam.category}</span><span aria-hidden="true">→</span><span className="rounded-lg bg-brand-soft px-2.5 py-1.5 font-bold text-brand">Milking</span></div>
          )}
          <div className="text-xs text-ink-3">{outcome === 'ABORTED' ? 'She goes back to open.' : 'A new lactation starts today; her heat watch starts after the waiting period.'}</div>
          <label className="flex min-h-12 items-center gap-2.5 text-[15px]"><input type="checkbox" checked={placenta} onChange={(e) => setPlacenta(e.target.checked)} className="h-[22px] w-[22px] accent-brand" />Placenta retained / needs vet</label>
        </div>
        <div><label className="lab" htmlFor="cv-nt">Notes</label><input className="inp" id="cv-nt" placeholder="Optional" value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <button type="button" className="btn" disabled={busy} onClick={save}>{busy ? 'Saving…' : created.length ? `Save calving & calf ${created.map((c) => c.tag).join(', ')}` : 'Save calving'}</button>
      </div>
    </Screen>
  )
}
