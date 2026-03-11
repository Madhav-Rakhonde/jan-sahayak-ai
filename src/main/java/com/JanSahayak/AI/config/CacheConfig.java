package com.JanSahayak.AI.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * CacheConfig — in-process caching only (no Redis).
 *
 * CHANGES v2:
 * ─────────────────────────────────────────────────────────────────────────────
 * BEFORE (v1):
 *   Used Spring's ConcurrentMapCache which has NO eviction policy.
 *   Problem: profiles accumulated indefinitely in memory. A JVM serving
 *   1 million users would hold 1M+ profile maps in the heap with no
 *   bound, eventually causing OutOfMemoryError.
 *
 * AFTER (v2):
 *   Uses Caffeine (backed by W-TinyLFU eviction) with:
 *     • maximumSize   — hard cap on number of cached entries
 *     • expireAfterWrite — TTL auto-eviction (stale data protection)
 *
 *   Why Caffeine instead of ConcurrentMapCache?
 *   ✓ LRU/LFU eviction — stale entries don't linger forever
 *   ✓ Per-cache size cap — prevents unbounded heap growth
 *   ✓ Optional stats recording (enables cache hit-ratio monitoring)
 *   ✓ Fully in-process — no Redis, no network, no extra dependency beyond
 *     spring-boot-starter-cache + caffeine (already in Spring Boot)
 *
 * Cache inventory:
 *   uip_profile           — user interest profiles
 *                           max 100k entries, TTL 5 min
 *                           (evicted eagerly by @CacheEvict on every interaction
 *                            — the TTL is a safety net, not the primary eviction)
 *
 *   user-distribution-pincode — geo distribution data (relatively stable)
 *                           max 50k entries, TTL 30 min
 *
 * Dependency required (already in Spring Boot starter):
 *   implementation 'com.github.ben-manes.caffeine:caffeine'
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Maximum number of user-interest-profile entries to keep in memory.
     *
     * Sizing guide:
     *   Each profile entry ≈ 20 topics × (16 B key + 8 B double) ≈ 480 B.
     *   100k entries ≈ 48 MB worst-case (well within standard JVM heap).
     *   For > 1M DAU, increase to 500_000 or use Caffeine's softValues().
     */
    private static final int  UIP_MAX_SIZE_ENTRIES = 100_000;

    /** Profile TTL — acts as a safety net if @CacheEvict somehow misses. */
    private static final long UIP_EXPIRE_AFTER_WRITE_MINUTES = 5L;

    private static final int  GEO_DIST_MAX_SIZE_ENTRIES = 50_000;
    private static final long GEO_DIST_EXPIRE_AFTER_WRITE_MINUTES = 30L;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCache profileCache = buildCache(
                Constant.HLIG_CACHE_PROFILE,
                UIP_MAX_SIZE_ENTRIES,
                UIP_EXPIRE_AFTER_WRITE_MINUTES,
                TimeUnit.MINUTES
        );

        CaffeineCache geoDistCache = buildCache(
                "user-distribution-pincode",
                GEO_DIST_MAX_SIZE_ENTRIES,
                GEO_DIST_EXPIRE_AFTER_WRITE_MINUTES,
                TimeUnit.MINUTES
        );

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(profileCache, geoDistCache));
        return manager;
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private CaffeineCache buildCache(String name, int maxSize, long ttl, TimeUnit unit) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .maximumSize(maxSize)
                        .expireAfterWrite(ttl, unit)
                        .recordStats()          // exposes hit/miss ratios via Actuator
                        .build());
    }
}