import { createContext } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

/** True on farm screens that show the main menu (forms with their own Save bar don't). */
export const MenuContext = createContext(false)

/** Go back one step when we came from another screen of the app; else open `fallback` in place of this one. */
export function useGoBack() {
  const nav = useNavigate()
  const loc = useLocation()
  return (fallback: string) => (loc.key !== 'default' ? nav(-1) : nav(fallback, { replace: true }))
}
