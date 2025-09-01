package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.CommentCreateDto;
import com.JanSahayak.AI.DTO.CommentUpdateDto;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.exception.CommentNotFoundException;
import com.JanSahayak.AI.exception.PostNotFoundException;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.UserNotFoundException;
import com.JanSahayak.AI.model.Comment;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommentRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;
import java.util.Collections;
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
            validateUser(user);
            validatePost(post);

            // Enhanced null-safe check for post status
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
                    user.getUsername(), user.getId(), post.getId(),
                    post.getStatus().getDisplayName(), post.getLocation());

            return savedComment;
        } catch (DataAccessException ex) {
            log.error("Database error while creating comment for user: {} on post: {}",
                    user != null ? user.getUsername() : "null",
                    post != null ? post.getId() : "null", ex);
            throw new ServiceException("Failed to create comment due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while creating comment for user: {} on post: {}",
                    user != null ? user.getUsername() : "null",
                    post != null ? post.getId() : "null", ex);
            throw new ServiceException("Failed to create comment", ex);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Comment updateComment(@NotNull Long commentId, @Valid CommentUpdateDto commentDto, @NotNull User user) {
        try {
            validateCommentId(commentId);
            validateCommentDto(commentDto);
            validateUser(user);

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
                        user.getUsername(), commentId);
                throw new SecurityException("Only comment owner can update comment");
            }

            comment.setText(commentDto.getText().trim());

            Comment updatedComment = commentRepository.save(comment);
            log.info("Comment updated by user: {} (ID: {}) on post with status: {}",
                    user.getUsername(), user.getId(), post.getStatus().getDisplayName());

            return updatedComment;
        } catch (SecurityException ex) {
            throw ex; // Re-throw security exceptions as-is
        } catch (DataAccessException ex) {
            log.error("Database error while updating comment: {} by user: {}",
                    commentId, user != null ? user.getUsername() : "null", ex);
            throw new ServiceException("Failed to update comment due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while updating comment: {} by user: {}",
                    commentId, user != null ? user.getUsername() : "null", ex);
            throw new ServiceException("Failed to update comment", ex);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(@NotNull Long commentId, @NotNull User user) {
        try {
            validateCommentId(commentId);
            validateUser(user);

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

            // Check if the post allows updates (deletions) based on status
            if (!post.getStatus().allowsUpdates() && !isAdmin(user)) {
                throw new SecurityException("Cannot delete comments on posts with status: " +
                        post.getStatus().getDisplayName() + " (admin override available)");
            }

            // Enhanced user ownership validation
            boolean isOwner = comment.getUser() != null && comment.getUser().getId() != null &&
                    comment.getUser().getId().equals(user.getId());

            if (!isOwner && !isAdmin(user)) {
                log.warn("Unauthorized comment deletion attempt by user: {} for comment: {}",
                        user.getUsername(), commentId);
                throw new SecurityException("Only comment owner or admin can delete comment");
            }

            commentRepository.delete(comment);
            log.info("Comment deleted by user: {} (ID: {}) from post with status: {}",
                    user.getUsername(), user.getId(), post.getStatus().getDisplayName());
        } catch (SecurityException ex) {
            throw ex; // Re-throw security exceptions as-is
        } catch (DataAccessException ex) {
            log.error("Database error while deleting comment: {} by user: {}",
                    commentId, user != null ? user.getUsername() : "null", ex);
            throw new ServiceException("Failed to delete comment due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while deleting comment: {} by user: {}",
                    commentId, user != null ? user.getUsername() : "null", ex);
            throw new ServiceException("Failed to delete comment", ex);
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

    /**
     * Get comments by post - matches documentation repository method
     */
    public List<Comment> getCommentsByPost(@NotNull Post post) {
        try {
            validatePost(post);

            // Enhanced null-safe visibility check
            if (post.getStatus() == null || !post.getStatus().isVisible()) {
                log.warn("Attempted to retrieve comments for non-visible post: {} with status: {}",
                        post.getId(), post.getStatus() != null ? post.getStatus().getDisplayName() : "null");
                return Collections.emptyList(); // Return empty list for non-visible posts
            }

            List<Comment> comments = commentRepository.findByPostOrderByCreatedAtAsc(post);
            return comments != null ? comments : Collections.emptyList();
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
     * Get top level comments by post - matches documentation repository method
     */
    public List<Comment> getTopLevelCommentsByPost(@NotNull Post post) {
        try {
            validatePost(post);

            // Enhanced null-safe visibility check
            if (post.getStatus() == null || !post.getStatus().isVisible()) {
                log.warn("Attempted to retrieve top-level comments for non-visible post: {} with status: {}",
                        post.getId(), post.getStatus() != null ? post.getStatus().getDisplayName() : "null");
                return Collections.emptyList(); // Return empty list for non-visible posts
            }

            List<Comment> comments = commentRepository.findTopLevelCommentsByPost(post);
            return comments != null ? comments : Collections.emptyList();
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
     * Count comments by post - matches documentation repository method
     */
    public Long countCommentsByPost(@NotNull Post post) {
        try {
            validatePost(post);

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
     * Get comments by user - matches documentation repository method
     */
    public List<Comment> getCommentsByUser(@NotNull User user) {
        try {
            validateUser(user);

            List<Comment> userComments = commentRepository.findByUserOrderByCreatedAtDesc(user);
            if (userComments == null) {
                return Collections.emptyList();
            }

            // Filter out comments on non-visible posts with enhanced null safety
            return userComments.stream()
                    .filter(comment -> comment != null &&
                            comment.getPost() != null &&
                            comment.getPost().getStatus() != null &&
                            comment.getPost().getStatus().isVisible())
                    .collect(Collectors.toList());

        } catch (DataAccessException ex) {
            log.error("Database error while retrieving comments for user: {}", user.getUsername(), ex);
            throw new ServiceException("Failed to retrieve user comments due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while retrieving comments for user: {}", user.getUsername(), ex);
            throw new ServiceException("Failed to retrieve user comments", ex);
        }
    }

    /**
     * Get replies to a specific comment (child comments)
     */
    public List<Comment> getCommentReplies(@NotNull Long commentId) {
        try {
            validateCommentId(commentId);

            Comment parentComment = findById(commentId);
            Post post = parentComment.getPost();

            // Enhanced null-safe checks
            if (post == null) {
                log.error("Parent comment {} has no associated post", commentId);
                return Collections.emptyList();
            }

            if (post.getStatus() == null) {
                log.error("Post status is null for post ID: {}", post.getId());
                return Collections.emptyList();
            }

            // Only return replies for visible posts
            if (!post.getStatus().isVisible()) {
                log.warn("Attempted to retrieve replies for comment on non-visible post: {} with status: {}",
                        post.getId(), post.getStatus().getDisplayName());
                return Collections.emptyList(); // Return empty list for non-visible posts
            }

            List<Comment> replies = commentRepository.findByParentCommentOrderByCreatedAtAsc(parentComment);
            return replies != null ? replies : Collections.emptyList();
        } catch (DataAccessException ex) {
            log.error("Database error while retrieving replies for comment: {}", commentId, ex);
            throw new ServiceException("Failed to retrieve comment replies due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while retrieving replies for comment: {}", commentId, ex);
            throw new ServiceException("Failed to retrieve comment replies", ex);
        }
    }

    // Enhanced validation helper methods with better null safety
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

    private void validateUser(User user) {
        if (user == null) {
            throw new UserNotFoundException("User cannot be null");
        }
        if (user.getId() == null) {
            throw new UserNotFoundException("User ID cannot be null");
        }
        // Additional validation for username
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new UserNotFoundException("User username cannot be null or empty");
        }
    }

    private void validatePost(Post post) {
        if (post == null) {
            throw new PostNotFoundException("Post cannot be null");
        }
        if (post.getId() == null) {
            throw new PostNotFoundException("Post ID cannot be null");
        }
        // Note: Removed status null check here since we check it in individual methods
        // where it's actually needed to provide more specific error handling
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

    private boolean isAdmin(User user) {
        try {
            return user != null &&
                    user.getRole() != null &&
                    user.getRole().getName() != null &&
                    Constant.ROLE_ADMIN.equals(user.getRole().getName());
        } catch (Exception ex) {
            log.warn("Error checking admin status for user: {}",
                    user != null ? user.getUsername() : "null", ex);
            return false;
        }
    }
}