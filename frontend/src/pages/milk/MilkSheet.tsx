import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ActionBar, Screen, Segmented, TopBar } from '../../components/ui'
import { DateNav, LoadState, useRecords } from '../../components/records'
import { Icon } from '../../components/icons'
import { useStore } from '../../data/store'
import { decimal, L, shortDate, STAGES, todayIso, toNum, useLoad, type Session, type SheetCow, type Stage } from '../../data/records'
import { errorMessage } from '../../lib/api'

export default function MilkSheet() {
  const { rec } = useRecords()
  const { notify } = useStore()
  const nav = useNavigate()
  const [params, setParams] = useSearchParams()
  const date = params.get('date') || todayIso()
  const session: Session = params.get('session') === 'PM' ? 'PM' : params.get('session') === 'AM' ? 'AM' : new Date().getHours() >= 12 ? 'PM' : 'AM'
  const stage = (STAGES.some((s) => s.value === params.get('stage')) ? params.get('stage') : '') as Stage | ''
  const setParam = (k: string, v: string) => { const p = new URLSearchParams(params); if (v) p.set(k, v); else p.delete(k); setParams(p, { replace: true }) }

  const sheet = useLoad(() => rec.sheet(date, session), [rec, date, session])
  const rules = useLoad(() => rec.grading(), [rec])
  const drop = rules.data?.dropAlertPct ?? 20

  const [values, setValues] = useState<Record<number, string>>({})
  const [skipped, setSkipped] = useState<Set<number>>(new Set())
  const [busy, setBusy] = useState(false)
  const [prefilled, setPrefilled] = useState(false)

  useEffect(() => {
    const s = sheet.data
    if (!s) return
    const v: Record<number, string> = {}
    let pre = false
    const sk = new Set<number>()
    for (const c of s.cows) {
      if (c.litres != null) v[c.animalId] = c.litres.toFixed(1)
      else if (c.lastLitres != null) { v[c.animalId] = c.lastLitres.toFixed(1); pre = true }
      else v[c.animalId] = ''
    }
    // A session that was saved before: cows without a value were skipped.
    if (s.saved) {
      for (const c of s.cows) if (c.litres == null) sk.add(c.animalId)
    }
    setValues(v); setSkipped(sk); setPrefilled(pre)
  }, [sheet.data])

  const sold = Boolean(sheet.data?.sold)
  // Only the chosen stage is shown and saved; panels run Fresh → Early → Mid → Late.
  const cows = useMemo(() => (sheet.data?.cows ?? []).filter((c) => !stage || c.stage === stage), [sheet.data, stage])
  const panels = useMemo(() => STAGES.map((st) => ({ ...st, cows: cows.filter((c) => c.stage === st.value) })).filter((p) => p.cows.length > 0), [cows])
  const totals = useMemo(() => {
    let sum = 0, withheld = 0, count = 0, saved = 0
    for (const c of cows) {
      saved += c.litres ?? 0
      if (skipped.has(c.animalId)) continue
      const n = toNum(values[c.animalId] ?? '')
      if (n === undefined) continue
      sum += n; count++
      if (c.inWithdrawal) withheld += n
    }
    return { sum, withheld, count, saved }
  }, [cows, values, skipped])
  // Top tiles: saved values only. Bottom bar: live values as you edit.
  const savedHere = sheet.data?.sessionLitres ?? 0
  const other = sheet.data?.otherSessionLitres ?? 0
  const liveSession = savedHere - totals.saved + totals.sum
  const name = session === 'AM' ? 'Morning' : 'Evening'
  const otherName = session === 'AM' ? 'Evening' : 'Morning'

  const step = (id: number, d: number) => setValues((v) => ({ ...v, [id]: Math.max(0, Math.round(((toNum(v[id] ?? '') ?? 0) + d) * 10) / 10).toFixed(1) }))
  const skip = (id: number, on: boolean) => setSkipped((s) => { const n = new Set(s); if (on) n.add(id); else n.delete(id); return n })

  const save = async () => {
    if (sold) { notify(`${name} milk is already sold — it can't be changed`); return }
    const entries = cows.map((c) => ({ animalId: c.animalId, litres: skipped.has(c.animalId) ? null : toNum(values[c.animalId] ?? '') ?? null }))
    if (!entries.some((e) => e.litres !== null)) { notify('Enter litres for at least one cow'); return }
    setBusy(true)
    try {
      await rec.saveSheet(date, session, entries)
      notify(`${name} milk saved for ${entries.filter((e) => e.litres !== null).length} cows`)
      nav('/milk')
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  const row = (c: SheetCow) => {
    const isSkipped = skipped.has(c.animalId)
    const n = toNum(values[c.animalId] ?? '')
    const usual = c.usualLitres ?? undefined
    const low = !isSkipped && n !== undefined && usual && n < usual * (1 - drop / 100)
    const edited = !isSkipped && n !== undefined && c.lastLitres != null && n !== c.lastLitres && c.litres == null
    if (isSkipped) {
      return (
        <div key={c.animalId} className="flex items-center gap-2.5 rounded-[14px] bg-white p-3 opacity-60">
          <div className="min-w-0 flex-1"><div className="font-bold">{c.tag} <span className="font-medium text-ink-2">{c.name}</span></div><div className="mt-0.5 text-xs text-ink-3">Skipped — not milked</div></div>
          <button type="button" disabled={sold} onClick={() => skip(c.animalId, false)} className="h-11 rounded-xl border border-line bg-white px-3 text-sm font-semibold">Undo skip</button>
        </div>
      )
    }
    return (
      <div key={c.animalId}>
        <div className={`flex items-center gap-2.5 rounded-[14px] bg-white p-3 pl-3.5 ${edited ? 'outline outline-2 outline-brand' : ''}`}>
          <div className="min-w-0 flex-1">
            <div className="truncate font-bold">{c.tag} <span className="font-medium text-ink-2">{c.name}</span></div>
            {c.inWithdrawal
              ? <div className="mt-0.5 text-xs font-semibold text-danger-text">Withdrawal — milk discarded</div>
              : <div className="mt-0.5 text-xs text-ink-3">Last {L(c.lastLitres)} L{edited ? ' · edited' : ''}{!sold && <> · <button type="button" onClick={() => skip(c.animalId, true)} className="border-0 bg-transparent p-0 text-xs font-semibold text-brand">Skip</button></>}</div>}
          </div>
          <button type="button" disabled={sold} aria-label={`Less for ${c.tag}`} onClick={() => step(c.animalId, -0.5)} className="flex h-11 w-11 items-center justify-center rounded-xl border border-line bg-white text-[22px] font-semibold">−</button>
          <input aria-label={`Litres for ${c.tag}`} readOnly={sold} inputMode="decimal" value={values[c.animalId] ?? ''} onChange={(e) => setValues((v) => ({ ...v, [c.animalId]: decimal(e.target.value) }))}
            className="box-border h-11 w-[72px] rounded-[10px] border border-line text-center text-xl font-bold" />
          <button type="button" disabled={sold} aria-label={`More for ${c.tag}`} onClick={() => step(c.animalId, 0.5)} className="flex h-11 w-11 items-center justify-center rounded-xl border border-line bg-white text-[22px] font-semibold">+</button>
        </div>
        {low && <div className="ml-1 mt-1 text-xs font-semibold text-danger">{L(n)} L is {Math.round((1 - n! / usual!) * 100)} % below her usual — check her health?</div>}
        {c.inWithdrawal && c.withdrawalUntil && <div className="ml-1 mt-1 text-xs text-ink-3">Withdrawal ends {shortDate(c.withdrawalUntil)}</div>}
      </div>
    )
  }

  return (
    <Screen>
      <TopBar title="Milking sheet" close="/milk" right={<Link to="/animals/scan" aria-label="Scan ear tag" className="flex h-11 w-11 items-center justify-center rounded-xl"><Icon name="scan" /></Link>} />
      <div className="flex flex-col gap-2.5 px-4">
        <DateNav id="ms-date" value={date} onChange={(v) => setParam('date', v)} />
        <div>
          <label className="lab" htmlFor="ms-group">Group</label>
          <select className="inp" id="ms-group" value={stage} onChange={(e) => setParam('stage', e.target.value)}>
            <option value="">All milking cows</option>
            {STAGES.map((g) => <option key={g.value} value={g.value}>{g.label}</option>)}
          </select>
        </div>
        <div className="grid grid-cols-3 gap-2">
          <div className={`rounded-xl bg-white px-3 py-2.5 ${session === 'AM' ? 'outline outline-2 outline-brand' : ''}`}><div className="text-xs font-semibold text-ink-3">Morning</div><div className="mt-0.5 text-lg font-bold">{L(session === 'AM' ? savedHere : other)} L</div></div>
          <div className={`rounded-xl bg-white px-3 py-2.5 ${session === 'PM' ? 'outline outline-2 outline-brand' : ''}`}><div className="text-xs font-semibold text-ink-3">Evening</div><div className="mt-0.5 text-lg font-bold">{L(session === 'PM' ? savedHere : other)} L</div></div>
          <div className="rounded-xl bg-brand-soft px-3 py-2.5"><div className="text-xs font-semibold text-[#1C3A28]">Day total</div><div className="mt-0.5 text-lg font-bold text-brand">{L(savedHere + other)} L</div></div>
        </div>
        <div className="-mt-1 text-xs text-ink-3">Saved totals{sheet.data && !sheet.data.saved ? ` · ${name.toLowerCase()} not saved yet` : ''}</div>
        <Segmented label="Session" value={session} onChange={(v) => setParam('session', v)} options={[
          { value: 'AM', label: `Morning${session === 'PM' && other > 0 ? ' · done' : ''}` },
          { value: 'PM', label: `Evening${session === 'AM' && other > 0 ? ' · done' : ''}` },
        ]} />
        {sold && <div className="rounded-xl bg-warn-soft px-3.5 py-3 text-sm text-[#4A3210]"><b>{name} milk is sold</b> — this log can't be changed. Delete or edit the sale first. <Link to={`/milk/sale?date=${date}&session=${session}`} className="font-semibold">Open sale</Link></div>}
        {prefilled && !sold && <div className="text-[13px] text-ink-3">Pre-filled from each cow's last {name.toLowerCase()} record. Tap − / + or type.</div>}
      </div>

      <div className="flex flex-col gap-2 px-4 py-3">
        <LoadState loading={sheet.loading} error={sheet.error} onRetry={sheet.reload} empty={!sheet.data} />
        {sheet.data && cows.length === 0 && (
          <div className="rounded-[14px] bg-white p-5 text-center">
            <div className="font-bold">No milking cows{stage ? ' in this group' : ''}</div>
            <p className="m-0 mt-1 text-sm text-ink-3">Cows must be female and in the Milking category.</p>
            <Link to="/animals" className="mt-2 inline-block font-semibold">Go to herd</Link>
          </div>
        )}
        {panels.map((p) => (
          <section key={p.value} className="flex flex-col gap-2" aria-label={p.label}>
            <div className="mt-1 flex items-baseline justify-between px-1"><b className="text-[15px]">{p.label}</b><span className="text-[13px] text-ink-3">{p.cows.length} cow{p.cows.length > 1 ? 's' : ''}</span></div>
            {p.cows.map(row)}
          </section>
        ))}
      </div>

      <ActionBar>
        <div className="w-full">
          <div className="mb-2.5 flex justify-between text-[15px]">
            <span>{name} · {totals.count} cows<br /><span className="text-[13px] text-ink-3">Day total {L(liveSession + other)} L · {otherName.toLowerCase()} {L(other)} L</span></span>
            <b>{L(totals.sum)} L {totals.withheld > 0 && <span className="text-[13px] font-medium text-ink-3">({L(totals.withheld)} L discarded)</span>}</b>
          </div>
          <button type="button" className="btn" disabled={busy || sold || !sheet.data || cows.length === 0} onClick={save}>{sold ? `${name} sold — can't change` : busy ? 'Saving…' : `Save ${name.toLowerCase()} for ${stage ? STAGES.find((x) => x.value === stage)!.label.toLowerCase() : 'all cows'}`}</button>
        </div>
      </ActionBar>
    </Screen>
  )
}
