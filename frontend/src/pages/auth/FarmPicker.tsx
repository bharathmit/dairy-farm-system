import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Footer, LogoRow, Screen } from '../../components/ui'
import { Icon } from '../../components/icons'
import { useStore } from '../../data/store'

export default function FarmPicker() {
  const { user, farm, farms, chooseFarm, addFarm, signOut } = useStore()
  const nav = useNavigate()
  const [adding, setAdding] = useState(false)
  const [name, setName] = useState('')
  const selectedId = farm?.id ?? farms[0]?.id

  const add = async (e: FormEvent) => {
    e.preventDefault()
    if (!name.trim()) return
    if (!(await addFarm(name.trim()))) return
    setName('')
    setAdding(false)
  }

  return (
    <Screen className="px-5 pb-5 pt-4">
      <button type="button" aria-label="Back to login" onClick={() => { void signOut().then(() => nav('/login')) }} className="-ml-2.5 mb-2 flex h-11 w-11 items-center justify-center"><Icon name="back" /></button>
      <div className="mb-5"><LogoRow /></div>
      <p className="m-0 text-sm text-ink-3">Signed in as {user?.email}</p>
      <h1 className="mb-6 mt-1.5 text-[26px] font-bold">Choose a farm</h1>

      <div className="flex flex-col gap-3">
        {farms.length === 0 && <p className="m-0 rounded-xl bg-white p-4 text-[15px] text-ink-2">You're not on a farm yet. Add your farm below, or ask your farm owner to add you to theirs.</p>}
        {farms.map((f) => {
          const sel = f.id === selectedId
          const owner = f.role === 'Owner'
          return (
            <button
              key={f.id}
              type="button"
              onClick={() => { chooseFarm(f.id); nav('/animals') }}
              className={`box-border flex min-h-[72px] w-full items-center gap-3.5 rounded-[14px] bg-white p-4 text-left text-ink ${sel ? 'border-2 border-brand' : 'border border-[#DDE1D9]'}`}
            >
              <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl font-bold ${owner ? 'bg-brand-soft text-brand' : 'bg-[#F3E9D9] text-warn-ink'}`}>{f.initials}</div>
              <div className="min-w-0 flex-1">
                <div className="truncate text-base font-semibold">{f.name}</div>
                <div className="mt-0.5 text-[13px] text-ink-3">{[f.district, f.state].filter(Boolean).join(', ') || 'Location not set'} · {f.animals} animals</div>
              </div>
              <span className={`badge ${owner ? 'bg-brand text-white' : 'bg-track text-ink-2'}`}>{f.role}</span>
            </button>
          )
        })}
      </div>

      {adding ? (
        <form onSubmit={add} className="mt-4 flex gap-2">
          <label htmlFor="nf" className="sr-only">New farm name</label>
          <input id="nf" className="inp flex-1" placeholder="New farm name" value={name} onChange={(e) => setName(e.target.value)} autoFocus />
          <button type="submit" className="h-12 rounded-xl bg-brand px-4 font-semibold text-white">Add</button>
          <button type="button" onClick={() => setAdding(false)} className="h-12 rounded-xl border border-line bg-white px-3 font-semibold text-ink">Cancel</button>
        </form>
      ) : (
        <button type="button" onClick={() => setAdding(true)} className="mt-4 flex h-[50px] items-center justify-center gap-2 rounded-xl border border-dashed border-[#A9B3A6] bg-transparent text-[15px] font-semibold text-brand">
          <Icon name="plus" size={18} color="currentColor" strokeWidth={2.2} />Add a new farm
        </button>
      )}

      <p className="m-0 mt-auto pt-6 text-center text-[13px] leading-normal text-ink-3">Shown only when you belong to more than one farm.<br />Switch any time from the account menu.</p>
      <Footer className="pb-0" />
    </Screen>
  )
}
