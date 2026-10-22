import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Screen, Switch, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { OwnerOnlyScreen } from '../../components/money'
import { refreshAlertCount, useAlerts } from '../../components/alerts'
import { useStore } from '../../data/store'
import { CATEGORY_LABEL, type AlertCategory, type Rule } from '../../data/alerts'
import { decimal, useLoad } from '../../data/records'
import { errorMessage } from '../../lib/api'

const ORDER: AlertCategory[] = ['REPRODUCTION', 'CALF', 'HEALTH', 'MILK', 'FEED', 'FINANCE_OPS']
const GROUP_TITLE: Record<AlertCategory, string> = { ...CATEGORY_LABEL, CALF: 'Calves & heifers' }

/** Readable names and units for rule params. */
const PARAM: Record<string, { before?: string; after?: string }> = {
  heatCycleDays: { after: 'days after last heat' }, pdAfterDays: { after: 'days after AI' }, daysBefore: { after: 'days before due date' },
  days: { after: 'days' }, colostrumHours: { before: 'within', after: 'hours of calving' }, weanMinDays: { before: 'from', after: 'days' },
  weanStarterKg: { before: 'starter ≥', after: 'kg/day' }, heiferFirstServiceKg: { after: 'kg' }, heiferFirstServiceMonths: { before: 'or', after: 'months' },
  everyDays: { before: 'every', after: 'days' }, min: { before: 'below' }, max: { before: 'or above' },
  dropAlertPct: { before: 'more than', after: '% vs recent avg' }, minFatPct: { before: 'below', after: '%' }, maxSccK: { before: 'above', after: 'k cells/mL' },
  dryOffDaysBeforeDue: { after: 'days before calving' }, minStraws: { before: 'fewer than', after: 'straws' }, ageYears: { before: 'at age', after: 'years' },
  marginAlertPct: { before: 'margin below', after: '%' },
}

/** Owner settings for every alert: on/off, who sees it, and its numbers. */
export default function AlertRules() {
  const { al, farmId, isOwner } = useAlerts()
  const { notify } = useStore()
  const rules = useLoad(isOwner ? () => al.rules() : null, [al, isOwner])
  const [draft, setDraft] = useState<Rule[]>([])
  const [text, setText] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!rules.data) return
    setDraft(rules.data)
    const t: Record<string, string> = {}
    rules.data.forEach((r) => Object.entries(r.params ?? {}).forEach(([k, v]) => { t[`${r.code}.${k}`] = v == null ? '' : String(v) }))
    setText(t)
  }, [rules.data])

  if (!isOwner) return <OwnerOnlyScreen title="Alert rules" back="/alerts" />

  const patch = (code: string, p: Partial<Rule>) => setDraft((d) => d.map((r) => (r.code === code ? { ...r, ...p } : r)))
  const save = async () => {
    const body = draft.map((r) => ({
      ...r,
      params: Object.fromEntries(Object.keys(r.params ?? {}).map((k) => {
        const v = text[`${r.code}.${k}`]
        return [k, v === undefined || v === '' ? null : Number(v)]
      })),
    }))
    setBusy(true)
    try { const saved = await al.saveRules(body); rules.setData(saved); notify('Alert rules saved'); refreshAlertCount(farmId) } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <Screen>
      <TopBar title="Alert rules" back="/alerts" right={<Link to="/settings/health-schedules" className="px-2 text-sm font-semibold no-underline">Schedules</Link>} />
      <div className="px-4 pb-4">
        <p className="mb-1 mt-0 text-[13px] text-ink-3">Owner only. A changed number applies from the next check. The switch on the right turns a rule on or off. Numbers marked ⇄ are shared with breeding, milk grade or margin settings.</p>
        <LoadState loading={rules.loading} error={rules.error} onRetry={rules.reload} empty={!rules.data} />
        {ORDER.filter((c) => draft.some((r) => r.category === c)).map((c) => (
          <div key={c}>
            <div className="mb-2 mt-4 text-xs font-bold uppercase tracking-[.06em] text-ink-3">{GROUP_TITLE[c]}</div>
            <div className="rounded-[14px] bg-white px-3.5">
              {draft.filter((r) => r.category === c).map((r, i) => (
                <div key={r.code} className={`py-3 ${i ? 'border-t border-line-faint' : ''} ${r.enabled ? '' : 'opacity-60'}`}>
                  <div className="flex items-center gap-3">
                    <b className="flex-1 text-[15px]">{r.title}</b>
                    {r.alwaysOn ? <span className="text-xs font-semibold text-ink-3">Always on</span> : <Switch checked={r.enabled} onChange={(v) => patch(r.code, { enabled: v })} label={`${r.title} on`} />}
                  </div>
                  {Object.keys(r.params ?? {}).length > 0 && (
                    <div className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1.5 text-sm text-ink-2">
                      {Object.keys(r.params).map((k) => (
                        <span key={k} className="flex items-center gap-1.5">
                          {PARAM[k]?.before}
                          <input aria-label={`${r.title} ${k}`} inputMode="decimal" value={text[`${r.code}.${k}`] ?? ''} disabled={!r.enabled}
                            onChange={(e) => setText((t) => ({ ...t, [`${r.code}.${k}`]: decimal(e.target.value) }))}
                            className="h-10 w-16 rounded-lg border border-line text-center text-[15px] font-semibold" />
                          {PARAM[k] ? PARAM[k].after : k}
                        </span>
                      ))}
                    </div>
                  )}
                  <label className="mt-2 flex min-h-9 items-center gap-2 text-[13px] text-ink-2">
                    <input type="checkbox" checked={r.workerVisible} onChange={(e) => patch(r.code, { workerVisible: e.target.checked })} className="h-5 w-5 accent-brand" />Workers see
                  </label>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-4 pt-3.5">
        <button type="button" className="btn" disabled={busy || !rules.data} onClick={save}>{busy ? 'Saving…' : 'Save rules'}</button>
      </div>
    </Screen>
  )
}
