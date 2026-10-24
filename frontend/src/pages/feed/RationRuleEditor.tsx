import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { FeedItemSelect, useFeed } from '../../components/feed'
import { useStore } from '../../data/store'
import { addDays, decimal, numText, shortDate, todayIso, toNum, useLoad } from '../../data/records'
import { ruleLabel, type FeedItem, type Limit, type RationParams, type RuleCategory } from '../../data/feed'
import { errorMessage } from '../../lib/api'

type Num = 'concBaseKg' | 'concPerLitreKg' | 'maxConcKg' | 'greenDmPct' | 'dryDmPct' | 'concDmPct' | 'dmIntakePct'
  | 'dmiEarlyPct' | 'dmiMidPct' | 'dmiLatePct' | 'dmiAge3to6Pct' | 'dmiAge6to12Pct' | 'dmiAge12to24Pct' | 'waterBaseL' | 'waterPerLitreL'
  | 'cpPct' | 'tdnPct' | 'caPct' | 'pPct'
interface Share { key: number; itemId: string; pct: string; fixed: string }
interface Lim { key: number; itemId: string; kg: string; stage: Limit['stage'] }
let seq = 1

const DMI_MILKING: { k: Num; label: string }[] = [
  { k: 'dmiEarlyPct', label: 'Early (≤ 100 days)' }, { k: 'dmiMidPct', label: 'Mid (≤ 200 days)' }, { k: 'dmiLatePct', label: 'Late' },
  { k: 'dmIntakePct', label: 'No calving date' },
]
const DMI_YOUNG: { k: Num; label: string }[] = [
  { k: 'dmiAge3to6Pct', label: '3–6 months' }, { k: 'dmiAge6to12Pct', label: '6–12 months' }, { k: 'dmiAge12to24Pct', label: '12+ months' },
  { k: 'dmIntakePct', label: 'Under 3 months / no birth date' },
]
const MIX: { k: Num; label: string; guide: [number, number] }[] = [
  { k: 'greenDmPct', label: 'Green fodder', guide: [35, 45] }, { k: 'dryDmPct', label: 'Dry fodder', guide: [15, 25] },
  { k: 'concDmPct', label: 'Concentrate', guide: [25, 40] },
]
const CONC: { k: Num; label: string }[] = [
  { k: 'concBaseKg', label: 'Concentrate base (kg)' }, { k: 'concPerLitreKg', label: '+ kg per litre of milk' }, { k: 'maxConcKg', label: 'Concentrate max (kg/day)' },
]
const WATER: { k: Num; label: string }[] = [{ k: 'waterBaseL', label: 'Water base (L/day)' }, { k: 'waterPerLitreL', label: '+ L per litre of milk' }]
const TARGETS: { k: Num; label: string }[] = [
  { k: 'cpPct', label: 'CP (% of DM)' }, { k: 'tdnPct', label: 'TDN (% of DM)' }, { k: 'caPct', label: 'Ca (% of DM)' }, { k: 'pPct', label: 'P (% of DM)' },
]
const ALL: Num[] = ['concBaseKg', 'concPerLitreKg', 'maxConcKg', 'greenDmPct', 'dryDmPct', 'concDmPct', 'dmIntakePct', 'dmiEarlyPct', 'dmiMidPct',
  'dmiLatePct', 'dmiAge3to6Pct', 'dmiAge6to12Pct', 'dmiAge12to24Pct', 'waterBaseL', 'waterPerLitreL', 'cpPct', 'tdnPct', 'caPct', 'pPct']

