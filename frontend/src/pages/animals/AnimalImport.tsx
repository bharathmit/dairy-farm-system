import { useRef, useState, type ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ActionBar, Screen, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { CATEGORIES, newId, SPECIES, useStore } from '../../data/store'
import type { Animal, AnimalStatus, Category, Sex, Species } from '../../data/types'
import { animalsToCsv, CSV_COLUMNS, download, parseCsv } from './shared'

interface Check { file: string; rows: number; ready: Animal[]; duplicates: string[]; errors: string[] }

const cap = (s: string) => s.trim().charAt(0).toUpperCase() + s.trim().slice(1).toLowerCase()
const numOrUndef = (s?: string) => (s && s.trim() !== '' && !Number.isNaN(Number(s)) ? Number(s) : undefined)

function check(file: string, text: string, existingTags: Set<string>): Check {
  const [header = [], ...body] = parseCsv(text)
  const idx = (c: string) => header.findIndex((h) => h.trim().toLowerCase() === c)
  const get = (r: string[], c: string) => { const i = idx(c); return i >= 0 ? (r[i] ?? '').trim() : '' }
  const ready: Animal[] = []
  const duplicates: string[] = []
  const errors: string[] = []
  const seen = new Set<string>()
  if (idx('tag_number') < 0) errors.push('Missing column: tag_number')
  body.forEach((r, i) => {
    const line = i + 2
    const tag = get(r, 'tag_number')
    const sex = cap(get(r, 'sex'))
    const species = cap(get(r, 'species'))
    const missing = [!tag && 'tag_number', !sex && 'sex', !species && 'species'].filter(Boolean)
    if (missing.length) { errors.push(`Row ${line}: ${missing.join(', ')} ${missing.length > 1 ? 'are' : 'is'} missing`); return }
    if (sex !== 'Female' && sex !== 'Male') { errors.push(`Row ${line}: sex must be female or male`); return }
    if (!(SPECIES as readonly string[]).includes(species)) { errors.push(`Row ${line}: unknown species "${species}"`); return }
    const key = tag.toLowerCase()
    if (existingTags.has(key) || seen.has(key)) { duplicates.push(tag); return }
    seen.add(key)
    const catRaw = cap(get(r, 'category'))
    const category: Category = (CATEGORIES as readonly string[]).includes(catRaw) ? (catRaw as Category) : 'Milking'
    const statusRaw = cap(get(r, 'status'))
    const status: AnimalStatus = ['Active', 'Sold', 'Dead', 'Transferred'].includes(statusRaw) ? (statusRaw as AnimalStatus) : 'Active'
    ready.push({
      id: newId('a'), tag, name: get(r, 'animal_name') || undefined, dob: get(r, 'dob') || undefined,
      sex: sex as Sex, species: species as Species, status, nationalId: get(r, 'national_id') || undefined,
      dam: get(r, 'dam_tag') || undefined, sire: get(r, 'sire_tag') || undefined, breed: get(r, 'breed') || undefined,
      color: get(r, 'color') || undefined, birthWeightKg: numOrUndef(get(r, 'birth_weight')), category,
      source: get(r, 'acquisition_type').toLowerCase().startsWith('born') ? 'Born on farm' : 'Purchased',
      purchaseDate: get(r, 'acquisition_date') || undefined, purchasePrice: numOrUndef(get(r, 'purchase_price')),
      notes: get(r, 'notes') || undefined, flags: [],
    })
  })
  return { file, rows: body.length, ready, duplicates, errors }
}

export default function AnimalImport() {
  const { animals, importAnimals, notify } = useStore()
  const nav = useNavigate()
  const input = useRef<HTMLInputElement>(null)
  const [result, setResult] = useState<Check | null>(null)

  const onFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0]
    if (!f) return
    const text = await f.text()
    setResult(check(f.name, text, new Set(animals.map((a) => a.tag.toLowerCase()))))
    e.target.value = ''
  }

  const n = result?.ready.length ?? 0
  const step = 'flex items-start gap-3'
  const num = (k: number) => <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand text-sm font-bold text-white">{k}</div>

  return (
    <Screen>
      <TopBar title="Import & export" back="/animals" />
      <div className="flex flex-col gap-5 px-5 py-2">
        <div className={step}>{num(1)}<div className="flex-1">
          <div className="text-base font-semibold">Download the template</div>
          <div className="mb-2.5 mt-1 text-sm leading-snug text-ink-3">Columns: {CSV_COLUMNS.map((c) => (['tag_number', 'sex', 'species'].includes(c) ? `${c}*` : c)).join(', ')}.</div>
          <button type="button" onClick={() => download('herd-template.csv', `${CSV_COLUMNS.join(',')}\nVD-021,Lakshmi,2021-08-14,female,cow,active,,D-12,,HF,Black & white,,Milking,purchased,2023-02-10,65000,\n`)} className="h-[46px] rounded-xl border border-brand bg-white px-3.5 text-[15px] font-semibold text-brand">Download CSV template</button>
        </div></div>

        <div className={step}>{num(2)}<div className="flex-1">
          <div className="text-base font-semibold">Upload your file</div>
          <input ref={input} type="file" accept=".csv,text/csv" className="sr-only" id="csv" onChange={onFile} />
          <label htmlFor="csv" className="mt-2.5 block cursor-pointer rounded-[14px] border-2 border-dashed border-[#A9B3A6] bg-white p-[22px] text-center">
            <span className="flex justify-center"><Icon name="upload" size={28} color="#1F5C3A" /></span>
            {result ? (
              <><div className="mt-1.5 font-semibold">{result.file}</div><div className="text-[13px] text-ink-3">{result.rows} rows read · tap to choose another</div></>
            ) : (
              <><div className="mt-1.5 font-semibold">Choose a CSV file</div><div className="text-[13px] text-ink-3">Use the template columns</div></>
            )}
          </label>
        </div></div>

        <div className={step}>{num(3)}<div className="flex-1">
          <div className="text-base font-semibold">Check before importing</div>
          {result ? (
            <div className="mt-2.5 overflow-hidden rounded-xl bg-white">
              <div className="flex justify-between px-3.5 py-3 text-[15px]"><span>Ready to add</span><b className="text-brand">{result.ready.length}</b></div>
              <div className="flex justify-between border-t border-line-faint px-3.5 py-3 text-[15px]"><span>Tag already exists — skip</span><b>{result.duplicates.length}</b></div>
              {result.errors.map((er) => <div key={er} className="border-t border-line-faint px-3.5 py-3 text-sm text-danger-text">{er}</div>)}
            </div>
          ) : (
            <div className="mt-1 text-sm text-ink-3">Upload a file to see what will be added.</div>
          )}
        </div></div>
      </div>
      <ActionBar>
        <button type="button" onClick={() => download('herd.csv', animalsToCsv(animals))} className="h-[50px] flex-1 rounded-xl border border-line bg-white text-[15px] font-semibold text-ink">Export herd</button>
        <button
          type="button"
          disabled={n === 0}
          onClick={() => { if (result) { importAnimals(result.ready); notify(`${n} animals imported`); nav('/animals') } }}
          className="h-[50px] flex-[2] rounded-xl bg-brand text-base font-semibold text-white disabled:opacity-50"
        >
          Import {n} {n === 1 ? 'animal' : 'animals'}
        </button>
      </ActionBar>
    </Screen>
  )
}
