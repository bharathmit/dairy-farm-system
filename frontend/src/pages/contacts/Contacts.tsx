import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { LoadState, useRecords } from '../../components/records'
import { inr } from '../../data/store'
import { ROLE_SHORT, useLoad, type ContactRole } from '../../data/records'

const TONE: Record<ContactRole, string> = {
  BUYER: 'bg-brand-soft text-brand', VET: 'bg-danger-soft text-danger-text', AI_TECH: 'bg-info-soft text-info-text',
  SUPPLIER: 'bg-warn-soft text-warn-ink', OTHER: 'bg-track text-ink-2',
}
const FILTERS: (ContactRole | 'ALL')[] = ['ALL', 'BUYER', 'VET', 'SUPPLIER', 'AI_TECH', 'OTHER']
const initials = (name: string) => name.replace(/[^A-Za-z0-9 ]/g, '').split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase() || '?'

export default function Contacts() {
  const { rec, isOwner } = useRecords()
  const all = useLoad(() => rec.contacts(), [rec])
  const [role, setRole] = useState<ContactRole | 'ALL'>('ALL')
  const [q, setQ] = useState('')
  const list = (all.data ?? [])
    .filter((c) => role === 'ALL' || c.roles.includes(role))
    .filter((c) => !q || `${c.name} ${c.phone ?? ''} ${c.village ?? ''}`.toLowerCase().includes(q.toLowerCase()))
    .sort((a, b) => Number(b.active) - Number(a.active) || a.name.localeCompare(b.name))

  return (
    <Screen>
      <TopBar title="Contacts" back="/settings" right={<Link to="/settings/contacts/new" className="flex h-10 items-center rounded-[10px] bg-brand px-3.5 text-sm font-semibold text-white no-underline hover:text-white">+ Add</Link>} />
      <div className="px-4 pb-4">
        <div className="-mt-1 mb-2.5 text-[13px] text-ink-3">Buyers, vets, AI technicians and suppliers</div>
        <label className="flex h-[46px] items-center gap-2 rounded-xl bg-white px-3"><Icon name="search" size={18} color="#56655B" />
          <input aria-label="Search contacts" placeholder="Search name or phone" value={q} onChange={(e) => setQ(e.target.value)} className="min-w-0 flex-1 border-0 text-[15px] outline-none" /></label>
        <div className="mt-3 flex gap-2 overflow-x-auto pb-1">
          {FILTERS.map((f) => <button key={f} type="button" aria-pressed={role === f} onClick={() => setRole(f)} className={`chip ${role === f ? 'chip-on' : ''}`}>{f === 'ALL' ? `All ${all.data?.length ?? ''}` : ROLE_SHORT[f]}</button>)}
        </div>
        <LoadState loading={all.loading} error={all.error} onRetry={all.reload} empty={!all.data} />
        {all.data && (
          <div className="mt-3 overflow-hidden rounded-[14px] bg-white">
            {list.length === 0 && <div className="px-4 py-5 text-center text-sm text-ink-3">{all.data.length ? 'No contacts match.' : 'No contacts yet.'} <Link to="/settings/contacts/new" className="font-semibold">Add one</Link></div>}
            {list.map((c, i) => (
              <div key={c.id} className={`flex items-center gap-3 px-3.5 py-3 ${i ? 'border-t border-line-faint' : ''} ${c.active ? '' : 'opacity-60'}`}>
                <Link to={`/settings/contacts/${c.id}`} className="flex min-w-0 flex-1 items-center gap-3 text-ink no-underline hover:text-ink">
                  <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full font-bold ${TONE[c.roles[0] ?? 'OTHER']}`}>{initials(c.name)}</span>
                  <span className="min-w-0 flex-1"><b className="block truncate">{c.name}</b>
                    <span className="block truncate text-[13px] text-ink-3">{c.active ? [c.village, isOwner && c.milkRate != null ? `${inr(c.milkRate)}/L` : '', c.phone].filter(Boolean).join(' · ') || '—' : 'Inactive · hidden from dropdowns'}</span></span>
                  <span className="flex flex-col items-end gap-1">{c.roles.slice(0, 2).map((r) => <span key={r} className={`badge font-bold ${TONE[r]}`}>{ROLE_SHORT[r]}</span>)}</span>
                </Link>
                {c.phone && c.active && <a href={`tel:${c.phone.replace(/[^+\d]/g, '')}`} aria-label={`Call ${c.name}`} className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border border-line">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#1F5C3A" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2" /></svg></a>}
              </div>
            ))}
          </div>
        )}
        <div className="mt-2.5 text-[13px] leading-normal text-ink-3">These people appear in the <b>Sold to</b> and <b>Vet</b> dropdowns. Contacts used on records can be made inactive but not deleted.</div>
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
