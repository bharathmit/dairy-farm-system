import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AddRecordSheet, BottomNav, Footer, LogoMark, Screen } from '../components/ui'
import { Icon } from '../components/icons'
import AccountButton from '../components/AccountButton'
import { useRecords } from '../components/records'
import { useStore } from '../data/store'
import { dayLabel, L, todayIso, useLoad } from '../data/records'

/** Today at a glance: milk and health. The full dashboard comes in a later module. */
export default function Home() {
  const { farm } = useStore()
  const { rec } = useRecords()
  const today = todayIso()
  const day = useLoad(() => rec.day(today), [rec, today])
  const health = useLoad(() => rec.healthOverview(), [rec])
  const [adding, setAdding] = useState(false)
  const d = day.data
  const h = health.data
  const pending = d ? [!d.am.cows && 'morning', !d.pm.cows && new Date().getHours() >= 15 && 'evening'].filter(Boolean) : []

  return (
    <Screen>
      <div className="flex items-center gap-2 px-4 pb-2.5 pt-3.5">
        <LogoMark />
        <div className="flex-1"><h1 className="m-0 text-2xl font-bold">Today</h1><div className="text-[13px] text-ink-3">{farm?.name} · {dayLabel(today)}</div></div>
        <AccountButton />
      </div>
      <div className="flex flex-col gap-3 px-4 pb-4">
        <Link to="/milk" className="block rounded-[14px] bg-white p-4 text-ink no-underline hover:text-ink">
          <div className="flex items-center gap-2"><Icon name="drop" color="#1F5C3A" /><b className="flex-1 text-base">Milk</b><Icon name="chevron" size={18} color="#8A968D" /></div>
          {day.error ? <div className="mt-2 text-sm text-danger">{day.error}</div> : !d ? <div className="mt-2 text-sm text-ink-3">Loading…</div> : (
            <>
              <div className="mt-2 grid grid-cols-3 gap-2 text-center">
                <div><div className="text-xs font-semibold text-ink-3">Morning</div><div className="text-lg font-bold">{L(d.am.litres)}</div></div>
                <div><div className="text-xs font-semibold text-ink-3">Evening</div><div className="text-lg font-bold">{L(d.pm.litres)}</div></div>
                <div><div className="text-xs font-semibold text-ink-3">Day L</div><div className="text-lg font-bold text-brand">{L(d.totalLitres ?? 0)}</div></div>
              </div>
              {pending.length > 0 && <div className="mt-2.5 rounded-[10px] bg-warn-soft px-3 py-2 text-sm text-warn-text">{pending.join(' and ')} milking not logged yet</div>}
            </>
          )}
        </Link>
        <Link to="/health" className="block rounded-[14px] bg-white p-4 text-ink no-underline hover:text-ink">
          <div className="flex items-center gap-2"><Icon name="health" color="#A3271B" /><b className="flex-1 text-base">Health</b><Icon name="chevron" size={18} color="#8A968D" /></div>
          {health.error ? <div className="mt-2 text-sm text-danger">{health.error}</div> : !h ? <div className="mt-2 text-sm text-ink-3">Loading…</div> : (
            <div className="mt-2 grid grid-cols-3 gap-2 text-center">
              <div><div className="text-xs font-semibold text-ink-3">Withdrawal</div><div className={`text-lg font-bold ${h.inWithdrawal ? 'text-warn-ink' : ''}`}>{h.inWithdrawal}</div></div>
              <div><div className="text-xs font-semibold text-ink-3">Follow-ups</div><div className={`text-lg font-bold ${h.openFollowUps ? 'text-info-text' : ''}`}>{h.openFollowUps}</div></div>
              <div><div className="text-xs font-semibold text-ink-3">This month</div><div className="text-lg font-bold">{h.thisMonth}</div></div>
            </div>
          )}
        </Link>
        <div className="grid grid-cols-2 gap-3">
          <Link to="/breeding" className="flex items-center gap-2 rounded-[14px] bg-white p-4 text-ink no-underline hover:text-ink"><Icon name="heart" color="#7A4B0E" /><b className="flex-1 text-base">Breeding</b><Icon name="chevron" size={18} color="#8A968D" /></Link>
          <Link to="/calves" className="flex items-center gap-2 rounded-[14px] bg-white p-4 text-ink no-underline hover:text-ink"><Icon name="leaf" color="#1F5C3A" /><b className="flex-1 text-base">Calves</b><Icon name="chevron" size={18} color="#8A968D" /></Link>
        </div>
        <button type="button" onClick={() => setAdding(true)} className="btn gap-2"><Icon name="plus" color="#fff" size={20} />Add a record</button>
        <div className="rounded-[14px] bg-white p-4 text-sm text-ink-3">The full dashboard and KPIs come in a later module. <Link to="/animals" className="font-semibold">Go to the herd</Link></div>
      </div>
      <Footer className="mt-auto" />
      <BottomNav active="home" />
      {adding && <AddRecordSheet onClose={() => setAdding(false)} />}
    </Screen>
  )
}
