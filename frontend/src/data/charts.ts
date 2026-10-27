import { useState } from 'react'
import { addDays, todayIso } from './records'

/** One point on a line chart: axis label, tooltip text and value. */
export interface Point { label: string; tip: string; value: number }

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
export const MAX_DAYS = 365
export const daysBetween = (a: string, b: string) => Math.round((Date.parse(b) - Date.parse(a)) / 86400000)
export const monthName = (yyyyMm: string) => `${MONTHS[Number(yyyyMm.slice(5, 7)) - 1]} ${yyyyMm.slice(2, 4)}`

/** First day of the month 11 months before today: a 12-month window ending this month. */
export function twelveMonthsFrom(today = todayIso()) {
  const d = new Date(Number(today.slice(0, 4)), Number(today.slice(5, 7)) - 12, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
}

/** Group dated values by month; `avg` averages over days with a value, otherwise sums. */
export function byMonth<T extends { date: string }>(rows: T[], value: (r: T) => number, avg: boolean) {
  const m = new Map<string, { sum: number; n: number }>()
  for (const r of rows) {
    const k = r.date.slice(0, 7)
    const e = m.get(k) ?? { sum: 0, n: 0 }
    const v = value(r)
    e.sum += v
    if (v !== 0) e.n++
    m.set(k, e)
  }
  return [...m.entries()].map(([k, e]) => ({ month: k, value: avg ? (e.n ? e.sum / e.n : 0) : e.sum }))
}

export type RangeKey = '7' | '30' | 'm' | 'c'

/** Range chips (7 days / 30 days / months / custom ≤ 1 year) and the dates they stand for. */
export function useRange(initial: RangeKey = '7') {
  const today = todayIso()
  const [range, setRange] = useState<RangeKey>(initial)
  const [cFrom, setCFrom] = useState(addDays(today, -29))
  const [cTo, setCTo] = useState(today)
  const customDays = daysBetween(cFrom, cTo)
  const customBad = !cFrom || !cTo || customDays < 0 ? 'Pick a start date before the end date.'
    : customDays > MAX_DAYS ? 'Pick at most 1 year.' : cTo > today ? 'The end date can\'t be in the future.' : null
  const from = range === '7' ? addDays(today, -6) : range === '30' ? addDays(today, -29) : range === 'm' ? twelveMonthsFrom(today) : cFrom
  const to = range === 'c' ? cTo : today
  const ok = range !== 'c' || !customBad
  // 12 months, or a custom range over ~3 months, is shown month by month.
  const monthly = range === 'm' || (range === 'c' && customDays + 1 > 92)
  return { range, setRange, from, to, ok, monthly, today, cFrom, setCFrom, cTo, setCTo, customDays, customBad }
}

/** Short axis label for rupees: 1200 → 1.2k. */
export const kFmt = (v: number) => (Math.abs(v) >= 1000 ? `${Math.round(v / 100) / 10}k` : String(v))

/** Day label for a series: weekday for a week, dd/mm otherwise. */
export const dayTick = (iso: string, short: boolean) =>
  short ? new Date(`${iso}T00:00:00`).toLocaleDateString('en-GB', { weekday: 'short' }) : `${iso.slice(8, 10)}/${iso.slice(5, 7)}`
