import { useEffect, useState, useRef } from 'react'
import { jobService, trackingService } from '../services/index.js'
import JobCard from '../components/JobCard.jsx'

export default function JobMatchesPage({ flashJobId }) {
  const [data, setData]         = useState(null)
  const [page, setPage]         = useState(0)
  const [tracking, setTracking] = useState({})
  const [loading, setLoading]   = useState(true)
  const prevFlash = useRef(null)

  const load = (p = 0) => {
    setLoading(true)
    jobService.matches(1, p, 20)
      .then(d => { setData(d); setPage(p) })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  // Re-fetch when a MATCH_SCORE event arrives for a job we're showing
  useEffect(() => {
    if (flashJobId && flashJobId !== prevFlash.current) {
      prevFlash.current = flashJobId
      load(page)
    }
  }, [flashJobId])

  // Load tracking status for listed jobs
  useEffect(() => {
    if (!data?.content?.length) return
    trackingService.list(1).then(rows => {
      const map = {}
      rows.forEach(r => { map[r.jobId] = r.status })
      setTracking(map)
    })
  }, [data])

  const handleTrack = async (jobId, resumeId) => {
    await trackingService.start(jobId, resumeId)
    setTracking(t => ({ ...t, [jobId]: 'BOOKMARKED' }))
  }

  const jobs = data?.content ?? []

  return (
    <div className="fade-in" style={{ padding: '36px 40px', maxWidth: 900 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 24 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 600, margin: 0 }}>Job matches</h1>
          <p style={{ fontSize: 13, color: '#6b7280', marginTop: 4 }}>
            Sorted by overall match score · {data?.totalElements ?? '…'} total
          </p>
        </div>
        <button onClick={() => load(page)} style={{
          background: '#1e2330', border: '1px solid #1e2330', borderRadius: 8,
          color: '#6b7280', fontSize: 12, padding: '8px 16px', cursor: 'pointer',
        }}>Refresh</button>
      </div>

      {loading && <div style={{ color: '#3a4055', fontSize: 13, fontFamily: 'JetBrains Mono, monospace' }}>loading…</div>}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {jobs.map(job => (
          <JobCard
            key={`${job.jobId}-${job.resumeId}`}
            job={job}
            onTrack={handleTrack}
            trackingStatus={tracking[job.jobId]}
            flash={flashJobId === job.jobId}
          />
        ))}
      </div>

      {!loading && jobs.length === 0 && (
        <div style={{ color: '#3a4055', fontSize: 14, marginTop: 40, textAlign: 'center' }}>
          No matches yet. Upload a resume — the scheduler will poll and score on the next tick.
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div style={{ display: 'flex', gap: 8, marginTop: 24, justifyContent: 'center' }}>
          {Array.from({ length: data.totalPages }, (_, i) => (
            <button key={i} onClick={() => load(i)} style={{
              background: i === page ? '#6366f1' : '#1e2330',
              border: 'none', borderRadius: 6, color: i === page ? '#fff' : '#6b7280',
              width: 32, height: 32, cursor: 'pointer', fontSize: 13,
            }}>{i + 1}</button>
          ))}
        </div>
      )}
    </div>
  )
}
