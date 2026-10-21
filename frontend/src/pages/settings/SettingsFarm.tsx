import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ActionBar, Screen, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { useStore } from '../../data/store'

export default function SettingsFarm() {
  const { farm, updateFarm, notify } = useStore()
  const nav = useNavigate()
  const [f, setF] = useState({
    name: farm?.name ?? '', village: farm?.village ?? '', district: farm?.district ?? '', state: farm?.state ?? '', country: farm?.country ?? 'India',
  })
  const [located, setLocated] = useState(false)
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((s) => ({ ...s, [k]: e.target.value }))

  const save = (e: FormEvent) => {
    e.preventDefault()
    if (!f.name.trim()) { notify('Farm name is required'); return }
    updateFarm({ ...f, name: f.name.trim() })
    notify('Farm profile saved')
    nav('/settings')
  }

  return (
    <Screen>
      <form onSubmit={save} className="flex flex-1 flex-col">
        <TopBar title="Farm profile" back="/settings" />
        <div className="flex flex-col gap-4 px-5 py-2">
          <div><label className="lab" htmlFor="fn">Farm name *</label><input className="inp" id="fn" value={f.name} onChange={set('name')} required /></div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="vi">Village / town</label><input className="inp" id="vi" placeholder="[Village]" value={f.village} onChange={set('village')} /></div>
            <div><label className="lab" htmlFor="di">District</label><input className="inp" id="di" value={f.district} onChange={set('district')} /></div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="st">State</label><input className="inp" id="st" value={f.state} onChange={set('state')} /></div>
            <div><label className="lab" htmlFor="co">Country</label><input className="inp" id="co" value={f.country} onChange={set('country')} /></div>
          </div>
          <div>
            <span className="lab">Farm location</span>
            <div className="flex items-center gap-3 rounded-xl border border-line bg-white p-3.5">
              <Icon name="pin" color="#1F5C3A" />
              <div className="flex-1"><div className="text-[15px] font-medium">{located ? 'Saved' : 'Not set'}</div><div className="text-[13px] text-ink-3">Used for weather and heat-stress advice</div></div>
              <button type="button" onClick={() => { setLocated(true); notify('Location saved') }} className="h-11 rounded-[10px] border border-brand bg-white px-3.5 text-sm font-semibold text-brand">Use my location</button>
            </div>
          </div>
          <div className="text-[13px] leading-normal text-ink-3">Currency is fixed to INR (₹). Weights in kg, milk in litres.</div>
        </div>
        <ActionBar><button type="submit" className="btn">Save changes</button></ActionBar>
      </form>
    </Screen>
  )
}
