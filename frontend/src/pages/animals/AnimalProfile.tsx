import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Dialog, Footer, LogoMark, Screen, Segmented, Sheet, TopBar } from '../../components/ui'
import { Icon, type IconName } from '../../components/icons'
import { ageText, daysInMilk, fmtDate, inr, toneClass, useStore } from '../../data/store'
import type { Animal } from '../../data/types'
import { HealthTab, MilkTab } from './ProfileTabs'
import { FeedGroupBadge, useFeedGroups } from '../../components/feed'

type ExitStatus = 'Sold' | 'Dead' | 'Transferred'
const REASONS = ['Low milk yield (cull)', 'Fertility problem (cull)', 'Age / replacement', 'Male calf sale', 'Illness', 'Other']

function StatusSheet({ animal, onClose }: { animal: Animal; onClose: () => void }) {
  const { groups, changeAnimalStatus, moveAnimal, deleteAnimal, notify } = useStore()
  const nav = useNavigate()
  const [status, setStatus] = useState<ExitStatus>('Sold')
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10))
  const [price, setPrice] = useState('')
  const [reason, setReason] = useState(REASONS[0])
  const [buyer, setBuyer] = useState('')
  const [moving, setMoving] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const act = 'flex min-h-[52px] w-full items-center gap-3 border-0 bg-transparent p-0 text-left text-[15px] font-medium text-ink'

  const copyTag = async () => {
    try { await navigator.clipboard.writeText(animal.tag); notify(`Copied ${animal.tag}`) } catch { notify('Could not copy') }
  }

  const markExit = async () => {
    const p = price.trim() === '' ? undefined : Number(price.replace(/,/g, ''))
    if (!(await changeAnimalStatus(animal.id, status, { date, price: status === 'Sold' ? p : undefined, reason, buyer: buyer.trim() || undefined }))) return
    notify(`${animal.tag} marked as ${status.toLowerCase()}`)
    nav('/animals', { replace: true })
  }

  return (
    <>
      <Sheet label="Animal actions" onClose={onClose}>
        <div className="overflow-y-auto px-5 pb-1">
          <div className="flex items-center gap-2.5"><LogoMark /><div className="text-[13px] text-ink-3">{animal.tag}{animal.name ? ` · ${animal.name}` : ''}</div></div>
          <div className="mb-1.5 border-b border-[#ECEEE8]">
            <button type="button" className={act} onClick={copyTag}><Icon name="copy" size={20} color="#3B4A41" />Copy tag</button>
            <button type="button" className={act} aria-expanded={moving} onClick={() => setMoving((m) => !m)}><Icon name="arrow" size={20} color="#3B4A41" />Move to group</button>
            {moving && (
              <div className="pb-3">
                <label className="lab" htmlFor="mg">Group</label>
                <select
                  id="mg"
                  className="inp"
                  value={animal.groupId ?? ''}
                  onChange={async (e) => {
                    const g = groups.find((x) => x.id === e.target.value)
                    if (!(await moveAnimal(animal.id, g?.id))) return
                    notify(g ? `Moved to ${g.name}` : 'Removed from group')
                    onClose()
                  }}
                >
                  {groups.filter((g) => g.species === animal.species).map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
                  <option value="">No group</option>
                </select>
              </div>
            )}
          </div>
          {animal.status === 'Active' ? (
            <>
              <h2 className="my-3 text-lg font-bold">Change status</h2>
              <Segmented<ExitStatus> label="Status" value={status} onChange={setStatus} options={[{ value: 'Sold', label: 'Sold' }, { value: 'Dead', label: 'Dead' }, { value: 'Transferred', label: 'Transferred' }]} />
              <div className="mt-3.5 grid grid-cols-2 gap-3">
                <div><label className="lab" htmlFor="ed">Date</label><input className="inp" id="ed" type="date" value={date} onChange={(e) => setDate(e.target.value)} /></div>
                {status === 'Sold' && <div><label className="lab" htmlFor="ep">Sale price (₹)</label><input className="inp" id="ep" inputMode="decimal" placeholder="0" value={price} onChange={(e) => setPrice(e.target.value)} /></div>}
              </div>
              <div className="mt-3.5"><label className="lab" htmlFor="er">Reason</label><select className="inp" id="er" value={reason} onChange={(e) => setReason(e.target.value)}>{REASONS.map((r) => <option key={r}>{r}</option>)}</select></div>
              {status !== 'Dead' && <div className="mt-3.5"><label className="lab" htmlFor="eb">{status === 'Sold' ? 'Buyer' : 'Transferred to'}</label><input className="inp" id="eb" placeholder="Optional" value={buyer} onChange={(e) => setBuyer(e.target.value)} /></div>}
              <div className="mt-2.5 text-[13px] text-ink-3">{status === 'Sold' ? 'A sale adds an income entry in Finance. ' : ''}History stays on the animal.</div>
              <button type="button" className="btn mt-4" onClick={markExit}>Mark as {status.toLowerCase()}</button>
            </>
          ) : (
            <>
              <div className="my-3 rounded-xl bg-track p-3 text-sm text-ink-2">This animal is <b>{animal.status.toLowerCase()}</b>{animal.exit ? ` since ${fmtDate(animal.exit.date)}${animal.exit.reason ? ` · ${animal.exit.reason}` : ''}` : ''}.</div>
              <button type="button" className="btn2" onClick={async () => { if (await changeAnimalStatus(animal.id, 'Active')) { notify(`${animal.tag} is active again`); onClose() } }}>Mark as active again</button>
            </>
          )}
          <button type="button" onClick={() => setConfirmDelete(true)} className="mt-2 h-11 w-full border-0 bg-transparent text-[15px] font-semibold text-danger">Delete animal (owner only)</button>
        </div>
        <Footer className="pt-1.5" />
      </Sheet>
      {confirmDelete && (
        <Dialog title={`Delete ${animal.tag}?`} onClose={() => setConfirmDelete(false)}>
          <p className="m-0 mb-5 text-[15px] leading-normal text-ink-2">This removes the animal and its records for good. To keep history, mark it as sold, dead or transferred instead.</p>
          <div className="grid grid-cols-2 gap-3">
            <button type="button" onClick={() => setConfirmDelete(false)} className="h-12 rounded-xl border border-line bg-white font-semibold text-ink">Cancel</button>
            <button type="button" onClick={async () => { if (await deleteAnimal(animal.id)) { notify(`${animal.tag} deleted`); nav('/animals', { replace: true }) } }} className="h-12 rounded-xl bg-danger font-semibold text-white">Delete</button>
          </div>
        </Dialog>
      )}
    </>
  )
}

