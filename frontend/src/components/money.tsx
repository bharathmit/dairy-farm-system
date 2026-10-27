/* eslint-disable react-refresh/only-export-components */
import { useMemo, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useStore } from '../data/store'
import { MONEY_KINDS, moneyApi, rupees, type MoneyKind, type Period } from '../data/money'
import { PAYMENT_METHODS, type PaymentMethod } from '../data/feed'
import { addDays, decimal, todayIso, toNum, useLoad } from '../data/records'
import { Icon } from './icons'
import { Screen, TopBar } from './ui'

/** Money API for the chosen farm, and whether the caller is an owner (everything here is owner only). */
export function useMoney() {
  const { farm } = useStore()
  const farmId = farm?.id ?? ''
  const mo = useMemo(() => moneyApi(farmId), [farmId])
  return { mo, farmId, isOwner: farm?.role === 'Owner' }
}

/** Page for workers who open an owner-only money screen. */
export function OwnerOnlyScreen({ title, back = '/home' }: { title: string; back?: string }) {
  return (
    <Screen>
      <TopBar title={title} back={back} />
      <div className="mx-4 mt-3 rounded-[14px] bg-white p-4 text-[15px]">
        <div className="flex items-center gap-2 font-semibold"><Icon name="gear" size={18} />Owners only</div>
        <p className="mb-0 mt-1.5 text-sm text-ink-3">Money, wages and costs are only shown to farm owners.</p>
        <Link to={back} className="mt-3 inline-block font-semibold">Go back</Link>
      </div>
    </Screen>
  )
}

export function OwnerNote() {
  return (
    <div className="mb-2.5 flex items-center gap-1.5 text-[13px] text-ink-3">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#56655B" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg>
      Owner only · workers cannot see this
    </div>
  )
}

export const PERIODS: { value: Period; label: string }[] = [
  { value: 'MONTH', label: 'This month' }, { value: 'LAST_MONTH', label: 'Last month' },
  { value: 'QUARTER', label: 'Quarter' }, { value: 'YEAR', label: 'Year' }, { value: 'CUSTOM', label: 'Custom' },
]

/** Single-choice chips in a scrolling row. */
export function ChoiceChips<T extends string>({ options, value, onChange, label, wrap }: {
  options: { value: T; label: ReactNode }[]; value: T; onChange: (v: T) => void; label: string; wrap?: boolean
}) {
  return (
    <div role="group" aria-label={label} className={`flex gap-2 ${wrap ? 'flex-wrap' : 'overflow-x-auto pb-1'}`}>
      {options.map((o) => (
        <button key={o.value} type="button" aria-pressed={value === o.value} onClick={() => onChange(o.value)} className={`chip ${value === o.value ? 'chip-on' : ''}`}>{o.label}</button>
      ))}
    </div>
  )
}

/** Payment method chips (no "Other"). */
export function PaymentChips({ value, onChange }: { value: PaymentMethod; onChange: (v: PaymentMethod) => void }) {
  return <ChoiceChips label="Payment method" wrap options={PAYMENT_METHODS.filter((p) => p.value !== 'OTHER')} value={value} onChange={onChange} />
}

/** ₹ amount with −/+ step buttons and quick-add buttons. */
export function AmountInput({ id, value, onChange, steps = [100], quick = [500, 1000, 5000], label = 'Amount (₹) *' }: {
  id: string; value: string; onChange: (v: string) => void; steps?: number[]; quick?: number[]; label?: string
}) {
  const add = (d: number) => onChange(String(Math.max(0, Math.round(((toNum(value) ?? 0) + d) * 100) / 100)))
  const b = 'h-11 min-w-14 rounded-[10px] border border-line bg-white text-sm font-semibold text-ink'
  return (
    <div className="rounded-[14px] bg-white p-3.5">
      <label className="lab" htmlFor={id}>{label}</label>
      <div className="flex items-center gap-2">
        {steps.map((s) => <button key={`m${s}`} type="button" className={b} onClick={() => add(-s)}>−{s.toLocaleString('en-IN')}</button>)}
        <input id={id} inputMode="decimal" value={value} onChange={(e) => onChange(decimal(e.target.value))} className="h-12 min-w-0 flex-1 rounded-[10px] border border-line text-center text-2xl font-bold" />
        {steps.map((s) => <button key={`p${s}`} type="button" className={b} onClick={() => add(s)}>+{s.toLocaleString('en-IN')}</button>)}
      </div>
      {quick.length > 0 && (
        <div className="mt-2 flex gap-2">{quick.map((s) => <button key={s} type="button" className={`${b} flex-1`} onClick={() => add(s)}>+{s.toLocaleString('en-IN')}</button>)}</div>
      )}
    </div>
  )
}

/** Labelled bar for a category total. */
export function BarRow({ label, amount, max, tone }: { label: string; amount: number; max: number; tone: 'in' | 'out' }) {
  const w = max > 0 ? Math.max(2, Math.round((amount / max) * 100)) : 0
  return (
    <div className="grid min-h-7 grid-cols-[110px_minmax(0,1fr)_76px] items-center gap-2 text-sm">
      <span className="truncate">{label}</span>
      <div className="h-2.5 overflow-hidden rounded bg-line-faint"><div className={`h-full ${tone === 'in' ? 'bg-brand' : 'bg-danger'}`} style={{ width: `${w}%` }} /></div>
      <b className="text-right">{rupees(amount)}</b>
    </div>
  )
}

/** Stat tile. */
export function Stat({ k, v, sub, tone = '', bg = 'bg-white' }: { k: string; v: ReactNode; sub?: ReactNode; tone?: string; bg?: string }) {
  return (
    <div className={`rounded-xl p-3 ${bg}`}>
      <div className={`text-xs font-semibold ${tone || 'text-ink-3'}`}>{k}</div>
      <div className={`mt-0.5 text-xl font-bold ${tone}`}>{v}</div>
      {sub && <div className={`mt-0.5 text-xs ${tone || 'text-ink-3'}`}>{sub}</div>}
    </div>
  )
}

/** Home: today's feed cost, milk income and the two margins; each opens its chart page. Owner only. */
export function TodayMoney() {
  const { mo } = useMoney()
  const today = todayIso()
  const days = useLoad(() => mo.daily(addDays(today, -1), today), [mo, today])
  const t = days.data?.find((d) => d.date === today)
  const y = days.data?.find((d) => d.date !== today)
  const kinds = Object.keys(MONEY_KINDS) as MoneyKind[]
  return (
    <div className="mt-2.5 grid grid-cols-2 gap-2.5 md:grid-cols-4">
      {kinds.map((k) => {
        const m = MONEY_KINDS[k]
        const v = t ? m.value(t) : undefined
        const margin = k.endsWith('margin')
        return (
          <Link key={k} to={`/money/trend/${k}`} className="rounded-xl bg-white p-3 text-ink no-underline hover:text-ink">
            <div className="text-xs font-semibold text-ink-3">{k === 'feed' ? m.short : `Today · ${m.short.toLowerCase()}`}</div>
            <div className={`mt-0.5 text-[22px] font-bold ${margin && v !== undefined && v < 0 ? 'text-danger' : ''}`}>{days.loading ? '…' : rupees(v, margin)}</div>
            <div className="mt-0.5 text-xs font-semibold text-ink-3">{days.error ? 'Couldn\'t load' : `Yday ${rupees(y ? m.value(y) : undefined, margin)}`}</div>
          </Link>
        )
      })}
    </div>
  )
}
