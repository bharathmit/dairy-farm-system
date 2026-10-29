import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Dialog, Screen, Segmented, TopBar } from '../../components/ui'
import { AnimalSelect, LoadState } from '../../components/records'
import { AmountInput, ChoiceChips, OwnerOnlyScreen, PaymentChips, useMoney } from '../../components/money'
import { useStore } from '../../data/store'
import { addDays, todayIso, toNum, useLoad } from '../../data/records'
import {
  EXPENSE_CATEGORIES, finCategoryLabel, INCOME_CATEGORIES, rupees, sourceLink,
  type AllocKind, type Direction, type EntryStatus, type FinCategory,
} from '../../data/money'
import type { PaymentMethod } from '../../data/feed'
import { errorMessage } from '../../lib/api'

export default function TransactionForm() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const { mo, isOwner } = useMoney()
  const { groups, notify } = useStore()
  const nav = useNavigate()
  const existing = useLoad(id && isOwner ? () => mo.entry(id) : null, [mo, id, isOwner])
  const [direction, setDirection] = useState<Direction>((params.get('direction') as Direction) || 'EXPENSE')
  const [amount, setAmount] = useState('')
  const [date, setDate] = useState(todayIso())
  const [category, setCategory] = useState<FinCategory>('FEED')
  const [method, setMethod] = useState<PaymentMethod>('UPI')
  const [status, setStatus] = useState<EntryStatus>('PAID')
  const [counterparty, setCounterparty] = useState('')
  const [alloc, setAlloc] = useState<AllocKind>(params.get('animal') ? 'ANIMAL' : 'FARM')
  const [groupId, setGroupId] = useState('')
  const [animalId, setAnimalId] = useState(params.get('animal') ?? '')
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const e = existing.data
  const auto = Boolean(e?.auto)

  useEffect(() => {
    if (!e) return
    setDirection(e.direction); setAmount(String(e.amount)); setDate(e.date); setCategory(e.category)
    setMethod(e.paymentMethod ?? 'UPI'); setStatus(e.status); setCounterparty(e.counterparty ?? '')
    setAlloc(e.alloc?.kind ?? 'FARM'); setGroupId(e.alloc?.group ? String(e.alloc.group.id) : ''); setAnimalId(e.alloc?.animal ? String(e.alloc.animal.id) : '')
    setNotes(e.notes ?? '')
  }, [e])

  if (!isOwner) return <OwnerOnlyScreen title="Transaction" back="/money" />
  const cats = direction === 'INCOME' ? INCOME_CATEGORIES : EXPENSE_CATEGORIES
  const switchDir = (d: Direction) => { setDirection(d); setCategory(d === 'INCOME' ? 'MILK_SALES' : 'FEED') }
  const amt = toNum(amount)
  const src = sourceLink(e?.source, e ?? undefined)

  const save = async () => {
    if (!auto) {
      if (!amt || amt <= 0) { notify('Enter an amount above ₹0'); return }
      if (alloc === 'GROUP' && !groupId) { notify('Choose the group'); return }
      if (alloc === 'ANIMAL' && !animalId) { notify('Choose the animal'); return }
    }
    setBusy(true)
    try {
      const body = auto
        ? { status, paymentMethod: method, notes: notes.trim() || null }
        : {
          direction, category, amount: amt, date, status, paymentMethod: method,
          counterparty: counterparty.trim() || null, notes: notes.trim() || null,
          allocKind: alloc, groupId: alloc === 'GROUP' ? Number(groupId) : null, animalId: alloc === 'ANIMAL' ? Number(animalId) : null,
        }
      await mo.saveEntry(body, id)
      notify(id ? 'Entry updated' : `${direction === 'INCOME' ? 'Income' : 'Expense'} saved`)
      nav(-1)
    } catch (err) { notify(errorMessage(err)) } finally { setBusy(false) }
  }
  const remove = async () => {
    if (!e) return
    setBusy(true)
    try { await mo.deleteEntry(e.id); notify('Entry deleted'); nav('/money', { replace: true }) } catch (err) { notify(errorMessage(err)) } finally { setBusy(false); setConfirm(false) }
  }

  return (
    <Screen>
      <TopBar title={id ? (auto ? 'Entry' : 'Edit transaction') : 'Add transaction'} close="/money" />
      <div className="flex flex-col gap-4 px-4 pb-4">
        {id && <LoadState loading={existing.loading} error={existing.error} onRetry={existing.reload} empty={!e} />}
        {auto && e ? (
          <div className="rounded-[14px] bg-white p-3.5">
            <div className="flex items-baseline justify-between"><b className="text-base">{finCategoryLabel(e.category)}</b>
              <b className={e.direction === 'INCOME' ? 'text-brand' : 'text-danger'}>{e.direction === 'INCOME' ? '+' : '−'}{rupees(e.amount)}</b></div>
            <div className="mt-1 text-[13px] text-ink-3">{e.date}{e.counterparty ? ` · ${e.counterparty}` : ''}{e.alloc?.animal ? ` · ${e.alloc.animal.tag}` : ''}{e.quantity ? ` · ${e.quantity} ${e.unit?.toLowerCase() ?? ''}` : ''}</div>
            <div className="mt-2.5 rounded-[10px] bg-info-soft px-3 py-2 text-sm text-info-text">
              Added automatically from {src?.to ? <Link to={src.to} className="font-semibold text-info-text underline">{src.label}</Link> : <b>{src?.label ?? 'a record'}</b>}. Change the amount there; here you can set payment and status.
            </div>
          </div>
        ) : (
          <>
            <Segmented label="Income or expense" value={direction} onChange={switchDir} options={[{ value: 'INCOME', label: 'Income' }, { value: 'EXPENSE', label: 'Expense' }]} />
            <AmountInput id="tx-amount" value={amount} onChange={setAmount} />
            <div>
              <span className="lab">Date</span>
              <div className="flex flex-wrap items-center gap-2">
                <button type="button" className={`chip ${date === todayIso() ? 'chip-on' : ''}`} onClick={() => setDate(todayIso())}>Today</button>
                <button type="button" className={`chip ${date === addDays(todayIso(), -1) ? 'chip-on' : ''}`} onClick={() => setDate(addDays(todayIso(), -1))}>Yesterday</button>
                <input aria-label="Pick date" type="date" className="inp h-10 w-[160px]" max={todayIso()} value={date} onChange={(ev) => ev.target.value && setDate(ev.target.value)} />
              </div>
            </div>
            <div>
              <span className="lab">Category *</span>
              <ChoiceChips label="Category" wrap options={cats} value={category} onChange={setCategory} />
              <div className="hint">Milk sales, purchases, vet costs, breeding fees and wages are added on their own from those records.</div>
            </div>
          </>
        )}
        <div><span className="lab">Payment method</span><PaymentChips value={method} onChange={setMethod} /></div>
        <div><span className="lab">Status</span>
          <Segmented label="Status" value={status} onChange={setStatus} options={[{ value: 'PAID', label: direction === 'INCOME' ? 'Received' : 'Paid' }, { value: 'PENDING', label: 'Pending' }]} /></div>
        {!auto && (
          <>
            <div><label className="lab" htmlFor="tx-cp">{direction === 'INCOME' ? 'From (buyer)' : 'Paid to'}</label><input className="inp" id="tx-cp" value={counterparty} onChange={(ev) => setCounterparty(ev.target.value)} placeholder="e.g. TNEB, transport owner" /></div>
            <div className="rounded-[14px] bg-white p-3.5">
              <span className="lab">Allocate to</span>
              <ChoiceChips label="Allocate to" wrap value={alloc} onChange={setAlloc} options={[{ value: 'FARM', label: 'Whole farm' }, { value: 'GROUP', label: 'Group' }, { value: 'ANIMAL', label: 'Animal' }]} />
              {alloc === 'GROUP' && (
                <div className="mt-3"><label className="lab" htmlFor="tx-group">Group</label>
                  <select className="inp" id="tx-group" value={groupId} onChange={(ev) => setGroupId(ev.target.value)}>
                    <option value="">Choose a group</option>{groups.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
                  </select></div>
              )}
              {alloc === 'ANIMAL' && <div className="mt-3"><label className="lab" htmlFor="tx-animal">Animal</label><AnimalSelect id="tx-animal" value={animalId} onChange={setAnimalId} /></div>}
              <div className="hint">{alloc === 'FARM' ? 'Shared costs are split across animals by days on the farm in profit per animal.' : alloc === 'GROUP' ? 'Split across the group\'s animals.' : 'Counted fully against this animal.'}</div>
            </div>
          </>
        )}
        <div><label className="lab" htmlFor="tx-notes">Notes</label><input className="inp" id="tx-notes" placeholder="Optional · e.g. bill no." value={notes} onChange={(ev) => setNotes(ev.target.value)} /></div>
        {id && e && !auto && <button type="button" className="btn2 h-12 text-danger" onClick={() => setConfirm(true)}>Delete entry</button>}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <div className="mb-2.5 flex justify-between text-[15px]">
          <span>{direction === 'INCOME' ? 'Income' : 'Expense'} · {finCategoryLabel(category)} · {status === 'PAID' ? (direction === 'INCOME' ? 'Received' : 'Paid') : 'Pending'}</span>
          <b className={direction === 'INCOME' ? 'text-brand' : 'text-danger'}>{amt ? `${direction === 'INCOME' ? '+' : '−'}${rupees(amt)}` : '—'}</b>
        </div>
        <button type="button" className="btn" disabled={busy || (Boolean(id) && !e)} onClick={save}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
      {confirm && (
        <Dialog title="Delete this entry?" onClose={() => setConfirm(false)}>
          <p className="mt-0 text-[15px] text-ink-2">It is removed from Finance and profit per animal.</p>
          <div className="mt-4 grid grid-cols-2 gap-2.5">
            <button type="button" onClick={() => setConfirm(false)} className="h-12 rounded-xl border border-line bg-white font-semibold">Cancel</button>
            <button type="button" onClick={remove} className="h-12 rounded-xl bg-danger font-semibold text-white">Delete</button>
          </div>
        </Dialog>
      )}
    </Screen>
  )
}