const QUICK: { label: string; icon: IconName; color: string }[] = [
  { label: 'Milk', icon: 'drop', color: '#1F5C3A' },
  { label: 'Health', icon: 'health', color: '#A3271B' },
  { label: 'Weight', icon: 'weight', color: '#1F4E87' },
  { label: 'Breeding', icon: 'heart', color: '#7A4B0E' },
]
const TABS = ['Overview', 'Health', 'Breeding', 'Milk', 'Weight', 'Family']

export default function AnimalProfile() {
  const { id } = useParams()
  const { animals, groups, loading, notify } = useStore()
  const [sheet, setSheet] = useState(false)
  const [tab, setTab] = useState('Overview')
  const feedGroups = useFeedGroups()
  const nav = useNavigate()
  const a = animals.find((x) => x.id === id)
  const later = () => notify('Coming in a later module')

  if (!a && loading) {
    return <Screen wide><TopBar title="Animal" back="/animals" /><p className="px-5 text-ink-3" role="status">Loading…</p></Screen>
  }
  if (!a) {
    return (
      <Screen wide>
        <TopBar title="Animal" back="/animals" />
        <div className="card mt-4"><div className="font-bold">Animal not found</div><p className="m-0 mt-1 text-sm text-ink-3">It may have been deleted.</p><Link to="/animals" className="mt-2 inline-block font-semibold">Back to herd</Link></div>
        <div className="flex-1" /><Footer />
      </Screen>
    )
  }

  const group = groups.find((g) => g.id === a.groupId)
  const dim = daysInMilk(a.lastCalving)
  const offspring = animals.filter((x) => x.dam && x.dam === a.tag)
  const ib = 'flex h-11 w-11 items-center justify-center rounded-xl'
  const kv = (k: string, v: string) => (
    <div key={k}><div className="text-xs font-semibold text-ink-3">{k}</div><div className="mt-0.5 text-[15px] font-semibold">{v}</div></div>
  )
  const stage = dim === undefined ? '' : dim <= 100 ? ' · early' : dim <= 200 ? ' · mid' : ' · late'

  return (
    <Screen wide>
      <div className="flex items-center gap-1 px-2 pt-2">
        <Link className={ib} to="/animals" aria-label="Back"><Icon name="back" /></Link>
        <LogoMark />
        <div className="flex-1" />
        <Link className={ib} to={`/animals/${a.id}/edit`} aria-label="Edit animal"><Icon name="edit" size={20} /></Link>
        <button type="button" className={ib} aria-label="More actions" onClick={() => setSheet(true)}><Icon name="more" size={20} strokeWidth={3.5} /></button>
      </div>

      <div className="flex items-center gap-3.5 px-4 pb-4 pt-1">
        {a.photos?.[0]?.url
          ? <img src={a.photos[0].url} alt={`${a.tag} photo`} className="h-[84px] w-[84px] shrink-0 rounded-[18px] object-cover" />
          : <div className="flex h-[84px] w-[84px] shrink-0 items-center justify-center rounded-[18px] bg-[#DCE7DE] font-bold text-brand">Photo</div>}
        <div className="min-w-0 flex-1">
          <h1 className="m-0 text-2xl font-bold">{a.tag} {a.name && <span className="font-medium text-ink-2">{a.name}</span>}</h1>
          <div className="mt-0.5 text-sm text-ink-3">{[a.species, a.breed, a.sex, ageText(a.dob)].filter(Boolean).join(' · ')}</div>
          <div className="mt-2 flex flex-wrap gap-1.5">
            <span className="badge bg-brand text-white">{a.status === 'Active' ? a.category : a.status}</span>
            {a.status === 'Active' && <FeedGroupBadge group={feedGroups.get(String(a.id))} />}
            {a.flags.map((f) => <span key={f.text} className={`badge ${toneClass[f.tone]}`}>{f.text}</span>)}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-4 px-4 pb-4">
        {QUICK.map((q) => (
          <button key={q.label} type="button" onClick={() => {
            if (q.label === 'Milk') nav(`/milk/cow?animal=${a.id}`)
            else if (q.label === 'Health') nav(`/health/new?animal=${a.id}&back=/animals/${a.id}`)
            else if (q.label === 'Weight') nav(`/weight/new?animal=${a.id}`)
            else if (q.label === 'Breeding' && a.sex === 'Female') nav(`/breeding/animals/${a.id}`)
            else later()
          }} className="flex flex-col items-center gap-1.5 border-0 bg-transparent text-xs font-semibold text-ink">
            <span className="flex h-[52px] w-[52px] items-center justify-center rounded-2xl bg-white"><Icon name={q.icon} color={q.color} /></span>{q.label}
          </button>
        ))}
      </div>

      <div role="tablist" aria-label="Sections" className="mb-3.5 flex gap-[18px] overflow-x-auto border-b border-line-soft px-4">
        {TABS.map((t) => (
          <button key={t} type="button" role="tab" aria-selected={tab === t} onClick={() => {
            if (t === 'Weight') nav(`/animals/${a.id}/growth`)
            else if (t === 'Family') nav(`/animals/${a.id}/family`)
            else if (t === 'Breeding' && a.sex === 'Female') nav(`/breeding/animals/${a.id}`)
            else if (t === 'Overview' || t === 'Health' || (t === 'Milk' && a.sex === 'Female')) setTab(t)
            else later()
          }} className={`flex h-11 shrink-0 items-center whitespace-nowrap border-0 border-b-2 bg-transparent px-1 text-sm font-semibold ${tab === t ? 'border-brand text-ink' : 'border-transparent text-ink-3'}`}>{t}</button>
        ))}
      </div>

      {tab === 'Health' && <HealthTab animalId={a.id} />}
      {tab === 'Milk' && <MilkTab animalId={a.id} />}
      {tab === 'Overview' && <>

      {a.flags.length > 0 && (
        <div className="card bg-warn-soft">
          <div className="mb-2.5 text-[15px] font-bold text-warn-text">Due for this animal</div>
          <ul className="m-0 list-disc pl-5 text-sm leading-[1.7] text-[#4A3210]">{a.flags.map((f) => <li key={f.text}>{f.text}</li>)}</ul>
        </div>
      )}

      <div className="card">
        <div className="mb-2.5 text-[15px] font-bold">Details</div>
        <div className="grid grid-cols-2 gap-x-4 gap-y-3">
          {kv('Group', group?.name ?? 'No group')}
          {kv('Date of birth', fmtDate(a.dob))}
          {kv('Last calving', fmtDate(a.lastCalving))}
          {kv('Days in milk', a.category === 'Milking' && dim !== undefined ? `${dim}${stage}` : '—')}
          {kv('Source', a.source)}
          {kv('Purchase price', a.source === 'Purchased' ? inr(a.purchasePrice) : '—')}
          {kv('National ID', a.nationalId || '—')}
          {kv('Colour', a.color || '—')}
        </div>
        {a.notes && <p className="m-0 mt-3 text-sm text-ink-2">{a.notes}</p>}
      </div>

      {a.weightKg !== undefined && (
        <div className="card">
          <div className="flex items-baseline justify-between"><div className="mb-2.5 text-[15px] font-bold">Weight</div><Link to={`/animals/${a.id}/growth`} className="flex h-11 items-center text-sm font-semibold">Trend</Link></div>
          <div className="text-[26px] font-bold">{a.weightKg} kg</div>
          <div className="text-[13px] text-ink-3">Latest record</div>
        </div>
      )}

      {a.category === 'Milking' && dim !== undefined && (
        <div className="card">
          <div className="mb-2.5 text-[15px] font-bold">Lactation</div>
          <div className="h-2 overflow-hidden rounded bg-track" role="progressbar" aria-valuemin={0} aria-valuemax={305} aria-valuenow={Math.min(dim, 305)} aria-label="Days in milk">
            <div className="h-2 bg-brand" style={{ width: `${Math.min(100, (dim / 305) * 100)}%` }} />
          </div>
          <div className="mt-1.5 flex justify-between text-xs text-ink-3"><span>Calving</span><span>Day {dim} of 305</span><span>Dry-off</span></div>
          {a.milkYesterday !== undefined && <div className="mt-3 text-sm"><b>{a.milkYesterday} L</b> <span className="text-ink-3">yesterday</span></div>}
        </div>
      )}

      <div className="card">
        <div className="mb-2.5 text-[15px] font-bold">Family</div>
        <div className="grid grid-cols-2 gap-2.5">
          <div className="rounded-[10px] bg-ground p-2.5"><div className="text-xs font-semibold text-ink-3">Dam</div><div className="mt-0.5 text-[15px] font-semibold">{(() => { const dam = animals.find((x) => x.tag === a.dam); return dam ? <Link to={`/animals/${dam.id}`}>{dam.tag}</Link> : a.dam || '—' })()}</div></div>
          <div className="rounded-[10px] bg-ground p-2.5"><div className="text-xs font-semibold text-ink-3">Sire</div><div className="mt-0.5 text-[15px] font-semibold">{a.sire || '—'}</div></div>
        </div>
        <div className="mt-2.5 text-sm">
          Offspring: {offspring.length === 0 ? '—' : offspring.map((o, i) => (
            <span key={o.id}>{i > 0 && ', '}<Link to={`/animals/${o.id}`} className="font-semibold">{o.tag}</Link> ({o.sex === 'Female' ? '♀' : '♂'}, {ageText(o.dob)})</span>
          ))}
        </div>
      </div>

      </>}

      <Footer className="mt-auto" />
      {sheet && <StatusSheet animal={a} onClose={() => setSheet(false)} />}
    </Screen>
  )
}
