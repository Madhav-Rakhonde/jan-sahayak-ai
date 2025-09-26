package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.UserTagSuggestionDto;
import com.JanSahayak.AI.DTO.UserTagValidationResult;
import com.JanSahayak.AI.exception.*;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.service.PostService;
import com.JanSahayak.AI.service.UserService;
import com.JanSahayak.AI.service.UserTaggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user-tagging")
@RequiredArgsConstructor
@Slf4j
public class UserTaggingController {

    private final UserTaggingService userTaggingService;
    private final PostService postService;
    private final UserService userService;

    // ===== Tag Processing Methods =====

    /**
     * Process user tags in a post (Admin/Department only)
     */
    @PostMapping("/posts/{postId}/process")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_DEPARTMENT')")
    public ResponseEntity<ApiResponse<String>> processUserTags(@PathVariable Long postId) {
        try {
            Post post = postService.findById(postId);

            userTaggingService.processUserTags(post);

            return ResponseEntity.ok(ApiResponse.success("User tags processed successfully for post"));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error processing user tags for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to process user tags"));
        }
    }

    /**
     * Add a user tag to a post
     */
    @PostMapping("/posts/{postId}/users/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> addUserTag(
            @PathVariable Long postId,
            @PathVariable Long userId,
            @CurrentUser User currentUser) {
        try {
            Post post = postService.findById(postId);
            User userToTag = userService.findById(userId);

            userTaggingService.addUserTag(post, userToTag);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("User tag added successfully"));
        } catch (PostNotFoundException | UserNotFoundException e) {
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
            log.error("Unexpected error adding user tag - post: {}, user: {}", postId, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to add user tag"));
        }
    }

    /**
     * Remove a user tag from a post
     */
    @DeleteMapping("/posts/{postId}/users/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> removeUserTag(
            @PathVariable Long postId,
            @PathVariable Long userId,
            @CurrentUser User currentUser) {
        try {
            Post post = postService.findById(postId);
            User userToRemove = userService.findById(userId);

            userTaggingService.removeUserTag(post, userToRemove);

            return ResponseEntity.ok(ApiResponse.success("User tag removed successfully"));
        } catch (PostNotFoundException | UserNotFoundException e) {
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
            log.error("Unexpected error removing user tag - post: {}, user: {}", postId, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to remove user tag"));
        }
    }

    /**
     * Update post tags when content changes (Internal use - Admin/Department only)
     */
    @PutMapping("/posts/{postId}/update-tags")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_DEPARTMENT')")
    public ResponseEntity<ApiResponse<String>> updatePostTags(
            @PathVariable Long postId,
            @RequestBody Map<String, String> request) {
        try {
            Post post = postService.findById(postId);
            String newContent = request.get("content");

            if (newContent == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Validation failed", "Content is required"));
            }

            userTaggingService.updatePostTags(post, newContent);

            return ResponseEntity.ok(ApiResponse.success("Post tags updated successfully"));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error updating post tags for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to update post tags"));
        }
    }

    // ===== Geographic-Aware Query Methods =====

    /**
     * Get posts visible to current user with cursor-based pagination
     */
    @GetMapping("/my-tagged-posts")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<Post>>> getMyTaggedPosts(
            @RequestParam(required = false) Boolean isResolved,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User currentUser) {
        try {
            PaginatedResponse<Post> posts = userTaggingService.getPostsVisibleToUser(
                    currentUser, isResolved, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Tagged posts retrieved successfully", posts));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting tagged posts for current user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get tagged posts"));
        }
    }

    /**
     * Get posts visible to a specific user with cursor-based pagination
     */
    @GetMapping("/users/{userId}/tagged-posts")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<Post>>> getUserTaggedPosts(
            @PathVariable Long userId,
            @RequestParam(required = false) Boolean isResolved,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        try {
            User user = userService.findById(userId);

            PaginatedResponse<Post> posts = userTaggingService.getPostsVisibleToUser(
                    user, isResolved, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("User tagged posts retrieved successfully", posts));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting tagged posts for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get user tagged posts"));
        }
    }

