package com.jobgraph.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class JobUpdateHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    /** All connected sessions keyed by session id. */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WS connected: {} (total={})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WS disconnected: {} (total={})", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Clients can send ping/subscribe messages; for now we just log
        log.debug("WS message from {}: {}", session.getId(), message.getPayload());
    }

    // ── Public broadcast API used by agents / controllers ──

    /** Broadcast a new-job event to all connected clients. */
    public void broadcastNewJob(Long jobId, String title, String company) {
        broadcast(Map.of(
                "type", "NEW_JOB",
                "jobId", jobId,
                "title", title,
                "company", company));
    }

    /** Broadcast a match-score event to all connected clients. */
    public void broadcastMatchScore(Long jobId, Long resumeId, double score) {
        broadcast(Map.of(
                "type", "MATCH_SCORE",
                "jobId", jobId,
                "resumeId", resumeId,
                "score", score));
    }

    /** Broadcast a status-change event. */
    public void broadcastStatusChange(Long trackingId, String newStatus) {
        broadcast(Map.of(
                "type", "STATUS_CHANGE",
                "trackingId", trackingId,
                "status", newStatus));
    }

    private void broadcast(Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            TextMessage msg = new TextMessage(json);
            for (WebSocketSession session : sessions.values()) {
                if (session.isOpen()) {
                    session.sendMessage(msg);
                }
            }
        } catch (IOException e) {
            log.error("Failed to broadcast WS message", e);
        }
    }
}
