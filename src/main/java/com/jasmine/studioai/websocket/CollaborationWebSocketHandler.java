package com.jasmine.studioai.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class CollaborationWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        if (payload.startsWith("JOIN:")) {
            String roomId = payload.substring(5).trim();
            rooms.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(session);
            sessionRooms.put(session.getId(), roomId);
            broadcast(roomId, "System", "User " + session.getId() + " joined room " + roomId);
            log.info("User {} joined room {}", session.getId(), roomId);
        } else if (payload.startsWith("MSG:")) {
            String content = payload.substring(4);
            String roomId = sessionRooms.get(session.getId());
            if (roomId != null) {
                broadcast(roomId, session.getId(), content);
            }
        } else if (payload.startsWith("LEAVE:")) {
            String roomId = payload.substring(6).trim();
            leaveRoom(session, roomId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = sessionRooms.remove(session.getId());
        if (roomId != null) {
            Set<WebSocketSession> roomSessions = rooms.get(roomId);
            if (roomSessions != null) {
                roomSessions.remove(session);
                if (roomSessions.isEmpty()) {
                    rooms.remove(roomId);
                }
            }
            broadcast(roomId, "System", "User " + session.getId() + " left room " + roomId);
        }
        log.info("WebSocket disconnected: {}", session.getId());
    }

    private void broadcast(String roomId, String sender, String content) {
        Set<WebSocketSession> roomSessions = rooms.get(roomId);
        if (roomSessions == null) return;

        String message = String.format("{\"room\":\"%s\",\"sender\":\"%s\",\"content\":\"%s\"}",
                roomId, sender, escapeJson(content));

        for (WebSocketSession s : roomSessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(message));
                } catch (Exception e) {
                    log.error("Failed to send WebSocket message: {}", e.getMessage());
                }
            }
        }
    }

    private void leaveRoom(WebSocketSession session, String roomId) {
        sessionRooms.remove(session.getId());
        Set<WebSocketSession> roomSessions = rooms.get(roomId);
        if (roomSessions != null) {
            roomSessions.remove(session);
            if (roomSessions.isEmpty()) rooms.remove(roomId);
        }
        broadcast(roomId, "System", "User left room " + roomId);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
