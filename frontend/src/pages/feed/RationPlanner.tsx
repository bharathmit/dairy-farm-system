import { Link, useSearchParams } from 'react-router-dom'
import { Footer, Screen, Segmented, TopBar } from '../../components/ui'
import { AnimalSelect, Card, LoadState } from '../../components/records'
import { Tile, useFeed, useFeedGroups } from '../../components/feed'
import { L, shortDate, useLoad } from '../../data/records'
import { inr0 } from '../../data/breeding'
import { kg, NUTRIENT_TONE, ruleHint, ruleLabel, RULE_CATEGORIES, SECTION_LABEL, type AnimalRation, type RuleCategory, type Section, type Totals } from '../../data/feed'

type View = 'FARM' | 'CATEGORY' | 'COW'

function TotalsRow({ t, showMoney }: { t: Totals; showMoney: boolean }) {
  return (
    <div className="grid grid-cols-3 gap-2.5">
      <Tile k="Green / day" v={`${kg(t.greenKg, 0)} kg`} />
      <Tile k="Dry / day" v={`${kg(t.dryKg, 0)} kg`} />
      <Tile k="Concentrate" v={`${kg(t.concentrateKg, 0)} kg`} />
      {showMoney && t.costPerDay != null && <div className="col-span-3 -mt-1 text-[13px] text-ink-3">Feed cost {inr0(t.costPerDay)} / day</div>}
    </div>
  )
}

