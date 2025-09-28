package com.JanSahayak.AI.service;

import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.PostLike;
import com.JanSahayak.AI.model.PostView;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.PostLikeRepo;
import com.JanSahayak.AI.repository.PostViewRepo;
import com.JanSahayak.AI.config.Constant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostInteractionService {

    private final PostRepo postRepository;
    private final PostViewRepo postViewRepository;
    private final PostLikeRepo postLikeRepository;

    // ===== METHOD 1: RECORD POST VIEW =====

    /**
     * Record a post view and update view counter atomically
     * Prevents duplicate views from same user within 1 hour
     *
     * @param post The post being viewed (must not be null)
     * @param user The user viewing the post (must not be null and active)
     * @return PostView entity if view was recorded, null if duplicate within prevention window
     * @throws ValidationException if inputs are invalid
     * @throws ServiceException if database operation fails
     */
    @Transactional(rollbackFor = Exception.class)
    public PostView recordPostViewWithCounterUpdate(Post post, User user) {
        try {
            // Step 1: Validate inputs
            PostUtility.validatePost(post);
            PostUtility.validateUser(user);

            // Step 2: Check if post is viewable
            if (!PostUtility.isPostStatusVisible(post)) {
                log.debug("Cannot record view for non-visible post: {} (status: {})",
                        post.getId(), post.getStatus().getDisplayName());
                return null;
            }

            // Step 3: Check for duplicate views within prevention window (1 hour)
            Date preventionThreshold = new Date(System.currentTimeMillis() - Constant.VIEW_DUPLICATE_PREVENTION_MILLIS);

            Optional<PostView> recentView = postViewRepository.findByPostAndUserAndViewedAtAfter(
                    post, user, preventionThreshold);

            if (recentView.isPresent()) {
                log.debug("Duplicate view prevented for user: {} on post: {} (last view: {})",
                        user.getActualUsername(), post.getId(), recentView.get().getViewedAt());
                return null;
            }

            // Step 4: Create new view record
            PostView postView = new PostView();
            postView.setPost(post);
            postView.setUser(user);
            postView.setViewedAt(new Date());

            // Step 5: Update post view counter atomically
            post.incrementViewCount();

            // Step 6: Save both entities in same transaction
            PostView savedView = postViewRepository.save(postView);
            postRepository.save(post); // Update the counter

            log.info("Recorded view for post: {} by user: {} (new count: {})",
                    post.getId(), user.getActualUsername(), post.getViewCount());

            return savedView;

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to record post view with counter update for post: {} by user: {}",
                    post != null ? post.getId() : "null",
                    user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to record post view: " + e.getMessage(), e);
        }
    }

    // ===== METHOD 2: TOGGLE POST LIKE =====

    /**
     * Toggle like status with atomic counter updates
     * If user hasn't liked: adds like and increments counter
     * If user has liked: removes like and decrements counter
     *
     * @param post The post to toggle like on (must not be null)
     * @param user The user toggling the like (must not be null and active)
     * @return true if like was added, false if like was removed
     * @throws ValidationException if inputs are invalid
     * @throws ServiceException if database operation fails
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean togglePostLikeWithCounterUpdate(Post post, User user) {
        try {
            // Step 1: Validate inputs
            PostUtility.validatePost(post);
            PostUtility.validateUser(user);

            // Step 2: Check if post allows interactions
            if (!PostUtility.isPostStatusVisible(post)) {
                throw new ValidationException("Cannot like posts with status: " + post.getStatus().getDisplayName());
            }

            if (post.getStatus() == null || !post.getStatus().isInteractable()) {
                throw new ValidationException("Post does not allow likes due to its current status");
            }

            // Step 3: Check if like already exists
            Optional<PostLike> existingLike = postLikeRepository.findByPostAndUser(post, user);

            if (existingLike.isPresent()) {
                // Like exists, remove it
                postLikeRepository.delete(existingLike.get());
                post.decrementLikeCount();
                postRepository.save(post);
                log.info("Toggled OFF like for post: {} by user: {} (new count: {})",
                        post.getId(), user.getActualUsername(), post.getLikeCount());
                return false; // Like was removed
            } else {
                // No like exists, add it
                PostLike newLike = new PostLike();
                newLike.setPost(post);
                newLike.setUser(user);
                newLike.setCreatedAt(new Date());

                post.incrementLikeCount();

                postLikeRepository.save(newLike);
                postRepository.save(post);

                log.info("Toggled ON like for post: {} by user: {} (new count: {})",
                        post.getId(), user.getActualUsername(), post.getLikeCount());
                return true; // Like was added
            }

        } catch (ValidationException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            // Handle race condition where two like requests arrive simultaneously
            log.debug("Duplicate like prevented by database constraint for user: {} on post: {}",
                    user.getActualUsername(), post.getId());
            return false;
        } catch (Exception e) {
            log.error("Failed to toggle post like with counter update for post: {} by user: {}",
                    post != null ? post.getId() : "null",
                    user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to toggle post like: " + e.getMessage(), e);
        }
    }

    // Add these new methods inside your PostInteractionService class

    /**
     * Checks if a user has liked a specific post.
     * @param post The post to check.
     * @param user The user to check.
     * @return true if the user has liked the post, false otherwise.
     */
    public boolean hasUserLikedPost(Post post, User user) {
        if (post == null || user == null) {
            return false;
        }
        return postLikeRepository.findByPostAndUser(post, user).isPresent();
    }

    /**
     * Checks if a user has viewed a specific post within the last hour.
     * @param post The post to check.
     * @param user The user to check.
     * @return true if the user has viewed the post recently, false otherwise.
     */
    public boolean hasUserViewedPostRecently(Post post, User user) {
        if (post == null || user == null) {
            return false;
        }
        Date preventionThreshold = new Date(System.currentTimeMillis() - Constant.VIEW_DUPLICATE_PREVENTION_MILLIS);
        return postViewRepository.findByPostAndUserAndViewedAtAfter(post, user, preventionThreshold).isPresent();
    }
}