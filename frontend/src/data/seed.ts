import type { Animal, Farm, Group, Member, Settings } from './types'

// Sample data for the clickable UI. Replaced by API data in a later step.

export const seedFarms: Farm[] = [
  { id: 'f1', name: 'Villupuram Dairy', village: '', district: 'Villupuram', state: 'Tamil Nadu', country: 'India', role: 'Owner', animals: 20, initials: 'VD' },
  { id: 'f2', name: '[Second farm name]', district: '[Location]', role: 'Worker', animals: 0, initials: 'F' },
]

export const seedGroups: Group[] = [
  { id: 'g1', name: 'Milking shed A', species: 'Cow', location: 'Shed A', purpose: 'Maintenance', managerId: 'm3', color: '#1F5C3A' },
  { id: 'g2', name: 'Dry cows', species: 'Cow', location: 'Paddock 2', purpose: 'Gestation', color: '#C2410C' },
  { id: 'g3', name: 'Heifers', species: 'Cow', location: 'Paddock 1', purpose: 'Growth / fattening', color: '#1F4E87' },
  { id: 'g4', name: 'Calves', species: 'Cow', location: 'Calf pen', purpose: 'Growth / fattening', color: '#7A4B0E' },
]

const cow = (a: Partial<Animal> & Pick<Animal, 'id' | 'tag' | 'category'>): Animal => ({
  species: 'Cow', sex: 'Female', source: 'Purchased', status: 'Active', flags: [], ...a,
})

