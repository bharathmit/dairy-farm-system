import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Footer, LogoRow, PasswordInput, Screen } from '../../components/ui'
import { Icon } from '../../components/icons'
import { useStore } from '../../data/store'

function Check({ ok, children }: { ok: boolean; children: string }) {
  return (
    <div className={`flex items-center gap-2 text-sm ${ok ? 'text-[#1C3A28]' : 'text-ink-3'}`}>
      {ok ? <Icon name="check" size={18} color="#1F5C3A" strokeWidth={2.4} /> : <span aria-hidden="true" className="flex h-[18px] w-[18px] items-center justify-center"><span className="h-3 w-3 rounded-full border-2 border-line" /></span>}
      <span>{children}<span className="sr-only">{ok ? ' (done)' : ' (not yet)'}</span></span>
    </div>
  )
}

export default function UpdatePassword() {
  const { notify } = useStore()
  const nav = useNavigate()
  const [pw, setPw] = useState('')
  const [pw2, setPw2] = useState('')
  const [error, setError] = useState('')
  const long = pw.length >= 8
  const digit = /\d/.test(pw)
  const match = pw.length > 0 && pw === pw2

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!(long && digit && match)) return setError('Check the password rules above.')
    notify('Password updated')
    nav('/login')
  }

  return (
    <Screen className="px-6 pb-5 pt-4">
      <Link to="/login" aria-label="Back to sign in" className="-ml-2.5 mb-2 flex h-11 w-11 items-center justify-center"><Icon name="back" /></Link>
      <LogoRow />
      <h1 className="mb-1.5 mt-7 text-[26px] font-bold">Set a new password</h1>
      <p className="m-0 mb-6 text-[15px] leading-snug text-ink-3">For bharath@… — opened from the reset link in your email.</p>

      <form onSubmit={submit} noValidate className="flex flex-col">
        <div className="flex flex-col gap-4">
          <div><label className="lab" htmlFor="np">New password *</label><PasswordInput id="np" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} /></div>
          <div><label className="lab" htmlFor="cp">Confirm new password *</label><PasswordInput id="cp" autoComplete="new-password" value={pw2} onChange={(e) => setPw2(e.target.value)} /></div>
          <div className="flex flex-col gap-1.5 rounded-xl bg-brand-soft px-3.5 py-3" aria-live="polite">
            <Check ok={long}>At least 8 characters</Check>
            <Check ok={digit}>Includes a number</Check>
            <Check ok={match}>Passwords match</Check>
          </div>
        </div>
        {error && <p role="alert" className="m-0 mt-3 text-sm font-medium text-danger-text">{error}</p>}
        <button type="submit" className="btn mt-5">Update password</button>
      </form>

      <Link to="/login" className="mt-auto flex min-h-11 items-center gap-1.5 self-center pt-6 text-[15px] font-semibold no-underline"><Icon name="back" size={18} color="currentColor" />Back to sign in</Link>
      <Footer className="pb-0" />
    </Screen>
  )
}
