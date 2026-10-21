/* eslint-disable react-refresh/only-export-components */
import { useMemo, useState, type ReactNode } from 'react'
import { Icon } from './icons'
import { Chips, Sheet } from './ui'
import { useStore } from '../data/store'
import type { Animal } from '../data/types'
import {
  addDays, CONTACT_ROLES, dayLabel, decimal, GRADE_TONE, PAY_EVERY, recordsApi, todayIso, toNum, useLoad,
  type Contact, type ContactInput, type ContactRole, type Grade,
} from '../data/records'
import { errorMessage } from '../lib/api'

/** API calls for the chosen farm, and whether money and owner actions are shown. */
export function useRecords() {
  const { farm } = useStore()
  const farmId = farm?.id ?? ''
  const rec = useMemo(() => recordsApi(farmId), [farmId])
  return { rec, farmId, isOwner: farm?.role === 'Owner' }
}

/** Date with ‹ › buttons to step a day back or forward (not past today). */
export function DateNav({ id, value, onChange, label = 'Date' }: { id: string; value: string; onChange: (v: string) => void; label?: string }) {
  const today = todayIso()
  const atToday = value >= today
  const btn = 'flex h-12 w-12 shrink-0 items-center justify-center rounded-[10px] border border-line bg-white disabled:opacity-40'
  return (
    <div>
      <label className="lab" htmlFor={id}>{label} · {dayLabel(value)}</label>
      <div className="flex gap-1.5">
        <button type="button" className={btn} aria-label="Previous day" onClick={() => onChange(addDays(value, -1))}><Icon name="back" size={20} strokeWidth={2.2} /></button>
        <input className="inp min-w-0 flex-1 text-center" id={id} type="date" max={today} value={value} onChange={(e) => e.target.value && onChange(e.target.value)} />
        <button type="button" className={btn} aria-label="Next day" disabled={atToday} onClick={() => onChange(addDays(value, 1))}><Icon name="chevron" size={20} strokeWidth={2.2} /></button>
      </div>
    </div>
  )
}

/** Loading text or the API's error with a retry button. Renders nothing when neither. */
export function LoadState({ loading, error, onRetry, empty }: { loading: boolean; error: string | null; onRetry: () => void; empty?: boolean }) {
  if (error) {
    return (
      <div className="card bg-danger-soft" role="alert">
        <div className="text-[15px] font-semibold text-danger-text">{error}</div>
        <button type="button" onClick={onRetry} className="mt-1 h-10 border-0 bg-transparent p-0 font-semibold text-danger-text underline">Try again</button>
      </div>
    )
  }
  if (loading && empty !== false) return <p className="px-5 text-sm text-ink-3" role="status">Loading…</p>
  return null
}

export function GradeBadge({ grade, large }: { grade?: Grade | null; large?: boolean }) {
  if (!grade) return <span className="text-ink-3">—</span>
  return <span className={`rounded-md font-bold ${GRADE_TONE[grade]} ${large ? 'px-3 py-1.5 text-lg' : 'px-[7px] py-0.5 text-xs'}`}>{grade}</span>
}

/** Litres entry with −1 −0.5 / +0.5 +1 buttons. */
export function LitresStepper({ value, onChange, label, big }: { value: string; onChange: (v: string) => void; label: string; big?: boolean }) {
  const step = (d: number) => {
    const n = Math.max(0, Math.round(((toNum(value) ?? 0) + d) * 10) / 10)
    onChange(n.toFixed(1))
  }
  const b = 'h-10 min-w-[48px] rounded-[10px] border border-line bg-white text-sm font-semibold text-ink'
  return (
    <div className="flex items-center gap-2">
      {big && <button type="button" className={b} onClick={() => step(-1)} aria-label={`${label} minus 1`}>−1</button>}
      <button type="button" className={b} onClick={() => step(-0.5)} aria-label={`${label} minus 0.5`}>−0.5</button>
      <input aria-label={label} inputMode="decimal" placeholder="0.0" value={value} onChange={(e) => onChange(decimal(e.target.value))}
        className="h-11 min-w-0 flex-1 rounded-[10px] border border-line text-center text-[22px] font-bold" />
      <button type="button" className={b} onClick={() => step(0.5)} aria-label={`${label} plus 0.5`}>+0.5</button>
      {big && <button type="button" className={b} onClick={() => step(1)} aria-label={`${label} plus 1`}>+1</button>}
    </div>
  )
}

