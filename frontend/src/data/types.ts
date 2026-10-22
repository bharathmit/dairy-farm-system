// Shapes the UI works with. data/mappers.ts converts them to and from the API.

export type Species = 'Cow' | 'Buffalo' | 'Goat' | 'Sheep' | 'Pig' | 'Poultry'
export type Sex = 'Female' | 'Male'
export type Category = 'Milking' | 'Dry' | 'Heifer' | 'Calf' | 'Bull' | 'Other'
export type AnimalStatus = 'Active' | 'Sold' | 'Dead' | 'Transferred'
export type Role = 'Owner' | 'Worker'

export interface Animal {
  id: string
  tag: string
  name?: string
  nationalId?: string
  species: Species
  sex: Sex
  breed?: string
  category: Category
  color?: string
  groupId?: string
  source: 'Born on farm' | 'Purchased'
  dob?: string
  birthWeightKg?: number
  purchaseDate?: string
  purchasePrice?: number
  lastCalving?: string
  weightKg?: number
  dam?: string
  sire?: string
  notes?: string
  status: AnimalStatus
  /** Display-only flags until the health/breeding modules exist. */
  flags: { text: string; tone: 'green' | 'red' | 'amber' | 'blue' }[]
  milkYesterday?: number
  exit?: { date: string; price?: number; reason: string; buyer?: string }
  /** Up to 3; url is a short-lived signed link. */
  photos?: { id: string; url?: string }[]
}

export interface Group {
  id: string
  name: string
  species: Species
  location?: string
  purpose: string
  managerId?: string
  color: string
  notes?: string
}

export interface Member {
  id: string
  name: string
  role: Role
  dob?: string
  sex?: Sex
  phone?: string
  email?: string
  canSignIn: boolean
  wageType: 'Daily' | 'Monthly' | 'None'
  wage?: number
  joined?: string
  active: boolean
  inactiveSince?: string
  isMe?: boolean
  /** NONE, PASSWORD (owner set it), INVITE (email link) or SELF (their own account). */
  loginType?: string
}

export interface Farm {
  id: string
  name: string
  village?: string
  district?: string
  state?: string
  country?: string
  role: Role
  animals: number
  initials: string
}

export interface Settings {
  theme: 'Light' | 'Dark' | 'System'
  language: 'English' | 'Tamil'
  milkRate: number
  morningMilking: string
  eveningMilking: string
  marginAlertPct: number
  /** Paid leave days a month for monthly wages; more absences are deducted. */
  leaveDaysPerMonth: number
  notify: { enabled: boolean; health: boolean; breeding: boolean; checklist: boolean; lead: string; reminderTime: string; digest: boolean; digestTime: string; quietHours: string }
}

export interface User {
  name: string
  email: string
}
