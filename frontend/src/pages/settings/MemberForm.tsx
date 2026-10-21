import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { ActionBar, PasswordInput, Screen, Segmented, TopBar } from '../../components/ui'
import { newId, useStore } from '../../data/store'
import type { Member, Role, Sex } from '../../data/types'

export default function MemberForm() {
  const { id } = useParams()
  const { members, saveMember, setMemberActive, notify } = useStore()
  const nav = useNavigate()
  const existing = id ? members.find((m) => m.id === id) : undefined

  const [m, setM] = useState<Member>(existing ?? {
    id: '', name: '', role: 'Worker', canSignIn: true, wageType: 'Daily', active: true,
  })
  const [pw, setPw] = useState('')
  const [pw2, setPw2] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (id && !existing) return <Navigate to="/settings/team" replace />

  const editing = Boolean(existing)
  const set = <K extends keyof Member>(k: K, v: Member[K]) => setM((s) => ({ ...s, [k]: v }))

  const ownPassword = editing && existing?.canSignIn && existing.loginType !== 'PASSWORD'

  const save = async (e: FormEvent) => {
    e.preventDefault()
    if (!m.name.trim()) return setError('Name is required.')
    if (m.canSignIn) {
      if (!m.email?.trim()) return setError('Email is needed so they can sign in.')
      if (!ownPassword && (!editing || !existing?.canSignIn || pw || pw2)) {
        if (pw.length < 8) return setError('Password must be at least 8 characters.')
        if (pw !== pw2) return setError('Passwords do not match.')
      }
    }
    setError(null)
    setBusy(true)
    const ok = await saveMember({ ...m, name: m.name.trim(), id: m.id || newId('m') }, pw || undefined)
    setBusy(false)
    if (!ok) return
    notify(editing ? 'Member updated' : 'Member added')
    nav('/settings/team')
  }

  const toggleActive = async () => {
    if (!(await setMemberActive(m.id, !m.active))) return
    notify(m.active ? 'Member deactivated' : 'Member reactivated')
    nav('/settings/team')
  }

  return (
    <Screen>
      <form onSubmit={save} className="flex flex-1 flex-col" noValidate>
        <TopBar title={editing ? 'Edit member' : 'Add member'} close="/settings/team" />
        <div className="flex flex-col gap-4 px-5 pb-4 pt-2">
          <div><span className="lab">Role</span>
            <Segmented<Role> label="Role" value={m.role} onChange={(v) => set('role', v)} options={[{ value: 'Owner', label: 'Owner' }, { value: 'Worker', label: 'Worker' }]} />
          </div>
          <div><label className="lab" htmlFor="nm">Name *</label><input className="inp" id="nm" value={m.name} onChange={(e) => set('name', e.target.value)} required /></div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="dob">Date of birth</label><input className="inp" id="dob" type="date" value={m.dob ?? ''} onChange={(e) => set('dob', e.target.value || undefined)} /></div>
            <div><label className="lab" htmlFor="sx">Sex</label>
              <select className="inp" id="sx" value={m.sex ?? ''} onChange={(e) => set('sex', (e.target.value || undefined) as Sex | undefined)}>
                <option value="">Select</option><option value="Male">Male</option><option value="Female">Female</option>
              </select>
            </div>
          </div>
          <div><label className="lab" htmlFor="ph">Phone</label><input className="inp" id="ph" type="tel" placeholder="+91" value={m.phone ?? ''} onChange={(e) => set('phone', e.target.value)} /></div>
          <div><label className="lab" htmlFor="em">Email (used to sign in)</label><input className="inp" id="em" type="email" placeholder="worker@example.com" value={m.email ?? ''} onChange={(e) => set('email', e.target.value)} /></div>
          <label className="flex items-start gap-3 rounded-xl border border-[#DDE1D9] bg-white p-3.5 text-[15px]">
            <input type="checkbox" checked={m.canSignIn} onChange={(e) => set('canSignIn', e.target.checked)} className="mt-px h-5 w-5 shrink-0 accent-brand" />
            <span><b className="font-semibold">Can sign in to the app</b><br /><span className="text-[13px] text-ink-3">Set a password below and share it with them yourself. Leave off for workers whose records you enter yourself.</span></span>
          </label>
          {m.canSignIn && ownPassword && (
            <div className="rounded-xl bg-track px-3.5 py-3 text-[13px] text-ink-2">{m.name} signs in with their own account and password.</div>
          )}
          {m.canSignIn && !ownPassword && (
            <>
              <div>
                <label className="lab" htmlFor="pw">Password{editing && existing?.canSignIn ? '' : ' *'}</label>
                <PasswordInput id="pw" value={pw} onChange={(e) => setPw(e.target.value)} placeholder={editing && existing?.canSignIn ? 'Leave blank to keep current password' : 'At least 8 characters'} autoComplete="new-password" />
              </div>
              <div>
                <label className="lab" htmlFor="pw2">Confirm password{editing && existing?.canSignIn ? '' : ' *'}</label>
                <input className="inp" id="pw2" type="password" value={pw2} onChange={(e) => setPw2(e.target.value)} placeholder="Re-enter password" autoComplete="new-password" />
              </div>
            </>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="wt">Wage type</label>
              <select className="inp" id="wt" value={m.wageType} onChange={(e) => set('wageType', e.target.value as Member['wageType'])}>
                <option>Daily</option><option>Monthly</option><option>None</option>
              </select>
            </div>
            <div><label className="lab" htmlFor="wr">Wage (₹)</label>
              <input className="inp" id="wr" inputMode="decimal" disabled={m.wageType === 'None'} value={m.wage ?? ''} onChange={(e) => set('wage', e.target.value ? Number(e.target.value.replace(/[^\d.]/g, '')) : undefined)} />
            </div>
          </div>
          <div><label className="lab" htmlFor="jd">Joined on</label><input className="inp" id="jd" type="date" value={m.joined ?? ''} onChange={(e) => set('joined', e.target.value || undefined)} /></div>
          {error && <div role="alert" className="rounded-[10px] bg-danger-soft px-3 py-2.5 text-sm text-danger-text">{error}</div>}
        </div>
        <ActionBar>
          {editing && (
            <button type="button" onClick={toggleActive} className={`h-[50px] flex-1 rounded-xl border border-line bg-white text-[15px] font-semibold ${m.active ? 'text-danger' : 'text-brand'}`}>
              {m.active ? 'Deactivate' : 'Reactivate'}
            </button>
          )}
          <button type="submit" className="btn flex-[2]" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
        </ActionBar>
      </form>
    </Screen>
  )
}