    /**
     * Get active posts visible to current user
     */
    @GetMapping("/my-active-posts")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<Post>>> getMyActiveTaggedPosts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User currentUser) {
        try {
            PaginatedResponse<Post> posts = userTaggingService.getActivePostsVisibleToUser(
                    currentUser, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Active tagged posts retrieved successfully", posts));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting active tagged posts for current user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get active tagged posts"));
        }
    }

    /**
     * Get resolved posts visible to current user
     */
    @GetMapping("/my-resolved-posts")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<Post>>> getMyResolvedTaggedPosts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User currentUser) {
        try {
            PaginatedResponse<Post> posts = userTaggingService.getResolvedPostsVisibleToUser(
                    currentUser, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Resolved tagged posts retrieved successfully", posts));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting resolved tagged posts for current user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get resolved tagged posts"));
        }
    }

    /**
     * Get tagged users in a post with cursor-based pagination
     */
    @GetMapping("/posts/{postId}/tagged-users")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> getTaggedUsersInPost(
            @PathVariable Long postId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        try {
            Post post = postService.findById(postId);

            PaginatedResponse<User> users = userTaggingService.getTaggedUsersInPost(post, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("Tagged users retrieved successfully", users));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting tagged users for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get tagged users"));
        }
    }

    /**
     * Check if current user is tagged in a post
     */
    @GetMapping("/posts/{postId}/is-tagged")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> isCurrentUserTagged(
            @PathVariable Long postId,
            @CurrentUser User currentUser) {
        try {
            Post post = postService.findById(postId);

            boolean isTagged = userTaggingService.isUserTaggedInPost(post, currentUser);

            return ResponseEntity.ok(ApiResponse.success("Tag status retrieved successfully", isTagged));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error checking tag status for post: {} and current user", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to check tag status"));
        }
    }

    /**
     * Check if a specific user is tagged in a post
     */
    @GetMapping("/posts/{postId}/users/{userId}/is-tagged")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> isUserTagged(
            @PathVariable Long postId,
            @PathVariable Long userId) {
        try {
            Post post = postService.findById(postId);
            User user = userService.findById(userId);

            boolean isTagged = userTaggingService.isUserTaggedInPost(post, user);

            return ResponseEntity.ok(ApiResponse.success("Tag status retrieved successfully", isTagged));
        } catch (PostNotFoundException | UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Resource not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error checking tag status for post: {} and user: {}", postId, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to check tag status"));
        }
    }

    // ===== Statistics and Analytics Methods =====

    /**
     * Get tagging statistics for current user
     */
    @GetMapping("/my-statistics")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getMyTaggingStatistics(@CurrentUser User currentUser) {
        try {
            Map<String, Long> statistics = userTaggingService.getTaggingStatistics(currentUser);

            return ResponseEntity.ok(ApiResponse.success("Tagging statistics retrieved successfully", statistics));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting tagging statistics for current user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get tagging statistics"));
        }
    }

    /**
     * Get tagging statistics for a specific user (Admin/Department only)
     */
    @GetMapping("/users/{userId}/statistics")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUserTaggingStatistics(@PathVariable Long userId) {
        try {
            User user = userService.findById(userId);

            Map<String, Long> statistics = userTaggingService.getTaggingStatistics(user);

            return ResponseEntity.ok(ApiResponse.success("User tagging statistics retrieved successfully", statistics));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting tagging statistics for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get user tagging statistics"));
        }
    }

    /**
     * Get comprehensive tagging analytics for current user
     */
    @GetMapping("/my-analytics")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyComprehensiveAnalytics(@CurrentUser User currentUser) {
        try {
            Map<String, Object> analytics = userTaggingService.getComprehensiveTaggingAnalytics(currentUser);

            return ResponseEntity.ok(ApiResponse.success("Comprehensive analytics retrieved successfully", analytics));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting comprehensive analytics for current user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get comprehensive analytics"));
        }
    }

    /**
     * Get comprehensive tagging analytics for a specific user (Admin/Department only)
     */
    @GetMapping("/users/{userId}/analytics")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserComprehensiveAnalytics(@PathVariable Long userId) {
        try {
            User user = userService.findById(userId);

            Map<String, Object> analytics = userTaggingService.getComprehensiveTaggingAnalytics(user);

            return ResponseEntity.ok(ApiResponse.success("User comprehensive analytics retrieved successfully", analytics));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting comprehensive analytics for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get user comprehensive analytics"));
        }
    }

    // ===== Tag Extraction and Validation Methods =====

    /**
     * Extract user tags from content
     */
    @PostMapping("/extract-tags")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> extractUserTags(@RequestBody Map<String, String> request) {
        try {
            String content = request.get("content");

            if (content == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Validation failed", "Content is required"));
            }

            List<String> extractedTags = PostUtility.extractUserTags(content);

            return ResponseEntity.ok(ApiResponse.success("User tags extracted successfully", extractedTags));
        } catch (Exception e) {
            log.error("Unexpected error extracting user tags", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to extract user tags"));
        }
    }

    /**
     * Validate user tags in content
     */
    @PostMapping("/validate-tags")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserTagValidationResult>> validateUserTags(@RequestBody Map<String, String> request) {
        try {
            String content = request.get("content");

            if (content == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Validation failed", "Content is required"));
            }

            UserTagValidationResult validationResult = userTaggingService.validateUserTags(content);

            return ResponseEntity.ok(ApiResponse.success("User tags validated successfully", validationResult));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error validating user tags", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to validate user tags"));
        }
    }

    // ===== User Tag Suggestions Methods =====

    /**
     * Get user tag suggestions with cursor-based pagination
     */
    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<UserTagSuggestionDto>>> getUserTagSuggestions(
            @RequestParam String query,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        try {
            PaginatedResponse<UserTagSuggestionDto> suggestions = userTaggingService.getUserTagSuggestions(
                    query, beforeId, limit);

            return ResponseEntity.ok(ApiResponse.success("User tag suggestions retrieved successfully", suggestions));
        } catch (ServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting user tag suggestions for query: {}", query, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", "Failed to get user tag suggestions"));
        }
    }
}