package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.SocialPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;


@Repository
public interface SocialPostRepo extends JpaRepository<SocialPost, Long> {

    // =========================================================================
    // BASIC QUERIES
    // =========================================================================

    List<SocialPost> findByUserIdAndStatusInOrderByCreatedAtDesc(
            Long userId, List<PostStatus> statuses, Pageable pageable);

    List<SocialPost> findByUserIdAndStatusInAndIdLessThanOrderByCreatedAtDesc(
            Long userId, List<PostStatus> statuses, Long id, Pageable pageable);

    List<SocialPost> findByStatusOrderByCreatedAtDesc(PostStatus status, Pageable pageable);

    /**
     * Cursor-paginated fallback for status-only queries.
     * Used by SocialPostService.fetchRecommendedPosts() and fetchTrendingPosts()
     * when geo/viral filters return fewer posts than the requested page size
     * (sparse-platform safety net for 0–5 user platforms).
     */
    List<SocialPost> findByStatusAndIdLessThanOrderByCreatedAtDesc(
            PostStatus status, Long id, Pageable pageable);

    // =========================================================================
    // VIRAL & TRENDING
    // =========================================================================

    List<SocialPost> findByIsViralTrueAndStatusOrderByViralityScoreDesc(
            PostStatus status, Pageable pageable);

    List<SocialPost> findByIsViralTrueAndStatusAndIdLessThanOrderByViralityScoreDesc(
            PostStatus status, Long id, Pageable pageable);

    List<SocialPost> findByViralTierAndStatusOrderByEngagementScoreDesc(
            String viralTier, PostStatus status, Pageable pageable);

    // =========================================================================
    // LOCATION-BASED
    // =========================================================================

    List<SocialPost> findByPincodeAndStatusOrderByCreatedAtDesc(
            String pincode, PostStatus status, Pageable pageable);

    List<SocialPost> findByStatePrefixAndStatusOrderByCreatedAtDesc(
            String statePrefix, PostStatus status, Pageable pageable);

    List<SocialPost> findByDistrictPrefixAndStatusOrderByCreatedAtDesc(
            String districtPrefix, PostStatus status, Pageable pageable);

    List<SocialPost> findByDistrictPrefixAndStatusAndIdLessThanOrderByCreatedAtDesc(
            String districtPrefix, PostStatus status, Long id, Pageable pageable);

    // =========================================================================
    // HASHTAG
    // =========================================================================

    @Query("SELECT sp FROM SocialPost sp WHERE sp.hashtags LIKE %:hashtag% AND sp.status = :status ORDER BY sp.createdAt DESC")
    List<SocialPost> findByHashtagContaining(@Param("hashtag") String hashtag,
                                             @Param("status") PostStatus status,
                                             Pageable pageable);

    @Query("SELECT sp FROM SocialPost sp WHERE sp.hashtags LIKE %:hashtag% AND sp.status = :status AND sp.id < :id ORDER BY sp.createdAt DESC")
    List<SocialPost> findByHashtagContainingAndIdLessThan(@Param("hashtag") String hashtag,
                                                          @Param("status") PostStatus status,
                                                          @Param("id") Long id,
                                                          Pageable pageable);

