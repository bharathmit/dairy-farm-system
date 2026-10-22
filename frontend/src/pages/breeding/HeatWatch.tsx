import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { LoadState } from '../../components/records'
import { useBreeding } from '../../components/breeding'
import { shortDate, useLoad } from '../../data/records'
import type { HeatWatchItem } from '../../data/breeding'

type F = 'ALL' | 'HIGH' | 'MEDIUM' | 'LOW'
const LEVEL = {
  HIGH: { label: 'High', cls: 'bg-danger-soft text-danger-text', bar: 'bg-danger' },
  MEDIUM: { label: 'Medium', cls: 'bg-warn-soft text-warn-ink', bar: 'bg-[#C2410C]' },
  LOW: { label: 'Low', cls: 'bg-info-soft text-info-text', bar: 'bg-info-text' },
}

function Item({ w }: { w: HeatWatchItem }) {
  const s = w.status
  const l = LEVEL[w.level]
  return (
    <div className="rounded-[14px] bg-white p-3.5">
      <div className="flex items-center gap-2">
        <Link to={`/breeding/animals/${s.animal.id}`} className="flex-1 text-base font-bold text-ink no-underline">{s.animal.tag} <span className="font-medium text-ink-2">{s.animal.name}</span></Link>
        <span className={`badge font-bold ${l.cls}`}>{l.label}</span>
      </div>
      <div className="mt-1.5 text-sm text-ink-2">{w.reason}{s.nextHeatExpected ? ` Next cycle expected ${shortDate(s.nextHeatExpected)}.` : ''}</div>
      <div className="mt-2.5 flex items-center gap-2 text-xs font-semibold text-ink-3">
        <span>Confidence</span>
        <div className="h-1.5 flex-1 overflow-hidden rounded bg-line-faint"><div className={`h-1.5 ${l.bar}`} style={{ width: `${Math.min(100, Math.max(0, w.confidencePct))}%` }} /></div>
        <span>{w.confidencePct} %</span>
      </div>
      <div className="mt-3 grid grid-cols-2 gap-2">
        <Link to={`/breeding/animals/${s.animal.id}`} className="flex h-11 items-center justify-center rounded-[10px] border border-line text-sm font-semibold text-ink no-underline">History</Link>
        <Link to={`/breeding/heat/new?animal=${s.animal.id}`} className="flex h-11 items-center justify-center rounded-[10px] bg-brand text-sm font-semibold text-white no-underline hover:text-white">Log heat signs</Link>
      </div>
    </div>
  )
}

export default function HeatWatch() {
  const { br } = useBreeding()
  const res = useLoad(() => br.heatWatch(), [br])
  const [f, setF] = useState<F>('ALL')
  const all = res.data ?? []
  const list = f === 'ALL' ? all : all.filter((w) => w.level === f)
  const n = (lv: F) => (lv === 'ALL' ? all.length : all.filter((w) => w.level === lv).length)
  const chips: { v: F; l: string }[] = [{ v: 'ALL', l: 'All' }, { v: 'HIGH', l: 'Heat due' }, { v: 'MEDIUM', l: 'No heat' }, { v: 'LOW', l: 'Heifers' }]
  return (
    <Screen>
      <TopBar title="Heat watch" back="/breeding" />
      <div className="flex flex-col gap-3 px-4 pb-4">
        <div className="text-sm text-ink-3">Cows to watch closely today — check morning and evening.</div>
        <div className="flex gap-2 overflow-x-auto pb-1">
          {chips.map((c) => <button key={c.v} type="button" aria-pressed={f === c.v} onClick={() => setF(c.v)} className={`chip ${f === c.v ? 'chip-on' : ''}`}>{c.l} {res.data ? n(c.v) : ''}</button>)}
        </div>
        <LoadState loading={res.loading} error={res.error} onRetry={res.reload} empty={!res.data} />
        {list.map((w) => <Item key={w.status.animal.id} w={w} />)}
        {res.data && list.length === 0 && <div className="rounded-[14px] bg-white px-4 py-5 text-center text-sm text-ink-3">No cows to watch right now.</div>}
        <Link to="/breeding/heat/new" className="btn2">Log heat signs — any cow</Link>
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
