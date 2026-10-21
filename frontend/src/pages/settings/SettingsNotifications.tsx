import type { ReactNode } from 'react'
import { Footer, Screen, Switch, TopBar } from '../../components/ui'
import { useStore } from '../../data/store'
import type { Settings } from '../../data/types'

type Notify = Settings['notify']

const LEADS = ['Same day', '1 day before', '3 days', '7 days']

function to12h(t: string) {
  const [h, m] = t.split(':').map(Number)
  if (Number.isNaN(h)) return t
  return `${((h + 11) % 12) + 1}:${String(m ?? 0).padStart(2, '0')} ${h < 12 ? 'AM' : 'PM'}`
}

function Row({ title, sub, children, big }: { title: string; sub?: string; children: ReactNode; big?: boolean }) {
  return (
    <div className="flex min-h-[58px] items-center gap-3 border-t border-line-faint px-4 py-2 first:border-t-0">
      <span className="flex-1">
        <span className={`block ${big ? 'text-base font-semibold' : 'text-[15px] font-medium'}`}>{title}</span>
        {sub && <span className="text-[13px] text-ink-3">{sub}</span>}
      </span>
      {children}
    </div>
  )
}

export default function SettingsNotifications() {
  const { settings, updateSettings } = useStore()
  const n = settings.notify
  const set = <K extends keyof Notify>(k: K, v: Notify[K]) => { void updateSettings({ notify: { ...n, [k]: v } }) }

  return (
    <Screen>
      <TopBar title="Notifications" back="/settings" />
      <div className="grp"><Row title="Enable reminders" big><Switch label="Enable reminders" checked={n.enabled} onChange={(v) => set('enabled', v)} /></Row></div>

      <div className={n.enabled ? '' : 'pointer-events-none opacity-50'} aria-disabled={!n.enabled}>
        <div className="sec">What to remind me about</div>
        <div className="grp">
          <Row title="Health follow-ups" sub="Next-action dates, vaccinations, deworming"><Switch label="Health follow-ups" checked={n.health} onChange={(v) => set('health', v)} /></Row>
          <Row title="Breeding & calving" sub="Heat, pregnancy check, due dates, dry-off"><Switch label="Breeding & calving" checked={n.breeding} onChange={(v) => set('breeding', v)} /></Row>
          <Row title="Daily checklist" sub="Evening nudge for unfinished tasks"><Switch label="Daily checklist" checked={n.checklist} onChange={(v) => set('checklist', v)} /></Row>
        </div>

        <div className="sec">How early</div>
        <div role="radiogroup" aria-label="How early" className="flex flex-wrap gap-2 px-4">
          {LEADS.map((l) => (
            <button key={l} type="button" role="radio" aria-checked={n.lead === l} onClick={() => set('lead', l)} className={`chip ${n.lead === l ? 'chip-on' : ''}`}>{l}</button>
          ))}
        </div>

        <div className="sec">When</div>
        <div className="grp">
          <Row title="Reminder time">
            <input type="time" aria-label="Reminder time" value={n.reminderTime} onChange={(e) => set('reminderTime', e.target.value)} className="h-11 rounded-lg border-0 bg-transparent text-right text-[15px] font-semibold text-brand" />
          </Row>
          <Row title="Morning digest" sub="One summary of today's tasks">
            <span className="mr-2 text-[15px] font-semibold text-brand">{to12h(n.digestTime)}</span>
            <Switch label="Morning digest" checked={n.digest} onChange={(v) => set('digest', v)} />
          </Row>
          <Row title="Quiet hours"><span className="text-[15px] font-semibold text-brand">{n.quietHours}</span></Row>
        </div>
      </div>
      <div className="flex-1" />
      <Footer />
    </Screen>
  )
}