/** Select of the farm's active animals (filter decides which). */
export function AnimalSelect({ id, value, onChange, filter, placeholder = 'Choose an animal' }: {
  id: string; value: string; onChange: (id: string) => void; filter?: (a: Animal) => boolean; placeholder?: string
}) {
  const { animals } = useStore()
  const list = animals
    .filter((a) => a.status === 'Active' && (!filter || filter(a) || a.id === value))
    .sort((x, y) => x.tag.localeCompare(y.tag, undefined, { numeric: true }))
  return (
    <select className="inp" id={id} value={value} onChange={(e) => onChange(e.target.value)}>
      <option value="">{placeholder}</option>
      {list.map((a) => <option key={a.id} value={a.id}>{a.tag}{a.name ? ` · ${a.name}` : ''}{a.breed ? ` (${a.breed}, ${a.category.toLowerCase()})` : ''}</option>)}
    </select>
  )
}

export const isMilkingCow = (a: Animal) => a.sex === 'Female' && a.category === 'Milking'

// ---------- contacts ----------

export const emptyContact = (role?: ContactRole): ContactInput => ({ name: '', roles: role ? [role] : [], phone: '', village: '', milkRate: null, payEvery: null, notes: '', active: true })

/** Contact form fields, shared by the contact page and the "+ New" sheet. */
export function ContactFields({ value, onChange, showMoney }: { value: ContactInput; onChange: (c: ContactInput) => void; showMoney: boolean }) {
  const set = (p: Partial<ContactInput>) => onChange({ ...value, ...p })
  const toggle = (r: ContactRole) => set({ roles: value.roles.includes(r) ? value.roles.filter((x) => x !== r) : [...value.roles, r] })
  return (
    <div className="flex flex-col gap-3.5">
      <div><label className="lab" htmlFor="ct-name">Name *</label><input className="inp" id="ct-name" value={value.name} onChange={(e) => set({ name: e.target.value })} /></div>
      <div>
        <span className="lab">Type * <span className="font-medium text-ink-3">· pick one or more</span></span>
        <Chips label="Contact type" options={CONTACT_ROLES} value={value.roles} onToggle={toggle} />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div><label className="lab" htmlFor="ct-phone">Phone</label><input className="inp" id="ct-phone" type="tel" placeholder="+91" value={value.phone ?? ''} onChange={(e) => set({ phone: e.target.value })} /></div>
        <div><label className="lab" htmlFor="ct-village">Village / town</label><input className="inp" id="ct-village" value={value.village ?? ''} onChange={(e) => set({ village: e.target.value })} /></div>
      </div>
      {value.roles.includes('BUYER') && (
        <div className="flex flex-col gap-3 rounded-[14px] bg-white p-3.5">
          <b className="text-[15px]">Milk buyer details</b>
          <div className="grid grid-cols-2 gap-3">
            {showMoney && <div><label className="lab" htmlFor="ct-rate">Usual rate ₹/L</label><input className="inp" id="ct-rate" inputMode="decimal" value={value.milkRate ?? ''} onChange={(e) => set({ milkRate: toNum(decimal(e.target.value)) ?? null })} /></div>}
            <div><label className="lab" htmlFor="ct-pay">Pays every</label>
              <select className="inp" id="ct-pay" value={value.payEvery ?? ''} onChange={(e) => set({ payEvery: (e.target.value || null) as ContactInput['payEvery'] })}>
                <option value="">—</option>
                {PAY_EVERY.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
              </select>
            </div>
          </div>
          <div className="hint mt-0">The rate fills in on a dairy sale; you can still change it each time.</div>
        </div>
      )}
      <div><label className="lab" htmlFor="ct-notes">Notes</label><textarea className="inp h-[76px] resize-none py-3" id="ct-notes" placeholder="Optional" value={value.notes ?? ''} onChange={(e) => set({ notes: e.target.value })} /></div>
    </div>
  )
}

export function contactProblem(c: ContactInput) {
  if (!c.name.trim()) return 'Enter a name'
  if (!c.roles.length) return 'Pick at least one type'
  return null
}

/** Body sent to the API: blanks become null. */
export const contactBody = (c: ContactInput): ContactInput => ({
  ...c, name: c.name.trim(), phone: c.phone?.trim() || undefined, village: c.village?.trim() || undefined, notes: c.notes?.trim() || undefined,
})

/** Bottom sheet to add a contact without leaving the current form. */
function NewContactSheet({ role, onClose, onSaved }: { role: ContactRole; onClose: () => void; onSaved: (c: Contact) => void }) {
  const { rec, isOwner } = useRecords()
  const { notify } = useStore()
  const [c, setC] = useState<ContactInput>(emptyContact(role))
  const [busy, setBusy] = useState(false)
  const save = async () => {
    const p = contactProblem(c)
    if (p) { notify(p); return }
    setBusy(true)
    try {
      const saved = await rec.saveContact(contactBody(c))
      notify(`${saved.name} added to contacts`)
      onSaved(saved)
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  return (
    <Sheet label="Add contact" onClose={onClose} tall>
      <div className="flex items-center justify-between px-4 pb-2"><h2 className="m-0 text-xl font-bold">Add contact</h2>
        <button type="button" aria-label="Close" onClick={onClose} className="flex h-11 w-11 items-center justify-center"><Icon name="close" /></button></div>
      <div className="overflow-y-auto bg-ground px-4 py-3"><ContactFields value={c} onChange={setC} showMoney={isOwner} /></div>
      <div className="border-t border-line-soft bg-white p-4"><button type="button" className="btn" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save contact'}</button></div>
    </Sheet>
  )
}

/** Dropdown of active contacts with a role, plus "+ New" that adds one and selects it. */
export function ContactSelect({ id, role, value, onChange, onPicked, newLabel }: {
  id: string; role: ContactRole; value: string; onChange: (id: string) => void; onPicked?: (c: Contact) => void; newLabel: string
}) {
  const { rec } = useRecords()
  const { data, setData, error } = useLoad(() => rec.contacts(role, true), [rec, role])
  const [adding, setAdding] = useState(false)
  const list = data ?? []
  return (
    <div>
      <select className="inp" id={id} value={value} onChange={(e) => {
        if (e.target.value === '__new') { setAdding(true); return }
        onChange(e.target.value)
        const c = list.find((x) => String(x.id) === e.target.value)
        if (c) onPicked?.(c)
      }}>
        <option value="">{error ? 'Contacts unavailable' : data ? (list.length ? 'Choose' : 'None saved yet') : 'Loading…'}</option>
        {list.map((c) => <option key={c.id} value={c.id}>{c.name}{c.village ? ` · ${c.village}` : ''}</option>)}
        <option value="__new">+ {newLabel}…</option>
      </select>
      <button type="button" onClick={() => setAdding(true)} className="mt-1.5 h-8 border-0 bg-transparent p-0 text-[13px] font-semibold text-brand">+ {newLabel}</button>
      {adding && (
        <NewContactSheet role={role} onClose={() => setAdding(false)} onSaved={(c) => {
          setData([...list, c].sort((x, y) => x.name.localeCompare(y.name)))
          onChange(String(c.id)); onPicked?.(c); setAdding(false)
        }} />
      )}
    </div>
  )
}

/** Section card with a title row. */
export function Card({ title, right, children, className = '' }: { title?: ReactNode; right?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-[14px] bg-white p-3.5 ${className}`}>
      {(title || right) && <div className="mb-2.5 flex items-baseline justify-between gap-2">{title && <b className="text-[15px]">{title}</b>}{right}</div>}
      {children}
    </div>
  )
}
