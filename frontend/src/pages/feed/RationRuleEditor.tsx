import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { FeedItemSelect, useFeed } from '../../components/feed'
import { useStore } from '../../data/store'
import { addDays, decimal, numText, shortDate, todayIso, toNum, useLoad } from '../../data/records'
import { ruleLabel, type FeedItem, type Limit, type RationParams, type RuleCategory } from '../../data/feed'
import { errorMessage } from '../../lib/api'

type Num = 'bwPct' | 'concBaseKg' | 'concPerLitreKg' | 'maxConcKg' | 'greenSharePct' | 'dmIntakePct' | 'cpPct' | 'tdnPct' | 'caPct' | 'pPct'
interface Share { key: number; itemId: string; pct: string }
interface Lim { key: number; itemId: string; kg: string; stage: Limit['stage'] }
let seq = 1

const NUMS: { k: Num; label: string }[] = [
  { k: 'bwPct', label: 'Total feed (% of body weight)' }, { k: 'greenSharePct', label: 'Green share of fodder (%)' },
  { k: 'concBaseKg', label: 'Concentrate base (kg)' }, { k: 'concPerLitreKg', label: '+ kg per litre of milk' }, { k: 'maxConcKg', label: 'Concentrate max (kg/day)' },
]
const TARGETS: { k: Num; label: string }[] = [
  { k: 'dmIntakePct', label: 'DM intake (% BW)' }, { k: 'cpPct', label: 'CP (% of DM)' }, { k: 'tdnPct', label: 'TDN (% of DM)' }, { k: 'caPct', label: 'Ca (% of DM)' }, { k: 'pPct', label: 'P (% of DM)' },
]

