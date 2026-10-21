import type { Animal } from '../../data/types'

/** Short label for the avatar tile, e.g. "HF", "J", "HJ". */
export function breedInitials(breed?: string) {
  if (!breed) return '—'
  if (breed === 'HF') return 'HF'
  const words = breed.replace(/[^A-Za-z ]/g, ' ').split(/\s+/).filter(Boolean)
  return words.map((w) => w[0].toUpperCase()).join('').slice(0, 2)
}

export const CSV_COLUMNS = [
  'tag_number', 'animal_name', 'dob', 'sex', 'species', 'status', 'national_id', 'dam_tag', 'sire_tag',
  'breed', 'color', 'birth_weight', 'category', 'acquisition_type', 'acquisition_date', 'purchase_price', 'notes',
] as const

function cell(v: string | number | undefined) {
  const s = v === undefined ? '' : String(v)
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

export function animalsToCsv(animals: Animal[]) {
  const rows = animals.map((a) => [
    a.tag, a.name, a.dob, a.sex, a.species, a.status, a.nationalId, a.dam, a.sire, a.breed, a.color,
    a.birthWeightKg, a.category, a.source === 'Born on farm' ? 'born' : 'purchased', a.purchaseDate, a.purchasePrice, a.notes,
  ].map(cell).join(','))
  return [CSV_COLUMNS.join(','), ...rows].join('\n')
}

/** Minimal CSV parser: handles quoted cells and escaped quotes. */
export function parseCsv(text: string): string[][] {
  const rows: string[][] = []
  let row: string[] = []
  let cur = ''
  let q = false
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    if (q) {
      if (c === '"' && text[i + 1] === '"') { cur += '"'; i++ }
      else if (c === '"') q = false
      else cur += c
    } else if (c === '"') q = true
    else if (c === ',') { row.push(cur); cur = '' }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && text[i + 1] === '\n') i++
      row.push(cur); cur = ''
      if (row.some((x) => x.trim() !== '')) rows.push(row)
      row = []
    } else cur += c
  }
  row.push(cur)
  if (row.some((x) => x.trim() !== '')) rows.push(row)
  return rows
}

export function download(filename: string, text: string) {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
