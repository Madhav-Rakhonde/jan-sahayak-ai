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

    private static final int  FEED_CACHE_MAX_SIZE_ENTRIES = 5_000;
    private static final long FEED_CACHE_EXPIRE_AFTER_WRITE_MINUTES = 5L;

    // ── New caches targeting hot frontend polling patterns ────────────────────

    /**
     * Notification unread count — polled every 60 s by useUnreadNotificationsCount hook.
     * With 1000 DAU this is 1000 DB SELECTs/min on a simple COUNT(*). Cache for 30 s.
     * Evicted eagerly by @CacheEvict in NotificationService on mark-read / delete.
     */
    private static final int  NOTIF_COUNT_MAX_SIZE  = 100_000;
    private static final long NOTIF_COUNT_TTL_SECS  = 30L;

    /**
     * User profiles — GET /api/users/me called on every page render.
     * Most frequent DB lookup in the entire system. Cache for 10 min.
     * Evicted on profile update / password change.
     */
    private static final int  USER_PROFILE_MAX_SIZE   = 50_000;
    private static final long USER_PROFILE_TTL_MINUTES = 10L;

    /**
     * Pincode reference data — getStates() / getDistricts() are called from
     * broadcast creation and Settings page. Data is read-only government reference;
     * virtually never changes. Cache for 24 h.
     */
    private static final int  PINCODE_MAX_SIZE     = 200_000;
    private static final long PINCODE_TTL_HOURS    = 24L;

    /**
     * Community list — browse page calls GET /api/communities on every mount.
     * Cache for 5 min; evicted on community create/archive.
     */
    private static final int  COMMUNITY_LIST_MAX_SIZE   = 10_000;
    private static final long COMMUNITY_LIST_TTL_MINUTES = 5L;

    private static final int  COMMUNITIES_MAX_SIZE   = 5_000;
    private static final long COMMUNITIES_TTL_MINUTES = 60L;

    /** Auth user details cache - strict 1 min TTL for quick ban propagation */
    private static final int  AUTH_USER_DETAILS_MAX_SIZE = 50_000;
    private static final long AUTH_USER_DETAILS_TTL_MINUTES = 1L;

    /** Subscription tiers cache */
    private static final int  USER_TIERS_MAX_SIZE = 50_000;
    private static final long USER_TIERS_TTL_HOURS = 1L;

    /** Translations API cache to prevent duplicate external calls */
    private static final int  TRANSLATIONS_API_MAX_SIZE = 10_000;
    private static final long TRANSLATIONS_API_TTL_HOURS = 2L;

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

        CaffeineCache trendingPostsCache = buildCache(
                "trending-posts",
                FEED_CACHE_MAX_SIZE_ENTRIES,
                FEED_CACHE_EXPIRE_AFTER_WRITE_MINUTES,
                TimeUnit.MINUTES
        );

        CaffeineCache broadcastFeedsCache = buildCache(
                "broadcast-feeds",
                FEED_CACHE_MAX_SIZE_ENTRIES,
                FEED_CACHE_EXPIRE_AFTER_WRITE_MINUTES,
                TimeUnit.MINUTES
        );

        CaffeineCache hligFeedCache = buildCache(
                "hlig_feed",
                FEED_CACHE_MAX_SIZE_ENTRIES,
                FEED_CACHE_EXPIRE_AFTER_WRITE_MINUTES,
                TimeUnit.MINUTES
        );

        // ── New performance caches ────────────────────────────────────────────

        CaffeineCache notifCountCache = buildCache(
                Constant.CACHE_NOTIF_UNREAD_COUNT,
                NOTIF_COUNT_MAX_SIZE,
                NOTIF_COUNT_TTL_SECS,
                TimeUnit.SECONDS
        );

        CaffeineCache userProfileCache = buildCache(
                Constant.CACHE_USER_PROFILE,
                USER_PROFILE_MAX_SIZE,
                USER_PROFILE_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        CaffeineCache pincodeCache = buildCache(
                Constant.CACHE_PINCODE_DATA,
                PINCODE_MAX_SIZE,
                PINCODE_TTL_HOURS,
                TimeUnit.HOURS
        );

        CaffeineCache communityListCache = buildCache(
                Constant.CACHE_COMMUNITY_LIST,
                COMMUNITY_LIST_MAX_SIZE,
                COMMUNITY_LIST_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        CaffeineCache communitiesCache = buildCache(
                "communities",
                COMMUNITIES_MAX_SIZE,
                COMMUNITIES_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        CaffeineCache authUserDetailsCache = buildCache(
                "authUserDetails",
                AUTH_USER_DETAILS_MAX_SIZE,
                AUTH_USER_DETAILS_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        CaffeineCache userTiersCache = buildCache(
                "userTiers",
                USER_TIERS_MAX_SIZE,
                USER_TIERS_TTL_HOURS,
                TimeUnit.HOURS
        );

        CaffeineCache translationsApiCache = buildCache(
                "translationsApi",
                TRANSLATIONS_API_MAX_SIZE,
                TRANSLATIONS_API_TTL_HOURS,
                TimeUnit.HOURS
        );

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                profileCache, geoDistCache,
                trendingPostsCache, broadcastFeedsCache, hligFeedCache,
                notifCountCache, userProfileCache, pincodeCache, communityListCache,
                communitiesCache, authUserDetailsCache, userTiersCache, translationsApiCache
        ));
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
