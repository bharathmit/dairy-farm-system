import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Screen, Segmented, TopBar } from '../../components/ui'
import { DateNav, LoadState } from '../../components/records'
import { useBreeding } from '../../components/breeding'
import { useStore } from '../../data/store'
import { decimal, L, numText, todayIso, toNum, useLoad } from '../../data/records'
import { errorMessage } from '../../lib/api'

type Ses = 'AM' | 'PM'
const FILTERS = [
  { key: 'f3', label: 'Female · under 3 m', sex: 'FEMALE', maxDays: 90 },
  { key: 'all3', label: 'All · under 3 m', sex: undefined, maxDays: 90 },
  { key: 'all', label: 'All calves', sex: undefined, maxDays: undefined },
] as const

export default function CalfFeedingGroup() {
  const [params, setParams] = useSearchParams()
  const { br } = useBreeding()
  const { notify } = useStore()
  const nav = useNavigate()
  const date = params.get('date') || todayIso()
  const session: Ses = params.get('session') === 'PM' ? 'PM' : params.get('session') === 'AM' ? 'AM' : new Date().getHours() >= 13 ? 'PM' : 'AM'
  const sex = params.get('sex') ?? (params.has('maxDays') || params.has('minDays') ? '' : 'FEMALE')
  const maxDays = params.get('maxDays') ?? (params.has('sex') || params.has('minDays') ? '' : '90')
  const minDays = params.get('minDays') ?? ''
  const setParam = (k: string, v: string) => setParams((cur) => { const n = new URLSearchParams(cur); if (v) n.set(k, v); else n.delete(k); return n }, { replace: true })
  const sheet = useLoad(() => br.feedingSheet({ date, session, sex: sex || undefined, minDays: toNum(minDays), maxDays: toNum(maxDays) }),
    [br, date, session, sex, maxDays, minDays])
  const [litres, setLitres] = useState<Record<number, string>>({})
  const [starter, setStarter] = useState<Record<number, string>>({})
  const [all, setAll] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const s = sheet.data
    if (!s) return
    const l: Record<number, string> = {}; const st: Record<number, string> = {}
    for (const c of s.calves) {
      l[c.animal.id] = c.litres != null ? numText(c.litres) : c.planLitres != null ? c.planLitres.toFixed(1) : ''
      st[c.animal.id] = numText(c.starterKg)
    }
    setLitres(l); setStarter(st)
  }, [sheet.data])
  const calves = useMemo(() => sheet.data?.calves ?? [], [sheet.data])
  const totalL = calves.reduce((s, c) => s + (toNum(litres[c.animal.id] ?? '') ?? 0), 0)
  const totalS = calves.reduce((s, c) => s + (toNum(starter[c.animal.id] ?? '') ?? 0), 0)
  const step = (id: number, d: number) => setLitres((cur) => ({ ...cur, [id]: Math.max(0, Math.round(((toNum(cur[id] ?? '') ?? 0) + d) * 10) / 10).toFixed(1) }))
  const preset = FILTERS.find((f) => (f.sex ?? '') === sex && String(f.maxDays ?? '') === maxDays && !minDays)?.key ?? ''
  const name = session === 'AM' ? 'Morning' : 'Evening'

  const save = async () => {
    setBusy(true)
    try {
      await br.saveFeedingSheet({ date, session, entries: calves.map((c) => ({ animalId: c.animal.id, litres: toNum(litres[c.animal.id] ?? '') ?? null, starterKg: toNum(starter[c.animal.id] ?? '') ?? null })) })
      notify(`${name} feeding saved for ${calves.length} calves`)
      nav('/calves')
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title="Calf feeding" close="/calves" />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <div role="group" aria-label="Feeding mode" className="grid grid-cols-2 rounded-xl bg-track p-1">
          <Link to="/calves/feeding" className="flex h-10 items-center justify-center text-sm font-semibold text-ink-2 no-underline">One calf</Link>
          <span className="flex h-10 items-center justify-center rounded-[9px] bg-white text-sm font-semibold shadow-sm">Group</span>
        </div>
        <div><label className="lab" htmlFor="cg-who">Calves</label>
          <select className="inp" id="cg-who" value={preset} onChange={(e) => {
            const f = FILTERS.find((x) => x.key === e.target.value)
            if (!f) return
            setParams((cur) => { const n = new URLSearchParams(cur); n.delete('minDays'); n.set('sex', f.sex ?? ''); n.set('maxDays', String(f.maxDays ?? '')); return n }, { replace: true })
          }}>
            {!preset && <option value="">From calves list filter</option>}
            {FILTERS.map((f) => <option key={f.key} value={f.key}>{f.label}</option>)}
          </select>
        </div>
        <DateNav id="cg-date" value={date} onChange={(v) => setParam('date', v)} />
        <Segmented label="Session" options={[{ value: 'AM', label: 'Morning' }, { value: 'PM', label: 'Evening' }]} value={session} onChange={(v) => setParam('session', v)} />
        <div className="text-[13px] text-ink-3">Litres pre-filled from each calf's taper plan. Change any calf, or set starter for all at once.</div>
        <LoadState loading={sheet.loading} error={sheet.error} onRetry={sheet.reload} empty={!sheet.data} />
        {calves.map((c) => (
          <div key={c.animal.id} className="flex flex-col gap-2 rounded-[14px] bg-white px-3.5 py-3">
            <div className="flex items-center gap-2"><b className="flex-1">{c.animal.tag} <span className="font-medium text-ink-2">{c.sex === 'FEMALE' ? '♀' : '♂'} {c.ageDays} d</span></b>
              {c.colostrum ? <span className="badge bg-danger-soft text-danger-text">Colostrum</span> : c.planLitres != null && <span className="text-xs text-ink-3">plan {L(c.planLitres)} L</span>}</div>
            <div className="flex items-center gap-2">
              <span className="flex-1 text-xs text-ink-3">{c.colostrum ? 'Colostrum L' : 'Milk L'}</span>
              <button type="button" aria-label={`Less for ${c.animal.tag}`} onClick={() => step(c.animal.id, -0.5)} className="h-10 w-10 shrink-0 rounded-[10px] border border-line bg-white text-xl font-semibold">−</button>
              <input aria-label={`Litres for ${c.animal.tag}`} inputMode="decimal" value={litres[c.animal.id] ?? ''} onChange={(e) => setLitres((cur) => ({ ...cur, [c.animal.id]: decimal(e.target.value) }))} className="h-10 w-16 rounded-[10px] border border-line text-center text-lg font-bold" />
              <button type="button" aria-label={`More for ${c.animal.tag}`} onClick={() => step(c.animal.id, 0.5)} className="h-10 w-10 shrink-0 rounded-[10px] border border-line bg-white text-xl font-semibold">+</button>
            </div>
            {!c.colostrum && (
              <div className="flex items-center gap-2"><span className="flex-1 text-xs text-ink-3">Starter kg</span>
                <input aria-label={`Starter for ${c.animal.tag}`} inputMode="decimal" value={starter[c.animal.id] ?? ''} onChange={(e) => setStarter((cur) => ({ ...cur, [c.animal.id]: decimal(e.target.value) }))} className="h-10 w-16 rounded-[10px] border border-line text-center text-base font-bold" />
              </div>
            )}
          </div>
        ))}
        {sheet.data && calves.length === 0 && <div className="rounded-[14px] bg-white px-4 py-5 text-center text-sm text-ink-3">No calves in this group.</div>}
        {calves.length > 0 && (
          <div className="flex items-center gap-2 rounded-xl bg-white px-3.5 py-2.5">
            <span className="flex-1 text-sm">Same starter for all</span>
            <input aria-label="Starter for all calves" inputMode="decimal" placeholder="kg" value={all} onChange={(e) => setAll(decimal(e.target.value))} className="h-10 w-16 rounded-[10px] border border-line text-center text-base font-bold" />
            <button type="button" onClick={() => { if (!all) return; setStarter(Object.fromEntries(calves.filter((c) => !c.colostrum).map((c) => [c.animal.id, all]))) }} className="h-10 rounded-[10px] border border-line bg-white px-3 font-semibold">Apply</button>
          </div>
        )}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <div className="mb-2.5 flex justify-between text-[15px]"><span>{name} · {calves.length} calves</span><b>{L(totalL)} L milk · {L(totalS)} kg starter</b></div>
        <button type="button" className="btn" disabled={busy || calves.length === 0} onClick={save}>{busy ? 'Saving…' : `Save ${name.toLowerCase()} for ${calves.length} calves`}</button>
      </div>
    </Screen>
  )
}
