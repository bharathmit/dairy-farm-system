import { useRef, useState, type PointerEvent } from 'react'
import type { Point, RangeKey, useRange } from '../data/charts'

export function RangePicker({ r, monthLabel = 'Month', id }: { r: ReturnType<typeof useRange>; monthLabel?: string; id: string }) {
  const chips: { key: RangeKey; label: string }[] = [
    { key: '7', label: '7 days' }, { key: '30', label: '30 days' }, { key: 'm', label: monthLabel }, { key: 'c', label: 'Custom' },
  ]
  return (
    <>
      <div className="flex flex-wrap gap-2">
        {chips.map((c) => <button key={c.key} type="button" aria-pressed={r.range === c.key} onClick={() => r.setRange(c.key)} className={`chip ${r.range === c.key ? 'chip-on' : ''}`}>{c.label}</button>)}
      </div>
      {r.range === 'c' && (
        <div className="mt-2.5 grid grid-cols-2 gap-3">
          <div><label className="lab" htmlFor={`${id}-from`}>From</label><input className="inp" id={`${id}-from`} type="date" value={r.cFrom} max={r.cTo} onChange={(e) => r.setCFrom(e.target.value)} /></div>
          <div><label className="lab" htmlFor={`${id}-to`}>To</label><input className="inp" id={`${id}-to`} type="date" value={r.cTo} max={r.today} onChange={(e) => r.setCTo(e.target.value)} /></div>
          <div className={`col-span-2 text-xs ${r.customBad ? 'font-semibold text-danger' : 'text-ink-3'}`}>{r.customBad ?? `${r.customDays + 1} days · up to 1 year${r.monthly ? ' · shown by month' : ''}`}</div>
        </div>
      )}
    </>
  )
}

/** Line chart with a value axis, tap/hover tooltip and a vertical guide line. Handles negative values. */
export function LineChart({ points, title, right, unit = '', fmtAxis = (v) => String(v) }: {
  points: Point[]; title: string; right?: string; unit?: string; fmtAxis?: (v: number) => string
}) {
  const [hover, setHover] = useState<number | null>(null)
  const ref = useRef<SVGSVGElement>(null)
  const n = points.length
  if (!n) return null
  const w = 340, h = 180, left = 40, rightPad = 8, top = 10, bottom = 150
  const vals = points.map((p) => p.value)
  const rawMax = Math.max(0, ...vals), rawMin = Math.min(0, ...vals)
  const span = Math.max(1, rawMax - rawMin)
  const mag = Math.pow(10, Math.floor(Math.log10(span / 4)))
  const step = [1, 2, 5, 10].map((k) => k * mag).find((s) => span / s <= 5) ?? 10 * mag
  const max = Math.ceil(rawMax / step) * step || step
  const min = Math.floor(rawMin / step) * step
  const ticks: number[] = []
  for (let t = min; t <= max + step / 2; t += step) ticks.push(Math.round(t * 1e6) / 1e6)
  const x = (i: number) => (n === 1 ? (left + w - rightPad) / 2 : left + (i * (w - left - rightPad)) / (n - 1))
  const y = (v: number) => bottom - ((v - min) / (max - min)) * (bottom - top)
  const line = points.map((p, i) => `${x(i)},${y(p.value)}`).join(' ')
  const every = Math.max(1, Math.ceil(n / 6))

  const pick = (e: PointerEvent<SVGSVGElement>) => {
    const r = ref.current?.getBoundingClientRect()
    if (!r) return
    const px = ((e.clientX - r.left) / r.width) * w
    const i = n === 1 ? 0 : Math.round(((px - left) / (w - left - rightPad)) * (n - 1))
    setHover(Math.min(n - 1, Math.max(0, i)))
  }
  const hp = hover === null ? null : points[hover]

  return (
    <div className="mt-2.5 rounded-[14px] bg-white p-3.5">
      <div className="flex items-baseline justify-between gap-2"><b className="text-[15px]">{title}</b>{right && <span className="text-[13px] text-ink-3">{right}</span>}</div>
      <div className="mt-1 h-5 text-[13px] font-semibold text-brand" aria-live="polite">{hp ? hp.tip : <span className="font-normal text-ink-3">Tap or hover a point to see the value</span>}</div>
      <svg ref={ref} width="100%" viewBox={`0 0 ${w} ${h}`} role="img" aria-label={`${title}: ${n} points`}
        className="block touch-none select-none" onPointerMove={pick} onPointerDown={pick} onPointerLeave={() => setHover(null)}>
        {ticks.map((t) => (
          <g key={t}>
            <line x1={left} y1={y(t)} x2={w - rightPad} y2={y(t)} stroke={t === 0 ? '#CDD2C8' : '#EEF0EA'} />
            <text x={left - 5} y={y(t) + 3.5} textAnchor="end" fontSize="10" fill="#56655B">{fmtAxis(t)}</text>
          </g>
        ))}
        {unit && <text x={2} y={top - 1} fontSize="9" fill="#56655B">{unit}</text>}
        <polyline points={line} fill="none" stroke="#1F5C3A" strokeWidth="2" strokeLinejoin="round" />
        {n <= 40 && points.map((p, i) => <circle key={i} cx={x(i)} cy={y(p.value)} r="2.5" fill={p.value < 0 ? '#A3271B' : '#1F5C3A'} />)}
        {points.map((p, i) => (i % every === 0 || i === n - 1) && (n - 1 - i >= every / 2 || i === n - 1)
          ? <text key={`l${i}`} x={x(i)} y={h - 14} textAnchor="middle" fontSize="10" fill="#56655B">{p.label}</text> : null)}
        {hp && hover !== null && (
          <g>
            <line x1={x(hover)} y1={top} x2={x(hover)} y2={bottom} stroke="#1F5C3A" strokeDasharray="3 3" />
            <circle cx={x(hover)} cy={y(hp.value)} r="5" fill="#fff" stroke="#1F5C3A" strokeWidth="2.5" />
          </g>
        )}
      </svg>
    </div>
  )
}

