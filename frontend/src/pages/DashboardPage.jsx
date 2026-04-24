import { useEffect, useState } from 'react'
import { jobService } from '../services/index.js'
import MetricCard from '../components/MetricCard.jsx'
import { STATUS_META, formatRelative } from '../utils/index.js'

export default function DashboardPage({ liveEvents }) {
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)

  const load = () => jobService.dashboard().then(setStats).finally(() => setLoading(false))
  useEffect(() => { load() }, [])

  // Refresh stats when live events arrive
  useEffect(() => { if (liveEvents.length) load() }, [liveEvents.length])

  const byStatus = stats?.applicationsByStatus ?? {}

  return (
    <div className="fade-in" style={{ padding: '36px 40px', maxWidth: 1100 }}>
      <div style={{ marginBottom: 28 }}>
        <h1 style={{ fontSize: 22, fontWeight: 600, margin: 0 }}>Dashboard</h1>
        <p style={{ fontSize: 13, color: '#6b7280', marginTop: 4 }}>Live pipeline overview</p>
      </div>

      {loading ? <Spinner /> : (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 16, marginBottom: 32 }}>
            <MetricCard label="Total jobs"       value={stats?.totalJobs}        accent="#6366f1" />
            <MetricCard label="Matches scored"   value={stats?.totalMatches}     accent="#14b8a6" />
            <MetricCard label="Applications"     value={stats?.totalApplications} accent="#f59e0b" />
            <MetricCard label="Avg match score"  value={stats?.averageMatchScore != null ? stats.averageMatchScore.toFixed(1) : '—'} accent="#22c55e" sub="out of 100" />
            <MetricCard label="Top score"        value={stats?.topMatchScore != null ? stats.topMatchScore.toFixed(1) : '—'} accent="#f43f5e" />
            <MetricCard label="Companies"        value={stats?.totalCompanies}   accent="#6b7280" />
          </div>

          <div style={{ background: '#141720', border: '1px solid #1e2330', borderRadius: 12, padding: '20px 24px', marginBottom: 32 }}>
            <div style={{ fontSize: 12, color: '#6b7280', textTransform: 'uppercase', letterSpacing: 1, fontFamily: 'JetBrains Mono, monospace', marginBottom: 14 }}>Applications by status</div>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              {Object.entries(byStatus).map(([k, v]) => {
                const m = STATUS_META[k]
                if (!m || v === 0) return null
                return (
                  <div key={k} style={{ display: 'flex', alignItems: 'center', gap: 6, background: m.bg, borderRadius: 8, padding: '8px 14px' }}>
                    <span style={{ fontSize: 20, fontWeight: 700, color: m.color, fontFamily: 'JetBrains Mono, monospace' }}>{v}</span>
                    <span style={{ fontSize: 11, color: m.color, textTransform: 'uppercase', letterSpacing: 0.5 }}>{m.label}</span>
                  </div>
                )
              })}
            </div>
          </div>

          <div style={{ background: '#141720', border: '1px solid #1e2330', borderRadius: 12, padding: '20px 24px' }}>
            <div style={{ fontSize: 12, color: '#6b7280', textTransform: 'uppercase', letterSpacing: 1, fontFamily: 'JetBrains Mono, monospace', marginBottom: 12 }}>Live events</div>
            {liveEvents.length === 0
              ? <div style={{ fontSize: 13, color: '#3a4055' }}>Waiting for pipeline activity…</div>
              : liveEvents.slice(0, 20).map((e, i) => (
                <div key={i} style={{ fontSize: 12, fontFamily: 'JetBrains Mono, monospace', color: '#6b7280', padding: '4px 0', borderBottom: '1px solid #1e2330' }}>{e}</div>
              ))
            }
          </div>
        </>
      )}
    </div>
  )
}

function Spinner() {
  return <div style={{ color: '#3a4055', fontSize: 13, fontFamily: 'JetBrains Mono, monospace' }}>loading…</div>
}
