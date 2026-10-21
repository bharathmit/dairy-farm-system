import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Footer, LogoRow, PasswordInput, Screen } from '../../components/ui'
import { useStore } from '../../data/store'

export default function Login() {
  const { signIn, signInWithGoogle } = useStore()
  const nav = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [keep, setKeep] = useState(true)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!email.trim() || !password) {
      setError('Enter your email and password.')
      return
    }
    setBusy(true)
    const problem = await signIn(email.trim(), password)
    setBusy(false)
    if (problem) { setError(problem); return }
    nav('/farms')
  }

  return (
    <Screen className="px-6 pb-5 pt-14">
      <LogoRow right={<Link to="/signup" className="flex min-h-11 items-center px-1 text-sm font-semibold">Create account</Link>} />
      <h1 className="mb-1.5 mt-10 text-[28px] font-bold">Welcome back</h1>
      <p className="m-0 mb-7 text-[15px] text-ink-3">Sign in to manage your herd.</p>

      <form onSubmit={submit} noValidate className="flex flex-col">
        <div className="flex flex-col gap-4">
          <div>
            <label className="lab" htmlFor="em">Email</label>
            <input className="inp" id="em" type="email" autoComplete="email" placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div>
            <div className="flex items-baseline justify-between">
              <label className="lab" htmlFor="pw">Password</label>
              <Link to="/forgot-password" className="text-[13px] font-semibold">Forgot password?</Link>
            </div>
            <PasswordInput id="pw" autoComplete="current-password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
          <label className="flex min-h-11 items-center gap-2.5 text-sm text-ink-2">
            <input type="checkbox" checked={keep} onChange={(e) => setKeep(e.target.checked)} className="h-5 w-5 accent-brand" /> Keep me signed in on this phone
          </label>
        </div>
        {error && <p role="alert" className="m-0 mb-1 text-sm font-medium text-danger-text">{error}</p>}
        <button type="submit" className="btn mt-2" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>

      <div className="my-5 flex items-center gap-3 text-[13px] text-ink-3"><div className="h-px flex-1 bg-[#D3D8CF]" />or<div className="h-px flex-1 bg-[#D3D8CF]" /></div>
      <button type="button" className="btn2" onClick={() => { void signInWithGoogle() }}>Sign in with Google</button>

      <div className="mt-auto pt-6 text-center text-[13px] text-ink-3">Workers: ask your farm owner to add you.</div>
      <Footer className="pb-0" />
    </Screen>
  )
}
