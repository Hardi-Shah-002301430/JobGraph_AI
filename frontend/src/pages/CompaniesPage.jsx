import { useEffect, useState } from 'react'
import { companyService } from '../services/index.js'

const BOARD_TYPES = ['ADZUNA', 'ASHBY', 'GREENHOUSE', 'LEVER', 'GENERIC']

export default function CompaniesPage() {
  const [companies, setCompanies] = useState([])
  const [loading, setLoading]     = useState(true)
  const [form, setForm]           = useState({ name: '', careersUrl: '', boardType: 'ASHBY', industry: '' })
  const [adding, setAdding]       = useState(false)
  const [showForm, setShowForm]   = useState(false)
  const [error, setError]         = useState(null)

  const load = () => companyService.list().then(setCompanies).finally(() => setLoading(false))
  useEffect(() => { load() }, [])

  const submit = async () => {
    if (!form.name.trim() || !form.careersUrl.trim()) { setError('Name and careers URL are required'); return }
    setAdding(true); setError(null)
    try { await companyService.create(form); await load(); setShowForm(false); setForm({ name: '', careersUrl: '', boardType: 'ASHBY', industry: '' }) }
    catch (e) { setError(e.message) }
    finally { setAdding(false) }
  }

  const toggle = async (id) => { await companyService.toggle(id); load() }
  const del    = async (id) => { if (confirm('Remove this company?')) { await companyService.delete(id); load() } }

  return (
    <div className="fade-in" style={{ padding: '36px 40px', maxWidth: 900 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 24 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 600, margin: 0 }}>Companies</h1>
          <p style={{ fontSize: 13, color: '#6b7280', marginTop: 4 }}>Tracked job sources</p>
        </div>
        <button onClick={() => setShowForm(f => !f)} style={{
          background: '#6366f1', border: 'none', borderRadius: 8,
          color: '#fff', padding: '9px 18px', cursor: 'pointer', fontWeight: 500, fontSize: 13,
        }}>+ Add company</button>
      </div>

      {showForm && (
        <div className="fade-in" style={{ background: '#141720', border: '1px solid #1e2330', borderRadius: 12, padding: 24, marginBottom: 20 }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 12 }}>
            <Input label="Company name" value={form.name}       onChange={v => setForm(f => ({ ...f, name: v }))} />
            <Input label="Careers URL"  value={form.careersUrl} onChange={v => setForm(f => ({ ...f, careersUrl: v }))} />
            <Input label="Industry"     value={form.industry}   onChange={v => setForm(f => ({ ...f, industry: v }))} />
            <div>
              <label style={{ fontSize: 11, color: '#6b7280', display: 'block', marginBottom: 4, textTransform: 'uppercase', letterSpacing: 1, fontFamily: 'JetBrains Mono, monospace' }}>Board type</label>
              <select value={form.boardType} onChange={e => setForm(f => ({ ...f, boardType: e.target.value }))} style={{
                width: '100%', background: '#0d0f14', border: '1px solid #1e2330',
                borderRadius: 6, color: '#e2e8f0', padding: '8px 10px', fontSize: 13,
              }}>
                {BOARD_TYPES.map(t => <option key={t}>{t}</option>)}
              </select>
            </div>
          </div>
          {error && <div style={{ color: '#f43f5e', fontSize: 12, marginBottom: 10 }}>{error}</div>}
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={submit} disabled={adding} style={{
              background: '#6366f1', border: 'none', borderRadius: 8, color: '#fff',
              padding: '9px 18px', cursor: 'pointer', fontSize: 13, opacity: adding ? 0.6 : 1,
            }}>{adding ? 'Saving…' : 'Save'}</button>
            <button onClick={() => setShowForm(false)} style={{
              background: '#1e2330', border: 'none', borderRadius: 8, color: '#6b7280',
              padding: '9px 18px', cursor: 'pointer', fontSize: 13,
            }}>Cancel</button>
          </div>
        </div>
      )}

      {loading && <div style={{ color: '#3a4055', fontSize: 13, fontFamily: 'JetBrains Mono, monospace' }}>loading…</div>}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {companies.map(c => (
          <div key={c.id} style={{
            background: '#141720', border: '1px solid #1e2330', borderRadius: 10,
            padding: '12px 18px', display: 'flex', alignItems: 'center', gap: 14,
            opacity: c.active ? 1 : 0.45,
          }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 14, fontWeight: 500 }}>{c.name}</div>
              <div style={{ fontSize: 11, color: '#3a4055', fontFamily: 'JetBrains Mono, monospace', marginTop: 2 }}>
                {c.boardType} · {c.industry ?? 'unknown industry'} · {c.jobCount} jobs
              </div>
            </div>
            <span style={{
              fontSize: 10, padding: '2px 7px', borderRadius: 4,
              background: c.active ? 'rgba(34,197,94,0.12)' : 'rgba(107,114,128,0.12)',
              color: c.active ? '#22c55e' : '#6b7280',
              fontFamily: 'JetBrains Mono, monospace', textTransform: 'uppercase',
            }}>{c.active ? 'active' : 'paused'}</span>
            <button onClick={() => toggle(c.id)} style={{
              background: '#1e2330', border: 'none', borderRadius: 6,
              color: '#6b7280', fontSize: 11, padding: '4px 10px', cursor: 'pointer',
            }}>{c.active ? 'Pause' : 'Resume'}</button>
            <button onClick={() => del(c.id)} style={{
              background: 'rgba(244,63,94,0.1)', border: 'none', borderRadius: 6,
              color: '#f43f5e', fontSize: 11, padding: '4px 10px', cursor: 'pointer',
            }}>Delete</button>
          </div>
        ))}
      </div>

      {!loading && companies.length === 0 && (
        <div style={{ color: '#3a4055', fontSize: 14, marginTop: 40, textAlign: 'center' }}>
          No companies yet. Adzuna creates them automatically when it scrapes jobs.
        </div>
      )}
    </div>
  )
}

function Input({ label, value, onChange }) {
  return (
    <div>
      <label style={{ fontSize: 11, color: '#6b7280', display: 'block', marginBottom: 4, textTransform: 'uppercase', letterSpacing: 1, fontFamily: 'JetBrains Mono, monospace' }}>{label}</label>
      <input value={value} onChange={e => onChange(e.target.value)} style={{
        width: '100%', background: '#0d0f14', border: '1px solid #1e2330',
        borderRadius: 6, color: '#e2e8f0', padding: '8px 10px', fontSize: 13,
      }} />
    </div>
  )
}
