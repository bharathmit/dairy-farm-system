import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Screen, TopBar } from '../../components/ui'
import { ContactSelect, DateNav, LoadState, useRecords } from '../../components/records'
import { inr, useStore } from '../../data/store'
import { addDays, decimal, L, numText, shortDate, todayIso, toNum, useLoad, type Product, type Sale, type Session } from '../../data/records'
import { errorMessage } from '../../lib/api'

type SaleSession = Session | 'DAY'
const PRODUCTS: { value: Product; label: string }[] = [{ value: 'MILK', label: 'Milk' }, { value: 'MANURE', label: 'Manure' }, { value: 'OTHER', label: 'Other' }]
const SESSIONS: { value: SaleSession; label: string }[] = [{ value: 'AM', label: 'Morning' }, { value: 'PM', label: 'Evening' }, { value: 'DAY', label: 'Whole day' }]

export default function MilkSale() {
  const { rec, isOwner } = useRecords()
  const { settings, notify } = useStore()
  const nav = useNavigate()
  const [params, setParams] = useSearchParams()
  const date = params.get('date') || todayIso()
  const [product, setProduct] = useState<Product>('MILK')
  const [session, setSession] = useState<SaleSession>(new Date().getHours() >= 12 ? 'PM' : 'AM')

  const day = useLoad(() => rec.day(date), [rec, date])
  const sales = useLoad(() => rec.sales(addDays(date, -6), date), [rec, date])
  const existing = sales.data?.find((s) => s.date === date && s.session === session && s.product === product)

  const [qty, setQty] = useState('')
  const [sold, setSold] = useState('')
  const [buyer, setBuyer] = useState('')
  const [rate, setRate] = useState('')
  const [fat, setFat] = useState('')
  const [snf, setSnf] = useState('')
  const [paid, setPaid] = useState(false)
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const s = existing
    setQty(numText(s?.quantity)); setSold(numText(s?.soldQty)); setBuyer(s?.buyer ? String(s.buyer.id) : '')
    setRate(s?.rate != null ? String(s.rate) : product === 'MILK' && settings.milkRate ? settings.milkRate.toFixed(2) : '')
    setFat(numText(s?.fatPct)); setSnf(numText(s?.snfPct)); setPaid(Boolean(s?.paid)); setNotes(s?.notes ?? '')
  }, [existing, product, settings.milkRate])

  const d = day.data
  const cowLitres = d ? (session === 'AM' ? d.am.litres : session === 'PM' ? d.pm.litres : d.totalLitres) ?? 0 : 0
  const withheld = d ? (session === 'AM' ? d.am.withheldLitres ?? 0 : session === 'PM' ? d.pm.withheldLitres ?? 0 : (d.am.withheldLitres ?? 0) + (d.pm.withheldLitres ?? 0)) : 0
  const total = toNum(qty) ?? (product === 'MILK' ? cowLitres : 0)
  const soldN = toNum(sold) ?? 0
  const rateN = toNum(rate) ?? 0
  const other = Math.max(0, total - soldN)
  const income = Math.round(soldN * rateN)
  const unit = product === 'MILK' ? 'L' : 'kg'
  const sessionName = SESSIONS.find((s) => s.value === session)!.label.toLowerCase()

  const save = async () => {
    if (!(soldN > 0) && toNum(qty) === undefined) { notify('Enter the total or the quantity sold'); return }
    if (product === 'MILK' && soldN > total - withheld + 0.001) { notify(`Only ${L(total - withheld)} L can be sold; ${L(withheld)} L is from cows in withdrawal`); return }
    setBusy(true)
    try {
      await rec.saveSale({
        date, session, product, quantity: toNum(qty) ?? null, soldQty: toNum(sold) ?? null,
        buyerContactId: buyer ? Number(buyer) : null, rate: isOwner ? toNum(rate) ?? null : undefined,
        fatPct: toNum(fat) ?? null, snfPct: toNum(snf) ?? null, paid, notes: notes.trim() || null,
      })
      notify('Sale saved')
      nav('/milk')
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  const remove = async (s: Sale) => {
    try { await rec.deleteSale(s.id); notify('Sale deleted'); void sales.reload() } catch (e) { notify(errorMessage(e)) }
  }

  return (
    <Screen>
      <TopBar title="Farm total & sale" close="/milk" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <div className="flex gap-2">{PRODUCTS.map((p) => <button key={p.value} type="button" aria-pressed={product === p.value} onClick={() => setProduct(p.value)} className={`chip ${product === p.value ? 'chip-on' : ''}`}>{p.label}</button>)}</div>
        <DateNav id="sl-date" value={date} onChange={(v) => { const p = new URLSearchParams(params); p.set('date', v); setParams(p, { replace: true }) }} />
        <div><label className="lab" htmlFor="sl-sess">Session</label>
          <select className="inp" id="sl-sess" value={session} onChange={(e) => setSession(e.target.value as SaleSession)}>{SESSIONS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}</select></div>
        <LoadState loading={day.loading || sales.loading} error={day.error ?? sales.error} onRetry={() => { void day.reload(); void sales.reload() }} empty={!d} />
        {product === 'MILK' && d && (
          <div className="rounded-xl bg-brand-soft px-3.5 py-3 text-sm leading-snug text-[#1C3A28]">
            Cows logged {session === 'DAY' ? 'today' : `this ${sessionName}`}: <b>{L(cowLitres)} L</b>{withheld > 0 && <> ({L(withheld)} L in withdrawal — can't be sold)</>}. Leave total blank to use that, or type a total if you didn't log each cow.
          </div>
        )}
        {existing && <div className="text-[13px] text-ink-3">Editing the {sessionName} {product.toLowerCase()} entry saved for this date.</div>}
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="sl-tot">Total ({unit})</label><input className="inp" id="sl-tot" inputMode="decimal" placeholder={product === 'MILK' ? L(cowLitres) : ''} value={qty} onChange={(e) => setQty(decimal(e.target.value))} /></div>
          <div><label className="lab" htmlFor="sl-sold">Sold ({unit})</label><input className="inp" id="sl-sold" inputMode="decimal" value={sold} onChange={(e) => setSold(decimal(e.target.value))} /></div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="sl-buyer">Sold to</label>
            <ContactSelect id="sl-buyer" role="BUYER" value={buyer} onChange={setBuyer} newLabel="New buyer"
              onPicked={(c) => { if (c.milkRate != null && product === 'MILK') setRate(c.milkRate.toFixed(2)) }} /></div>
          {isOwner && <div><label className="lab" htmlFor="sl-rate">Rate ₹/{unit}</label><input className="inp" id="sl-rate" inputMode="decimal" value={rate} onChange={(e) => setRate(decimal(e.target.value))} /><div className="hint">Buyer's usual rate</div></div>}
        </div>
        {product === 'MILK' && (
          <div className="grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="sl-fat">Fat % (slip)</label><input className="inp" id="sl-fat" inputMode="decimal" placeholder="4.0" value={fat} onChange={(e) => setFat(decimal(e.target.value))} /></div>
            <div><label className="lab" htmlFor="sl-snf">SNF % (slip)</label><input className="inp" id="sl-snf" inputMode="decimal" placeholder="8.5" value={snf} onChange={(e) => setSnf(decimal(e.target.value))} /></div>
          </div>
        )}
        <div className="rounded-xl bg-white px-3.5 py-3">
          <div className="flex justify-between py-1 text-sm text-ink-2"><span>Sold</span><span>{L(soldN)} {unit}{isOwner ? ` × ${inr(rateN)}` : ''}</span></div>
          <div className="flex justify-between py-1 text-sm text-ink-2"><span>{product === 'MILK' ? 'Calves / home use / discarded' : 'Kept / used'}</span><span>{L(other)} {unit}</span></div>
          {isOwner && <div className="mt-1 flex justify-between border-t border-line-faint pt-2 text-base font-bold"><span>Income</span><span>{inr(income)}</span></div>}
          <label className="mt-2 flex min-h-10 items-center gap-2.5 text-sm"><input type="checkbox" checked={paid} onChange={(e) => setPaid(e.target.checked)} className="h-5 w-5 accent-brand" /> Payment received (else marked pending)</label>
        </div>
        <div><label className="lab" htmlFor="sl-notes">Notes</label><input className="inp" id="sl-notes" placeholder="Optional" value={notes} onChange={(e) => setNotes(e.target.value)} /></div>

        {sales.data && sales.data.length > 0 && (
          <div>
            <div className="mb-2 mt-1 text-[15px] font-bold">Last 7 days</div>
            <div className="overflow-hidden rounded-[14px] bg-white">
              {sales.data.map((s) => (
                <div key={s.id} className="flex items-center gap-2 border-t border-line-faint px-3.5 py-2.5 text-sm first:border-t-0">
                  <span className="flex-1"><b>{shortDate(s.date)}</b> · {SESSIONS.find((x) => x.value === s.session)?.label} · {s.product.toLowerCase()}<br /><span className="text-ink-3">{L(s.soldQty)} sold{s.buyer ? ` to ${s.buyer.name}` : ''}{isOwner && s.incomeAmount != null ? ` · ${inr(s.incomeAmount)}` : ''}{s.paid ? '' : ' · pending'}</span></span>
                  {isOwner && <button type="button" onClick={() => remove(s)} className="h-10 border-0 bg-transparent px-2 text-sm font-semibold text-danger">Delete</button>}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-ground px-4 pb-6 pt-3.5">
        <button type="button" className="btn" disabled={busy} onClick={save}>{busy ? 'Saving…' : isOwner ? 'Save & add to Finance' : 'Save sale'}</button>
      </div>
    </Screen>
  )
}
