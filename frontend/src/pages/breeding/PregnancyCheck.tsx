import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Screen, Segmented, TopBar } from '../../components/ui'
import { ContactSelect, LoadState } from '../../components/records'
import { useBreeding } from '../../components/breeding'
import { useStore } from '../../data/store'
import { addDays, decimal, shortDate, todayIso, toNum, useLoad } from '../../data/records'
import { methodLabel, PD_METHODS, refLabel, type PdMethod } from '../../data/breeding'
import { errorMessage } from '../../lib/api'

const daysBetween = (a: string, b: string) => Math.round((new Date(b).getTime() - new Date(a).getTime()) / 86400000)

export default function PregnancyCheck() {
  const [params] = useSearchParams()
  const { br, isOwner } = useBreeding()
  const { notify } = useStore()
  const nav = useNavigate()
  const ov = useLoad(() => br.overview(), [br])
  const settings = useLoad(() => br.settings(), [br])
  const [serviceId, setServiceId] = useState(params.get('service') ?? '')
  const [date, setDate] = useState(todayIso())
  const [method, setMethod] = useState<PdMethod>('ULTRASOUND')
  const [result, setResult] = useState<'PREGNANT' | 'NOT_PREGNANT'>('PREGNANT')
  const [by, setBy] = useState('')
  const [cost, setCost] = useState('')
  const [due, setDue] = useState('')
  const [busy, setBusy] = useState(false)
  const back = params.get('back') || '/breeding'

  // Cows waiting for a check (served), plus pregnant ones for a re-check.
  const choices = useMemo(() => {
    const g = ov.data?.groups
    return g ? [...g.served, ...g.pregnant, ...g.dry].filter((s) => s.currentServiceId) : []
  }, [ov.data])
  useEffect(() => {
    const a = params.get('animal')
    if (!serviceId && a && choices.length) {
      const s = choices.find((c) => String(c.animal.id) === a)
      if (s?.currentServiceId) setServiceId(String(s.currentServiceId))
    }
  }, [choices, params, serviceId])
  const svc = useLoad(serviceId ? () => br.service(serviceId) : null, [br, serviceId])
  const r = svc.data
  useEffect(() => { if (r?.expectedDueDate) setDue(r.expectedDueDate) }, [r])
  const dryOff = due ? addDays(due, -(settings.data?.dryOffDaysBeforeDue ?? 60)) : ''
  // A check needs at least pdMinDays after service (the API enforces it too).
  const pdMin = settings.data?.pdMinDays ?? 28
  const earliest = r?.serviceDate ? addDays(r.serviceDate, pdMin) : ''
  const tooEarly = Boolean(earliest) && earliest > todayIso()
  useEffect(() => { if (earliest && date < earliest && !tooEarly) setDate(todayIso()) }, [earliest, date, tooEarly])

  const save = async () => {
    if (!r) { notify('Choose the cow'); return }
    if (earliest && date < earliest) { notify(`A pregnancy check needs at least ${pdMin} days after service — earliest ${shortDate(earliest)}.`); return }
    setBusy(true)
    try {
      await br.pregnancyCheck(r.id, {
        date, method, result, checkedByContactId: by ? Number(by) : undefined, cost: isOwner ? toNum(cost) ?? null : null,
        expectedDueDate: result === 'PREGNANT' && due ? due : undefined,
      })
      notify(result === 'PREGNANT' ? `${r.animal.tag} confirmed pregnant` : `${r.animal.tag} back to open · added to heat watch`)
      nav(back)
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title="Pregnancy check" close={back} />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <div>
          <label className="lab" htmlFor="pd-cow">Cow *</label>
          <select className="inp" id="pd-cow" value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
            <option value="">{ov.data ? (choices.length ? 'Choose a served cow' : 'No served cows waiting') : 'Loading…'}</option>
            {choices.map((s) => <option key={s.currentServiceId} value={s.currentServiceId!}>{refLabel(s.animal)} · served {shortDate(s.lastServiceDate)}</option>)}
            {r && !choices.some((c) => c.currentServiceId === r.id) && <option value={r.id}>{refLabel(r.animal)} · served {shortDate(r.serviceDate)}</option>}
          </select>
        </div>
        <LoadState loading={ov.loading || svc.loading} error={ov.error ?? svc.error} onRetry={() => { void ov.reload(); void svc.reload() }} empty={!ov.data || (Boolean(serviceId) && !r)} />
        {r && r.serviceDate && (
          <div className="rounded-xl bg-white px-3.5 py-3 text-sm">Served <b>{shortDate(r.serviceDate)}</b> by {methodLabel(r.method)}{r.semen ? ` · ${r.semen.bullName}` : r.sire ? ` · ${r.sire.tag}` : r.sireTag ? ` · ${r.sireTag}` : ''} · <b>{daysBetween(r.serviceDate, date)} days</b> ago</div>
        )}
        <div><label className="lab" htmlFor="pd-dt">Check date *</label><input className="inp" id="pd-dt" type="date" min={earliest || undefined} max={todayIso()} value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
          {earliest && <div className={`hint ${tooEarly || date < earliest ? 'font-semibold text-danger-text' : ''}`}>Earliest check: {shortDate(earliest)} ({pdMin} days after service){tooEarly ? ' — too early to check today.' : ''}</div>}</div>
        <div><div className="lab">Method</div><Segmented label="Method" options={PD_METHODS} value={method} onChange={setMethod} /></div>
        <div><div className="lab">Result *</div><Segmented label="Result" options={[{ value: 'PREGNANT', label: 'Confirmed' }, { value: 'NOT_PREGNANT', label: 'Not confirmed' }]} value={result} onChange={setResult} /></div>
        {result === 'PREGNANT' ? (
          <div className="flex flex-col gap-3 rounded-[14px] bg-white p-3.5">
            <div className="text-[15px] font-bold">Updates on save</div>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="lab" htmlFor="pd-due">Expected due date</label><input className="inp" id="pd-due" type="date" value={due} onChange={(e) => setDue(e.target.value)} /></div>
              <div><span className="lab">Planned dry-off</span><div className="flex h-12 items-center text-base font-semibold">{dryOff ? shortDate(dryOff) : '—'}</div></div>
            </div>
            <div className="text-xs text-ink-3">Dry-off = {settings.data?.dryOffDaysBeforeDue ?? 60} days before due. Status changes to <b>Pregnant</b>.</div>
          </div>
        ) : (
          <div className="rounded-xl bg-warn-soft px-3.5 py-3 text-sm text-warn-text">She goes back to <b>Open</b> and is added to Heat watch{r?.serviceDate ? ` for about ${shortDate(addDays(r.serviceDate, 2 * (settings.data?.heatCycleDays ?? 21)))}` : ''}.</div>
        )}
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="pd-by">Checked by</label><ContactSelect id="pd-by" role="VET" value={by} onChange={setBy} newLabel="New vet" /></div>
          {isOwner && <div><label className="lab" htmlFor="pd-cost">Cost (₹)</label><input className="inp" id="pd-cost" inputMode="decimal" value={cost} onChange={(e) => setCost(decimal(e.target.value))} /></div>}
        </div>
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <button type="button" className="btn" disabled={busy || !r || tooEarly} onClick={save}>{busy ? 'Saving…' : 'Save check'}</button>
      </div>
    </Screen>
  )
}
