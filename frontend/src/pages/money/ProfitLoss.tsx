import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Footer, Screen, Segmented, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { BarRow, ChoiceChips, OwnerOnlyScreen, useMoney } from '../../components/money'
import { isoDate, useLoad } from '../../data/records'
import { finCategoryLabel, rupees } from '../../data/money'

type Range = 'LIFE' | 'YEAR' | 'MONTH'
const RANGES: { value: Range; label: string }[] = [{ value: 'LIFE', label: 'Lifetime' }, { value: 'YEAR', label: 'This year' }, { value: 'MONTH', label: 'This month' }]

function rangeOf(r: Range) {
  const t = new Date()
  if (r === 'YEAR') return { from: isoDate(new Date(t.getFullYear(), 0, 1)), to: isoDate(t) }
  if (r === 'MONTH') return { from: isoDate(new Date(t.getFullYear(), t.getMonth(), 1)), to: isoDate(t) }
  return { from: undefined, to: undefined }
}
const ageText = (d?: number | null) => (d == null ? '' : d < 60 ? `${d} days` : d < 730 ? `${Math.round(d / 30.4)} m` : `${(d / 365).toFixed(1).replace(/\.0$/, '')} y`)

export default function ProfitLoss() {
  const { mo, isOwner } = useMoney()
  const [params, setParams] = useSearchParams()
  const scope = params.get('scope') === 'FARM' ? 'FARM' : 'ANIMAL'
  const range = (params.get('r') as Range) || 'LIFE'
  const sel = params.get('animal') ?? ''
  const { from, to } = useMemo(() => rangeOf(range), [range])
  const pnl = useLoad(isOwner ? () => mo.pnl(scope, from, to) : null, [mo, scope, from, to, isOwner])
  const pick = pnl.data?.animals.find((a) => String(a.animal.id) === sel) ?? pnl.data?.animals[0]
  const detailId = pick ? String(pick.animal.id) : ''
  const detail = useLoad(isOwner && scope === 'ANIMAL' && detailId ? () => mo.animalPnl(detailId, from, to) : null, [mo, detailId, from, to, scope, isOwner])
  const set = (k: string, v: string) => { const n = new URLSearchParams(params); n.set(k, v); setParams(n, { replace: true }) }

  if (!isOwner) return <OwnerOnlyScreen title="Profit & loss" back="/money" />
  const p = pnl.data
  const d = detail.data
  const maxCat = Math.max(0, ...(p?.farm.byCategory ?? []).map((c) => Math.abs(c.amount)))

  return (
    <Screen wide>
      <TopBar title="Profit & loss" back="/money" />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <Segmented label="Scope" value={scope} onChange={(v) => set('scope', v)} options={[{ value: 'FARM', label: 'Farm' }, { value: 'ANIMAL', label: 'Per animal' }]} />
        <ChoiceChips label="Period" options={RANGES} value={range} onChange={(v) => set('r', v)} />
        <div className="text-[13px] text-ink-3">Net = milk income − feed − health − breeding − purchase + sale. Shared feed is split by days on the farm.</div>
        <LoadState loading={pnl.loading} error={pnl.error} onRetry={pnl.reload} empty={!p} />

        {p && scope === 'FARM' && (
          <div className="flex flex-col gap-1.5 rounded-[14px] bg-white p-3.5">
            <div className="grid grid-cols-3 gap-2 text-center">
              <div><div className="text-xs font-semibold text-ink-3">Income</div><b className="text-brand">{rupees(p.farm.income)}</b></div>
              <div><div className="text-xs font-semibold text-ink-3">Expense</div><b className="text-danger">{rupees(p.farm.expense)}</b></div>
              <div><div className="text-xs font-semibold text-ink-3">Net</div><b>{rupees(p.farm.net)}</b></div>
            </div>
            <div className="mt-2 border-t border-line-faint pt-2" />
            {p.farm.byCategory.map((c) => <BarRow key={c.category} label={finCategoryLabel(c.category)} amount={Math.abs(c.amount)} max={maxCat} tone={c.amount >= 0 ? 'in' : 'out'} />)}
          </div>
        )}

        {p && scope === 'ANIMAL' && (
          <>
            {p.animals.length === 0 ? <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">No animals with money recorded yet.</div> : (
              <div className="overflow-hidden rounded-[14px] bg-white">
                <div className="grid min-h-10 grid-cols-[28px_minmax(0,1fr)_100px] items-center gap-2 px-3 text-xs font-bold text-ink-3"><span>#</span><span>Animal · ranked by net</span><span className="text-right">{range === 'LIFE' ? 'Lifetime net' : 'Net'}</span></div>
                {p.animals.map((a) => {
                  const on = String(a.animal.id) === detailId
                  const loss = a.net < 0
                  const sub = [a.animal.breed, a.animal.category?.toLowerCase(), a.animal.lactationNumber ? `lactation ${a.animal.lactationNumber}` : null, a.note ?? (a.animal.category === 'CALF' ? ageText(a.animal.ageDays) : null)].filter(Boolean).join(' · ')
                  return (
                    <button key={a.animal.id} type="button" onClick={() => set('animal', String(a.animal.id))} aria-pressed={on}
                      className={`grid min-h-[58px] w-full grid-cols-[28px_minmax(0,1fr)_100px] items-center gap-2 border-0 border-t border-line-faint px-3 py-1.5 text-left text-sm ${on ? (loss ? 'bg-danger-soft' : 'bg-brand-soft') : 'bg-white'}`}>
                      <b>{a.rank}</b>
                      <span className="min-w-0"><b>{a.animal.tag}</b> {a.animal.name}<span className={`block truncate text-xs ${loss ? 'text-danger-text' : 'text-ink-3'}`}>{sub}</span></span>
                      <b className={`text-right ${loss ? 'text-danger-text' : 'text-brand'}`}>{rupees(a.net)}</b>
                    </button>
                  )
                })}
              </div>
            )}
            {detailId && <LoadState loading={detail.loading} error={detail.error} onRetry={detail.reload} empty={!d} />}
            {d && (
              <div className="rounded-[14px] bg-white p-3.5">
                <div className="flex items-center gap-2"><b className="flex-1 text-[17px]">{d.animal.tag} {d.animal.name}</b>{d.rank && d.of ? <span className="badge bg-brand-soft text-brand">Rank {d.rank} of {d.of}</span> : null}</div>
                {d.onFarmDays != null && <div className="mt-0.5 text-[13px] text-ink-3">{d.onFarmDays} days on farm{d.milkLitres ? ` · ${Math.round(d.milkLitres).toLocaleString('en-IN')} L milk` : ''}</div>}
                <div className="mt-2.5">
                  {d.lines.map((l, i) => (
                    <div key={l.key} className={`flex min-h-10 items-center justify-between text-[15px] ${i ? 'border-t border-line-faint' : ''}`}>
                      <span>{l.label}</span>
                      {l.amount === 0 && l.key === 'SALE' ? <span className="text-ink-3">— not sold</span>
                        : <b className={l.amount >= 0 ? 'text-brand' : 'text-danger'}>{rupees(l.amount, true)}</b>}
                    </div>
                  ))}
                  <div className="flex min-h-12 items-center justify-between border-t-2 border-ink text-[17px]"><b>{range === 'LIFE' ? 'Lifetime net' : 'Net'}</b><b className={d.net >= 0 ? 'text-brand' : 'text-danger-text'}>{rupees(d.net)}</b></div>
                </div>
                <div className="mt-2.5 grid grid-cols-3 gap-2">
                  <div className="rounded-[10px] bg-ground p-2.5"><div className="text-xs font-semibold text-ink-3">Per day</div><b>{rupees(d.perDay)}</b></div>
                  <div className="rounded-[10px] bg-ground p-2.5"><div className="text-xs font-semibold text-ink-3">Cost / L</div><b>{d.costPerLitre != null ? `₹${d.costPerLitre.toFixed(1)}` : '—'}</b></div>
                  <div className="rounded-[10px] bg-ground p-2.5"><div className="text-xs font-semibold text-ink-3">Margin</div><b>{d.marginPct != null ? `${Math.round(d.marginPct)} %` : '—'}</b></div>
                </div>
                <Link to={`/money/entries?animal=${d.animal.id}`} className="btn2 mt-3 h-11 text-sm">See all entries for {d.animal.name || d.animal.tag}</Link>
              </div>
            )}
          </>
        )}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