function CowRation({ r, showMoney }: { r: AnimalRation; showMoney: boolean }) {
  const sections: Section[] = ['GREEN', 'DRY', 'CONCENTRATE', 'OTHER']
  return (
    <>
      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Body weight</div><div className="mt-0.5 text-lg font-bold">{kg(r.bodyWeightKg, 0)} kg</div>
          <div className="text-xs text-ink-3">{r.weightSource === 'MEASURED' ? `Weighed ${shortDate(r.weighedOn)}` : r.weightSource === 'ANIMAL' ? 'From the animal record' : 'Estimate — weigh her'}</div></div>
        <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Milk yesterday</div><div className="mt-0.5 text-lg font-bold">{r.milkYesterday != null ? `${L(r.milkYesterday)} L` : '—'}</div>
          <div className="text-xs text-ink-3">{r.daysInMilk != null ? `Day ${r.daysInMilk} since calving${r.stage ? ` · ${r.stage.toLowerCase()} lactation` : ''}` : 'From milk log'}</div></div>
      </div>
      <div className="grid grid-cols-3 gap-2.5">
        <Tile k="Green" v={`${kg(r.totals.greenKg)} kg`} /><Tile k="Dry" v={`${kg(r.totals.dryKg)} kg`} /><Tile k="Concentrate" v={`${kg(r.totals.concentrateKg)} kg`} />
      </div>
      <div className="rounded-[14px] bg-white p-3.5">
        <div className="grid grid-cols-2 gap-3">
          <div><div className="text-xs font-semibold text-ink-3">Total fresh feed</div><div className="mt-0.5 text-lg font-bold">{kg(r.totals.totalKg)} kg/day</div>
            <div className="text-xs text-ink-3">Green + dry + concentrate, weighed as you feed it (with its moisture).</div></div>
          <div><div className="text-xs font-semibold text-ink-3">Dry matter intake (DMI)</div><div className="mt-0.5 text-lg font-bold">{kg(r.totals.targetKg)} kg/day</div>
            <div className="text-xs text-ink-3">The same feed with the moisture taken out. The TMR is planned on this.</div></div>
        </div>
        <div className="mt-2.5 text-[13px] text-ink-3">{r.explain}</div>
        <div className="mt-1 text-[13px] text-ink-3">Water ~{kg(r.totals.waterL, 0)} L/day</div>
      </div>
      <div className="overflow-hidden rounded-[14px] bg-white">
        {sections.map((s) => {
          const ls = r.lines.filter((l) => l.section === s)
          if (!ls.length) return null
          return (
            <div key={s}>
              <div className="flex justify-between bg-[#F7F7F3] px-3.5 pb-1 pt-3 text-xs font-bold uppercase tracking-[.06em] text-ink-3"><span>{SECTION_LABEL[s].split(' · ').pop()}</span></div>
              {ls.map((l, i) => <div key={i} className="flex justify-between border-t border-line-faint px-3.5 py-2.5 text-sm"><span>{l.item?.name ?? (s === 'GREEN' ? 'Green fodder (pick items in the rule)' : s === 'DRY' ? 'Dry fodder (pick items in the rule)' : 'Concentrate')}</span><b>{kg(l.kg)} kg</b></div>)}
            </div>
          )
        })}
        {r.ingredients.length > 0 && (
          <details className="border-t border-line-faint">
            <summary className="flex min-h-11 cursor-pointer items-center justify-between px-3.5 text-[13px] font-semibold text-brand">Concentrate ingredients ({r.ingredients.length})<span className="text-ink-3">Show</span></summary>
            {r.ingredients.map((g) => <div key={g.item.id} className="flex justify-between border-t border-line-faint px-3.5 py-2 text-[13px] text-ink-3"><span>of which {g.item.name}</span><span className={g.over ? 'font-bold text-danger-text' : ''}>{kg(g.kgPerDay, 2)} kg</span></div>)}
          </details>
        )}
        {showMoney && r.costPerDay != null && <div className="flex justify-between border-t border-line-faint bg-[#F7F7F3] px-3.5 py-2.5 text-sm font-bold"><span>Cost per day</span><span>{inr0(r.costPerDay)}</span></div>}
      </div>
      {r.ingredients.filter((g) => g.over).map((g) => <div key={g.item.id} className="rounded-xl bg-warn-soft px-3.5 py-3 text-sm text-warn-text"><b className="text-warn-ink">{g.item.name} {kg(g.kgPerDay, 2)} kg/day</b> — above the limit of {kg(g.limitKg, 2)} kg/day.</div>)}
      {r.capped && <div className="rounded-xl bg-warn-soft px-3.5 py-3 text-sm text-warn-text"><b className="text-warn-ink">Concentrate capped</b> at the {ruleLabel(r.rule.category)} maximum.</div>}
      <div className="overflow-hidden rounded-[14px] bg-white">
        <div className="flex justify-between px-3 pb-1.5 pt-3"><b className="text-[15px]">Nutrient check</b><span className="text-[13px] text-ink-3">whole day ration</span></div>
        <div className="grid min-h-9 grid-cols-[1fr_1fr_1fr_.8fr] items-center px-3 text-xs font-bold text-ink-3"><span>Nutrient</span><span>Ration</span><span>Need</span><span /></div>
        {r.nutrients.map((n) => (
          <div key={n.name} className="grid min-h-11 grid-cols-[1fr_1fr_1fr_.8fr] items-center border-t border-line-faint px-3 text-sm">
            <span>{n.name}</span><span>{n.ration != null ? `${kg(n.ration, 2)} ${n.unit}` : '—'}</span><span>{n.need != null ? `${kg(n.need, 2)} ${n.unit}` : '—'}</span>
            <span className={`badge justify-self-end ${NUTRIENT_TONE[n.status].cls}`}>{NUTRIENT_TONE[n.status].text}</span>
          </div>
        ))}
      </div>
    </>
  )
}

