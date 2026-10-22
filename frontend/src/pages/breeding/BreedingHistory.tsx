import { Link, useParams } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { useBreeding } from '../../components/breeding'
import { L, shortDate, useLoad } from '../../data/records'
import { inr0, STATUS_LABEL, STATUS_TONE, SUGGESTION_TONE, type HistoryEvent } from '../../data/breeding'
import { useStore } from '../../data/store'

const DOT: Record<HistoryEvent['kind'], string> = {
  CALVING: 'bg-brand', HEAT: 'bg-[#C2410C]', HEAT_MISSED: 'bg-line', SERVICE: 'bg-info-text', PD: 'bg-danger', DRY_OFF: 'bg-ink-3', EXPECTED_HEAT: 'bg-[#C2410C]',
}
const year = (d?: string | null) => (d ? d.slice(0, 4) : '')

function Tile({ k, v, s, tone = '' }: { k: string; v: string; s?: string; tone?: string }) {
  return <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">{k}</div><div className={`mt-0.5 text-xl font-bold ${tone}`}>{v}</div>{s && <div className="mt-0.5 text-xs text-ink-3">{s}</div>}</div>
}

export default function BreedingHistory() {
  const { id = '' } = useParams()
  const { br, isOwner } = useBreeding()
  const { animals } = useStore()
  const res = useLoad(() => br.history(id), [br, id])
  const settings = useLoad(() => br.settings(), [br])
  const h = res.data
  const s = h?.status
  const a = animals.find((x) => x.id === id)
  const sg = s?.suggestion
  const target = settings.data?.targetDaysOpen ?? 90
  const sell = settings.data?.sellDaysOpen ?? 150
  const open = s && (s.status === 'OPEN' || s.status === 'SERVED')

  return (
    <Screen>
      <TopBar title={s ? `${s.animal.tag}${s.animal.name ? ` ${s.animal.name}` : ''}` : a ? a.tag : 'Breeding history'} back={-1}
        right={<Link to={`/breeding/service/new?animal=${id}&back=/breeding/animals/${id}`} className="flex h-10 items-center rounded-[10px] bg-brand px-3 text-sm font-semibold text-white no-underline hover:text-white">+ Service</Link>} />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <LoadState loading={res.loading} error={res.error} onRetry={res.reload} empty={!h} />
        {h && s && sg && (
          <>
            <div className="-mt-1 text-[13px] text-ink-3">{[s.breed, s.lactationNumber ? `lactation ${s.lactationNumber}` : null, h.milkPerDay != null ? `${L(h.milkPerDay)} L/day` : null].filter(Boolean).join(' · ')}</div>
            <div className="flex flex-wrap items-center gap-2">
              <span className={`badge px-2.5 py-1 text-[13px] font-bold ${STATUS_TONE[s.status]}`}>{STATUS_LABEL[s.status]}{open && s.daysOpen != null ? ` · ${s.daysOpen} days` : ''}</span>
              {s.lastCalvingDate && <span className="text-[13px] text-ink-3">calved {shortDate(s.lastCalvingDate)} {year(s.lastCalvingDate)}</span>}
              {s.expectedDueDate && s.status !== 'OPEN' && <span className="text-[13px] text-ink-3">due {shortDate(s.expectedDueDate)}</span>}
            </div>

            <div className="grid grid-cols-2 gap-2.5">
              <Tile k="Days open" v={s.daysOpen != null ? String(s.daysOpen) : '—'} s={`target ≤ ${target} · sell over ${sell}`} tone={s.daysOpen != null && s.daysOpen > target ? 'text-danger' : ''} />
              <Tile k="Heats missed" v={s.heatsExpected ? `${s.heatsMissed} of ${s.heatsExpected}` : String(s.heatsMissed)} s={s.lastHeatDate ? `last heat ${shortDate(s.lastHeatDate)}` : 'no heat recorded'} tone={s.heatsMissed ? 'text-warn-ink' : ''} />
              <Tile k="Services failed" v={String(s.failedServices)} s={`${s.servicesThisLactation} service${s.servicesThisLactation === 1 ? '' : 's'} this lactation`} tone={s.failedServices ? 'text-danger' : ''} />
              {isOwner ? <Tile k="Breeding spend" v={inr0(s.spendThisLactation ?? 0)} s="this lactation" /> : <Tile k="Next heat" v={s.nextHeatExpected ? shortDate(s.nextHeatExpected) : '—'} />}
            </div>

            <div className={`rounded-[14px] p-3.5 ${SUGGESTION_TONE[sg.action]}`}>
              <b className="text-base">{sg.action === 'OK' ? 'On track' : `Suggestion: ${sg.title}`}</b>
              {sg.reasons.length > 0 && <ul className="m-0 mt-2 list-disc pl-5 text-sm leading-relaxed">{sg.reasons.map((r) => <li key={r}>{r}</li>)}</ul>}
              <div className="mt-3 grid grid-cols-2 gap-2">
                {sg.action === 'PD_DUE' ? <Link to={`/breeding/pregnancy-check?animal=${id}&back=/breeding/animals/${id}`} className="flex h-11 items-center justify-center rounded-[10px] bg-white text-sm font-semibold text-ink no-underline">Pregnancy check</Link>
                  : sg.action === 'CALVING_DUE' ? <Link to={`/breeding/calving?animal=${id}`} className="flex h-11 items-center justify-center rounded-[10px] bg-white text-sm font-semibold text-ink no-underline">Record calving</Link>
                  : <Link to={`/breeding/heat/new?animal=${id}`} className="flex h-11 items-center justify-center rounded-[10px] bg-white text-sm font-semibold text-ink no-underline">Log heat</Link>}
                {sg.action === 'CONSIDER_SELLING' && isOwner
                  ? <Link to={`/animals/${id}`} className="flex h-11 items-center justify-center rounded-[10px] bg-white text-sm font-semibold text-ink no-underline">Mark as sold</Link>
                  : <Link to={`/health/new?animal=${id}&back=/breeding/animals/${id}`} className="flex h-11 items-center justify-center rounded-[10px] bg-white text-sm font-semibold text-ink no-underline">Book vet</Link>}
              </div>
              <div className="mt-2 text-xs opacity-80">Rules: sell over {sell} days open or {settings.data?.sellFailedServices ?? 3}+ failed services.</div>
            </div>

            <div className="rounded-[14px] bg-white p-3.5">
              <div className="flex items-baseline justify-between"><b className="text-[15px]">This lactation</b><span className="text-[13px] text-ink-3">{h.current.calvedOn ? `since ${shortDate(h.current.calvedOn)}` : 'no calving yet'}</span></div>
              {h.current.events.length === 0 && <div className="mt-2 text-sm text-ink-3">No breeding events yet.</div>}
              <div className="relative mt-1.5 pl-[22px] before:absolute before:bottom-1.5 before:left-1.5 before:top-1.5 before:w-0.5 before:bg-track">
                {h.current.events.map((e, i) => {
                  const body = (
                    <>
                      <span className={`absolute -left-5 top-[13px] h-2.5 w-2.5 rounded-full ring-[3px] ring-white ${DOT[e.kind]}`} />
                      <div className="text-xs font-bold text-ink-3">{shortDate(e.date)}</div>
                      <div className="mt-0.5 text-sm"><b>{e.title}</b>{e.detail ? ` · ${e.detail}` : ''}{isOwner && e.cost ? ` · ${inr0(e.cost)}` : ''}</div>
                    </>
                  )
                  return e.serviceId
                    ? <Link key={i} to={`/breeding/service/${e.serviceId}?back=/breeding/animals/${id}`} className="relative block py-2 text-ink no-underline hover:text-ink">{body}</Link>
                    : <div key={i} className="relative py-2">{body}</div>
                })}
              </div>
            </div>

            {h.lactations.length > 0 && <div className="mt-1 text-xs font-bold uppercase tracking-[.06em] text-ink-3">Earlier lactations</div>}
            {h.lactations.map((l) => (
              <div key={l.number} className="rounded-[14px] bg-white p-3.5">
                <div className="flex items-center gap-2"><b className="flex-1 text-[15px]">Lactation {l.number}{l.calvedOn ? ` · ${year(l.calvedOn)}${l.nextCalvedOn ? `–${year(l.nextCalvedOn).slice(2)}` : ''}` : ''}</b>
                  <span className={`badge font-bold ${l.conceived ? 'bg-brand-soft text-brand' : 'bg-danger-soft text-danger-text'}`}>{l.conceived ? 'Conceived' : 'Not conceived'}</span></div>
                <div className="mt-1 text-[13px] text-ink-3">{l.calvedOn ? `Calved ${shortDate(l.calvedOn)} ${year(l.calvedOn)}` : 'Calving date unknown'}{l.nextCalvedOn ? ` · next calving ${shortDate(l.nextCalvedOn)} ${year(l.nextCalvedOn)}` : ''}</div>
                <div className="mt-2.5 grid grid-cols-4 gap-1.5 text-xs text-ink-3">
                  <span>Days open<b className="block text-[15px] text-ink">{l.daysOpen ?? '—'}</b></span>
                  <span>Services<b className="block text-[15px] text-ink">{l.services}</b></span>
                  <span>Interval<b className="block text-[15px] text-ink">{l.calvingIntervalDays != null ? `${l.calvingIntervalDays} d` : '—'}</b></span>
                  <span>Spend<b className="block text-[15px] text-ink">{isOwner ? inr0(l.spend ?? 0) : '—'}</b></span>
                </div>
              </div>
            ))}
            {h.totals.calvings > 0 && (
              <div className="grid grid-cols-4 gap-1.5 rounded-[14px] bg-[#F7F7F3] p-3.5 text-xs text-ink-3">
                <span>Avg days open<b className="block text-[15px] text-ink">{h.totals.avgDaysOpen ?? '—'}</b></span>
                <span>Services / conception<b className="block text-[15px] text-ink">{h.totals.servicesPerConception != null ? h.totals.servicesPerConception.toFixed(1) : '—'}</b></span>
                <span>Calvings<b className="block text-[15px] text-ink">{h.totals.calvings}</b></span>
                <span>Total spend<b className="block text-[15px] text-ink">{isOwner ? inr0(h.totals.totalSpend ?? 0) : '—'}</b></span>
              </div>
            )}
            <div className="grid grid-cols-2 gap-2">
              <Link to={`/animals/${id}`} className="btn2 h-11 text-sm">Animal page</Link>
              <Link to={`/animals/${id}/family`} className="btn2 h-11 text-sm">Family tree</Link>
            </div>
          </>
        )}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
