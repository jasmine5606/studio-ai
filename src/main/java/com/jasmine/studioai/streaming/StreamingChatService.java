package com.jasmine.studioai.streaming;

import com.jasmine.studioai.service.AiAnswerService;
import com.jasmine.studioai.service.ConversationOrchestrator;
import com.jasmine.studioai.service.SessionMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingChatService {

    private final AiAnswerService aiAnswerService;
    private final SessionMemoryService memoryService;
    private final ConversationOrchestrator conversationOrchestrator;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public SseEmitter streamChat(String userId, String sessionId, String question, String mode) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        String finalSessionId = sessionId;
        SseEmitter emitter = new SseEmitter(300000L);

        emitter.onCompletion(() -> log.info("SSE completed for session {}", finalSessionId));
        emitter.onTimeout(() -> log.warn("SSE timeout for session {}", finalSessionId));
        emitter.onError(e -> log.error("SSE error for session {}: {}", finalSessionId, e.getMessage()));

        executor.execute(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("session")
                        .data(Map.of("sessionId", finalSessionId)));

                String answer = aiAnswerService.answerWithSteps(
                        userId, finalSessionId, question, mode,
                        step -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("step")
                                        .data(Map.of(
                                                "step", step.step(),
                                                "status", step.status(),
                                                "detail", step.detail() != null ? step.detail() : ""
                                        )));
                            } catch (IOException ignored) {}
                        }
                );

                emitter.send(SseEmitter.event()
                        .name("answer")
                        .data(answer));

                memoryService.addMessage(userId, finalSessionId, "user", question);
                memoryService.addMessage(userId, finalSessionId, "assistant", answer);
                conversationOrchestrator.summarizeIfNeeded(userId, finalSessionId);

                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(Map.of("sessionId", finalSessionId)));

                emitter.complete();
            } catch (IOException e) {
                log.error("SSE send error: {}", e.getMessage());
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("Chat streaming error: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("message", e.getMessage())));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
