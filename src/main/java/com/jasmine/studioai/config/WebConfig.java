package com.jasmine.studioai.config;

import com.jasmine.studioai.ratelimit.RateLimitInterceptor;
import com.jasmine.studioai.websocket.CollaborationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer, WebSocketConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final CollaborationWebSocketHandler collaborationWebSocketHandler;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/health");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(collaborationWebSocketHandler, "/ws/collaboration")
                .setAllowedOrigins("*");
    }
}
