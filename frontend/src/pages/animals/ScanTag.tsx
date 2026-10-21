import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Footer, LogoMark } from '../../components/ui'
import { Icon } from '../../components/icons'
import { useStore } from '../../data/store'
import type { Animal } from '../../data/types'

export default function ScanTag() {
  const { animals, notify } = useStore()
  const [found, setFound] = useState<Animal | null>(null)
  const [typing, setTyping] = useState(false)
  const [tag, setTag] = useState('')
  const [miss, setMiss] = useState('')

  const find = (e: FormEvent) => {
    e.preventDefault()
    const a = animals.find((x) => x.tag.toLowerCase() === tag.trim().toLowerCase())
    setFound(a ?? null)
    setMiss(a ? '' : `No animal with tag "${tag.trim()}"`)
  }
  const simulate = () => {
    const a = animals.find((x) => x.tag === 'VD-001') ?? animals[0] ?? null
    setFound(a)
    setMiss('')
  }

  const small = 'flex h-[46px] items-center justify-center rounded-xl text-sm font-semibold no-underline'

  return (
    <div className="relative mx-auto flex min-h-[100dvh] w-full max-w-[430px] flex-col bg-[#121815] text-white">
      <div className="flex items-center px-3 py-2">
        <Link to="/animals" aria-label="Close scanner" className="flex h-11 w-11 items-center justify-center"><Icon name="close" color="#fff" /></Link>
        <div className="mr-11 flex flex-1 items-center justify-center gap-2"><LogoMark /><h1 className="m-0 text-[17px] font-semibold">Scan ear tag or QR</h1></div>
      </div>

      <div className="flex flex-1 flex-col items-center justify-center px-6">
        <div className="relative h-[280px] w-[280px] rounded-[20px] border-[3px] border-[#9FD3B0]" aria-label="Camera view" role="img">
          <div className="absolute left-5 right-5 top-1/2 h-0.5 bg-[#9FD3B0]" />
        </div>
        <p className="m-0 mt-6 text-center text-[15px] text-[#D8E2DA]">Point the camera at the QR sticker or ear-tag number</p>
        {!found && (
          <button type="button" onClick={simulate} className="mt-4 h-11 rounded-xl border border-[#9FD3B0] bg-transparent px-4 font-semibold text-[#9FD3B0]">Simulate scan (demo)</button>
        )}
      </div>

      <div className="mx-4 mb-2 rounded-[18px] bg-white p-4 text-ink">
        {found ? (
          <>
            <div className="text-[13px] text-ink-3">Found</div>
            <div className="mb-3 mt-0.5 text-xl font-bold">{found.tag}{found.name ? ` · ${found.name}` : ''}</div>
            <div className="grid grid-cols-3 gap-2">
              <Link to={`/animals/${found.id}`} className={`${small} bg-brand text-white hover:text-white`}>Open</Link>
              <button type="button" onClick={() => notify('Milk records come in a later module')} className={`${small} border border-line bg-white text-ink`}>+ Milk</button>
              <button type="button" onClick={() => notify('Health records come in a later module')} className={`${small} border border-line bg-white text-ink`}>+ Health</button>
            </div>
          </>
        ) : (
          <div className="text-[15px] text-ink-2">{miss || 'Waiting for a tag…'}</div>
        )}
        {typing ? (
          <form onSubmit={find} className="mt-3 flex gap-2">
            <label htmlFor="tagq" className="sr-only">Tag number</label>
            <input id="tagq" className="inp" placeholder="e.g. VD-008" value={tag} onChange={(e) => setTag(e.target.value)} autoFocus />
            <button type="submit" className="h-12 shrink-0 rounded-[10px] bg-brand px-4 font-semibold text-white">Find</button>
          </form>
        ) : (
          <button type="button" onClick={() => setTyping(true)} className="mt-2.5 h-11 w-full border-0 bg-transparent font-semibold text-brand">Type tag number instead</button>
        )}
      </div>
      <Footer className="!text-[#D8E2DA]" />
    </div>
  )
}
