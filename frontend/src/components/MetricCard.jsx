export default function MetricCard({ label, value, sub, accent = '#6366f1' }) {
  return (
    <div style={{
      background: '#141720', border: '1px solid #1e2330', borderRadius: 12,
      padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 4,
    }}>
      <div style={{ fontSize: 11, color: '#6b7280', textTransform: 'uppercase', letterSpacing: 1, fontFamily: 'JetBrains Mono, monospace' }}>{label}</div>
      <div style={{ fontSize: 32, fontWeight: 600, color: accent, fontFamily: 'JetBrains Mono, monospace', lineHeight: 1.1 }}>{value ?? '—'}</div>
      {sub && <div style={{ fontSize: 12, color: '#3a4055' }}>{sub}</div>}
    </div>
  )
}
