import { useState, useCallback } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { useWebSocket } from './hooks/useWebSocket.js'
import Sidebar from './components/Sidebar.jsx'
import DashboardPage  from './pages/DashboardPage.jsx'
import JobMatchesPage from './pages/JobMatchesPage.jsx'
import ResumePage     from './pages/ResumePage.jsx'
import TrackingPage   from './pages/TrackingPage.jsx'
import CompaniesPage  from './pages/CompaniesPage.jsx'
import ClusterPage    from './pages/ClusterPage.jsx'
import { formatRelative } from './utils/index.js'

export default function App() {
  const [wsStatus,        setWsStatus]        = useState('connecting')
  const [liveEvents,      setLiveEvents]       = useState([])
  const [flashJobId,      setFlashJobId]       = useState(null)
  const [flashTrackingId, setFlashTrackingId]  = useState(null)

  const onEvent = useCallback((evt) => {
    setWsStatus('connected')

    let label = ''
    if (evt.type === 'NEW_JOB') {
      label = `+ ${evt.company} — ${evt.title}`
      setFlashJobId(evt.jobId)
      setTimeout(() => setFlashJobId(null), 3000)
    } else if (evt.type === 'MATCH_SCORE') {
      label = `score ${Math.round(evt.score)} · job #${evt.jobId}`
      setFlashJobId(evt.jobId)
      setTimeout(() => setFlashJobId(null), 3000)
    } else if (evt.type === 'STATUS_CHANGE') {
      label = `tracking #${evt.trackingId} → ${evt.status}`
      setFlashTrackingId(evt.trackingId)
      setTimeout(() => setFlashTrackingId(null), 3000)
    }

    if (label) {
      const ts = new Date().toLocaleTimeString('en-US', { hour12: false })
      setLiveEvents(prev => [`[${ts}] ${label}`, ...prev].slice(0, 50))
    }
  }, [])

  // Track WS disconnect
  const onEventWithStatus = useCallback((evt) => {
    setWsStatus('connected')
    onEvent(evt)
  }, [onEvent])

  useWebSocket(onEventWithStatus)

  return (
    <BrowserRouter>
      <div style={{ display: 'flex', minHeight: '100vh' }}>
        <Sidebar wsStatus={wsStatus} liveEvents={liveEvents} />
        <main style={{ flex: 1, minWidth: 0, overflowY: 'auto' }}>
          <Routes>
            <Route path="/"          element={<DashboardPage  liveEvents={liveEvents} />} />
            <Route path="/matches"   element={<JobMatchesPage flashJobId={flashJobId} />} />
            <Route path="/resume"    element={<ResumePage />} />
            <Route path="/tracking"  element={<TrackingPage   flashTrackingId={flashTrackingId} />} />
            <Route path="/companies" element={<CompaniesPage />} />
            <Route path="/cluster"   element={<ClusterPage />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  )
}
