package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.config.Constant;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.SocialPostDto;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.service.PostService;
import com.JanSahayak.AI.service.SocialPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
@Slf4j
public class FeedController {

    private final SocialPostService socialPostService;
    private final PostService          postService;

    // =========================================================================
    // DEFAULT LIMITS
    // =========================================================================

    // =========================================================================
    // HLIG v2 — 5-TAB PERSONALISED FEED (cursor-based infinite scroll)
    // =========================================================================
    //
    // ALL 5 TABS USE THE SAME INFINITE SCROLL CONTRACT:
    //
    //   First page:   GET /api/v1/feed/for-you?size=20
    //   Next pages:   GET /api/v1/feed/for-you?lastPostId=<nextCursor>&size=20
    //
    // The `nextCursor` comes from the PaginatedResponse the server returns:
    //   {
    //     "content":    [...],        // list of posts
    //     "hasMore":    true,         // false = end of feed reached
    //     "nextCursor": 12345,        // pass as ?lastPostId= in next request
    //     "size":       20
    //   }
    //
    // When hasMore = false, stop fetching. No more calls needed.
    //
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * FOR YOU — fully personalised HLIG v2 feed, infinite scroll.
     *
     * Cold-start users (no interaction history) receive a geographic fallback.
     * Warm users receive a ML-scored, diversity-shuffled, seen-deduped feed.
     *
     * First page:  GET /api/v1/feed/for-you?size=20
     * Next pages:  GET /api/v1/feed/for-you?lastPostId={cursor}&size=20
     */
    @GetMapping("/for-you")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getForYouFeed(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long lastPostId,
            @RequestParam(defaultValue = "20") int size) {

        size = clampSize(size);
        log.debug("[Feed] FOR YOU: userId={} lastPostId={} size={}", userId(user), lastPostId, size);

        PaginatedResponse<SocialPostDto> response = socialPostService.getPersonalisedFeed(user, lastPostId, size);
        return ResponseEntity.ok(response);
    }

    /**
     * HOT — trending posts from the user's local + national in the last 72 hours.
     * Sorted by virality score. Cursor-paginated infinite scroll.
     *
     * First page:  GET /api/v1/feed/hot?size=20
     * Next pages:  GET /api/v1/feed/hot?lastPostId={cursor}&size=20
     */
    @GetMapping("/hot")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getHotFeed(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long lastPostId,
            @RequestParam(defaultValue = "20") int size) {

        size = clampSize(size);
        log.debug("[Feed] HOT: userId={} lastPostId={} size={}", userId(user), lastPostId, size);

        PaginatedResponse<SocialPostDto> response = socialPostService.getHotFeed(user, lastPostId, size);
        return ResponseEntity.ok(response);
    }

    /**
     * NEW — chronological feed for the user's state + national viral.
     * Equivalent to Reddit's "New" sort. Cursor-paginated infinite scroll.
     *
     * First page:  GET /api/v1/feed/new?size=20
     * Next pages:  GET /api/v1/feed/new?lastPostId={cursor}&size=20
     */
    @GetMapping("/new")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getNewFeed(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long lastPostId,
            @RequestParam(defaultValue = "20") int size) {

        size = clampSize(size);
        log.debug("[Feed] NEW: userId={} lastPostId={} size={}", userId(user), lastPostId, size);

        PaginatedResponse<SocialPostDto> response = socialPostService.getNewFeed(user, lastPostId, size);
        return ResponseEntity.ok(response);
    }

    /**
     * TOP — all-time highest engagement posts in the user's state + national.
     * Equivalent to Reddit's "Top" sort. Cursor-paginated infinite scroll.
     *
     * First page:  GET /api/v1/feed/top?size=20
     * Next pages:  GET /api/v1/feed/top?lastPostId={cursor}&size=20
     */
    @GetMapping("/top")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getTopFeed(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long lastPostId,
            @RequestParam(defaultValue = "20") int size) {

        size = clampSize(size);
        log.debug("[Feed] TOP: userId={} lastPostId={} size={}", userId(user), lastPostId, size);

        PaginatedResponse<SocialPostDto> response = socialPostService.getTopFeed(user, lastPostId, size);
        return ResponseEntity.ok(response);
    }