export default function RationPlanner() {
  const [params, setParams] = useSearchParams()
  const { fd, isOwner } = useFeed()
  const view = (['FARM', 'CATEGORY', 'COW'].includes(params.get('view') ?? '') ? params.get('view') : params.get('animal') ? 'COW' : 'FARM') as View
  const category = (params.get('category') as RuleCategory) || 'EARLY'
  const animalId = params.get('animal') ?? ''
  const setParam = (p: Record<string, string>) => setParams((cur) => { const n = new URLSearchParams(cur); Object.entries(p).forEach(([k, v]) => (v ? n.set(k, v) : n.delete(k))); return n }, { replace: true })
  const summary = useLoad(() => fd.summary(), [fd])
  const ration = useLoad(view === 'COW' && animalId ? () => fd.ration(animalId) : null, [fd, view, animalId])
  const groups = useFeedGroups()
  const s = summary.data
  const counts = new Map((s?.categories ?? []).map((c) => [c.category, c]))
  const cat = counts.get(category)
  const ruleCat = view === 'COW' ? ration.data?.rule.category ?? category : category

  return (
    <Screen>
      <TopBar title="Ration planner" back="/feed" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <Segmented label="View" options={[{ value: 'FARM', label: 'Farm total' }, { value: 'CATEGORY', label: 'By group' }, { value: 'COW', label: 'One cow' }]} value={view} onChange={(v) => setParam({ view: v })} />
        <LoadState loading={summary.loading} error={summary.error} onRetry={summary.reload} empty={!s} />
        {view === 'CATEGORY' && s && (
          <div className="flex gap-2 overflow-x-auto pb-1">
            {RULE_CATEGORIES.map((c) => (
              <button key={c.value} type="button" aria-pressed={category === c.value} onClick={() => setParam({ category: c.value, animal: '' })} className={`chip ${category === c.value ? 'chip-on' : ''}`}>
                {c.label}{counts.get(c.value)?.animals ? ` ${counts.get(c.value)!.animals}` : ''}
              </button>
            ))}
          </div>
        )}

        {view === 'FARM' && s && (
          <>
            <TotalsRow t={s.farm} showMoney={isOwner} />
            <div className="overflow-hidden rounded-[14px] bg-white">
              {s.categories.filter((c) => c.animals > 0).map((c, i) => (
                <button key={c.category} type="button" onClick={() => setParam({ view: 'CATEGORY', category: c.category })} className={`flex w-full items-center gap-2 border-0 bg-transparent px-3.5 py-3 text-left text-sm ${i ? 'border-t border-line-faint' : ''}`}>
                  <span className="flex-1"><b>{ruleLabel(c.category)}</b> · {c.animals} animal{c.animals === 1 ? '' : 's'}<div className="text-[13px] text-ink-3">Green {kg(c.totals.greenKg, 0)} · dry {kg(c.totals.dryKg, 0)} · conc {kg(c.totals.concentrateKg, 0)} kg/day{isOwner && c.totals.costPerDay != null ? ` · ${inr0(c.totals.costPerDay)}` : ''}</div></span>
                  <span className="text-ink-3">›</span>
                </button>
              ))}
              {!s.categories.some((c) => c.animals > 0) && <div className="px-4 py-5 text-center text-sm text-ink-3">No active animals to plan for. <Link to="/animals/new" className="font-semibold">Add an animal</Link></div>}
            </div>
            <Link to="/feed/need" className="btn2 h-12 no-underline">Feed need vs stock</Link>
          </>
        )}

        {view === 'CATEGORY' && s && (
          cat && cat.animals > 0 ? (
            <>
              <div className="-mb-1.5 text-[13px] text-ink-3">{ruleHint(category)}</div>
              <Card title={`${ruleLabel(category)} · ${cat.animals} animal${cat.animals === 1 ? '' : 's'}`}><TotalsRow t={cat.totals} showMoney={isOwner} /></Card>
              <div className="text-[13px] text-ink-3">Pick one animal to see her ration and nutrient check.</div>
              <AnimalSelect id="rp-cow" value={animalId} onChange={(v) => setParam({ view: 'COW', animal: v })} filter={(a) => groups.get(String(a.id)) === category} />
            </>
          ) : <div className="rounded-[14px] bg-white px-4 py-5 text-center text-sm text-ink-3">No animals in {ruleLabel(category)} today.</div>
        )}

        {view === 'COW' && (
          <>
            <div><label className="lab" htmlFor="rp-an">Cow</label><AnimalSelect id="rp-an" value={animalId} onChange={(v) => setParam({ animal: v })} placeholder="Choose an animal" />
              {ration.data && <div className="hint">{ration.data.animal.tag} · {ration.data.animal.name} · {ration.data.animal.breed} · {ruleLabel(ration.data.rule.category)}</div>}</div>
            <LoadState loading={ration.loading} error={ration.error} onRetry={ration.reload} empty={!ration.data} />
            {!animalId && <div className="rounded-[14px] bg-white px-4 py-5 text-center text-sm text-ink-3">Choose an animal to see her daily ration.</div>}
            {ration.data && <CowRation r={ration.data} showMoney={isOwner} />}
          </>
        )}

        {(view !== 'FARM') && (
          <Link to={`/feed/ration-rules/${ruleCat}`} className="btn2 h-12 no-underline">
            {isOwner ? 'Edit' : 'See'} {ruleLabel(ruleCat)} ration rule{ration.data?.rule.effectiveFrom ? ` (from ${shortDate(ration.data.rule.effectiveFrom)})` : ''}
          </Link>
        )}
        <Link to="/feed/log" className="btn h-12 no-underline hover:text-white">Log today's feeding from this plan</Link>
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
