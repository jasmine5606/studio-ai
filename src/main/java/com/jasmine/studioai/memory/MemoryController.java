package com.jasmine.studioai.memory;

import com.jasmine.studioai.auth.UserContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/memory")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class MemoryController {

    private final LongTermMemoryService longTermMemoryService;

    @PostMapping("/pin")
    public void pin(@RequestBody PinRequest request) {
        longTermMemoryService.pin(UserContext.username(), request.getSessionId(), request.getText());
    }

    @GetMapping("/pins")
    public List<String> pins(@RequestParam(required = false) String sessionId,
                             @RequestParam(defaultValue = "20") int limit) {
        return longTermMemoryService.listPins(UserContext.username(), sessionId, limit);
    }

    @PostMapping("/recall")
    public List<LongTermMemoryService.MemoryHit> recall(@RequestBody RecallRequest request) {
        return longTermMemoryService.recall(UserContext.username(), request.getQuery(), request.getTopK());
    }

    @Data
    public static class PinRequest {
        private String sessionId;
        private String text;
    }

    @Data
    public static class RecallRequest {
        private String query;
        private int topK = 5;
    }
}
