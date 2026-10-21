import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Screen, Segmented, TopBar } from '../../components/ui'
import { LoadState, useRecords } from '../../components/records'
import { useStore } from '../../data/store'
import { decimal, useLoad, type BreedPeak, type Check, type GradingRules } from '../../data/records'
import { errorMessage } from '../../lib/api'

/** Expected litres on a lactation day — same curve as the server. */
function expected(r: GradingRules, peak: number, peakDay: number, day: number) {
  const start = r.startPct / 100
  if (day <= peakDay) return peak * (start + ((1 - start) * (day - 1)) / Math.max(1, peakDay - 1))
  return peak * Math.pow(1 - r.fallPctPerMonth / 100, (day - peakDay) / 30.4)
}

function Curve({ r }: { r: GradingRules }) {
  const p = r.breeds[0] ?? r.otherBreeds
  const peak = Number(p.peakLitres) || 1
  const days = Math.max(150, Number(r.lactationDays) || 305)
  const w = 326, h = 110
  const x = (d: number) => 6 + ((d - 1) / (days - 1)) * (w - 12)
  const y = (v: number) => 100 - (v / peak) * 82
  const pts: string[] = []
  for (let d = 1; d <= days; d += 5) pts.push(`${x(d).toFixed(1)},${y(expected(r, peak, p.peakDay, d)).toFixed(1)}`)
  return (
    <div>
      <svg width="100%" viewBox={`0 0 ${w} ${h}`} role="img" aria-label={`Lactation curve for ${p.breed}: rises to ${peak} litres on day ${p.peakDay}, then falls ${r.fallPctPerMonth} percent a month`} className="block">
        <line x1="0" y1="100" x2={w} y2="100" stroke="#E4E6DF" />
        <polyline points={pts.join(' ')} fill="none" stroke="#1F5C3A" strokeWidth="2.5" />
        <line x1={x(p.peakDay)} y1="18" x2={x(p.peakDay)} y2="100" stroke="#C9D0C6" strokeDasharray="3 3" />
        <text x={x(p.peakDay) + 4} y="14" fontSize="11" fill="#3B4A41" fontFamily="Figtree, sans-serif">{p.breed} peak {peak} L</text>
      </svg>
      <div className="flex justify-between text-xs text-ink-3"><span>day 1</span><span>{p.peakDay}</span><span>{Math.round(days / 2)}</span><span>{days}</span></div>
    </div>
  )
}

const num = (v: unknown) => Number(v)

