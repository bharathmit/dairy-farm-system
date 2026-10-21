import { Link } from 'react-router-dom'
import { BottomNav, Footer, LogoMark, Screen } from '../components/ui'
import AccountButton from '../components/AccountButton'

/** Tabs that belong to later modules (dashboard, alerts). */
export default function Placeholder({ title, tab }: { title: string; tab: 'home' | 'alerts' }) {
  return (
    <Screen>
      <div className="flex items-center gap-2 px-4 pb-2.5 pt-3.5">
        <LogoMark />
        <h1 className="m-0 flex-1 text-2xl font-bold">{title}</h1>
        <AccountButton />
      </div>
      <div className="card mt-4">
        <div className="text-base font-bold">Coming in a later module</div>
        <p className="m-0 mt-1 text-sm leading-relaxed text-ink-3">
          {tab === 'home' ? 'The dashboard and KPIs are built in Module 15.' : 'Alert rules and the alert list are built in Module 14.'} For now, go to the <Link to="/animals" className="font-semibold">Herd</Link>.
        </p>
      </div>
      <div className="flex-1" />
      <Footer />
      <BottomNav active={tab} />
    </Screen>
  )
}
