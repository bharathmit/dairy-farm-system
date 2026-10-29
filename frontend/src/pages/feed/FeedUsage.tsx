import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Screen, Segmented, TopBar } from '../../components/ui'
import { AnimalSelect, DateNav, LoadState } from '../../components/records'
import { FeedItemSelect, useFeed, useFeedGroups } from '../../components/feed'
import { useStore } from '../../data/store'
import { decimal, numText, todayIso, toNum, useLoad } from '../../data/records'
import { kg, RULE_CATEGORIES, SECTION_LABEL, type ItemRef, type Section, type Ses, type SheetLine, type Target } from '../../data/feed'
import { errorMessage } from '../../lib/api'

const SECTIONS: Section[] = ['GREEN', 'CONCENTRATE', 'DRY', 'OTHER']
const nowTime = () => { const d = new Date(); return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` }

export default function FeedUsage() {
  const [params, setParams] = useSearchParams()
  const { fd } = useFeed()
  const { notify } = useStore()
  const feedGroups = useFeedGroups()
  const nav = useNavigate()
  const date = params.get('date') || todayIso()
  const session: Ses = params.get('session') === 'PM' ? 'PM' : params.get('session') === 'AM' ? 'AM' : new Date().getHours() >= 12 ? 'PM' : 'AM'
  const target = (['FARM', 'GROUP', 'ANIMAL'].includes(params.get('target') ?? '') ? params.get('target') : 'FARM') as Target
  // Group = feeding group, worked out daily (same groups as the ration planner's "By group")
  const groupId = RULE_CATEGORIES.some((c) => c.value === params.get('group')) ? params.get('group')! : ''
  const animalId = params.get('animal') ?? ''
  const setParam = (p: Record<string, string>) => setParams((cur) => { const n = new URLSearchParams(cur); Object.entries(p).forEach(([k, v]) => (v ? n.set(k, v) : n.delete(k))); return n }, { replace: true })
  const ready = target === 'FARM' || (target === 'GROUP' && groupId) || (target === 'ANIMAL' && animalId)
  const sheet = useLoad(ready ? () => fd.sheet({ date, session, target, feedGroup: groupId || undefined, animalId: animalId || undefined }) : null,
    [fd, date, session, target, groupId, animalId])
  const items = useLoad(() => fd.items(), [fd])
  const [lines, setLines] = useState<SheetLine[]>([])
  const [actual, setActual] = useState<Record<number, string>>({})
  const [time, setTime] = useState(nowTime())
  const [notes, setNotes] = useState('')
  const [adding, setAdding] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const s = sheet.data
    if (!s) { setLines([]); return }
    setLines(s.lines)
    const a: Record<number, string> = {}
    s.lines.forEach((l) => { if (l.item) a[l.item.id] = l.actualKg != null ? numText(l.actualKg) : l.plannedKg != null ? numText(Math.round(l.plannedKg * 10) / 10) : '' })
    setActual(a)
  }, [sheet.data])

  const planned = lines.reduce((t, l) => t + (l.plannedKg ?? 0), 0)
  const total = lines.reduce((t, l) => t + (l.item ? toNum(actual[l.item.id] ?? '') ?? 0 : 0), 0)
  const warnings = useMemo(() => {
    const w: string[] = []
    lines.forEach((l) => {
      if (!l.item) return
      const a = toNum(actual[l.item.id] ?? '')
      if (l.plannedKg && a != null && a < l.plannedKg * 0.9) w.push(`${l.item.name} ${kg(l.plannedKg - a)} kg under plan.`)
      if (a != null && l.stock != null && a > l.stock) w.push(`${l.item.name}: only ${kg(l.stock)} kg in stock.`)
    })
    return [...new Set([...(sheet.data?.warnings ?? []), ...w])]
  }, [lines, actual, sheet.data])

  const addItem = () => {
    const it = items.data?.find((i) => String(i.id) === adding)
    if (!it || lines.some((l) => l.item?.id === it.id)) { setAdding(''); return }
    const ref: ItemRef = { id: it.id, name: it.name, category: it.category, unit: it.unit }
    const section: Section = it.category === 'GREEN_FODDER' || it.category === 'SILAGE' ? 'GREEN' : it.category === 'DRY_FODDER' ? 'DRY' : it.category === 'MIX' || it.category === 'CONCENTRATE_INGREDIENT' ? 'CONCENTRATE' : 'OTHER'
    setLines((ls) => [...ls, { item: ref, section, plannedKg: null, actualKg: null }])
    setAdding('')
  }
  const save = async () => {
    setBusy(true)
    try {
      await fd.saveSheet({
        date, session, time, target, feedGroup: target === 'GROUP' ? groupId : undefined, animalId: target === 'ANIMAL' ? Number(animalId) : undefined,
        lines: lines.flatMap((l) => (l.item ? [{ itemId: l.item.id, kg: toNum(actual[l.item.id] ?? '') ?? 0 }] : [])).filter((l) => l.kg > 0), notes: notes.trim() || undefined,
      })
      notify(`${session === 'AM' ? '6 AM' : '6 PM'} feeding saved · ${kg(total)} kg`)
      nav('/feed', { replace: true })
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title="Log feeding" close="/feed" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <div className="grid grid-cols-[1fr_120px] items-end gap-3">
          <DateNav id="fu-date" value={date} onChange={(v) => setParam({ date: v === todayIso() ? '' : v })} />
          <div><label className="lab" htmlFor="fu-tm">Time</label><input className="inp" id="fu-tm" type="time" value={time} onChange={(e) => setTime(e.target.value)} /></div>
        </div>
        <div><span className="lab">Session</span><Segmented label="Session" options={[{ value: 'AM', label: '6 AM' }, { value: 'PM', label: '6 PM' }]} value={session} onChange={(v) => setParam({ session: v })} /></div>
        <div>
          <span className="lab">Give to</span>
          <Segmented label="Give to" options={[{ value: 'FARM', label: 'Whole farm' }, { value: 'GROUP', label: 'Group' }, { value: 'ANIMAL', label: 'Animal' }]} value={target} onChange={(v) => setParam({ target: v })} />
          {target === 'GROUP' && (
            <select className="inp mt-2" aria-label="Group" value={groupId} onChange={(e) => setParam({ group: e.target.value })}>
              <option value="">Choose a feeding group</option>
              {RULE_CATEGORIES.map((c) => {
                const n = [...feedGroups.values()].filter((g) => g === c.value).length
                return <option key={c.value} value={c.value}>{c.label} · {n} animal{n === 1 ? '' : 's'}</option>
              })}
            </select>
          )}
          {target === 'ANIMAL' && <div className="mt-2"><AnimalSelect id="fu-animal" value={animalId} onChange={(v) => setParam({ animal: v })} /></div>}
          {sheet.data && <div className="hint">{sheet.data.label} · {sheet.data.animals} animal{sheet.data.animals === 1 ? '' : 's'} · planned from today's ration plan{sheet.data.saved ? ' · saved before, editing' : ''}</div>}
        </div>

        <LoadState loading={sheet.loading} error={sheet.error} onRetry={sheet.reload} empty={!sheet.data} />
        {sheet.data && (
          <div className="overflow-hidden rounded-[14px] bg-white">
            <div className="grid min-h-10 grid-cols-[1.7fr_.8fr_1fr] items-center gap-2 px-3 text-xs font-bold text-ink-3"><span>Item</span><span className="text-right">Planned</span><span className="text-center">Actual kg</span></div>
            {lines.length === 0 && <div className="border-t border-line-faint px-3 py-4 text-sm text-ink-3">No ration plan items yet. Add items below, or set up the ration rules.</div>}
            {SECTIONS.map((sec) => {
              const ls = lines.filter((l) => l.section === sec)
              if (!ls.length) return null
              return (
                <div key={sec}>
                  <div className="bg-[#F7F7F3] px-3 pb-1 pt-3 text-xs font-bold uppercase tracking-[.06em] text-ink-3">{SECTION_LABEL[sec]}</div>
                  {ls.map((l) => {
                    if (!l.item) {
                      return (
                        <div key={`unpicked-${sec}`} className="grid min-h-14 grid-cols-[1.7fr_.8fr_1fr] items-center gap-2 border-t border-line-faint px-3 text-sm">
                          <span className="text-ink-3">{sec === 'GREEN' ? 'Green fodder' : sec === 'DRY' ? 'Dry fodder' : 'Concentrate'} — no item picked in the rule</span>
                          <span className="text-right text-ink-3">{l.plannedKg != null ? kg(l.plannedKg) : '—'}</span>
                          <span className="text-center text-xs text-ink-3">Pick it in the ration rules</span>
                        </div>
                      )
                    }
                    const item = l.item
                    const a = toNum(actual[item.id] ?? '')
                    const off = l.plannedKg && a != null && a < l.plannedKg * 0.9
                    return (
                      <div key={item.id} className="grid min-h-14 grid-cols-[1.7fr_.8fr_1fr] items-center gap-2 border-t border-line-faint px-3 text-sm">
                        <span>{item.name}</span><span className="text-right text-ink-3">{l.plannedKg != null ? kg(l.plannedKg) : '—'}</span>
                        <input className={`h-11 w-full rounded-[10px] border bg-white text-center text-base font-bold ${off ? 'border-[#C2410C]' : 'border-line'}`} inputMode="decimal" aria-label={`${item.name} actual kg`} placeholder="0"
                          value={actual[item.id] ?? ''} onChange={(e) => setActual((c) => ({ ...c, [item.id]: decimal(e.target.value) }))} />
                      </div>
                    )
                  })}
                </div>
              )
            })}
          </div>
        )}
        {sheet.data && (
          <div className="flex gap-2">
            <div className="flex-1"><FeedItemSelect id="fu-add" items={items.data} value={adding} onChange={setAdding} placeholder="+ Add item" filter={(i) => !i.excluded && !lines.some((l) => l.item?.id === i.id)} /></div>
            <button type="button" className="h-12 rounded-[10px] border border-line bg-white px-4 font-semibold" disabled={!adding} onClick={addItem}>Add</button>
          </div>
        )}
        {warnings.length > 0 && (
          <div className="rounded-xl bg-warn-soft px-3.5 py-3 text-sm text-warn-text">{warnings.map((w) => <div key={w}>{w}</div>)}</div>
        )}
        <div><label className="lab" htmlFor="fu-notes">Notes</label><input className="inp" id="fu-notes" placeholder="e.g. less green, rain delayed cutting" value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <div className="mb-2.5 flex justify-between text-[15px]"><span>Planned {kg(planned)} kg</span><b>Actual {kg(total)} kg</b></div>
        <button type="button" className="btn" disabled={busy || !sheet.data || total <= 0} onClick={save}>{busy ? 'Saving…' : `Save ${session === 'AM' ? '6 AM' : '6 PM'} feeding`}</button>
      </div>
    </Screen>
  )
}
