import { useEffect, useState, useSyncExternalStore, type ReactNode, type InputHTMLAttributes } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Icon, type IconName } from './icons'
import { useStore } from '../data/store'
import { alertCountSnapshot, loadAlertCount, subscribeAlertCount } from '../data/alerts'

/**
 * Page column. Phone: full width (max 430 px). Tablet (md ≥ 768 px): 720 px. `wide` screens — dashboards,
 * lists and reports — grow to 1040 px on laptops (lg ≥ 1024 px). Forms stay at 720 px so fields stay readable.
 */
export function Screen({ children, className = '', wide }: { children: ReactNode; className?: string; wide?: boolean }) {
  return (
    <div className={`relative mx-auto flex min-h-[100dvh] w-full max-w-[430px] flex-col bg-ground shadow-sm md:max-w-[720px] ${wide ? 'lg:max-w-[1040px]' : ''} ${className}`}>
      {children}
    </div>
  )
}

export function LogoMark({ size = 28 }: { size?: number }) {
  const r = size >= 40 ? 12 : 8
  return (
    <span
      aria-hidden="true"
      className="flex shrink-0 items-center justify-center bg-brand"
      style={{ width: size, height: size, borderRadius: r }}
    >
      <svg width={size * 0.57} height={size * 0.57} viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth={size >= 40 ? 2 : 2.2} strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 3c3 4 6 6.5 6 10a6 6 0 0 1-12 0c0-3.5 3-6 6-10z" />
      </svg>
    </span>
  )
}

export function LogoRow({ right }: { right?: ReactNode }) {
  return (
    <div className="flex items-center gap-3">
      <LogoMark size={44} />
      <div className="flex-1 text-xl font-bold">Dairy Farm</div>
      {right}
    </div>
  )
}

export function Footer({ className = '' }: { className?: string }) {
  return <div className={`px-4 py-3 text-center text-xs text-ink-3 ${className}`}>© 2026 Dairy Farm. All rights reserved.</div>
}

/** Title bar: back/close button, logo, title, optional actions. */
export function TopBar({ title, back, close, right, border }: {
  title: ReactNode; back?: string | number; close?: string; right?: ReactNode; border?: boolean
}) {
  const nav = useNavigate()
  const target = close ?? back
  return (
    <div className={`sticky top-0 z-10 flex items-center gap-1 bg-ground px-3 py-2 ${border ? 'border-b border-line-soft' : ''}`}>
      {target !== undefined && (
        <button
          type="button"
          aria-label={close ? 'Close' : 'Back'}
          onClick={() => (typeof target === 'number' ? nav(target) : nav(target))}
          className="flex h-11 w-11 items-center justify-center rounded-xl"
        >
          <Icon name={close ? 'close' : 'back'} />
        </button>
      )}
      <span className="mr-1.5"><LogoMark /></span>
      <h1 className="m-0 flex-1 truncate text-xl font-bold">{title}</h1>
      {right}
    </div>
  )
}

/** Sticky bottom action area followed by the footer. */
export function ActionBar({ children }: { children: ReactNode }) {
  return (
    <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-ground">
      <div className="flex gap-3 px-5 pb-2.5 pt-4">{children}</div>
      <Footer className="pt-0" />
    </div>
  )
}

export function PasswordInput(props: InputHTMLAttributes<HTMLInputElement>) {
  const [show, setShow] = useState(false)
  return (
    <div className="relative">
      <input {...props} type={show ? 'text' : 'password'} className="inp pr-[52px]" />
      <button
        type="button"
        aria-label={show ? 'Hide password' : 'Show password'}
        aria-pressed={show}
        onClick={() => setShow((s) => !s)}
        className="absolute right-0.5 top-0.5 flex h-11 w-11 items-center justify-center rounded-lg"
      >
        <Icon name={show ? 'eyeOff' : 'eye'} color="#56655B" />
      </button>
    </div>
  )
}

