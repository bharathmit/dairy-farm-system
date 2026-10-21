import { useState, type ReactNode, type InputHTMLAttributes } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Icon } from './icons'

/** Phone-width column. On wider screens it sits centred like a device. */
export function Screen({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`relative mx-auto flex min-h-[100dvh] w-full max-w-[430px] flex-col bg-ground shadow-sm ${className}`}>
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

/** Bottom sheet over a dimmed backdrop. */
export function Sheet({ onClose, children, label, tall }: { onClose: () => void; children: ReactNode; label: string; tall?: boolean }) {
  return (
    <div className="fixed inset-0 z-40 mx-auto flex max-w-[430px] flex-col justify-end" role="dialog" aria-modal="true" aria-label={label}>
      <button type="button" aria-label="Close" onClick={onClose} className="absolute inset-0 bg-[rgba(20,30,24,.45)]" />
      <div className={`relative flex flex-col overflow-hidden rounded-t-[22px] bg-white ${tall ? 'max-h-[calc(100dvh-64px)]' : 'max-h-[90dvh]'}`}>
        <div className="mx-auto mb-1 mt-2.5 h-1 w-10 shrink-0 rounded bg-line" />
        {children}
      </div>
    </div>
  )
}

export function Dialog({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 mx-auto flex max-w-[430px] items-center px-6" role="dialog" aria-modal="true" aria-labelledby="dlg-title">
      <button type="button" aria-label="Close" onClick={onClose} className="absolute inset-0 bg-[rgba(20,30,24,.45)]" />
      <div className="relative w-full rounded-[18px] bg-white p-6">
        <h2 id="dlg-title" className="m-0 mb-2 text-xl font-bold">{title}</h2>
        {children}
      </div>
    </div>
  )
}

export function BottomNav({ active }: { active: 'home' | 'herd' | 'alerts' | 'more' }) {
  const item = (key: typeof active, to: string, icon: Parameters<typeof Icon>[0]['name'], label: string) => (
    <Link to={to} aria-current={active === key ? 'page' : undefined} className={`flex flex-col items-center justify-center gap-[3px] text-[11px] font-semibold no-underline ${active === key ? 'text-brand' : 'text-ink-3'}`}>
      <Icon name={icon} color="currentColor" />{label}
    </Link>
  )
  return (
    <nav aria-label="Main" className="sticky bottom-0 z-20 grid h-[72px] grid-cols-5 border-t border-line-soft bg-white">
      {item('home', '/home', 'home', 'Home')}
      {item('herd', '/animals', 'tag', 'Herd')}
      <Link to="/animals/new" aria-label="Add animal" className="flex items-center justify-center">
        <span className="-mt-[18px] flex h-12 w-12 items-center justify-center rounded-full bg-brand shadow-[0_4px_10px_rgba(31,92,58,.3)]"><Icon name="plus" color="#fff" /></span>
      </Link>
      {item('alerts', '/alerts', 'bell', 'Alerts')}
      {item('more', '/settings', 'menu', 'More')}
    </nav>
  )
}

export function Toast({ text }: { text: string | null }) {
  if (!text) return null
  return (
    <div role="status" className="fixed bottom-24 left-1/2 z-50 -translate-x-1/2 rounded-xl bg-ink px-4 py-2.5 text-sm font-medium text-white shadow-lg">{text}</div>
  )
}
