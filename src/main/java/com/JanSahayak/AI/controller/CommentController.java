package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.CommentCreateDto;
import com.JanSahayak.AI.DTO.CommentDto;
import com.JanSahayak.AI.DTO.CommentUpdateDto;
import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.exception.*;
import com.JanSahayak.AI.model.Comment;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.service.CommentService;
import com.JanSahayak.AI.service.PostService;
import com.JanSahayak.AI.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.JanSahayak.AI.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentService commentService;
    private final PostService postService;
    private final UserService userService;

    // ===== Comment Creation Methods =====

    /**
     * Create a new comment on a post - Updated to return CommentDto to prevent LazyInitializationException
     */
    @PostMapping("/posts/{postId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CommentDto>> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateDto commentDto,
            @CurrentUser User currentUser) {
        try {
            Post post = postService.findById(postId);

            CommentDto comment = commentService.createComment(commentDto, currentUser, post);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Comment created successfully", comment));
        } catch (CommentNotFoundException | PostNotFoundException | UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Resource not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error creating comment for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to create comment"));
        }
    }

    // ===== Comment Update Methods =====

    /**
     * Update an existing comment
     */
    @PutMapping("/{commentId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Comment>> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateDto commentDto,
            @CurrentUser User currentUser) {
        try {

            Comment updatedComment = commentService.updateComment(commentId, commentDto, currentUser);

            return ResponseEntity.ok(ApiResponse.success("Comment updated successfully", updatedComment));
        } catch (CommentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Comment not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error updating comment: {}", commentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to update comment"));
        }
    }

    // ===== Comment Query Methods =====

    /**
     * Get a comment by ID
     */
    @GetMapping("/{commentId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Comment>> getCommentById(@PathVariable Long commentId) {
        try {
            Comment comment = commentService.findById(commentId);
            return ResponseEntity.ok(ApiResponse.success("Comment found", comment));
        } catch (CommentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Comment not found", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting comment: {}", commentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get comment"));
        }
    }

    /**
     * Get all comments for a post with cursor-based pagination - Updated to return CommentDto to prevent LazyInitializationException
     */
    @GetMapping("/posts/{postId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getCommentsByPost(
            @PathVariable Long postId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        try {
            Post post = postService.findById(postId);

            PaginatedResponse<CommentDto> comments = commentService.getCommentsByPost(post, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Comments retrieved successfully", comments));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting comments for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get comments"));
        }
    }

    /**
     * Get top-level comments for a post with cursor-based pagination - Updated to return CommentDto to prevent LazyInitializationException
     */
    @GetMapping("/posts/{postId}/top-level")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getTopLevelCommentsByPost(
            @PathVariable Long postId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        try {
            Post post = postService.findById(postId);

            PaginatedResponse<CommentDto> comments = commentService.getTopLevelCommentsByPost(post, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Top-level comments retrieved successfully", comments));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting top-level comments for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get top-level comments"));
        }
    }

    /**
     * Count comments for a post
     */
    @GetMapping("/posts/{postId}/count")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Long>> countCommentsByPost(@PathVariable Long postId) {
        try {
            Post post = postService.findById(postId);

            Long count = commentService.countCommentsByPost(post);

            return ResponseEntity.ok(ApiResponse.success("Comment count retrieved successfully", count));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error counting comments for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to count comments"));
        }
    }

    /**
     * Get comments by user with cursor-based pagination - Updated to return CommentDto to prevent LazyInitializationException
     */
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getCommentsByUser(
            @PathVariable Long userId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        try {
            User user = userService.findById(userId);

            PaginatedResponse<CommentDto> comments = commentService.getCommentsByUser(user, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("User comments retrieved successfully", comments));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting comments by user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get user comments"));
        }
    }

    /**
     * Get current user's comments with cursor-based pagination - Updated to return CommentDto to prevent LazyInitializationException
     */
    @GetMapping("/my-comments")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getMyComments(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User currentUser) {
        try {

            PaginatedResponse<CommentDto> comments = commentService.getCommentsByUser(currentUser, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Your comments retrieved successfully", comments));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting current user's comments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get your comments"));
        }
    }

    /**
     * Get replies to a comment with cursor-based pagination - Updated to return CommentDto to prevent LazyInitializationException
     */
    @GetMapping("/{commentId}/replies")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getCommentReplies(
            @PathVariable Long commentId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        try {
            PaginatedResponse<CommentDto> replies = commentService.getCommentReplies(commentId, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Comment replies retrieved successfully", replies));
        } catch (CommentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Comment not found", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting replies for comment: {}", commentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get comment replies"));
        }
    }

    // ===== Admin Methods =====

    /**
     * Get all comments with cursor-based pagination (Admin only) - Updated to return CommentDto to prevent LazyInitializationException
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getAllComments(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        try {
            PaginatedResponse<CommentDto> comments = commentService.getAllComments(beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("All comments retrieved successfully", comments));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting all comments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get all comments"));
        }
    }

    /**
     * Get recent comments with cursor-based pagination (Admin/Department) - Updated to return CommentDto to prevent LazyInitializationException
     */
    @GetMapping("/admin/recent")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_DEPARTMENT')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> getRecentComments(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date fromDate,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        try {
            PaginatedResponse<CommentDto> comments = commentService.getRecentComments(fromDate, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Recent comments retrieved successfully", comments));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting recent comments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get recent comments"));
        }
    }

    /**
     * Search comments by content with cursor-based pagination - Added new endpoint that returns CommentDto
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentDto>>> searchComments(
            @RequestParam String searchTerm,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        try {
            PaginatedResponse<CommentDto> comments = commentService.searchComments(searchTerm, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Comments search completed successfully", comments));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error searching comments with term: {}", searchTerm, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to search comments"));
        }
    }
}