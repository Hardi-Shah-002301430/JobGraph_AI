import { useEffect, useState, useRef } from 'react'
import { resumeService } from '../services/index.js'
import { formatDate } from '../utils/index.js'

export default function ResumePage() {
  const [resumes, setResumes] = useState([])
  const [selected, setSelected] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState(null)
  const [mode, setMode] = useState('file') // 'file' | 'text'
  const [rawText, setRawText] = useState('')
  const fileRef = useRef()

  const load = () => resumeService.list(1).then(r => {
    setResumes(r)
    if (r.length && !selected) setSelected(r[0])
  })
  useEffect(() => { load() }, [])

  const uploadFile = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true); setError(null)
    try {
      const r = await resumeService.uploadFile(file)
      await load()
      setSelected(r)
    } catch (ex) { setError(ex.message) }
    finally { setUploading(false) }
  }

  const uploadText = async () => {
    if (!rawText.trim()) return
    setUploading(true); setError(null)
    try {
      const r = await resumeService.uploadText(rawText)
      await load()
      setSelected(r)
      setRawText('')
    } catch (ex) { setError(ex.message) }
    finally { setUploading(false) }
  }

  return (
    <div className="fade-in" style={{ padding: '36px 40px', maxWidth: 1000, display: 'flex', gap: 32 }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <h1 style={{ fontSize: 22, fontWeight: 600, margin: '0 0 6px' }}>Resume</h1>
        <p style={{ fontSize: 13, color: '#6b7280', marginTop: 0, marginBottom: 24 }}>
          Upload your resume — the agent extracts skills and roles that drive polling.
        </p>

        <div style={{ background: '#141720', border: '1px solid #1e2330', borderRadius: 12, padding: 24, marginBottom: 20 }}>
          <div style={{ display: 'flex', gap: 8, marginBottom: 18 }}>
            {['file', 'text'].map(m => (
              <button key={m} onClick={() => setMode(m)} style={{
                background: mode === m ? '#1e2330' : 'transparent',
                border: `1px solid ${mode === m ? '#3a4055' : '#1e2330'}`,
                borderRadius: 6, color: mode === m ? '#e2e8f0' : '#6b7280',
                padding: '6px 14px', cursor: 'pointer', fontSize: 13,
              }}>{m === 'file' ? 'Upload PDF / TXT' : 'Paste text'}</button>
            ))}
          </div>

          {mode === 'file' ? (
            <div onClick={() => fileRef.current?.click()} style={{
              border: '2px dashed #1e2330', borderRadius: 10, padding: '32px 20px',
              textAlign: 'center', cursor: 'pointer', color: '#3a4055',
              transition: 'border-color 0.2s',
            }}
            onMouseOver={e => e.currentTarget.style.borderColor = '#6366f1'}
            onMouseOut={e  => e.currentTarget.style.borderColor = '#1e2330'}
            >
              <div style={{ fontSize: 28, marginBottom: 8 }}>⬆</div>
              <div style={{ fontSize: 13 }}>Click to select PDF or TXT</div>
              <input ref={fileRef} type="file" accept=".pdf,.txt" style={{ display: 'none' }} onChange={uploadFile} />
            </div>
          ) : (
            <div>
              <textarea value={rawText} onChange={e => setRawText(e.target.value)}
                rows={10} placeholder="Paste resume text here…"
                style={{
                  width: '100%', background: '#0d0f14', border: '1px solid #1e2330',
                  borderRadius: 8, color: '#e2e8f0', fontSize: 13, padding: 12,
                  fontFamily: 'JetBrains Mono, monospace', resize: 'vertical',
                }} />
              <button onClick={uploadText} disabled={uploading} style={{
                marginTop: 10, background: '#6366f1', border: 'none', borderRadius: 8,
                color: '#fff', padding: '10px 20px', cursor: 'pointer', fontWeight: 500,
                opacity: uploading ? 0.6 : 1,
              }}>
                {uploading ? 'Analyzing…' : 'Analyze resume'}
              </button>
            </div>
          )}

          {uploading && mode === 'file' && (
            <div style={{ marginTop: 14, fontSize: 13, color: '#6366f1', fontFamily: 'JetBrains Mono, monospace' }}>
              Analyzing with LLM…
            </div>
          )}
          {error && <div style={{ marginTop: 12, fontSize: 13, color: '#f43f5e' }}>{error}</div>}
        </div>

        {resumes.length > 1 && (
          <div style={{ background: '#141720', border: '1px solid #1e2330', borderRadius: 12, padding: '16px 20px' }}>
            <div style={{ fontSize: 11, color: '#6b7280', textTransform: 'uppercase', letterSpacing: 1, fontFamily: 'JetBrains Mono, monospace', marginBottom: 10 }}>All resumes</div>
            {resumes.map(r => (
              <div key={r.id} onClick={() => setSelected(r)} style={{
                padding: '8px 0', borderBottom: '1px solid #1e2330', cursor: 'pointer',
                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                color: selected?.id === r.id ? '#e2e8f0' : '#6b7280', fontSize: 13,
              }}>
                <span>{r.fullName ?? 'Resume'} <span style={{ color: '#3a4055', fontSize: 11 }}>#{r.id}</span></span>
                <span style={{ fontSize: 11, color: '#3a4055' }}>{formatDate(r.createdAt)}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      {selected && <ResumeDetail resume={selected} />}
    </div>
  )
}

function ResumeDetail({ resume }) {
  return (
    <div style={{ width: 320, flexShrink: 0 }}>
      <div style={{ background: '#141720', border: '1px solid #1e2330', borderRadius: 12, padding: 24 }}>
        <div style={{ fontSize: 11, color: '#6b7280', textTransform: 'uppercase', letterSpacing: 1, fontFamily: 'JetBrains Mono, monospace', marginBottom: 14 }}>Extracted profile</div>
        <div style={{ fontSize: 18, fontWeight: 600, marginBottom: 4 }}>{resume.fullName ?? '—'}</div>
        <div style={{ fontSize: 13, color: '#6b7280', marginBottom: 4 }}>{resume.email}</div>
        {resume.summary && <div style={{ fontSize: 12, color: '#6b7280', marginTop: 10, lineHeight: 1.6 }}>{resume.summary}</div>}

        <Row label="Experience" value={resume.experienceYears != null ? `${resume.experienceYears} yrs` : '—'} />
        <Row label="Education"  value={resume.educationLevel ?? '—'} />

        {resume.preferredRoles?.length > 0 && (
          <div style={{ marginTop: 16 }}>
            <div style={{ fontSize: 11, color: '#3a4055', textTransform: 'uppercase', letterSpacing: 1, fontFamily: 'JetBrains Mono, monospace', marginBottom: 8 }}>Roles to poll</div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {resume.preferredRoles.map(r => (
                <span key={r} style={{ fontSize: 11, padding: '3px 8px', borderRadius: 4, background: 'rgba(99,102,241,0.12)', color: '#6366f1', fontFamily: 'JetBrains Mono, monospace' }}>{r}</span>
              ))}
            </div>
          </div>
        )}

        {resume.skills?.length > 0 && (
          <div style={{ marginTop: 16 }}>
            <div style={{ fontSize: 11, color: '#3a4055', textTransform: 'uppercase', letterSpacing: 1, fontFamily: 'JetBrains Mono, monospace', marginBottom: 8 }}>Skills</div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5 }}>
              {resume.skills.map(s => (
                <span key={s} style={{ fontSize: 11, padding: '3px 8px', borderRadius: 4, background: '#1e2330', color: '#6b7280' }}>{s}</span>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function Row({ label, value }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid #1e2330', fontSize: 13 }}>
      <span style={{ color: '#6b7280' }}>{label}</span>
      <span style={{ color: '#e2e8f0', fontFamily: 'JetBrains Mono, monospace' }}>{value}</span>
    </div>
  )
}
