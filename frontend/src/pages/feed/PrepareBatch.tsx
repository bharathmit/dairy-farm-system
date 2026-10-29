import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Screen, Segmented, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { useFeed } from '../../components/feed'
import { useStore } from '../../data/store'
import { decimal, todayIso, toNum, useLoad } from '../../data/records'
import { inr0 } from '../../data/breeding'
import { inr2, kg } from '../../data/feed'
import { errorMessage } from '../../lib/api'

type Size = 'X1' | 'X2' | 'CUSTOM'

export default function PrepareBatch() {
  const [params, setParams] = useSearchParams()
  const { fd, isOwner } = useFeed()
  const { notify, members } = useStore()
  const nav = useNavigate()
  const formulas = useLoad(() => fd.formulas(), [fd])
  const formulaId = params.get('formula') ?? ''
  const formula = formulas.data?.find((f) => String(f.id) === formulaId) ?? (formulas.data?.length === 1 ? formulas.data[0] : undefined)
  const [size, setSize] = useState<Size>('X1')
  const [custom, setCustom] = useState('')
  const [date, setDate] = useState(todayIso())
  const [by, setBy] = useState('')
  const [allowShort, setAllowShort] = useState(false)
  const [busy, setBusy] = useState(false)
  const base = formula?.batchKg ?? 100
  const batchKg = size === 'X1' ? base : size === 'X2' ? base * 2 : toNum(custom) ?? 0
  const preview = useLoad(formula && batchKg > 0 ? () => fd.batchPreview(formula.id, batchKg) : null, [fd, formula?.id, batchKg])
  const p = preview.data
  useEffect(() => { setAllowShort(false) }, [batchKg, formula?.id])
  const active = members.filter((m) => m.active)

  // Largest batch the current stock allows, rounded down to 5 kg.
  const possible = useMemo(() => {
    if (!p || !p.shortCount) return null
    const ratio = Math.min(...p.lines.filter((l) => l.needKg > 0).map((l) => l.stock / l.needKg))
    return Math.max(0, Math.floor((p.batchKg * ratio) / 5) * 5)
  }, [p])
  const shortLines = p?.lines.filter((l) => l.shortKg > 0) ?? []

  const confirm = async () => {
    if (!formula) { notify('Choose a formula'); return }
    if (p?.shortCount && !allowShort) { notify('Some ingredients are short — buy first, make less, or tick "use the stock I have"'); return }
    setBusy(true)
    try {
      const res = await fd.prepareBatch(formula.id, { date, batchKg, preparedByMemberId: by ? Number(by) : undefined, allowShort })
      notify(`${formula.mixItem.name} +${kg(batchKg, 0)} kg · now ${kg(res.mix.stock, 0)} kg`)
      nav('/feed', { replace: true })
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title="Prepare batch" close="/feed" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <LoadState loading={formulas.loading} error={formulas.error} onRetry={formulas.reload} empty={!formulas.data} />
        {formulas.data && formulas.data.length === 0 && (
          <div className="rounded-[14px] bg-white px-4 py-5 text-center text-sm text-ink-3">No concentrate formula yet. {isOwner && <Link to="/feed/formulas/new" className="font-semibold">Create one first</Link>}</div>
        )}
        {formulas.data && formulas.data.length > 0 && (
          <div>
            <div className="flex items-baseline justify-between"><label className="lab" htmlFor="pb-f">Formula</label>{formula && <Link to={`/feed/formulas/${formula.id}`} className="text-[13px] font-semibold">Edit formula</Link>}</div>
            <select className="inp" id="pb-f" value={formula ? String(formula.id) : ''} onChange={(e) => setParams({ formula: e.target.value }, { replace: true })}>
              <option value="">Choose</option>
              {formulas.data.map((f) => <option key={f.id} value={f.id}>{f.name}{isOwner && f.costPerKg != null ? ` · ${inr2(f.costPerKg)}/kg` : ''}</option>)}
            </select>
          </div>
        )}
        {formula && (
          <>
            <div>
              <span className="lab">Batch size</span>
              <Segmented label="Batch size" options={[{ value: 'X1', label: `${kg(base, 0)} kg` }, { value: 'X2', label: `${kg(base * 2, 0)} kg` }, { value: 'CUSTOM', label: 'Custom' }]} value={size} onChange={setSize} />
              {size === 'CUSTOM' && <input className="inp mt-2" aria-label="Custom batch kg" inputMode="decimal" placeholder="kg" value={custom} onChange={(e) => setCustom(decimal(e.target.value))} />}
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="lab" htmlFor="pb-dt">Date</label><input className="inp" id="pb-dt" type="date" max={todayIso()} value={date} onChange={(e) => e.target.value && setDate(e.target.value)} /></div>
              <div><label className="lab" htmlFor="pb-by">Prepared by</label>
                {active.length ? (
                  <select className="inp" id="pb-by" value={by} onChange={(e) => setBy(e.target.value)}><option value="">Me</option>{active.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}</select>
                ) : <div className="flex h-12 items-center text-ink-3">Me</div>}
              </div>
            </div>
            <LoadState loading={preview.loading} error={preview.error} onRetry={preview.reload} empty={!p} />
            {p && (
              <>
                <div className="overflow-hidden rounded-[14px] bg-white">
                  <div className="flex items-center justify-between px-3 pb-1.5 pt-3"><b className="text-[15px]">Ingredient stock check</b>{p.shortCount > 0 ? <span className="badge bg-danger-soft text-danger-text">{p.shortCount} short</span> : <span className="badge bg-brand-soft text-brand">All in stock</span>}</div>
                  <div className="grid min-h-9 grid-cols-[1.6fr_.7fr_.7fr_.9fr] items-center gap-1 px-3 text-xs font-bold text-ink-3"><span>Ingredient</span><span className="text-right">Need</span><span className="text-right">Stock</span><span className="text-right" /></div>
                  {p.lines.map((l) => (
                    <div key={l.item.id} className={`grid min-h-11 grid-cols-[1.6fr_.7fr_.7fr_.9fr] items-center gap-1 border-t border-line-faint px-3 text-sm ${l.shortKg > 0 ? 'bg-danger-soft' : ''}`}>
                      <span>{l.item.name}</span><span className="text-right">{kg(l.needKg)}</span><span className="text-right">{kg(l.stock)}</span>
                      <span className={`text-right text-xs font-semibold ${l.shortKg > 0 ? 'text-danger-text' : 'text-brand'}`}>{l.shortKg > 0 ? `Short ${kg(l.shortKg)}` : 'Enough'}</span>
                    </div>
                  ))}
                </div>
                {shortLines.length > 0 && (
                  <div className="flex flex-col gap-2 rounded-xl bg-danger-soft px-3.5 py-3 text-sm text-danger-text">
                    <b>{shortLines.map((l) => `${l.item.name} short by ${kg(l.shortKg)} kg`).join(' · ')}.</b>
                    <span>Buy first, make {possible ? `${possible} kg` : 'a smaller batch'} instead, or confirm with the stock you have.</span>
                    <div className="flex flex-wrap gap-2">
                      {shortLines.slice(0, 2).map((l) => <Link key={l.item.id} to={`/feed/buy?item=${l.item.id}`} className="flex h-10 items-center rounded-[10px] border border-danger bg-white px-3 text-sm font-semibold text-danger-text no-underline">Buy {l.item.name}</Link>)}
                      {possible ? <button type="button" onClick={() => { setSize('CUSTOM'); setCustom(String(possible)) }} className="h-10 rounded-[10px] border border-danger bg-white px-3 text-sm font-semibold text-danger-text">Make {possible} kg</button> : null}
                    </div>
                    <label className="flex min-h-10 items-center gap-2.5"><input type="checkbox" checked={allowShort} onChange={(e) => setAllowShort(e.target.checked)} className="h-5 w-5 accent-brand" />Use the stock I have (stock goes below zero)</label>
                  </div>
                )}
                <div className="rounded-[14px] bg-white p-3.5">
                  <b className="text-[15px]">Result</b>
                  <div className="mt-1 text-[15px]">{formula.mixItem.name} <b className="text-brand">+{kg(p.batchKg, 0)} kg</b>{isOwner && p.costPerKg != null ? ` at ${inr2(p.costPerKg)}/kg` : ''}</div>
                  <div className="mt-1 text-[13px] text-ink-3">{[isOwner && p.batchCost != null && `Batch cost ${inr0(p.batchCost)}`, `mix stock ${kg(p.mixStockBefore, 0)} → ${kg(p.mixStockAfter, 0)} kg`, p.extraDays != null && `about ${kg(p.extraDays, 1)} more days`].filter(Boolean).join(' · ')}</div>
                  <div className="hint">Saves one usage entry per ingredient and one produce entry for the mix.</div>
                </div>
              </>
            )}
          </>
        )}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <button type="button" className="btn" disabled={busy || !formula || !p || batchKg <= 0} onClick={confirm}>{busy ? 'Saving…' : `Confirm ${kg(batchKg, 0)} kg batch`}</button>
      </div>
    </Screen>
  )
}
