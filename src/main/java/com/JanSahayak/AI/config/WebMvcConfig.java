package com.JanSahayak.AI.config;

import com.JanSahayak.AI.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                // Apply to every /api/** route ...
                .addPathPatterns("/api/**")
                // ... except monitoring/ops paths that must never be throttled
                .excludePathPatterns(
                        "/api/*/health",    // e.g. /api/notifications/health
                        "/actuator/**"      // Spring Boot Actuator
                );
    }
}
