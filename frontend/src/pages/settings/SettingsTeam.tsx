import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Footer, Screen, Segmented, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { inr, useStore } from '../../data/store'
import type { Member } from '../../data/types'

type Filter = 'all' | 'active' | 'inactive'

function subtitle(m: Member) {
  if (!m.active) return `Inactive since ${m.inactiveSince ?? '—'}`
  const parts: string[] = []
  const contact = m.email || m.phone
  if (m.canSignIn) parts.push(contact ? `${contact} · signs in` : 'Signs in')
  else parts.push(m.phone ? `${m.phone} · No login` : 'No login')
  if (m.wageType !== 'None' && m.wage) parts.push(`${inr(m.wage)}/${m.wageType === 'Daily' ? 'day' : 'month'}`)
  return parts.join(' · ')
}

function MemberRow({ m }: { m: Member }) {
  const owner = m.role === 'Owner'
  const avatar = !m.active ? 'bg-[#ECECEC] text-ink-3' : owner ? (m.isMe ? 'bg-brand text-white' : 'bg-brand-soft text-brand') : 'bg-[#F3E9D9] text-warn-ink'
  const tag = !m.active ? <span className="badge bg-track text-ink-3">Inactive</span>
    : owner ? <span className="badge bg-brand text-white">Owner</span>
      : <span className="badge bg-track text-ink-2">Worker</span>
  const body = (
    <>
      <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full font-bold ${avatar}`}>{m.name.replace(/^\[/, '').charAt(0).toUpperCase()}</div>
      <div className="min-w-0 flex-1">
        <div className={`truncate font-semibold ${m.active ? '' : 'text-ink-3'}`}>{m.name}{m.isMe && <span className="font-normal text-ink-3"> (you)</span>}</div>
        <div className="truncate text-[13px] text-ink-3">{subtitle(m)}</div>
      </div>
      {tag}
    </>
  )
  const cls = 'flex min-h-[68px] items-center gap-3 border-t border-line-faint px-4 text-ink no-underline first:border-t-0'
  return m.isMe ? <div className={cls}>{body}</div> : <Link to={`/settings/team/${m.id}`} className={cls}>{body}</Link>
}

export default function SettingsTeam() {
  const { members } = useStore()
  const [filter, setFilter] = useState<Filter>('all')
  const activeN = members.filter((m) => m.active).length
  const shown = members.filter((m) => (filter === 'all' ? true : filter === 'active' ? m.active : !m.active))
  const owners = shown.filter((m) => m.role === 'Owner')
  const workers = shown.filter((m) => m.role === 'Worker')

  return (
    <Screen>
      <TopBar title="Team" back="/settings" />
      <Segmented
        label="Filter members"
        className="mx-4 mt-1"
        value={filter}
        onChange={setFilter}
        options={[
          { value: 'all', label: `All (${members.length})` },
          { value: 'active', label: `Active (${activeN})` },
          { value: 'inactive', label: `Inactive (${members.length - activeN})` },
        ]}
      />

      {owners.length > 0 && <><div className="sec">Owners · full access</div><div className="grp">{owners.map((m) => <MemberRow key={m.id} m={m} />)}</div></>}
      {workers.length > 0 && <><div className="sec">Workers · daily records</div><div className="grp">{workers.map((m) => <MemberRow key={m.id} m={m} />)}</div></>}
      {shown.length === 0 && <p className="mx-5 mt-6 text-center text-sm text-ink-3">No members match this filter.</p>}

      <p className="mx-5 mb-4 mt-3.5 text-[13px] leading-normal text-ink-3">Workers can add milk, feed, health and weight records and see health and feeding reminders. They can't delete records, see finances or change settings.</p>

      <div className="sticky bottom-0 mt-auto bg-ground">
        <div className="px-5 pt-3">
          <Link to="/settings/team/new" className="btn gap-2"><Icon name="plus" size={18} color="#fff" strokeWidth={2.4} />Add member</Link>
        </div>
        <Footer />
      </div>
    </Screen>
  )
}