    // =========================================================================
    // RECOMMENDATION — ORIGINAL (geographic + quality fallback)
    // Used by SocialPostService.getHomeFeed() for non-HLIG users.
    // =========================================================================

    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
            AND sp.isFlagged = false
            AND sp.qualityScore >= 50
            AND sp.user.isActive = true
            AND (
                sp.viralTier = 'NATIONAL_VIRAL'
                OR (sp.viralTier = 'STATE_VIRAL'    AND (sp.pincode IS NULL OR SUBSTRING(sp.pincode, 1, 2) = :statePrefix))
                OR (sp.viralTier = 'DISTRICT_VIRAL' AND (sp.pincode IS NULL OR SUBSTRING(sp.pincode, 1, 3) = :districtPrefix))
                OR (sp.viralTier = 'LOCAL'          AND (sp.pincode IS NULL OR SUBSTRING(sp.pincode, 1, 3) = :districtPrefix))
            )
            ORDER BY (sp.engagementScore * 0.4 + sp.viralityScore * 0.3) DESC, sp.createdAt DESC
            """)
    List<SocialPost> findRecommendedPostsForUser(@Param("statePrefix") String statePrefix,
                                                 @Param("districtPrefix") String districtPrefix,
                                                 Pageable pageable);

    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
            AND sp.isFlagged = false
            AND sp.qualityScore >= 50
            AND sp.user.isActive = true
            AND sp.id < :beforeId
            AND (
                sp.viralTier = 'NATIONAL_VIRAL'
                OR (sp.viralTier = 'STATE_VIRAL'    AND (sp.pincode IS NULL OR SUBSTRING(sp.pincode, 1, 2) = :statePrefix))
                OR (sp.viralTier = 'DISTRICT_VIRAL' AND (sp.pincode IS NULL OR SUBSTRING(sp.pincode, 1, 3) = :districtPrefix))
                OR (sp.viralTier = 'LOCAL'          AND (sp.pincode IS NULL OR SUBSTRING(sp.pincode, 1, 3) = :districtPrefix))
            )
            ORDER BY (sp.engagementScore * 0.4 + sp.viralityScore * 0.3) DESC, sp.createdAt DESC
            """)
    List<SocialPost> findRecommendedPostsForUserWithCursor(@Param("statePrefix") String statePrefix,
                                                           @Param("districtPrefix") String districtPrefix,
                                                           @Param("beforeId") Long beforeId,
                                                           Pageable pageable);

    // =========================================================================
    // STATISTICS
    // =========================================================================

    Long countByUserId(Long userId);

    Long countByStatus(PostStatus status);

    Long countByCreatedAtAfter(Date date);

    // =========================================================================
    // COMMUNITY FEED — LOCAL / PERSONALIZED
    // =========================================================================

    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND (:cursor IS NULL OR sp.id < :cursor)
              AND (
                sp.community IS NULL
                OR (
                    sp.communityFeedEligible = true
                    AND sp.communityPrivacy  = 'PUBLIC'
                    AND (
                        (sp.pincode        = :pincode        AND sp.engagementScore >= :localThreshold)
                        OR (sp.districtPrefix = :districtPrefix AND sp.engagementScore >= :districtThreshold)
                        OR (sp.statePrefix    = :statePrefix    AND sp.engagementScore >= :stateThreshold)
                    )
                )
              )
            ORDER BY sp.engagementScore DESC, sp.createdAt DESC, sp.id DESC
            """)
    List<SocialPost> findLocalFeedIncludingCommunityPosts(
            @Param("pincode")           String  pincode,
            @Param("districtPrefix")    String  districtPrefix,
            @Param("statePrefix")       String  statePrefix,
            @Param("localThreshold")    int     localThreshold,
            @Param("districtThreshold") int     districtThreshold,
            @Param("stateThreshold")    int     stateThreshold,
            @Param("cursor")            Long    cursor,
            Pageable pageable);

    // =========================================================================
    // COMMUNITY FEED — NATIONAL / EXPLORE
    // =========================================================================

    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND (:cursor IS NULL OR sp.id < :cursor)
              AND (
                sp.community IS NULL
                OR (
                    sp.communityFeedEligible = true
                    AND sp.communityPrivacy  = 'PUBLIC'
                    AND sp.engagementScore  >= :nationalThreshold
                )
              )
            ORDER BY sp.engagementScore DESC, sp.createdAt DESC, sp.id DESC
            """)
    List<SocialPost> findNationalFeedIncludingCommunityPosts(
            @Param("nationalThreshold") int  nationalThreshold,
            @Param("cursor")            Long cursor,
            Pageable pageable);

    // =========================================================================
    // COMMUNITY DETAIL — RECENCY SORT
    // =========================================================================

    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.community.id = :communityId
              AND sp.status = 'ACTIVE'
              AND (:cursor IS NULL OR sp.id < :cursor)
            ORDER BY sp.createdAt DESC, sp.id DESC
            """)
    List<SocialPost> findCommunityPostsCursor(@Param("communityId") Long communityId,
                                              @Param("cursor")      Long cursor,
                                              Pageable pageable);

    // =========================================================================
    // COMMUNITY DETAIL — ENGAGEMENT SORT ("Top" / "Hot")
    // =========================================================================

    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.community.id = :communityId
              AND sp.status = 'ACTIVE'
              AND (:cursor IS NULL OR
                   sp.engagementScore < :cursorScore OR
                   (sp.engagementScore = :cursorScore AND sp.id < :cursor))
            ORDER BY sp.engagementScore DESC, sp.id DESC
            """)
    List<SocialPost> findCommunityPostsByEngagement(@Param("communityId") Long   communityId,
                                                    @Param("cursor")      Long   cursor,
                                                    @Param("cursorScore") Double cursorScore,
                                                    Pageable pageable);

    // =========================================================================
    // DENORMALIZED FIELD SYNC
    // =========================================================================

    @Modifying
    @Query("""
            UPDATE SocialPost sp
            SET sp.communityPrivacy      = :privacy,
                sp.communityFeedEligible = :feedEligible
            WHERE sp.community.id = :communityId
            """)
    void syncCommunityDenormalizedFields(@Param("communityId")  Long    communityId,
                                         @Param("privacy")       String  privacy,
                                         @Param("feedEligible")  boolean feedEligible);

    // =========================================================================
    // REMOVE FROM FEED (community archived / deleted)
    // =========================================================================

    @Modifying
    @Query("UPDATE SocialPost sp SET sp.communityFeedEligible = false WHERE sp.community.id = :communityId")
    void removeCommunityPostsFromFeed(@Param("communityId") Long communityId);

    // ==========================================================================
    // UNIFIED SEARCH — CURSOR-BASED (used by SearchService)
    // ==========================================================================

    /** Keyword search across content + hashtags — FIRST page (no cursor). */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = :status
              AND (LOWER(sp.content)  LIKE LOWER(CONCAT('%', :query, '%'))
               OR  LOWER(sp.hashtags) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY sp.id DESC
            """)
    List<SocialPost> searchFirstPage(
            @Param("query")  String query,
            @Param("status") PostStatus status,
            Pageable pageable);

    /** Keyword search — NEXT pages. */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = :status
              AND sp.id < :cursor
              AND (LOWER(sp.content)  LIKE LOWER(CONCAT('%', :query, '%'))
               OR  LOWER(sp.hashtags) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY sp.id DESC
            """)
    List<SocialPost> searchNextPage(
            @Param("query")  String query,
            @Param("status") PostStatus status,
            @Param("cursor") Long cursor,
            Pageable pageable);

    /** Pincode-scoped keyword search — FIRST page. */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status  = :status
              AND sp.pincode = :pincode
              AND (LOWER(sp.content)  LIKE LOWER(CONCAT('%', :query, '%'))
               OR  LOWER(sp.hashtags) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY sp.id DESC
            """)
    List<SocialPost> searchFirstPageByPincode(
            @Param("query")   String query,
            @Param("status")  PostStatus status,
            @Param("pincode") String pincode,
            Pageable pageable);

    /** Pincode-scoped keyword search — NEXT pages. */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status  = :status
              AND sp.pincode = :pincode
              AND sp.id < :cursor
              AND (LOWER(sp.content)  LIKE LOWER(CONCAT('%', :query, '%'))
               OR  LOWER(sp.hashtags) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY sp.id DESC
            """)
    List<SocialPost> searchNextPageByPincode(
            @Param("query")   String query,
            @Param("status")  PostStatus status,
            @Param("pincode") String pincode,
            @Param("cursor")  Long cursor,
            Pageable pageable);

    /** Hashtag-exact search — FIRST page. */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = :status
              AND LOWER(sp.hashtags) LIKE LOWER(CONCAT('%', :hashtag, '%'))
            ORDER BY sp.id DESC
            """)
    List<SocialPost> searchByHashtagFirstPage(
            @Param("hashtag") String hashtag,
            @Param("status")  PostStatus status,
            Pageable pageable);

    /** Hashtag-exact search — NEXT pages. */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = :status
              AND sp.id < :cursor
              AND LOWER(sp.hashtags) LIKE LOWER(CONCAT('%', :hashtag, '%'))
            ORDER BY sp.id DESC
            """)
    List<SocialPost> searchByHashtagNextPage(
            @Param("hashtag") String hashtag,
            @Param("status")  PostStatus status,
            @Param("cursor")  Long cursor,
            Pageable pageable);

    /** Top N distinct hashtags matching the search query. */
    @Query(value = """
            SELECT token               AS hashtag,
                   COUNT(sp.id)        AS post_count
            FROM   social_posts sp,
                   LATERAL (
                       SELECT TRIM(t) AS token
                       FROM   regexp_split_to_table(sp.hashtags, '\\s+') AS t
                       WHERE  TRIM(t) <> ''
                   ) tokens
            WHERE  sp.status   = 'ACTIVE'
              AND  sp.hashtags IS NOT NULL
              AND  LOWER(token) LIKE LOWER(CONCAT('%', :query, '%'))
            GROUP  BY token
            ORDER  BY post_count DESC
            LIMIT  :limit
            """, nativeQuery = true)
    List<Object[]> findTopHashtags(
            @Param("query") String query,
            @Param("limit") int limit);

    // ==========================================================================
    // HLIG v2 — PERSONALISED FEED QUERIES (used by HLIGFeedService)
    // ==========================================================================

    /**
     * FOR YOU tab — local pincode candidates.
     * First pool in HLIGFeedService.fetchCandidates().
     * Only quality-scored active posts from the user's own pincode.
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.pincode = :pincode
              AND sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.qualityScore >= 40
              AND sp.user.isActive = true
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findLocalFeedPosts(
            @Param("pincode") String pincode, Pageable pageable);

    /**
     * FOR YOU tab — district-viral candidates.
     * Second pool in HLIGFeedService.fetchCandidates().
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.districtPrefix = :districtPrefix
              AND sp.viralTier = 'DISTRICT_VIRAL'
              AND sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.user.isActive = true
            ORDER BY sp.viralityScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findDistrictViralPosts(
            @Param("districtPrefix") String districtPrefix, Pageable pageable);

    /**
     * FOR YOU tab — state-viral candidates.
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.statePrefix = :statePrefix
              AND sp.viralTier = 'STATE_VIRAL'
              AND sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.user.isActive = true
            ORDER BY sp.viralityScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findStateViralPosts(
            @Param("statePrefix") String statePrefix, Pageable pageable);

    /**
     * FOR YOU tab — national-viral candidates.
     * Shown to all users regardless of location.
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.viralTier = 'NATIONAL_VIRAL'
              AND sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.user.isActive = true
            ORDER BY sp.viralityScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findNationalViralPosts(Pageable pageable);

    /**
     * Cold-area fallback: recent quality posts nationwide.
     * Used when the user's local pool is too small (< 50 candidates).
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.qualityScore >= 50
              AND sp.user.isActive = true
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findRecentPostsNational(Pageable pageable);

    /**
     * Cross-area sparse-platform fallback — NO geo filter, NO viral-tier filter,
     * NO qualityScore filter. Returns any ACTIVE post ordered by recency.
     *
     * WHY THIS EXISTS: fetchCandidates() runs a geo waterfall. On a brand-new
     * platform with 2 users from different cities all geo waterfall steps return 0.
     * This query has none of those filters, guaranteeing cross-area users always
     * see each other's posts regardless of viral tier, quality score, or geo match.
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.user.isActive = true
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findRecentActivePosts(Pageable pageable);

    /**
     * Sparse-platform safety net — returns ALL active posts ordered by recency.
     *
     * Last-resort fallback in HLIGFeedService sort=NEW and as a catch-all when
     * every other fallback is exhausted. Unlike findRecentPostsNational, this query
     * has NO qualityScore filter, so even brand-new posts with qualityScore=0 surface.
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.user.isActive = true
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findAllActivePostsForFeed(Pageable pageable);

    /**
     * HOT tab — geo-scoped trending posts within the last 72 hours.
     *
     * Used by HLIGFeedService.fetchBrowsePool() for scope=LOCATION + sort=HOT,
     * and by widenToState() when the pincode-level HOT pool is sparse.
     *
     * Geo filter: posts matching the user's pincode OR districtPrefix
     *             OR state/national viral tier (null params are ignored).
     * Time filter: createdAt >= :since  (caller passes now-72h)
     * Sort: viralityScore DESC, createdAt DESC
     *
     * Index hint: idx_social_post_virality_created on (status, viralityScore, createdAt)
     * covers the WHERE and ORDER BY clauses at scale.
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.createdAt >= :since
              AND sp.user.isActive = true
              AND (
                  (:pincode IS NULL        OR sp.pincode        = :pincode)
                  OR (:districtPrefix IS NULL OR sp.districtPrefix = :districtPrefix)
                  OR sp.viralTier IN ('STATE_VIRAL', 'NATIONAL_VIRAL')
              )
              AND sp.viralityScore > 0
            ORDER BY sp.viralityScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findHotPostsForUser(
            @Param("pincode")         String pincode,
            @Param("districtPrefix")  String districtPrefix,
            @Param("since")           Date   since,
            Pageable pageable);

    /**
     * NEW tab — chronological feed for the user's state + national viral.
     *
     * Used by HLIGFeedService.fetchBrowsePool() for scope=LOCATION + sort=NEW,
     * and by widenToState() when the state-level NEW pool is sparse.
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.user.isActive = true
              AND (
                  sp.statePrefix = :statePrefix
                  OR sp.viralTier = 'NATIONAL_VIRAL'
              )
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findNewPostsForUser(
            @Param("statePrefix") String statePrefix, Pageable pageable);

    /**
     * TOP tab — highest engagement posts for the user's state + national, all-time.
     *
     * Used by HLIGFeedService.fetchBrowsePool() for scope=LOCATION + sort=TOP,
     * and by widenToState() when the state-level TOP pool is sparse.
     *
     * No time window — TOP surfaces the best posts ever in the region.
     * qualityScore >= 50 guards against brand-new zero-engagement posts
     * from cluttering the all-time leaderboard.
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.qualityScore >= 50
              AND sp.user.isActive = true
              AND (
                  sp.statePrefix = :statePrefix
                  OR sp.viralTier IN ('STATE_VIRAL', 'NATIONAL_VIRAL')
              )
            ORDER BY sp.engagementScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findTopPostsForUser(
            @Param("statePrefix") String statePrefix, Pageable pageable);

    /**
     * FOLLOWING tab — posts from communities the user has joined.
     * Only ACTIVE posts from communities where user has an active membership.
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.community.id IN (
                SELECT cm.community.id FROM CommunityMember cm
                WHERE cm.user.id = :userId
                  AND cm.isActive = true
            )
            AND sp.status = 'ACTIVE'
            AND sp.isFlagged = false
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findPostsFromUserCommunities(
            @Param("userId") Long userId, Pageable pageable);

    // ==========================================================================
    // HLIG v2 — PLATFORM-WIDE SORT FALLBACKS
    // Used by HLIGFeedService.widenToPlatform() and absoluteFallback()
    // when geo-scoped queries return fewer than CANDIDATE_SPARSE_FLOOR posts.
    // ==========================================================================

    /**
     * HOT platform-wide fallback — viralityScore DESC within the given time window.
     *
     * Called by HLIGFeedService when:
     *   - widenToPlatform() for sort=HOT (sparse area, no geo posts viral in 72h)
     *   - absoluteFallback() for sort=HOT (last resort before returning empty feed)
     *
     * The :since parameter is supplied by the caller (typically now-72h).
     * No geo filter — surfaces the most viral posts on the entire platform.
     *
     * Index hint: idx_social_post_virality_created on (status, viralityScore, createdAt)
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.createdAt >= :since
              AND sp.user.isActive = true
              AND sp.viralityScore > 0
            ORDER BY sp.viralityScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findHotActivePostsForFeed(
            @Param("since") Date since, Pageable pageable);

    /**
     * TOP platform-wide fallback — engagementScore DESC, all-time, no time window.
     *
     * Called by HLIGFeedService when:
     *   - widenToPlatform() for sort=TOP (sparse area, no high-engagement geo posts)
     *   - absoluteFallback() for sort=TOP (last resort before returning empty feed)
     *
     * qualityScore >= 50 guards against zero-engagement brand-new posts
     * dominating the all-time leaderboard on a sparse platform.
     *
     * Index hint: idx_social_post_engagement on (status, engagementScore DESC)
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.qualityScore >= 50
              AND sp.user.isActive = true
            ORDER BY sp.engagementScore DESC, sp.createdAt DESC
            """)
    List<SocialPost> findTopActivePostsForFeed(Pageable pageable);

    // ==========================================================================
    // HLIG v2 — OWN-POSTS INJECTION (used by HLIGFeedService.injectOwnPosts)
    // ==========================================================================

    /**
     * Returns the user's own most recent ACTIVE posts for own-post injection.
     *
     * WHY THIS EXISTS: On a new/sparse platform a creator's post may not yet
     * have a viralTier, qualityScore, or geo-tag that passes any of the normal
     * geo waterfall queries. Without this the creator would not see their own
     * post in the feed immediately after posting.
     *
     * No qualityScore filter — a brand-new post with qualityScore=0 must
     * still be visible to its creator immediately after posting.
     *
     * Capped to 3 posts (HLIG_OWN_POST_INJECT_LIMIT) by the caller.
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.user.id = :userId
              AND sp.status = 'ACTIVE'
              AND sp.isFlagged = false
            ORDER BY sp.createdAt DESC
            """)
    List<SocialPost> findRecentPostsByUser(
            @Param("userId") Long userId, Pageable pageable);


    // Used by language-aware widening in HLIGFeedService + fetchCandidates
    @Query("""
  SELECT p FROM SocialPost p
  WHERE p.status = 'ACTIVE'
    AND p.language IN :languages
  ORDER BY p.createdAt DESC
""")
    List<SocialPost> findActivePostsByLanguages(
            @Param("languages") List<String> languages, Pageable pageable);

}