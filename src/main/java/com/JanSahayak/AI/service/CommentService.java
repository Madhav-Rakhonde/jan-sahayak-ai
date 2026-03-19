package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.CommentCreateDto;
import com.JanSahayak.AI.DTO.CommentDto;
import com.JanSahayak.AI.DTO.CommentUpdateDto;
import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.exception.CommentNotFoundException;
import com.JanSahayak.AI.exception.ResourceNotFoundException;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.model.Comment;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.repository.CommentRepo;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unified CommentService that handles comments for both regular Posts and SocialPosts
 * Eliminates code duplication between comment handling logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepo commentRepository;
    private final PostRepo postRepository;
    private final SocialPostRepo socialPostRepository;
    private final ContentValidationService contentValidationService;


    @Lazy
    @Autowired
    private PostService postService;

    private final NotificationService notificationService;

    // FIX #5: CommunityService was not injected — totalCommentCount always stayed 0.
    // @Lazy breaks the circular dependency: CommunityService → SocialPostRepo,
    // CommentService → CommunityService.
    @Lazy
    @Autowired
    private CommunityService communityService;


    @Transactional(rollbackFor = Exception.class)
    public CommentDto createCommentOnPost(@Valid CommentCreateDto commentDto, @NotNull User user, @NotNull Post post) {
        try {
            validateCommentDto(commentDto);
            PostUtility.validateUser(user);
            PostUtility.validatePost(post);


            if (post.getStatus() == null || !post.getStatus().isInteractable()) {
                throw new ServiceException("Cannot add comments to posts with status: " + post.getStatus().getDisplayName());
            }

            contentValidationService.validateContent(commentDto.getText());

            Comment comment = new Comment();
            comment.setText(commentDto.getText().trim());
            comment.setUser(user);
            comment.setPost(post);
            comment.setCreatedAt(new Date());

            if (commentDto.getParentCommentId() != null) {
                Comment parentComment = findById(commentDto.getParentCommentId());
                validateParentCommentForPost(parentComment, post);
                comment.setParentComment(parentComment);
            }

            Comment savedComment = commentRepository.save(comment);
            post.incrementCommentCount();
            postRepository.save(post);

            // ── Comment notification ─────────────────────────────────────────
            try {
                notificationService.notifyPostCommented(post, savedComment, user);
            } catch (Exception e) {
                log.warn("[Notification] Failed to notify post comment: post={}: {}", post.getId(), e.getMessage());
            }
            // ────────────────────────────────────────────────────────────────

            // ── Issue post promotion check ───────────────────────────────────
            // After a successful comment, check geographic promotion thresholds.
            // Swallows exceptions so it never rolls back the comment above.
            try {
                postService.checkAndPromoteIssuePost(post.getId());
            } catch (Exception e) {
                log.warn("[Promotion] Failed after comment on post={}: {}", post.getId(), e.getMessage());
            }
            // ────────────────────────────────────────────────────────────────

            log.info("Comment created by user: {} on post: {}", user.getActualUsername(), post.getId());
            return CommentDto.fromComment(savedComment);

        } catch (DataAccessException ex) {
            log.error("Database error while creating comment for user: {} on post: {}", user.getActualUsername(), post.getId(), ex);
            throw new ServiceException("Failed to create comment due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while creating comment for user: {} on post: {}", user.getActualUsername(), post.getId(), ex);
            throw new ServiceException("Failed to create comment", ex);
        }
    }

    // ===== CREATE COMMENT - SOCIAL POST =====

    /**
     * Create a comment on a social post.
     * SocialPosts have no resolved status so no resolved-post guard is needed here.
     */
    @Transactional(rollbackFor = Exception.class)
    public CommentDto createCommentOnSocialPost(@Valid CommentCreateDto commentDto, @NotNull User user, @NotNull SocialPost socialPost) {
        try {
            validateCommentDto(commentDto);
            PostUtility.validateUser(user);
            validateSocialPost(socialPost);

            if (!socialPost.getStatus().allowsComments()) {
                throw new ServiceException("Cannot add comments to social posts with status: " +
                        socialPost.getStatus().getDisplayName());
            }

            if (!socialPost.getAllowComments()) {
                throw new ServiceException("Comments are disabled for this social post");
            }

            contentValidationService.validateContent(commentDto.getText());

            Comment comment = new Comment();
            comment.setText(commentDto.getText().trim());
            comment.setUser(user);
            comment.setSocialPost(socialPost);
            comment.setCreatedAt(new Date());

            if (commentDto.getParentCommentId() != null) {
                Comment parentComment = findById(commentDto.getParentCommentId());
                validateParentCommentForSocialPost(parentComment, socialPost);
                comment.setParentComment(parentComment);
            }

            Comment savedComment = commentRepository.save(comment);
            socialPost.incrementCommentCount();
            socialPostRepository.save(socialPost);

            // ── Comment notification ─────────────────────────────────────────
            try {
                notificationService.notifySocialPostCommented(socialPost, savedComment, user);
            } catch (Exception e) {
                log.warn("[Notification] Failed to notify social post comment: post={}: {}", socialPost.getId(), e.getMessage());
            }
            // ────────────────────────────────────────────────────────────────

            // FIX #5: Wire onCommentAdded so community totalCommentCount is incremented.
            if (socialPost.getCommunityId() != null) {
                try {
                    communityService.onCommentAdded(socialPost.getCommunityId());
                } catch (Exception e) {
                    log.warn("[Community] onCommentAdded failed for post={} community={}: {}",
                            socialPost.getId(), socialPost.getCommunityId(), e.getMessage());
                }
            }

            log.info("Comment created by user: {} on social post: {}", user.getActualUsername(), socialPost.getId());
            return CommentDto.fromComment(savedComment);

        } catch (Exception ex) {
            log.error("Error creating comment for user: {} on social post: {}", user.getActualUsername(), socialPost.getId(), ex);
            throw new ServiceException("Failed to create comment on social post", ex);
        }
    }

    // ===== UPDATE COMMENT (WORKS FOR BOTH POST TYPES) =====

    @Transactional(rollbackFor = Exception.class)
    public Comment updateComment(@NotNull Long commentId, @Valid CommentUpdateDto commentDto, @NotNull User user) {
        try {
            validateCommentId(commentId);
            validateCommentDto(commentDto);
            PostUtility.validateUser(user);

            Comment comment = findById(commentId);

            // Check if post allows updates
            if (comment.getPost() != null) {
                Post post = comment.getPost();
                if (post == null || post.getStatus() == null || !post.getStatus().allowsUpdates()) {
                    throw new SecurityException("Cannot update comments on this post.");
                }
            } else if (comment.getSocialPost() != null) {
                SocialPost socialPost = comment.getSocialPost();
                if (socialPost == null || !socialPost.getStatus().allowsComments()) {
                    throw new SecurityException("Cannot update comments on this social post.");
                }
            }

            if (comment.getUser() == null || !comment.getUser().getId().equals(user.getId())) {
                throw new SecurityException("Only the comment owner can update the comment.");
            }

            contentValidationService.validateContent(commentDto.getText());

            comment.setText(commentDto.getText().trim());
            Comment updatedComment = commentRepository.save(comment);

            log.info("Comment updated by user: {}", user.getActualUsername());
            return updatedComment;

        } catch (Exception ex) {
            log.error("Error updating comment {}: {}", commentId, ex.getMessage());
            throw new ServiceException("Failed to update comment", ex);
        }
    }

    // ===== GET COMMENTS BY POST =====

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getCommentsByPost(@NotNull Post post, Long beforeId, Integer limit) {
        try {
            PostUtility.validatePost(post);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getCommentsByPost", beforeId, limit);

            if (post.getStatus() == null || !post.getStatus().isVisible()) {
                log.warn("Attempted to retrieve comments for non-visible post: {} with status: {}",
                        post.getId(), post.getStatus() != null ? post.getStatus().getDisplayName() : "null");
                return PaginationUtils.createEmptyCommentDtoResponse(setup.getValidatedLimit());
            }

            List<Comment> comments;
            if (setup.hasCursor()) {
                comments = commentRepository.findByPostAndIdLessThanOrderByCreatedAtAsc(
                        post, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                comments = commentRepository.findByPostOrderByCreatedAtAsc(post, setup.toPageable());
            }

            List<CommentDto> commentDtos = convertCommentsToDto(comments);
            PaginatedResponse<CommentDto> response = PaginationUtils.createCommentDtoResponse(commentDtos, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getCommentsByPost", comments, response.isHasMore(), response.getNextCursor());
            return response;

        } catch (DataAccessException ex) {
            log.error("Database error while retrieving comments for post: {}", post.getId(), ex);
            throw new ServiceException("Failed to retrieve comments due to database error", ex);
        }
    }

    // ===== GET COMMENTS BY SOCIAL POST =====

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getCommentsBySocialPost(@NotNull SocialPost socialPost, Long beforeId, Integer limit) {
        try {
            validateSocialPost(socialPost);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getCommentsBySocialPost", beforeId, limit);

            if (!socialPost.isEligibleForDisplay()) {
                log.warn("Attempted to retrieve comments for non-visible social post: {}", socialPost.getId());
                return PaginationUtils.createEmptyCommentDtoResponse(setup.getValidatedLimit());
            }

            List<Comment> comments;
            if (setup.hasCursor()) {
                comments = commentRepository.findBySocialPostAndIdLessThanOrderByCreatedAtAsc(
                        socialPost, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                comments = commentRepository.findBySocialPostOrderByCreatedAtAsc(socialPost, setup.toPageable());
            }

            List<CommentDto> commentDtos = convertCommentsToDto(comments);
            PaginatedResponse<CommentDto> response = PaginationUtils.createCommentDtoResponse(commentDtos, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getCommentsBySocialPost", comments, response.isHasMore(), response.getNextCursor());
            return response;

        } catch (DataAccessException ex) {
            log.error("Database error while retrieving comments for social post: {}", socialPost.getId(), ex);
            throw new ServiceException("Failed to retrieve comments due to database error", ex);
        }
    }

    // ===== GET TOP LEVEL COMMENTS - POST =====

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getTopLevelCommentsByPost(@NotNull Post post, Long beforeId, Integer limit) {
        try {
            PostUtility.validatePost(post);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getTopLevelCommentsByPost", beforeId, limit);

            if (post.getStatus() == null || !post.getStatus().isVisible()) {
                return PaginationUtils.createEmptyCommentDtoResponse(setup.getValidatedLimit());
            }

            List<Comment> comments;
            if (setup.hasCursor()) {
                comments = commentRepository.findTopLevelCommentsByPostAndIdLessThan(
                        post, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                comments = commentRepository.findTopLevelCommentsByPost(post, setup.toPageable());
            }

            List<CommentDto> commentDtos = convertCommentsToDto(comments);
            PaginatedResponse<CommentDto> response = PaginationUtils.createCommentDtoResponse(commentDtos, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getTopLevelCommentsByPost", comments, response.isHasMore(), response.getNextCursor());
            return response;

        } catch (Exception ex) {
            log.error("Error retrieving top-level comments for post: {}", post.getId(), ex);
            throw new ServiceException("Failed to retrieve top-level comments", ex);
        }
    }

    // ===== GET TOP LEVEL COMMENTS - SOCIAL POST =====

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getTopLevelCommentsBySocialPost(@NotNull SocialPost socialPost, Long beforeId, Integer limit) {
        try {
            validateSocialPost(socialPost);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getTopLevelCommentsBySocialPost", beforeId, limit);

            if (!socialPost.isEligibleForDisplay()) {
                return PaginationUtils.createEmptyCommentDtoResponse(setup.getValidatedLimit());
            }

            List<Comment> comments;
            if (setup.hasCursor()) {
                comments = commentRepository.findTopLevelCommentsBySocialPostAndIdLessThan(
                        socialPost, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                comments = commentRepository.findTopLevelCommentsBySocialPost(socialPost, setup.toPageable());
            }

            List<CommentDto> commentDtos = convertCommentsToDto(comments);
            PaginatedResponse<CommentDto> response = PaginationUtils.createCommentDtoResponse(commentDtos, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getTopLevelCommentsBySocialPost", comments, response.isHasMore(), response.getNextCursor());
            return response;

        } catch (Exception ex) {
            log.error("Error retrieving top-level comments for social post: {}", socialPost.getId(), ex);
            throw new ServiceException("Failed to retrieve top-level comments", ex);
        }
    }

    // ===== COUNT COMMENTS =====

    public Long countCommentsByPost(@NotNull Post post) {
        try {
            PostUtility.validatePost(post);
            if (post.getStatus() == null || !post.getStatus().isVisible()) {
                return 0L;
            }
            Long count = commentRepository.countByPost(post);
            return count != null ? count : 0L;
        } catch (Exception ex) {
            log.error("Error counting comments for post: {}", post.getId(), ex);
            throw new ServiceException("Failed to count comments", ex);
        }
    }

    public Long countCommentsBySocialPost(@NotNull SocialPost socialPost) {
        try {
            validateSocialPost(socialPost);
            if (!socialPost.isEligibleForDisplay()) {
                return 0L;
            }
            Long count = commentRepository.countBySocialPost(socialPost);
            return count != null ? count : 0L;
        } catch (Exception ex) {
            log.error("Error counting comments for social post: {}", socialPost.getId(), ex);
            throw new ServiceException("Failed to count comments", ex);
        }
    }

    // ===== GET COMMENT REPLIES =====

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getCommentReplies(@NotNull Long commentId, Long beforeId, Integer limit) {
        try {
            validateCommentId(commentId);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getCommentReplies", beforeId, limit);

            Comment parentComment = findById(commentId);

            // Check if parent comment belongs to visible post
            boolean isVisible = false;
            if (parentComment.getPost() != null) {
                Post post = parentComment.getPost();
                isVisible = post.getStatus() != null && post.getStatus().isVisible();
            } else if (parentComment.getSocialPost() != null) {
                SocialPost socialPost = parentComment.getSocialPost();
                isVisible = socialPost.isEligibleForDisplay();
            }

            if (!isVisible) {
                return PaginationUtils.createEmptyCommentDtoResponse(setup.getValidatedLimit());
            }

            List<Comment> replies;
            if (setup.hasCursor()) {
                replies = commentRepository.findByParentCommentAndIdLessThanOrderByCreatedAtAsc(
                        parentComment, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                replies = commentRepository.findByParentCommentOrderByCreatedAtAsc(parentComment, setup.toPageable());
            }

            List<CommentDto> replyDtos = convertCommentsToDto(replies);
            PaginatedResponse<CommentDto> response = PaginationUtils.createCommentDtoResponse(replyDtos, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getCommentReplies", replies, response.isHasMore(), response.getNextCursor());
            return response;

        } catch (Exception ex) {
            log.error("Error retrieving replies for comment: {}", commentId, ex);
            throw new ServiceException("Failed to retrieve comment replies", ex);
        }
    }

    // ===== GET COMMENTS BY USER =====

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getCommentsByUser(@NotNull User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getCommentsByUser", beforeId, limit);

            List<Comment> userComments;
            if (setup.hasCursor()) {
                userComments = commentRepository.findByUserWithVisiblePostsAndIdLessThanOrderByCreatedAtDesc(
                        user, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                userComments = commentRepository.findByUserWithVisiblePostsOrderByCreatedAtDesc(user, setup.toPageable());
            }

            List<CommentDto> commentDtos = convertCommentsToDto(userComments);
            PaginatedResponse<CommentDto> response = PaginationUtils.createCommentDtoResponse(commentDtos, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getCommentsByUser", userComments, response.isHasMore(), response.getNextCursor());
            return response;

        } catch (Exception ex) {
            log.error("Error retrieving comments for user: {}", user.getActualUsername(), ex);
            throw new ServiceException("Failed to retrieve user comments", ex);
        }
    }

    // ===== HELPER METHODS =====

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

    private List<CommentDto> convertCommentsToDto(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return Collections.emptyList();
        }

        return comments.stream()
                .map(comment -> {
                    try {
                        return CommentDto.fromComment(comment);
                    } catch (Exception e) {
                        log.warn("Failed to convert comment {} to DTO: {}", comment.getId(), e.getMessage());
                        return null;
                    }
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    // ===== VALIDATION METHODS =====

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
            throw new IllegalArgumentException("Comment text exceeds maximum length of " +
                    Constant.MAX_COMMENT_LENGTH + " characters");
        }
    }

    private void validateSocialPost(SocialPost socialPost) {
        if (socialPost == null) {
            throw new IllegalArgumentException("Social post cannot be null");
        }
        if (socialPost.getId() == null) {
            throw new IllegalArgumentException("Social post ID cannot be null");
        }
    }

    private void validateParentCommentForPost(Comment parentComment, Post post) {
        if (parentComment == null) {
            throw new CommentNotFoundException("Parent comment not found");
        }

        if (parentComment.getPost() == null) {
            throw new IllegalArgumentException("Parent comment has no associated post");
        }

        if (!parentComment.getPost().getId().equals(post.getId())) {
            throw new IllegalArgumentException("Parent comment must belong to the same post");
        }

        if (parentComment.getPost().getStatus() == null || !parentComment.getPost().getStatus().isInteractable()) {
            throw new IllegalArgumentException("Cannot reply to comments on posts with status: " +
                    parentComment.getPost().getStatus().getDisplayName());
        }
    }

    private void validateParentCommentForSocialPost(Comment parentComment, SocialPost socialPost) {
        if (parentComment == null) {
            throw new CommentNotFoundException("Parent comment not found");
        }

        if (parentComment.getSocialPost() == null) {
            throw new IllegalArgumentException("Parent comment is not associated with a social post");
        }

        if (!parentComment.getSocialPost().getId().equals(socialPost.getId())) {
            throw new IllegalArgumentException("Parent comment must belong to the same social post");
        }

        if (!parentComment.getSocialPost().getStatus().allowsComments()) {
            throw new IllegalArgumentException("Cannot reply to comments on social posts with status: " +
                    parentComment.getSocialPost().getStatus().getDisplayName());
        }
    }
    /**
     * Delete a comment by id.
     *
     * Rules enforced:
     *   • Only the comment owner OR a user with ROLE_ADMIN may delete.
     *   • Decrements the parent post's / social-post's commentCount atomically.
     *   • Throws CommentNotFoundException (404) if the id is unknown.
     *   • Throws SecurityException (403) if the caller is not the owner/admin.
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(@NotNull Long commentId, @NotNull User currentUser) {
        try {
            validateCommentId(commentId);
            PostUtility.validateUser(currentUser);

            Comment comment = findById(commentId);

            // ── Ownership / admin guard ───────────────────────────────────────
            boolean isOwner = comment.getUser() != null &&
                    comment.getUser().getId().equals(currentUser.getId());
            boolean isAdmin = currentUser.getAuthorities() != null &&
                    currentUser.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (!isOwner && !isAdmin) {
                throw new SecurityException("You are not authorised to delete this comment");
            }
            // ─────────────────────────────────────────────────────────────────

            // ── Decrement parent entity's comment counter ─────────────────────
            if (comment.getPost() != null) {
                Post post = comment.getPost();
                post.decrementCommentCount();
                postRepository.save(post);
            } else if (comment.getSocialPost() != null) {
                SocialPost socialPost = comment.getSocialPost();
                socialPost.decrementCommentCount();
                socialPostRepository.save(socialPost);
            }
            // ─────────────────────────────────────────────────────────────────

            commentRepository.delete(comment);
            log.info("Comment {} deleted by user: {}", commentId, currentUser.getActualUsername());

        } catch (SecurityException | CommentNotFoundException e) {
            throw e;
        } catch (DataAccessException ex) {
            log.error("Database error while deleting comment: {}", commentId, ex);
            throw new ServiceException("Failed to delete comment due to database error", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while deleting comment: {}", commentId, ex);
            throw new ServiceException("Failed to delete comment", ex);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentDto createCommentOnPostById(Long postId, CommentCreateDto dto, User user) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        return createCommentOnPost(dto, user, post);
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentDto createCommentOnSocialPostById(Long socialPostId, CommentCreateDto dto, User user) {
        SocialPost sp = socialPostRepository.findById(socialPostId)
                .orElseThrow(() -> new ResourceNotFoundException("SocialPost not found: " + socialPostId));
        return createCommentOnSocialPost(dto, user, sp);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getCommentsByPostId(Long postId, Long beforeId, Integer limit) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        return getCommentsByPost(post, beforeId, limit);
    }

    /**
     * All comments for a SocialPost, cursor-paginated.
     * Controller calls this instead of loading SocialPost then calling getCommentsBySocialPost(sp, ...).
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getCommentsBySocialPostId(Long postId, Long beforeId, Integer limit) {
        SocialPost sp = socialPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("SocialPost not found: " + postId));
        return getCommentsBySocialPost(sp, beforeId, limit);
    }

    // ── Read: top-level only ─────────────────────────────────────────────────

    /**
     * Top-level comments only for a Post, cursor-paginated.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getTopLevelCommentsByPostId(Long postId, Long beforeId, Integer limit) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        return getTopLevelCommentsByPost(post, beforeId, limit);
    }

    /**
     * Top-level comments only for a SocialPost, cursor-paginated.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<CommentDto> getTopLevelCommentsBySocialPostId(Long postId, Long beforeId, Integer limit) {
        SocialPost sp = socialPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("SocialPost not found: " + postId));
        return getTopLevelCommentsBySocialPost(sp, beforeId, limit);
    }

    // ── Read: counts ─────────────────────────────────────────────────────────

    /**
     * Total comment count for a Post.
     */
    @Transactional(readOnly = true)
    public Long countCommentsByPostId(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        return countCommentsByPost(post);
    }

    /**
     * Total comment count for a SocialPost.
     */
    @Transactional(readOnly = true)
    public Long countCommentsBySocialPostId(Long postId) {
        SocialPost sp = socialPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("SocialPost not found: " + postId));
        return countCommentsBySocialPost(sp);
    }

}