import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ActionBar, Screen, Segmented, TopBar } from '../../components/ui'
import { DateNav, LoadState, useRecords } from '../../components/records'
import { Icon } from '../../components/icons'
import { useStore } from '../../data/store'
import { decimal, L, shortDate, todayIso, toNum, useLoad, type Session } from '../../data/records'
import { errorMessage } from '../../lib/api'

export default function MilkSheet() {
  const { rec } = useRecords()
  const { groups, notify } = useStore()
  const nav = useNavigate()
  const [params, setParams] = useSearchParams()
  const date = params.get('date') || todayIso()
  const session: Session = params.get('session') === 'PM' ? 'PM' : params.get('session') === 'AM' ? 'AM' : new Date().getHours() >= 12 ? 'PM' : 'AM'
  const groupId = params.get('group') ?? ''
  const setParam = (k: string, v: string) => { const p = new URLSearchParams(params); if (v) p.set(k, v); else p.delete(k); setParams(p, { replace: true }) }

  const sheet = useLoad(() => rec.sheet(date, session, groupId || undefined), [rec, date, session, groupId])
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

  const cows = useMemo(() => sheet.data?.cows ?? [], [sheet.data])
  const totals = useMemo(() => {
    let sum = 0, withheld = 0, count = 0
    for (const c of cows) {
      if (skipped.has(c.animalId)) continue
      const n = toNum(values[c.animalId] ?? '')
      if (n === undefined) continue
      sum += n; count++
      if (c.inWithdrawal) withheld += n
    }
    return { sum, withheld, count }
  }, [cows, values, skipped])
  const other = sheet.data?.otherSessionLitres ?? 0
  const name = session === 'AM' ? 'Morning' : 'Evening'
  const otherName = session === 'AM' ? 'Evening' : 'Morning'

  const step = (id: number, d: number) => setValues((v) => ({ ...v, [id]: Math.max(0, Math.round(((toNum(v[id] ?? '') ?? 0) + d) * 10) / 10).toFixed(1) }))
  const skip = (id: number, on: boolean) => setSkipped((s) => { const n = new Set(s); if (on) n.add(id); else n.delete(id); return n })

  const save = async () => {
    const entries = cows.map((c) => ({ animalId: c.animalId, litres: skipped.has(c.animalId) ? null : toNum(values[c.animalId] ?? '') ?? null }))
    if (!entries.some((e) => e.litres !== null)) { notify('Enter litres for at least one cow'); return }
    setBusy(true)
    try {
      await rec.saveSheet(date, session, entries)
      notify(`${name} milk saved for ${entries.filter((e) => e.litres !== null).length} cows`)
      nav('/milk')
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title="Milking sheet" close="/milk" right={<Link to="/animals/scan" aria-label="Scan ear tag" className="flex h-11 w-11 items-center justify-center rounded-xl"><Icon name="scan" /></Link>} />
      <div className="flex flex-col gap-2.5 px-4">
        <DateNav id="ms-date" value={date} onChange={(v) => setParam('date', v)} />
        <div>
          <label className="lab" htmlFor="ms-group">Group</label>
          <select className="inp" id="ms-group" value={groupId} onChange={(e) => setParam('group', e.target.value)}>
            <option value="">All milking cows</option>
            {groups.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
          </select>
        </div>
        <div className="grid grid-cols-3 gap-2">
          <div className={`rounded-xl bg-white px-3 py-2.5 ${session === 'AM' ? 'outline outline-2 outline-brand' : ''}`}><div className="text-xs font-semibold text-ink-3">Morning</div><div className="mt-0.5 text-lg font-bold">{L(session === 'AM' ? totals.sum : other)} L</div></div>
          <div className={`rounded-xl bg-white px-3 py-2.5 ${session === 'PM' ? 'outline outline-2 outline-brand' : ''}`}><div className="text-xs font-semibold text-ink-3">Evening</div><div className="mt-0.5 text-lg font-bold">{L(session === 'PM' ? totals.sum : other)} L</div></div>
          <div className="rounded-xl bg-brand-soft px-3 py-2.5"><div className="text-xs font-semibold text-[#1C3A28]">Day total</div><div className="mt-0.5 text-lg font-bold text-brand">{L(totals.sum + other)} L</div></div>
        </div>
        <Segmented label="Session" value={session} onChange={(v) => setParam('session', v)} options={[
          { value: 'AM', label: `Morning${session === 'PM' && other > 0 ? ' · done' : ''}` },
          { value: 'PM', label: `Evening${session === 'AM' && other > 0 ? ' · done' : ''}` },
        ]} />
        {prefilled && <div className="text-[13px] text-ink-3">Pre-filled from each cow's last {name.toLowerCase()} record. Tap − / + or type.</div>}
      </div>

      <div className="flex flex-col gap-2 px-4 py-3">
        <LoadState loading={sheet.loading} error={sheet.error} onRetry={sheet.reload} empty={!sheet.data} />
        {sheet.data && cows.length === 0 && (
          <div className="rounded-[14px] bg-white p-5 text-center">
            <div className="font-bold">No milking cows{groupId ? ' in this group' : ''}</div>
            <p className="m-0 mt-1 text-sm text-ink-3">Cows must be female and in the Milking category.</p>
            <Link to="/animals" className="mt-2 inline-block font-semibold">Go to herd</Link>
          </div>
        )}
        {cows.map((c) => {
          const isSkipped = skipped.has(c.animalId)
          const n = toNum(values[c.animalId] ?? '')
          const usual = c.usualLitres ?? undefined
          const low = !isSkipped && n !== undefined && usual && n < usual * (1 - drop / 100)
          const edited = !isSkipped && n !== undefined && c.lastLitres != null && n !== c.lastLitres && c.litres == null
          if (isSkipped) {
            return (
              <div key={c.animalId} className="flex items-center gap-2.5 rounded-[14px] bg-white p-3 opacity-60">
                <div className="min-w-0 flex-1"><div className="font-bold">{c.tag} <span className="font-medium text-ink-2">{c.name}</span></div><div className="mt-0.5 text-xs text-ink-3">Skipped — not milked</div></div>
                <button type="button" onClick={() => skip(c.animalId, false)} className="h-11 rounded-xl border border-line bg-white px-3 text-sm font-semibold">Undo skip</button>
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
                    : <div className="mt-0.5 text-xs text-ink-3">Last {L(c.lastLitres)} L{edited ? ' · edited' : ''} · <button type="button" onClick={() => skip(c.animalId, true)} className="border-0 bg-transparent p-0 text-xs font-semibold text-brand">Skip</button></div>}
                </div>
                <button type="button" aria-label={`Less for ${c.tag}`} onClick={() => step(c.animalId, -0.5)} className="flex h-11 w-11 items-center justify-center rounded-xl border border-line bg-white text-[22px] font-semibold">−</button>
                <input aria-label={`Litres for ${c.tag}`} inputMode="decimal" value={values[c.animalId] ?? ''} onChange={(e) => setValues((v) => ({ ...v, [c.animalId]: decimal(e.target.value) }))}
                  className="box-border h-11 w-[72px] rounded-[10px] border border-line text-center text-xl font-bold" />
                <button type="button" aria-label={`More for ${c.tag}`} onClick={() => step(c.animalId, 0.5)} className="flex h-11 w-11 items-center justify-center rounded-xl border border-line bg-white text-[22px] font-semibold">+</button>
              </div>
              {low && <div className="ml-1 mt-1 text-xs font-semibold text-danger">{L(n)} L is {Math.round((1 - n! / usual!) * 100)} % below her usual — check her health?</div>}
              {c.inWithdrawal && c.withdrawalUntil && <div className="ml-1 mt-1 text-xs text-ink-3">Withdrawal ends {shortDate(c.withdrawalUntil)}</div>}
            </div>
          )
        })}
      </div>

      <ActionBar>
        <div className="w-full">
          <div className="mb-2.5 flex justify-between text-[15px]">
            <span>{name} · {totals.count} cows<br /><span className="text-[13px] text-ink-3">Day total {L(totals.sum + other)} L · {otherName.toLowerCase()} {L(other)} L</span></span>
            <b>{L(totals.sum)} L {totals.withheld > 0 && <span className="text-[13px] font-medium text-ink-3">({L(totals.withheld)} L discarded)</span>}</b>
          </div>
          <button type="button" className="btn" disabled={busy || !sheet.data || cows.length === 0} onClick={save}>{busy ? 'Saving…' : `Save ${name.toLowerCase()} for all cows`}</button>
        </div>
      </ActionBar>
    </Screen>
  )
}
