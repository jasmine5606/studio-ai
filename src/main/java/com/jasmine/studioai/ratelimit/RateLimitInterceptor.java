package com.jasmine.studioai.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final long requestsPerMinute;
    private final long burstCapacity;

    public RateLimitInterceptor(
            @Value("${rate-limit.enabled:true}") boolean enabled,
            @Value("${rate-limit.requests-per-minute:60}") long requestsPerMinute,
            @Value("${rate-limit.burst-capacity:100}") long burstCapacity) {
        this.enabled = enabled;
        this.requestsPerMinute = requestsPerMinute;
        this.burstCapacity = burstCapacity;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!enabled) return true;

        String clientId = getClientId(request);
        Bucket bucket = buckets.computeIfAbsent(clientId, this::createBucket);

        if (bucket.tryConsume(1)) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("X-RateLimit-RetryAfter", "60");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again later.\"}");
        return false;
    }

    private Bucket createBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(burstCapacity)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) return userId;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
