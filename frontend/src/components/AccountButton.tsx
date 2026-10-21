import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useStore } from '../data/store'
import { Dialog, Footer, Sheet } from './ui'
import { Icon } from './icons'

export function LogoutDialog({ onClose }: { onClose: () => void }) {
  const { signOut } = useStore()
  const nav = useNavigate()
  return (
    <Dialog title="Log out?" onClose={onClose}>
      <p className="m-0 mb-5 text-[15px] leading-normal text-ink-2">You'll need to sign in again to use the app on this phone.</p>
      <div className="grid grid-cols-2 gap-3">
        <button type="button" onClick={onClose} className="h-12 rounded-xl border border-line bg-white font-semibold text-ink">Cancel</button>
        <button type="button" onClick={() => { void signOut().then(() => nav('/login')) }} className="h-12 rounded-xl bg-danger font-semibold text-white">Log out</button>
      </div>
    </Dialog>
  )
}

/** Avatar in list headers; opens the account menu sheet. */
export default function AccountButton() {
  const { user, farm } = useStore()
  const [open, setOpen] = useState(false)
  const [logout, setLogout] = useState(false)
  const initial = (user?.name ?? 'B').charAt(0).toUpperCase()
  const row = 'flex min-h-14 items-center gap-3.5 px-5 text-base font-medium text-ink no-underline'
  return (
    <>
      <button type="button" aria-label="Account" onClick={() => setOpen(true)} className="flex h-10 w-10 items-center justify-center rounded-full bg-brand font-bold text-white">{initial}</button>
      {open && (
        <Sheet label="Account menu" onClose={() => setOpen(false)}>
          <div className="flex items-center gap-3.5 border-b border-[#ECEEE8] px-5 pb-4 pt-1">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-brand text-lg font-bold text-white">{initial}</div>
            <div><div className="text-[17px] font-bold">{user?.name}</div><div className="text-[13px] text-ink-3">{user?.email} · {farm?.role}</div></div>
          </div>
          <Link to="/farms" className={row}><Icon name="farm" color="#3B4A41" /><div className="flex-1"><div>{farm?.name}</div><div className="text-[13px] font-normal text-ink-3">Current farm</div></div><span className="text-sm font-semibold text-brand">Switch</span></Link>
          <Link to="/settings" className={row}><Icon name="gear" color="#3B4A41" /><div className="flex-1">Settings</div></Link>
          <Link to="/home" className={row}><Icon name="help" color="#3B4A41" /><div className="flex-1">Help</div></Link>
          <button type="button" onClick={() => { setOpen(false); setLogout(true) }} className={`${row} w-full border-0 bg-transparent text-danger`}><Icon name="logout" color="#A3271B" /><div className="flex-1 text-left">Log out</div></button>
          <Footer />
        </Sheet>
      )}
      {logout && <LogoutDialog onClose={() => setLogout(false)} />}
    </>
  )
}
