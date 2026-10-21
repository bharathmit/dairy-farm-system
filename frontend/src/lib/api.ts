import { supabase } from './supabase'

const API_URL = (import.meta.env.VITE_API_URL as string).replace(/\/+$/, '')

/** A failed API call, with the backend's plain-language message. */
export class ApiError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function request<T>(method: string, path: string, body?: unknown, asBlob = false): Promise<T> {
  const { data } = await supabase.auth.getSession()
  const headers: Record<string, string> = {}
  if (data.session) headers.Authorization = `Bearer ${data.session.access_token}`
  let payload: BodyInit | undefined
  if (body instanceof FormData) payload = body
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }

  let res: Response
  try {
    res = await fetch(API_URL + path, { method, headers, body: payload })
  } catch {
    throw new ApiError("Can't reach the server. Check your connection and try again.", 0)
  }

  if (!res.ok) {
    let message = `Something went wrong (${res.status}). Try again.`
    try {
      const problem = await res.json()
      const fields = problem.errors ? Object.values(problem.errors as Record<string, string>) : []
      if (fields.length) message = String(fields[0])
      else if (problem.detail) message = problem.detail
    } catch { /* not JSON */ }
    if (res.status === 401) message = 'Your session has ended. Sign in again.'
    throw new ApiError(message, res.status)
  }
  if (asBlob) return (await res.blob()) as T
  if (res.status === 204) return undefined as T
  const type = res.headers.get('content-type') ?? ''
  return (type.includes('json') ? await res.json() : undefined) as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
  del: (path: string) => request<void>('DELETE', path),
  blob: (path: string) => request<Blob>('GET', path, undefined, true),
}

export function errorMessage(e: unknown) {
  return e instanceof Error ? e.message : 'Something went wrong. Try again.'
}