    /**
     * FOLLOWING — posts from communities the user has explicitly joined.
     * Equivalent to Reddit's home feed. Cursor-paginated infinite scroll.
     *
     * First page:  GET /api/v1/feed/following?size=20
     * Next pages:  GET /api/v1/feed/following?lastPostId={cursor}&size=20
     */
    @GetMapping("/following")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getFollowingFeed(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long lastPostId,
            @RequestParam(defaultValue = "20") int size) {

        size = clampSize(size);
        log.debug("[Feed] FOLLOWING: userId={} lastPostId={} size={}", userId(user), lastPostId, size);

        PaginatedResponse<SocialPostDto> response = socialPostService.getFollowingFeed(user, lastPostId, size);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // LEGACY FEED ENDPOINTS (pre-HLIG, kept for backward compatibility)
    // =========================================================================

    /**
     * Home feed — pre-HLIG geographic recommendation.
     * Still used as the fallback inside SocialPostService.getPersonalisedFeed()
     * for cold-start users. Kept as a standalone endpoint for older app versions.
     *
     * GET /api/v1/feed/home?beforeId=&limit=20
     *
     * @deprecated Use /for-you for personalised feed.
     */
    @GetMapping("/home")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getHomeFeed(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long    beforeId,
            @RequestParam(defaultValue = "20") int  limit) {

        limit = clampSize(limit);
        log.debug("[Feed] HOME (legacy): userId={} beforeId={} limit={}", userId(user), beforeId, limit);

        PaginatedResponse<SocialPostDto> response = socialPostService.getHomeFeed(user, beforeId, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * Local posts — geo waterfall: pincode → district → state → national.
     *
     * Powered by PostService (BroadcastScope-based issue posts), NOT SocialPostService.
     * Automatically widens scope when the user's pincode / district has too few posts,
     * so this tab always has content even on a sparse platform.
     *
     * Response type: PaginatedResponse<PostResponse>  (Post DTOs, not SocialPostDto)
     *
     * GET /api/v1/feed/local?beforeId=&limit=20
     */
    @GetMapping("/local")
    public ResponseEntity<PaginatedResponse<PostResponse>> getLocalFeed(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long   beforeId,
            @RequestParam(defaultValue = "20") int limit) {

        limit = clampSize(limit);
        log.debug("[Feed] LOCAL: userId={} beforeId={} limit={}", userId(user), beforeId, limit);

        PaginatedResponse<PostResponse> response = postService.getLocalFeed(user, beforeId, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * Trending posts — viral posts sorted by viralityScore.
     *
     * GET /api/v1/feed/trending?beforeId=&limit=20
     */
    @GetMapping("/trending")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getTrendingFeed(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long    beforeId,
            @RequestParam(defaultValue = "20") int  limit) {

        limit = clampSize(limit);
        log.debug("[Feed] TRENDING: userId={} beforeId={} limit={}", userId(user), beforeId, limit);

        PaginatedResponse<SocialPostDto> response = socialPostService.getTrendingPosts(user, beforeId, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * User profile posts.
     *
     * GET /api/v1/feed/user/{userId}?beforeId=&limit=20
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getUserFeed(
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) Long    beforeId,
            @RequestParam(defaultValue = "20") int  limit) {

        limit = clampSize(limit);
        PaginatedResponse<SocialPostDto> response = socialPostService.getUserPosts(userId, currentUser, beforeId, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * Hashtag feed.
     *
     * GET /api/v1/feed/hashtag/{hashtag}?beforeId=&limit=20
     */
    @GetMapping("/hashtag/{hashtag}")
    public ResponseEntity<PaginatedResponse<SocialPostDto>> getHashtagFeed(
            @PathVariable String hashtag,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long    beforeId,
            @RequestParam(defaultValue = "20") int  limit) {

        limit = clampSize(limit);
        PaginatedResponse<SocialPostDto> response = socialPostService.searchByHashtag(hashtag, user, beforeId, limit);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // HLIG v2 — NEGATIVE SIGNAL ENDPOINTS
    // These are fire-and-forget; they always return 204 No Content.
    // =========================================================================

    /**
     * Record that the user scrolled past a post without engaging.
     * Fires a weak negative interest signal to the HLIG system.
     *
     * POST /api/v1/feed/signal/scroll-past
     * Body: { "postId": 123 }
     */
    @PostMapping("/signal/scroll-past")
    public ResponseEntity<Void> recordScrolledPast(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Long> body) {

        Long postId = body.get("postId");
        if (postId != null) {
            socialPostService.recordScrolledPast(postId, user);
            log.debug("[HLIG] scroll-past signal: postId={} userId={}", postId, userId(user));
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Record that the user explicitly tapped "Not Interested" on a post.
     * Fires a strong negative interest signal — suppresses this topic in future scoring.
     *
     * POST /api/v1/feed/signal/not-interested
     * Body: { "postId": 123 }
     */
    @PostMapping("/signal/not-interested")
    public ResponseEntity<Map<String, Object>> recordNotInterested(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Long> body) {

        Long postId = body.get("postId");
        if (postId != null) {
            socialPostService.recordNotInterested(postId, user);
            log.info("[HLIG] not-interested signal: postId={} userId={}", postId, userId(user));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "We'll show you less content like this."
        ));
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /** Clamps page size between 1 and Constant.FEED_MAX_PAGE_SIZE. */
    private int clampSize(int size) {
        return Math.max(1, Math.min(size, Constant.FEED_MAX_PAGE_SIZE));
    }

    /** Safe userId extraction for logging (avoids NPE). */
    private String userId(User user) {
        return user != null && user.getId() != null ? user.getId().toString() : "anon";
    }
}