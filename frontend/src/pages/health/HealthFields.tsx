/* eslint-disable react-refresh/only-export-components */
import { Chips } from '../../components/ui'
import { ContactSelect } from '../../components/records'
import { useStore } from '../../data/store'
import { addDays, dayLabel, decimal, HEALTH_TYPES, type HealthType } from '../../data/records'

export interface HealthDraft {
  date: string; type: HealthType; symptoms: string; diagnosis: string; medicine: string; dose: string; route: string
  vet: string; cost: string; milkDays: string; meatDays: string; nextDate: string; nextAction: string; notes: string
}

export const ROUTES = ['Intramuscular', 'Intravenous', 'Subcutaneous', 'Oral', 'Intramammary', 'Topical', 'Pour-on']

export const emptyDraft = (date: string, type: HealthType = 'TREATMENT'): HealthDraft => ({
  date, type, symptoms: '', diagnosis: '', medicine: '', dose: '', route: '', vet: '', cost: '',
  milkDays: '', meatDays: '', nextDate: '', nextAction: '', notes: '',
})

const t = (s: string) => s.trim() || undefined
const n = (s: string) => (s.trim() === '' ? undefined : Number(s))

export const draftBody = (d: HealthDraft, showMoney: boolean) => ({
  date: d.date, type: d.type, symptoms: t(d.symptoms), diagnosis: t(d.diagnosis), medicine: t(d.medicine), dose: t(d.dose),
  route: t(d.route), vetContactId: d.vet ? Number(d.vet) : undefined, cost: showMoney ? n(d.cost) : undefined,
  milkWithdrawalDays: n(d.milkDays), meatWithdrawalDays: n(d.meatDays), nextActionDate: d.nextDate || undefined,
  nextAction: t(d.nextAction), notes: t(d.notes),
})

function time12(hhmm: string) {
  const [h, m] = hhmm.split(':').map(Number)
  return `${((h + 11) % 12) + 1}:${String(m).padStart(2, '0')} ${h < 12 ? 'AM' : 'PM'}`
}

/** Type, illness, medicine, withdrawal and follow-up fields shared by one-animal and batch records. */
export default function HealthFields({ d, set, showMoney, batch }: { d: HealthDraft; set: (p: Partial<HealthDraft>) => void; showMoney: boolean; batch?: boolean }) {
  const { settings } = useStore()
  const milk = Number(d.milkDays) || 0
  const meat = Number(d.meatDays) || 0
  const sick = d.type === 'ILLNESS' || d.type === 'TREATMENT' || d.type === 'INJURY'
  return (
    <>
      <div>
        <span className="lab">Type *</span>
        <Chips label="Type" options={HEALTH_TYPES} value={[d.type]} onToggle={(v) => set({ type: v })} />
      </div>

      {sick && (
        <div className="flex flex-col gap-3 rounded-[14px] bg-white p-3.5">
          <div><label className="lab" htmlFor="hf-sy">Symptoms</label><textarea className="inp h-[76px] resize-none py-3" id="hf-sy" value={d.symptoms} onChange={(e) => set({ symptoms: e.target.value })} /></div>
          <div><label className="lab" htmlFor="hf-dg">Diagnosis</label><input className="inp" id="hf-dg" value={d.diagnosis} onChange={(e) => set({ diagnosis: e.target.value })} /></div>
        </div>
      )}

      <div className="flex flex-col gap-3 rounded-[14px] bg-white p-3.5">
        <b className="text-[15px]">{d.type === 'VACCINATION' ? 'Vaccine' : 'Medicine'}</b>
        <div><label className="lab" htmlFor="hf-md">Name</label><input className="inp" id="hf-md" placeholder={d.type === 'VACCINATION' ? 'e.g. FMD vaccine' : 'e.g. Ceftriaxone inj.'} value={d.medicine} onChange={(e) => set({ medicine: e.target.value })} />
          <div className="hint">Picking from stock comes with the inventory module.</div></div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="hf-ds">Dose{batch ? ' / animal' : ''}</label><input className="inp" id="hf-ds" placeholder="10 ml" value={d.dose} onChange={(e) => set({ dose: e.target.value })} /></div>
          <div><label className="lab" htmlFor="hf-rt">Route</label>
            <select className="inp" id="hf-rt" value={d.route} onChange={(e) => set({ route: e.target.value })}><option value="">—</option>{ROUTES.map((r) => <option key={r}>{r}</option>)}</select></div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="hf-vet">Vet</label><ContactSelect id="hf-vet" role="VET" value={d.vet} onChange={(v) => set({ vet: v })} newLabel="New vet" /></div>
          {showMoney && <div><label className="lab" htmlFor="hf-co">Cost ₹{batch ? ' / animal' : ''}</label><input className="inp" id="hf-co" inputMode="decimal" value={d.cost} onChange={(e) => set({ cost: decimal(e.target.value) })} /></div>}
        </div>
      </div>

      <div className="flex flex-col gap-3 rounded-[14px] bg-white p-3.5">
        <b className="text-[15px]">Withdrawal</b>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="hf-mw">Milk (days)</label><input className="inp" id="hf-mw" inputMode="numeric" placeholder="0" value={d.milkDays} onChange={(e) => set({ milkDays: e.target.value.replace(/\D/g, '') })} /></div>
          <div><label className="lab" htmlFor="hf-ew">Meat (days)</label><input className="inp" id="hf-ew" inputMode="numeric" placeholder="0" value={d.meatDays} onChange={(e) => set({ meatDays: e.target.value.replace(/\D/g, '') })} /></div>
        </div>
        {(milk > 0 || meat > 0) && (
          <div className="rounded-[10px] bg-danger-soft p-3 text-sm text-danger-text">
            {milk > 0 && <b className="block">Do not sell milk until {dayLabel(addDays(d.date, milk))}, {time12(settings.eveningMilking)}</b>}
            {meat > 0 && <div className="mt-0.5">No slaughter or sale for meat until {dayLabel(addDays(d.date, meat))}</div>}
          </div>
        )}
      </div>

      <div className="flex flex-col gap-3 rounded-[14px] bg-white p-3.5">
        <b className="text-[15px]">Next action</b>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor="hf-nd">Date</label><input className="inp" id="hf-nd" type="date" min={d.date} value={d.nextDate} onChange={(e) => set({ nextDate: e.target.value })} /></div>
          <div><label className="lab" htmlFor="hf-nn">What</label><input className="inp" id="hf-nn" placeholder={d.type === 'VACCINATION' ? 'Booster' : 'Recheck'} value={d.nextAction} onChange={(e) => set({ nextAction: e.target.value })} /></div>
        </div>
        <div className="hint mt-0">Shows on the health page as a follow-up on that date.</div>
      </div>

      <div><label className="lab" htmlFor="hf-nt">Notes</label><textarea className="inp h-[76px] resize-none py-3" id="hf-nt" placeholder="Optional" value={d.notes} onChange={(e) => set({ notes: e.target.value })} /></div>
    </>
  )
}
