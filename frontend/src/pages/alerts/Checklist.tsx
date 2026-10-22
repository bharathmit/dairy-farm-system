import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Footer, Screen, TopBar } from '../../components/ui'
import { Icon } from '../../components/icons'
import { DateNav, LoadState } from '../../components/records'
import { useAlerts } from '../../components/alerts'
import { useStore } from '../../data/store'
import { taskLink, type Task } from '../../data/alerts'
import { todayIso, useLoad } from '../../data/records'
import { errorMessage } from '../../lib/api'

const time = (iso?: string | null) => (iso ? new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }) : '')

/** Daily checklist: built-in tasks (ticked by the records) and the farm's own tasks. */
export default function Checklist() {
  const { al, isOwner } = useAlerts()
  const { notify } = useStore()
  const [date, setDate] = useState(todayIso())
  const list = useLoad(() => al.checklist(date), [al, date])
  const [editing, setEditing] = useState<Task | 'new' | null>(null)
  const [name, setName] = useState('')
  const [daily, setDaily] = useState(true)
  const [workers, setWorkers] = useState(true)
  const [busy, setBusy] = useState(false)
  const c = list.data
  const pctDone = c && c.total ? Math.round((c.done / c.total) * 100) : 0

  const tick = async (t: Task) => {
    if (t.auto && t.done) { notify('Done automatically from the records'); return }
    try { list.setData(await al.tick(t.key, !t.done, date)) } catch (e) { notify(errorMessage(e)) }
  }
  const startEdit = (t: Task | 'new') => {
    setEditing(t)
    setName(t === 'new' ? '' : t.name)
    setDaily(t === 'new' ? true : t.repeatDaily ?? true)
    setWorkers(t === 'new' ? true : t.workerVisible ?? true)
  }
  const saveTask = async () => {
    if (!name.trim()) { notify('Enter the task name'); return }
    setBusy(true)
    try {
      await al.saveTask({ name: name.trim(), repeatDaily: daily, workerVisible: workers }, editing !== 'new' && editing?.id ? editing.id : undefined)
      notify('Task saved'); setEditing(null); void list.reload()
    } catch (e) { notify(errorMessage(e)) } finally { setBusy(false) }
  }
  const removeTask = async () => {
    if (editing === 'new' || !editing?.id) return
    try { await al.deleteTask(editing.id); notify('Task removed'); setEditing(null); void list.reload() } catch (e) { notify(errorMessage(e)) }
  }

  const row = (t: Task, i: number) => (
    <div key={t.key} className={`flex min-h-14 items-center gap-3 ${i ? 'border-t border-line-faint' : ''}`}>
      <input type="checkbox" checked={t.done} onChange={() => tick(t)} aria-label={t.name} className="h-6 w-6 shrink-0 accent-brand" />
      <span className="flex-1 py-1.5">
        <span className={`text-[15px] font-semibold ${t.done ? 'text-ink-3 line-through' : ''}`}>{t.name}</span><br />
        <span className="text-[13px] text-ink-3">
          {t.done ? [t.doneBy?.name, time(t.doneAt), t.detail].filter(Boolean).join(' · ') : (t.detail ?? (t.builtIn ? '' : t.repeatDaily ? 'Daily' : 'Today only'))}
          {!t.builtIn && t.workerVisible ? ' · visible to workers' : ''}
        </span>
      </span>
      {t.builtIn && !t.done && t.link && <Link to={taskLink(t)} className="text-sm font-semibold no-underline">Open</Link>}
      {!t.builtIn && isOwner && <button type="button" aria-label={`Edit ${t.name}`} onClick={() => startEdit(t)} className="flex h-11 w-11 items-center justify-center border-0 bg-transparent"><Icon name="edit" size={18} color="#56655B" /></button>}
    </div>
  )

  return (
    <Screen>
      <TopBar title="Daily checklist" back="/home" />
      <div className="px-4 pb-4">
        <DateNav id="ck-date" value={date} onChange={setDate} />
        <LoadState loading={list.loading} error={list.error} onRetry={list.reload} empty={!c} />
        {c && (
          <>
            <div className="mt-3 flex items-center justify-between text-sm"><span className="text-ink-3">{c.total - c.done} remaining</span><b>{c.done} of {c.total} done</b></div>
            <div className="mt-2 h-2 rounded bg-track" aria-hidden="true"><div className="h-2 rounded bg-brand" style={{ width: `${pctDone}%` }} /></div>

            <div className="mb-2 mt-[18px] text-xs font-bold uppercase tracking-[.06em] text-ink-3">Built-in</div>
            <div className="rounded-[14px] bg-white px-3.5">{c.builtIn.map(row)}</div>

            <div className="mb-2 mt-[18px] text-xs font-bold uppercase tracking-[.06em] text-ink-3">Custom</div>
            <div className="rounded-[14px] bg-white px-3.5">{c.custom.length ? c.custom.map(row) : <div className="py-3 text-sm text-ink-3">No custom tasks yet.</div>}</div>

            {isOwner && editing === null && (
              <button type="button" onClick={() => startEdit('new')} className="mt-3 flex h-[50px] w-full items-center justify-center gap-2 rounded-xl border-[1.5px] border-dashed border-brand bg-transparent font-semibold text-brand">
                <Icon name="plus" size={20} color="currentColor" strokeWidth={2.4} />Add custom task
              </button>
            )}
            {isOwner && editing !== null && (
              <div className="mt-4 rounded-[14px] bg-white p-3.5">
                <b className="text-[15px]">{editing === 'new' ? 'New custom task' : 'Edit task'}</b>
                <div className="mt-2.5"><label className="lab" htmlFor="ck-name">Task name *</label><input className="inp" id="ck-name" placeholder="e.g. Clean milking cans" value={name} onChange={(e) => setName(e.target.value)} /></div>
                <div className="mt-2.5 flex gap-2">
                  <label className="flex min-h-11 flex-1 items-center gap-2 text-sm"><input type="checkbox" checked={daily} onChange={(e) => setDaily(e.target.checked)} className="h-[22px] w-[22px] accent-brand" />Repeat daily</label>
                  <label className="flex min-h-11 flex-1 items-center gap-2 text-sm"><input type="checkbox" checked={workers} onChange={(e) => setWorkers(e.target.checked)} className="h-[22px] w-[22px] accent-brand" />Workers see</label>
                </div>
                <div className="mt-2 flex gap-2">
                  <button type="button" className="btn2" onClick={() => setEditing(null)}>Cancel</button>
                  <button type="button" className="btn" disabled={busy} onClick={saveTask}>{busy ? 'Saving…' : editing === 'new' ? 'Add task' : 'Save task'}</button>
                </div>
                {editing !== 'new' && <button type="button" onClick={removeTask} className="mt-2 h-11 w-full border-0 bg-transparent font-semibold text-danger">Remove task</button>}
              </div>
            )}
          </>
        )}
      </div>
      <Footer className="mt-auto" />
    </Screen>
  )
}
