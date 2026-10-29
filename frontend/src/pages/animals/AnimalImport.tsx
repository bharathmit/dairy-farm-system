import { useRef, useState, type ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ActionBar, Screen, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { useStore, type ImportResult } from '../../data/store'
import { CSV_COLUMNS } from './shared'

interface Check extends ImportResult { file: File }

export default function AnimalImport() {
  const { checkImport, runImport, downloadExport, downloadTemplate, notify } = useStore()
  const nav = useNavigate()
  const input = useRef<HTMLInputElement>(null)
  const [result, setResult] = useState<Check | null>(null)
  const [busy, setBusy] = useState(false)

  const onFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0]
    e.target.value = ''
    if (!f) return
    setBusy(true)
    const res = await checkImport(f)
    setBusy(false)
    setResult(res ? { ...res, file: f } : null)
  }

  const doImport = async () => {
    if (!result) return
    setBusy(true)
    const res = await runImport(result.file)
    setBusy(false)
    if (!res) return
    notify(`${res.created} ${res.created === 1 ? 'animal' : 'animals'} imported`)
    nav('/animals', { replace: true })
  }

  const n = result?.ready ?? 0
  const step = 'flex items-start gap-3'
  const num = (k: number) => <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand text-sm font-bold text-white">{k}</div>

  return (
    <Screen>
      <TopBar title="Import & export" back="/animals" />
      <div className="flex flex-col gap-5 px-5 py-2">
        <div className={step}>{num(1)}<div className="flex-1">
          <div className="text-base font-semibold">Download the template</div>
          <div className="mb-2.5 mt-1 text-sm leading-snug text-ink-3">Columns: {CSV_COLUMNS.map((c) => (['tag_number', 'sex', 'species'].includes(c) ? `${c}*` : c)).join(', ')}.</div>
          <button type="button" onClick={() => { void downloadTemplate() }} className="h-[46px] rounded-xl border border-brand bg-white px-3.5 text-[15px] font-semibold text-brand">Download CSV template</button>
        </div></div>

        <div className={step}>{num(2)}<div className="flex-1">
          <div className="text-base font-semibold">Upload your file</div>
          <input ref={input} type="file" accept=".csv,text/csv" className="sr-only" id="csv" onChange={onFile} />
          <label htmlFor="csv" className="mt-2.5 block cursor-pointer rounded-[14px] border-2 border-dashed border-[#A9B3A6] bg-white p-[22px] text-center">
            <span className="flex justify-center"><Icon name="upload" size={28} color="#1F5C3A" /></span>
            {result ? (
              <><div className="mt-1.5 font-semibold">{result.file.name}</div><div className="text-[13px] text-ink-3">{result.rows} rows read · tap to choose another</div></>
            ) : (
              <><div className="mt-1.5 font-semibold">{busy ? 'Checking…' : 'Choose a CSV file'}</div><div className="text-[13px] text-ink-3">Use the template columns</div></>
            )}
          </label>
        </div></div>

        <div className={step}>{num(3)}<div className="flex-1">
          <div className="text-base font-semibold">Check before importing</div>
          {result ? (
            <div className="mt-2.5 overflow-hidden rounded-xl bg-white">
              <div className="flex justify-between px-3.5 py-3 text-[15px]"><span>Ready to add</span><b className="text-brand">{result.ready}</b></div>
              <div className="flex justify-between border-t border-line-faint px-3.5 py-3 text-[15px]"><span>Tag already exists — skip</span><b>{result.duplicates.length}</b></div>
              {result.errors.map((er) => <div key={er} className="border-t border-line-faint px-3.5 py-3 text-sm text-danger-text">{er}</div>)}
            </div>
          ) : (
            <div className="mt-1 text-sm text-ink-3">Upload a file to see what will be added.</div>
          )}
        </div></div>
      </div>
      <ActionBar>
        <button type="button" onClick={() => { void downloadExport() }} className="h-[50px] flex-1 rounded-xl border border-line bg-white text-[15px] font-semibold text-ink">Export herd</button>
        <button
          type="button"
          disabled={n === 0 || busy}
          onClick={() => { void doImport() }}
          className="h-[50px] flex-[2] rounded-xl bg-brand text-base font-semibold text-white disabled:opacity-50"
        >
          Import {n} {n === 1 ? 'animal' : 'animals'}
        </button>
      </ActionBar>
    </Screen>
  )
}
