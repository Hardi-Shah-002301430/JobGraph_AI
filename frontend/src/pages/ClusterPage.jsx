import { useEffect, useState, useRef } from 'react'
import { clusterService } from '../services/index.js'

const ROLE_COLOR = {
  scheduler: { bg: 'rgba(99,102,241,0.15)', color: '#6366f1' },
  worker:    { bg: 'rgba(20,184,166,0.15)',  color: '#14b8a6' },
  api:       { bg: 'rgba(245,158,11,0.15)',  color: '#f59e0b' },
  'dc-default': { bg: 'rgba(107,114,128,0.1)', color: '#6b7280' },
}

export default function ClusterPage() {
  const [data, setData]         = useState(null)
  const [error, setError]       = useState(false)
  const [history, setHistory]   = useState([])  // [{time, event, detail}]
  const prevNodes               = useRef({})

  const load = () => {
    clusterService.status()
      .then(d => {
        setError(false)
        setData(d)

        // Detect topology changes for the event log
        const now = new Date().toLocaleTimeString('en-US', { hour12: false })
        d.nodes.forEach(n => {
          const prev = prevNodes.current[n.address]
          if (!prev) {
            addEvent(now, 'joined', n.address, n.status, n.roles)
          } else if (prev.status !== n.status) {
            addEvent(now, 'status-change', n.address, n.status, n.roles)
          }
          prevNodes.current[n.address] = n
        })
        // Detect nodes that left
        Object.keys(prevNodes.current).forEach(addr => {
          if (!d.nodes.find(n => n.address === addr)) {
            addEvent(now, 'left', addr, 'removed', [])
            delete prevNodes.current[addr]
          }
        })
      })
      .catch(() => setError(true))
  }

  function addEvent(time, type, address, status, roles) {
    const short = address.replace('akka://JobGraphCluster@', '')
    const label = type === 'joined'        ? `Node joined: ${short}`
                : type === 'left'          ? `Node left: ${short}`
                : `${short} → ${status}`
    setHistory(h => [{ time, label, type }, ...h].slice(0, 20))
  }

  useEffect(() => {
    load()
    const t = setInterval(load, 3000)
    return () => clearInterval(t)
  }, [])

  const nodes    = data?.nodes ?? []
  const upNodes  = nodes.filter(n => n.status === 'Up')
  const downNodes = nodes.filter(n => n.status !== 'Up')
  const singleton = nodes.find(n => n.roles?.includes('scheduler') && n.status === 'Up')

  return (
    <div className="fade-in" style={{ padding: '36px 40px', maxWidth: 860 }}>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 600, margin: 0 }}>Akka cluster</h1>
        <p style={{ fontSize: 13, color: '#6b7280', marginTop: 4 }}>
          Live topology · refreshes every 3 s
          {error && <span style={{ color: '#f43f5e', marginLeft: 12 }}>
            — cannot reach backend, trying…
          </span>}
        </p>
      </div>

      {/* Metric row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 14, marginBottom: 24 }}>
        <Stat label="Total nodes" value={data?.totalNodes ?? '—'} />
        <Stat label="Up"          value={upNodes.length}   color="#22c55e" />
        <Stat label="Down"        value={downNodes.length} color={downNodes.length > 0 ? '#f43f5e' : '#6b7280'} />
        <Stat label="Scheduler on"
          value={singleton ? singleton.address.replace('akka://JobGraphCluster@','') : 'migrating…'}
          color="#6366f1"
          small
        />
      </div>

      {/* Singleton highlight */}
      {singleton && (
        <div style={{
          background: 'rgba(99,102,241,0.07)', border: '1px solid rgba(99,102,241,0.25)',
          borderRadius: 12, padding: '14px 18px', marginBottom: 20,
          display: 'flex', alignItems: 'center', gap: 12,
        }}>
          <span className="pulse-dot" style={{
            width: 8, height: 8, borderRadius: '50%', background: '#6366f1',
            flexShrink: 0, display: 'inline-block',
          }} />
          <span style={{ fontSize: 13, color: '#e2e8f0' }}>
            <span style={{ color: '#6366f1', fontWeight: 500 }}>SchedulerAgent singleton</span>
            {' '}running on{' '}
            <span style={{ fontFamily: 'JetBrains Mono, monospace', color: '#6366f1' }}>
              {singleton.address.replace('akka://JobGraphCluster@', '')}
            </span>
            {' '}— will migrate automatically if this node goes down
          </span>
        </div>
      )}

      {/* Node list */}
      <div style={{ background: '#141720', border: '1px solid #1e2330', borderRadius: 12, padding: '16px 20px', marginBottom: 20 }}>
        <div style={{ fontSize: 11, color: '#6b7280', textTransform: 'uppercase', letterSpacing: 1, fontFamily: 'JetBrains Mono, monospace', marginBottom: 12 }}>
          Nodes · leader: <span style={{ color: '#6366f1' }}>{data?.leaderAddress?.replace('akka://JobGraphCluster@','') ?? '—'}</span>
        </div>
        {nodes.length === 0 && !data && (
          <div style={{ color: '#3a4055', fontSize: 13, fontFamily: 'JetBrains Mono, monospace' }}>loading…</div>
        )}
        {nodes.map((n, i) => {
          const short   = n.address.replace('akka://JobGraphCluster@', '')
          const isUp    = n.status === 'Up'
          const isSingleton = n.roles?.includes('scheduler') && isUp
          return (
            <div key={n.address} style={{
              display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap',
              padding: '12px 0',
              borderBottom: i < nodes.length - 1 ? '1px solid #1e2330' : 'none',
              opacity: isUp ? 1 : 0.5,
            }}>
              <span style={{
                width: 8, height: 8, borderRadius: '50%', flexShrink: 0,
                background: isUp ? '#22c55e' : '#f43f5e',
              }} />
              <span style={{ fontSize: 13, fontFamily: 'JetBrains Mono, monospace', color: '#e2e8f0', flex: 1, minWidth: 180 }}>
                {short}
                {isSingleton && (
                  <span style={{ marginLeft: 8, fontSize: 10, color: '#6366f1', fontFamily: 'JetBrains Mono, monospace' }}>
                    ★ singleton
                  </span>
                )}
              </span>
              <span style={{
                fontSize: 10, padding: '2px 8px', borderRadius: 4,
                background: isUp ? 'rgba(34,197,94,0.12)' : 'rgba(244,63,94,0.12)',
                color: isUp ? '#22c55e' : '#f43f5e',
                fontFamily: 'JetBrains Mono, monospace', textTransform: 'uppercase',
              }}>{n.status}</span>
              <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap' }}>
                {n.roles?.filter(r => r !== 'dc-default').map(r => {
                  const c = ROLE_COLOR[r] ?? ROLE_COLOR['dc-default']
                  return (
                    <span key={r} style={{
                      fontSize: 10, padding: '2px 7px', borderRadius: 4,
                      background: c.bg, color: c.color,
                      fontFamily: 'JetBrains Mono, monospace',
                    }}>{r}</span>
                  )
                })}
              </div>
            </div>
          )
        })}
      </div>

      {/* Event log */}
      <div style={{ background: '#141720', border: '1px solid #1e2330', borderRadius: 12, padding: '16px 20px' }}>
        <div style={{ fontSize: 11, color: '#6b7280', textTransform: 'uppercase', letterSpacing: 1, fontFamily: 'JetBrains Mono, monospace', marginBottom: 12 }}>
          Topology events
        </div>
        {history.length === 0 ? (
          <div style={{ fontSize: 12, color: '#3a4055', fontFamily: 'JetBrains Mono, monospace' }}>
            Watching for changes…
          </div>
        ) : history.map((e, i) => (
          <div key={i} style={{
            display: 'flex', gap: 12, fontSize: 12, fontFamily: 'JetBrains Mono, monospace',
            padding: '5px 0', borderBottom: i < history.length - 1 ? '1px solid #1e2330' : 'none',
          }}>
            <span style={{ color: '#3a4055', flexShrink: 0 }}>{e.time}</span>
            <span style={{
              color: e.type === 'left' ? '#f43f5e'
                   : e.type === 'joined' ? '#22c55e'
                   : '#f59e0b',
            }}>{e.label}</span>
          </div>
        ))}
      </div>

      {/* Demo instructions */}
      <div style={{
        marginTop: 20, background: '#0d0f14', border: '1px solid #1e2330',
        borderRadius: 12, padding: '16px 20px',
      }}>
        <div style={{ fontSize: 11, color: '#6b7280', textTransform: 'uppercase', letterSpacing: 1, fontFamily: 'JetBrains Mono, monospace', marginBottom: 10 }}>
          Demo: kill a node without losing the app
        </div>
        <div style={{ fontSize: 12, color: '#6b7280', lineHeight: 2, fontFamily: 'JetBrains Mono, monospace' }}>
          <div>1. All 3 nodes are up — scheduler singleton on node1 (port 8081)</div>
          <div>2. Kill node1 → watch "Node left" event appear here in ~25 s</div>
          <div>3. Singleton migrates to node2 — "★ singleton" moves to :2552</div>
          <div style={{ color: '#f59e0b' }}>4. Switch frontend: set NODE_PORT=8082 and restart Vite</div>
          <div>5. App keeps working — scheduler still ticking, jobs still scoring</div>
        </div>
      </div>
    </div>
  )
}

function Stat({ label, value, color = '#e2e8f0', small }) {
  return (
    <div style={{ background: '#141720', border: '1px solid #1e2330', borderRadius: 10, padding: '16px 20px' }}>
      <div style={{ fontSize: 11, color: '#6b7280', textTransform: 'uppercase', letterSpacing: 1, fontFamily: 'JetBrains Mono, monospace' }}>{label}</div>
      <div style={{ fontSize: small ? 14 : 28, fontWeight: 600, color, fontFamily: 'JetBrains Mono, monospace', marginTop: 4, wordBreak: 'break-all' }}>{value}</div>
    </div>
  )
}