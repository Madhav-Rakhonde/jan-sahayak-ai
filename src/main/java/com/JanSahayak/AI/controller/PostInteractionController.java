package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.*;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.PostView;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.service.PostInteractionService;
import com.JanSahayak.AI.service.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for handling post interactions including views and likes.
 * Provides endpoints for recording post views and toggling post likes with atomic counter updates.
 */
@RestController
@RequestMapping("/api/posts/interactions")
@RequiredArgsConstructor
@Slf4j
public class PostInteractionController {

    private final PostInteractionService postInteractionService;
    private final PostService postService;

    // ===== POST VIEW ENDPOINTS =====

    /**
     * Record a post view by the current authenticated user
     * Prevents duplicate views within 1 hour and updates view counter atomically
     *
     * POST /api/posts/interactions/{postId}/view
     *
     * @param postId The ID of the post being viewed
     * @return ApiResponse with PostView if recorded, null if duplicate prevented
     */
    @PostMapping("/{postId}/view")
    public ResponseEntity<ApiResponse<PostViewResponse>> recordPostView(
            @PathVariable @NotNull Long postId,
            @CurrentUser User currentUser) {

        try {
            var post = postService.findById(postId);

            PostView postView = postInteractionService.recordPostViewWithCounterUpdate(post, currentUser);

            if (postView != null) {
                PostViewResponse response = PostViewResponse.builder()
                        .viewId(postView.getId())
                        .postId(postView.getPost().getId())
                        .userId(postView.getUser().getId())
                        .username(postView.getUser().getActualUsername())
                        .viewedAt(postView.getViewedAt())
                        .viewDuration(postView.getViewDuration())
                        .newViewCount(postView.getPost().getViewCount())
                        .build();

                log.debug("Post view recorded successfully for post: {} by user: {}",
                        postId, currentUser.getActualUsername());

                return ResponseEntity.ok(ApiResponse.success("Post view recorded", response));
            } else {
                log.debug("Post view prevented (duplicate within 1 hour) for post: {} by user: {}",
                        postId, currentUser.getActualUsername());

                return ResponseEntity.ok(ApiResponse.success("View already recorded recently", null));
            }

        } catch (Exception e) {
            log.error("Failed to record post view for post: {}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to record post view", e.getMessage()));
        }
    }

    /**
     * Record a post view with duration tracking
     * Allows specifying how long the user viewed the post
     *
     * POST /api/posts/interactions/{postId}/view/duration
     *
     * @param postId The ID of the post being viewed
     * @param request Request body containing view duration in seconds
     * @return ApiResponse with PostView if recorded
     */
    @PostMapping("/{postId}/view/duration")
    public ResponseEntity<ApiResponse<PostViewResponse>> recordPostViewWithDuration(
            @PathVariable @NotNull Long postId,
            @RequestBody @Valid ViewDurationRequest request,
            @CurrentUser User currentUser) {

        try {
            var post = postService.findById(postId);

            PostView postView = postInteractionService.recordPostViewWithCounterUpdate(post, currentUser);

            if (postView != null && request.getDuration() != null && request.getDuration() > 0) {
                // Update the view with duration (would need to add this method to service)
                postView.setViewDuration(request.getDuration());

                PostViewResponse response = PostViewResponse.builder()
                        .viewId(postView.getId())
                        .postId(postView.getPost().getId())
                        .userId(postView.getUser().getId())
                        .username(postView.getUser().getActualUsername())
                        .viewedAt(postView.getViewedAt())
                        .viewDuration(postView.getViewDuration())
                        .newViewCount(postView.getPost().getViewCount())
                        .isLongView(postView.isLongView())
                        .isQuickView(postView.isQuickView())
                        .build();

                log.debug("Post view with duration recorded for post: {} by user: {} ({}s)",
                        postId, currentUser.getActualUsername(), request.getDuration());

                return ResponseEntity.ok(ApiResponse.success("Post view with duration recorded", response));
            } else if (postView != null) {
                PostViewResponse response = PostViewResponse.builder()
                        .viewId(postView.getId())
                        .postId(postView.getPost().getId())
                        .userId(postView.getUser().getId())
                        .username(postView.getUser().getActualUsername())
                        .viewedAt(postView.getViewedAt())
                        .newViewCount(postView.getPost().getViewCount())
                        .build();

                return ResponseEntity.ok(ApiResponse.success("Post view recorded", response));
            } else {
                return ResponseEntity.ok(ApiResponse.success("View already recorded recently", null));
            }

        } catch (Exception e) {
            log.error("Failed to record post view with duration for post: {}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to record post view", e.getMessage()));
        }
    }

    // ===== POST LIKE ENDPOINTS =====

    /**
     * Toggle like status for a post by the current authenticated user
     * If user hasn't liked: adds like and increments counter
     * If user has liked: removes like and decrements counter
     *
     * POST /api/posts/interactions/{postId}/like
     *
     * @param postId The ID of the post to toggle like on
     * @return ApiResponse with like status and updated counts
     */
    @PostMapping("/{postId}/like")
    public ResponseEntity<ApiResponse<PostLikeResponse>> togglePostLike(
            @PathVariable @NotNull Long postId,
            @CurrentUser User currentUser) {

        try {
            var post = postService.findById(postId);

            boolean likeAdded = postInteractionService.togglePostLikeWithCounterUpdate(post, currentUser);

            PostLikeResponse response = PostLikeResponse.builder()
                    .postId(postId)
                    .userId(currentUser.getId())
                    .username(currentUser.getActualUsername())
                    .isLiked(likeAdded)
                    .newLikeCount(post.getLikeCount())
                    .action(likeAdded ? "LIKED" : "UNLIKED")
                    .build();

            String message = likeAdded ? "Post liked successfully" : "Post unliked successfully";

            log.debug("Post like toggled for post: {} by user: {} - Action: {}",
                    postId, currentUser.getActualUsername(), response.getAction());

            return ResponseEntity.ok(ApiResponse.success(message, response));

        } catch (Exception e) {
            log.error("Failed to toggle post like for post: {}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to toggle post like", e.getMessage()));
        }
    }

    // ===== BATCH INTERACTION ENDPOINTS =====

    /**
     * Record multiple post views in a single request
     * Useful for tracking views when user scrolls through multiple posts
     *
     * POST /api/posts/interactions/views/batch
     *
     * @param request Request body containing list of post IDs and optional durations
     * @return ApiResponse with batch view results
     */
    @PostMapping("/views/batch")
    public ResponseEntity<ApiResponse<BatchViewResponse>> recordBatchViews(
            @RequestBody @Valid BatchViewRequest request,
            @CurrentUser User currentUser) {

        try {
            if (request.getPostIds() == null || request.getPostIds().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Post IDs list cannot be empty"));
            }

            if (request.getPostIds().size() > 50) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Cannot process more than 50 posts at once"));
            }

            BatchViewResponse.BatchViewResponseBuilder responseBuilder = BatchViewResponse.builder();
            int successCount = 0;
            int skippedCount = 0;

            for (Long postId : request.getPostIds()) {
                try {
                    var post = postService.findById(postId);
                    PostView postView = postInteractionService.recordPostViewWithCounterUpdate(post, currentUser);

                    if (postView != null) {
                        successCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to record view for post: {} in batch operation", postId, e);
                    skippedCount++;
                }
            }

            BatchViewResponse response = responseBuilder
                    .totalRequested(request.getPostIds().size())
                    .successCount(successCount)
                    .skippedCount(skippedCount)
                    .failedCount(request.getPostIds().size() - successCount - skippedCount)
                    .build();

            log.debug("Batch view recording completed - Success: {}, Skipped: {}, Failed: {}",
                    successCount, skippedCount, response.getFailedCount());

            return ResponseEntity.ok(ApiResponse.success("Batch views processed", response));

        } catch (Exception e) {
            log.error("Failed to process batch view request", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to process batch views", e.getMessage()));
        }
    }

    // ===== INTERACTION STATUS ENDPOINTS =====

    /**
     * Get the current user's interaction status with a specific post
     * Returns whether user has liked/viewed the post and current counts
     *
     * GET /api/posts/interactions/{postId}/status
     *
     * @param postId The ID of the post to check interaction status for
     * @return ApiResponse with interaction status
     */
    @GetMapping("/{postId}/status")
    public ResponseEntity<ApiResponse<InteractionStatusResponse>> getInteractionStatus(
            @PathVariable @NotNull Long postId,
            @CurrentUser User currentUser) {

        try {
            var post = postService.findById(postId);

            // Note: Would need to add methods to service to check if user has liked/viewed
            // For now, returning basic structure
            InteractionStatusResponse response = InteractionStatusResponse.builder()
                    .postId(postId)
                    .userId(currentUser.getId())
                    .username(currentUser.getActualUsername())
                    .isLiked(false) // TODO: Implement check in service
                    .hasViewed(false) // TODO: Implement check in service
                    .currentLikeCount(post.getLikeCount())
                    .currentViewCount(post.getViewCount())
                    .currentCommentCount(post.getCommentCount())
                    .canInteract(true) // TODO: Check post status and permissions
                    .build();

            log.debug("Retrieved interaction status for post: {} and user: {}",
                    postId, currentUser.getActualUsername());

            return ResponseEntity.ok(ApiResponse.success("Interaction status retrieved", response));

        } catch (Exception e) {
            log.error("Failed to get interaction status for post: {}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get interaction status", e.getMessage()));
        }
    }
}