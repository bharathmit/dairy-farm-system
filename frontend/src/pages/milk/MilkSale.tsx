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
const isProduct = (v: string | null): v is Product => PRODUCTS.some((p) => p.value === v)
const isSession = (v: string | null): v is SaleSession => SESSIONS.some((s) => s.value === v)

export default function MilkSale() {
  const { rec, isOwner } = useRecords()
  const { settings, notify } = useStore()
  const nav = useNavigate()
  const [params, setParams] = useSearchParams()
  // Product, date and session live in the URL so links (list rows, Finance) open the right record.
  const date = params.get('date') || todayIso()
  const product: Product = isProduct(params.get('product')) ? params.get('product') as Product : 'MILK'
  const urlSession = params.get('session')
  const session: SaleSession = isSession(urlSession) ? urlSession
    : product === 'MILK' ? (new Date().getHours() >= 12 ? 'PM' : 'AM') : 'DAY'
  const update = (next: Record<string, string | null>) => {
    const p = new URLSearchParams(params)
    for (const [k, v] of Object.entries(next)) { if (v) p.set(k, v); else p.delete(k) }
    setParams(p, { replace: true })
  }
  // Manure and other produce are day entries: no session.
  const setProduct = (v: Product) => update({ product: v === 'MILK' ? null : v, session: null, sale: null })
  const setSession = (v: SaleSession) => update({ session: v })

  const day = useLoad(() => rec.day(date), [rec, date])
  const sales = useLoad(() => rec.sales(addDays(date, -6), date), [rec, date])
  const existing = sales.data?.find((s) => s.date === date && s.session === session && s.product === product)

  // Opened from Finance with ?sale=id: pick that record's product and session.
  const saleId = params.get('sale')
  useEffect(() => {
    if (!saleId || !sales.data) return
    const s = sales.data.find((x) => String(x.id) === saleId)
    update(s ? { sale: null, date: s.date, product: s.product === 'MILK' ? null : s.product, session: s.session } : { sale: null })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [saleId, sales.data])

  const open = (s: Sale) => {
    update({ date: s.date, product: s.product === 'MILK' ? null : s.product, session: s.session, sale: null })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  // Whole day only when both sessions are logged and neither has its own sale (or a whole-day sale exists).
  const d0 = day.data
  const daySales = (sales.data ?? []).filter((s) => s.date === date && s.product === 'MILK')
  const showWholeDay = daySales.some((s) => s.session === 'DAY')
    || (Boolean(d0 && (d0.am.cows ?? 0) > 0 && (d0.pm.cows ?? 0) > 0) && !daySales.some((s) => s.session !== 'DAY'))
  const sessionOptions = SESSIONS.filter((s) => s.value !== 'DAY' || showWholeDay)

  const [qty, setQty] = useState('')
  const [sold, setSold] = useState('')
  const [buyer, setBuyer] = useState('')
  const [rate, setRate] = useState('')
  const [fat, setFat] = useState('')
  const [snf, setSnf] = useState('')
  const [paid, setPaid] = useState(false)
  const [notes, setNotes] = useState('')
  const [discount, setDiscount] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const s = existing
    setQty(numText(s?.quantity)); setSold(numText(s?.soldQty)); setBuyer(s?.buyer ? String(s.buyer.id) : '')
    setRate(s?.rate != null ? String(s.rate) : product === 'MILK' && settings.milkRate ? settings.milkRate.toFixed(2) : '')
    setFat(numText(s?.fatPct)); setSnf(numText(s?.snfPct)); setPaid(Boolean(s?.paid)); setNotes(s?.notes ?? ''); setDiscount(numText(s?.discount))
  }, [existing, product, settings.milkRate])

  const d = day.data
  const cowLitres = d ? (session === 'AM' ? d.am.litres : session === 'PM' ? d.pm.litres : d.totalLitres) ?? 0 : 0
  const withheld = d ? (session === 'AM' ? d.am.withheldLitres ?? 0 : session === 'PM' ? d.pm.withheldLitres ?? 0 : (d.am.withheldLitres ?? 0) + (d.pm.withheldLitres ?? 0)) : 0
  const total = toNum(qty) ?? (product === 'MILK' ? cowLitres : 0)
  const soldN = toNum(sold) ?? 0
  const rateN = toNum(rate) ?? 0
  const other = Math.max(0, total - soldN)
  // Other produce: number of units × unit price − discount.
  const isOther = product === 'OTHER'
  const discountN = isOther ? toNum(discount) ?? 0 : 0
  const gross = soldN * rateN
  const income = Math.round(gross - discountN)
  const unit = product === 'MILK' ? 'L' : product === 'MANURE' ? 'kg' : 'units'
  const sessionName = SESSIONS.find((s) => s.value === session)!.label.toLowerCase()

  const save = async () => {
    if (isOther && !(soldN > 0)) { notify('Enter the number of units'); return }
    if (isOther && discountN > gross + 0.001) { notify('Discount can\'t be more than units × unit price'); return }
    if (!(soldN > 0) && toNum(qty) === undefined) { notify('Enter the total or the quantity sold'); return }
    if (product === 'MILK' && soldN > total - withheld + 0.001) { notify(`Only ${L(total - withheld)} L can be sold; ${L(withheld)} L is from cows in withdrawal`); return }
    setBusy(true)
    try {
      await rec.saveSale({
        date, session, product, quantity: isOther ? null : toNum(qty) ?? null, soldQty: toNum(sold) ?? null,
        discount: isOther && isOwner ? toNum(discount) ?? null : undefined,
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
        <DateNav id="sl-date" value={date} onChange={(v) => update({ date: v, session: product === 'MILK' && session === 'DAY' ? null : urlSession, sale: null })} />
        {product === 'MILK' && (
          <div><label className="lab" htmlFor="sl-sess">Session</label>
            <select className="inp" id="sl-sess" value={session} onChange={(e) => setSession(e.target.value as SaleSession)}>{(sessionOptions.some((s) => s.value === session) ? sessionOptions : [...sessionOptions, SESSIONS.find((s) => s.value === session)!]).map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}</select>
            {!showWholeDay && <div className="hint">Whole day shows once both sessions are logged and neither is sold on its own.</div>}</div>
        )}
        <LoadState loading={day.loading || sales.loading} error={day.error ?? sales.error} onRetry={() => { void day.reload(); void sales.reload() }} empty={!d} />
        {product === 'MILK' && d && (
          <div className="rounded-xl bg-brand-soft px-3.5 py-3 text-sm leading-snug text-[#1C3A28]">
            Cows logged {session === 'DAY' ? 'today' : `this ${sessionName}`}: <b>{L(cowLitres)} L</b>{withheld > 0 && <> ({L(withheld)} L in withdrawal — can't be sold)</>}. Leave total blank to use that, or type a total if you didn't log each cow.
          </div>
        )}
        {existing && <div className="text-[13px] text-ink-3">Editing the {product === 'MILK' || session !== 'DAY' ? `${sessionName} ` : ''}{product.toLowerCase()} entry saved for {shortDate(date)}.</div>}
        {isOther ? (
          <>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="lab" htmlFor="sl-sold">No. of units</label><input className="inp" id="sl-sold" inputMode="decimal" value={sold} onChange={(e) => setSold(decimal(e.target.value))} /></div>
              {isOwner && <div><label className="lab" htmlFor="sl-rate">Unit price ₹</label><input className="inp" id="sl-rate" inputMode="decimal" value={rate} onChange={(e) => setRate(decimal(e.target.value))} /></div>}
            </div>
            <div className="grid grid-cols-2 gap-3">
              {isOwner && <div><label className="lab" htmlFor="sl-disc">Discount ₹</label><input className="inp" id="sl-disc" inputMode="decimal" placeholder="0" value={discount} onChange={(e) => setDiscount(decimal(e.target.value))} /></div>}
              <div><label className="lab" htmlFor="sl-buyer">Sold to</label>
                <ContactSelect id="sl-buyer" role="BUYER" value={buyer} onChange={setBuyer} newLabel="New buyer" /></div>
            </div>
          </>
        ) : (<>
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
        </>)}
        {product === 'MILK' && (
          <div className="grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="sl-fat">Fat % (slip)</label><input className="inp" id="sl-fat" inputMode="decimal" placeholder="4.0" value={fat} onChange={(e) => setFat(decimal(e.target.value))} /></div>
            <div><label className="lab" htmlFor="sl-snf">SNF % (slip)</label><input className="inp" id="sl-snf" inputMode="decimal" placeholder="8.5" value={snf} onChange={(e) => setSnf(decimal(e.target.value))} /></div>
          </div>
        )}
        <div className="rounded-xl bg-white px-3.5 py-3">
          {isOther ? (
            <>
              <div className="flex justify-between py-1 text-sm text-ink-2"><span>Units sold</span><span>{L(soldN, Number.isInteger(soldN) ? 0 : 1)}{isOwner ? ` × ${inr(rateN)}` : ''}</span></div>
              {isOwner && <div className="flex justify-between py-1 text-sm text-ink-2"><span>Gross amount</span><span>{inr(Math.round(gross))}</span></div>}
              {isOwner && <div className="flex justify-between py-1 text-sm text-ink-2"><span>Discount</span><span>− {inr(Math.round(discountN))}</span></div>}
              {isOwner && <div className="mt-1 flex justify-between border-t border-line-faint pt-2 text-base font-bold"><span>Net income</span><span>{inr(income)}</span></div>}
            </>
          ) : (
            <>
              <div className="flex justify-between py-1 text-sm text-ink-2"><span>Sold</span><span>{L(soldN)} {unit}{isOwner ? ` × ${inr(rateN)}` : ''}</span></div>
              <div className="flex justify-between py-1 text-sm text-ink-2"><span>{product === 'MILK' ? 'Calves / home use / discarded' : 'Kept / used'}</span><span>{L(other)} {unit}</span></div>
              {isOwner && <div className="mt-1 flex justify-between border-t border-line-faint pt-2 text-base font-bold"><span>Income</span><span>{inr(income)}</span></div>}
            </>
          )}
          <label className="mt-2 flex min-h-10 items-center gap-2.5 text-sm"><input type="checkbox" checked={paid} onChange={(e) => setPaid(e.target.checked)} className="h-5 w-5 accent-brand" /> Payment received (else marked pending)</label>
        </div>
        <div><label className="lab" htmlFor="sl-notes">Notes</label><input className="inp" id="sl-notes" placeholder="Optional" value={notes} onChange={(e) => setNotes(e.target.value)} /></div>

        {sales.data && sales.data.length > 0 && (
          <div>
            <div className="mb-2 mt-1 text-[15px] font-bold">Last 7 days</div>
            <div className="overflow-hidden rounded-[14px] bg-white">
              {sales.data.map((s) => (
                <div key={s.id} className={`flex items-center gap-2 border-t border-line-faint px-3.5 py-2.5 text-sm first:border-t-0 ${existing?.id === s.id ? 'bg-brand-soft' : ''}`}>
                  <button type="button" onClick={() => open(s)} aria-label={`Edit ${s.product.toLowerCase()} sale of ${shortDate(s.date)}`} className="flex-1 border-0 bg-transparent p-0 text-left text-sm text-ink"><b>{shortDate(s.date)}</b>{s.product === 'MILK' || s.session !== 'DAY' ? ` · ${SESSIONS.find((x) => x.value === s.session)?.label}` : ''} · {s.product.toLowerCase()}<br /><span className="text-ink-3">{s.product === 'OTHER' ? `${s.soldQty ?? 0} units` : L(s.soldQty)} sold{s.buyer ? ` to ${s.buyer.name}` : ''}{isOwner && s.incomeAmount != null ? ` · ${inr(s.incomeAmount)}` : ''}{s.paid ? '' : ' · pending'}</span></button>
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
