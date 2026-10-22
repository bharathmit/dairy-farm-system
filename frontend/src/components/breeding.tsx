/* eslint-disable react-refresh/only-export-components */
import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useStore } from '../data/store'
import { breedingApi, inr0, SUGGESTION_SHORT, SUGGESTION_TONE, type CowStatus } from '../data/breeding'
import { shortDate } from '../data/records'

/** Breeding API calls for the chosen farm, and whether money and owner actions are shown. */
export function useBreeding() {
  const { farm } = useStore()
  const farmId = farm?.id ?? ''
  const br = useMemo(() => breedingApi(farmId), [farmId])
  return { br, farmId, isOwner: farm?.role === 'Owner' }
}

export const isBreedable = (a: { sex: string; category: string; status: string }) =>
  a.status === 'Active' && a.sex === 'Female' && ['Heifer', 'Milking', 'Dry'].includes(a.category)

/** One-line summary under a cow's name, chosen by her status. */
export function statusLine(s: CowStatus, showMoney: boolean) {
  const parts: string[] = []
  switch (s.status) {
    case 'OVERDUE': parts.push(`Due ${shortDate(s.expectedDueDate)}`); break
    case 'PREGNANT': case 'DRY_PREGNANT':
      parts.push(`Due ${shortDate(s.expectedDueDate)}`); if (s.dryOffDate) parts.push(`dry-off ${shortDate(s.dryOffDate)}`); break
    case 'SERVED': parts.push(`Served ${shortDate(s.lastServiceDate)}`); if (s.pdDueDate) parts.push(`PD ${shortDate(s.pdDueDate)}`); break
    case 'HEIFER_NOT_SERVED': parts.push('Heifer · not served yet'); break
    default:
      if (s.daysOpen != null) parts.push(`${s.daysOpen} d open`)
      if (s.failedServices) parts.push(`${s.failedServices} failed`)
      if (s.heatsMissed) parts.push(`${s.heatsMissed} heat${s.heatsMissed === 1 ? '' : 's'} missed`)
      if (!s.failedServices && !s.heatsMissed && s.lastHeatDate) parts.push(`last heat ${shortDate(s.lastHeatDate)}`)
      if (showMoney && s.spendThisLactation != null) parts.push(inr0(s.spendThisLactation))
  }
  if (s.breed && (s.status === 'OVERDUE')) parts.push(s.breed)
  return parts.join(' · ')
}

/** A cow row that opens her breeding history. */
export function CowRow({ s, showMoney, first }: { s: CowStatus; showMoney: boolean; first?: boolean }) {
  const a = s.suggestion.action
  return (
    <Link to={`/breeding/animals/${s.animal.id}`} className={`flex min-h-[52px] items-center gap-2.5 px-3.5 py-2 text-sm text-ink no-underline hover:text-ink ${first ? '' : 'border-t border-line-faint'}`}>
      <span className="min-w-0 flex-1"><b>{s.animal.tag}</b> {s.animal.name}<div className="text-[13px] text-ink-3">{statusLine(s, showMoney)}</div></span>
      {a !== 'OK' && <span className={`badge ${SUGGESTION_TONE[a]}`}>{SUGGESTION_SHORT[a]}</span>}
    </Link>
  )
}
