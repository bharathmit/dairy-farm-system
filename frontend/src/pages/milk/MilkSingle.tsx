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
  const [prefilled, setPrefilled] = useState(false)
  const [amOn, setAmOn] = useState(true)
  const [pmOn, setPmOn] = useState(true)

  useEffect(() => {
    const fd = animalId ? form.data : null
    // Tick the sessions that can still be changed; a sold session stays locked.
    setAmOn(!fd?.amSold); setPmOn(!fd?.pmSold)
    const d = fd?.day
    // Nothing saved for this date yet: start from her last values (normally yesterday).
    const preAm = d?.am == null && !fd?.amSold && fd?.lastAm != null
    const prePm = d?.pm == null && !fd?.pmSold && fd?.lastPm != null
    setAm(d?.am != null ? d.am.toFixed(1) : preAm ? fd!.lastAm!.toFixed(1) : '')
    setPm(d?.pm != null ? d.pm.toFixed(1) : prePm ? fd!.lastPm!.toFixed(1) : '')
    setPrefilled(preAm || prePm)
    setFat(numText(d?.fatPct)); setSnf(numText(d?.snfPct)); setProtein(numText(d?.proteinPct))
    setDensity(numText(d?.density)); setScc(numText(d?.scc)); setNotes(d?.notes ?? '')
  }, [form.data, animalId])

  // Changing back to "Choose a milking cow" must not keep showing the last cow.
  const f = animalId ? form.data : null
  const exp = f?.expectation
  const amSold = Boolean(f?.amSold), pmSold = Boolean(f?.pmSold)
  const amUse = amOn && !amSold, pmUse = pmOn && !pmSold
  const total = (amUse ? toNum(am) ?? 0 : 0) + (pmUse ? toNum(pm) ?? 0 : 0)
  const fatN = toNum(fat)
  // Grade only what is ticked: both → day grade; one → that session against half the expected day.
  const gradeLitres = amUse && pmUse ? total : amUse || pmUse ? total * 2 : 0
  const g = exp && !exp.pastLactation && !f?.inWithdrawal ? liveGrade(rules.data, gradeLitres, exp.expectedLitres, fatN, toNum(snf), toNum(scc)) : null
  const locked = amSold && pmSold
  const fatWarn = fatN !== undefined && rules.data?.minFatPct.enabled && fatN < rules.data.minFatPct.limit

  const save = async () => {
    if (!animalId) { notify('Choose a cow'); return }
    if (locked) { notify('Milk for this date is sold — it can\'t be changed'); return }
    if (!amUse && !pmUse) { notify('Tick morning, evening or both'); return }
    if ((amUse ? toNum(am) : undefined) === undefined && (pmUse ? toNum(pm) : undefined) === undefined) { notify('Enter litres for the ticked session'); return }
    setBusy(true)
    try {
      await rec.saveCowDay(animalId, date, {
        sessions: [...(amUse ? ['AM'] : []), ...(pmUse ? ['PM'] : [])],
        am: amUse ? toNum(am) ?? null : null, pm: pmUse ? toNum(pm) ?? null : null, fatPct: fatN ?? null, snfPct: toNum(snf) ?? null,
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
        <DateNav id="mc-date" value={date} onChange={(v) => setParam('date', v)} />
        <div><label className="lab" htmlFor="mc-cow">Cow *</label><AnimalSelect id="mc-cow" value={animalId} onChange={(v) => setParam('animal', v)} filter={isMilkingCow} placeholder="Choose a milking cow" /></div>
        {!animalId && <p className="m-0 text-sm text-ink-3">Choose a cow to log her milk and quality.</p>}
        {animalId && <LoadState loading={form.loading} error={form.error} onRetry={form.reload} empty={!f} />}
        {f && (
          <>
            {f.inWithdrawal && <div className="rounded-xl bg-danger-soft px-3.5 py-3 text-sm text-danger-text"><b>Milk withdrawal</b> — milk is discarded{f.withdrawalUntil ? ` until ${shortDate(f.withdrawalUntil)}` : ''} and not graded.</div>}
            {prefilled && <div className="text-[13px] text-ink-3">Pre-filled from her last morning/evening values — check and save.</div>}
            {(amSold || pmSold) && <div className="rounded-xl bg-warn-soft px-3.5 py-3 text-sm text-[#4A3210]"><b>{locked ? 'Day' : amSold ? 'Morning' : 'Evening'} milk is sold</b> — {locked ? 'this day' : 'that session'} can't be changed. <Link to={`/milk/sale?date=${date}`} className="font-semibold">Open sale</Link></div>}
            {([['AM', 'Morning', am, setAm, amOn, setAmOn, amSold, f.lastAm], ['PM', 'Evening', pm, setPm, pmOn, setPmOn, pmSold, f.lastPm]] as const).map(([k, label, v, set, on, setOn, soldS, last]) => (
              <div key={k} className={`rounded-[14px] bg-white p-3.5 ${on && !soldS ? '' : 'opacity-60'}`}>
                <div className="flex items-center justify-between">
                  <label className="flex min-h-10 items-center gap-2.5 font-bold"><input type="checkbox" checked={on && !soldS} disabled={soldS} onChange={(e) => setOn(e.target.checked)} className="h-5 w-5 accent-brand" /> {label}{soldS ? <span className="text-[13px] font-semibold text-warn-ink"> · sold</span> : null}</label>
                  <span className="text-[13px] text-ink-3">last {L(last)} L</span>
                </div>
                {on && !soldS
                  ? <div className="mt-2.5"><LitresStepper big label={`${label} litres`} value={v} onChange={set} /></div>
                  : <div className="mt-1 text-sm text-ink-3">{v ? `${v} L saved` : 'Not logged'}{soldS ? '' : ' — tick to change'}</div>}
              </div>
            ))}

            <div className="rounded-[14px] bg-white p-3.5">
              <div className="flex items-baseline justify-between"><b>Expected yield</b><span className="text-[13px] text-ink-3">{exp ? `day ${exp.daysInMilk} of lactation${amUse !== pmUse ? ` · ${amUse ? 'morning' : 'evening'} only` : ''}` : ''}</span></div>
              {exp ? (
                <>
                  <div className="mt-2.5 grid grid-cols-2 gap-3">
                    <div><label className="lab" htmlFor="mc-exp">Expected ({amUse !== pmUse ? 'L/session' : 'L/day'})</label><input className="inp bg-ground" id="mc-exp" readOnly value={L(amUse !== pmUse ? exp.expectedLitres / 2 : exp.expectedLitres)} /></div>
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
                <span className="flex-1 text-sm leading-snug"><b>{amUse !== pmUse ? `${amUse ? 'Morning' : 'Evening'} grade` : 'Grade'} {g.grade}</b> · {g.pct} % of expected ({g.band} is {g.range})
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
        <div className="mb-2.5 flex justify-between text-[15px]"><span>{amUse && pmUse ? 'Day total' : amUse ? 'Morning' : pmUse ? 'Evening' : 'Nothing ticked'}{exp ? ` · expected ${L(amUse !== pmUse ? exp.expectedLitres / 2 : exp.expectedLitres)} L` : ''}</span><b>{L(total)} L{g ? ` · ${g.grade}` : ''}</b></div>
        <button type="button" className="btn" disabled={busy || !f || locked} onClick={save}>{locked ? 'Sold — can\'t change' : busy ? 'Saving…' : 'Save'}</button>
      </div>
    </Screen>
  )
}
