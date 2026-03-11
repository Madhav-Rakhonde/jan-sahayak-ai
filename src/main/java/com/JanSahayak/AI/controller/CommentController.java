package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.*;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.service.CommentService;
import com.JanSahayak.AI.service.PostService;
import com.JanSahayak.AI.service.SocialPostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                     CommentController  /api/comments                     ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  Owns ALL comment operations for both Post and SocialPost.               ║
 * ║  Delegates 100 % of business logic to CommentService.                    ║
 * ║                                                                          ║
 * ╠═══════════════════════╦══════════╦═══════════════════════════════════════╣
 * ║ Group                 ║ Method   ║ Path                                  ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ CREATE                ║ POST     ║ /post/{postId}                        ║
 * ║                       ║ POST     ║ /social-posts/{postId}                ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ READ — all comments   ║ GET      ║ /post/{postId}                        ║
 * ║                       ║ GET      ║ /social-posts/{postId}                ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ READ — top-level only ║ GET      ║ /post/{postId}/top-level              ║
 * ║                       ║ GET      ║ /social-posts/{postId}/top-level      ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ READ — replies        ║ GET      ║ /{commentId}/replies                  ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ READ — counts         ║ GET      ║ /social-posts/{postId}/count          ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ READ — current user   ║ GET      ║ /my                                   ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ UPDATE                ║ PUT      ║ /{commentId}                          ║
 * ╠═══════════════════════╬══════════╬═══════════════════════════════════════╣
 * ║ DELETE                ║ DELETE   ║ /{commentId}                          ║
 * ╚═══════════════════════╩══════════╩═══════════════════════════════════════╝
 */
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentService    commentService;
    private final PostService       postService;
    private final SocialPostService socialPostService;

    // =========================================================================
    // CREATE
    // =========================================================================

    /**
     * Create a comment (or reply) on a regular Post.
     *
     * POST /api/comments/post/{postId}
     *
     * Side-effects handled by CommentService:
     *   • assertPostAcceptsInteractions() — rejects RESOLVED / DELETED / FLAGGED posts
     *   • checkAndPromoteIssuePost()      — geographic promotion after comment
     *   • notifyPostCommented()           — push notification (swallowed on failure)
     */
    @PostMapping("/post/{postId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CommentDto>> createCommentOnPost(
            @PathVariable @NotNull Long postId,
            @RequestBody @Valid CommentCreateDto commentDto,
            @CurrentUser User currentUser) {

        try {
            log.info("[Comment] CREATE on post={} user={}", postId, currentUser.getActualUsername());
            Post post = postService.findById(postId);
            CommentDto created = commentService.createCommentOnPost(commentDto, currentUser, post);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Comment created successfully", created));

        } catch (Exception e) {
            log.error("[Comment] CREATE failed on post={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to create comment", e.getMessage()));
        }
    }

    /**
     * Create a comment (or reply) on a SocialPost.
     *
     * POST /api/comments/social-posts/{postId}
     *
     * Side-effects handled by CommentService:
     *   • allowsComments status guard + per-post allowComments toggle guard
     *   • notifySocialPostCommented() — push notification (swallowed on failure)
     */
    @PostMapping("/social-posts/{postId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CommentDto>> createCommentOnSocialPost(
            @PathVariable @NotNull Long postId,
            @RequestBody @Valid CommentCreateDto commentDto,
            @CurrentUser User currentUser) {

        try {
            log.info("[Comment] CREATE on socialPost={} user={}", postId, currentUser.getActualUsername());
            SocialPost sp = socialPostService.findById(postId);
            CommentDto created = commentService.createCommentOnSocialPost(commentDto, currentUser, sp);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Comment created successfully", created));

        } catch (Exception e) {
            log.error("[Comment] CREATE failed on socialPost={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to create comment", e.getMessage()));
        }
    }

    // =========================================================================
    // READ — ALL COMMENTS (cursor-paginated)
    // =========================================================================

    /**
     * All comments (top-level + replies) for a regular Post, newest first.
     *
     * GET /api/comments/post/{postId}?beforeId=N&limit=20
     */
    @GetMapping("/post/{postId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getCommentsByPost(
            @PathVariable @NotNull Long postId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {

        try {
            log.debug("[Comment] LIST all for post={}", postId);
            Post post = postService.findById(postId);
            PaginatedResponse<CommentDto> comments = commentService.getCommentsByPost(post, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));

        } catch (Exception e) {
            log.error("[Comment] LIST failed for post={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve comments", e.getMessage()));
        }
    }

    /**
     * All comments for a SocialPost, newest first.
     *
     * GET /api/comments/social-posts/{postId}?beforeId=N&limit=20
     */
    @GetMapping("/social-posts/{postId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getCommentsBySocialPost(
            @PathVariable @NotNull Long postId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {

        try {
            log.debug("[Comment] LIST all for socialPost={}", postId);
            SocialPost sp = socialPostService.findById(postId);
            PaginatedResponse<CommentDto> comments = commentService.getCommentsBySocialPost(sp, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));

        } catch (Exception e) {
            log.error("[Comment] LIST failed for socialPost={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve comments", e.getMessage()));
        }
    }

    // =========================================================================
    // READ — TOP-LEVEL ONLY (no replies, for threaded UI first load)
    // =========================================================================

    /**
     * Top-level comments only (no replies) for a regular Post.
     *
     * GET /api/comments/post/{postId}/top-level?beforeId=N&limit=20
     *
     * Use this for the initial threaded comment list.
     * Fetch replies separately via GET /{commentId}/replies.
     */
    @GetMapping("/post/{postId}/top-level")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getTopLevelCommentsByPost(
            @PathVariable @NotNull Long postId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {

        try {
            log.debug("[Comment] LIST top-level for post={}", postId);
            Post post = postService.findById(postId);
            PaginatedResponse<CommentDto> comments = commentService.getTopLevelCommentsByPost(post, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Top-level comments retrieved", comments));

        } catch (Exception e) {
            log.error("[Comment] LIST top-level failed for post={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve comments", e.getMessage()));
        }
    }

    /**
     * Top-level comments only for a SocialPost.
     *
     * GET /api/comments/social-posts/{postId}/top-level?beforeId=N&limit=20
     */
    @GetMapping("/social-posts/{postId}/top-level")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getTopLevelCommentsBySocialPost(
            @PathVariable @NotNull Long postId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {

        try {
            log.debug("[Comment] LIST top-level for socialPost={}", postId);
            SocialPost sp = socialPostService.findById(postId);
            PaginatedResponse<CommentDto> comments = commentService.getTopLevelCommentsBySocialPost(sp, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Top-level comments retrieved", comments));

        } catch (Exception e) {
            log.error("[Comment] LIST top-level failed for socialPost={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve comments", e.getMessage()));
        }
    }

    // =========================================================================
    // READ — REPLIES
    // =========================================================================

    /**
     * Paginated replies for a specific comment (works for both post types —
     * the reply chain is keyed on parentCommentId, not on post type).
     *
     * GET /api/comments/{commentId}/replies?beforeId=N&limit=20
     *
     * Use this to lazy-load nested replies in a threaded UI.
     */
    @GetMapping("/{commentId}/replies")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getCommentReplies(
            @PathVariable @NotNull Long commentId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {

        try {
            log.debug("[Comment] LIST replies for comment={}", commentId);
            PaginatedResponse<CommentDto> replies = commentService.getCommentReplies(commentId, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Replies retrieved", replies));

        } catch (Exception e) {
            log.error("[Comment] LIST replies failed for comment={}", commentId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve replies", e.getMessage()));
        }
    }

    // =========================================================================
    // READ — COUNT
    // =========================================================================

    /**
     * Total comment count for a regular Post.
     *
     * GET /api/comments/post/{postId}/count
     */
    @GetMapping("/post/{postId}/count")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Long>> getCommentCountByPost(
            @PathVariable @NotNull Long postId) {

        try {
            Post post = postService.findById(postId);
            Long count = commentService.countCommentsByPost(post);
            return ResponseEntity.ok(ApiResponse.success("Comment count retrieved", count));

        } catch (Exception e) {
            log.error("[Comment] COUNT failed for post={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get comment count", e.getMessage()));
        }
    }

    /**
     * Total comment count for a SocialPost.
     *
     * GET /api/comments/social-posts/{postId}/count
     */
    @GetMapping("/social-posts/{postId}/count")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Long>> getCommentCountBySocialPost(
            @PathVariable @NotNull Long postId) {

        try {
            SocialPost sp = socialPostService.findById(postId);
            Long count = commentService.countCommentsBySocialPost(sp);
            return ResponseEntity.ok(ApiResponse.success("Comment count retrieved", count));

        } catch (Exception e) {
            log.error("[Comment] COUNT failed for socialPost={}", postId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get comment count", e.getMessage()));
        }
    }

    // =========================================================================
    // READ — CURRENT USER'S COMMENT HISTORY
    // =========================================================================

    /**
     * All comments made by the authenticated user, across all post types,
     * ordered by newest first.
     *
     * GET /api/comments/my?beforeId=N&limit=20
     *
     * Useful for a "My Activity" / profile comment history screen.
     */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getMyComments(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {

        try {
            log.debug("[Comment] LIST my comments for user={}", currentUser.getActualUsername());
            PaginatedResponse<CommentDto> comments = commentService.getCommentsByUser(currentUser, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));

        } catch (Exception e) {
            log.error("[Comment] LIST my comments failed for user={}", currentUser.getActualUsername(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve your comments", e.getMessage()));
        }
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    /**
     * Edit a comment (owner only — enforced in CommentService).
     *
     * PUT /api/comments/{commentId}
     *
     * Works for comments on both Post and SocialPost.
     * Content validation (length, prohibited words) is re-run on edit.
     */
    @PutMapping("/{commentId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CommentDto>> updateComment(
            @PathVariable @NotNull Long commentId,
            @RequestBody @Valid CommentUpdateDto commentDto,
            @CurrentUser User currentUser) {

        try {
            log.info("[Comment] UPDATE commentId={} user={}", commentId, currentUser.getActualUsername());
            CommentDto updated = CommentDto.fromComment(
                    commentService.updateComment(commentId, commentDto, currentUser));
            return ResponseEntity.ok(ApiResponse.success("Comment updated successfully", updated));

        } catch (Exception e) {
            log.error("[Comment] UPDATE failed for commentId={}", commentId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to update comment", e.getMessage()));
        }
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    /**
     * Delete a comment (owner or ADMIN — enforced in CommentService).
     *
     * DELETE /api/comments/{commentId}
     *
     * Works for comments on both Post and SocialPost.
     * Decrements the parent post's commentCount atomically inside the service.
     *
     * Returns: 204 No Content
     */
    @DeleteMapping("/{commentId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable @NotNull Long commentId,
            @CurrentUser User currentUser) {

        try {
            log.info("[Comment] DELETE commentId={} user={}", commentId, currentUser.getActualUsername());
            commentService.deleteComment(commentId, currentUser);
            return ResponseEntity.status(HttpStatus.NO_CONTENT)
                    .body(ApiResponse.success("Comment deleted successfully", null));

        } catch (Exception e) {
            log.error("[Comment] DELETE failed for commentId={}", commentId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to delete comment", e.getMessage()));
        }
    }
}