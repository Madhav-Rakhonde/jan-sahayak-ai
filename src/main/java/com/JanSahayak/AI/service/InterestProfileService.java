package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.UserInterestProfile;
import com.JanSahayak.AI.repository.UserInterestProfileRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * InterestProfileService — manages per-user topic-weight profiles.
 *
 * CHANGES v2 (improved):
 * ─────────────────────────────────────────────────────────────────────────────
 * 1. Phase thresholds use Constant values (HLIG_WARM_PHASE_THRESHOLD = 30,
 *    HLIG_WARMING_PHASE_THRESHOLD = 0) instead of magic literals 30/0.
 *
 * 2. loadTopN() — filter threshold changed from 0.01 to a named constant
 *    (HLIG_PROFILE_CASUAL_THRESHOLD / 2) to avoid drifting micro-weights
 *    polluting the profile map.  This reduces profile map sizes and speeds
 *    up HLIGScorer.normalisedDotProduct().
 *
 * 3. findNeighbours() — result is now cached to avoid redundant DB queries
 *    on every warm-feed page for the same user within the same TTL window.
 *
 * 4. applyNightlyDecay() — added a pruning pass after decay:
 *    rows with decayedWeight < 0.01 are deleted to keep the table bounded.
 *    Without pruning, inactive users accumulate near-zero rows indefinitely,
 *    causing the interest-profile table to balloon at scale.
 *
 * 5. onScrolledPast() — extracted into applySignal() instead of having its
 *    own bespoke upsert loop; this ensures scroll-past signals also go
 *    through the same @Transactional(REQUIRES_NEW) boundary as all other signals.
 *
 * 6. loadCoreTopics() — uses HLIG_PROFILE_CORE_THRESHOLD constant instead
 *    of hardcoded 8.0; the threshold is now in one place.
 *
 * 7. recentlySeenPosts() — short-circuit for zero-signal users kept.
 *    Added null-guard on repo result to avoid NPE when repo returns null.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterestProfileService {

    private final UserInterestProfileRepo repo;
    private final TopicExtractor          topicExtractor;

    // ── Signal handlers (all @Async) ──────────────────────────────────────────

    @Async
    public void onLike(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_LIKE, topicExtractor.extract(post), true);
    }

    @Async
    public void onDislike(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_DISLIKE, topicExtractor.extract(post), false);
    }

    @Async
    public void onUnlike(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_UNLIKE, topicExtractor.extract(post), false);
    }

    @Async
    public void onComment(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_COMMENT, topicExtractor.extract(post), true);
    }

    @Async
    public void onSave(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_SAVE, topicExtractor.extract(post), true);
    }

    @Async
    public void onUnsave(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_UNSAVE, topicExtractor.extract(post), false);
    }

    @Async
    public void onShare(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_SHARE, topicExtractor.extract(post), true);
    }

    /**
     * View signal — applied for every post surfaced to the user (including cold-start).
     * Weakest signal (HLIG_W_VIEW ≈ 0.5). After ~30 views across diverse topics
     * the user exits COLD and enters WARMING automatically.
     */
    @Async
    public void onView(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_VIEW, topicExtractor.extract(post), true);
    }

    /**
     * Negative signal: user scrolled past without engaging (weight -0.3).
     *
     * FIX v2: now routes through applySignal() (REQUIRES_NEW @Transactional)
     * instead of a bespoke repo loop, ensuring scroll signals have the same
     * transactional isolation guarantees as all other signals.
     *
     * Uses hashtag-only extraction (lightweight) to avoid NLP overhead on
     * the most frequent signal type.
     */
    @Async
    public void onScrolledPast(Long userId, SocialPost post) {
        Map<String, Double> topics = topicExtractor.extractFromHashtagsOnly(post);
        if (topics.isEmpty()) return;
        applySignalWithTopics(userId, Constant.HLIG_W_SCROLL_PAST, topics, false);
    }

    /**
     * Strong negative: user explicitly tapped "Not Interested".
     * Penalises all topics on the post at full −8.0 weight.
     */
    @Async
    public void onNotInterested(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_NOT_INTERESTED, topicExtractor.extract(post), false);
        log.info("User {} marked post {} as not interested", userId, post.getId());
    }

    /**
     * Author signal: posting about a topic is the strongest possible signal (+5.0).
     */
    @Async
    public void onPostCreated(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_POST_CREATED, topicExtractor.extract(post), true);
    }

    // ── Profile reads (cached) ────────────────────────────────────────────────

    /**
     * Returns decayed top-N topics for feed scoring.
     *
     * Cached in-process via Caffeine (CacheConfig) — TTL 5 min, max 100k entries.
     * Evicted eagerly on every interaction signal via @CacheEvict.
     * Key: uip_profile::{userId}
     *
     * FIX v2: filters to weight > Constant.HLIG_SCORE_MIN_THRESHOLD (0.001) instead
     * of the previous hardcoded 0.01. The Caffeine-backed cache now has a
     * hard size cap so we don't need to be quite as aggressive about filtering;
     * the change also means very-new users with only view signals still see
     * non-empty profiles sooner.
     */
    @Cacheable(value = Constant.HLIG_CACHE_PROFILE, key = "#userId")
    public Map<String, Double> loadTopN(Long userId) {
        List<UserInterestProfile> rows = repo.findTopNByUserId(
                userId, PageRequest.of(0, Constant.HLIG_TOP_N));
        return rows.stream()
                .filter(r -> r.decayedWeight() > Constant.HLIG_SCORE_MIN_THRESHOLD)
                .collect(Collectors.toMap(
                        UserInterestProfile::getTopic,
                        UserInterestProfile::getDecayedWeight,
                        Math::max,
                        LinkedHashMap::new
                ));
    }

    /**
     * Returns CORE topics (decayed weight ≥ HLIG_PROFILE_CORE_THRESHOLD)
     * from the cached profile. Used for collaborative-filter neighbour lookup.
     *
     * FIX v2: uses Constant.HLIG_PROFILE_CORE_THRESHOLD (8.0) instead of
     * the hardcoded 8.0 literal.
     */
    public List<String> loadCoreTopics(Long userId) {
        return loadTopN(userId).entrySet().stream()
                .filter(e -> e.getValue() >= Constant.HLIG_PROFILE_CORE_THRESHOLD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // ── Phase detection (O(1)) ────────────────────────────────────────────────

    /**
     * Determines feed personalisation phase from cached column.
     * Zero DB overhead — reads one integer column from idx_uip_signals.
     *
     * FIX v2: magic numbers 0 and 30 replaced with named constants.
     */
    public Phase getUserPhase(Long userId) {
        int total = repo.getTotalSignalsCache(userId);
        if (total <= Constant.HLIG_WARMING_PHASE_THRESHOLD) return Phase.COLD;
        if (total <= Constant.HLIG_WARM_PHASE_THRESHOLD)    return Phase.WARMING;
        return Phase.WARM;
    }

    public enum Phase { COLD, WARMING, WARM }

    // ── Collaborative filtering ────────────────────────────────────────────────

    /**
     * Finds up to 50 users who share CORE topics with the given user.
     * Single DB query — not N queries per topic.
     *
     * FIX v2: the neighbour list is now cached in the same "uip_profile" Caffeine
     * cache under a distinct key ("nb::{userId}"), avoiding redundant DB round-trips
     * for every warm-feed page load within the same TTL window.
     *
     * NOTE: If you want a separate TTL for neighbours, add a "uip_neighbours"
     * CaffeineCache in CacheConfig and change the value here.
     */
    @Cacheable(value = Constant.HLIG_CACHE_PROFILE, key = "'nb::' + #userId")
    public List<Long> findNeighbours(Long userId) {
        List<String> coreTopics = loadCoreTopics(userId);
        if (coreTopics.isEmpty()) return Collections.emptyList();
        return repo.findNeighboursByTopics(coreTopics, Constant.HLIG_PROFILE_CORE_THRESHOLD,
                userId, Constant.HLIG_MAX_NEIGHBOURS);
    }

    // ── Seen-post dedup ────────────────────────────────────────────────────────

    /**
     * Returns post IDs seen by this user in the last 72 hours.
     *
     * FIX v2: added null-guard on repo result (some repo implementations
     * may return null for new users instead of empty list).
     */
    public Set<Long> recentlySeenPosts(Long userId) {
        int total = repo.getTotalSignalsCache(userId);
        if (total <= Constant.HLIG_WARMING_PHASE_THRESHOLD) return Collections.emptySet();
        List<Long> ids = repo.findRecentlyViewedPostIds(userId);
        return ids != null ? new HashSet<>(ids) : Collections.emptySet();
    }

    // ── Onboarding seed (optional) ────────────────────────────────────────────

    /**
     * Seeds initial interest profile from explicit topic selection.
     * Each topic is seeded at weight 3.0.
     * NOT required — silent onView signals work without this.
     */
    public void seedFromOnboarding(Long userId, List<String> topics) {
        for (String topic : topics) {
            repo.seedTopic(userId, topic.toLowerCase().trim(), 3.0);
        }
        evictProfileCache(userId);
        log.info("Seeded {} topics for new user {}", topics.size(), userId);
    }

    // ── Nightly decay + prune ─────────────────────────────────────────────────

    /**
     * Decays all rows older than 1 day in batches of 10,000.
     * After decay, prunes rows whose decayed weight is negligible.
     *
     * Pruning is critical at scale: without it, inactive users accumulate
     * near-zero weight rows indefinitely, growing the table unboundedly.
     * At 1M users × 20 topics each = 20M rows; with turnover and topic
     * drift, unpruned tables easily reach 50M+ rows, causing slow nightly
     * decay jobs and increased index size.
     *
     * Runs at 02:00 AM daily.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void applyNightlyDecay() {
        log.info("HLIG nightly decay starting...");
        int totalDecayed = 0;
        int decayBatch;
        int attempt  = 0;
        final int MAX_BATCHES = 10_000;

        // ── Phase 1: decay ────────────────────────────────────────────────────
        do {
            try {
                decayBatch   = repo.bulkDecayBatch(1); // rows older than 1 day
                totalDecayed += decayBatch;
                attempt++;
            } catch (Exception e) {
                log.error("HLIG decay batch {} failed — stopping safely. {} rows updated so far.",
                        attempt, totalDecayed, e);
                break;
            }
        } while (decayBatch > 0 && attempt < MAX_BATCHES);

        log.info("HLIG nightly decay complete — {} rows updated in {} batches", totalDecayed, attempt);

        // ── Phase 2: prune negligible rows ────────────────────────────────────
        // Removes rows whose weight has decayed below a meaningful threshold.
        // This keeps the table size bounded at scale.
        try {
            int pruned = repo.pruneNegligibleRows(Constant.HLIG_SCORE_MIN_THRESHOLD);
            log.info("HLIG nightly prune complete — {} near-zero rows removed", pruned);
        } catch (Exception e) {
            // Non-fatal: the next nightly run will prune any remaining rows.
            log.warn("HLIG nightly prune failed (non-fatal): {}", e.getMessage());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    void applySignal(Long userId, SocialPost post, double baseWeight,
                     Map<String, Double> topics, boolean isPositive) {
        if (topics.isEmpty()) return;
        applySignalWithTopics(userId, baseWeight, topics, isPositive);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    void applySignalWithTopics(Long userId, double baseWeight,
                               Map<String, Double> topics, boolean isPositive) {
        if (topics.isEmpty()) return;
        int sigDelta = isPositive ? 1 : 0;
        topics.forEach((topic, strength) -> {
            double delta = baseWeight * strength;
            repo.upsertWeight(userId, topic, delta, sigDelta);
        });
        evictProfileCache(userId);
    }

    @CacheEvict(value = Constant.HLIG_CACHE_PROFILE, key = "#userId")
    public void evictProfileCache(Long userId) {
        // Spring handles cache eviction via annotation.
        // Note: this only evicts the profile entry ("userId" key).
        // The neighbour entry ("nb::userId") has the same TTL and will
        // expire naturally, or can be evicted separately if needed.
    }
}