export default function GradeRules() {
  const { rec, isOwner } = useRecords()
  const { notify } = useStore()
  const nav = useNavigate()
  const loaded = useLoad(() => rec.grading(), [rec])
  const [r, setR] = useState<GradingRules | null>(null)
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (loaded.data) setR(loaded.data) }, [loaded.data])

  const set = (p: Partial<GradingRules>) => setR((cur) => (cur ? { ...cur, ...p } : cur))
  const setBreed = (i: number, p: Partial<BreedPeak>) => r && set({ breeds: r.breeds.map((b, j) => (j === i ? { ...b, ...p } : b)) })
  const setCheck = (k: 'minFatPct' | 'minSnfPct' | 'maxSccK', p: Partial<Check>) => r && set({ [k]: { ...r[k], ...p } })
  const field = 'h-10 w-full rounded-lg border border-line text-center text-[15px] disabled:bg-ground'
  const ro = !isOwner

  const save = async () => {
    if (!r) return
    if (!(r.bandA > r.bandB && r.bandB > r.bandC)) { notify('Grade bands must go down: A above B above C'); return }
    if (r.breeds.some((b) => !b.breed.trim())) { notify('Enter a name for each breed'); return }
    setBusy(true)
    try {
      const clean: GradingRules = {
        ...r,
        breeds: r.breeds.map((b) => ({ breed: b.breed.trim(), peakLitres: num(b.peakLitres), peakDay: num(b.peakDay) })),
        otherBreeds: { ...r.otherBreeds, peakLitres: num(r.otherBreeds.peakLitres), peakDay: num(r.otherBreeds.peakDay) },
        startPct: num(r.startPct), fallPctPerMonth: num(r.fallPctPerMonth), firstCalverPct: num(r.firstCalverPct), lactationDays: num(r.lactationDays),
        bandA: num(r.bandA), bandB: num(r.bandB), bandC: num(r.bandC), dropAlertPct: num(r.dropAlertPct),
        minFatPct: { ...r.minFatPct, limit: num(r.minFatPct.limit) }, minSnfPct: { ...r.minSnfPct, limit: num(r.minSnfPct.limit) }, maxSccK: { ...r.maxSccK, limit: num(r.maxSccK.limit) },
      }
      await rec.saveGrading(clean)
      notify('Grade rules saved')
      nav(-1)
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  const bandRow = (g: 'A' | 'B' | 'C', k: 'bandA' | 'bandB' | 'bandC', tone: string) => (
    <div className="grid grid-cols-[44px_1fr_90px] items-center gap-2.5 text-sm">
      <span className={`rounded-lg py-1.5 text-center font-bold ${tone}`}>{g}</span><span>at least (% of expected)</span>
      <input aria-label={`Grade ${g} from percent`} disabled={ro} className={field} inputMode="numeric" value={r?.[k] ?? ''} onChange={(e) => set({ [k]: e.target.value.replace(/\D/g, '') } as never)} />
    </div>
  )
  const checkRow = (k: 'minFatPct' | 'minSnfPct' | 'maxSccK', label: string, unit: string) => (
    <label className="flex min-h-11 items-center gap-2.5 text-sm">
      <input type="checkbox" disabled={ro} checked={r?.[k].enabled ?? false} onChange={(e) => setCheck(k, { enabled: e.target.checked })} className="h-5 w-5 accent-brand" />
      <span className="flex-1">{label}</span>
      <input aria-label={`${label} limit`} disabled={ro} className="h-10 w-[76px] rounded-lg border border-line text-center text-[15px] disabled:bg-ground" inputMode="decimal" value={r?.[k].limit ?? ''} onChange={(e) => setCheck(k, { limit: decimal(e.target.value) as unknown as number })} />
      <span className="w-[38px]">{unit}</span>
    </label>
  )

  return (
    <Screen>
      <TopBar title="Milk grade rules" back={-1} />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <div className="rounded-xl bg-brand-soft px-3.5 py-3 text-sm leading-snug text-[#1C3A28]">Each cow's grade compares what she gave with what she <b>should</b> give at her stage of lactation. Quality checks can then drop the grade.{ro && <> Only owners can change these rules.</>}</div>
        <LoadState loading={loaded.loading} error={loaded.error} onRetry={loaded.reload} empty={!r} />
        {r && (
          <>
            <div className="flex flex-col gap-3 rounded-[14px] bg-white p-3.5">
              <b className="text-[15px]">1 · Expected yield by lactation day</b>
              <div>
                <span className="lab">Base the peak on</span>
                <Segmented label="Peak basis" value={r.basis} onChange={(v) => !ro && set({ basis: v })} options={[{ value: 'OWN_LACTATION', label: "Cow's last lactation" }, { value: 'BREED', label: 'Breed default' }]} />
                <div className="hint">Cows with no earlier lactation use the breed value.</div>
              </div>
              <div className="grid grid-cols-[1.3fr_1fr_1fr_28px] gap-2 text-xs font-bold text-ink-3"><span>Breed</span><span>Peak L/day</span><span>Peak day</span><span /></div>
              {r.breeds.map((b, i) => (
                <div key={i} className="grid grid-cols-[1.3fr_1fr_1fr_28px] items-center gap-2">
                  <input aria-label="Breed" disabled={ro} className="h-10 w-full rounded-lg border border-line px-2 text-[15px] disabled:bg-ground" value={b.breed} onChange={(e) => setBreed(i, { breed: e.target.value })} />
                  <input aria-label={`${b.breed} peak litres`} disabled={ro} className={field} inputMode="decimal" value={b.peakLitres} onChange={(e) => setBreed(i, { peakLitres: decimal(e.target.value) as unknown as number })} />
                  <input aria-label={`${b.breed} peak day`} disabled={ro} className={field} inputMode="numeric" value={b.peakDay} onChange={(e) => setBreed(i, { peakDay: e.target.value.replace(/\D/g, '') as unknown as number })} />
                  {!ro ? <button type="button" aria-label={`Remove ${b.breed}`} onClick={() => set({ breeds: r.breeds.filter((_, j) => j !== i) })} className="h-10 border-0 bg-transparent text-lg text-ink-3">×</button> : <span />}
                </div>
              ))}
              <div className="grid grid-cols-[1.3fr_1fr_1fr_28px] items-center gap-2">
                <span className="text-sm">Other breeds</span>
                <input aria-label="Other breeds peak litres" disabled={ro} className={field} inputMode="decimal" value={r.otherBreeds.peakLitres} onChange={(e) => set({ otherBreeds: { ...r.otherBreeds, peakLitres: decimal(e.target.value) as unknown as number } })} />
                <input aria-label="Other breeds peak day" disabled={ro} className={field} inputMode="numeric" value={r.otherBreeds.peakDay} onChange={(e) => set({ otherBreeds: { ...r.otherBreeds, peakDay: e.target.value.replace(/\D/g, '') as unknown as number } })} />
                <span />
              </div>
              {!ro && <button type="button" onClick={() => set({ breeds: [...r.breeds, { breed: '', peakLitres: 10, peakDay: 50 }] })} className="h-9 self-start border-0 bg-transparent p-0 text-sm font-semibold text-brand">+ Add breed</button>}
              <div className="hint mt-0">A cow's breed matches by name; crossbreds (e.g. "HF × Jersey") use the Crossbred row.</div>
              <div className="grid grid-cols-2 gap-2.5">
                <div><label className="lab" htmlFor="gr-fall">Fall after peak (%/month)</label><input className="inp disabled:bg-ground" disabled={ro} id="gr-fall" inputMode="decimal" value={r.fallPctPerMonth} onChange={(e) => set({ fallPctPerMonth: decimal(e.target.value) as unknown as number })} /></div>
                <div><label className="lab" htmlFor="gr-first">First-calver peak (% of breed)</label><input className="inp disabled:bg-ground" disabled={ro} id="gr-first" inputMode="numeric" value={r.firstCalverPct} onChange={(e) => set({ firstCalverPct: e.target.value.replace(/\D/g, '') as unknown as number })} /></div>
                <div><label className="lab" htmlFor="gr-start">Day 1 (% of peak)</label><input className="inp disabled:bg-ground" disabled={ro} id="gr-start" inputMode="numeric" value={r.startPct} onChange={(e) => set({ startPct: e.target.value.replace(/\D/g, '') as unknown as number })} /></div>
                <div><label className="lab" htmlFor="gr-len">Lactation length (days)</label><input className="inp disabled:bg-ground" disabled={ro} id="gr-len" inputMode="numeric" value={r.lactationDays} onChange={(e) => set({ lactationDays: e.target.value.replace(/\D/g, '') as unknown as number })} /></div>
              </div>
              <Curve r={{ ...r, startPct: num(r.startPct) || 70, fallPctPerMonth: num(r.fallPctPerMonth) || 0, lactationDays: num(r.lactationDays) || 305 }} />
              <div className="hint mt-0">After the lactation length a cow is flagged for dry-off, not graded.</div>
            </div>

            <div className="flex flex-col gap-3 rounded-[14px] bg-white p-3.5">
              <b className="text-[15px]">2 · Grade by % of expected</b>
              {bandRow('A', 'bandA', 'bg-brand-soft text-brand')}
              {bandRow('B', 'bandB', 'bg-info-soft text-info-text')}
              {bandRow('C', 'bandC', 'bg-warn-soft text-warn-ink')}
              <div className="grid grid-cols-[44px_1fr_90px] items-center gap-2.5 text-sm"><span className="rounded-lg bg-danger-soft py-1.5 text-center font-bold text-danger-text">D</span><span>below C</span><span className="text-center text-ink-3">&lt; {r.bandC} %</span></div>
              <label className="flex items-center gap-2.5 text-sm">
                <span className="flex-1">Warn on the milking sheet when a cow drops by</span>
                <input aria-label="Drop alert percent" disabled={ro} className="h-10 w-[64px] rounded-lg border border-line text-center disabled:bg-ground" inputMode="numeric" value={r.dropAlertPct} onChange={(e) => set({ dropAlertPct: e.target.value.replace(/\D/g, '') as unknown as number })} /> %
              </label>
            </div>

            <div className="flex flex-col gap-1 rounded-[14px] bg-white p-3.5">
              <b className="mb-1 text-[15px]">3 · Quality checks (drop one grade)</b>
              {checkRow('minFatPct', 'Fat below', '%')}
              {checkRow('minSnfPct', 'SNF below', '%')}
              {checkRow('maxSccK', 'SCC above', 'k/mL')}
              <div className="hint">Checks only apply on days quality was tested. Cows in milk withdrawal are not graded.</div>
            </div>
          </>
        )}
      </div>
      {isOwner && r && (
        <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
          <button type="button" className="btn" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save rules'}</button>
          <div className="pt-2.5 text-center text-xs text-ink-3">Grades are worked out again with the new rules.</div>
        </div>
      )}
    </Screen>
  )
}
