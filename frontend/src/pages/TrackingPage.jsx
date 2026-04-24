import { useEffect, useState } from 'react'
import { trackingService } from '../services/index.js'
import StatusBadge from '../components/StatusBadge.jsx'
import ScoreRing from '../components/ScoreRing.jsx'
import { formatRelative, STATUS_META } from '../utils/index.js'

const STATUSES = Object.keys(STATUS_META)

export default function TrackingPage({ flashTrackingId }) {
  const [rows, setRows]       = useState([])
  const [editing, setEditing] = useState(null)
  const [loading, setLoading] = useState(true)

  const load = () => trackingService.list(1).then(setRows).finally(() => setLoading(false))
  useEffect(() => { load() }, [])
  useEffect(() => { if (flashTrackingId) load() }, [flashTrackingId])

  const update = async (id, status) => {
    await trackingService.update(id, status)
    setEditing(null)
    load()
  }

  return (
    <div className="fade-in" style={{ padding: '36px 40px', maxWidth: 900 }}>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 600, margin: 0 }}>Application tracking</h1>
        <p style={{ fontSize: 13, color: '#6b7280', marginTop: 4 }}>
          Status updates auto-applied by email classifier · manually editable
        </p>
      </div>

      {loading && <div style={{ color: '#3a4055', fontSize: 13, fontFamily: 'JetBrains Mono, monospace' }}>loading…</div>}

      {!loading && rows.length === 0 && (
        <div style={{ color: '#3a4055', fontSize: 14, marginTop: 40, textAlign: 'center' }}>
          No tracked applications yet. Click "Track" on any job match.
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {rows.map(row => (
          <div key={row.id} className="fade-in" style={{
            background: flashTrackingId === row.id ? 'rgba(99,102,241,0.06)' : '#141720',
            border: `1px solid ${flashTrackingId === row.id ? '#6366f1' : '#1e2330'}`,
            borderRadius: 12, padding: '14px 18px',
            display: 'flex', alignItems: 'center', gap: 16,
            transition: 'all 0.4s',
          }}>
            <ScoreRing score={row.matchScore ?? 0} size={44} stroke={3} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 14, fontWeight: 500 }}>{row.jobTitle}</div>
              <div style={{ fontSize: 12, color: '#6b7280' }}>{row.companyName}</div>
            </div>
            {editing === row.id ? (
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', maxWidth: 360 }}>
                {STATUSES.map(s => (
                  <button key={s} onClick={() => update(row.id, s)} style={{
                    fontSize: 11, padding: '4px 8px', borderRadius: 4, cursor: 'pointer',
                    border: '1px solid #1e2330',
                    background: STATUS_META[s].bg, color: STATUS_META[s].color,
                    fontFamily: 'JetBrains Mono, monospace',
                  }}>{STATUS_META[s].label}</button>
                ))}
                <button onClick={() => setEditing(null)} style={{
                  fontSize: 11, padding: '4px 8px', borderRadius: 4, cursor: 'pointer',
                  border: '1px solid #1e2330', background: 'transparent', color: '#6b7280',
                }}>Cancel</button>
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <StatusBadge status={row.status} />
                <span style={{ fontSize: 11, color: '#3a4055' }}>{formatRelative(row.updatedAt)}</span>
                <button onClick={() => setEditing(row.id)} style={{
                  background: '#1e2330', border: 'none', borderRadius: 6,
                  color: '#6b7280', fontSize: 12, padding: '4px 10px', cursor: 'pointer',
                }}>Edit</button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
