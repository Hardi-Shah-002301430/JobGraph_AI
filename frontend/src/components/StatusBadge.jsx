import { STATUS_META } from '../utils/index.js'

export default function StatusBadge({ status }) {
  const meta = STATUS_META[status] ?? { label: status, color: '#6b7280', bg: 'rgba(107,114,128,0.15)' }
  return (
    <span style={{
      fontSize: 11, fontWeight: 500, padding: '3px 8px', borderRadius: 4,
      color: meta.color, background: meta.bg,
      fontFamily: 'JetBrains Mono, monospace', letterSpacing: 0.3,
      textTransform: 'uppercase', whiteSpace: 'nowrap',
    }}>
      {meta.label}
    </span>
  )
}