export function Segmented<T extends string>({ options, value, onChange, label, className = '' }: {
  options: { value: T; label: ReactNode }[]; value: T; onChange: (v: T) => void; label: string; className?: string
}) {
  return (
    <div role="group" aria-label={label} className={`grid rounded-xl bg-track p-1 ${className}`} style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          aria-pressed={value === o.value}
          onClick={() => onChange(o.value)}
          className={`h-10 rounded-[9px] text-sm font-semibold ${value === o.value ? 'bg-white text-ink shadow-sm' : 'text-ink-2'}`}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

export function Chips<T extends string>({ options, value, onToggle, label, wrap = true }: {
  options: { value: T; label: ReactNode }[]; value: T[]; onToggle: (v: T) => void; label: string; wrap?: boolean
}) {
  return (
    <div role="group" aria-label={label} className={`flex gap-2 ${wrap ? 'flex-wrap' : 'overflow-x-auto pb-1'}`}>
      {options.map((o) => (
        <button key={o.value} type="button" aria-pressed={value.includes(o.value)} onClick={() => onToggle(o.value)} className={`chip ${value.includes(o.value) ? 'chip-on' : ''}`}>
          {o.label}
        </button>
      ))}
    </div>
  )
}

export function Switch({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={`relative h-7 w-12 shrink-0 rounded-full transition-colors ${checked ? 'bg-brand' : 'bg-line'}`}
    >
      <span className={`absolute top-[3px] h-[22px] w-[22px] rounded-full bg-white transition-all ${checked ? 'left-[23px]' : 'left-[3px]'}`} />
    </button>
  )
}

/** Bottom sheet over a dimmed backdrop; a centred panel on tablet and desktop. */
export function Sheet({ onClose, children, label, tall }: { onClose: () => void; children: ReactNode; label: string; tall?: boolean }) {
  return (
    <div className="fixed inset-0 z-40 mx-auto flex max-w-[430px] flex-col justify-end md:max-w-none md:items-center md:justify-center md:p-6" role="dialog" aria-modal="true" aria-label={label}>
      <button type="button" aria-label="Close" onClick={onClose} className="absolute inset-0 bg-[rgba(20,30,24,.45)]" />
      <div className={`relative flex w-full flex-col overflow-hidden rounded-t-[22px] bg-white md:max-w-[560px] md:rounded-[22px] md:pt-3 ${tall ? 'max-h-[calc(100dvh-64px)]' : 'max-h-[90dvh]'}`}>
        <div className="mx-auto mb-1 mt-2.5 h-1 w-10 shrink-0 rounded bg-line md:hidden" />
        {children}
      </div>
    </div>
  )
}

export function Dialog({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 mx-auto flex max-w-[430px] items-center px-6 md:max-w-none md:justify-center" role="dialog" aria-modal="true" aria-labelledby="dlg-title">
      <button type="button" aria-label="Close" onClick={onClose} className="absolute inset-0 bg-[rgba(20,30,24,.45)]" />
      <div className="relative w-full rounded-[18px] bg-white p-6 md:max-w-[420px]">
        <h2 id="dlg-title" className="m-0 mb-2 text-xl font-bold">{title}</h2>
        {children}
      </div>
    </div>
  )
}

const RECORD_TILES: { to: string; icon: IconName; color: string; title: string; sub: string }[] = [
  { to: '/milk/sheet', icon: 'list', color: '#1F5C3A', title: 'Milking sheet', sub: 'All cows, one session' },
  { to: '/milk/cow', icon: 'drop', color: '#1F5C3A', title: 'Milk — one cow', sub: 'With fat, SNF, SCC' },
  { to: '/milk/sale', icon: 'farm', color: '#1F5C3A', title: 'Farm total / sale', sub: 'Litres to dairy, income' },
  { to: '/weight/new', icon: 'weight', color: '#1F4E87', title: 'Weight & BCS', sub: 'Weight, height, condition' },
  { to: '/health/new', icon: 'health', color: '#A3271B', title: 'Health', sub: 'Treatment, vaccine, deworm' },
  { to: '/health/batch', icon: 'team', color: '#A3271B', title: 'Batch treatment', sub: 'Whole group at once' },
  { to: '/breeding/heat/new', icon: 'heart', color: '#A3271B', title: 'Heat', sub: 'Signs seen, best AI time' },
  { to: '/breeding/service/new', icon: 'heart', color: '#7A4B0E', title: 'Service / AI', sub: 'AI, natural, costs' },
  { to: '/breeding/pregnancy-check', icon: 'check', color: '#1F4E87', title: 'Pregnancy check', sub: 'Confirm or back to open' },
  { to: '/breeding/calving', icon: 'leaf', color: '#1F5C3A', title: 'Calving', sub: 'Dam, calf, colostrum' },
  { to: '/calves/feeding', icon: 'drop', color: '#1F5C3A', title: 'Calf feeding', sub: 'One calf or a group' },
  { to: '/feed/log', icon: 'leaf', color: '#1F5C3A', title: 'Log feeding', sub: '6 AM / 6 PM, planned vs actual' },
  { to: '/feed/buy', icon: 'download', color: '#7A4B0E', title: 'Buy feed', sub: 'Stock in, price, supplier' },
  { to: '/feed/batch', icon: 'list', color: '#7A4B0E', title: 'Prepare batch', sub: 'Concentrate from a formula' },
  { to: '/reminders/new', icon: 'bell', color: '#1F4E87', title: 'Reminder', sub: 'Anything to remember, repeats' },
]

/** The + button's sheet: every daily record plus adding an animal. */
export function AddRecordSheet({ onClose }: { onClose: () => void }) {
  const { farm } = useStore()
  const owner = farm?.role === 'Owner'
  const tiles = owner
    ? [...RECORD_TILES, { to: '/money/entries/new', icon: 'farm' as IconName, color: '#1F5C3A', title: 'Add transaction', sub: 'Income or expense' }]
    : RECORD_TILES
  return (
    <Sheet label="Add a record" onClose={onClose}>
      <div className="overflow-y-auto px-4 pb-6">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="m-0 text-xl font-bold">Add a record</h2>
          <Link to="/animals/scan" className="flex h-10 items-center gap-1.5 rounded-[10px] border border-line px-3 text-sm font-semibold text-ink no-underline"><Icon name="scan" size={18} color="currentColor" />Scan cow first</Link>
        </div>
        <div className="grid grid-cols-2 gap-2.5 md:grid-cols-3">
          {tiles.map((t) => (
            <Link key={t.to} to={t.to} className="flex min-h-24 flex-col items-start gap-2 rounded-[14px] bg-ground p-3.5 text-ink no-underline hover:text-ink">
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white"><Icon name={t.icon} color={t.color} /></span>
              <span className="text-[15px] font-semibold">{t.title}</span><span className="-mt-1.5 text-xs text-ink-3">{t.sub}</span>
            </Link>
          ))}
        </div>
        <Link to="/animals/new" className="btn2 mt-2.5 h-12 gap-2 text-[15px]"><Icon name="plus" size={18} />Add animal</Link>
      </div>
    </Sheet>
  )
}

/** Open-alert count for the nav badge (shared cache, refreshed at most once a minute). */
function useOpenAlerts() {
  const { farm } = useStore()
  const farmId = farm?.id ?? ''
  const snap = useSyncExternalStore(subscribeAlertCount, () => alertCountSnapshot(farmId))
  useEffect(() => { void loadAlertCount(farmId) }, [farmId])
  return snap?.count ?? 0
}

export function BottomNav({ active }: { active: 'home' | 'milk' | 'feed' | 'more' }) {
  const [adding, setAdding] = useState(false)
  const open = useOpenAlerts()
  const item = (key: typeof active, to: string, icon: Parameters<typeof Icon>[0]['name'], label: string, badge = 0) => (
    <Link to={to} aria-current={active === key ? 'page' : undefined} aria-label={badge ? `${label}, ${badge} open` : undefined} className={`relative flex flex-col items-center justify-center gap-[3px] text-[11px] font-semibold no-underline ${active === key ? 'text-brand' : 'text-ink-3'}`}>
      <Icon name={icon} color="currentColor" />{label}
      {badge > 0 && <span aria-hidden="true" className="absolute left-1/2 top-2 ml-1.5 min-w-[18px] rounded-full bg-[#C2410C] px-1 text-center text-[10px] font-bold leading-[18px] text-white">{badge > 99 ? '99+' : badge}</span>}
    </Link>
  )
  return (
    <>
      {/* Bottom bar on phone and tablet; a fixed left rail on desktop (xl ≥ 1280 px), with + on top. */}
      <nav aria-label="Main" className="sticky bottom-0 z-20 grid h-[72px] grid-cols-5 border-t border-line-soft bg-white xl:fixed xl:inset-y-0 xl:left-0 xl:h-auto xl:w-24 xl:auto-rows-[76px] xl:grid-cols-1 xl:content-start xl:gap-1 xl:border-r xl:border-t-0 xl:pt-5">
        {item('home', '/home', 'home', 'Home')}
        {item('milk', '/milk', 'drop', 'Milk')}
        <button type="button" onClick={() => setAdding(true)} aria-label="Add a record" className="flex items-center justify-center border-0 bg-transparent xl:order-first">
          <span className="-mt-[18px] xl:mt-0 flex h-12 w-12 items-center justify-center rounded-full bg-brand shadow-[0_4px_10px_rgba(31,92,58,.3)]"><Icon name="plus" color="#fff" /></span>
        </button>
        {item('feed', '/feed', 'leaf', 'Feed')}
        {item('more', '/settings', 'menu', 'More', open)}
      </nav>
      {adding && <AddRecordSheet onClose={() => setAdding(false)} />}
    </>
  )
}

export function Toast({ text }: { text: string | null }) {
  if (!text) return null
  return (
    <div role="status" className="fixed bottom-24 left-1/2 z-50 xl:bottom-8 -translate-x-1/2 rounded-xl bg-ink px-4 py-2.5 text-sm font-medium text-white shadow-lg">{text}</div>
  )
}
