import { Link } from 'react-router-dom'
import { BottomNav, Footer, LogoMark, Screen } from '../../components/ui'
import { Icon } from '../../components/icons'
import AccountButton from '../../components/AccountButton'
import { useStore } from '../../data/store'

export default function GroupList() {
  const { groups, animals, members } = useStore()
  const active = animals.filter((a) => a.status === 'Active')
  const ungrouped = active.filter((a) => !a.groupId).length

  return (
    <Screen wide>
      <div className="flex items-center gap-2 px-4 pb-2.5 pt-3.5">
        <LogoMark />
        <h1 className="m-0 flex-1 text-2xl font-bold">Herd</h1>
        <AccountButton />
      </div>
      <nav aria-label="Herd views" className="mx-4 grid grid-cols-2 rounded-xl bg-track p-1">
        <Link to="/animals" className="flex h-[38px] items-center justify-center rounded-[9px] text-sm font-semibold text-ink-2 no-underline">Animals · {active.length}</Link>
        <Link to="/groups" aria-current="page" className="flex h-[38px] items-center justify-center rounded-[9px] bg-white text-sm font-semibold text-ink no-underline shadow-sm">Groups · {groups.length}</Link>
      </nav>

      <div className="flex flex-1 flex-col gap-2.5 p-4 pb-28">
        {groups.length === 0 && (
          <div className="rounded-[14px] bg-white p-5 text-center">
            <div className="font-bold">No groups yet</div>
            <p className="m-0 mt-1 text-sm text-ink-3">Group animals by shed, paddock or purpose to record for them together.</p>
          </div>
        )}
        {groups.map((g) => {
          const count = active.filter((a) => a.groupId === g.id).length
          const manager = members.find((m) => m.id === g.managerId)
          return (
            <Link key={g.id} to={`/groups/${g.id}`} className="flex items-center gap-3 rounded-[14px] bg-white p-3.5 text-ink no-underline hover:text-ink">
              <span className="h-11 w-3 shrink-0 rounded-md" style={{ background: g.color }} />
              <div className="min-w-0 flex-1">
                <div className="text-base font-bold">{g.name}</div>
                <div className="mt-0.5 text-[13px] text-ink-3">{[g.species, g.location, g.purpose].filter(Boolean).join(' · ')}</div>
                <div className="mt-1.5 flex flex-wrap gap-1.5">
                  <span className="badge bg-track text-ink-2">{count} {count === 1 ? 'animal' : 'animals'}</span>
                  {manager && <span className="badge bg-track text-ink-2">Manager: {manager.name}</span>}
                </div>
              </div>
              <Icon name="chevron" size={18} color="#8A968D" />
            </Link>
          )
        })}
        {groups.length > 0 && ungrouped > 0 && (
          <div className="mt-1 text-center text-[13px] text-ink-3">{ungrouped} {ungrouped === 1 ? 'animal is' : 'animals are'} not in any group</div>
        )}
      </div>

      <div className="sticky bottom-[72px] z-10 xl:bottom-0">
        <div className="pointer-events-none flex justify-end px-4 pb-3">
          <Link to="/groups/new" className="pointer-events-auto flex h-[52px] items-center gap-2 rounded-full bg-brand px-5 font-semibold text-white no-underline shadow-[0_6px_14px_rgba(31,92,58,.3)] hover:text-white">
            <Icon name="plus" size={20} color="#fff" strokeWidth={2.4} />New group
          </Link>
        </div>
        <Footer className="bg-ground py-2" />
      </div>
      <BottomNav active="more" />
    </Screen>
  )
}
