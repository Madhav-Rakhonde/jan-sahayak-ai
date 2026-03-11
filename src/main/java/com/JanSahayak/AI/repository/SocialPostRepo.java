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

    List<SocialPost> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<SocialPost> findByUserIdAndIdLessThanOrderByCreatedAtDesc(
            Long userId, Long id, Pageable pageable);

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
     *
     * NOTE: This query filters qualityScore >= 50 AND viralTier implicitly
     * via the ordering. It is NOT suitable as a sparse-platform fallback
     * because on a brand-new platform all posts have viralTier='LOCAL' and
     * qualityScore may be 0, causing this query to return 0 results.
     * Use findRecentActivePosts() for the sparse-platform case.
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
     * WHY THIS EXISTS (the root fix for cross-area empty feeds):
     * fetchCandidates() runs a geo waterfall (local → district viral → state viral
     * → national viral). On a brand-new platform with 2 users from different cities
     * (e.g. Mumbai + Delhi), all steps return 0 because:
     *   - Local posts:          0  (Mumbai user, no Mumbai posts from Delhi user)
     *   - District viral posts: 0  (Delhi post not district-viral yet)
     *   - State viral posts:    0  (same reason)
     *   - National viral posts: 0  (viralTier = 'LOCAL' on new posts)
     * The old Fallback 1 (findRecentPostsNational) ALSO returned 0 because it
     * filters qualityScore >= 50 and on a new post qualityScore may be 0.
     *
     * This query has none of those filters. It fires whenever the geo pool
     * is < 50, guaranteeing cross-area users always see each other's posts
     * regardless of viral tier, quality score, or geo match.
     *
     * Index: idx_social_post_status (status) covers the WHERE clause.
     * No new DB migration needed.
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
     * Used as the last-resort fallback in HLIGFeedService (HOT / NEW / TOP tabs)
     * and SocialPostService when geo-scoped or viral-filtered queries return fewer
     * than 20 posts. This happens on brand-new platforms with 0–5 users before
     * any virality scoring, geo-tagging, or qualityScore computation has run.
     *
     * Unlike findRecentPostsNational, this query has NO qualityScore filter,
     * ensuring that even freshly created posts with qualityScore = 0 are surfaced.
     *
     * Performance note: at scale (millions of posts) the HLIG geo + viral queries
     * always return 50+ candidates so this method is never called in production —
     * it is purely a cold-start / sparse-data safety net.
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
     * HOT tab — trending posts within the user's geographic reach in the last 72 hours.
     * Sorted by viralityScore (pre-computed on SocialPost entity).
     */
    @Query("""
            SELECT sp FROM SocialPost sp
            WHERE sp.status = 'ACTIVE'
              AND sp.isFlagged = false
              AND sp.createdAt >= :since
              AND sp.user.isActive = true
              AND (
                  (:pincode IS NULL OR sp.pincode = :pincode)
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
     * TOP tab — highest engagement posts for the user's state + national.
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
    // HLIG v2 — OWN-POSTS INJECTION (used by HLIGFeedService.injectOwnPosts)
    // ==========================================================================

    /**
     * Returns the user's own most recent ACTIVE posts for own-post injection.
     *
     * WHY THIS EXISTS:
     * On a new/sparse platform a creator's post may not yet have a viralTier,
     * qualityScore, or geo-tag that passes any of the normal geo waterfall queries
     * (findLocalFeedPosts, findDistrictViralPosts, etc.).
     * Without this query the creator would open the app and not see their own post
     * anywhere in the feed — a critical first-impression failure.
     *
     * HLIGFeedService.injectOwnPosts() calls this and adds the results directly
     * into the candidate map via putIfAbsent(), so they are deduplicated if they
     * were already fetched by the geo waterfall.
     *
     * The result is capped to 3 posts (HLIG_OWN_POST_INJECT_LIMIT) so the feed
     * is never dominated by the creator's own content.
     *
     * No qualityScore filter — a brand-new post with qualityScore = 0 must
     * still be visible to its creator immediately after posting.
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

}