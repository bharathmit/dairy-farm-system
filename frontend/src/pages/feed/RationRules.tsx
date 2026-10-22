import { Link } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { useFeed } from '../../components/feed'
import { shortDate, useLoad } from '../../data/records'
import { ruleLabel } from '../../data/feed'

export default function RationRules() {
  const { fd, isOwner } = useFeed()
  const res = useLoad(() => fd.rules(), [fd])
  return (
    <Screen>
      <TopBar title="Ration rules" back="/feed" />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <div className="text-sm text-ink-3">One rule per animal category. {isOwner ? 'Changes take effect from the date you choose; older days keep the rule that was in force.' : 'Only farm owners can change them.'}</div>
        <LoadState loading={res.loading} error={res.error} onRetry={res.reload} empty={!res.data} />
        {res.data && (
          <div className="overflow-hidden rounded-[14px] bg-white">
            {res.data.map((r, i) => {
              const p = r.current.params
              return (
                <Link key={r.category} to={`/feed/ration-rules/${r.category}`} className={`flex items-center gap-2 px-3.5 py-3 text-ink no-underline hover:text-ink ${i ? 'border-t border-line-faint' : ''}`}>
                  <span className="min-w-0 flex-1">
                    <b>{ruleLabel(r.category)}</b>{r.upcoming.length > 0 && <span className="badge ml-2 bg-info-soft text-info-text">change from {shortDate(r.upcoming[0].effectiveFrom)}</span>}
                    <div className="text-[13px] text-ink-3">BW × {p.bwPct} % · conc {p.concBaseKg} kg{p.concPerLitreKg ? ` + ${p.concPerLitreKg} kg/L` : ''}, max {p.maxConcKg} kg · green {p.greenSharePct} % of forage</div>
                    <div className="text-[13px] text-ink-3">{p.green.length + p.dry.length ? `${p.green.length} green, ${p.dry.length} dry items` : 'No fodder items picked yet'}{r.current.effectiveFrom ? ` · since ${shortDate(r.current.effectiveFrom)}` : ' · default'}</div>
                  </span>
                  <span className="text-ink-3">›</span>
                </Link>
              )
            })}
          </div>
        )}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
