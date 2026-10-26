import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { FeedItemSelect, useFeed } from '../../components/feed'
import { useStore } from '../../data/store'
import { addDays, decimal, numText, shortDate, todayIso, toNum, useLoad } from '../../data/records'
import { isMilkingGroup, ruleHint, ruleLabel, STAGE_DMI, type FeedItem, type Limit, type RationParams, type RuleCategory } from '../../data/feed'
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

interface MixLine { key: number; name: string; dm: number; dmKg: number; fresh: number }
interface Mix { mixDm: number | null; lines: MixLine[]; fresh: number }

/** Splits a part's DM over its items: fixed-kg items first, the rest by share; fresh kg = DM ÷ item DM %. */
function mixOf(rows: Share[], items: FeedItem[] | null, fallback: number, partDm: number): Mix {
  const picked = rows.filter((r) => r.itemId)
  const info = (r: Share) => { const it = items?.find((i) => String(i.id) === r.itemId); return { name: it?.name ?? '', dm: it?.dmPct || fallback } }
  const shared = picked.filter((r) => !toNum(r.fixed))
  const sum = shared.reduce((t, r) => t + (toNum(r.pct) ?? 0), 0)
  const mixDm = sum > 0 ? shared.reduce((t, r) => t + info(r).dm * (toNum(r.pct) ?? 0), 0) / sum : null
  if (!picked.length) return { mixDm, lines: [], fresh: partDm / (fallback / 100) }
  const fixedDm = picked.reduce((t, r) => t + (toNum(r.fixed) ?? 0) * info(r).dm / 100, 0)
  const left = Math.max(0, partDm - fixedDm)
  const lines = picked.map((r) => {
    const { name, dm } = info(r)
    const fixed = toNum(r.fixed)
    const dmKg = fixed ? fixed * dm / 100 : sum > 0 ? left * (toNum(r.pct) ?? 0) / sum : 0
    return { key: r.key, name, dm, dmKg, fresh: fixed || dmKg / (dm / 100) }
  })
  return { mixDm, lines, fresh: lines.reduce((t, l) => t + l.fresh, 0) }
}

function ShareList({ title, rows, setRows, items, filter, ro, hint, fallback, partDm, dmi, mix }: {
  title: string; rows: Share[]; setRows: (r: Share[]) => void; items: FeedItem[] | null; filter: (i: FeedItem) => boolean; ro: boolean; hint?: string
  fallback: number; partDm: number; dmi: number; mix: Mix
}) {
  const shared = rows.filter((r) => !toNum(r.fixed))
  const sum = shared.reduce((t, r) => t + (toNum(r.pct) ?? 0), 0)
  const itemDm = (r: Share) => items?.find((i) => String(i.id) === r.itemId)?.dmPct
  const cols = 'grid-cols-[1fr_44px_58px_58px_28px]'
  return (
    <div className="rounded-[14px] bg-white p-3.5">
      <div className="mb-2 flex items-baseline justify-between"><b className="text-[15px]">{title}</b><span className={`text-[13px] font-semibold ${shared.length && Math.abs(sum - 100) > 0.5 ? 'text-danger-text' : 'text-ink-3'}`}>{rows.length ? `Share ${Math.round(sum)} % of 100` : 'none picked'}</span></div>
      {hint && <div className="hint mb-2 mt-0">{hint}</div>}
      {rows.length > 0 && <div className={`mb-1 grid ${cols} gap-1.5 text-xs font-semibold text-ink-3`}><span>Item</span><span className="text-center">DM %</span><span className="text-center">Share %</span><span className="text-center">Fixed kg</span><span /></div>}
      {rows.map((r) => {
        const dm = itemDm(r)
        return (
          <div key={r.key} className={`mb-2 grid ${cols} items-center gap-1.5`}>
            <FeedItemSelect id={`sh-${r.key}`} items={items} value={r.itemId} onChange={(v) => setRows(rows.map((x) => (x.key === r.key ? { ...x, itemId: v } : x)))} filter={filter} />
            <span className={`text-center text-sm ${dm ? '' : 'text-ink-3'}`} title={dm ? 'From Feed Master' : `Not set in Feed Master, ${fallback} % used`}>{r.itemId ? (dm ? numText(dm) : `${fallback}*`) : '—'}</span>
            <input className="h-12 rounded-[10px] border border-line text-center" inputMode="decimal" aria-label="Share percent" disabled={ro || !!toNum(r.fixed)} value={toNum(r.fixed) ? '' : r.pct} placeholder={toNum(r.fixed) ? '—' : ''} onChange={(e) => setRows(rows.map((x) => (x.key === r.key ? { ...x, pct: decimal(e.target.value) } : x)))} />
            <input className="h-12 rounded-[10px] border border-line text-center" inputMode="decimal" aria-label="Fixed kg per day" placeholder="opt." disabled={ro} value={r.fixed} onChange={(e) => setRows(rows.map((x) => (x.key === r.key ? { ...x, fixed: decimal(e.target.value) } : x)))} />
            {!ro && <button type="button" aria-label="Remove" onClick={() => setRows(rows.filter((x) => x.key !== r.key))} className="h-8 w-7 rounded-lg border-0 bg-transparent text-lg text-ink-3">×</button>}
          </div>
        )
      })}
      {!ro && <button type="button" onClick={() => setRows([...rows, { key: seq++, itemId: '', pct: '', fixed: '' }])} className="h-10 w-full rounded-[10px] border border-dashed border-line bg-transparent text-sm font-semibold text-brand">+ Add item</button>}
      {mix.lines.length > 0 && (
        <div className="mt-3 rounded-[10px] bg-brand-soft px-3 py-2.5 text-[13px]">
          {mix.mixDm != null && <div>Mix DM = Σ (item DM % × share %) = <b>{mix.mixDm.toFixed(1)} %</b></div>}
          <div>Example cow: {partDm.toFixed(1)} kg DM = <b>{dmi > 0 ? Math.round(partDm / dmi * 100) : 0} % of TMR</b> → {mix.fresh.toFixed(1)} kg fresh</div>
          {mix.lines.map((l) => <div key={l.key} className="text-ink-3">{l.name || 'Item'}: {l.dmKg.toFixed(1)} kg DM ÷ {numText(l.dm)} % = {l.fresh.toFixed(1)} kg fresh</div>)}
          {rows.some((r) => r.itemId && !itemDm(r)) && <div className="text-ink-3">* DM % not set in Feed Master, {fallback} % used.</div>}
        </div>
      )}
    </div>
  )
}

