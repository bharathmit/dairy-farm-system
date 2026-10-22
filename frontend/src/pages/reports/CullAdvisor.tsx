import { Link, useNavigate } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { OwnerOnlyScreen } from '../../components/money'
import { Badge, useAlerts } from '../../components/alerts'
import { useStore } from '../../data/store'
import { VERDICT_LABEL, VERDICT_TONE, type CullRow, type Verdict } from '../../data/alerts'
import { rupees } from '../../data/money'
import { shortDate, useLoad } from '../../data/records'
import { errorMessage } from '../../lib/api'

const ORDER: Verdict[] = ['CULL', 'DRY_OFF', 'MONITOR', 'KEEP']
const scoreTone = (n: number) => (n < 40 ? 'text-danger-text' : n < 65 ? 'text-warn-ink' : 'text-brand')

/** Keep / monitor / dry-off / cull suggestions from 90-day scores. Advice only. */
export default function CullAdvisor() {
  const { al, isOwner } = useAlerts()
  const { notify } = useStore()
  const nav = useNavigate()
  const data = useLoad(isOwner ? () => al.cull() : null, [al, isOwner])
  if (!isOwner) return <OwnerOnlyScreen title="Cull advisor" />
  const d = data.data

  const keep = async (row: CullRow) => {
    try {
      const updated = await al.keep(row.animal.id)
      data.setData((cur) => (cur ? { ...cur, cows: cur.cows.map((c) => (c.animal.id === row.animal.id ? updated : c)) } : cur))
      notify(`${row.animal.tag} kept · not flagged for cull for 90 days`)
    } catch (e) { notify(errorMessage(e)) }
  }

  const card = (c: CullRow) => (
    <div key={c.animal.id} className="rounded-[14px] bg-white p-3.5">
      <div className="flex items-center gap-2">
        <b className="text-base">{c.animal.tag}</b><span className="flex-1 text-ink-2">{c.animal.name}</span>
        <Badge className={VERDICT_TONE[c.verdict]}>{VERDICT_LABEL[c.verdict]} · {Math.round(c.score)}</Badge>
      </div>
      <div className="mt-0.5 text-[13px] text-ink-3">
        {[c.animal.breed, c.animal.ageYears != null ? `${c.animal.ageYears} y` : null, c.animal.lactationNumber ? `lactation ${c.animal.lactationNumber}` : null, c.animal.daysInMilk != null ? `DIM ${c.animal.daysInMilk}` : null].filter(Boolean).join(' · ')}
      </div>
      <div className="mt-2.5 grid grid-cols-3 gap-2 text-center">
        {([['Production', c.production], ['Reproduction', c.reproduction], ['Health', c.health]] as const).map(([l, v]) => (
          <div key={l} className="rounded-[10px] bg-ground px-1 py-2"><div className="text-[11px] font-semibold text-ink-3">{l}</div><b className={scoreTone(v)}>{Math.round(v)}</b></div>
        ))}
      </div>
      {c.reasons.length > 0 && <ul className="m-0 mt-2.5 list-disc pl-5 text-sm text-ink-2">{c.reasons.map((r, i) => <li key={i}>{r}</li>)}</ul>}
      {(c.lifetimeNet != null || c.last90Net != null) && (
        <div className="mt-2.5 flex justify-between border-t border-line-faint pt-2 text-sm">
          <span>Lifetime P&amp;L <b className={(c.lifetimeNet ?? 0) < 0 ? 'text-danger' : 'text-brand'}>{rupees(c.lifetimeNet, true)}</b></span>
          <span>Last 90 days <b className={(c.last90Net ?? 0) < 0 ? 'text-danger' : 'text-brand'}>{rupees(c.last90Net, true)}</b></span>
        </div>
      )}
      {c.keepUntil && <div className="mt-1.5 text-[13px] text-ink-3">Kept until {shortDate(c.keepUntil)}</div>}
      <div className="mt-2.5 grid grid-cols-2 gap-2">
        {c.verdict === 'CULL' && <button type="button" className="btn h-11 text-sm" onClick={() => nav(`/animals/${c.animal.id}`)}>Mark as sold</button>}
        {c.verdict === 'CULL' && <button type="button" className="btn2 h-11 text-sm" onClick={() => keep(c)}>Keep anyway</button>}
        {c.verdict === 'DRY_OFF' && <button type="button" className="btn h-11 text-sm" onClick={() => nav(`/animals/${c.animal.id}/edit`)}>Mark as dry</button>}
        <Link to={`/animals/${c.animal.id}`} className={`btn2 h-11 text-sm ${c.verdict === 'CULL' ? 'col-span-2' : ''}`}>Open cow</Link>
        {c.verdict !== 'CULL' && c.verdict !== 'DRY_OFF' && <Link to={`/breeding/animals/${c.animal.id}`} className="btn2 h-11 text-sm">Breeding</Link>}
      </div>
    </div>
  )

  return (
    <Screen>
      <TopBar title="Cull advisor" back="/reports" />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <LoadState loading={data.loading} error={data.error} onRetry={data.reload} empty={!d} />
        {d && (
          <>
            <div className="grid grid-cols-4 gap-2 text-center">
              {([['Keep', d.counts.keep, 'text-brand'], ['Monitor', d.counts.monitor, 'text-warn-ink'], ['Dry-off', d.counts.dryOff, 'text-info-text'], ['Cull', d.counts.cull, 'text-danger-text']] as const).map(([l, n, tone]) => (
                <div key={l} className="rounded-xl bg-white px-1 py-2.5"><div className={`text-xl font-bold ${tone}`}>{n}</div><div className="text-xs font-semibold text-ink-3">{l}</div></div>
              ))}
            </div>
            <p className="m-0 text-[13px] text-ink-3">Scores 0–100 from the last 90 days. Advice only — you decide.</p>
            {ORDER.map((v) => {
              const rows = d.cows.filter((c) => c.verdict === v)
              if (!rows.length) return null
              if (v === 'KEEP') {
                const avg = Math.round(rows.reduce((s, c) => s + c.score, 0) / rows.length)
                return (
                  <details key={v} className="rounded-[14px] bg-white p-3.5">
                    <summary className="cursor-pointer font-semibold">Keep · {rows.length} <span className="font-normal text-ink-3">· performing well · avg score {avg}</span></summary>
                    <div className="mt-2 flex flex-col">
                      {rows.map((c) => <Link key={c.animal.id} to={`/animals/${c.animal.id}`} className="flex min-h-11 items-center gap-2 border-t border-line-faint text-ink no-underline first:border-t-0"><b>{c.animal.tag}</b><span className="flex-1">{c.animal.name}</span><b className="text-brand">{Math.round(c.score)}</b></Link>)}
                    </div>
                  </details>
                )
              }
              return (
                <div key={v}>
                  <div className="mb-2 mt-1 text-xs font-bold uppercase tracking-[.06em] text-ink-3">{VERDICT_LABEL[v]} · {rows.length}</div>
                  <div className="flex flex-col gap-2.5">{rows.map(card)}</div>
                </div>
              )
            })}
            {!d.cows.length && <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">No cows to score yet — the advisor needs milk and breeding records.</div>}
          </>
        )}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
