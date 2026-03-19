package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.UserInterestProfile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


public interface UserInterestProfileRepo
        extends JpaRepository<UserInterestProfile, Long> {

    // ── Profile reads ─────────────────────────────────────────────────────────

    /** Full profile ordered by decayed weight DESC — used for profile display. */
    List<UserInterestProfile> findByUserIdOrderByWeightDesc(Long userId);

    /**
     * Top-N topics for dot-product scoring.
     * Bound to 20 (Constant.HLIG_TOP_N) to keep feed latency < 5 ms.
     */
    @Query("SELECT u FROM UserInterestProfile u WHERE u.userId = :uid ORDER BY u.weight DESC")
    List<UserInterestProfile> findTopNByUserId(@Param("uid") Long userId, Pageable pageable);

    Optional<UserInterestProfile> findByUserIdAndTopic(Long userId, String topic);

    // ── Phase detection (O(1) via cached column) ──────────────────────────────

    /**
     * Returns the cached total signal count for phase detection.
     * This avoids a COUNT(*) per feed request.
     * If no profile exists returns 0 (cold user).
     */
    @Query(value = """
        SELECT COALESCE(MAX(total_signals_cache), 0)
        FROM user_interest_profiles
        WHERE user_id = :uid
        LIMIT 1
        """, nativeQuery = true)
    int getTotalSignalsCache(@Param("uid") Long userId);

    // ── Atomic upsert ─────────────────────────────────────────────────────────

    /**
     * Race-safe atomic upsert.
     * Updates weight, interaction_count, total_signals_cache, bubble_risk_flag,
     * and last_engaged_at in one SQL statement. No read-before-write.
     *
     * Uses GREATEST(0, weight + delta) to prevent negative weights.
     * signalDelta should be +1 for positive signals, 0 for decrement-only.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO user_interest_profiles
            (user_id, topic, weight, interaction_count, total_signals_cache,
             bubble_risk_flag, last_engaged_at, created_at)
        VALUES
            (:uid, :topic, GREATEST(0.0, :delta), GREATEST(0, :sigDelta), GREATEST(0, :sigDelta),
             FALSE,
             CASE WHEN :delta > 0 THEN NOW() ELSE NULL END, NOW())
        ON CONFLICT (user_id, topic) DO UPDATE
        SET weight              = GREATEST(0.0, user_interest_profiles.weight + :delta),
            interaction_count   = user_interest_profiles.interaction_count + :sigDelta,
            total_signals_cache = user_interest_profiles.total_signals_cache + :sigDelta,
            bubble_risk_flag    = (user_interest_profiles.interaction_count + :sigDelta > 200),
            last_engaged_at     = CASE WHEN :delta > 0 THEN NOW() ELSE user_interest_profiles.last_engaged_at END
        """, nativeQuery = true)
    void upsertWeight(
            @Param("uid")      Long   userId,
            @Param("topic")    String topic,
            @Param("delta")    double weightDelta,
            @Param("sigDelta") int    signalDelta
    );

    // ── Collaborative filtering — single query, not N per-topic ───────────────

    /**
     * Finds up to :limit neighbours who share interest in any of the given topics.
     * Single query replaces the old "N queries, one per CORE topic" pattern.
     * Returns user IDs only — no LAZY loading of full profiles.
     *
     * For 1M users this query runs in < 20 ms with idx_uip_topic.
     *
     * Uses GROUP BY user_id + ORDER BY MAX(weight) DESC to return one row per
     * user ordered by their strongest topic weight.
     */
    @Query(value = """
        SELECT user_id
        FROM user_interest_profiles
        WHERE topic IN (:topics)
          AND weight >= :minWeight
          AND user_id != :excludeUid
        GROUP BY user_id
        ORDER BY MAX(weight) DESC
        LIMIT :lim
        """, nativeQuery = true)
    List<Long> findNeighboursByTopics(
            @Param("topics")     List<String> topics,
            @Param("minWeight")  double       minWeight,
            @Param("excludeUid") Long         excludeUserId,
            @Param("lim")        int          limit
    );

    // ── Nightly decay — paginated to avoid full-table lock ────────────────────

    /**
     * Decays a batch of rows older than :minAgeDays.
     * Call repeatedly until rows_affected = 0.
     *
     * Batch size 10,000 rows per call — safe for InnoDB without table-lock.
     * Excludes rows with weight already 0 (no-op rows).
     *
     * @return number of rows updated (stop looping when 0)
     */
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE user_interest_profiles
        SET weight = GREATEST(0.0, weight * EXP(-0.015 * EXTRACT(EPOCH FROM (NOW() - last_engaged_at)) / 86400.0))
        WHERE id IN (
            SELECT id FROM user_interest_profiles
            WHERE weight > 0
              AND last_engaged_at < NOW() - (:minAgeDays || ' days')::INTERVAL
            LIMIT 10000
        )
        """, nativeQuery = true)
    int bulkDecayBatch(@Param("minAgeDays") int minAgeDays);

    // ── Nightly prune — removes near-zero weight rows after decay ─────────────

    /**
     * Deletes rows whose weight has decayed below the given threshold.
     *
     * WHY THIS IS CRITICAL AT SCALE:
     * Without pruning, users who stop using the app accumulate rows that are
     * effectively zero but never get deleted. At 1M users × 20 topics each the
     * table reaches 20M rows on day one. With natural churn and topic drift,
     * an unpruned table can grow to 50M–100M rows within 6 months, causing:
     *   - Slow nightly decay jobs (more rows to scan even if most are 0)
     *   - Larger indexes (slower findNeighboursByTopics and findTopNByUserId)
     *   - Increased PostgreSQL storage cost
     *
     * Called by InterestProfileService.applyNightlyDecay() AFTER the decay pass.
     * Uses a LIMIT to prevent a single massive DELETE from locking the table.
     * InterestProfileService loops this until 0 rows are deleted.
     *
     * Safe to re-run: deleting a near-zero row is idempotent — on next interaction
     * upsertWeight() recreates it with the correct fresh weight.
     *
     * The threshold matches Constant.HLIG_SCORE_MIN_THRESHOLD (0.001).
     * Any row below this weight contributes nothing to feed scoring and is noise.
     *
     * @param threshold rows with weight below this value are deleted (e.g. 0.001)
     * @return number of rows deleted
     */
    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM user_interest_profiles
        WHERE id IN (
            SELECT id FROM user_interest_profiles
            WHERE weight < :threshold
              AND weight >= 0
            LIMIT 10000
        )
        """, nativeQuery = true)
    int pruneNegligibleRows(@Param("threshold") double threshold);

    // ── NeighbourBoost helper ─────────────────────────────────────────────────

    /**
     * Counts how many of the given neighbour user IDs liked the given social post.
     * Used by HLIGScorer.neighbourBoost().
     */
    @Query(value = """
        SELECT COUNT(*)
        FROM post_likes
        WHERE social_post_id = :postId
          AND user_id IN (:nids)
          AND reaction_type = 'LIKE'
        """, nativeQuery = true)
    int countNeighbourLikes(
            @Param("postId") Long       socialPostId,
            @Param("nids")   List<Long> neighbourIds
    );

    // ── Seen-post dedup helper ────────────────────────────────────────────────

    /**
     * Returns the set of post IDs this user has already viewed in the last 72 hours.
     * Used by HLIGFeedService to filter already-seen posts from the candidate pool.
     *
     * 72-hour window is intentional: beyond 72 hours a post may have new comments
     * or context worth re-surfacing, and the seen-set would be enormous for active
     * users if we tracked longer windows.
     */
    @Query(value = """
        SELECT social_post_id
        FROM post_views
        WHERE user_id = :uid
          AND viewed_at > NOW() - INTERVAL '72 hours'
        """, nativeQuery = true)
    List<Long> findRecentlyViewedPostIds(@Param("uid") Long userId);

    // ── Bulk seed (onboarding) ────────────────────────────────────────────────

    /**
     * Batch insert for onboarding seed topics (5 cards at signup).
     * Uses INSERT ... ON CONFLICT DO NOTHING to skip topics the user already has.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO user_interest_profiles
            (user_id, topic, weight, interaction_count, total_signals_cache, last_engaged_at, created_at)
        VALUES (:uid, :topic, :weight, 1, 1, NOW(), NOW())
        ON CONFLICT (user_id, topic) DO NOTHING
        """, nativeQuery = true)
    void seedTopic(
            @Param("uid")    Long   userId,
            @Param("topic")  String topic,
            @Param("weight") double weight
    );


    // Reads the denormalized preferred_languages column from the top-weight row
    @Query("""
  SELECT u.preferredLanguages FROM UserInterestProfile u
  WHERE u.userId = :userId
  ORDER BY u.weight DESC
  LIMIT 1
""")
    String getPreferredLanguages(@Param("userId") Long userId);

    // Finds all "lang:XX" topic rows for language preference rebuilding
    @Query("SELECT u FROM UserInterestProfile u WHERE u.userId = :userId AND u.topic LIKE 'lang:%'")
    List<UserInterestProfile> findLanguageTopicsByUserId(@Param("userId") Long userId);

    // Updates the denormalized preferred_languages on the top-weight row
    @Modifying
    @Query("UPDATE UserInterestProfile u SET u.preferredLanguages = :langs WHERE u.userId = :userId AND u.weight = (SELECT MAX(u2.weight) FROM UserInterestProfile u2 WHERE u2.userId = :userId)")
    void updatePreferredLanguages(@Param("userId") Long userId, @Param("langs") String langs);
}