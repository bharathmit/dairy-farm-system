import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Dialog, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { FeedItemSelect, useFeed } from '../../components/feed'
import { useStore } from '../../data/store'
import { decimal, numText, toNum, useLoad } from '../../data/records'
import { inr0 } from '../../data/breeding'
import { BIS_GRADES, bisCheck, inr2, kg, NUTRIENT_TONE, type BisGrade, type BisStatus, type FeedItem, type Formula, type NutrientStatus } from '../../data/feed'
import { errorMessage } from '../../lib/api'

interface Line { key: number; itemId: string; kg: string }
/** Compared with the chosen Mix targets item (as fed); same ±10 % rule as the backend. */
const NUTS = [['DM', 'dmPct', '%'], ['CP', 'cpPct', '%'], ['TDN', 'tdnPct', '%'], ['Ca', 'caPct', '%'], ['P', 'pPct', '%'],
  ['Crude fat', 'fatPct', '%'], ['Crude fibre', 'fibrePct', '%'], ['Acid insol. ash', 'aiaPct', '%'], ['Aflatoxin B1', 'aflatoxinPpb', 'ppb']] as const
/** Ingredient fields the BIS check needs, with the label used when an ingredient is missing one. */
const BIS_KEYS = [['dm', 'dmPct', 'DM'], ['cp', 'cpPct', 'CP'], ['fat', 'fatPct', 'fat'], ['fibre', 'fibrePct', 'fibre'], ['aia', 'aiaPct', 'acid insol. ash'],
  ['ca', 'caPct', 'Ca'], ['p', 'pPct', 'P'], ['aflatoxin', 'aflatoxinPpb', 'aflatoxin']] as const
const BIS_TONE: Record<BisStatus, { text: string; cls: string }> = {
  PASS: { text: 'Pass', cls: 'bg-brand-soft text-brand' }, FAIL: { text: 'Fail', cls: 'bg-danger-soft text-danger-text' }, UNKNOWN: { text: '—', cls: 'bg-ground text-ink-3' },
}
let seq = 1

