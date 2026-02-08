package com.jasmine.studioai.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
@RequiredArgsConstructor
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private final AuthService authService;
    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(authService, authProperties, objectMapper))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
    }
}

