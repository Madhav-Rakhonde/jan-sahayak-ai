package com.JanSahayak.AI.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

/**
 * Enhanced Feed System Configuration
 *
 * This configuration sets up the enhanced feed system with proper caching,
 * async processing, and scheduled tasks for optimal performance.
 */
@Configuration
@EnableCaching
@EnableAsync
@EnableScheduling
public class EnhancedFeedConfiguration {

    /**
     * Cache manager for feed caching
     */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "user-feeds",
                "department-tags",
                "location-posts",
                "feed-stats",
                "user-preferences"
        );
    }
}
