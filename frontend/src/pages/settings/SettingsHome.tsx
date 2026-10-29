import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { LogoMark, Screen } from '../../components/ui'
import { Icon, type IconName } from '../../components/icons'
import { LogoutDialog } from '../../components/AccountButton'
import { useStore } from '../../data/store'

function Row({ to, icon, title, sub, muted }: { to: string; icon: IconName; title: string; sub: ReactNode; muted?: boolean }) {
  return (
    <Link to={to} className={`flex min-h-[60px] items-center gap-3.5 border-t border-line-faint px-4 text-ink no-underline first:border-t-0 ${muted ? 'opacity-70' : ''}`}>
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] bg-brand-soft"><Icon name={icon} size={20} color="#1F5C3A" /></span>
      <div className="flex-1"><div className="text-base font-medium">{title}</div><div className="text-[13px] text-ink-3">{sub}</div></div>
      <Icon name="chevron" size={18} color="#8A968D" />
    </Link>
  )
}

export default function SettingsHome() {
  const { farm, members, settings } = useStore()
  const [logout, setLogout] = useState(false)
  const active = members.filter((m) => m.active)
  const owners = active.filter((m) => m.role === 'Owner').length
  const workers = active.filter((m) => m.role === 'Worker').length
  const lang = settings.language === 'Tamil' ? 'தமிழ்' : 'English'
  return (
    <Screen>
      <div className="px-5 pb-1 pt-4">
        <div className="flex items-center gap-2.5"><LogoMark /><h1 className="m-0 text-[26px] font-bold">Settings</h1></div>
        <div className="mt-0.5 text-sm text-ink-3">{farm?.name}</div>
      </div>

      <div className="sec">Farm</div>
      <div className="grp">
        <Row to="/settings/farm" icon="home" title="Farm profile" sub="Name, address, location" />
        <Row to="/settings/team" icon="team" title="Team" sub={`${owners} owner${owners === 1 ? '' : 's'} · ${workers} worker${workers === 1 ? '' : 's'}`} />
        <Row to="/settings/milk-rate" icon="drop" title="Milking & Feeding Margins" sub={`₹${settings.milkRate.toFixed(2)} / L · alert below ${settings.marginAlertPct} % margin`} />
        <Row to="/settings/milk-grading" icon="check" title="Milk grade rules" sub={farm?.role === 'Owner' ? 'Expected yield by lactation day, A–D grades' : 'See how grades are worked out'} />
        <Row to="/settings/contacts" icon="team" title="Contacts" sub="Milk buyers, vets, AI technicians, suppliers" />
      </div>

      <div className="sec">Records</div>
      <div className="grp">
        <Row to="/animals" icon="tag" title="Herd" sub="Animals, groups, tags" />
        <Row to="/milk" icon="drop" title="Milk" sub="Today, sheets, sales" />
        <Row to="/health" icon="health" title="Health" sub="Treatments, withdrawals, follow-ups" />
        <Row to="/breeding" icon="heart" title="Breeding" sub="Heat, services, pregnancy, calving" />
        <Row to="/calves" icon="leaf" title="Calves" sub="Rearing and feeding" />
        <Row to="/breeding/semen" icon="drop" title="Semen stock" sub="Straws, expiry" />
        <Row to="/feed" icon="leaf" title="Feed" sub="Stock, feeding, batches, ration plan" />
        <Row to="/feed/items" icon="list" title="Feed items" sub="Nutrients, limits, reorder, suppliers" />
        <Row to="/feed/ration-rules" icon="check" title="Ration rules" sub="Daily ration by animal category" />
      </div>

      {farm?.role === 'Owner' && (
        <>
          <div className="sec">Money &amp; team</div>
          <div className="grp">
            <Row to="/money" icon="farm" title="Finance" sub="Income, expenses, profit per animal" />
            <Row to="/money/workers" icon="team" title="Workers & wages" sub="Attendance, pay, advances" />
            <Row to="/money/equipment" icon="gear" title="Equipment" sub="Service schedule and costs" />
            <Row to="/money/procurement" icon="list" title="Procurement" sub="What to buy, ordered, received" />
            <Row to="/reports" icon="list" title="Reports" sub="Exports, cull advisor, compare cows" />
          </div>
        </>
      )}

      <div className="sec">Alerts &amp; tasks</div>
      <div className="grp">
        <Row to="/alerts" icon="bell" title="Alerts" sub="Open alerts and reminders" />
        {farm?.role === 'Owner' && (
          <>
            <Row to="/settings/alert-rules" icon="bell" title="Alert rules" sub="What raises an alert, who sees it" />
            <Row to="/settings/health-schedules" icon="health" title="Health schedules" sub="Deworming and vaccination by age" />
            <Row to="/checklist" icon="check" title="Checklist tasks" sub="Daily built-in and custom tasks" />
          </>
        )}
      </div>

      <div className="sec">App</div>
      <div className="grp">
        <Row to="/settings/notifications" icon="bell" title="Notifications" sub={settings.notify.enabled ? `Reminders ${settings.notify.lead.toLowerCase()} · ${settings.notify.digestTime} digest` : 'Reminders off'} />
        <Row to="/settings/preferences" icon="sun" title="Theme & language" sub={`${settings.theme} · ${lang}`} />
      </div>

      <div className="grp mt-5">
        <button type="button" onClick={() => setLogout(true)} className="flex min-h-[60px] w-full items-center justify-center border-0 bg-transparent font-semibold text-danger">Log out</button>
      </div>
      <div className="mb-4 mt-3 text-center text-xs text-ink-3">Version 1.0.0<br />© 2026 Dairy Farm. All rights reserved.</div>
      <div className="flex-1" />
      {logout && <LogoutDialog onClose={() => setLogout(false)} />}
    </Screen>
  )
}
