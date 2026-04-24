import { useEffect, useRef, useCallback } from 'react'

export function useWebSocket(onEvent) {
  const ref = useRef(null)
  const handler = useRef(onEvent)
  handler.current = onEvent

  useEffect(() => {
    const connect = () => {
      const ws = new WebSocket(`ws://${window.location.host}/ws/jobs`)
      ws.onopen    = () => console.log('[WS] connected')
      ws.onmessage = (e) => { try { handler.current(JSON.parse(e.data)) } catch {} }
      ws.onerror   = () => {}
      ws.onclose   = () => setTimeout(connect, 3000)
      ref.current  = ws
    }
    connect()
    return () => ref.current?.close()
  }, [])
}
