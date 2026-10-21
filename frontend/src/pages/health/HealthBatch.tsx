import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Screen, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { useRecords } from '../../components/records'
import { inr, useStore } from '../../data/store'
import { todayIso, useLoad } from '../../data/records'
import { errorMessage } from '../../lib/api'
import HealthFields, { draftBody, emptyDraft, type HealthDraft } from './HealthFields'

export default function HealthBatch() {
  const { rec, isOwner } = useRecords()
  const { animals, groups, notify } = useStore()
  const nav = useNavigate()
  const active = useMemo(() => animals.filter((a) => a.status === 'Active').sort((x, y) => x.tag.localeCompare(y.tag, undefined, { numeric: true })), [animals])
  const firstGroup = groups.find((g) => active.some((a) => a.groupId === g.id))?.id ?? ''
  const [scope, setScope] = useState<string>(firstGroup || 'pick')
  const [q, setQ] = useState('')
  const [showAll, setShowAll] = useState(false)
  const [picked, setPicked] = useState<Set<string>>(() => new Set(firstGroup ? active.filter((a) => a.groupId === firstGroup).map((a) => a.id) : []))
  const [d, setD] = useState<HealthDraft>(emptyDraft(todayIso(), 'VACCINATION'))
  const [busy, setBusy] = useState(false)
  const wd = useLoad(() => rec.withdrawals(), [rec])
  const inWithdrawal = new Set((wd.data?.active ?? []).map((w) => String(w.record.animalId)))

  const inScope = scope === 'pick' ? active : active.filter((a) => a.groupId === scope)
  const shown = inScope.filter((a) => !q || `${a.tag} ${a.name ?? ''}`.toLowerCase().includes(q.toLowerCase()))
  const visible = showAll || q ? shown : shown.slice(0, 8)
  const allOn = inScope.length > 0 && inScope.every((a) => picked.has(a.id))

  const chooseScope = (s: string) => {
    setScope(s); setShowAll(false)
    setPicked(new Set(s === 'pick' ? [] : active.filter((a) => a.groupId === s).map((a) => a.id)))
  }
  const toggle = (id: string) => setPicked((p) => { const n = new Set(p); if (n.has(id)) n.delete(id); else n.add(id); return n })
  const toggleAll = () => setPicked((p) => { const n = new Set(p); inScope.forEach((a) => (allOn ? n.delete(a.id) : n.add(a.id))); return n })

  const count = picked.size
  const cost = Number(d.cost) || 0
  const save = async () => {
    if (!count) { notify('Pick at least one animal'); return }
    setBusy(true)
    try {
      const res = await rec.saveHealthBatch({ ...draftBody(d, isOwner), animalIds: [...picked].map(Number) })
      notify(`${res.count} health records saved`)
      nav('/health')
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title="Batch treatment" close="/health" />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <div className="flex gap-2 overflow-x-auto pb-1">
          {groups.map((g) => <button key={g.id} type="button" aria-pressed={scope === g.id} onClick={() => chooseScope(g.id)} className={`chip ${scope === g.id ? 'chip-on' : ''}`}>{g.name}</button>)}
          <button type="button" aria-pressed={scope === 'pick'} onClick={() => chooseScope('pick')} className={`chip ${scope === 'pick' ? 'chip-on' : ''}`}>Pick</button>
        </div>
        <div className="overflow-hidden rounded-[14px] bg-white">
          <label className="flex items-center gap-2 px-3.5 py-2.5"><Icon name="search" size={18} color="#56655B" />
            <input aria-label="Search animals" placeholder="Search tag or name" value={q} onChange={(e) => setQ(e.target.value)} className="h-9 min-w-0 flex-1 border-0 text-[15px] outline-none" /></label>
          {inScope.length > 0 && (
            <label className="flex min-h-11 items-center gap-3 border-t border-line-faint bg-brand-soft px-3.5 text-[15px] font-semibold">
              <input type="checkbox" checked={allOn} onChange={toggleAll} className="h-5 w-5 accent-brand" />
              <span className="flex-1">Select all{scope === 'pick' ? '' : ` in ${groups.find((g) => g.id === scope)?.name}`}</span>
              <span className="text-[13px] text-brand">{inScope.filter((a) => picked.has(a.id)).length} of {inScope.length}</span>
            </label>
          )}
          {inScope.length === 0 && <div className="border-t border-line-faint px-3.5 py-4 text-sm text-ink-3">No animals here.</div>}
          {visible.map((a) => (
            <label key={a.id} className="flex min-h-11 items-center gap-3 border-t border-line-faint px-3.5 text-[15px]">
              <input type="checkbox" checked={picked.has(a.id)} onChange={() => toggle(a.id)} className="h-5 w-5 accent-brand" />
              <span className="flex-1"><b>{a.tag}</b> {a.name}</span>
              {inWithdrawal.has(a.id) ? <span className="badge bg-warn-soft font-bold text-warn-ink">in withdrawal</span> : <span className="text-[13px] text-ink-3">{a.breed}</span>}
            </label>
          ))}
          {!showAll && !q && shown.length > 8 && <div className="flex min-h-11 items-center justify-center border-t border-line-faint"><button type="button" onClick={() => setShowAll(true)} className="border-0 bg-transparent text-sm font-semibold text-brand">Show {shown.length - 8} more</button></div>}
        </div>
        <div><label className="lab" htmlFor="hb-dt">Date</label><input className="inp" id="hb-dt" type="date" max={todayIso()} value={d.date} onChange={(e) => e.target.value && setD({ ...d, date: e.target.value })} /></div>
        <HealthFields d={d} set={(p) => setD((c) => ({ ...c, ...p }))} showMoney={isOwner} batch />
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <div className="mb-2.5 flex justify-between text-[15px]"><span>Will create <b>{count} record{count === 1 ? '' : 's'}</b></span>{isOwner && cost > 0 && <span className="text-[13px] text-ink-3">Cost {inr(cost * count)}</span>}</div>
        <button type="button" className="btn" disabled={busy || !count} onClick={save}>{busy ? 'Saving…' : `Save ${count} record${count === 1 ? '' : 's'}`}</button>
      </div>
    </Screen>
  )
}
