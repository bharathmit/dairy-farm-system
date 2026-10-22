import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Dialog, Screen, Segmented, TopBar } from '../../components/ui'
import { AnimalSelect, ContactSelect, LoadState } from '../../components/records'
import { isBreedable, useBreeding } from '../../components/breeding'
import { useStore } from '../../data/store'
import { addDays, decimal, numText, shortDate, todayIso, toNum, useLoad } from '../../data/records'
import { inr0, intensityLabel, METHODS, time12, type BreedMethod } from '../../data/breeding'
import { errorMessage } from '../../lib/api'

const SPECIES_KEY: Record<string, string> = { Cow: 'COW', Buffalo: 'BUFFALO', Goat: 'GOAT', Sheep: 'SHEEP' }

export default function ServiceForm() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const { br, isOwner } = useBreeding()
  const { animals, notify } = useStore()
  const nav = useNavigate()
  const existing = useLoad(id ? () => br.service(id) : null, [br, id])
  const settings = useLoad(() => br.settings(), [br])
  const semen = useLoad(() => br.semen(), [br])

  const [animalId, setAnimalId] = useState(params.get('animal') ?? '')
  const [date, setDate] = useState(todayIso())
  const [time, setTime] = useState('')
  const [heatId, setHeatId] = useState(params.get('heat') ?? '')
  const [method, setMethod] = useState<BreedMethod>('AI')
  const [semenId, setSemenId] = useState('')
  const [straws, setStraws] = useState('1')
  const [tech, setTech] = useState('')
  const [sireId, setSireId] = useState('')
  const [sireTag, setSireTag] = useState('')
  const [expStart, setExpStart] = useState('')
  const [expEnd, setExpEnd] = useState('')
  const [bullOwner, setBullOwner] = useState('')
  const [techFee, setTechFee] = useState('')
  const [bullFee, setBullFee] = useState('')
  const [paid, setPaid] = useState(true)
  const [due, setDue] = useState('')
  const [dueEdited, setDueEdited] = useState(false)
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const back = params.get('back') || '/breeding'

  const heats = useLoad(animalId ? () => br.heats({ animalId, from: addDays(date, -5), to: date }) : null, [br, animalId, date])
  const animal = animals.find((a) => a.id === animalId)
  const gestation = settings.data?.gestationDays?.[SPECIES_KEY[animal?.species ?? 'Cow'] ?? 'COW'] ?? 283
  const pdAfter = settings.data?.pdAfterDays ?? 45

  useEffect(() => {
    const r = existing.data
    if (!r) return
    setAnimalId(String(r.animal.id)); setDate(r.serviceDate ?? todayIso()); setTime(r.serviceTime ?? '')
    setHeatId(r.heatId ? String(r.heatId) : ''); setMethod(r.method === 'UNKNOWN' ? 'AI' : r.method)
    setSemenId(r.semen ? String(r.semen.itemId) : ''); setStraws(numText(r.straws ?? 1)); setTech(r.technician ? String(r.technician.id) : '')
    setSireId(r.sire ? String(r.sire.id) : ''); setSireTag(r.sire ? '' : r.sireTag ?? '')
    setExpStart(r.bullExposureStart ?? ''); setExpEnd(r.bullExposureEnd ?? ''); setBullOwner(r.bullOwner ? String(r.bullOwner.id) : '')
    setTechFee(numText(r.technicianFee)); setBullFee(numText(r.bullFee)); setPaid(r.feePaid ?? true)
    setDue(r.expectedDueDate ?? ''); setDueEdited(true); setNotes(r.notes ?? '')
  }, [existing.data])
  useEffect(() => { if (!dueEdited) setDue(addDays(date, gestation)) }, [date, gestation, dueEdited])

  const batches = useMemo(() => (semen.data?.batches ?? []).filter((b) => b.straws > 0 || String(b.id) === semenId), [semen.data, semenId])
  const batch = batches.find((b) => String(b.id) === semenId)
  const strawsN = toNum(straws) ?? 1
  const semenCost = batch?.costPerStraw != null ? batch.costPerStraw * strawsN : undefined
  const total = (method !== 'NATURAL' ? semenCost ?? 0 : 0) + (toNum(techFee) ?? 0) + (method === 'NATURAL' ? toNum(bullFee) ?? 0 : 0)
  const bulls = animals.filter((a) => a.status === 'Active' && a.sex === 'Male' && a.category === 'Bull')

  const save = async () => {
    if (!animalId) { notify('Choose the cow'); return }
    if (method !== 'NATURAL' && !semenId) { notify('Pick the semen batch used'); return }
    setBusy(true)
    try {
      const body: Record<string, unknown> = {
        animalId: Number(animalId), date, time: time || undefined, heatId: heatId ? Number(heatId) : undefined, method,
        semenItemId: method !== 'NATURAL' && semenId ? Number(semenId) : undefined, straws: method !== 'NATURAL' ? strawsN : undefined,
        sireAnimalId: method === 'NATURAL' && sireId ? Number(sireId) : undefined, sireTag: method === 'NATURAL' && !sireId ? sireTag.trim() || undefined : undefined,
        bullExposureStart: method === 'NATURAL' ? expStart || undefined : undefined, bullExposureEnd: method === 'NATURAL' ? expEnd || undefined : undefined,
        technicianContactId: tech ? Number(tech) : undefined, bullOwnerContactId: method === 'NATURAL' && bullOwner ? Number(bullOwner) : undefined,
        technicianFee: isOwner ? toNum(techFee) ?? null : null, bullFee: isOwner && method === 'NATURAL' ? toNum(bullFee) ?? null : null,
        feePaid: paid, expectedDueDate: due || undefined, notes: notes.trim() || undefined,
      }
      const r = await br.saveService(body, id)
      notify(`Service saved · PD due ${shortDate(r.pdDueDate)}`)
      nav(back)
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  const remove = async () => {
    try { await br.deleteService(Number(id)); notify('Service deleted'); nav(back) } catch (e) { notify(errorMessage(e)) }
  }

  return (
    <Screen>
      <TopBar title="Breeding / service" close={back} />
      <div className="flex flex-col gap-3.5 px-4 pb-4">
        {id && <LoadState loading={existing.loading} error={existing.error} onRetry={existing.reload} empty={!existing.data} />}
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="sv-cow">Cow *</label><AnimalSelect id="sv-cow" value={animalId} onChange={(v) => { setAnimalId(v); setHeatId('') }} filter={isBreedable} placeholder="Choose" /></div>
          <div><label className="lab" htmlFor="sv-dt">Service date *</label><input className="inp" id="sv-dt" type="date" max={todayIso()} value={date} onChange={(e) => e.target.value && setDate(e.target.value)} /></div>
        </div>
        <div><label className="lab" htmlFor="sv-heat">Linked heat</label>
          <select className="inp" id="sv-heat" value={heatId} onChange={(e) => setHeatId(e.target.value)}>
            <option value="">{heats.data?.length ? 'None' : 'None recorded'}</option>
            {(heats.data ?? []).map((h) => <option key={h.id} value={h.id}>{shortDate(h.date)}{h.time ? ` ${time12(h.time)}` : ''} · {intensityLabel(h.intensity)} · {h.signs.length} signs</option>)}
          </select>
        </div>
        <div><div className="lab">Method</div><Segmented label="Breeding method" options={METHODS} value={method} onChange={setMethod} /></div>

        {method !== 'NATURAL' ? (
          <div className="flex flex-col gap-3 rounded-[14px] bg-white p-3.5">
            <div className="flex items-center gap-2 text-[15px] font-bold"><span className="flex-1">{method === 'AI' ? 'Artificial insemination' : 'Embryo transfer'}</span><Link to="/breeding/semen" className="text-[13px] font-semibold">Stock</Link></div>
            <fieldset className="m-0 flex flex-col gap-2 border-0 p-0">
              <legend className="lab p-0">Semen batch *</legend>
              <LoadState loading={semen.loading} error={semen.error} onRetry={semen.reload} empty={!semen.data} />
              {semen.data && batches.length === 0 && <div className="text-sm text-ink-3">No straws in stock. <Link to="/breeding/semen" className="font-semibold">Add a semen batch</Link></div>}
              {batches.map((b) => (
                <label key={b.id} className={`flex min-h-14 cursor-pointer items-center gap-2.5 rounded-[10px] border px-3 py-2 ${String(b.id) === semenId ? 'border-brand bg-brand-soft' : 'border-line'}`}>
                  <input type="radio" name="semen" checked={String(b.id) === semenId} onChange={() => setSemenId(String(b.id))} className="h-5 w-5 accent-brand" />
                  <span className="flex-1"><b>{b.bullName}</b>{b.breed ? ` · ${b.breed}` : ''}<div className="text-[13px] text-ink-3">{[b.bullCode && `Code ${b.bullCode}`, b.expiryDate && `exp ${shortDate(b.expiryDate)}`, b.sexed && 'sexed'].filter(Boolean).join(' · ')}</div></span>
                  <span className={`badge ${b.low ? 'bg-danger-soft text-danger-text' : 'bg-white text-brand'}`}>{b.low ? `${b.straws} left` : `${b.straws} straws`}</span>
                </label>
              ))}
            </fieldset>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="lab" htmlFor="sv-st">Straws used</label><input className="inp" id="sv-st" inputMode="numeric" value={straws} onChange={(e) => setStraws(e.target.value.replace(/\D/g, ''))} /></div>
              <div><label className="lab" htmlFor="sv-tm">Time</label><input className="inp" id="sv-tm" type="time" value={time} onChange={(e) => setTime(e.target.value)} /></div>
            </div>
            <div><label className="lab" htmlFor="sv-tech">Technician</label><ContactSelect id="sv-tech" role="AI_TECH" value={tech} onChange={setTech} newLabel="New technician" /></div>
            {batch && !id && <div className="text-xs text-ink-3">After save: {batch.bullName} will have {Math.max(0, batch.straws - strawsN)} straws left.</div>}
          </div>
        ) : (
          <div className="flex flex-col gap-3 rounded-[14px] bg-white p-3.5">
            <div className="text-[15px] font-bold">Natural service</div>
            <div><label className="lab" htmlFor="sv-bull">Bull</label>
              <select className="inp" id="sv-bull" value={sireId} onChange={(e) => setSireId(e.target.value)}>
                <option value="">Outside bull — enter tag</option>
                {bulls.map((x) => <option key={x.id} value={x.id}>From herd — {x.tag}{x.name ? ` ${x.name}` : ''}</option>)}
              </select>
              {!sireId && <input className="inp mt-2" aria-label="Outside bull tag" placeholder="Bull tag or name" value={sireTag} onChange={(e) => setSireTag(e.target.value)} />}
            </div>
            <div><label className="lab" htmlFor="sv-owner">Bull owner</label><ContactSelect id="sv-owner" role="OTHER" value={bullOwner} onChange={setBullOwner} newLabel="New bull owner" /></div>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="lab" htmlFor="sv-es">Exposure start</label><input className="inp" id="sv-es" type="date" value={expStart} onChange={(e) => setExpStart(e.target.value)} /></div>
              <div><label className="lab" htmlFor="sv-ee">Exposure end</label><input className="inp" id="sv-ee" type="date" value={expEnd} onChange={(e) => setExpEnd(e.target.value)} /></div>
            </div>
          </div>
        )}

        {isOwner && (
          <div className="flex flex-col gap-3 rounded-[14px] bg-white p-3.5">
            <div className="text-[15px] font-bold">Costs</div>
            <div className="grid grid-cols-2 gap-3">
              {method !== 'NATURAL' && <div><label className="lab" htmlFor="sv-sc">Semen ₹</label><input className="inp bg-ground" id="sv-sc" readOnly value={semenCost != null ? String(semenCost) : '—'} /><div className="mt-1 text-xs text-ink-3">{batch?.costPerStraw != null ? `${strawsN} × ₹${batch.costPerStraw} from stock` : 'No cost on the batch'}</div></div>}
              <div><label className="lab" htmlFor="sv-tf">{method === 'NATURAL' ? 'Handler fee ₹' : 'Technician fee ₹'}</label><input className="inp" id="sv-tf" inputMode="decimal" value={techFee} onChange={(e) => setTechFee(decimal(e.target.value))} /></div>
              {method === 'NATURAL' && <div><label className="lab" htmlFor="sv-bf">Bull service fee ₹</label><input className="inp" id="sv-bf" inputMode="decimal" placeholder="0" value={bullFee} onChange={(e) => setBullFee(decimal(e.target.value))} /></div>}
            </div>
            <div className="flex justify-between border-t border-line-faint pt-2.5 text-[15px]"><span>Breeding cost</span><b>{inr0(total)}</b></div>
            <label className="flex min-h-10 items-center gap-2.5 text-sm"><input type="checkbox" checked={paid} onChange={(e) => setPaid(e.target.checked)} className="h-5 w-5 accent-brand" /> Fees paid (else pending)</label>
            <div className="text-xs text-ink-3">Fees go to Finance as a Breeding expense against this cow. Semen is counted when bought, and in her profit when the straw is used.</div>
          </div>
        )}

        <div className="flex flex-col gap-2 rounded-[14px] bg-white p-3.5">
          <div className="text-[15px] font-bold">Expected due date</div>
          <div className="flex items-center gap-3">
            <input className="inp flex-1" aria-label="Expected due date" type="date" value={due} onChange={(e) => { setDue(e.target.value); setDueEdited(true) }} />
            <span className="badge bg-brand-soft text-brand">+{gestation} d</span>
          </div>
          <div className="text-xs text-ink-3">Auto: service date + {gestation} days. Edit if your vet gives another date. Pregnancy check due <b>{shortDate(addDays(date, pdAfter))}</b> ({pdAfter} d).</div>
        </div>
        <div><label className="lab" htmlFor="sv-nt">Notes</label><input className="inp" id="sv-nt" placeholder="Optional" value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
        {id && isOwner && <button type="button" onClick={() => setConfirm(true)} className="h-12 rounded-xl border border-line bg-white font-semibold text-danger">Delete service</button>}
      </div>
      <div className="sticky bottom-0 mt-auto border-t border-line-soft bg-white px-4 pb-6 pt-3.5">
        <button type="button" className="btn" disabled={busy || (Boolean(id) && !existing.data)} onClick={save}>{busy ? 'Saving…' : 'Save service'}</button>
      </div>
      {confirm && (
        <Dialog title="Delete this service?" onClose={() => setConfirm(false)}>
          <p className="m-0 mb-5 text-[15px] text-ink-2">Straws used are put back in stock.</p>
          <div className="grid grid-cols-2 gap-3">
            <button type="button" onClick={() => setConfirm(false)} className="h-12 rounded-xl border border-line bg-white font-semibold">Cancel</button>
            <button type="button" onClick={remove} className="h-12 rounded-xl bg-danger font-semibold text-white">Delete</button>
          </div>
        </Dialog>
      )}
    </Screen>
  )
}
