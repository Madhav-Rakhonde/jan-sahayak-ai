package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.config.Constant;

import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.exception.ResourceNotFoundException;
import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.model.PostShare.ShareType;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.service.PostInteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║         PostInteractionController  —  Non-comment Interactions           ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  Owns: VIEWS · LIKES · DISLIKES · SAVES · SHARES                        ║
 * ║  Comments are handled exclusively by CommentController (/api/comments)   ║
 * ║                                                                          ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  Post type is selected via {postType} path variable:                     ║
 * ║    "posts"        →  regular Issue / Broadcast Post                      ║
 * ║    "social-posts" →  SocialPost                                          ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  ROOT CAUSE FIX (detached-entity / post_id cannot be null):             ║
 * ║  The controller NO LONGER calls postRepo / socialPostRepo directly for   ║
 * ║  any write operation. It passes only the ID to the service's *ById       ║
 * ║  methods, which load the entity INSIDE the @Transactional boundary.      ║
 * ║  This keeps the entity managed throughout the entire operation and        ║
 * ║  prevents the FK (social_post_id / post_id) from resolving to null.      ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
@RestController
@RequestMapping("/api/interactions")
@RequiredArgsConstructor
@Slf4j
public class PostInteractionController {

    // ── Service (all interaction logic lives here) ─────────────────────────────
    // NOTE: PostRepo / SocialPostRepo are intentionally NOT injected here.
    // All entity resolution for write paths happens inside the service transaction.
    // Read-only count/status GETs use the service's getSocialPostById / getPostById.
    private final PostInteractionService interactionService;

    // =========================================================================
    // VIEWS
    // =========================================================================