export const seedAnimals: Animal[] = [
  cow({ id: 'a1', tag: 'VD-001', name: 'Lakshmi', breed: 'HF', category: 'Milking', groupId: 'g1', dob: '2021-08-14', lastCalving: '2026-07-28', purchaseDate: '2023-02-10', purchasePrice: 65000, color: 'Black & white', weightKg: 452, dam: 'D-12', sire: 'AI · [bull code]', milkYesterday: 18.5, flags: [{ text: 'Pregnant', tone: 'green' }, { text: 'Milk withdrawal · 2 d', tone: 'red' }] }),
  cow({ id: 'a2', tag: 'VD-002', name: 'Ponni', breed: 'HF', category: 'Milking', groupId: 'g1', dob: '2020-11-02', lastCalving: '2026-06-03', milkYesterday: 15.0 }),
  cow({ id: 'a3', tag: 'VD-003', name: 'Meena', breed: 'HF', category: 'Milking', groupId: 'g1', dob: '2021-01-20', lastCalving: '2026-05-11', milkYesterday: 16.2 }),
  cow({ id: 'a4', tag: 'VD-004', name: 'Selvi', breed: 'HF', category: 'Milking', groupId: 'g1', dob: '2019-09-05', lastCalving: '2026-04-19', milkYesterday: 14.8 }),
  cow({ id: 'a5', tag: 'VD-005', name: 'Kamala', breed: 'HF', category: 'Milking', groupId: 'g1', dob: '2022-03-12', lastCalving: '2026-08-30', milkYesterday: 19.1 }),
  cow({ id: 'a6', tag: 'VD-006', name: 'Valli', breed: 'HF', category: 'Milking', groupId: 'g1', dob: '2020-06-28', lastCalving: '2026-03-22', milkYesterday: 13.4 }),
  cow({ id: 'a7', tag: 'VD-007', name: 'Rani', breed: 'HF', category: 'Dry', groupId: 'g2', dob: '2019-12-01', flags: [{ text: 'Calving in 25 d', tone: 'amber' }] }),
  cow({ id: 'a8', tag: 'VD-008', name: 'Kaveri', breed: 'Jersey', category: 'Dry', groupId: 'g2', dob: '2022-03-15', flags: [{ text: 'Calving in 12 d', tone: 'amber' }] }),
  cow({ id: 'a9', tag: 'VD-009', name: 'Thenmozhi', breed: 'Jersey', category: 'Dry', groupId: 'g2', dob: '2021-04-09' }),
  cow({ id: 'a10', tag: 'VD-010', name: 'Amudha', breed: 'Jersey', category: 'Milking', dob: '2021-10-17', lastCalving: '2026-07-02', milkYesterday: 11.9 }),
  cow({ id: 'a11', tag: 'VD-011', name: 'Malar', breed: 'Jersey', category: 'Milking', groupId: 'g1', dob: '2022-07-21', lastCalving: '2026-09-20', milkYesterday: 12.5, flags: [{ text: 'Heat due', tone: 'amber' }] }),
  cow({ id: 'a12', tag: 'VD-012', name: 'Devi', breed: 'Jersey', category: 'Milking', groupId: 'g1', dob: '2020-02-14', lastCalving: '2026-02-08', milkYesterday: 10.6 }),
  cow({ id: 'a13', tag: 'VD-013', name: 'Pavithra', breed: 'Jersey', category: 'Milking', groupId: 'g1', dob: '2021-05-30', lastCalving: '2026-06-25', milkYesterday: 12.0 }),
  cow({ id: 'a14', tag: 'VD-014', name: 'Sundari', breed: 'Jersey', category: 'Milking', dob: '2022-01-08', lastCalving: '2026-08-12', milkYesterday: 12.8 }),
  cow({ id: 'a15', tag: 'VD-015', name: 'Ganga', breed: 'HF × Jersey', category: 'Heifer', groupId: 'g3', source: 'Born on farm', dob: '2025-08-10', flags: [{ text: 'First AI due', tone: 'blue' }] }),
  cow({ id: 'a16', tag: 'VD-016', name: 'Yamuna', breed: 'HF × Jersey', category: 'Heifer', groupId: 'g3', source: 'Born on farm', dob: '2025-10-02' }),
  cow({ id: 'a17', tag: 'VD-017', breed: 'HF', category: 'Calf', groupId: 'g4', source: 'Born on farm', dob: '2026-06-18', dam: 'VD-004' }),
  cow({ id: 'a18', tag: 'VD-018', breed: 'HF', category: 'Calf', sex: 'Male', groupId: 'g4', source: 'Born on farm', dob: '2026-08-02', dam: 'VD-002' }),
  cow({ id: 'a19', tag: 'VD-019', breed: 'Jersey', category: 'Calf', groupId: 'g4', source: 'Born on farm', dob: '2026-09-28', dam: 'VD-001', flags: [{ text: 'Deworm day 30', tone: 'amber' }] }),
  cow({ id: 'a20', tag: 'VD-020', breed: 'Jersey', category: 'Calf', sex: 'Male', groupId: 'g4', source: 'Born on farm', dob: '2026-10-05', dam: 'VD-012' }),
]

export const seedMembers: Member[] = [
  { id: 'm1', name: 'Bharath', role: 'Owner', email: 'bharath@example.com', canSignIn: true, wageType: 'None', active: true, isMe: true },
  { id: 'm2', name: '[Co-owner name]', role: 'Owner', email: '[co-owner email]', canSignIn: true, wageType: 'None', active: true },
  { id: 'm3', name: 'Murugan', role: 'Worker', sex: 'Male', phone: '+91 9xxxx', email: 'murugan@example.com', canSignIn: true, wageType: 'Daily', wage: 500, joined: '2025-04-01', active: true },
  { id: 'm4', name: 'Selvi', role: 'Worker', sex: 'Female', canSignIn: false, wageType: 'Monthly', wage: 12000, joined: '2025-06-15', active: true },
  { id: 'm5', name: 'Ravi', role: 'Worker', sex: 'Male', canSignIn: false, wageType: 'Daily', wage: 450, joined: '2024-11-01', active: false, inactiveSince: 'Sep 2026' },
]

export const seedSettings: Settings = {
  theme: 'Light',
  language: 'English',
  milkRate: 38,
  morningMilking: '06:00',
  eveningMilking: '18:00',
  marginAlertPct: 30,
  notify: { enabled: true, health: true, breeding: true, checklist: true, lead: '1 day before', reminderTime: '08:00', digest: true, digestTime: '07:00', quietHours: '10 PM – 5 AM' },
}
