import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Dialog, Footer, LogoMark, Screen, TopBar } from '../../components/ui'
import { Icon, type IconName } from '../../components/icons'
import { daysInMilk, toneClass, useStore } from '../../data/store'

export default function GroupDetail() {
  const { id } = useParams()
  const nav = useNavigate()
  const { groups, animals, members, deleteGroup, notify } = useStore()
  const [showAll, setShowAll] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const g = groups.find((x) => x.id === id)

  if (!g) {
    return (
      <Screen>
        <TopBar title="Group not found" back="/groups" />
        <p className="px-5 text-ink-3">This group doesn't exist or was deleted. <Link to="/groups" className="font-semibold">Back to groups</Link></p>
        <div className="flex-1" />
        <Footer />
      </Screen>
    )
  }

  const list = animals.filter((a) => a.groupId === g.id && a.status === 'Active')
  const manager = members.find((m) => m.id === g.managerId)
  const milk = list.reduce((s, a) => s + (a.milkYesterday ?? 0), 0)
  const alerts = list.filter((a) => a.flags.some((f) => f.tone === 'red' || f.tone === 'amber')).length
  const shown = showAll ? list : list.slice(0, 3)

  const tile = (icon: IconName, color: string, label: string) => (
    <button type="button" onClick={() => notify('Coming in a later module')} className="flex h-[76px] flex-col items-center justify-center gap-1.5 rounded-xl border-0 bg-white text-[13px] font-semibold text-ink">
      <Icon name={icon} color={color} />{label}
    </button>
  )

  return (
    <Screen>
      <div className="flex items-center px-2 pt-2">
        <Link to="/groups" aria-label="Back" className="flex h-11 w-11 items-center justify-center rounded-xl"><Icon name="back" /></Link>
        <LogoMark />
        <div className="flex-1" />
        <Link to={`/groups/${g.id}/edit`} aria-label="Edit group" className="flex h-11 w-11 items-center justify-center rounded-xl"><Icon name="edit" size={20} /></Link>
      </div>
      <div className="px-4">
        <div className="flex items-center gap-2.5"><span className="h-3.5 w-3.5 shrink-0 rounded-full" style={{ background: g.color }} /><h1 className="m-0 text-2xl font-bold">{g.name}</h1></div>
        <div className="mt-1 text-sm text-ink-3">{[g.species, g.location, g.purpose, manager ? `Manager: ${manager.name}` : undefined].filter(Boolean).join(' · ')}</div>
        {g.notes && <p className="m-0 mt-2 text-sm text-ink-2">{g.notes}</p>}

        <div className="mt-4 grid grid-cols-3 gap-2.5">
          <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Animals</div><div className="mt-0.5 text-xl font-bold">{list.length}</div></div>
          <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Milk / day</div><div className="mt-0.5 text-xl font-bold">{Math.round(milk * 10) / 10} L</div></div>
          <div className="rounded-xl bg-white p-3"><div className="text-xs font-semibold text-ink-3">Alerts</div><div className={`mt-0.5 text-xl font-bold ${alerts ? 'text-danger' : ''}`}>{alerts}</div></div>
        </div>

        <div className="mb-2.5 mt-5 text-xs font-bold uppercase tracking-[.06em] text-ink-3">Record for the whole group</div>
        <div className="grid grid-cols-3 gap-2.5">
          {tile('health', '#A3271B', 'Batch health')}
          {tile('drop', '#1F5C3A', 'Milking sheet')}
          {tile('leaf', '#7A4B0E', 'Feed group')}
        </div>
        <div className="mt-1.5 text-xs text-ink-3">These open in their own modules later.</div>

        <div className="mb-2.5 mt-5 flex items-baseline justify-between">
          <div className="text-xs font-bold uppercase tracking-[.06em] text-ink-3">Members</div>
          <Link to={`/groups/${g.id}/edit`} className="flex min-h-11 items-center text-sm font-semibold">Add / remove</Link>
        </div>
        <div className="overflow-hidden rounded-[14px] bg-white">
          {list.length === 0 && <div className="px-3.5 py-4 text-center text-sm text-ink-3">No animals in this group yet.</div>}
          {shown.map((a, i) => {
            const dim = daysInMilk(a.lastCalving)
            const meta = [a.breed, a.category === 'Milking' && dim !== undefined ? `DIM ${dim}` : a.category, a.milkYesterday ? `${a.milkYesterday} L` : undefined].filter(Boolean).join(' · ')
            return (
              <Link key={a.id} to={`/animals/${a.id}`} className={`flex min-h-[60px] items-center gap-3 px-3.5 py-2 text-ink no-underline hover:text-ink ${i ? 'border-t border-line-faint' : ''}`}>
                <span className="flex-1"><b>{a.tag}</b> {a.name}<br /><span className="text-[13px] text-ink-3">{meta}</span></span>
                {a.flags[0] && <span className={`badge ${toneClass[a.flags[0].tone]}`}>{a.flags[0].text}</span>}
              </Link>
            )
          })}
          {list.length > 3 && (
            <button type="button" onClick={() => setShowAll((s) => !s)} className="flex min-h-[60px] w-full items-center justify-center border-0 border-t border-line-faint bg-transparent font-semibold text-brand">
              {showAll ? 'Show fewer' : `See all ${list.length}`}
            </button>
          )}
        </div>
        <button type="button" onClick={() => setConfirm(true)} className="mt-5 h-[46px] w-full border-0 bg-transparent text-[15px] font-semibold text-danger">Delete group (animals stay in herd)</button>
      </div>
      <div className="flex-1" />
      <Footer />

      {confirm && (
        <Dialog title="Delete group?" onClose={() => setConfirm(false)}>
          <p className="m-0 mb-5 text-[15px] leading-normal text-ink-2">"{g.name}" will be deleted. Its {list.length} {list.length === 1 ? 'animal stays' : 'animals stay'} in the herd without a group.</p>
          <div className="grid grid-cols-2 gap-3">
            <button type="button" onClick={() => setConfirm(false)} className="h-12 rounded-xl border border-line bg-white font-semibold text-ink">Cancel</button>
            <button type="button" onClick={() => { deleteGroup(g.id); notify('Group deleted'); nav('/groups', { replace: true }) }} className="h-12 rounded-xl bg-danger font-semibold text-white">Delete</button>
          </div>
        </Dialog>
      )}
    </Screen>
  )
}
