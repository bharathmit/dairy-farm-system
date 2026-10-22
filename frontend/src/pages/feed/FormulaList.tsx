import { Link } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { useFeed } from '../../components/feed'
import { useLoad } from '../../data/records'
import { inr2, kg } from '../../data/feed'

export default function FormulaList() {
  const { fd, isOwner } = useFeed()
  const res = useLoad(() => fd.formulas(), [fd])
  const list = res.data ?? []
  return (
    <Screen>
      <TopBar title="Concentrate formulas" back="/feed" right={isOwner ? <Link to="/feed/formulas/new" className="flex h-10 items-center rounded-[10px] bg-brand px-3 text-sm font-semibold text-white no-underline hover:text-white">+ Formula</Link> : undefined} />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <LoadState loading={res.loading} error={res.error} onRetry={res.reload} empty={!res.data} />
        {list.map((f) => (
          <div key={f.id} className="rounded-[14px] bg-white p-3.5">
            <Link to={`/feed/formulas/${f.id}`} className="block text-ink no-underline hover:text-ink">
              <div className="flex items-center gap-2"><b className="flex-1 text-[15px]">{f.name}</b>{f.warnings.length > 0 && <span className="badge bg-warn-soft text-warn-ink">{f.warnings.length} warning{f.warnings.length === 1 ? '' : 's'}</span>}</div>
              <div className="mt-0.5 text-[13px] text-ink-3">Makes {f.mixItem.name} · {kg(f.batchKg, 0)} kg batch · {f.lines.length} ingredients{isOwner && f.costPerKg != null ? ` · ${inr2(f.costPerKg)}/kg` : ''}</div>
            </Link>
            <Link to={`/feed/batch?formula=${f.id}`} className="btn2 mt-2.5 h-11 no-underline">Prepare batch</Link>
          </div>
        ))}
        {res.data && list.length === 0 && (
          <div className="rounded-[14px] bg-white px-4 py-5 text-center text-sm text-ink-3">No formulas yet. {isOwner ? <Link to="/feed/formulas/new" className="font-semibold">Create your concentrate recipe</Link> : 'Ask the farm owner to add one.'}</div>
        )}
        <div className="text-xs leading-relaxed text-ink-3">A formula is a recipe for a home-made concentrate mix. Preparing a batch takes the ingredients out of stock and adds the mix.</div>
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
