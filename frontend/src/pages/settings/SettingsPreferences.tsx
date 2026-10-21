import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ActionBar, Screen, Segmented, TopBar } from '../../components/ui'
import { useStore } from '../../data/store'
import type { Settings } from '../../data/types'

export default function SettingsPreferences() {
  const { settings, updateSettings, notify } = useStore()
  const nav = useNavigate()
  const [theme, setTheme] = useState<Settings['theme']>(settings.theme)
  const [language, setLanguage] = useState<Settings['language']>(settings.language)

  return (
    <Screen>
      <TopBar title="Theme & language" back="/settings" />
      <div className="sec">Theme</div>
      <Segmented<Settings['theme']> label="Theme" className="mx-4" value={theme} onChange={setTheme}
        options={[{ value: 'Light', label: 'Light' }, { value: 'Dark', label: 'Dark' }, { value: 'System', label: 'System' }]} />
      <div className="sec">Language</div>
      <Segmented<Settings['language']> label="Language" className="mx-4" value={language} onChange={setLanguage}
        options={[{ value: 'English', label: 'English' }, { value: 'Tamil', label: <span lang="ta">தமிழ்</span> }]} />
      <ActionBar>
        <button type="button" className="btn" onClick={() => { void updateSettings({ theme, language }).then((ok) => { if (ok) { notify('Preferences saved'); nav('/settings') } }) }}>Save</button>
      </ActionBar>
    </Screen>
  )
}
