import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ActionBar, Chips, Screen, Segmented, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { CATEGORIES, newId, SPECIES, useStore } from '../../data/store'
import type { Animal, Category, Sex, Species } from '../../data/types'

const BREEDS = ['HF', 'Jersey', 'HF × Jersey', 'Sahiwal', 'Gir'] as const
const OTHER = 'Other…'

type Draft = Omit<Animal, 'birthWeightKg' | 'purchasePrice' | 'weightKg'> & {
  birthWeightKg: string
  purchasePrice: string
  weightKg: string
}

const num = (s: string) => (s.trim() === '' ? undefined : Number(s.replace(/,/g, '')))

function Field({ id, label, children, hint, error }: { id: string; label: string; children: ReactNode; hint?: string; error?: string }) {
  return (
    <div>
      <label className="lab" htmlFor={id}>{label}</label>
      {children}
      {error ? <div className="mt-1 text-xs font-semibold text-danger-text" role="alert">{error}</div> : hint ? <div className="mt-1 text-xs text-ink-3">{hint}</div> : null}
    </div>
  )
}

export default function AnimalForm() {
  const { id } = useParams()
  const nav = useNavigate()
  const { animals, groups, saveAnimal, notify } = useStore()
  const existing = id ? animals.find((a) => a.id === id) : undefined
  const editing = Boolean(existing)

  const [d, setD] = useState<Draft>(() => {
    const a: Animal = existing ?? { id: '', tag: '', species: 'Cow', sex: 'Female', category: 'Milking', source: 'Purchased', status: 'Active', flags: [], breed: 'HF' }
    return { ...a, birthWeightKg: a.birthWeightKg?.toString() ?? '', purchasePrice: a.purchasePrice?.toString() ?? '', weightKg: a.weightKg?.toString() ?? '' }
  })
  const knownBreed = !d.breed || (BREEDS as readonly string[]).includes(d.breed)
  const [otherBreed, setOtherBreed] = useState(!knownBreed)
  const [errors, setErrors] = useState<{ tag?: string; dob?: string }>({})

  if (id && !existing) {
    return (
      <Screen>
        <TopBar title="Animal" back="/animals" />
        <div className="card mt-4"><div className="font-bold">Animal not found</div><Link to="/animals" className="mt-2 inline-block font-semibold">Back to herd</Link></div>
      </Screen>
    )
  }

  const set = <K extends keyof Draft>(k: K, v: Draft[K]) => setD((x) => ({ ...x, [k]: v }))
  const speciesGroups = groups.filter((g) => g.species === d.species)

  const save = (e?: FormEvent) => {
    e?.preventDefault()
    const errs: typeof errors = {}
    const tag = d.tag.trim()
    if (!tag) errs.tag = 'Ear tag number is required'
    else if (animals.some((a) => a.tag.toLowerCase() === tag.toLowerCase() && a.id !== d.id)) errs.tag = 'This tag is already used in the herd'
    if (d.source === 'Born on farm' && !d.dob) errs.dob = 'Required if born on farm'
    setErrors(errs)
    if (Object.keys(errs).length) {
      document.getElementById(errs.tag ? 'tg' : 'db')?.focus()
      return
    }
    const purchased = d.source === 'Purchased'
    const animal: Animal = {
      ...d,
      id: d.id || newId('a'),
      tag,
      name: d.name?.trim() || undefined,
      birthWeightKg: num(d.birthWeightKg),
      weightKg: num(d.weightKg),
      purchasePrice: purchased ? num(d.purchasePrice) : undefined,
      purchaseDate: purchased ? d.purchaseDate : undefined,
      groupId: d.groupId || undefined,
    }
    saveAnimal(animal)
    notify(editing ? 'Animal updated' : 'Animal added')
    nav(`/animals/${animal.id}`, { replace: true })
  }

  const sec = 'mb-2.5 mt-[22px] text-xs font-bold uppercase tracking-[.06em] text-ink-3'
  const two = 'grid grid-cols-2 gap-3'

  return (
    <Screen>
      <TopBar
        title={editing ? 'Edit animal' : 'Add animal'}
        close={editing ? `/animals/${id}` : '/animals'}
        border
        right={<button type="button" onClick={() => save()} className="flex h-11 items-center px-3 font-bold text-brand">Save</button>}
      />
      <form onSubmit={save} noValidate className="flex flex-1 flex-col">
        <div className="px-5 pb-6">
          <div className={sec}>Photos</div>
          <div className="flex gap-2.5">
            <button type="button" aria-label="Add photo" onClick={() => notify('Photo upload comes with the backend')} className="flex h-[88px] w-[88px] flex-col items-center justify-center gap-1 rounded-xl border border-dashed border-[#A9B3A6] bg-white text-xs text-ink-2">
              <Icon name="camera" color="#1F5C3A" />Add
            </button>
            <div className="h-[88px] w-[88px] rounded-xl bg-track" />
            <div className="h-[88px] w-[88px] rounded-xl bg-track" />
          </div>
          <div className="mt-1.5 text-xs text-ink-3">Up to 3 photos</div>

          <div className={sec}>Identity</div>
          <div className="flex flex-col gap-3.5">
            <Field id="tg" label="Ear tag number *" error={errors.tag}>
              <div className="flex gap-2">
                <input className="inp" id="tg" value={d.tag} onChange={(e) => set('tag', e.target.value)} aria-invalid={Boolean(errors.tag)} placeholder="VD-021" />
                <Link to="/animals/scan" aria-label="Scan tag" className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[10px] border border-line bg-white"><Icon name="scan" size={20} /></Link>
              </div>
            </Field>
            <div className={two}>
              <Field id="nm" label="Name"><input className="inp" id="nm" placeholder="Optional" value={d.name ?? ''} onChange={(e) => set('name', e.target.value)} /></Field>
              <Field id="ni" label="National ID"><input className="inp" id="ni" placeholder="Optional" value={d.nationalId ?? ''} onChange={(e) => set('nationalId', e.target.value)} /></Field>
            </div>
          </div>

          <div className={sec}>Basics</div>
          <span className="lab">Species *</span>
          {editing ? (
            <div className="flex"><span className="chip chip-on flex items-center">{d.species}</span></div>
          ) : (
            <Chips label="Species" options={SPECIES.map((s) => ({ value: s, label: s }))} value={[d.species]} onToggle={(v: Species) => setD((x) => ({ ...x, species: v, groupId: undefined }))} />
          )}
          <div className="mt-1.5 text-xs text-ink-3">Can't be changed after saving.</div>

          <span className="lab mt-3.5">Sex *</span>
          <Segmented<Sex> label="Sex" value={d.sex} onChange={(v) => set('sex', v)} options={[{ value: 'Female', label: 'Female' }, { value: 'Male', label: 'Male' }]} />

          <span className="lab mt-3.5">Breed</span>
          <Chips
            label="Breed"
            options={[...BREEDS, OTHER].map((b) => ({ value: b, label: b }))}
            value={[otherBreed ? OTHER : d.breed ?? '']}
            onToggle={(v: string) => {
              if (v === OTHER) { setOtherBreed(true); set('breed', '') }
              else { setOtherBreed(false); set('breed', v) }
            }}
          />
          {otherBreed && (
            <div className="mt-2.5"><label className="lab" htmlFor="ob">Breed name</label><input className="inp" id="ob" value={d.breed ?? ''} onChange={(e) => set('breed', e.target.value)} placeholder="e.g. Red Sindhi" /></div>
          )}
          <div className={`${two} mt-3.5`}>
            <Field id="ct" label="Category *">
              <select className="inp" id="ct" value={d.category} onChange={(e) => set('category', e.target.value as Category)}>
                {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
              </select>
            </Field>
            <Field id="cl" label="Colour"><input className="inp" id="cl" placeholder="Black & white" value={d.color ?? ''} onChange={(e) => set('color', e.target.value)} /></Field>
          </div>
          <div className="mt-3.5">
            <Field id="gr" label="Group">
              <select className="inp" id="gr" value={d.groupId ?? ''} onChange={(e) => set('groupId', e.target.value || undefined)}>
                {speciesGroups.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
                <option value="">No group</option>
              </select>
            </Field>
          </div>

          <div className={sec}>Birth &amp; source</div>
          <Segmented<Animal['source']> label="Source" value={d.source} onChange={(v) => set('source', v)} options={[{ value: 'Born on farm', label: 'Born on farm' }, { value: 'Purchased', label: 'Purchased' }]} />
          <div className={`${two} mt-3.5`}>
            <Field id="db" label="Date of birth" hint="Required if born on farm" error={errors.dob}>
              <input className="inp" id="db" type="date" value={d.dob ?? ''} onChange={(e) => set('dob', e.target.value || undefined)} aria-invalid={Boolean(errors.dob)} />
            </Field>
            <Field id="bw" label="Birth weight (kg)"><input className="inp" id="bw" inputMode="decimal" value={d.birthWeightKg} onChange={(e) => set('birthWeightKg', e.target.value)} /></Field>
          </div>
          {d.source === 'Purchased' && (
            <div className={`${two} mt-3.5`}>
              <Field id="ad" label="Purchase date"><input className="inp" id="ad" type="date" value={d.purchaseDate ?? ''} onChange={(e) => set('purchaseDate', e.target.value || undefined)} /></Field>
              <Field id="pp" label="Purchase price (₹)"><input className="inp" id="pp" inputMode="decimal" placeholder="65,000" value={d.purchasePrice} onChange={(e) => set('purchasePrice', e.target.value)} /></Field>
            </div>
          )}
          <div className={`${two} mt-3.5`}>
            <Field id="lc" label="Last calving date" hint="Milking / dry cows"><input className="inp" id="lc" type="date" value={d.lastCalving ?? ''} onChange={(e) => set('lastCalving', e.target.value || undefined)} /></Field>
            <Field id="cw" label="Current weight (kg)"><input className="inp" id="cw" inputMode="decimal" value={d.weightKg} onChange={(e) => set('weightKg', e.target.value)} /></Field>
          </div>

          <div className={sec}>Parents</div>
          <div className="flex flex-col gap-3.5">
            <Field id="dm" label="Dam (mother)">
              <input className="inp" id="dm" list="dam-tags" placeholder="Pick from herd or type tag" value={d.dam ?? ''} onChange={(e) => set('dam', e.target.value)} />
              <datalist id="dam-tags">{animals.filter((a) => a.sex === 'Female' && a.id !== d.id).map((a) => <option key={a.id} value={a.tag}>{a.name}</option>)}</datalist>
            </Field>
            <Field id="sr" label="Sire (father)"><input className="inp" id="sr" placeholder="Pick bull, or AI bull name / code" value={d.sire ?? ''} onChange={(e) => set('sire', e.target.value)} /></Field>
          </div>

          <div className={sec}>Notes</div>
          <textarea aria-label="Notes" value={d.notes ?? ''} onChange={(e) => set('notes', e.target.value)} className="box-border h-24 w-full resize-none rounded-[10px] border border-line px-3.5 py-3 text-base" placeholder="Temperament, markings, history…" />
        </div>
        <ActionBar><button type="submit" className="btn">Save animal</button></ActionBar>
      </form>
    </Screen>
  )
}
