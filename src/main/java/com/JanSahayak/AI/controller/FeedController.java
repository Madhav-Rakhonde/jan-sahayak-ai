package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.DTO.SocialPostDto;
import com.JanSahayak.AI.enums.FeedScope;
import com.JanSahayak.AI.enums.FeedSort;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.service.PostService;
import com.JanSahayak.AI.service.SocialPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * FeedController
 *
 * ════════════════════════════════════════════════════════════
 *  UI LAYOUT
 * ════════════════════════════════════════════════════════════
 *
 *   Tab row:   [ For You ]  [ Location ]  [ Following ]
 *   Sort row:  [ Hot ]  [ New ]  [ Top ]
 *
 *   "For You" replaces the old raw "All" tab.
 *   The sort row applies to every tab.
 *
 * ════════════════════════════════════════════════════════════
 *  ENDPOINT MAP
 * ════════════════════════════════════════════════════════════
 *
 *  FOR YOU   →  GET /api/v1/feed/for-you?sort=HOT|NEW|TOP   (default HOT)
 *               HLIG builds a personalised candidate pool (interest-scored,
 *               geo-weighted, seen-deduped) then re-ranks it by the sort metric.
 *               Cold-start users fall back to geo-based candidates automatically.
 *
 *  LOCATION  →  GET /api/v1/feed/location?sort=HOT|NEW|TOP  (default HOT)
 *               User's pincode → district → state waterfall, sorted.
 *
 *  FOLLOWING →  GET /api/v1/feed/following?sort=HOT|NEW|TOP (default HOT)
 *               Posts from communities the user has joined, sorted.
 *               Falls back to cold feed when user has no communities yet.
 *
 * ════════════════════════════════════════════════════════════
 *  SORT VALUES
 * ════════════════════════════════════════════════════════════
 *
 *   HOT  →  viralityScore DESC, 72-hour window
 *   NEW  →  createdAt DESC (pure chronological)
 *   TOP  →  engagementScore DESC, all-time
 *
 * ════════════════════════════════════════════════════════════
 *  INFINITE SCROLL CONTRACT (same for all three tabs)
 * ════════════════════════════════════════════════════════════
 *
 *   First page:  GET /api/v1/feed/for-you?sort=HOT&size=20
 *   Next pages:  GET /api/v1/feed/for-you?sort=HOT&lastPostId={nextCursor}&size=20
 *
 *   Response shape:
 *   {
 *     "content":    [...],    // List<SocialPostDto>
 *     "hasMore":    true,
 *     "nextCursor": 12345,    // pass as ?lastPostId= on the next call
 *     "size":       20
 *   }
 *
 *   Stop fetching when hasMore = false.
 */
@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
@Slf4j
public class FeedController {

    private final SocialPostService socialPostService;
    private final PostService       postService;

    // =========================================================================
    // TAB 1 — FOR YOU  (HLIG personalised pool, re-ranked by sort)
    // =========================================================================

    /**
     * FOR YOU tab.
     *
     * Step 1: HLIG phase scorer (cold / warming / warm) builds a candidate pool
     *         matched to the user's interest profile.
     * Step 2: That pool is re-ranked by the chosen sort metric.
     *
     *   ?sort=HOT  →  trending posts from topics you care about
     *   ?sort=NEW  →  latest posts from topics you care about
     *   ?sort=TOP  →  all-time best posts from topics you care about
     *
     * GET /api/v1/feed/for-you?sort=HOT&lastPostId={cursor}&size=20
     */
    @GetMapping("/for-you")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getForYouFeed(
            @AuthenticationPrincipal            User     user,
            @RequestParam(defaultValue = "HOT") FeedSort sort,
            @RequestParam(required = false)     Long     lastPostId,
            @RequestParam(defaultValue = "20")  int      size) {

        size = clampSize(size);
        log.debug("[Feed] FOR-YOU sort={}: userId={} lastPostId={} size={}",
                sort, userId(user), lastPostId, size);
        return ResponseEntity.ok(
                socialPostService.getBrowseFeed(user, FeedScope.FOR_YOU, sort, lastPostId, size));
    }

    // =========================================================================
    // TAB 2 — LOCATION  (user's area, sorted)
    // =========================================================================

    /**
     * LOCATION tab.
     *
     * Fetches posts from the user's pincode → district → state (waterfall).
     * Automatically widens to platform-wide when the local area is sparse.
     *
     * GET /api/v1/feed/location?sort=NEW&lastPostId={cursor}&size=20
     */
    @GetMapping("/location")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getLocationFeed(
            @AuthenticationPrincipal            User     user,
            @RequestParam(defaultValue = "HOT") FeedSort sort,
            @RequestParam(required = false)     Long     lastPostId,
            @RequestParam(defaultValue = "20")  int      size) {

        size = clampSize(size);
        log.debug("[Feed] LOCATION sort={}: userId={} lastPostId={} size={}",
                sort, userId(user), lastPostId, size);
        return ResponseEntity.ok(
                socialPostService.getBrowseFeed(user, FeedScope.LOCATION, sort, lastPostId, size));
    }

    // =========================================================================
    // TAB 3 — FOLLOWING  (joined communities, sorted)
    // =========================================================================

