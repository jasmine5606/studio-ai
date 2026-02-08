package com.jasmine.studioai.codereview;

import com.jasmine.studioai.codereview.dto.ReviewFeedbackRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CodeReviewFeedbackService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final Duration TTL = Duration.ofDays(30);

    public void record(ReviewFeedbackRequest request) {
        String profile = normalizeProfile(request.getProfile());
        String base = "codereview:profile:" + profile;
        stringRedisTemplate.opsForValue().increment(base + ":total", 1);
        if (request.isFalsePositive()) {
            stringRedisTemplate.opsForValue().increment(base + ":falsePositive", 1);
        }
        if (request.getReviewId() != null && !request.getReviewId().isBlank()) {
            String reviewKey = "codereview:review:" + request.getReviewId();
            stringRedisTemplate.opsForHash().put(reviewKey, "profile", profile);
            stringRedisTemplate.opsForHash().put(reviewKey, "falsePositive", String.valueOf(request.isFalsePositive()));
            if (request.getComment() != null) {
                stringRedisTemplate.opsForHash().put(reviewKey, "comment", request.getComment());
            }
            stringRedisTemplate.expire(reviewKey, TTL);
        }
        stringRedisTemplate.expire(base + ":total", TTL);
        stringRedisTemplate.expire(base + ":falsePositive", TTL);
    }

    public ProfileStats getStats(String profile) {
        String p = normalizeProfile(profile);
        String base = "codereview:profile:" + p;
        long total = parseLong(stringRedisTemplate.opsForValue().get(base + ":total"));
        long falsePositive = parseLong(stringRedisTemplate.opsForValue().get(base + ":falsePositive"));

        ProfileStats stats = new ProfileStats();
        stats.setProfile(p);
        stats.setTotal(total);
        stats.setFalsePositive(falsePositive);
        stats.setFalsePositiveRate(total == 0 ? 0.0 : (falsePositive * 1.0 / total));
        return stats;
    }

    private static long parseLong(String s) {
        if (s == null || s.isBlank()) return 0L;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String normalizeProfile(String p) {
        if (p == null || p.isBlank()) return "default";
        return p.trim().toLowerCase();
    }

    @Data
    public static class ProfileStats {
        private String profile;
        private long total;
        private long falsePositive;
        private double falsePositiveRate;
    }
}