function ShareList({ title, rows, setRows, items, filter, ro, hint }: {
  title: string; rows: Share[]; setRows: (r: Share[]) => void; items: FeedItem[] | null; filter: (i: FeedItem) => boolean; ro: boolean; hint?: string
}) {
  const shared = rows.filter((r) => !toNum(r.fixed))
  const sum = shared.reduce((t, r) => t + (toNum(r.pct) ?? 0), 0)
  return (
    <div className="rounded-[14px] bg-white p-3.5">
      <div className="mb-2 flex items-baseline justify-between"><b className="text-[15px]">{title}</b><span className={`text-[13px] font-semibold ${shared.length && Math.abs(sum - 100) > 0.5 ? 'text-danger-text' : 'text-ink-3'}`}>{rows.length ? `${Math.round(sum)} % of 100` : 'none picked'}</span></div>
      {hint && <div className="hint mb-2 mt-0">{hint}</div>}
      {rows.length > 0 && <div className="mb-1 grid grid-cols-[1fr_64px_64px_28px] gap-1.5 text-xs font-semibold text-ink-3"><span>Item</span><span className="text-center">DM %</span><span className="text-center">Fixed kg</span><span /></div>}
      {rows.map((r) => (
        <div key={r.key} className="mb-2 grid grid-cols-[1fr_64px_64px_28px] items-center gap-1.5">
          <FeedItemSelect id={`sh-${r.key}`} items={items} value={r.itemId} onChange={(v) => setRows(rows.map((x) => (x.key === r.key ? { ...x, itemId: v } : x)))} filter={filter} />
          <input className="h-12 rounded-[10px] border border-line text-center" inputMode="decimal" aria-label="Share of dry matter percent" disabled={ro || !!toNum(r.fixed)} value={toNum(r.fixed) ? '' : r.pct} placeholder={toNum(r.fixed) ? '—' : ''} onChange={(e) => setRows(rows.map((x) => (x.key === r.key ? { ...x, pct: decimal(e.target.value) } : x)))} />
          <input className="h-12 rounded-[10px] border border-line text-center" inputMode="decimal" aria-label="Fixed kg per day" placeholder="opt." disabled={ro} value={r.fixed} onChange={(e) => setRows(rows.map((x) => (x.key === r.key ? { ...x, fixed: decimal(e.target.value) } : x)))} />
          {!ro && <button type="button" aria-label="Remove" onClick={() => setRows(rows.filter((x) => x.key !== r.key))} className="h-8 w-7 rounded-lg border-0 bg-transparent text-lg text-ink-3">×</button>}
        </div>
      ))}
      {!ro && <button type="button" onClick={() => setRows([...rows, { key: seq++, itemId: '', pct: '', fixed: '' }])} className="h-10 w-full rounded-[10px] border border-dashed border-line bg-transparent text-sm font-semibold text-brand">+ Add item</button>}
    </div>
  )
}

