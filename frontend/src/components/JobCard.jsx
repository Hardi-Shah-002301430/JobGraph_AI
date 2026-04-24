import { useState } from 'react'
import ScoreRing from './ScoreRing.jsx'
import StatusBadge from './StatusBadge.jsx'
import { scoreColor, scoreBg, formatRelative } from '../utils/index.js'

const CATS = [
  { key: 'skillScore',      label: 'Skills',      detailKey: 'skillDetail' },
  { key: 'experienceScore', label: 'Experience',   detailKey: 'experienceDetail' },
  { key: 'educationScore',  label: 'Education',    detailKey: 'educationDetail' },
  { key: 'industryScore',   label: 'Industry',     detailKey: 'industryDetail' },
  { key: 'locationScore',   label: 'Location',     detailKey: 'locationDetail' },
]

function parseAdvisor(raw) {
  if (!raw) return null
  try {
    const p = JSON.parse(raw)
    if (!p.tips?.length && !p.missingSkills?.length && !p.rewrittenSummary) return null
    return p
  } catch { return null }
}

export default function JobCard({ job, onTrack, trackingStatus, flash }) {
  const [expanded, setExpanded]     = useState(false)
  const [activeTab, setActiveTab]   = useState('breakdown') // 'breakdown' | 'advisor'

  const advisor = parseAdvisor(job.resumeTips)

  return (
    <div className="fade-in" style={{
      background: flash ? 'rgba(99,102,241,0.06)' : '#141720',
      border: `1px solid ${flash ? '#6366f1' : '#1e2330'}`,
      borderRadius: 12, padding: '18px 20px',
      transition: 'all 0.4s ease',
    }}>
      {/* ── Header row ── */}
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16 }}>
        <ScoreRing score={job.overallScore} size={52} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 15, fontWeight: 500, color: '#e2e8f0' }}>{job.jobTitle}</span>
            {trackingStatus && <StatusBadge status={trackingStatus} />}
            {advisor && (
              <span style={{
                fontSize: 10, padding: '2px 7px', borderRadius: 4,
                background: 'rgba(245,158,11,0.12)', color: '#f59e0b',
                fontFamily: 'JetBrains Mono, monospace', letterSpacing: 0.3,
              }}>ADVISOR READY</span>
            )}
          </div>
          <div style={{ fontSize: 13, color: '#6b7280', marginTop: 2 }}>
            {job.companyName}{job.location ? ` · ${job.location}` : ''}
            {job.employmentType ? ` · ${job.employmentType}` : ''}
          </div>
          {job.resumeLabel && (
            <div style={{ fontSize: 11, color: '#3a4055', marginTop: 3, fontFamily: 'JetBrains Mono, monospace' }}>
              via {job.resumeLabel}
            </div>
          )}
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexShrink: 0 }}>
          <span style={{ fontSize: 11, color: '#3a4055' }}>{formatRelative(job.postedAt)}</span>
          <button onClick={() => setExpanded(e => !e)} style={{
            background: '#1e2330', border: 'none', borderRadius: 6,
            color: '#6b7280', fontSize: 13, padding: '4px 10px', cursor: 'pointer',
          }}>{expanded ? '▲' : '▼'}</button>
          {onTrack && !trackingStatus && (
            <button onClick={() => onTrack(job.jobId, job.resumeId)} style={{
              background: 'rgba(99,102,241,0.15)', border: '1px solid rgba(99,102,241,0.3)',
              borderRadius: 6, color: '#6366f1', fontSize: 12,
              padding: '4px 12px', cursor: 'pointer', fontWeight: 500,
            }}>Track</button>
          )}
        </div>
      </div>

      {/* ── Expanded panel ── */}
      {expanded && (
        <div style={{ marginTop: 16, borderTop: '1px solid #1e2330', paddingTop: 16 }}>

          {/* Tab bar — only show Advisor tab if data exists */}
          <div style={{ display: 'flex', gap: 4, marginBottom: 16 }}>
            {[
              { id: 'breakdown', label: 'Score breakdown' },
              ...(advisor ? [{ id: 'advisor', label: 'Advisor suggestions' }] : []),
            ].map(tab => (
              <button key={tab.id} onClick={() => setActiveTab(tab.id)} style={{
                fontSize: 11, padding: '5px 12px', borderRadius: 6, cursor: 'pointer',
                fontFamily: 'JetBrains Mono, monospace', letterSpacing: 0.3,
                border: 'none',
                background: activeTab === tab.id ? '#1e2330' : 'transparent',
                color: activeTab === tab.id ? '#e2e8f0' : '#3a4055',
                transition: 'all 0.15s',
              }}>{tab.label}</button>
            ))}
          </div>

          {/* ── Tab: Score breakdown ── */}
          {activeTab === 'breakdown' && (
            <div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14 }}>
                {CATS.map(c => (
                  <div key={c.key} title={job[c.detailKey] ?? ''} style={{
                    fontSize: 11, padding: '4px 10px', borderRadius: 6,
                    color: scoreColor(job[c.key]), background: scoreBg(job[c.key]),
                    fontFamily: 'JetBrains Mono, monospace', cursor: 'default',
                  }}>
                    {c.label} {Math.round(job[c.key] ?? 0)}
                  </div>
                ))}
              </div>
              {/* Category details */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {CATS.filter(c => job[c.detailKey]).map(c => (
                  <div key={c.key} style={{
                    background: '#0d0f14', borderRadius: 8, padding: '10px 14px',
                    borderLeft: `2px solid ${scoreColor(job[c.key])}`,
                  }}>
                    <div style={{ fontSize: 10, color: scoreColor(job[c.key]), fontFamily: 'JetBrains Mono, monospace', marginBottom: 4, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                      {c.label} · {Math.round(job[c.key] ?? 0)}
                    </div>
                    <div style={{ fontSize: 12, color: '#6b7280', lineHeight: 1.6 }}>{job[c.detailKey]}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ── Tab: Advisor suggestions ── */}
          {activeTab === 'advisor' && advisor && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>

              {/* Tips */}
              {advisor.tips?.length > 0 && (
                <div>
                  <div style={{ fontSize: 11, color: '#f59e0b', fontFamily: 'JetBrains Mono, monospace', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 10 }}>
                    What to change ({advisor.tips.length} suggestions)
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {advisor.tips.map((tip, i) => (
                      <div key={i} style={{
                        display: 'flex', gap: 12, alignItems: 'flex-start',
                        background: '#0d0f14', borderRadius: 8, padding: '10px 14px',
                      }}>
                        <span style={{
                          fontSize: 10, fontFamily: 'JetBrains Mono, monospace',
                          color: '#f59e0b', background: 'rgba(245,158,11,0.12)',
                          borderRadius: 4, padding: '2px 6px', flexShrink: 0, marginTop: 1,
                        }}>{String(i + 1).padStart(2, '0')}</span>
                        <span style={{ fontSize: 13, color: '#c4cad6', lineHeight: 1.65 }}>{tip}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Missing skills */}
              {advisor.missingSkills?.length > 0 && (
                <div>
                  <div style={{ fontSize: 11, color: '#f43f5e', fontFamily: 'JetBrains Mono, monospace', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 8 }}>
                    Missing skills
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                    {advisor.missingSkills.map(s => (
                      <span key={s} style={{
                        fontSize: 12, padding: '4px 10px', borderRadius: 6,
                        background: 'rgba(244,63,94,0.1)', color: '#f43f5e',
                        border: '1px solid rgba(244,63,94,0.2)',
                      }}>{s}</span>
                    ))}
                  </div>
                </div>
              )}

              {/* Rewritten summary */}
              {advisor.rewrittenSummary && (
                <div>
                  <div style={{ fontSize: 11, color: '#14b8a6', fontFamily: 'JetBrains Mono, monospace', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 8 }}>
                    Suggested summary rewrite
                  </div>
                  <div style={{
                    background: '#0d0f14', borderRadius: 8, padding: '14px 16px',
                    borderLeft: '2px solid #14b8a6',
                    fontSize: 13, color: '#c4cad6', lineHeight: 1.75, fontStyle: 'italic',
                  }}>
                    "{advisor.rewrittenSummary}"
                  </div>
                </div>
              )}

            </div>
          )}

        </div>
      )}
    </div>
  )
}