    @PostMapping("/{postType}/{id}/view")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordView(
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        log.debug("[View] postType={} id={} user={}", postType, id, currentUser.getActualUsername());

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                Post post = interactionService.getPostById(id);
                PostView view = interactionService.recordPostViewById(id, currentUser);
                if (view == null) return ResponseEntity.noContent().build();
                return ok("View recorded", Map.of("viewCount", post.getViewCount()));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                PostView view = interactionService.recordSocialPostViewById(id, currentUser);
                if (view == null) return ResponseEntity.noContent().build();
                SocialPost sp = interactionService.getSocialPostById(id);
                return ok("View recorded", Map.of("viewCount", sp.getViewCount()));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[View] Failed: postType={} id={}", postType, id, e);
            return err("Failed to record view", e.getMessage());
        }
    }

    // =========================================================================
    // LIKES
    // =========================================================================

    @PostMapping("/{postType}/{id}/like")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> likePost(
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        log.debug("[Like] postType={} id={} user={}", postType, id, currentUser.getActualUsername());

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                boolean liked = interactionService.likePostById(id, currentUser);
                Post post = interactionService.getPostById(id);
                return ok("Like toggled", reactionBody("liked", liked, post.getLikeCount(), post.getDislikeCount()));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                boolean liked = interactionService.likeSocialPostById(id, currentUser);
                SocialPost sp = interactionService.getSocialPostById(id);
                return ok("Like toggled", reactionBody("liked", liked, sp.getLikeCount(), sp.getDislikeCount()));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Like] Failed: postType={} id={}", postType, id, e);
            return err("Failed to toggle like", e.getMessage());
        }
    }

    // =========================================================================
    // DISLIKES
    // =========================================================================

    @PostMapping("/{postType}/{id}/dislike")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dislikePost(
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        log.debug("[Dislike] postType={} id={} user={}", postType, id, currentUser.getActualUsername());

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                boolean disliked = interactionService.dislikePostById(id, currentUser);
                Post post = interactionService.getPostById(id);
                return ok("Dislike toggled", reactionBody("disliked", disliked, post.getLikeCount(), post.getDislikeCount()));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                boolean disliked = interactionService.dislikeSocialPostById(id, currentUser);
                SocialPost sp = interactionService.getSocialPostById(id);
                return ok("Dislike toggled", reactionBody("disliked", disliked, sp.getLikeCount(), sp.getDislikeCount()));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Dislike] Failed: postType={} id={}", postType, id, e);
            return err("Failed to toggle dislike", e.getMessage());
        }
    }

    // =========================================================================
    // SAVE (toggle)
    // =========================================================================

    @PostMapping("/{postType}/{id}/save")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleSave(
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        log.debug("[Save] postType={} id={} user={}", postType, id, currentUser.getActualUsername());

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                boolean saved = interactionService.toggleBroadcastPostSaveById(id, currentUser);
                Post post = interactionService.getPostById(id);
                return ok("Save toggled", Map.of("saved", saved, "saveCount", post.getSaveCount()));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                boolean saved = interactionService.toggleSocialPostSaveById(id, currentUser);
                SocialPost sp = interactionService.getSocialPostById(id);
                return ok("Save toggled", Map.of("saved", saved, "saveCount", sp.getSaveCount()));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Save] Failed: postType={} id={}", postType, id, e);
            return err("Failed to toggle save", e.getMessage());
        }
    }

    @GetMapping("/saved")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<SavedPost>>> getSavedPosts(
            @CurrentUser User currentUser,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            return ok("Saved posts retrieved",
                    interactionService.getSavedPostsForUser(currentUser, page, size));
        } catch (Exception e) {
            log.error("[Save] getSavedPosts failed for user={}", currentUser.getActualUsername(), e);
            return err("Failed to retrieve saved posts", e.getMessage());
        }
    }

    @GetMapping("/saved/cursor")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<SavedPost>>> getSavedPostsCursor(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int size) {

        try {
            return ok("Saved posts retrieved",
                    interactionService.getSavedPostsForUserWithCursor(currentUser, beforeId, size));
        } catch (Exception e) {
            log.error("[Save] getSavedPostsCursor failed for user={}", currentUser.getActualUsername(), e);
            return err("Failed to retrieve saved posts", e.getMessage());
        }
    }

    @GetMapping("/saved/social-posts")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<SavedPost>>> getSavedSocialPosts(
            @CurrentUser User currentUser,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            return ok("Saved social posts retrieved",
                    interactionService.getSavedSocialPostsForUser(currentUser, page, size));
        } catch (Exception e) {
            log.error("[Save] getSavedSocialPosts failed for user={}", currentUser.getActualUsername(), e);
            return err("Failed to retrieve saved social posts", e.getMessage());
        }
    }

    @GetMapping("/saved/posts")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<SavedPost>>> getSavedBroadcastPosts(
            @CurrentUser User currentUser,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            return ok("Saved broadcast posts retrieved",
                    interactionService.getSavedBroadcastPostsForUser(currentUser, page, size));
        } catch (Exception e) {
            log.error("[Save] getSavedBroadcastPosts failed for user={}", currentUser.getActualUsername(), e);
            return err("Failed to retrieve saved broadcast posts", e.getMessage());
        }
    }

    // =========================================================================
    // SHARE
    // =========================================================================

    @PostMapping("/{postType}/{id}/share")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordShare(
            @PathVariable String postType,
            @PathVariable Long id,
            @RequestParam(required = false) ShareType shareType,
            @CurrentUser User currentUser) {

        log.debug("[Share] postType={} id={} shareType={} user={}", postType, id, shareType, currentUser.getActualUsername());

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                interactionService.recordPostShareById(id, currentUser, shareType);
                Post post = interactionService.getPostById(id);
                return ok("Share recorded", Map.of("shareCount", post.getShareCount()));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                interactionService.recordSocialPostShareById(id, currentUser, shareType);
                SocialPost sp = interactionService.getSocialPostById(id);
                return ok("Share recorded", Map.of("shareCount", sp.getShareCount()));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Share] Failed: postType={} id={}", postType, id, e);
            return err("Failed to record share", e.getMessage());
        }
    }

    @GetMapping("/{postType}/{id}/share/breakdown")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<Object[]>>> getShareBreakdown(
            @PathVariable String postType,
            @PathVariable Long id) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                return ok("Share breakdown retrieved",
                        interactionService.getShareBreakdownForPost(interactionService.getPostById(id)));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                return ok("Share breakdown retrieved",
                        interactionService.getShareBreakdownForSocialPost(interactionService.getSocialPostById(id)));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Share] getShareBreakdown failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve share breakdown", e.getMessage());
        }
    }

    // =========================================================================
    // COUNTS
    // =========================================================================

    @GetMapping("/{postType}/{id}/counts")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCounts(
            @PathVariable String postType,
            @PathVariable Long id) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                Post post = interactionService.getPostById(id);
                return ok("Counts retrieved", Map.of(
                        "likeCount",    post.getLikeCount(),
                        "dislikeCount", post.getDislikeCount(),
                        "commentCount", post.getCommentCount(),
                        "viewCount",    post.getViewCount(),
                        "saveCount",    post.getSaveCount(),
                        "shareCount",   post.getShareCount()
                ));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                SocialPost sp = interactionService.getSocialPostById(id);
                return ok("Counts retrieved", Map.of(
                        "likeCount",    sp.getLikeCount(),
                        "dislikeCount", sp.getDislikeCount(),
                        "commentCount", sp.getCommentCount(),
                        "viewCount",    sp.getViewCount(),
                        "saveCount",    sp.getSaveCount(),
                        "shareCount",   sp.getShareCount()
                ));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Counts] Failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve counts", e.getMessage());
        }
    }

    @GetMapping("/{postType}/{id}/counts/likes")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLikeCount(
            @PathVariable String postType,
            @PathVariable Long id) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                return ok("Like count retrieved", Map.of("likeCount", interactionService.getPostById(id).getLikeCount()));
            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                return ok("Like count retrieved", Map.of("likeCount", interactionService.getSocialPostById(id).getLikeCount()));
            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Counts] getLikeCount failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve like count", e.getMessage());
        }
    }

    @GetMapping("/{postType}/{id}/counts/dislikes")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDislikeCount(
            @PathVariable String postType,
            @PathVariable Long id) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                return ok("Dislike count retrieved", Map.of("dislikeCount", interactionService.getPostById(id).getDislikeCount()));
            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                return ok("Dislike count retrieved", Map.of("dislikeCount", interactionService.getSocialPostById(id).getDislikeCount()));
            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Counts] getDislikeCount failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve dislike count", e.getMessage());
        }
    }

    @GetMapping("/{postType}/{id}/counts/comments")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCommentCount(
            @PathVariable String postType,
            @PathVariable Long id) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                return ok("Comment count retrieved", Map.of("commentCount", interactionService.getPostById(id).getCommentCount()));
            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                return ok("Comment count retrieved", Map.of("commentCount", interactionService.getSocialPostById(id).getCommentCount()));
            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Counts] getCommentCount failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve comment count", e.getMessage());
        }
    }

    // =========================================================================
    // REACTION STATUS
    // =========================================================================

    @GetMapping("/{postType}/{id}/my-status")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyReactionStatus(
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                Post post = interactionService.getPostById(id);
                return ok("Reaction status retrieved", Map.of(
                        "liked",    interactionService.hasUserLikedPost(post, currentUser),
                        "disliked", interactionService.hasUserDislikedPost(post, currentUser),
                        "saved",    interactionService.hasSavedBroadcastPost(post, currentUser)
                ));

            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                SocialPost sp = interactionService.getSocialPostById(id);
                return ok("Reaction status retrieved", Map.of(
                        "liked",    interactionService.hasUserLikedSocialPost(sp, currentUser),
                        "disliked", interactionService.hasUserDislikedSocialPost(sp, currentUser),
                        "saved",    interactionService.hasSavedSocialPost(sp, currentUser)
                ));

            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Status] getMyReactionStatus failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve reaction status", e.getMessage());
        }
    }

    @GetMapping("/{postType}/{id}/my-status/liked")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> hasUserLiked(
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                return ok("Like status retrieved",
                        Map.of("liked", interactionService.hasUserLikedPost(interactionService.getPostById(id), currentUser)));
            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                return ok("Like status retrieved",
                        Map.of("liked", interactionService.hasUserLikedSocialPost(interactionService.getSocialPostById(id), currentUser)));
            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Status] hasUserLiked failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve like status", e.getMessage());
        }
    }

    @GetMapping("/{postType}/{id}/my-status/disliked")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> hasUserDisliked(
            @PathVariable String postType,
            @PathVariable Long id,
            @CurrentUser User currentUser) {

        try {
            if (Constant.INTERACTION_TYPE_POSTS.equals(postType)) {
                return ok("Dislike status retrieved",
                        Map.of("disliked", interactionService.hasUserDislikedPost(interactionService.getPostById(id), currentUser)));
            } else if (Constant.INTERACTION_TYPE_SOCIAL_POSTS.equals(postType)) {
                return ok("Dislike status retrieved",
                        Map.of("disliked", interactionService.hasUserDislikedSocialPost(interactionService.getSocialPostById(id), currentUser)));
            } else {
                return badPostType(postType);
            }
        } catch (Exception e) {
            log.error("[Status] hasUserDisliked failed: postType={} id={}", postType, id, e);
            return err("Failed to retrieve dislike status", e.getMessage());
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Map<String, Object> reactionBody(String key, boolean value, long likeCount, long dislikeCount) {
        return Map.of(key, value, "likeCount", likeCount, "dislikeCount", dislikeCount);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }

    private <T> ResponseEntity<ApiResponse<T>> err(String message, String detail) {
        return ResponseEntity.badRequest().body(ApiResponse.error(message, detail));
    }

    private <T> ResponseEntity<ApiResponse<T>> badPostType(String postType) {
        log.warn("[Interaction] Unknown postType='{}'. Accepted: '{}', '{}'",
                postType, Constant.INTERACTION_TYPE_POSTS, Constant.INTERACTION_TYPE_SOCIAL_POSTS);
        return err("Unsupported post type",
                "'" + postType + "' is not valid. Use 'posts' or 'social-posts'.");
    }
}