export default function RationRuleEditor() {
  const { category = 'EARLY' } = useParams()
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
    const milking = isMilkingGroup(cat)
    const bw = milking ? 450 : cat === 'CALF' ? 100 : cat === 'HEIFER' ? 250 : cat === 'BULL' ? 500 : 420
    const milk = milking ? 18 : 0
    const pct = milking ? n(STAGE_DMI[cat]!) : cat === 'HEIFER' ? n('dmiAge12to24Pct') : cat === 'CALF' ? n('dmiAge3to6Pct') : n('dmIntakePct')
    const dmi = bw * pct / 100
    const g = n('greenDmPct'); const d = n('dryDmPct'); const c = n('concDmPct'); const sum = g + d + c
    const rawConc = concMode === 'MILK' ? n('concBaseKg') + milk * n('concPerLitreKg') : sum > 0 ? dmi * c / sum / 0.8 : 0
    const conc = Math.min(nums.maxConcKg ? n('maxConcKg') : 99, rawConc)
    const concDm = conc * 0.8
    const forageDm = Math.max(0, dmi - concDm)
    const greenDm = g + d > 0 ? forageDm * g / (g + d) : 0
    const dryDm = forageDm - greenDm
    return {
      bw, milk, pct, dmi, conc, capped: conc < rawConc, concDm, forageDm, g, d, c, greenDm, dryDm,
      greenMix: mixOf(green, items.data, 30, greenDm), dryMix: mixOf(dry, items.data, 90, dryDm),
      waterBase: n('waterBaseL'), waterPerL: n('waterPerLitreL'), water: n('waterBaseL') + milk * n('waterPerLitreL'),
    }
  }, [nums, cat, concMode, green, dry, items.data])
  const ofTmr = (kg: number) => (preview.dmi > 0 ? Math.round(kg / preview.dmi * 100) : 0)
  const effDm = (dm: number, fresh: number) => (fresh > 0 ? (dm / fresh * 100).toFixed(0) : '—')
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
              <div className="text-ink-3">{ruleHint(cat)}{cat === 'DRY' || cat === 'HEIFER' ? ' (first service age and weight from Breeding settings)' : ''}.</div>
              In force {rule.current.effectiveFrom ? `since ${shortDate(rule.current.effectiveFrom)}` : 'as the default'}.
              {rule.upcoming.map((u) => <div key={u.effectiveFrom} className="text-info-text">Change saved from {shortDate(u.effectiveFrom)} (shown below).</div>)}
              {rule.history.length > 0 && <div className="text-ink-3">{rule.history.length} earlier version{rule.history.length === 1 ? '' : 's'} kept for past days.</div>}
            </div>
            <fieldset disabled={ro} className="m-0 flex flex-col gap-3.5 border-0 p-0">
              {isOwner && <div><label className="lab" htmlFor="rr-from">Effective from</label><input className="inp" id="rr-from" type="date" value={from} onChange={(e) => e.target.value && setFrom(e.target.value)} /><div className="hint">Saving on the same date replaces that version.</div></div>}
              <div className="rounded-[14px] bg-white p-3.5">
                <b className="text-[15px]">Dry matter intake (% of body weight)</b>
                <div className="mt-3 grid grid-cols-2 gap-3">
                  {(isMilkingGroup(cat) ? DMI_MILKING.filter((x) => x.k === STAGE_DMI[cat]) : cat === 'HEIFER' || cat === 'CALF' ? DMI_YOUNG
                    : cat === 'BULL' ? [{ k: 'dmIntakePct' as Num, label: 'DMI %' }]
                    : [{ k: 'dmIntakePct' as Num, label: 'Dry cows' }, { k: 'dmiAge12to24Pct' as Num, label: 'Heifers 12+ months' }]).map(numInput)}
                </div>
                <div className="hint">{isMilkingGroup(cat) ? 'Guide: early 3.75, mid 3.25, late 2.75 %.' : cat === 'HEIFER' || cat === 'CALF' ? 'Picked by age from the birth date. Guide: 3.00, 2.75, 2.50 %.' : cat === 'BULL' ? '' : 'Guide: dry cow 2.00 %, heifer 2.50 %.'}</div>
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
                  <div><label className="lab" htmlFor="rr-mode">Concentrate by</label><select className="inp" id="rr-mode" value={concMode} onChange={(e) => setConcMode(e.target.value as RationParams['concMode'])}><option value="MILK">Milk yield</option><option value="DMI">TMR share</option></select></div>
                  {(concMode === 'MILK' ? CONC : CONC.filter((x) => x.k === 'maxConcKg')).map(numInput)}
                  <div><label className="lab" htmlFor="rr-conc">Concentrate item</label><FeedItemSelect id="rr-conc" items={items.data} value={concItem} onChange={setConcItem} filter={(i) => i.category === 'MIX' || i.category === 'CONCENTRATE_INGREDIENT'} placeholder="First mix item" /></div>
                  {isMilkingGroup(cat) && WATER.filter((x) => x.k === 'waterPerLitreL').map(numInput)}
                </div>
                <div className="mt-3 rounded-[10px] bg-brand-soft px-3 py-2.5 text-[13px] leading-relaxed">
                  <b>Example: {preview.bw} kg animal{preview.milk ? `, ${preview.milk} L milk/day` : ''}</b>
                  <ol className="m-0 mt-1 list-decimal pl-5">
                    <li>DMI = {preview.bw} kg × {preview.pct} % = <b>{preview.dmi.toFixed(1)} kg DM</b></li>
                    <li>Concentrate = {concMode === 'MILK'
                      ? `${nums.concBaseKg || 0} + ${preview.milk} L × ${nums.concPerLitreKg || 0}`
                      : `${preview.dmi.toFixed(1)} × ${preview.c} % ÷ 80 % DM`}{preview.capped ? ' (capped at max)' : ''} = <b>{preview.conc.toFixed(1)} kg fresh</b> = {preview.concDm.toFixed(1)} kg DM ({ofTmr(preview.concDm)} % of TMR)</li>
                    <li>Forage DM left = {preview.dmi.toFixed(1)} − {preview.concDm.toFixed(1)} = {preview.forageDm.toFixed(1)} kg, split green {preview.g} : dry {preview.d}</li>
                    <li>Green {preview.greenDm.toFixed(1)} kg DM ({ofTmr(preview.greenDm)} % of TMR) ÷ {effDm(preview.greenDm, preview.greenMix.fresh)} % DM = <b>{preview.greenMix.fresh.toFixed(1)} kg fresh</b></li>
                    <li>Dry {preview.dryDm.toFixed(1)} kg DM ({ofTmr(preview.dryDm)} % of TMR) ÷ {effDm(preview.dryDm, preview.dryMix.fresh)} % DM = <b>{preview.dryMix.fresh.toFixed(1)} kg fresh</b></li>
                  </ol>
                  <div className="mt-1 text-ink-3">DM % comes from the items picked below (30 / 90 / 80 % until items are picked). Per-item kg are shown under each mix.</div>
                </div>
                <div className="mt-2 rounded-[10px] bg-brand-soft px-3 py-2.5 text-[13px]">
                  <b>Water</b>: {preview.milk
                    ? <>{preview.waterBase} L base + {preview.milk} L milk × {preview.waterPerL} L = <b>{preview.water.toFixed(0)} L/day</b></>
                    : <><b>{preview.waterBase} L/day</b></>}
                  <div className="text-ink-3">Shown on each animal's plan{isMilkingGroup(cat) ? ' (base + litres per litre of milk yesterday)' : ''}.</div>
                </div>
              </div>
              <ShareList title="Green fodder mix" rows={green} setRows={setGreen} items={items.data} filter={(i) => i.category === 'GREEN_FODDER' || i.category === 'SILAGE'} ro={ro} hint="DM % comes from Feed Master. Share = % of the green dry matter; shares add up to 100 %. Fixed kg for optional feeds (hydroponic fodder, azolla): fed first, skipped when the item is inactive, and the rest fills the share." fallback={30} partDm={preview.greenDm} dmi={preview.dmi} mix={preview.greenMix} />
              <ShareList title="Dry fodder mix" rows={dry} setRows={setDry} items={items.data} filter={(i) => i.category === 'DRY_FODDER'} ro={ro} hint="DM % comes from Feed Master. Share = % of the dry fodder dry matter; shares add up to 100 %." fallback={90} partDm={preview.dryDm} dmi={preview.dmi} mix={preview.dryMix} />
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
