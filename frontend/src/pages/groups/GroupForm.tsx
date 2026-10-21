import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ActionBar, Chips, Screen, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { newId, SPECIES, useStore } from '../../data/store'
import type { Group, Species } from '../../data/types'

const PURPOSES = ['Maintenance', 'Growth / fattening', 'Recovery', 'Gestation', 'Other']
const COLOURS = [
  { value: '#1F5C3A', label: 'Green' },
  { value: '#C2410C', label: 'Orange' },
  { value: '#1F4E87', label: 'Blue' },
  { value: '#7A4B0E', label: 'Brown' },
  { value: '#5B3A8C', label: 'Purple' },
]
const NO_GROUP = '__none'

export default function GroupForm() {
  const { id } = useParams()
  const nav = useNavigate()
  const { groups, animals, members, saveGroup, notify } = useStore()
  const existing = groups.find((g) => g.id === id)
  const editing = Boolean(existing)

  const [species, setSpecies] = useState<Species>(existing?.species ?? 'Cow')
  const [name, setName] = useState(existing?.name ?? '')
  const [location, setLocation] = useState(existing?.location ?? '')
  const [purpose, setPurpose] = useState(existing?.purpose ?? 'Maintenance')
  const [managerId, setManagerId] = useState(existing?.managerId ?? '')
  const [color, setColor] = useState(existing?.color ?? COLOURS[0].value)
  const [notes, setNotes] = useState(existing?.notes ?? '')
  const [selected, setSelected] = useState<string[]>(() => (existing ? animals.filter((a) => a.groupId === existing.id && a.status === 'Active').map((a) => a.id) : []))
  const [query, setQuery] = useState('')
  const [filters, setFilters] = useState<string[]>([])
  const [error, setError] = useState('')

  const workers = members.filter((m) => m.role === 'Worker' && m.active)
  const pool = animals.filter((a) => a.status === 'Active' && a.species === species)
  const categories = useMemo(() => Array.from(new Set(pool.map((a) => a.category))), [pool])

  const visible = pool.filter((a) => {
    const q = query.trim().toLowerCase()
    if (q && !`${a.tag} ${a.name ?? ''} ${a.breed ?? ''}`.toLowerCase().includes(q)) return false
    if (filters.length === 0) return true
    const cats = filters.filter((f) => f !== NO_GROUP)
    const matchCat = cats.length > 0 && cats.includes(a.category)
    const matchNone = filters.includes(NO_GROUP) && !a.groupId
    return matchCat || matchNone
  })

  const toggle = (aid: string) => setSelected((s) => (s.includes(aid) ? s.filter((x) => x !== aid) : [...s, aid]))
  const close = existing ? `/groups/${existing.id}` : '/groups'

  const save = () => {
    if (!name.trim()) { setError('Enter a group name'); return }
    const g: Group = {
      id: existing?.id ?? newId('g'),
      name: name.trim(),
      species,
      location: location.trim() || undefined,
      purpose,
      managerId: managerId || undefined,
      color,
      notes: notes.trim() || undefined,
    }
    saveGroup(g, selected.filter((sid) => pool.some((a) => a.id === sid)))
    notify(editing ? 'Group saved' : 'Group created')
    nav(`/groups/${g.id}`, { replace: true })
  }

  if (id && !existing) {
    return (
      <Screen>
        <TopBar title="Group not found" close="/groups" />
        <p className="px-5 text-ink-3">This group doesn't exist or was deleted.</p>
      </Screen>
    )
  }

  return (
    <Screen>
      <TopBar title={editing ? 'Edit group' : 'New group'} close={close} border />
      <div className="px-5 pb-5">
        <div className="mb-2.5 mt-[22px] text-xs font-bold uppercase tracking-[.06em] text-ink-3">Species *</div>
        {editing ? (
          <div className="flex"><span className="chip chip-on flex items-center">{species}</span></div>
        ) : (
          <Chips
            label="Species"
            options={SPECIES.map((s) => ({ value: s, label: s }))}
            value={[species]}
            onToggle={(v) => { setSpecies(v); setSelected([]); setFilters([]) }}
          />
        )}
        <div className="mt-1.5 text-xs text-ink-3">A group holds one species only.{editing ? ' Species can’t be changed after saving.' : ''}</div>

        <div className="mb-2.5 mt-[22px] text-xs font-bold uppercase tracking-[.06em] text-ink-3">Details</div>
        <div className="flex flex-col gap-3.5">
          <div>
            <label className="lab" htmlFor="gn">Group name *</label>
            <input className="inp" id="gn" value={name} onChange={(e) => { setName(e.target.value); setError('') }} aria-invalid={Boolean(error)} aria-describedby={error ? 'gn-err' : undefined} placeholder="e.g. Milking shed A" />
            {error && <div id="gn-err" className="mt-1.5 text-[13px] font-medium text-danger-text">{error}</div>}
          </div>
          <div><label className="lab" htmlFor="gl">Location</label><input className="inp" id="gl" value={location} onChange={(e) => setLocation(e.target.value)} placeholder="Shed, paddock, pen…" /></div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="lab" htmlFor="go">Purpose</label><select className="inp" id="go" value={purpose} onChange={(e) => setPurpose(e.target.value)}>{PURPOSES.map((p) => <option key={p}>{p}</option>)}</select></div>
            <div><label className="lab" htmlFor="gm">Manager</label><select className="inp" id="gm" value={managerId} onChange={(e) => setManagerId(e.target.value)}>{workers.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}<option value="">None</option></select></div>
          </div>
          <div>
            <span className="lab" id="gc-lab">Colour</span>
            <div role="radiogroup" aria-labelledby="gc-lab" className="flex gap-1">
              {COLOURS.map((c) => (
                <button
                  key={c.value}
                  type="button"
                  role="radio"
                  aria-checked={color === c.value}
                  aria-label={c.label}
                  onClick={() => setColor(c.value)}
                  className="flex h-11 w-11 items-center justify-center rounded-full"
                >
                  <span
                    className="block h-9 w-9 rounded-full border-[3px] border-white"
                    style={{ background: c.value, boxShadow: color === c.value ? '0 0 0 2px #1C2620' : '0 0 0 1px #C9D0C6' }}
                  />
                </button>
              ))}
            </div>
          </div>
          <div><label className="lab" htmlFor="gt">Notes</label><textarea id="gt" value={notes} onChange={(e) => setNotes(e.target.value)} className="box-border h-[72px] w-full resize-none rounded-[10px] border border-line px-3.5 py-3 text-base" /></div>
        </div>

        <div className="mb-2.5 mt-[22px] text-xs font-bold uppercase tracking-[.06em] text-ink-3">Members · {selected.length} selected</div>
        <label className="mb-2.5 flex h-[46px] items-center gap-2 rounded-xl border border-line bg-white px-3">
          <Icon name="search" size={18} color="#56655B" />
          <input aria-label={`Search ${species.toLowerCase()}s`} placeholder={`Search ${species.toLowerCase()}s`} value={query} onChange={(e) => setQuery(e.target.value)} className="min-w-0 flex-1 border-0 bg-transparent text-[15px] outline-none" />
        </label>
        <div className="mb-2.5">
          <Chips
            label="Filter animals"
            options={[...categories.map((c) => ({ value: c as string, label: c })), { value: NO_GROUP, label: 'Not in a group' }]}
            value={filters}
            onToggle={(v) => setFilters((f) => (f.includes(v) ? f.filter((x) => x !== v) : [...f, v]))}
          />
        </div>
        <div className="overflow-hidden rounded-[14px] bg-white">
          {visible.length === 0 && <div className="px-3 py-4 text-center text-sm text-ink-3">No {species.toLowerCase()}s match.</div>}
          {visible.map((a, i) => {
            const other = a.groupId && a.groupId !== existing?.id ? groups.find((g) => g.id === a.groupId) : undefined
            return (
              <label key={a.id} className={`flex min-h-14 items-center gap-3 px-3 py-2 ${i ? 'border-t border-line-faint' : ''}`}>
                <input type="checkbox" checked={selected.includes(a.id)} onChange={() => toggle(a.id)} className="m-0 h-[22px] w-[22px] accent-brand" />
                <span className="flex-1">
                  <b>{a.tag}</b> {a.name}
                  <br />
                  <span className="text-[13px] text-ink-3">{[a.breed, a.category].filter(Boolean).join(' · ')}{other && selected.includes(a.id) ? ` · in "${other.name}" — will move` : other ? ` · in "${other.name}"` : ''}</span>
                </span>
              </label>
            )
          })}
        </div>
      </div>
      <ActionBar>
        <button type="button" onClick={save} className="btn">Save group</button>
      </ActionBar>
    </Screen>
  )
}