    /**
     * FOLLOWING tab.
     *
     * Posts from communities the user has explicitly joined.
     * Falls back to cold-feed candidates for users with no communities yet.
     *
     * GET /api/v1/feed/following?sort=TOP&lastPostId={cursor}&size=20
     */
    @GetMapping("/following")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getFollowingFeed(
            @AuthenticationPrincipal            User     user,
            @RequestParam(defaultValue = "HOT") FeedSort sort,
            @RequestParam(required = false)     Long     lastPostId,
            @RequestParam(defaultValue = "20")  int      size) {

        size = clampSize(size);
        log.debug("[Feed] FOLLOWING sort={}: userId={} lastPostId={} size={}",
                sort, userId(user), lastPostId, size);
        return ResponseEntity.ok(
                socialPostService.getBrowseFeed(user, FeedScope.FOLLOWING, sort, lastPostId, size));
    }

    // =========================================================================
    // SUPPORTING FEED ENDPOINTS  (not main tabs)
    // =========================================================================

    /**
     * User profile posts.
     *
     * GET /api/v1/feed/user/{userId}?beforeId={cursor}&limit=20
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getUserFeed(
            @PathVariable                   Long userId,
            @AuthenticationPrincipal        User currentUser,
            @RequestParam(required = false)  Long beforeId,
            @RequestParam(defaultValue = "20") int limit) {

        limit = clampSize(limit);
        return ResponseEntity.ok(
                socialPostService.getUserPosts(userId, currentUser, beforeId, limit));
    }

    /**
     * Hashtag feed.
     *
     * GET /api/v1/feed/hashtag/{hashtag}?beforeId={cursor}&limit=20
     */
    @GetMapping("/hashtag/{hashtag}")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getHashtagFeed(
            @PathVariable                   String hashtag,
            @AuthenticationPrincipal        User   user,
            @RequestParam(required = false)  Long   beforeId,
            @RequestParam(defaultValue = "20") int  limit) {

        limit = clampSize(limit);
        return ResponseEntity.ok(
                socialPostService.searchByHashtag(hashtag, user, beforeId, limit));
    }

    /**
     * Local issue posts — geo waterfall: pincode → district → state → national.
     *
     * Backed by PostService (BroadcastScope issue posts, not social posts).
     * Returns PostResponse, not SocialPostDto.
     *
     * GET /api/v1/feed/local?beforeId={cursor}&limit=20
     */
    @GetMapping("/local")
    public ResponseEntity<PaginatedResponse<PostResponse>> getLocalFeed(
            @AuthenticationPrincipal        User user,
            @RequestParam(required = false)  Long beforeId,
            @RequestParam(defaultValue = "20") int limit) {

        limit = clampSize(limit);
        log.debug("[Feed] LOCAL: userId={} beforeId={} limit={}", userId(user), beforeId, limit);
        return ResponseEntity.ok(postService.getLocalFeed(user, beforeId, limit));
    }

    /**
     * Official government broadcasts only.
     * Filtered by department/admin roles and user's geographic location.
     *
     * GET /api/v1/feed/official?beforeId={cursor}&limit=20
     */
    @GetMapping("/official")
    public ResponseEntity<PaginatedResponse<PostResponse>> getOfficialFeed(
            @AuthenticationPrincipal        User user,
            @RequestParam(required = false)  Long beforeId,
            @RequestParam(defaultValue = "20") int limit) {

        limit = clampSize(limit);
        log.debug("[Feed] OFFICIAL: userId={} beforeId={} limit={}", userId(user), beforeId, limit);
        return ResponseEntity.ok(postService.getOfficialFeed(user, beforeId, limit));
    }


    // =========================================================================
    // HLIG NEGATIVE SIGNAL ENDPOINTS  (fire-and-forget)
    // =========================================================================

    /**
     * User scrolled past a post without engaging.
     * Fires a weak negative interest signal. Always returns 204.
     *
     * POST /api/v1/feed/signal/scroll-past
     * Body: { "postId": 123 }
     */
    @PostMapping("/signal/scroll-past")
    public ResponseEntity<Void> recordScrolledPast(
            @AuthenticationPrincipal User              user,
            @RequestBody             Map<String, Long> body) {

        Long postId = body.get("postId");
        if (postId != null) {
            socialPostService.recordScrolledPast(postId, user);
            log.debug("[HLIG] scroll-past: postId={} userId={}", postId, userId(user));
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * User tapped "Not Interested".
     * Fires a strong negative signal — suppresses this topic in future For You scoring.
     *
     * POST /api/v1/feed/signal/not-interested
     * Body: { "postId": 123 }
     */
    @PostMapping("/signal/not-interested")
    public ResponseEntity<Map<String, Object>> recordNotInterested(
            @AuthenticationPrincipal User              user,
            @RequestBody             Map<String, Long> body) {

        Long postId = body.get("postId");
        if (postId != null) {
            socialPostService.recordNotInterested(postId, user);
            log.info("[HLIG] not-interested: postId={} userId={}", postId, userId(user));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "We'll show you less content like this."
        ));
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /** Clamps page size to [1, FEED_MAX_PAGE_SIZE]. */
    private int clampSize(int size) {
        return Math.max(1, Math.min(size, Constant.FEED_MAX_PAGE_SIZE));
    }

    /** Safe userId extraction for logging — guards against unauthenticated calls. */
    private String userId(User user) {
        return (user != null && user.getId() != null) ? user.getId().toString() : "anon";
    }
}