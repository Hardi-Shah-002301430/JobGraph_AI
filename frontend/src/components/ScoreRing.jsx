import { scoreColor } from '../utils/index.js'

export default function ScoreRing({ score, size = 56, stroke = 4 }) {
  const s = Math.round(score ?? 0)
  const r = (size - stroke * 2) / 2
  const circ = 2 * Math.PI * r
  const dash = (s / 100) * circ
  const color = scoreColor(s)
  const cx = size / 2

  return (
    <div style={{ position: 'relative', width: size, height: size, flexShrink: 0 }}>
      <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
        <circle cx={cx} cy={cx} r={r} fill="none" stroke="#1e2330" strokeWidth={stroke} />
        <circle cx={cx} cy={cx} r={r} fill="none" stroke={color} strokeWidth={stroke}
          strokeDasharray={`${dash} ${circ}`} strokeLinecap="round"
          style={{ transition: 'stroke-dasharray 0.5s ease' }} />
      </svg>
      <span style={{
        position: 'absolute', inset: 0, display: 'flex', alignItems: 'center',
        justifyContent: 'center', fontSize: size * 0.24, fontWeight: 600,
        color, fontFamily: 'JetBrains Mono, monospace',
      }}>{s}</span>
    </div>
  )
}