export default function RationRuleEditor() {
  const { category = 'MILKING' } = useParams()
  const cat = category.toUpperCase() as RuleCategory
  const { fd, isOwner } = useFeed()
  const { notify } = useStore()
  const nav = useNavigate()
  const rules = useLoad(() => fd.rules(), [fd])
  const items = useLoad(() => fd.items(), [fd])
  const rule = rules.data?.find((r) => r.category === cat)
  const [from, setFrom] = useState(addDays(todayIso(), 1))
  const [nums, setNums] = useState<Record<Num, string>>({} as Record<Num, string>)
  const [concItem, setConcItem] = useState('')
  const [concMode, setConcMode] = useState<RationParams['concMode']>('MILK')
  const [green, setGreen] = useState<Share[]>([])
  const [dry, setDry] = useState<Share[]>([])
  const [limits, setLimits] = useState<Lim[]>([])
  const [busy, setBusy] = useState(false)
  const ro = !isOwner

  useEffect(() => {
    if (!rule) return
    const p = rule.upcoming[0]?.params ?? rule.current.params
    setNums(Object.fromEntries(ALL.map((k) => [k, numText(p[k])])) as Record<Num, string>)
    setConcItem(p.concItemId ? String(p.concItemId) : '')
    setConcMode(p.concMode ?? 'MILK')
    const toRow = (g: RationParams['green'][number]) => ({ key: seq++, itemId: String(g.itemId), pct: numText(g.sharePct), fixed: g.fixedKg ? numText(g.fixedKg) : '' })
    setGreen(p.green.map(toRow))
    setDry(p.dry.map(toRow))
    setLimits(p.limits.map((l) => ({ key: seq++, itemId: String(l.itemId), kg: numText(l.maxKgPerDay), stage: l.stage })))
  }, [rule])

  // Example with standard DM: green 30 %, dry 90 %, concentrate 80 % (the plan uses each item's own DM %)
  const preview = useMemo(() => {
    const n = (k: Num) => toNum(nums[k] ?? '') ?? 0
    const bw = cat === 'MILKING' ? 450 : cat === 'CALF' ? 100 : cat === 'HEIFER' ? 250 : cat === 'BULL' ? 500 : 420
    const milk = cat === 'MILKING' ? 18 : 0
    const pct = cat === 'MILKING' ? n('dmiEarlyPct') : cat === 'HEIFER' ? n('dmiAge12to24Pct') : cat === 'CALF' ? n('dmiAge3to6Pct') : n('dmIntakePct')
    const dmi = bw * pct / 100
    const g = n('greenDmPct'); const d = n('dryDmPct'); const c = n('concDmPct'); const sum = g + d + c
    let conc = concMode === 'MILK' ? n('concBaseKg') + milk * n('concPerLitreKg') : sum > 0 ? dmi * c / sum / 0.8 : 0
    conc = Math.min(nums.maxConcKg ? n('maxConcKg') : 99, conc)
    const forageDm = Math.max(0, dmi - conc * 0.8)
    const greenDm = g + d > 0 ? forageDm * g / (g + d) : 0
    return { bw, milk, pct, dmi, conc, green: greenDm / 0.3, dry: (forageDm - greenDm) / 0.9, water: n('waterBaseL') + milk * n('waterPerLitreL') }
  }, [nums, cat, concMode])
  const mixSum = MIX.reduce((t, { k }) => t + (toNum(nums[k] ?? '') ?? 0), 0)
  const numInput = ({ k, label }: { k: Num; label: string }) => <div key={k}><label className="lab" htmlFor={`rr-${k}`}>{label}</label><input className="inp" id={`rr-${k}`} inputMode="decimal" value={nums[k] ?? ''} onChange={(e) => setNums((x) => ({ ...x, [k]: decimal(e.target.value) }))} /></div>

  const save = async () => {
    const sharesOk = (rows: Share[]) => { const r = rows.filter((x) => !toNum(x.fixed)); return !r.length || Math.abs(r.reduce((t, x) => t + (toNum(x.pct) ?? 0), 0) - 100) <= 0.5 }
    if (!sharesOk(green) || !sharesOk(dry)) { notify('Green and dry item shares must each add up to 100 % (fixed-kg items aside)'); return }
    if (Math.abs(mixSum - 100) > 0.5) { notify('Green, dry and concentrate shares of dry matter must add up to 100 %'); return }
    const toShare = (g: Share) => ({ itemId: Number(g.itemId), sharePct: toNum(g.fixed) ? 0 : toNum(g.pct) ?? 0, fixedKg: toNum(g.fixed) || null })
    const params: RationParams = {
      ...(Object.fromEntries(ALL.map((k) => [k, toNum(nums[k] ?? '') ?? 0])) as Pick<RationParams, Num>),
      concMode,
      concItemId: concItem ? Number(concItem) : null,
      green: green.filter((g) => g.itemId).map(toShare),
      dry: dry.filter((g) => g.itemId).map(toShare),
      limits: limits.filter((l) => l.itemId && toNum(l.kg)).map((l) => ({ itemId: Number(l.itemId), maxKgPerDay: toNum(l.kg) ?? 0, stage: l.stage })),
    }
    setBusy(true)
    try {
      await fd.saveRule(cat, { effectiveFrom: from, params })
      notify(`${ruleLabel(cat)} rule saved · from ${shortDate(from)}`)
      nav('/feed/ration-rules')
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title={`${ruleLabel(cat)} ration rule`} back="/feed/ration-rules" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <LoadState loading={rules.loading} error={rules.error} onRetry={rules.reload} empty={!rule} />
        {rule && (
          <>
            <div className="rounded-xl bg-white px-3.5 py-3 text-sm">
              In force {rule.current.effectiveFrom ? `since ${shortDate(rule.current.effectiveFrom)}` : 'as the default'}.
              {rule.upcoming.map((u) => <div key={u.effectiveFrom} className="text-info-text">Change saved from {shortDate(u.effectiveFrom)} (shown below).</div>)}
              {rule.history.length > 0 && <div className="text-ink-3">{rule.history.length} earlier version{rule.history.length === 1 ? '' : 's'} kept for past days.</div>}
            </div>
            <fieldset disabled={ro} className="m-0 flex flex-col gap-3.5 border-0 p-0">
              {isOwner && <div><label className="lab" htmlFor="rr-from">Effective from</label><input className="inp" id="rr-from" type="date" value={from} onChange={(e) => e.target.value && setFrom(e.target.value)} /><div className="hint">Saving on the same date replaces that version.</div></div>}
              <div className="rounded-[14px] bg-white p-3.5">
                <b className="text-[15px]">Dry matter intake (% of body weight)</b>
                <div className="mt-3 grid grid-cols-2 gap-3">
                  {(cat === 'MILKING' ? DMI_MILKING : cat === 'HEIFER' || cat === 'CALF' ? DMI_YOUNG : [{ k: 'dmIntakePct' as Num, label: 'DMI %' }]).map(numInput)}
                </div>
                <div className="hint">{cat === 'MILKING' ? 'Guide: early 3.75, mid 3.25, late 2.75 %.' : cat === 'HEIFER' || cat === 'CALF' ? 'Picked by age from the birth date. Guide: 3.00, 2.75, 2.50 %.' : cat === 'DRY' ? 'Guide: dry cow 2.00 %.' : ''}</div>
              </div>
              <div className="rounded-[14px] bg-white p-3.5">
                <div className="flex items-baseline justify-between"><b className="text-[15px]">TMR mix (% of dry matter)</b><span className={`text-[13px] font-semibold ${Math.abs(mixSum - 100) > 0.5 ? 'text-danger-text' : 'text-ink-3'}`}>{Math.round(mixSum)} % of 100</span></div>
                <div className="mt-3 grid grid-cols-3 gap-3">
                  {MIX.map(({ k, label, guide }) => {
                    const v = toNum(nums[k] ?? '')
                    const out = v != null && (v < guide[0] || v > guide[1])
                    return <div key={k}><label className="lab" htmlFor={`rr-${k}`}>{label}</label><input className="inp" id={`rr-${k}`} inputMode="decimal" value={nums[k] ?? ''} onChange={(e) => setNums((x) => ({ ...x, [k]: decimal(e.target.value) }))} /><div className={`hint ${out ? 'text-warn-text' : ''}`}>{guide[0]}–{guide[1]} %</div></div>
                  })}
                </div>
                <div className="hint">Fresh kg = DM ÷ each item's DM % from Feed Master.{concMode === 'MILK' ? ' With concentrate by milk, the forage left is split green : dry in this ratio.' : ''}</div>
              </div>
              <div className="rounded-[14px] bg-white p-3.5">
                <b className="text-[15px]">Concentrate & water</b>
                <div className="mt-3 grid grid-cols-2 gap-3">
                  <div><label className="lab" htmlFor="rr-mode">Concentrate by</label><select className="inp" id="rr-mode" value={concMode} onChange={(e) => setConcMode(e.target.value as RationParams['concMode'])}><option value="MILK">Milk yield</option><option value="DMI">DMI share</option></select></div>
                  {(concMode === 'MILK' ? CONC : CONC.filter((x) => x.k === 'maxConcKg')).map(numInput)}
                  <div><label className="lab" htmlFor="rr-conc">Concentrate item</label><FeedItemSelect id="rr-conc" items={items.data} value={concItem} onChange={setConcItem} filter={(i) => i.category === 'MIX' || i.category === 'CONCENTRATE_INGREDIENT'} placeholder="First mix item" /></div>
                  {(cat === 'MILKING' ? WATER : WATER.slice(0, 1)).map(numInput)}
                </div>
                <div className="mt-3 rounded-[10px] bg-brand-soft px-3 py-2.5 text-[13px]">
                  Example {preview.bw} kg{preview.milk ? `, ${preview.milk} L/day` : ''}: DMI {preview.pct} % = {preview.dmi.toFixed(1)} kg DM → green {preview.green.toFixed(1)} + dry {preview.dry.toFixed(1)} + concentrate {preview.conc.toFixed(1)} kg fresh · water {preview.water.toFixed(0)} L
                  <div className="text-ink-3">At 30 / 90 / 80 % DM; the plan uses each item's own DM %.</div>
                </div>
              </div>
              <ShareList title="Green fodder mix" rows={green} setRows={setGreen} items={items.data} filter={(i) => i.category === 'GREEN_FODDER' || i.category === 'SILAGE'} ro={ro} hint="Share = % of the green dry matter. Fixed kg for optional feeds (hydroponic fodder, azolla): fed first, skipped when the item is inactive, and silage fills the rest." />
              <ShareList title="Dry fodder mix" rows={dry} setRows={setDry} items={items.data} filter={(i) => i.category === 'DRY_FODDER'} ro={ro} />
              <div className="rounded-[14px] bg-white p-3.5">
                <b className="text-[15px]">Daily limits</b>
                <div className="hint mb-2 mt-0.5">e.g. gingelly cake max kg/day in early lactation. Concentrate ingredients count through the formula share.</div>
                {limits.map((l) => (
                  <div key={l.key} className="mb-2 grid grid-cols-[1fr_64px_92px_28px] items-center gap-1.5">
                    <FeedItemSelect id={`lm-${l.key}`} items={items.data} value={l.itemId} onChange={(v) => setLimits(limits.map((x) => (x.key === l.key ? { ...x, itemId: v } : x)))} />
                    <input className="h-12 rounded-[10px] border border-line text-center" inputMode="decimal" aria-label="Max kg per day" placeholder="kg" value={l.kg} onChange={(e) => setLimits(limits.map((x) => (x.key === l.key ? { ...x, kg: decimal(e.target.value) } : x)))} />
                    <select className="inp px-2" aria-label="Stage" value={l.stage} onChange={(e) => setLimits(limits.map((x) => (x.key === l.key ? { ...x, stage: e.target.value as Limit['stage'] } : x)))}>
                      <option value="ANY">Any</option><option value="EARLY">Early</option><option value="MID">Mid</option><option value="LATE">Late</option>
                    </select>
                    {!ro && <button type="button" aria-label="Remove limit" onClick={() => setLimits(limits.filter((x) => x.key !== l.key))} className="h-8 w-7 border-0 bg-transparent text-lg text-ink-3">×</button>}
                  </div>
                ))}
                {!ro && <button type="button" onClick={() => setLimits([...limits, { key: seq++, itemId: '', kg: '', stage: 'EARLY' }])} className="h-10 w-full rounded-[10px] border border-dashed border-line bg-transparent text-sm font-semibold text-brand">+ Add limit</button>}
                <div className="hint">Early ≤ 100 days in milk, mid ≤ 200, late after.</div>
              </div>
              <div className="rounded-[14px] bg-white p-3.5">
                <b className="text-[15px]">Nutrient needs (DM need = DMI above)</b>
                <div className="mt-3 grid grid-cols-2 gap-3">
                  {TARGETS.map(numInput)}
                </div>
              </div>
            </fieldset>
          </>
        )}
      </div>
      {isOwner && rule && (
        <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
          <button type="button" className="btn" disabled={busy || !items.data} onClick={save}>{busy ? 'Saving…' : `Save · effective ${shortDate(from)}`}</button>
        </div>
      )}
    </Screen>
  )
}
