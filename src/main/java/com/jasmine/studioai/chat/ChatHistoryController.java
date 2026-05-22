package com.jasmine.studioai.chat;

import com.jasmine.studioai.auth.UserContext;
import com.jasmine.studioai.service.SessionMemoryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final SessionMemoryService sessionMemoryService;

    @GetMapping("/sessions")
    public List<SessionInfo> sessions(@RequestParam(defaultValue = "20") int limit) {
        String userId = UserContext.username();
        List<String> ids = sessionMemoryService.listSessions(userId, limit);
        return ids.stream().map(id -> {
            SessionInfo info = new SessionInfo();
            info.setSessionId(id);
            info.setTitle(sessionMemoryService.getTitle(userId, id));

            long ts = sessionMemoryService.getLastActive(userId, id);
            info.setLastActive(ts);
            info.setLastActiveIso(ts <= 0 ? "" : Instant.ofEpochMilli(ts).toString());

            info.setSummary(sessionMemoryService.getSummary(userId, id));
            return info;
        }).toList();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<SessionMemoryService.Message> messages(@PathVariable String sessionId,
                                                      @RequestParam(defaultValue = "50") int limit) {
        String userId = UserContext.username();
        int n = Math.max(1, Math.min(200, limit));
        return sessionMemoryService.getRecentMessages(userId, sessionId, n);
    }

    @PutMapping("/sessions/{sessionId}/title")
    public void rename(@PathVariable String sessionId, @RequestBody RenameRequest request) {
        String userId = UserContext.username();
        String title = request == null ? "" : request.getTitle();
        sessionMemoryService.setTitle(userId, sessionId, title);
    }

    @Data
    public static class SessionInfo {
        private String sessionId;
        private String title;
        private long lastActive;
        private String lastActiveIso;
        private String summary;
    }

    @Data
    public static class RenameRequest {
        private String title;
    }
}
