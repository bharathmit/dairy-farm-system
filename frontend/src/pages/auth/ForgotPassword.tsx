import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Footer, LogoRow, Screen } from '../../components/ui'
import { Icon } from '../../components/icons'
import { useStore } from '../../data/store'

export default function ForgotPassword() {
  const { sendPasswordReset } = useStore()
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!email.trim()) return setError('Enter the email you sign in with.')
    setError('')
    setBusy(true)
    const ok = await sendPasswordReset(email.trim())
    setBusy(false)
    if (ok) setSent(true)
  }

  return (
    <Screen className="px-6 pb-5 pt-4">
      <Link to="/login" aria-label="Back to login" className="-ml-2.5 mb-2 flex h-11 w-11 items-center justify-center"><Icon name="back" /></Link>
      <LogoRow />
      <h1 className="mb-1.5 mt-7 text-[26px] font-bold">Reset password</h1>
      <p className="m-0 mb-7 text-[15px] leading-snug text-ink-3">Enter the email you sign in with. We'll send a link to set a new password.</p>
      <form onSubmit={submit} noValidate>
        <label className="lab" htmlFor="em">Email</label>
        <input className="inp" id="em" type="email" autoComplete="email" placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
        {error && <p role="alert" className="m-0 mt-2 text-sm font-medium text-danger-text">{error}</p>}
        <button type="submit" className="btn mt-5" disabled={busy}>{busy ? 'Sending…' : 'Send reset link'}</button>
      </form>

      {sent && (
        <div role="status" className="mt-7 flex gap-3 rounded-xl bg-brand-soft px-4 py-3.5">
          <span className="shrink-0"><Icon name="check" color="#1F5C3A" /></span>
          <div className="text-sm leading-snug text-[#1C3A28]"><b>Check your inbox.</b><br />If an account exists for that email, a reset link is on its way. It opens <b>Set a new password</b> and expires in 1 hour.</div>
        </div>
      )}

      <Link to="/login" className="mt-auto flex min-h-11 items-center gap-1.5 self-center pt-6 text-[15px] font-semibold no-underline"><Icon name="back" size={18} color="currentColor" />Back to sign in</Link>
      <Footer className="pb-0" />
    </Screen>
  )
}
