import { useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Screen, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { AnimalSelect, DateNav, GradeBadge, isMilkingCow, LitresStepper, LoadState, useRecords } from '../../components/records'
import { useStore } from '../../data/store'
import { decimal, L, numText, shortDate, todayIso, toNum, useLoad, type Grade, type GradingRules } from '../../data/records'
import { errorMessage } from '../../lib/api'

const SOURCE: Record<string, string> = {
  OWN_LACTATION: 'her last lactation peak',
  BREED: 'the breed peak',
  FIRST_CALVER: 'the first-calver peak',
}

/** Same rule as the server, so the grade shows while typing. */
function liveGrade(rules: GradingRules | null, total: number, expected: number, fat?: number, snf?: number, scc?: number) {
  if (!rules || !(expected > 0) || !(total > 0)) return null
  const p = Math.round((total / expected) * 100)
  const bands: Grade[] = ['A', 'B', 'C', 'D']
  let i = p >= rules.bandA ? 0 : p >= rules.bandB ? 1 : p >= rules.bandC ? 2 : 3
  const band = bands[i]
  const issues: string[] = []
  if (fat !== undefined && rules.minFatPct.enabled && fat < rules.minFatPct.limit) issues.push(`Fat ${fat} % is below ${rules.minFatPct.limit} %`)
  if (snf !== undefined && rules.minSnfPct.enabled && snf < rules.minSnfPct.limit) issues.push(`SNF ${snf} % is below ${rules.minSnfPct.limit} %`)
  if (scc !== undefined && rules.maxSccK.enabled && scc > rules.maxSccK.limit) issues.push(`SCC ${scc}k is above ${rules.maxSccK.limit}k`)
  i = Math.min(3, i + issues.length)
  const range = band === 'A' ? `${rules.bandA} %+` : band === 'B' ? `${rules.bandB}–${rules.bandA - 1} %` : band === 'C' ? `${rules.bandC}–${rules.bandB - 1} %` : `below ${rules.bandC} %`
  return { grade: bands[i], pct: p, band, range, issues }
}

export default function MilkSingle() {
  const { rec } = useRecords()
  const { notify } = useStore()
  const nav = useNavigate()
  const [params, setParams] = useSearchParams()
  const animalId = params.get('animal') ?? ''
  const date = params.get('date') || todayIso()
  const setParam = (k: string, v: string) => { const p = new URLSearchParams(params); if (v) p.set(k, v); else p.delete(k); setParams(p, { replace: true }) }

  const form = useLoad(animalId ? () => rec.cowDay(animalId, date) : null, [rec, animalId, date])
  const rules = useLoad(() => rec.grading(), [rec])

  const [am, setAm] = useState('')
  const [pm, setPm] = useState('')
  const [fat, setFat] = useState('')
  const [snf, setSnf] = useState('')
  const [protein, setProtein] = useState('')
  const [density, setDensity] = useState('')
  const [scc, setScc] = useState('')
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const d = form.data?.day
    setAm(d?.am != null ? d.am.toFixed(1) : ''); setPm(d?.pm != null ? d.pm.toFixed(1) : '')
    setFat(numText(d?.fatPct)); setSnf(numText(d?.snfPct)); setProtein(numText(d?.proteinPct))
    setDensity(numText(d?.density)); setScc(numText(d?.scc)); setNotes(d?.notes ?? '')
  }, [form.data])

  const f = form.data
  const exp = f?.expectation
  const total = (toNum(am) ?? 0) + (toNum(pm) ?? 0)
  const fatN = toNum(fat)
  const g = exp && !exp.pastLactation && !f?.inWithdrawal ? liveGrade(rules.data, total, exp.expectedLitres, fatN, toNum(snf), toNum(scc)) : null
  const fatWarn = fatN !== undefined && rules.data?.minFatPct.enabled && fatN < rules.data.minFatPct.limit

  const save = async () => {
    if (!animalId) { notify('Choose a cow'); return }
    if (toNum(am) === undefined && toNum(pm) === undefined) { notify('Enter morning or evening litres'); return }
    setBusy(true)
    try {
      await rec.saveCowDay(animalId, date, {
        am: toNum(am) ?? null, pm: toNum(pm) ?? null, fatPct: fatN ?? null, snfPct: toNum(snf) ?? null,
        proteinPct: toNum(protein) ?? null, density: toNum(density) ?? null, scc: toNum(scc) ?? null, notes: notes.trim() || null,
      })
      notify(`Milk saved for ${f?.animal.tag ?? 'the cow'}`)
      nav('/milk')
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  const q = (id: string, label: string, v: string, set: (s: string) => void, ph: string, extra?: ReactNode, warn?: boolean) => (
    <div><label className="lab" htmlFor={id}>{label}</label><input className={`inp ${warn ? 'border-[#C2410C]' : ''}`} id={id} inputMode="decimal" placeholder={ph} value={v} onChange={(e) => set(decimal(e.target.value))} />{extra}</div>
  )

  return (
    <Screen>
      <TopBar title="Milk — one cow" close="/milk" />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        <div><label className="lab" htmlFor="mc-cow">Cow *</label><AnimalSelect id="mc-cow" value={animalId} onChange={(v) => setParam('animal', v)} filter={isMilkingCow} placeholder="Choose a milking cow" /></div>
        <DateNav id="mc-date" value={date} onChange={(v) => setParam('date', v)} />
        {!animalId && <p className="m-0 text-sm text-ink-3">Choose a cow to log her milk and quality.</p>}
        {animalId && <LoadState loading={form.loading} error={form.error} onRetry={form.reload} empty={!f} />}
        {f && (
          <>
            {f.inWithdrawal && <div className="rounded-xl bg-danger-soft px-3.5 py-3 text-sm text-danger-text"><b>Milk withdrawal</b> — milk is discarded{f.withdrawalUntil ? ` until ${shortDate(f.withdrawalUntil)}` : ''} and not graded.</div>}
            <div className="rounded-[14px] bg-white p-3.5">
              <div className="flex items-center justify-between"><b>Morning</b><span className="text-[13px] text-ink-3">last {L(f.lastAm)} L</span></div>
              <div className="mt-2.5"><LitresStepper big label="Morning litres" value={am} onChange={setAm} /></div>
            </div>
            <div className="rounded-[14px] bg-white p-3.5">
              <div className="flex items-center justify-between"><b>Evening</b><span className="text-[13px] text-ink-3">last {L(f.lastPm)} L</span></div>
              <div className="mt-2.5"><LitresStepper big label="Evening litres" value={pm} onChange={setPm} /></div>
            </div>

            <div className="rounded-[14px] bg-white p-3.5">
              <div className="flex items-baseline justify-between"><b>Expected yield</b><span className="text-[13px] text-ink-3">{exp ? `day ${exp.daysInMilk} of lactation` : ''}</span></div>
              {exp ? (
                <>
                  <div className="mt-2.5 grid grid-cols-2 gap-3">
                    <div><label className="lab" htmlFor="mc-exp">Expected (L/day)</label><input className="inp bg-ground" id="mc-exp" readOnly value={L(exp.expectedLitres)} /></div>
                    <div><span className="lab">Actual</span><div className="flex h-12 items-center gap-2 text-xl font-bold">{L(total)} L {g && <span className="text-sm text-info-text">{g.pct} %</span>}</div></div>
                  </div>
                  <div className="mt-2 text-xs leading-relaxed text-ink-3">
                    {exp.pastLactation ? 'Past the end of lactation — due for dry-off, not graded. ' : `From ${SOURCE[exp.source]} ${L(exp.peakLitres)} L at day ${exp.peakDay}${rules.data ? `, falling ${rules.data.fallPctPerMonth} % a month after peak` : ''}. `}
                    <Link to="/settings/milk-grading" className="font-semibold">Grade rules</Link>
                  </div>
                </>
              ) : (
                <div className="mt-1.5 text-sm text-ink-3">Add her last calving date on the animal's page to see the expected yield and grade. <Link to={`/animals/${animalId}/edit`} className="font-semibold">Edit animal</Link></div>
              )}
            </div>

            <div className="rounded-[14px] bg-white p-3.5">
              <div className="flex items-center justify-between"><b>Milk quality</b><span className="text-[13px] text-ink-3">optional</span></div>
              <div className="mt-3 grid grid-cols-2 gap-3">
                {q('mc-fat', 'Fat %', fat, setFat, '4.0', fatWarn ? <div className="mt-1 text-xs font-semibold text-[#9A3412]">Below {rules.data?.minFatPct.limit} — drops the grade</div> : undefined, Boolean(fatWarn))}
                {q('mc-snf', 'SNF %', snf, setSnf, '8.5')}
                {q('mc-pro', 'Protein %', protein, setProtein, '3.2')}
                {q('mc-den', 'Density g/mL', density, setDensity, '1.030')}
                {q('mc-scc', 'SCC (k/mL)', scc, setScc, 'weekly')}
              </div>
            </div>

            {g && (
              <Link to="/settings/milk-grading" className="flex items-center gap-3 rounded-[14px] bg-white p-3.5 text-ink no-underline hover:text-ink">
                <GradeBadge grade={g.grade} large />
                <span className="flex-1 text-sm leading-snug"><b>Grade {g.grade}</b> · {g.pct} % of expected ({g.band} is {g.range})
                  {g.issues.length > 0 && <><br /><span className="text-[#9A3412]">{g.issues.join(' · ')} — down {g.issues.length} grade{g.issues.length > 1 ? 's' : ''}</span></>}
                </span>
                <Icon name="chevron" size={18} color="#56655B" />
              </Link>
            )}

            <div><label className="lab" htmlFor="mc-notes">Notes</label><input className="inp" id="mc-notes" placeholder="Optional" value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
          </>
        )}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <div className="mb-2.5 flex justify-between text-[15px]"><span>Day total{exp ? ` · expected ${L(exp.expectedLitres)} L` : ''}</span><b>{L(total)} L{g ? ` · ${g.grade}` : ''}</b></div>
        <button type="button" className="btn" disabled={busy || !f} onClick={save}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </Screen>
  )
}
