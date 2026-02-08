package com.jasmine.studioai.error;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of(400, safe(e.getMessage(), "参数错误")));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> illegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of(400, safe(e.getMessage(), "请求无法处理")));
    }

    @ExceptionHandler({MultipartException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ApiError> multipart(MultipartException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiError.of(413, "文件过大或上传失败，请检查上传大小限制"));
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ApiError> redisDown(RedisConnectionFailureException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(503, "Redis 连接失败：请确认 Redis 已启动，并检查 REDIS_HOST/REDIS_PORT/REDIS_PASSWORD"));
    }

    @ExceptionHandler(RedisSystemException.class)
    public ResponseEntity<ApiError> redisSystem(RedisSystemException e) {
        Throwable cause = e.getCause();
        String detail = cause != null && cause.getMessage() != null ? cause.getMessage() : e.getMessage();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(503, "Redis 不可用：" + safe(detail, "请检查 Redis 服务与网络")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> notFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(404, "Not Found"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unknown(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(500, "Internal Server Error"));
    }

    private static String safe(String s, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        return s;
    }

    @Data
    public static class ApiError {
        private int status;
        private String message;

        public static ApiError of(int status, String message) {
            ApiError err = new ApiError();
            err.setStatus(status);
            err.setMessage(message);
            return err;
        }
    }
}
