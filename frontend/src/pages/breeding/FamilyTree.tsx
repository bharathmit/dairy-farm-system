import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { useBreeding } from '../../components/breeding'
import { useStore } from '../../data/store'
import { shortDate, useLoad } from '../../data/records'
import { refLabel, type FamilyNode, type MateCheck } from '../../data/breeding'
import { errorMessage } from '../../lib/api'

function Node({ n, role }: { n: FamilyNode | null; role: string }) {
  const body = n ? (n.animal ? refLabel(n.animal) : n.label) : '+ Unknown'
  return (
    <div className={`rounded-xl px-2.5 py-2 ${n ? 'bg-white' : 'border border-dashed border-line bg-transparent'}`}>
      <div className="text-[11px] font-semibold text-ink-3">{role}</div>
      {n?.animal ? <Link to={`/animals/${n.animal.id}`} className="block truncate text-sm font-bold">{body}</Link> : <div className={`truncate text-sm ${n ? 'font-bold' : 'text-ink-3'}`}>{body}</div>}
    </div>
  )
}
const RISK = { HIGH: 'bg-danger-soft text-danger-text', MEDIUM: 'bg-warn-soft text-warn-ink', NONE: 'bg-brand-soft text-brand' }

export default function FamilyTree() {
  const { id = '' } = useParams()
  const { br } = useBreeding()
  const { animals } = useStore()
  const res = useLoad(() => br.family(id), [br, id])
  const semen = useLoad(() => br.semen(), [br])
  const [pick, setPick] = useState('')
  const [check, setCheck] = useState<MateCheck | null>(null)
  const [checkErr, setCheckErr] = useState<string | null>(null)
  const f = res.data
  const bulls = animals.filter((a) => a.status === 'Active' && a.sex === 'Male' && a.category === 'Bull' && a.id !== id)
  const runCheck = async (v: string) => {
    setPick(v); setCheck(null); setCheckErr(null)
    if (!v) return
    const [kind, ref] = v.split(':')
    try { setCheck(await br.mateCheck(id, kind === 'b' ? { sireAnimalId: ref } : { semenItemId: ref })) } catch (e) { setCheckErr(errorMessage(e)) }
  }
  const female = f?.animal.sex === 'FEMALE' || animals.find((a) => a.id === id)?.sex === 'Female'

  return (
    <Screen>
      <TopBar title="Family tree" back={-1} />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <LoadState loading={res.loading} error={res.error} onRetry={res.reload} empty={!f} />
        {f && (
          <>
            <div className="rounded-[14px] bg-white p-3.5">
              <Link to={`/animals/${f.animal.id}`} className="text-lg font-bold">{refLabel(f.animal)}</Link>
              <div className="text-[13px] text-ink-3">{[f.animal.category, f.animal.breed].filter(Boolean).join(' · ')}</div>
              <div className="mt-2.5 flex items-center gap-2 text-xs font-semibold text-ink-3">
                <span>Pedigree known</span>
                <div className="h-1.5 flex-1 overflow-hidden rounded bg-line-faint"><div className="h-1.5 bg-brand" style={{ width: `${f.pedigreeKnownPct}%` }} /></div>
                <span>{f.pedigreeKnownPct} %</span>
              </div>
            </div>

            <div>
              <div className="mb-2 text-xs font-bold uppercase tracking-[.06em] text-ink-3">Ancestors</div>
              <div className="grid grid-cols-4 gap-1.5">
                <Node n={f.grandparents.damDam} role="Dam's dam" /><Node n={f.grandparents.damSire} role="Dam's sire" />
                <Node n={f.grandparents.sireDam} role="Sire's dam" /><Node n={f.grandparents.sireSire} role="Sire's sire" />
              </div>
              <div className="mt-1.5 grid grid-cols-2 gap-1.5">
                <Node n={f.parents.dam} role="Dam" /><Node n={f.parents.sire} role="Sire" />
              </div>
            </div>

            <div className="rounded-[14px] bg-white p-3.5">
              <div className="mb-1 flex items-baseline justify-between"><b className="text-[15px]">Siblings</b><span className="text-[13px] text-ink-3">{f.siblings.length}</span></div>
              {f.siblings.length === 0 && <div className="text-sm text-ink-3">No siblings on the farm.</div>}
              {f.siblings.map((s) => (
                <Link key={s.animal.id} to={`/animals/${s.animal.id}`} className="flex min-h-11 items-center gap-2 border-t border-line-faint text-sm text-ink no-underline first:border-t-0">
                  <span className="flex-1"><b>{s.animal.tag}</b> {s.animal.name}</span>
                  <span className={`badge ${s.kind === 'FULL' ? 'bg-brand-soft text-brand' : 'bg-ground text-ink-2'}`}>{s.kind === 'FULL' ? 'Full' : s.kind === 'HALF_DAM' ? 'Half · same dam' : 'Half · same sire'}</span>
                </Link>
              ))}
            </div>

            {female && (
              <div className="rounded-[14px] bg-white p-3.5">
                <div className="mb-1 flex items-baseline justify-between"><b className="text-[15px]">Offspring & birth history</b>{f.avgCalvingIntervalDays != null && <span className="text-[13px] text-ink-3">interval avg {f.avgCalvingIntervalDays} d</span>}</div>
                {f.offspring.length === 0 && <div className="text-sm text-ink-3">No calvings recorded.</div>}
                {f.offspring.map((o, i) => (
                  <div key={i} className="flex min-h-12 items-center gap-2 border-t border-line-faint py-1.5 text-sm first:border-t-0">
                    <span className="flex-1">
                      {o.animal ? <Link to={`/animals/${o.animal.id}`} className="font-bold">{refLabel(o.animal)}</Link> : <b>Not kept</b>} {o.sex === 'FEMALE' ? '♀' : '♂'}
                      <div className="text-[13px] text-ink-3">{[`${shortDate(o.birthDate)} ${o.birthDate.slice(0, 4)}`, o.sire && `sire ${o.sire}`, o.ease?.toLowerCase(), o.birthWeightKg != null && `${o.birthWeightKg} kg`].filter(Boolean).join(' · ')}</div>
                    </span>
                    {o.status && <span className="badge bg-ground text-ink-2">{o.status}</span>}
                  </div>
                ))}
              </div>
            )}

            {female && (
              <div className="rounded-[14px] bg-white p-3.5">
                <b className="text-[15px]">Mate-risk checker</b>
                <label className="lab mt-2" htmlFor="ft-bull">Pick a bull for next service</label>
                <select className="inp" id="ft-bull" value={pick} onChange={(e) => void runCheck(e.target.value)}>
                  <option value="">Choose a bull or semen</option>
                  {(semen.data?.batches ?? []).filter((b) => b.straws > 0).map((b) => <option key={`s${b.id}`} value={`s:${b.id}`}>{b.bullName}{b.breed ? ` · ${b.breed}` : ''}{b.bullCode ? ` · ${b.bullCode}` : ''}</option>)}
                  {bulls.map((b) => <option key={`b${b.id}`} value={`b:${b.id}`}>Herd bull {b.tag}{b.name ? ` ${b.name}` : ''}</option>)}
                </select>
                {checkErr && <div className="mt-2 text-sm text-danger">{checkErr}</div>}
                {check && (
                  <div className={`mt-2.5 rounded-xl px-3.5 py-3 text-sm ${RISK[check.risk]}`}>
                    <b>{check.risk === 'HIGH' ? 'High risk — do not use.' : check.risk === 'MEDIUM' ? 'Some risk.' : 'No shared ancestors found.'}</b> {check.text}
                    {check.sharedAncestors.length > 0 && <div className="mt-1">Shared ancestors: {check.sharedAncestors.join(', ')}</div>}
                  </div>
                )}
                <div className="mt-2 text-xs text-ink-3">Checks only recorded ancestors. Missing links ({100 - (check?.knownPct ?? f.pedigreeKnownPct)} %) can hide risk.</div>
              </div>
            )}
          </>
        )}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
