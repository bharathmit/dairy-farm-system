import { createClient } from '@supabase/supabase-js'

export const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL as string
const SUPABASE_KEY = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY as string

/** Supabase handles sign-in only; all farm data goes through the backend API. */
export const supabase = createClient(SUPABASE_URL, SUPABASE_KEY, {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true },
})

/** True when Google sign-in is turned on in the Supabase project. */
export async function googleEnabled(): Promise<boolean> {
  try {
    const res = await fetch(`${SUPABASE_URL}/auth/v1/settings`, { headers: { apikey: SUPABASE_KEY } })
    const s = await res.json()
    return Boolean(s?.external?.google)
  } catch {
    return false
  }
}
