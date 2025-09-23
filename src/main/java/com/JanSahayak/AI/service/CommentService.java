package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.CommentCreateDto;
import com.JanSahayak.AI.DTO.CommentUpdateDto;
import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.exception.CommentNotFoundException;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.model.Comment;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.repository.CommentRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepo commentRepository;

    @Transactional(rollbackFor = Exception.class)
    public Comment createComment(@Valid CommentCreateDto commentDto, @NotNull User user, @NotNull Post post) {
        try {
            validateCommentDto(commentDto);
            PostUtility.validateUser(user);
            PostUtility.validatePost(post);

            if (post.getStatus() == null) {
                log.error("Post status is null for post ID: {}", post.getId());
                throw new ServiceException("Post status is invalid");
            }

            // Check if post allows new comments (should be interactable)
            if (!post.getStatus().isInteractable()) {
                throw new ServiceException("Cannot add comments to posts with status: " + post.getStatus().getDisplayName());
            }

            Comment comment = new Comment();
            comment.setText(commentDto.getText().trim()); // Trim whitespace
            comment.setUser(user);
            comment.setPost(post);
            comment.setCreatedAt(new Date());

            // Set parent comment if this is a reply
            if (commentDto.getParentCommentId() != null) {
                Comment parentComment = findById(commentDto.getParentCommentId());
                validateParentComment(parentComment, post);
                comment.setParentComment(parentComment);
            }

            Comment savedComment = commentRepository.save(comment);
            log.info("Comment created by user: {} (ID: {}) on post: {} (status: {}) at location: {}",
                    user.getActualUsername(), user.getId(), post.getId(),
                    post.getStatus().getDisplayName(), post.getPostPincode());

            return savedComment;
        } catch (DataAccessException ex) {
            log.error("Database error while creating comment for user: {} on post: {}",
                    user != null ? user.getActualUsername() : "null",
                    post != null ? post.getId() : "null", ex);
            throw new ServiceException("Failed to create comment due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while creating comment for user: {} on post: {}",
                    user != null ? user.getActualUsername() : "null",
                    post != null ? post.getId() : "null", ex);
            throw new ServiceException("Failed to create comment", ex);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Comment updateComment(@NotNull Long commentId, @Valid CommentUpdateDto commentDto, @NotNull User user) {
        try {
            validateCommentId(commentId);
            validateCommentDto(commentDto);
            PostUtility.validateUser(user);

            Comment comment = findById(commentId);

            // Enhanced null-safe checks
            Post post = comment.getPost();
            if (post == null) {
                log.error("Comment {} has no associated post", commentId);
                throw new ServiceException("Comment has no associated post");
            }

            if (post.getStatus() == null) {
                log.error("Post status is null for post ID: {}", post.getId());
                throw new ServiceException("Post status is invalid");
            }

            // Check if the post allows updates based on status
            if (!post.getStatus().allowsUpdates()) {
                throw new SecurityException("Cannot update comments on posts with status: " + post.getStatus().getDisplayName());
            }

            // Enhanced user ownership validation
            if (comment.getUser() == null || comment.getUser().getId() == null ||
                    !comment.getUser().getId().equals(user.getId())) {
                log.warn("Unauthorized comment update attempt by user: {} for comment: {}",
                        user.getActualUsername(), commentId);
                throw new SecurityException("Only comment owner can update comment");
            }

            comment.setText(commentDto.getText().trim());

            Comment updatedComment = commentRepository.save(comment);
            log.info("Comment updated by user: {} (ID: {}) on post with status: {}",
                    user.getActualUsername(), user.getId(), post.getStatus().getDisplayName());

            return updatedComment;
        } catch (SecurityException ex) {
            throw ex; // Re-throw security exceptions as-is
        } catch (DataAccessException ex) {
            log.error("Database error while updating comment: {} by user: {}",
                    commentId, user != null ? user.getActualUsername() : "null", ex);
            throw new ServiceException("Failed to update comment due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while updating comment: {} by user: {}",
                    commentId, user != null ? user.getActualUsername() : "null", ex);
            throw new ServiceException("Failed to update comment", ex);
        }
    }

    public Comment findById(@NotNull Long commentId) {
        try {
            validateCommentId(commentId);

            return commentRepository.findById(commentId)
                    .orElseThrow(() -> new CommentNotFoundException("Comment not found with ID: " + commentId));
        } catch (DataAccessException ex) {
            log.error("Database error while finding comment with ID: {}", commentId, ex);
            throw new ServiceException("Failed to retrieve comment due to database error", ex);
        }
    }

    // ===== UPDATED METHODS WITH CURSOR-BASED PAGINATION =====

    /**
     * Get comments by post with cursor-based pagination
     * Updated method signature to include cursor and limit parameters
     */
    public PaginatedResponse<Comment> getCommentsByPost(@NotNull Post post, Long beforeId, Integer limit) {
        try {
            PostUtility.validatePost(post);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getCommentsByPost", beforeId, limit);

            // Enhanced null-safe visibility check
            if (post.getStatus() == null || !post.getStatus().isVisible()) {
                log.warn("Attempted to retrieve comments for non-visible post: {} with status: {}",
                        post.getId(), post.getStatus() != null ? post.getStatus().getDisplayName() : "null");
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<Comment> comments;
            if (setup.hasCursor()) {
                comments = commentRepository.findByPostAndIdLessThanOrderByCreatedAtAsc(
                        post, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                comments = commentRepository.findByPostOrderByCreatedAtAsc(
                        post, setup.toPageable());
            }

            if (comments == null) {
                comments = Collections.emptyList();
            }

            PaginatedResponse<Comment> response = PaginationUtils.createCommentResponse(comments, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getCommentsByPost", comments,
                    response.isHasMore(), response.getNextCursor());

            return response;
        } catch (DataAccessException ex) {
            log.error("Database error while retrieving comments for post: {} (status: {})",
                    post.getId(), post.getStatus() != null ? post.getStatus().getDisplayName() : "null", ex);
            throw new ServiceException("Failed to retrieve comments due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while retrieving comments for post: {} (status: {})",
                    post.getId(), post.getStatus() != null ? post.getStatus().getDisplayName() : "null", ex);
            throw new ServiceException("Failed to retrieve comments", ex);
        }
    }

    /**
     * Get top level comments by post with cursor-based pagination
     * Updated method signature to include cursor and limit parameters
     */
    public PaginatedResponse<Comment> getTopLevelCommentsByPost(@NotNull Post post, Long beforeId, Integer limit) {
        try {
            PostUtility.validatePost(post);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getTopLevelCommentsByPost", beforeId, limit);

            // Enhanced null-safe visibility check
            if (post.getStatus() == null || !post.getStatus().isVisible()) {
                log.warn("Attempted to retrieve top-level comments for non-visible post: {} with status: {}",
                        post.getId(), post.getStatus() != null ? post.getStatus().getDisplayName() : "null");
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<Comment> comments;
            if (setup.hasCursor()) {
                comments = commentRepository.findTopLevelCommentsByPostAndIdLessThan(
                        post, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                comments = commentRepository.findTopLevelCommentsByPost(
                        post, setup.toPageable());
            }

            if (comments == null) {
                comments = Collections.emptyList();
            }

            PaginatedResponse<Comment> response = PaginationUtils.createCommentResponse(comments, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getTopLevelCommentsByPost", comments,
                    response.isHasMore(), response.getNextCursor());

            return response;
        } catch (DataAccessException ex) {
            log.error("Database error while retrieving top-level comments for post: {} (status: {})",
                    post.getId(), post.getStatus() != null ? post.getStatus().getDisplayName() : "null", ex);
            throw new ServiceException("Failed to retrieve top-level comments due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while retrieving top-level comments for post: {} (status: {})",
                    post.getId(), post.getStatus() != null ? post.getStatus().getDisplayName() : "null", ex);
            throw new ServiceException("Failed to retrieve top-level comments", ex);
        }
    }

    /**
     * Count comments by post - kept as-is since it returns a count, not a list
     */
    public Long countCommentsByPost(@NotNull Post post) {
        try {
            PostUtility.validatePost(post);

            // Enhanced null-safe visibility check
            if (post.getStatus() == null || !post.getStatus().isVisible()) {
                return 0L; // Return 0 for non-visible posts
            }

            Long count = commentRepository.countByPost(post);
            return count != null ? count : 0L;
        } catch (DataAccessException ex) {
            log.error("Database error while counting comments for post: {} (status: {})",
                    post.getId(), post.getStatus() != null ? post.getStatus().getDisplayName() : "null", ex);
            throw new ServiceException("Failed to count comments due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while counting comments for post: {} (status: {})",
                    post.getId(), post.getStatus() != null ? post.getStatus().getDisplayName() : "null", ex);
            throw new ServiceException("Failed to count comments", ex);
        }
    }

    /**
     * Get comments by user with cursor-based pagination
     * Updated method signature to include cursor and limit parameters
     */
    public PaginatedResponse<Comment> getCommentsByUser(@NotNull User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getCommentsByUser", beforeId, limit);

            List<Comment> userComments;
            if (setup.hasCursor()) {
                userComments = commentRepository.findByUserWithVisiblePostsAndIdLessThanOrderByCreatedAtDesc(
                        user, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                userComments = commentRepository.findByUserWithVisiblePostsOrderByCreatedAtDesc(
                        user, setup.toPageable());
            }

            if (userComments == null) {
                userComments = Collections.emptyList();
            }

            PaginatedResponse<Comment> response = PaginationUtils.createCommentResponse(userComments, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getCommentsByUser", userComments,
                    response.isHasMore(), response.getNextCursor());

            return response;
        } catch (DataAccessException ex) {
            log.error("Database error while retrieving comments for user: {}", user.getActualUsername(), ex);
            throw new ServiceException("Failed to retrieve user comments due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while retrieving comments for user: {}", user.getActualUsername(), ex);
            throw new ServiceException("Failed to retrieve user comments", ex);
        }
    }

    /**
     * Get replies to a specific comment (child comments) with cursor-based pagination
     * Updated method signature to include cursor and limit parameters
     */
    public PaginatedResponse<Comment> getCommentReplies(@NotNull Long commentId, Long beforeId, Integer limit) {
        try {
            validateCommentId(commentId);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getCommentReplies", beforeId, limit);

            Comment parentComment = findById(commentId);
            Post post = parentComment.getPost();

            // Enhanced null-safe checks
            if (post == null) {
                log.error("Parent comment {} has no associated post", commentId);
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            if (post.getStatus() == null) {
                log.error("Post status is null for post ID: {}", post.getId());
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            // Only return replies for visible posts
            if (!post.getStatus().isVisible()) {
                log.warn("Attempted to retrieve replies for comment on non-visible post: {} with status: {}",
                        post.getId(), post.getStatus().getDisplayName());
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<Comment> replies;
            if (setup.hasCursor()) {
                replies = commentRepository.findByParentCommentAndIdLessThanOrderByCreatedAtAsc(
                        parentComment, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                replies = commentRepository.findByParentCommentOrderByCreatedAtAsc(
                        parentComment, setup.toPageable());
            }

            if (replies == null) {
                replies = Collections.emptyList();
            }

            PaginatedResponse<Comment> response = PaginationUtils.createCommentResponse(replies, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getCommentReplies", replies,
                    response.isHasMore(), response.getNextCursor());

            return response;
        } catch (DataAccessException ex) {
            log.error("Database error while retrieving replies for comment: {}", commentId, ex);
            throw new ServiceException("Failed to retrieve comment replies due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while retrieving replies for comment: {}", commentId, ex);
            throw new ServiceException("Failed to retrieve comment replies", ex);
        }
    }

    // ===== ADDITIONAL PAGINATED METHODS =====

    /**
     * Get all comments with cursor-based pagination
     * New method for admin/management use cases
     */
    public PaginatedResponse<Comment> getAllComments(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllComments", beforeId, limit);

            List<Comment> comments;
            if (setup.hasCursor()) {
                comments = commentRepository.findByIdLessThanOrderByCreatedAtDesc(
                        setup.getSanitizedCursor(), setup.toPageable());
            } else {
                comments = commentRepository.findAllByOrderByCreatedAtDesc(
                        setup.toPageable());
            }

            if (comments == null) {
                comments = Collections.emptyList();
            }

            PaginatedResponse<Comment> response = PaginationUtils.createCommentResponse(comments, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getAllComments", comments,
                    response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception ex) {
            log.error("Unexpected error while retrieving all comments", ex);
            throw new ServiceException("Failed to retrieve all comments", ex);
        }
    }

    /**
     * Get recent comments with cursor-based pagination
     * New method for activity feeds
     */
    public PaginatedResponse<Comment> getRecentComments(Date fromDate, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getRecentComments", beforeId, limit);

            if (fromDate == null) {
                fromDate = new Date(System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)); // Last 7 days
            }

            List<Comment> comments;
            if (setup.hasCursor()) {
                comments = commentRepository.findByCreatedAtAfterAndIdLessThanOrderByCreatedAtDesc(
                        fromDate, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                comments = commentRepository.findByCreatedAtAfterOrderByCreatedAtDesc(
                        fromDate, setup.toPageable());
            }

            if (comments == null) {
                comments = Collections.emptyList();
            }

            PaginatedResponse<Comment> response = PaginationUtils.createCommentResponse(comments, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getRecentComments", comments,
                    response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception ex) {
            log.error("Unexpected error while retrieving recent comments", ex);
            throw new ServiceException("Failed to retrieve recent comments", ex);
        }
    }

    /**
     * Search comments by content with cursor-based pagination
     * New method for comment search functionality
     */
    public PaginatedResponse<Comment> searchComments(String searchTerm, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("searchComments", beforeId, limit);

            if (searchTerm == null || searchTerm.trim().isEmpty()) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            String cleanSearchTerm = searchTerm.trim().toLowerCase();

            List<Comment> comments;
            if (setup.hasCursor()) {
                comments = commentRepository.findByTextContainingIgnoreCaseAndIdLessThanOrderByCreatedAtDesc(
                        cleanSearchTerm, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                comments = commentRepository.findByTextContainingIgnoreCaseOrderByCreatedAtDesc(
                        cleanSearchTerm, setup.toPageable());
            }

            if (comments == null) {
                comments = Collections.emptyList();
            }

            // Filter to only include comments on visible posts
            List<Comment> visibleComments = comments.stream()
                    .filter(comment -> comment.getPost() != null &&
                            comment.getPost().getStatus() != null &&
                            comment.getPost().getStatus().isVisible())
                    .collect(Collectors.toList());

            PaginatedResponse<Comment> response = PaginationUtils.createCommentResponse(visibleComments, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("searchComments", visibleComments,
                    response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception ex) {
            log.error("Unexpected error while searching comments with term: {}", searchTerm, ex);
            throw new ServiceException("Failed to search comments", ex);
        }
    }

    // ===== PRIVATE VALIDATION METHODS =====

    private void validateCommentId(Long commentId) {
        if (commentId == null || commentId <= 0) {
            throw new IllegalArgumentException("Comment ID must be a positive number");
        }
    }

    private void validateCommentDto(Object commentDto) {
        if (commentDto == null) {
            throw new IllegalArgumentException("Comment data cannot be null");
        }

        if (commentDto instanceof CommentCreateDto) {
            CommentCreateDto dto = (CommentCreateDto) commentDto;
            validateCommentText(dto.getText());
        }

        if (commentDto instanceof CommentUpdateDto) {
            CommentUpdateDto dto = (CommentUpdateDto) commentDto;
            validateCommentText(dto.getText());
        }
    }

    private void validateCommentText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Comment text cannot be empty");
        }
        if (text.length() > Constant.MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Comment text exceeds maximum length of " + Constant.MAX_COMMENT_LENGTH + " characters");
        }
    }

    private void validateParentComment(Comment parentComment, Post post) {
        if (parentComment == null) {
            throw new CommentNotFoundException("Parent comment not found");
        }

        if (parentComment.getPost() == null) {
            throw new IllegalArgumentException("Parent comment has no associated post");
        }

        if (parentComment.getPost().getId() == null || post.getId() == null) {
            throw new IllegalArgumentException("Invalid post IDs for parent comment validation");
        }

        if (!parentComment.getPost().getId().equals(post.getId())) {
            throw new IllegalArgumentException("Parent comment must belong to the same post");
        }

        // Enhanced null-safe check for parent comment's post status
        if (parentComment.getPost().getStatus() == null) {
            throw new IllegalArgumentException("Parent comment's post has invalid status");
        }

        // Ensure parent comment's post is also in a valid state
        if (!parentComment.getPost().getStatus().isInteractable()) {
            throw new IllegalArgumentException("Cannot reply to comments on posts with status: " +
                    parentComment.getPost().getStatus().getDisplayName());
        }
    }
}