import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Footer, LogoRow, PasswordInput, Screen } from '../../components/ui'
import { useStore } from '../../data/store'

export default function CreateAccount() {
  const { signUp } = useStore()
  const nav = useNavigate()
  const [f, setF] = useState({ name: '', farm: '', email: '', phone: '', pw: '', pw2: '' })
  const [terms, setTerms] = useState(false)
  const [error, setError] = useState('')
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((s) => ({ ...s, [k]: e.target.value }))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!f.name.trim() || !f.farm.trim() || !f.email.trim() || !f.pw) return setError('Fill in all fields marked *.')
    if (f.pw.length < 8) return setError('Password must be at least 8 characters.')
    if (f.pw !== f.pw2) return setError('Passwords do not match.')
    if (!terms) return setError('Please agree to the Terms and Privacy policy.')
    signUp(f.name.trim(), f.email.trim(), f.farm.trim())
    nav('/farms')
  }

  return (
    <Screen className="px-6 pb-5 pt-14">
      <LogoRow right={<Link to="/login" className="flex min-h-11 items-center px-1 text-sm font-semibold">Sign in</Link>} />
      <h1 className="mb-1.5 mt-10 text-[28px] font-bold">Create your account</h1>
      <p className="m-0 mb-6 text-[15px] text-ink-3">For farm owners. You can add workers after setup.</p>

      <form onSubmit={submit} noValidate className="flex flex-col">
        <div className="flex flex-col gap-4">
          <div><label className="lab" htmlFor="fn">Full name *</label><input className="inp" id="fn" autoComplete="name" placeholder="Your name" value={f.name} onChange={set('name')} /></div>
          <div><label className="lab" htmlFor="fm">Farm name *</label><input className="inp" id="fm" placeholder="e.g. Villupuram Dairy" value={f.farm} onChange={set('farm')} /></div>
          <div><label className="lab" htmlFor="em">Email *</label><input className="inp" id="em" type="email" autoComplete="email" placeholder="you@example.com" value={f.email} onChange={set('email')} /></div>
          <div><label className="lab" htmlFor="ph">Phone</label><input className="inp" id="ph" type="tel" autoComplete="tel" placeholder="+91" value={f.phone} onChange={set('phone')} /></div>
          <div><label className="lab" htmlFor="pw">Password *</label><PasswordInput id="pw" autoComplete="new-password" placeholder="At least 8 characters" value={f.pw} onChange={set('pw')} /></div>
          <div><label className="lab" htmlFor="pw2">Confirm password *</label><input className="inp" id="pw2" type="password" autoComplete="new-password" placeholder="Re-enter password" value={f.pw2} onChange={set('pw2')} /></div>
          <label className="flex min-h-11 items-center gap-2.5 text-sm text-ink-2">
            <input type="checkbox" checked={terms} onChange={(e) => setTerms(e.target.checked)} className="h-5 w-5 shrink-0 accent-brand" />
            <span>I agree to the <a href="#">Terms</a> and <a href="#">Privacy policy</a></span>
          </label>
        </div>
        {error && <p role="alert" className="m-0 mb-1 text-sm font-medium text-danger-text">{error}</p>}
        <button type="submit" className="btn mt-2">Create account</button>
      </form>

      <div className="my-5 flex items-center gap-3 text-[13px] text-ink-3"><div className="h-px flex-1 bg-[#D3D8CF]" />or<div className="h-px flex-1 bg-[#D3D8CF]" /></div>
      <button type="button" className="btn2" onClick={() => { signUp('Bharath', 'bharath@example.com', 'Villupuram Dairy'); nav('/farms') }}>Sign up with Google</button>

      <div className="mt-auto pt-6 text-center text-sm text-ink-3">Already have an account? <Link to="/login" className="font-semibold">Sign in</Link></div>
      <Footer className="pb-0" />
    </Screen>
  )
}