export default function FormulaEditor() {
  const { id } = useParams()
  const { fd, isOwner } = useFeed()
  const { notify } = useStore()
  const nav = useNavigate()
  const existing = useLoad(id ? () => fd.formula(id) : null, [fd, id])
  const items = useLoad(() => fd.items({ includeInactive: true }), [fd])
  const [name, setName] = useState('')
  const [mixItem, setMixItem] = useState('')
  const [batchKg, setBatchKg] = useState('100')
  const [lines, setLines] = useState<Line[]>([])
  const [targets, setTargets] = useState<Record<string, string>>({})
  const [grade, setGrade] = useState<BisGrade | ''>('')
  const [notes, setNotes] = useState('')
  const [server, setServer] = useState<Formula | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const [blocked, setBlocked] = useState<string | null>(null)

  useEffect(() => {
    const f = existing.data
    if (!f) return
    setServer(f); setName(f.name); setMixItem(String(f.mixItem.id)); setBatchKg(numText(f.batchKg)); setNotes(f.notes ?? '')
    setLines(f.lines.map((l) => ({ key: seq++, itemId: String(l.item.id), kg: numText(l.kg) })))
    setTargets(Object.fromEntries(Object.entries(f.targets).filter(([k]) => k !== 'bisGrade').map(([k, v]) => [k, numText(v as number | null)])))
    setGrade(f.targets.bisGrade ?? '')
  }, [existing.data])

  const byId = useMemo(() => new Map((items.data ?? []).map((i) => [String(i.id), i])), [items.data])
  const calc = useMemo(() => {
    const rows = lines.map((l) => ({ l, it: byId.get(l.itemId), kg: toNum(l.kg) ?? 0 }))
    const total = rows.reduce((t, r) => t + r.kg, 0)
    const cost = rows.reduce((t, r) => t + r.kg * (r.it?.costPerUnit ?? 0), 0)
    const priced = rows.every((r) => !r.it || r.it.costPerUnit != null)
    const mixTarget = byId.get(mixItem)
    const nut = NUTS.map(([label, k, unit]) => {
      const known = rows.filter((r) => r.it && r.it[k] != null)
      const knownKg = known.reduce((t, r) => t + r.kg, 0)
      const mix = total > 0 && knownKg >= total * 0.999 ? known.reduce((t, r) => t + r.kg * ((r.it![k] as number) ?? 0), 0) / total : null
      // the Mix targets item's value; older formulas fall back to their saved target
      const tgt = mixTarget?.[k] ?? (k in targets ? toNum(targets[k] ?? '') ?? null : null)
      const status: NutrientStatus = mix == null || tgt == null ? 'UNKNOWN' : tgt > 0 && mix < tgt * 0.9 ? 'LOW' : tgt > 0 && mix > tgt * 1.1 ? 'HIGH' : 'OK'
      return { label, unit, mix, tgt, status }
    })
    const over = rows.filter((r) => r.it?.maxInclusionPct != null && total > 0 && (r.kg / total) * 100 > (r.it.maxInclusionPct ?? 100))
    // BIS: as-fed weighted averages; an ingredient missing a value makes that parameter unknown
    const used = rows.filter((r) => r.it && r.kg > 0)
    const avg = (k: (typeof BIS_KEYS)[number][1]) => (total > 0 && used.length && used.every((r) => r.it![k] != null)
      ? used.reduce((t, r) => t + r.kg * (r.it![k] as number), 0) / total : null)
    const mix = Object.fromEntries(BIS_KEYS.map(([m, k]) => [m, avg(k)])) as Parameters<typeof bisCheck>[1]
    const bis = grade && used.length ? bisCheck(grade, mix) : null
    const missing = used.map((r) => ({ name: r.it!.name, what: BIS_KEYS.filter(([, k]) => r.it![k] == null).map(([, , l]) => l) })).filter((x) => x.what.length)
    return { rows, total, cost, priced, nut, over, bis, missing, mixTarget }
  }, [lines, byId, targets, grade, mixItem])
  const batch = toNum(batchKg) ?? 0
  const costPerKg = calc.total > 0 ? calc.cost / calc.total : null
  const ro = !isOwner

  const setLine = (key: number, p: Partial<Line>) => setLines((ls) => ls.map((l) => (l.key === key ? { ...l, ...p } : l)))
  const pickItem = (key: number, v: string) => {
    const it = byId.get(v)
    if (it?.excluded) { setBlocked(`${it.name} can't be added. Its max inclusion is 0 % (excluded)${it.excludedReason ? ` — ${it.excludedReason}` : ''}.`); return }
    setLine(key, { itemId: v })
  }
  const body = () => ({
    name: name.trim(), mixItemId: Number(mixItem), batchKg: batch,
    targets: { ...Object.fromEntries(Object.entries(targets).map(([k, v]) => [k, toNum(v) ?? null])), bisGrade: grade || null },
    lines: lines.filter((l) => l.itemId && toNum(l.kg)).map((l, i) => ({ itemId: Number(l.itemId), kg: toNum(l.kg), sortOrder: i })), notes: notes.trim() || null,
  })
  const save = async (thenBatch: boolean) => {
    if (!name.trim()) { notify('Enter the formula name'); return }
    if (!mixItem) { notify('Pick the mix item it makes'); return }
    if (!lines.some((l) => l.itemId && toNum(l.kg))) { notify('Add at least one ingredient'); return }
    setBusy(true)
    try {
      const f = await fd.saveFormula(body(), id)
      setServer(f)
      notify(f.warnings.length ? `Saved with ${f.warnings.length} warning${f.warnings.length === 1 ? '' : 's'}` : 'Formula saved')
      nav(thenBatch ? `/feed/batch?formula=${f.id}` : id ? '/feed/formulas' : `/feed/formulas/${f.id}`, { replace: !thenBatch && !id })
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  const remove = async () => {
    if (!id) return
    try { await fd.deleteFormula(Number(id)); notify('Formula deleted'); nav('/feed/formulas') } catch (e) { setConfirm(false); notify(errorMessage(e)) }
  }
  const mixes = (items.data ?? []).filter((i: FeedItem) => i.category === 'MIX')

  return (
    <Screen>
      <TopBar title="Concentrate formula" back="/feed/formulas" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <LoadState loading={existing.loading || items.loading} error={existing.error ?? items.error} onRetry={() => { void existing.reload(); void items.reload() }} empty={!items.data || (Boolean(id) && !existing.data)} />
        <fieldset disabled={ro} className="m-0 flex flex-col gap-3.5 border-0 p-0">
          <div className="grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="fe-name">Formula name</label><input className="inp" id="fe-name" placeholder="Villupuram 100 kg" value={name} onChange={(e) => setName(e.target.value)} /></div>
            <div><label className="lab" htmlFor="fe-mix">Mix targets</label>
              <select className="inp" id="fe-mix" value={mixItem} onChange={(e) => setMixItem(e.target.value)}>
                <option value="">{mixes.length ? 'Choose' : 'No mix targets'}</option>{mixes.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}
              </select>
              {!mixes.length && isOwner && <Link to="/feed/items/new" className="mt-1.5 inline-block text-[13px] font-semibold">+ Add Mix targets</Link>}
            </div>
          </div>
          <div><label className="lab" htmlFor="fe-batch">Standard batch (kg)</label><input className="inp" id="fe-batch" inputMode="decimal" value={batchKg} onChange={(e) => setBatchKg(decimal(e.target.value))} /></div>
        </fieldset>

        <div className="flex items-center gap-3 rounded-xl bg-brand-soft px-3.5 py-3">
          <div className="flex-1"><div className="text-[13px] font-semibold text-ink-2">Batch total</div><div className="text-[22px] font-bold">{kg(calc.total)} kg <span className={`text-sm ${Math.abs(calc.total - batch) < 0.05 ? 'text-brand' : 'text-warn-ink'}`}>of {kg(batch, 0)}</span></div></div>
          {isOwner && <div className="text-right"><div className="text-[13px] font-semibold text-ink-2">Cost per kg</div><div className="text-[22px] font-bold">{calc.priced && costPerKg != null ? inr2(costPerKg) : '—'}</div></div>}
        </div>
        {isOwner && (toNum(targets.costMin ?? '') != null || toNum(targets.costMax ?? '') != null || calc.cost > 0) && (
          <div className="-mt-2 text-xs text-ink-3">{toNum(targets.costMin ?? '') != null && toNum(targets.costMax ?? '') != null ? `Target range ₹${targets.costMin}–${targets.costMax}/kg · ` : ''}batch {inr0(calc.cost)} · prices from latest purchases</div>
        )}

        <div className="overflow-hidden rounded-[14px] bg-white">
          <div className="grid min-h-10 grid-cols-[1.8fr_.9fr_.8fr] items-center gap-2 px-3 text-xs font-bold text-ink-3"><span>Ingredient</span><span className="text-center">kg</span><span className="text-right">{isOwner ? '₹' : '%'}</span></div>
          {calc.rows.map(({ l, it, kg: k }) => {
            const over = calc.over.some((o) => o.l.key === l.key)
            return (
              <div key={l.key} className={`grid min-h-14 grid-cols-[1.8fr_.9fr_.8fr] items-center gap-2 border-t border-line-faint px-3 py-1.5 text-sm ${over ? 'bg-warn-soft' : ''}`}>
                <div className="min-w-0">
                  {ro ? <span>{it?.name ?? '—'}</span> : <FeedItemSelect id={`fe-i-${l.key}`} items={items.data} value={l.itemId} onChange={(v) => pickItem(l.key, v)} filter={(i) => i.category !== 'MIX'} placeholder="Ingredient" />}
                  {over && <span className="badge mt-1 inline-block bg-white text-warn-ink">Over max {it?.maxInclusionPct} %</span>}
                </div>
                <input className={`h-11 w-full rounded-[10px] border bg-white text-center text-base font-bold ${over ? 'border-[#C2410C]' : 'border-line'}`} inputMode="decimal" aria-label={`${it?.name ?? 'Ingredient'} kg`} disabled={ro}
                  value={l.kg} onChange={(e) => setLine(l.key, { kg: decimal(e.target.value) })} />
                <span className="flex items-center justify-end gap-1 text-right text-ink-3">
                  {isOwner ? (it?.costPerUnit != null ? inr0(k * it.costPerUnit) : '—') : calc.total ? `${kg((k / calc.total) * 100)} %` : '—'}
                  {!ro && <button type="button" aria-label={`Remove ${it?.name ?? 'line'}`} onClick={() => setLines((ls) => ls.filter((x) => x.key !== l.key))} className="ml-1 h-8 w-8 rounded-lg border-0 bg-transparent text-lg text-ink-3">×</button>}
                </span>
              </div>
            )
          })}
          <div className="grid min-h-12 grid-cols-[1.8fr_.9fr_.8fr] items-center gap-2 border-t border-line-faint bg-[#F7F7F3] px-3 text-sm font-bold"><span>Total</span><span className="text-center">{kg(calc.total)}</span><span className="text-right">{isOwner ? inr0(calc.cost) : '100 %'}</span></div>
        </div>
        {!ro && <button type="button" onClick={() => setLines((ls) => [...ls, { key: seq++, itemId: '', kg: '' }])} className="h-11 rounded-[10px] border border-dashed border-line bg-transparent text-sm font-semibold text-brand">+ Add ingredient</button>}

        {blocked && <div className="rounded-xl bg-danger-soft px-3.5 py-3 text-sm text-danger-text" role="alert">{blocked} <button type="button" className="ml-1 border-0 bg-transparent p-0 font-semibold text-danger-text underline" onClick={() => setBlocked(null)}>OK</button></div>}
        {calc.over.map((o) => (
          <div key={o.l.key} className="rounded-xl bg-warn-soft px-3.5 py-3 text-sm text-warn-text"><b className="text-warn-ink">{o.it?.name} {kg((o.kg / calc.total) * 100)} % is above its max inclusion {o.it?.maxInclusionPct} %.</b> Save is allowed with a warning.</div>
        ))}
        {server?.warnings.filter((w) => !calc.over.some((o) => w.includes(o.it?.name ?? '§'))).map((w) => <div key={w} className="rounded-xl bg-warn-soft px-3.5 py-3 text-sm text-warn-text">{w}</div>)}

        <div className="overflow-hidden rounded-[14px] bg-white">
          <div className="flex justify-between px-3 pb-1.5 pt-3"><b className="text-[15px]">Nutrients in mix vs target</b><span className="text-[13px] text-ink-3">per 100 kg · as fed</span></div>
          <div className="grid min-h-9 grid-cols-[1.3fr_1fr_1fr_.7fr] items-center px-3 text-xs font-bold text-ink-3"><span>Nutrient</span><span>Mix</span><span>{calc.mixTarget ? calc.mixTarget.name : 'Target'}</span><span /></div>
          {calc.nut.map((n) => (
            <div key={n.label} className="grid min-h-[44px] grid-cols-[1.3fr_1fr_1fr_.7fr] items-center gap-2 border-t border-line-faint px-3 text-sm">
              <span>{n.label}</span><span>{n.mix != null ? `${kg(n.mix, 2)} ${n.unit}` : '—'}</span>
              <span className="text-ink-2">{n.tgt != null ? `${kg(n.tgt, 2)} ${n.unit}` : '—'}</span>
              <span className={`badge justify-self-end ${NUTRIENT_TONE[n.status].cls}`}>{NUTRIENT_TONE[n.status].text}</span>
            </div>
          ))}
        </div>
        {!mixItem && <div className="-mt-1.5 text-xs text-ink-3">Pick the Mix targets above to compare.</div>}
        {mixItem && isOwner && <div className="-mt-1.5 text-xs text-ink-3">Targets come from the Mix targets item; within ±10 % = OK. <Link to={`/feed/items/${mixItem}`} className="font-semibold">Edit targets</Link></div>}
        <div className="-mt-1.5 text-xs text-ink-3">Values fill in once each ingredient's nutrients are entered on its feed item.</div>

        <div className="overflow-hidden rounded-[14px] bg-white">
          <div className="flex items-center justify-between px-3 pb-1.5 pt-3">
            <b className="text-[15px]">BIS grade · IS 2052:2023</b>
            {calc.bis && <span className={`badge ${calc.bis.status === 'PASS' ? BIS_TONE.PASS.cls : calc.bis.status === 'FAIL' ? BIS_TONE.FAIL.cls : BIS_TONE.UNKNOWN.cls}`}>{calc.bis.status === 'PASS' ? `Meets ${calc.bis.label}` : calc.bis.status === 'FAIL' ? `Below ${calc.bis.label}` : 'Incomplete'}</span>}
          </div>
          <div role="group" aria-label="BIS grade" className="flex flex-wrap gap-2 px-3 pb-3">
            <button type="button" disabled={ro} aria-pressed={grade === ''} onClick={() => setGrade('')} className={`chip h-10 ${grade === '' ? 'chip-on' : ''}`}>No check</button>
            {BIS_GRADES.map((g) => <button key={g.value} type="button" disabled={ro} aria-pressed={grade === g.value} onClick={() => setGrade(g.value)} className={`chip h-10 ${grade === g.value ? 'chip-on' : ''}`}>{g.label} · {g.hint}</button>)}
          </div>
          {calc.bis && (
            <>
              <div className="grid min-h-9 grid-cols-[1.4fr_.9fr_.9fr_.7fr] items-center border-t border-line-faint px-3 text-xs font-bold text-ink-3"><span>Parameter</span><span>Mix</span><span>BIS</span><span /></div>
              {calc.bis.params.map((p) => (
                <div key={p.name} className="grid min-h-[42px] grid-cols-[1.4fr_.9fr_.9fr_.7fr] items-center gap-2 border-t border-line-faint px-3 text-sm">
                  <span>{p.name}</span>
                  <span>{p.value != null ? `${kg(p.value, 2)} ${p.unit}` : '—'}</span>
                  <span className="text-ink-3">{p.kind === 'MIN' ? '≥' : '≤'} {p.limit} {p.unit}</span>
                  <span className={`badge justify-self-end ${BIS_TONE[p.status].cls}`}>{BIS_TONE[p.status].text}</span>
                </div>
              ))}
            </>
          )}
        </div>
        {calc.bis && <div className="-mt-1.5 text-xs text-ink-3">Mix values are on dry-matter basis, as the standard requires (moisture is as fed). Lab-test a batch to confirm.</div>}
        {calc.bis && calc.missing.length > 0 && (
          <div className="rounded-xl bg-warn-soft px-3.5 py-3 text-sm text-warn-text">
            <b className="text-warn-ink">Missing on feed items:</b> {calc.missing.map((m) => `${m.name} (${m.what.join(', ')})`).join(' · ')}
          </div>
        )}
        {isOwner && (
          <div className="grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="fe-cmin">Cost target min ₹/kg</label><input className="inp" id="fe-cmin" inputMode="decimal" value={targets.costMin ?? ''} onChange={(e) => setTargets((t) => ({ ...t, costMin: decimal(e.target.value) }))} /></div>
            <div><label className="lab" htmlFor="fe-cmax">Cost target max ₹/kg</label><input className="inp" id="fe-cmax" inputMode="decimal" value={targets.costMax ?? ''} onChange={(e) => setTargets((t) => ({ ...t, costMax: decimal(e.target.value) }))} /></div>
          </div>
        )}
        <div><label className="lab" htmlFor="fe-notes">Notes</label><input className="inp" id="fe-notes" disabled={ro} placeholder="Optional" value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
        {id && isOwner && <button type="button" onClick={() => setConfirm(true)} className="h-12 rounded-xl border border-line bg-white font-semibold text-danger">Delete formula</button>}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        {isOwner ? (
          <div className="grid grid-cols-2 gap-2.5">
            <button type="button" className="btn2" disabled={busy} onClick={() => save(false)}>Save formula</button>
            <button type="button" className="btn" disabled={busy} onClick={() => save(true)}>{busy ? 'Saving…' : 'Prepare batch'}</button>
          </div>
        ) : id ? <Link to={`/feed/batch?formula=${id}`} className="btn no-underline hover:text-white">Prepare batch</Link> : null}
      </div>
      {confirm && (
        <Dialog title="Delete this formula?" onClose={() => setConfirm(false)}>
          <p className="mb-4 mt-0 text-sm text-ink-2">Batches already made stay in stock history.</p>
          <div className="grid grid-cols-2 gap-2.5">
            <button type="button" onClick={() => setConfirm(false)} className="h-12 rounded-xl border border-line bg-white font-semibold">Cancel</button>
            <button type="button" onClick={remove} className="h-12 rounded-xl bg-danger font-semibold text-white">Delete</button>
          </div>
        </Dialog>
      )}
    </Screen>
  )
}