function ShareList({ title, rows, setRows, items, filter, ro }: {
  title: string; rows: Share[]; setRows: (r: Share[]) => void; items: FeedItem[] | null; filter: (i: FeedItem) => boolean; ro: boolean
}) {
  const sum = rows.reduce((t, r) => t + (toNum(r.pct) ?? 0), 0)
  return (
    <div className="rounded-[14px] bg-white p-3.5">
      <div className="mb-2 flex items-baseline justify-between"><b className="text-[15px]">{title}</b><span className={`text-[13px] font-semibold ${rows.length && Math.abs(sum - 100) > 0.5 ? 'text-danger-text' : 'text-ink-3'}`}>{rows.length ? `${Math.round(sum)} % of 100` : 'none picked'}</span></div>
      {rows.map((r) => (
        <div key={r.key} className="mb-2 grid grid-cols-[1fr_76px_32px] items-center gap-2">
          <FeedItemSelect id={`sh-${r.key}`} items={items} value={r.itemId} onChange={(v) => setRows(rows.map((x) => (x.key === r.key ? { ...x, itemId: v } : x)))} filter={filter} />
          <input className="h-12 rounded-[10px] border border-line text-center" inputMode="decimal" aria-label="Share percent" disabled={ro} value={r.pct} onChange={(e) => setRows(rows.map((x) => (x.key === r.key ? { ...x, pct: decimal(e.target.value) } : x)))} />
          {!ro && <button type="button" aria-label="Remove" onClick={() => setRows(rows.filter((x) => x.key !== r.key))} className="h-8 w-8 rounded-lg border-0 bg-transparent text-lg text-ink-3">×</button>}
        </div>
      ))}
      {!ro && <button type="button" onClick={() => setRows([...rows, { key: seq++, itemId: '', pct: '' }])} className="h-10 w-full rounded-[10px] border border-dashed border-line bg-transparent text-sm font-semibold text-brand">+ Add item</button>}
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
  const [green, setGreen] = useState<Share[]>([])
  const [dry, setDry] = useState<Share[]>([])
  const [limits, setLimits] = useState<Lim[]>([])
  const [busy, setBusy] = useState(false)
  const ro = !isOwner

  useEffect(() => {
    if (!rule) return
    const p = rule.upcoming[0]?.params ?? rule.current.params
    setNums(Object.fromEntries([...NUMS, ...TARGETS].map(({ k }) => [k, numText(p[k])])) as Record<Num, string>)
    setConcItem(p.concItemId ? String(p.concItemId) : '')
    setGreen(p.green.map((g) => ({ key: seq++, itemId: String(g.itemId), pct: numText(g.sharePct) })))
    setDry(p.dry.map((g) => ({ key: seq++, itemId: String(g.itemId), pct: numText(g.sharePct) })))
    setLimits(p.limits.map((l) => ({ key: seq++, itemId: String(l.itemId), kg: numText(l.maxKgPerDay), stage: l.stage })))
  }, [rule])

  const preview = useMemo(() => {
    const bw = cat === 'MILKING' ? 450 : cat === 'CALF' ? 60 : cat === 'HEIFER' ? 250 : cat === 'BULL' ? 500 : 420
    const milk = cat === 'MILKING' ? 18 : 0
    const total = bw * (toNum(nums.bwPct ?? '') ?? 0) / 100
    const conc = Math.min(toNum(nums.maxConcKg ?? '') ?? 99, (toNum(nums.concBaseKg ?? '') ?? 0) + milk * (toNum(nums.concPerLitreKg ?? '') ?? 0))
    const forage = Math.max(0, total - conc)
    const g = forage * (toNum(nums.greenSharePct ?? '') ?? 0) / 100
    return { bw, milk, total, conc, green: g, dry: forage - g }
  }, [nums, cat])

  const save = async () => {
    const sharesOk = (rows: Share[]) => !rows.length || Math.abs(rows.reduce((t, r) => t + (toNum(r.pct) ?? 0), 0) - 100) <= 0.5
    if (!sharesOk(green) || !sharesOk(dry)) { notify('Green and dry item shares must each add up to 100 %'); return }
    const params: RationParams = {
      ...(Object.fromEntries([...NUMS, ...TARGETS].map(({ k }) => [k, toNum(nums[k] ?? '') ?? 0])) as Pick<RationParams, Num>),
      concItemId: concItem ? Number(concItem) : null,
      green: green.filter((g) => g.itemId).map((g) => ({ itemId: Number(g.itemId), sharePct: toNum(g.pct) ?? 0 })),
      dry: dry.filter((g) => g.itemId).map((g) => ({ itemId: Number(g.itemId), sharePct: toNum(g.pct) ?? 0 })),
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
                <b className="text-[15px]">Daily amounts</b>
                <div className="mt-3 grid grid-cols-2 gap-3">
                  {NUMS.map(({ k, label }) => <div key={k}><label className="lab" htmlFor={`rr-${k}`}>{label}</label><input className="inp" id={`rr-${k}`} inputMode="decimal" value={nums[k] ?? ''} onChange={(e) => setNums((n) => ({ ...n, [k]: decimal(e.target.value) }))} /></div>)}
                  <div><label className="lab" htmlFor="rr-conc">Concentrate item</label><FeedItemSelect id="rr-conc" items={items.data} value={concItem} onChange={setConcItem} filter={(i) => i.category === 'MIX' || i.category === 'CONCENTRATE_INGREDIENT'} placeholder="First mix item" /></div>
                </div>
                <div className="mt-3 rounded-[10px] bg-brand-soft px-3 py-2.5 text-[13px]">
                  Example {preview.bw} kg{preview.milk ? `, ${preview.milk} L/day` : ''}: total {preview.total.toFixed(1)} kg = green {preview.green.toFixed(1)} + dry {preview.dry.toFixed(1)} + concentrate {preview.conc.toFixed(1)} kg
                </div>
              </div>
              <ShareList title="Green fodder mix" rows={green} setRows={setGreen} items={items.data} filter={(i) => i.category === 'GREEN_FODDER' || i.category === 'SILAGE'} ro={ro} />
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
                <b className="text-[15px]">Nutrient needs</b>
                <div className="mt-3 grid grid-cols-2 gap-3">
                  {TARGETS.map(({ k, label }) => <div key={k}><label className="lab" htmlFor={`rr-${k}`}>{label}</label><input className="inp" id={`rr-${k}`} inputMode="decimal" value={nums[k] ?? ''} onChange={(e) => setNums((n) => ({ ...n, [k]: decimal(e.target.value) }))} /></div>)}
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
