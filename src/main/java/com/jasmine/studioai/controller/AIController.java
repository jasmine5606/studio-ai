package com.jasmine.studioai.controller;

import com.jasmine.studioai.audit.AuditService;
import com.jasmine.studioai.auth.UserContext;
import com.jasmine.studioai.rag.AdvancedRagService;
import com.jasmine.studioai.rag.SemanticCacheService;
import com.jasmine.studioai.service.AiAnswerService;
import com.jasmine.studioai.service.ConversationOrchestrator;
import com.jasmine.studioai.service.SessionMemoryService;
import com.jasmine.studioai.service.StudioAIAssistant;
import com.jasmine.studioai.streaming.StreamingChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "AI 对话", description = "AI 问答、聊天与检索接口")
public class AIController {

    private final StudioAIAssistant aiAssistant;
    private final AiAnswerService aiAnswerService;
    private final SessionMemoryService memoryService;
    private final ConversationOrchestrator conversationOrchestrator;
    private final StreamingChatService streamingChatService;
    private final AdvancedRagService advancedRagService;
    private final SemanticCacheService semanticCacheService;
    private final AuditService auditService;

    @Operation(summary = "简单问答（无会话）")
    @PostMapping("/ask")
    public String ask(@RequestBody QuestionRequest request) {
        auditService.log(UserContext.userId(), "AI_ASK", "question", request.getQuestion());
        return aiAssistant.answer(request.getQuestion());
    }

    @Operation(summary = "对话（带会话记忆与 RAG）")
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        String userId = UserContext.userId();

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        String answer = aiAnswerService.answer(userId, sessionId, request.getQuestion(), request.getMode());

        memoryService.addMessage(userId, sessionId, "user", request.getQuestion());
        memoryService.addMessage(userId, sessionId, "assistant", answer);
        conversationOrchestrator.summarizeIfNeeded(userId, sessionId);

        auditService.log(userId, "AI_CHAT", "session/" + sessionId, request.getQuestion());

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("answer", answer);
        return result;
    }

    @Operation(summary = "流式对话（SSE）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        String userId = UserContext.userId();

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        auditService.log(userId, "AI_CHAT_STREAM", "session/" + sessionId, request.getQuestion());
        return streamingChatService.streamChat(userId, sessionId, request.getQuestion(), request.getMode());
    }

    @Operation(summary = "HyDE 增强检索")
    @PostMapping("/retrieve/hyde")
    public List<HydeResult> hydeRetrieve(@RequestBody RetrieveRequest request) {
        var results = advancedRagService.hydeRetrieve(request.getQuestion(), request.getMaxResults());
        return results.stream()
                .map(r -> new HydeResult(r.score(), r.text()))
                .toList();
    }

    @Operation(summary = "Re-rank 重排序检索")
    @PostMapping("/retrieve/rerank")
    public List<HydeResult> rerank(@RequestBody RerankRequest request) {
        var candidates = request.getTexts().stream()
                .map(t -> new com.jasmine.studioai.retriever.HybridContentRetriever.ScoredText(0.5, t))
                .toList();
        var results = advancedRagService.rerank(request.getQuestion(), candidates, request.getTopK());
        return results.stream()
                .map(r -> new HydeResult(r.score(), r.text()))
                .toList();
    }

    @Operation(summary = "语义缓存查找")
    @PostMapping("/cache/lookup")
    public Map<String, Object> cacheLookup(@RequestBody QuestionRequest request) {
        var cached = semanticCacheService.findSimilar(request.getQuestion());
        Map<String, Object> result = new HashMap<>();
        result.put("hit", cached != null);
        if (cached != null) {
            result.put("similarQuestion", cached.question());
            result.put("answer", cached.answer());
        }
        return result;
    }

    public record HydeResult(double score, String text) {}

    @Data
    public static class QuestionRequest {
        private String question;
    }

    @Data
    public static class ChatRequest {
        private String sessionId;
        private String question;
        private String mode;
    }

    @Data
    public static class RetrieveRequest {
        private String question;
        private int maxResults = 5;
    }

    @Data
    public static class RerankRequest {
        private String question;
        private List<String> texts;
        private int topK = 3;
    }
}
