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
  'group', 'exit_date',
] as const
