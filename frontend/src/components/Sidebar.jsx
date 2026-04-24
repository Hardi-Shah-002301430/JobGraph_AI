import { NavLink } from 'react-router-dom'

const NAV = [
  { to: '/',          icon: '▦', label: 'Dashboard'   },
  { to: '/matches',   icon: '◎', label: 'Job matches' },
  { to: '/resume',    icon: '◈', label: 'Resume'      },
  { to: '/tracking',  icon: '◷', label: 'Tracking'    },
  { to: '/companies', icon: '⬡', label: 'Companies'   },
  { to: '/cluster',   icon: '⬢', label: 'Cluster'     },
]

export default function Sidebar({ wsStatus, liveEvents }) {
  return (
    <aside style={{ width: 220, minHeight: '100vh', background: '#141720', borderRight: '1px solid #1e2330', display: 'flex', flexDirection: 'column', flexShrink: 0 }}>
      <div style={{ padding: '28px 20px 20px' }}>
        <div style={{ fontFamily: 'JetBrains Mono, monospace', fontWeight: 500, fontSize: 15, color: '#6366f1', letterSpacing: 1 }}>JOBGRAPH</div>
        <div style={{ fontSize: 11, color: '#3a4055', marginTop: 2, fontFamily: 'JetBrains Mono, monospace' }}>AI · multi-agent</div>
      </div>

      <nav style={{ flex: 1, padding: '8px 12px' }}>
        {NAV.map(({ to, icon, label }) => (
          <NavLink key={to} to={to} end={to === '/'} style={({ isActive }) => ({
            display: 'flex', alignItems: 'center', gap: 10, padding: '9px 10px',
            borderRadius: 8, marginBottom: 2, textDecoration: 'none',
            color: isActive ? '#e2e8f0' : '#6b7280',
            background: isActive ? '#1e2330' : 'transparent',
            fontSize: 14, fontWeight: isActive ? 500 : 400,
            transition: 'all 0.15s',
          })}>
            <span style={{ fontSize: 13, width: 16, textAlign: 'center', opacity: 0.8 }}>{icon}</span>
            {label}
          </NavLink>
        ))}
      </nav>

      <div style={{ padding: '16px 20px', borderTop: '1px solid #1e2330' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 10 }}>
          <span className={wsStatus === 'connected' ? 'pulse-dot' : ''} style={{
            width: 7, height: 7, borderRadius: '50%',
            background: wsStatus === 'connected' ? '#22c55e' : '#f43f5e',
            display: 'inline-block',
          }} />
          <span style={{ fontSize: 11, color: '#6b7280', fontFamily: 'JetBrains Mono, monospace' }}>
            {wsStatus === 'connected' ? 'live' : 'offline'}
          </span>
        </div>
        {liveEvents.length > 0 && (
          <div style={{ fontSize: 11, color: '#3a4055', fontFamily: 'JetBrains Mono, monospace', lineHeight: 1.7 }}>
            {liveEvents.slice(0, 3).map((e, i) => (
              <div key={i} style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {e}
              </div>
            ))}
          </div>
        )}
      </div>
    </aside>
  )
}
