import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { OwnerOnlyScreen, PaymentChips, useMoney } from '../../components/money'
import { useStore } from '../../data/store'
import { decimal, shortDate, todayIso, toNum, useLoad } from '../../data/records'
import { rupees } from '../../data/money'
import type { PaymentMethod } from '../../data/feed'
import { errorMessage } from '../../lib/api'

export default function PayWages() {
  const { mo, isOwner } = useMoney()
  const { notify } = useStore()
  const nav = useNavigate()
  const [params] = useSearchParams()
  const workers = useLoad(isOwner ? () => mo.workers() : null, [mo, isOwner])
  const [memberId, setMemberId] = useState(params.get('member') ?? '')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [advance, setAdvance] = useState(false)
  const due = useLoad(isOwner && memberId && !advance ? () => mo.due(memberId, from || undefined, to || undefined) : null, [mo, memberId, from, to, advance, isOwner])
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState<PaymentMethod>('CASH')
  const [paidOn, setPaidOn] = useState(todayIso())
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const d = due.data
  useEffect(() => {
    if (!d) return
    if (!from) setFrom(d.from)
    if (!to) setTo(d.to)
    setAmount(String(Math.max(0, Math.round(d.amountDue))))
  }, [d]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (!memberId && workers.data?.workers.length === 1) setMemberId(String(workers.data.workers[0].member.id)) }, [workers.data, memberId])

  if (!isOwner) return <OwnerOnlyScreen title="Pay wages" back="/money/workers" />
  const w = workers.data?.workers.find((x) => String(x.member.id) === memberId)
  const amt = toNum(amount)
  const pick = (v: string) => { setMemberId(v); setFrom(''); setTo(''); setAmount('') }

  const save = async () => {
    if (!w) { notify('Choose the worker'); return }
    if (!amt || amt <= 0) { notify('Enter the amount paid'); return }
    setBusy(true)
    try {
      await mo.pay({ memberId: w.member.id, periodFrom: advance ? null : from, periodTo: advance ? null : to, amount: amt, paymentMethod: method, paidOn, advance, notes: notes.trim() || null })
      notify(`${rupees(amt)} ${advance ? 'advance' : 'wages'} paid to ${w.member.name}`)
      nav('/money/workers')
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title="Pay wages" close="/money/workers" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <LoadState loading={workers.loading} error={workers.error} onRetry={workers.reload} empty={!workers.data} />
        <div><label className="lab" htmlFor="pw-worker">Worker *</label>
          <select className="inp" id="pw-worker" value={memberId} onChange={(e) => pick(e.target.value)}>
            <option value="">Choose a worker</option>
            {workers.data?.workers.map((x) => <option key={x.member.id} value={x.member.id}>{x.member.name} · {x.wageType === 'DAILY' ? `daily ${rupees(x.wageRate)}` : `monthly ${rupees(x.wageRate)}`}</option>)}
          </select></div>
        <label className="flex min-h-11 items-center gap-2.5 text-[15px]"><input type="checkbox" checked={advance} onChange={(e) => setAdvance(e.target.checked)} className="h-5 w-5 accent-brand" />Advance (taken off the next wages)</label>
        {!advance && (
          <>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="lab" htmlFor="pw-from">Period from</label><input className="inp" id="pw-from" type="date" value={from} max={to || undefined} onChange={(e) => e.target.value && setFrom(e.target.value)} /></div>
              <div><label className="lab" htmlFor="pw-to">Period to</label><input className="inp" id="pw-to" type="date" value={to} min={from || undefined} max={todayIso()} onChange={(e) => e.target.value && setTo(e.target.value)} /></div>
            </div>
            {memberId && <LoadState loading={due.loading} error={due.error} onRetry={due.reload} empty={!d} />}
            {d && (
              <div className="rounded-[14px] bg-white p-3.5">
                <div className="flex min-h-[38px] items-center justify-between text-[15px]"><span>Days present <span className="text-xs text-ink-3">from attendance</span></span><b>{d.presentDays}</b></div>
                <div className="flex min-h-[38px] items-center justify-between border-t border-line-faint text-[15px]"><span>Half days</span><b>{d.halfDays}</b></div>
                {d.absentDays > 0 && <div className="flex min-h-[38px] items-center justify-between border-t border-line-faint text-[15px]"><span>Absent</span><b>{d.absentDays}</b></div>}
                <div className="flex min-h-[38px] items-center justify-between border-t border-line-faint text-[15px]"><span>Rate</span><b>{w?.wageType === 'MONTHLY' ? `${rupees(w.wageRate)} / month` : `${rupees(d.rate)} / day`}</b></div>
                <div className="flex min-h-[38px] items-center justify-between border-t border-line-faint text-[15px]"><span>Gross</span><b>{rupees(d.gross)}</b></div>
                <div className="flex min-h-[38px] items-center justify-between border-t border-line-faint text-[15px]"><span>Advance taken</span><span className={d.advances ? 'font-semibold text-danger' : 'text-ink-3'}>{d.advances ? `−${rupees(d.advances)}` : '₹0'}</span></div>
                <div className="flex min-h-12 items-center justify-between border-t-2 border-ink text-[17px]"><b>Amount due</b><b>{rupees(d.amountDue)}</b></div>
                {d.explain && <div className="mt-1 text-xs text-ink-3">{d.explain}</div>}
              </div>
            )}
          </>
        )}
        <div><label className="lab" htmlFor="pw-amt">Amount paid (₹)</label><input className="inp text-xl font-bold" id="pw-amt" inputMode="decimal" value={amount} onChange={(e) => setAmount(decimal(e.target.value))} /></div>
        <div><span className="lab">Payment method</span><PaymentChips value={method} onChange={setMethod} /></div>
        <div><label className="lab" htmlFor="pw-on">Paid on</label><input className="inp" id="pw-on" type="date" max={todayIso()} value={paidOn} onChange={(e) => e.target.value && setPaidOn(e.target.value)} /></div>
        <div><label className="lab" htmlFor="pw-notes">Notes</label><input className="inp" id="pw-notes" placeholder="Optional" value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
        {w && amt ? (
          <div className="rounded-xl bg-info-soft px-3.5 py-3 text-sm text-info-text">
            Saving adds a <b>Labour</b> expense of {rupees(amt)} for {w.member.name}{!advance && from && to ? `, ${shortDate(from)} – ${shortDate(to)}` : ' as an advance'}, status Paid.
          </div>
        ) : null}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <button type="button" className="btn" disabled={busy || !w} onClick={save}>{busy ? 'Saving…' : `Mark paid${amt ? ` · ${rupees(amt)}` : ''}`}</button>
      </div>
    </Screen>
  )
}
