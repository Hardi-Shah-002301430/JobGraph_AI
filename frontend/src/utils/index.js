export function scoreColor(score) {
  if (score >= 75) return '#22c55e'
  if (score >= 55) return '#f59e0b'
  return '#f43f5e'
}

export function scoreBg(score) {
  if (score >= 75) return 'rgba(34,197,94,0.12)'
  if (score >= 55) return 'rgba(245,158,11,0.12)'
  return 'rgba(244,63,94,0.12)'
}

export function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

export function formatRelative(iso) {
  if (!iso) return '—'
  const diff = Date.now() - new Date(iso).getTime()
  const h = Math.floor(diff / 3600000)
  if (h < 1) return 'just now'
  if (h < 24) return `${h}h ago`
  const d = Math.floor(h / 24)
  if (d < 7) return `${d}d ago`
  return formatDate(iso)
}

export const STATUS_META = {
  BOOKMARKED:   { label: 'Bookmarked',   color: '#6b7280', bg: 'rgba(107,114,128,0.15)' },
  APPLIED:      { label: 'Applied',      color: '#6366f1', bg: 'rgba(99,102,241,0.15)'  },
  PHONE_SCREEN: { label: 'Phone screen', color: '#14b8a6', bg: 'rgba(20,184,166,0.15)'  },
  INTERVIEW:    { label: 'Interview',    color: '#f59e0b', bg: 'rgba(245,158,11,0.15)'  },
  OFFER:        { label: 'Offer',        color: '#22c55e', bg: 'rgba(34,197,94,0.15)'   },
  ACCEPTED:     { label: 'Accepted',     color: '#22c55e', bg: 'rgba(34,197,94,0.2)'    },
  REJECTED:     { label: 'Rejected',     color: '#f43f5e', bg: 'rgba(244,63,94,0.15)'   },
  WITHDRAWN:    { label: 'Withdrawn',    color: '#6b7280', bg: 'rgba(107,114,128,0.12)' },
}
