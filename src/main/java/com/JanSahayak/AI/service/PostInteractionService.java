package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.*;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.ResourceNotFoundException;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.model.PostShare.ShareType;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.payload.SocialPostUtility;
import com.JanSahayak.AI.repository.*;
import com.JanSahayak.AI.config.Constant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostInteractionService {

    // ── Repositories ──────────────────────────────────────────────────────────
    private final PostRepo       postRepository;
    private final SocialPostRepo socialPostRepository;
    private final PostViewRepo   postViewRepository;
    private final PostLikeRepo   postLikeRepository;
    private final SavedPostRepo  savedPostRepo;
    private final PostShareRepo  postShareRepo;

    @Lazy
    @Autowired
    private CommunityService communityService;

    @Lazy
    @Autowired
    private InterestProfileService interestProfileService;

    // =========================================================================
    // VIEWS — REGULAR POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostView recordPostView(Post post, User user) {
        try {
            PostUtility.validatePost(post);
            PostUtility.validateUser(user);

            if (!PostUtility.isPostStatusVisible(post)) {
                log.debug("Cannot record view for non-visible post: {} (status: {})",
                        post.getId(), post.getStatus().getDisplayName());
                return null;
            }

            Date threshold = dedupeThreshold();
            Optional<PostView> recent = postViewRepository.findByPostAndUserAndViewedAtAfter(post, user, threshold);
            if (recent.isPresent()) {
                log.debug("Duplicate view prevented: post={} user={}", post.getId(), user.getActualUsername());
                return null;
            }

            PostView view = new PostView();
            view.setPost(post);
            view.setUser(user);
            view.setViewedAt(new Date());
            post.incrementViewCount();

            PostView saved = postViewRepository.save(view);
            postRepository.save(post);

            log.info("View recorded: post={} user={} viewCount={}", post.getId(), user.getActualUsername(), post.getViewCount());
            return saved;

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to record view: post={} user={}",
                    post != null ? post.getId() : "null",
                    user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to record post view: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // VIEWS — SOCIAL POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostView recordSocialPostView(SocialPost socialPost, User user) {
        try {
            SocialPostUtility.validateSocialPost(socialPost);
            SocialPostUtility.validateUser(user);

            if (!socialPost.isEligibleForDisplay()) {
                log.debug("Cannot record view for non-visible social post: {} (status: {})",
                        socialPost.getId(), socialPost.getStatus().getDisplayName());
                return null;
            }

            Date threshold = dedupeThreshold();
            Optional<PostView> recent = postViewRepository.findBySocialPostAndUserAndViewedAtAfter(socialPost, user, threshold);
            if (recent.isPresent()) {
                log.debug("Duplicate view prevented: socialPost={} user={}", socialPost.getId(), user.getActualUsername());
                return null;
            }

            PostView view = new PostView();
            view.setSocialPost(socialPost);
            view.setUser(user);
            view.setViewedAt(new Date());
            socialPost.incrementViewCount();

            PostView saved = postViewRepository.save(view);
            socialPostRepository.save(socialPost);

            fireHligSignal(() -> interestProfileService.onView(user.getId(), socialPost.getId()),
                    "VIEW", socialPost.getId(), user.getId());

            log.info("View recorded: socialPost={} user={} viewCount={}", socialPost.getId(), user.getActualUsername(), socialPost.getViewCount());
            return saved;

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to record view: socialPost={} user={}",
                    socialPost != null ? socialPost.getId() : "null",
                    user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to record social post view: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // LIKE — REGULAR POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public boolean likePost(Post post, User user) {
        try {
            PostUtility.validatePost(post);
            PostUtility.validateUser(user);
            validatePostInteractable(post);

            Optional<PostLike> existing = postLikeRepository.findByPostAndUser(post, user);
            if (existing.isPresent()) {
                PostLike row = existing.get();
                if (row.isLike()) {
                    postLikeRepository.delete(row);
                    post.decrementLikeCount();
                    postRepository.save(post);
                    log.info("[Like] Removed: post={} user={} likeCount={}", post.getId(), user.getActualUsername(), post.getLikeCount());
                    return false;
                } else {
                    row.setReactionType(PostLike.ReactionType.LIKE);
                    postLikeRepository.save(row);
                    post.decrementDislikeCount();
                    post.incrementLikeCount();
                    postRepository.save(post);
                    log.info("[Like] Flipped DISLIKE→LIKE: post={} user={}", post.getId(), user.getActualUsername());
                    return true;
                }
            } else {
                PostLike newLike = buildLike(post, null, user, PostLike.ReactionType.LIKE);
                post.incrementLikeCount();
                postLikeRepository.save(newLike);
                postRepository.save(post);
                log.info("[Like] Added: post={} user={} likeCount={}", post.getId(), user.getActualUsername(), post.getLikeCount());
                return true;
            }
        } catch (ValidationException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            log.debug("[Like] Race condition (DB): post={} user={}", post.getId(), user.getActualUsername());
            return false;
        } catch (Exception e) {
            log.error("[Like] Failed: post={} user={}", post != null ? post.getId() : "null", user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to like post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // DISLIKE — REGULAR POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public boolean dislikePost(Post post, User user) {
        try {
            PostUtility.validatePost(post);
            PostUtility.validateUser(user);
            validatePostInteractable(post);

            Optional<PostLike> existing = postLikeRepository.findByPostAndUser(post, user);
            if (existing.isPresent()) {
                PostLike row = existing.get();
                if (row.isDislike()) {
                    postLikeRepository.delete(row);
                    post.decrementDislikeCount();
                    postRepository.save(post);
                    log.info("[Dislike] Removed: post={} user={} dislikeCount={}", post.getId(), user.getActualUsername(), post.getDislikeCount());
                    return false;
                } else {
                    row.setReactionType(PostLike.ReactionType.DISLIKE);
                    postLikeRepository.save(row);
                    post.decrementLikeCount();
                    post.incrementDislikeCount();
                    postRepository.save(post);
                    log.info("[Dislike] Flipped LIKE→DISLIKE: post={} user={}", post.getId(), user.getActualUsername());
                    return true;
                }
            } else {
                PostLike newDislike = buildLike(post, null, user, PostLike.ReactionType.DISLIKE);
                post.incrementDislikeCount();
                postLikeRepository.save(newDislike);
                postRepository.save(post);
                log.info("[Dislike] Added: post={} user={} dislikeCount={}", post.getId(), user.getActualUsername(), post.getDislikeCount());
                return true;
            }
        } catch (ValidationException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            log.debug("[Dislike] Race condition (DB): post={} user={}", post.getId(), user.getActualUsername());
            return false;
        } catch (Exception e) {
            log.error("[Dislike] Failed: post={} user={}", post != null ? post.getId() : "null", user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to dislike post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // LIKE — SOCIAL POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public boolean likeSocialPost(SocialPost socialPost, User user) {
        try {
            SocialPostUtility.validateSocialPost(socialPost);
            SocialPostUtility.validateUser(user);
            validateSocialPostInteractable(socialPost);

            Optional<PostLike> existing = postLikeRepository.findBySocialPostAndUser(socialPost, user);
            if (existing.isPresent()) {
                PostLike row = existing.get();
                if (row.isLike()) {
                    postLikeRepository.delete(row);
                    socialPost.decrementLikeCount();
                    socialPostRepository.save(socialPost);
                    fireHligSignal(() -> interestProfileService.onUnlike(user.getId(), socialPost.getId()),
                            "UNLIKE", socialPost.getId(), user.getId());
                    log.info("[Like] Removed: socialPost={} user={} likeCount={}", socialPost.getId(), user.getActualUsername(), socialPost.getLikeCount());
                    return false;
                } else {
                    row.setReactionType(PostLike.ReactionType.LIKE);
                    postLikeRepository.save(row);
                    socialPost.decrementDislikeCount();
                    socialPost.incrementLikeCount();
                    socialPostRepository.save(socialPost);
                    fireHligSignal(() -> interestProfileService.onLike(user.getId(), socialPost.getId()),
                            "LIKE", socialPost.getId(), user.getId());
                    if (socialPost.getCommunity() != null) {
                        try { communityService.onLikeAdded(socialPost.getCommunity().getId()); }
                        catch (Exception e) { log.warn("[Community] onLikeAdded (flip) failed: {}", e.getMessage()); }
                    }
                    log.info("[Like] Flipped DISLIKE→LIKE: socialPost={} user={}", socialPost.getId(), user.getActualUsername());
                    return true;
                }
            } else {
                PostLike newLike = buildLike(null, socialPost, user, PostLike.ReactionType.LIKE);
                socialPost.incrementLikeCount();
                postLikeRepository.save(newLike);
                socialPostRepository.save(socialPost);
                fireHligSignal(() -> interestProfileService.onLike(user.getId(), socialPost.getId()),
                        "LIKE", socialPost.getId(), user.getId());
                if (socialPost.getCommunity() != null) {
                    try { communityService.onLikeAdded(socialPost.getCommunity().getId()); }
                    catch (Exception e) { log.warn("[Community] onLikeAdded (new) failed: {}", e.getMessage()); }
                }
                log.info("[Like] Added: socialPost={} user={} likeCount={}", socialPost.getId(), user.getActualUsername(), socialPost.getLikeCount());
                return true;
            }
        } catch (ValidationException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            log.debug("[Like] Race condition (DB): socialPost={} user={}", socialPost.getId(), user.getActualUsername());
            return false;
        } catch (Exception e) {
            log.error("[Like] Failed: socialPost={} user={}", socialPost != null ? socialPost.getId() : "null", user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to like social post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // DISLIKE — SOCIAL POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public boolean dislikeSocialPost(SocialPost socialPost, User user) {
        try {
            SocialPostUtility.validateSocialPost(socialPost);
            SocialPostUtility.validateUser(user);
            validateSocialPostInteractable(socialPost);

            Optional<PostLike> existing = postLikeRepository.findBySocialPostAndUser(socialPost, user);
            if (existing.isPresent()) {
                PostLike row = existing.get();
                if (row.isDislike()) {
                    postLikeRepository.delete(row);
                    socialPost.decrementDislikeCount();
                    socialPostRepository.save(socialPost);
                    fireHligSignal(() -> interestProfileService.onUnlike(user.getId(), socialPost.getId()),
                            "UN-DISLIKE", socialPost.getId(), user.getId());
                    log.info("[Dislike] Removed: socialPost={} user={} dislikeCount={}", socialPost.getId(), user.getActualUsername(), socialPost.getDislikeCount());
                    return false;
                } else {
                    row.setReactionType(PostLike.ReactionType.DISLIKE);
                    postLikeRepository.save(row);
                    socialPost.decrementLikeCount();
                    socialPost.incrementDislikeCount();
                    socialPostRepository.save(socialPost);
                    fireHligSignal(() -> interestProfileService.onDislike(user.getId(), socialPost.getId()),
                            "DISLIKE", socialPost.getId(), user.getId());
                    log.info("[Dislike] Flipped LIKE→DISLIKE: socialPost={} user={}", socialPost.getId(), user.getActualUsername());
                    return true;
                }
            } else {
                PostLike newDislike = buildLike(null, socialPost, user, PostLike.ReactionType.DISLIKE);
                socialPost.incrementDislikeCount();
                postLikeRepository.save(newDislike);
                socialPostRepository.save(socialPost);
                fireHligSignal(() -> interestProfileService.onDislike(user.getId(), socialPost.getId()),
                        "DISLIKE", socialPost.getId(), user.getId());
                log.info("[Dislike] Added: socialPost={} user={} dislikeCount={}", socialPost.getId(), user.getActualUsername(), socialPost.getDislikeCount());
                return true;
            }
        } catch (ValidationException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            log.debug("[Dislike] Race condition (DB): socialPost={} user={}", socialPost.getId(), user.getActualUsername());
            return false;
        } catch (Exception e) {
            log.error("[Dislike] Failed: socialPost={} user={}", socialPost != null ? socialPost.getId() : "null", user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to dislike social post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // LEGACY TOGGLES (backward compatibility)
    // =========================================================================

    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean togglePostLike(Post post, User user) {
        return likePost(post, user);
    }

    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleSocialPostLike(SocialPost socialPost, User user) {
        return likeSocialPost(socialPost, user);
    }

    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public PostView recordPostViewWithCounterUpdate(Post post, User user) {
        return recordPostView(post, user);
    }

    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean togglePostLikeWithCounterUpdate(Post post, User user) {
        return togglePostLike(post, user);
    }

    // =========================================================================
    // REACTION STATUS CHECKS — single post (used for single post detail page)
    // =========================================================================

    public boolean hasUserLikedPost(Post post, User user) {
        if (post == null || user == null) return false;
        return postLikeRepository.findByPostAndUser(post, user).map(PostLike::isLike).orElse(false);
    }

    public boolean hasUserDislikedPost(Post post, User user) {
        if (post == null || user == null) return false;
        return postLikeRepository.findByPostAndUser(post, user).map(PostLike::isDislike).orElse(false);
    }

    public boolean hasUserLikedSocialPost(SocialPost socialPost, User user) {
        if (socialPost == null || user == null) return false;
        return postLikeRepository.findBySocialPostAndUser(socialPost, user).map(PostLike::isLike).orElse(false);
    }

    public boolean hasUserDislikedSocialPost(SocialPost socialPost, User user) {
        if (socialPost == null || user == null) return false;
        return postLikeRepository.findBySocialPostAndUser(socialPost, user).map(PostLike::isDislike).orElse(false);
    }

    public boolean hasUserViewedPostRecently(Post post, User user) {
        if (post == null || user == null) return false;
        return postViewRepository.findByPostAndUserAndViewedAtAfter(post, user, dedupeThreshold()).isPresent();
    }

    public boolean hasUserViewedSocialPostRecently(SocialPost socialPost, User user) {
        if (socialPost == null || user == null) return false;
        return postViewRepository.findBySocialPostAndUserAndViewedAtAfter(socialPost, user, dedupeThreshold()).isPresent();
    }

    // =========================================================================
    // BATCH INTERACTION CHECKS — eliminates N+1 queries in feed (NEW)
    // =========================================================================

    /**
     * Returns IDs of social posts (from given list) that the user has LIKED.
     * Replaces N individual hasUserLikedSocialPost() calls with 1 query.
     */
    public Set<Long> getBatchLikedSocialPostIds(User user, List<Long> postIds) {
        if (user == null || postIds == null || postIds.isEmpty())
            return Collections.emptySet();
        return new HashSet<>(
                postLikeRepository.findLikedSocialPostIdsByUser(user.getId(), postIds));
    }

    /**
     * Returns IDs of social posts (from given list) that the user has DISLIKED.
     * Replaces N individual hasUserDislikedSocialPost() calls with 1 query.
     */
    public Set<Long> getBatchDislikedSocialPostIds(User user, List<Long> postIds) {
        if (user == null || postIds == null || postIds.isEmpty())
            return Collections.emptySet();
        return new HashSet<>(
                postLikeRepository.findDislikedSocialPostIdsByUser(user.getId(), postIds));
    }

    /**
     * Returns IDs of social posts (from given list) that the user has SAVED.
     * Replaces N individual hasSavedSocialPost() calls with 1 query.
     */
    public Set<Long> getBatchSavedSocialPostIds(User user, List<Long> postIds) {
        if (user == null || postIds == null || postIds.isEmpty())
            return Collections.emptySet();
        return new HashSet<>(
                savedPostRepo.findSavedSocialPostIdsByUser(user.getId(), postIds));
    }

    /**
     * Returns IDs of social posts (from given list) that the user has VIEWED.
     * Replaces N individual hasUserViewedSocialPostRecently() calls with 1 query.
     */
    public Set<Long> getBatchViewedSocialPostIds(User user, List<Long> postIds) {
        if (user == null || postIds == null || postIds.isEmpty())
            return Collections.emptySet();
        return new HashSet<>(
                postViewRepository.findViewedSocialPostIdsByUser(user.getId(), postIds));
    }

    // =========================================================================
    // SAVE — SOCIAL POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleSocialPostSave(SocialPost socialPost, User user) {
        validateSocialPost(socialPost);
        validateUser(user);

        if (!socialPost.isEligibleForDisplay()) {
            throw new ValidationException("Cannot save a social post with status: " + socialPost.getStatus().getDisplayName());
        }

        try {
            Optional<SavedPost> existing = savedPostRepo.findByUserAndSocialPost(user, socialPost);
            if (existing.isPresent()) {
                savedPostRepo.delete(existing.get());
                socialPost.decrementSaveCount();
                socialPostRepository.save(socialPost);
                fireHligSignal(() -> interestProfileService.onUnsave(user.getId(), socialPost.getId()),
                        "UNSAVE", socialPost.getId(), user.getId());
                log.info("[Save] Removed: socialPost={} user={} saveCount={}", socialPost.getId(), user.getActualUsername(), socialPost.getSaveCount());
                return false;
            } else {
                SavedPost sp = SavedPost.builder().user(user).socialPost(socialPost).savedAt(new Date()).build();
                socialPost.incrementSaveCount();
                savedPostRepo.save(sp);
                socialPostRepository.save(socialPost);
                fireHligSignal(() -> interestProfileService.onSave(user.getId(), socialPost.getId()),
                        "SAVE", socialPost.getId(), user.getId());
                log.info("[Save] Added: socialPost={} user={} saveCount={}", socialPost.getId(), user.getActualUsername(), socialPost.getSaveCount());
                return true;
            }
        } catch (DataIntegrityViolationException e) {
            log.debug("[Save] Race condition (DB): socialPost={} user={}", socialPost.getId(), user.getActualUsername());
            return true;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Save] Failed: socialPost={} user={}", socialPost.getId(), user.getActualUsername(), e);
            throw new ServiceException("Failed to toggle social post save: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // SAVE — GOVERNMENT BROADCAST POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleBroadcastPostSave(Post post, User user) {
        validatePost(post);
        validateUser(user);

        if (!post.isEligibleForDisplay()) {
            throw new ValidationException("Cannot save a post with status: " + post.getStatus().getDisplayName());
        }
        if (!post.isGovernmentBroadcast()) {
            throw new ValidationException("Only government broadcast posts can be saved. PostId=" + post.getId() + " is a regular issue post.");
        }

        try {
            Optional<SavedPost> existing = savedPostRepo.findByUserAndPost(user, post);
            if (existing.isPresent()) {
                savedPostRepo.delete(existing.get());
                post.decrementSaveCount();
                postRepository.save(post);
                log.info("[Save] Removed: broadcastPost={} user={} saveCount={}", post.getId(), user.getActualUsername(), post.getSaveCount());
                return false;
            } else {
                SavedPost sp = SavedPost.builder().user(user).post(post).savedAt(new Date()).build();
                post.incrementSaveCount();
                savedPostRepo.save(sp);
                postRepository.save(post);
                log.info("[Save] Added: broadcastPost={} user={} saveCount={}", post.getId(), user.getActualUsername(), post.getSaveCount());
                return true;
            }
        } catch (DataIntegrityViolationException e) {
            log.debug("[Save] Race condition (DB): broadcastPost={} user={}", post.getId(), user.getActualUsername());
            return true;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Save] Failed: broadcastPost={} user={}", post.getId(), user.getActualUsername(), e);
            throw new ServiceException("Failed to toggle broadcast post save: " + e.getMessage(), e);
        }
    }

    // ── Save status checks ────────────────────────────────────────────────────

    public boolean hasSavedSocialPost(SocialPost socialPost, User user) {
        if (socialPost == null || user == null) return false;
        return savedPostRepo.existsByUserAndSocialPost(user, socialPost);
    }

    public boolean hasSavedBroadcastPost(Post post, User user) {
        if (post == null || user == null) return false;
        return savedPostRepo.existsByUserAndPost(user, post);
    }

    // ── Saved post listing ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SavedPostDto> getSavedPostsForUser(User user, int page, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        return savedPostRepo.findByUserOrderBySavedAtDesc(user, pageable)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public Page<SavedPostDto> getSavedSocialPostsForUser(User user, int page, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        return savedPostRepo.findSocialPostSavesByUserOrderBySavedAtDesc(user, pageable)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public Page<SavedPostDto> getSavedBroadcastPostsForUser(User user, int page, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        return savedPostRepo.findBroadcastPostSavesByUserOrderBySavedAtDesc(user, pageable)
                .map(this::convertToDto);
    }

    private SavedPostDto convertToDto(SavedPost sp) {
        if (sp == null) return null;
        
        String content = "";
        String type = "unknown";
        
        if (sp.isSocialPostSave() && sp.getSocialPost() != null) {
            content = sp.getSocialPost().getContent();
            type = "social";
        } else if (sp.isBroadcastPostSave() && sp.getPost() != null) {
            content = sp.getPost().getContent();
            type = "issue";
        }

        return SavedPostDto.builder()
                .id(sp.getId())
                .userId(sp.getUserId())
                .socialPostId(sp.getSocialPostId())
                .postId(sp.getPostId())
                .savedAt(sp.getSavedAt())
                .content(content)
                .type(type)
                .build();
    }

    // ── Save counts ───────────────────────────────────────────────────────────

    public long getSaveCountForSocialPost(SocialPost socialPost) {
        if (socialPost == null) return 0L;
        return savedPostRepo.countBySocialPost(socialPost);
    }

    public long getSaveCountForBroadcastPost(Post post) {
        if (post == null) return 0L;
        return savedPostRepo.countByPost(post);
    }

    public long getTotalSavesByUser(User user) {
        if (user == null) return 0L;
        return savedPostRepo.countByUser(user);
    }

    // =========================================================================
    // SHARE — REGULAR POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordPostShare(Post post, User user, ShareType shareType) {
        validatePost(post);

        if (!post.isEligibleForDisplay()) {
            throw new ValidationException("Cannot share a post with status: " + post.getStatus().getDisplayName());
        }
        if (post.isResolved() || post.getStatus() == PostStatus.RESOLVED) {
            throw new ValidationException(
                    "Cannot share a resolved issue post. PostId=" + post.getId() +
                            " has been marked as resolved and is no longer shareable.");
        }

        try {
            PostShare share = PostShare.builder()
                    .post(post)
                    .user(user)
                    .shareType(shareType != null ? shareType : ShareType.LINK_COPY)
                    .sharedAt(new Date())
                    .build();

            post.incrementShareCount();
            PostShare saved = postShareRepo.save(share);
            postRepository.save(post);

            log.info("[Share] post={} user={} type={} shareCount={}",
                    post.getId(),
                    user != null ? user.getActualUsername() : "anonymous",
                    share.getShareType(), post.getShareCount());
            return saved;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Share] Failed: post={} user={}", post.getId(), user != null ? user.getActualUsername() : "anonymous", e);
            throw new ServiceException("Failed to record post share: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordPostShare(Post post, User user) {
        return recordPostShare(post, user, ShareType.LINK_COPY);
    }

    // =========================================================================
    // SHARE — SOCIAL POST
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordSocialPostShare(SocialPost socialPost, User user, ShareType shareType) {
        validateSocialPost(socialPost);

        if (!socialPost.isEligibleForDisplay()) {
            throw new ValidationException("Cannot share a social post with status: " + socialPost.getStatus().getDisplayName());
        }

        try {
            PostShare share = PostShare.builder()
                    .socialPost(socialPost)
                    .user(user)
                    .shareType(shareType != null ? shareType : ShareType.LINK_COPY)
                    .sharedAt(new Date())
                    .build();

            socialPost.incrementShareCount();
            PostShare saved = postShareRepo.save(share);
            socialPostRepository.save(socialPost);

            if (user != null) {
                fireHligSignal(() -> interestProfileService.onShare(user.getId(), socialPost.getId()),
                        "SHARE", socialPost.getId(), user.getId());
            }

            log.info("[Share] socialPost={} user={} type={} shareCount={}",
                    socialPost.getId(),
                    user != null ? user.getActualUsername() : "anonymous",
                    share.getShareType(), socialPost.getShareCount());
            return saved;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Share] Failed: socialPost={} user={}", socialPost.getId(), user != null ? user.getActualUsername() : "anonymous", e);
            throw new ServiceException("Failed to record social post share: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordSocialPostShare(SocialPost socialPost, User user) {
        return recordSocialPostShare(socialPost, user, ShareType.LINK_COPY);
    }

    // ── Share counts & breakdown ──────────────────────────────────────────────

    public long getShareCountForPost(Post post) {
        if (post == null) return 0L;
        return postShareRepo.countByPost(post);
    }

    public long getShareCountForSocialPost(SocialPost socialPost) {
        if (socialPost == null) return 0L;
        return postShareRepo.countBySocialPost(socialPost);
    }

    public List<Object[]> getShareBreakdownForPost(Post post) {
        if (post == null) return List.of();
        return postShareRepo.countByPostGroupByShareType(post)
                .stream()
                .filter(row -> row[0] != null)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<Object[]> getShareBreakdownForSocialPost(SocialPost socialPost) {
        if (socialPost == null) return List.of();
        return postShareRepo.countBySocialPostGroupByShareType(socialPost)
                .stream()
                .filter(row -> row[0] != null)
                .collect(java.util.stream.Collectors.toList());
    }

    // =========================================================================
    // HLIG v2 — NOT INTERESTED & SCROLLED PAST
    // =========================================================================

    public void markNotInterested(SocialPost socialPost, User user) {
        validateSocialPost(socialPost);
        validateUser(user);
        fireHligSignal(() -> interestProfileService.onNotInterested(user.getId(), socialPost.getId()),
                "NOT_INTERESTED", socialPost.getId(), user.getId());
        log.info("[HLIG] Not-interested: socialPost={} user={}", socialPost.getId(), user.getId());
    }

    public void recordScrollPast(SocialPost socialPost, User user) {
        validateSocialPost(socialPost);
        validateUser(user);
        fireHligSignal(() -> interestProfileService.onScrolledPast(user.getId(), socialPost.getId()),
                "SCROLL_PAST", socialPost.getId(), user.getId());
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public void cleanupForSocialPostDeletion(SocialPost socialPost) {
        if (socialPost == null) return;
        savedPostRepo.deleteAllBySocialPost(socialPost);
        postShareRepo.deleteAllBySocialPost(socialPost);
        log.info("Cleaned up saves and shares for socialPost={}", socialPost.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void cleanupForPostDeletion(Post post) {
        if (post == null) return;
        postShareRepo.deleteAllByPost(post);
        savedPostRepo.deleteAllByPost(post);
        log.info("Cleaned up shares and saves for post={}", post.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void cleanupForUserDeletion(User user) {
        if (user == null) return;
        savedPostRepo.deleteAllByUser(user);
        postShareRepo.deleteAllByUser(user);
        log.info("Cleaned up saves and shares for user={}", user.getActualUsername());
    }

    // =========================================================================
    // ById ENTRY POINTS
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostView recordSocialPostViewById(Long socialPostId, User user) {
        SocialPost sp = requireSocialPost(socialPostId);
        return recordSocialPostView(sp, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean likeSocialPostById(Long socialPostId, User user) {
        SocialPost sp = requireSocialPost(socialPostId);
        return likeSocialPost(sp, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean dislikeSocialPostById(Long socialPostId, User user) {
        SocialPost sp = requireSocialPost(socialPostId);
        return dislikeSocialPost(sp, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleSocialPostSaveById(Long socialPostId, User user) {
        SocialPost sp = requireSocialPost(socialPostId);
        return toggleSocialPostSave(sp, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordSocialPostShareById(Long socialPostId, User user, ShareType shareType) {
        SocialPost sp = requireSocialPost(socialPostId);
        return recordSocialPostShare(sp, user, shareType);
    }

    @Transactional(rollbackFor = Exception.class)
    public PostView recordPostViewById(Long postId, User user) {
        Post post = requirePost(postId);
        return recordPostView(post, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean likePostById(Long postId, User user) {
        Post post = requirePost(postId);
        return likePost(post, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean dislikePostById(Long postId, User user) {
        Post post = requirePost(postId);
        return dislikePost(post, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleBroadcastPostSaveById(Long postId, User user) {
        Post post = requirePost(postId);
        return toggleBroadcastPostSave(post, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordPostShareById(Long postId, User user, ShareType shareType) {
        Post post = requirePost(postId);
        return recordPostShare(post, user, shareType);
    }

    @Transactional(readOnly = true)
    public SocialPost getSocialPostById(Long id) {
        return requireSocialPost(id);
    }

    @Transactional(readOnly = true)
    public Post getPostById(Long id) {
        return requirePost(id);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Date dedupeThreshold() {
        return new Date(System.currentTimeMillis() - Constant.VIEW_DUPLICATE_PREVENTION_MILLIS);
    }

    private PostLike buildLike(Post post, SocialPost socialPost, User user, PostLike.ReactionType type) {
        PostLike like = new PostLike();
        like.setPost(post);
        like.setSocialPost(socialPost);
        like.setUser(user);
        like.setReactionType(type);
        like.setCreatedAt(new Date());
        return like;
    }

    private void validatePostInteractable(Post post) {
        PostStatus status = post.getStatus();
        if (status == null) {
            throw new ValidationException("Post has no status — cannot interact.");
        }
        if (!status.isInteractable()) {
            throw new ValidationException("Post does not allow reactions in its current status: " + status.getDisplayName());
        }
    }

    private void validateSocialPostInteractable(SocialPost socialPost) {
        if (!socialPost.isEligibleForDisplay()) {
            throw new ValidationException("Cannot interact with social post in status: " + socialPost.getStatus().getDisplayName());
        }
        if (!socialPost.getStatus().allowsLikes()) {
            throw new ValidationException("Social post does not allow reactions in its current status.");
        }
    }

    private void validateUser(User user) {
        if (user == null)         throw new ValidationException("User cannot be null");
        if (user.getId() == null) throw new ValidationException("User must be persisted (id is null)");
    }

    private void validatePost(Post post) {
        if (post == null)         throw new ValidationException("Post cannot be null");
        if (post.getId() == null) throw new ValidationException("Post must be persisted (id is null)");
    }

    private void validateSocialPost(SocialPost sp) {
        if (sp == null)         throw new ValidationException("SocialPost cannot be null");
        if (sp.getId() == null) throw new ValidationException("SocialPost must be persisted (id is null)");
    }

    private void fireHligSignal(Runnable signal, String signalType, Long postId, Long userId) {
        try {
            signal.run();
        } catch (Exception e) {
            log.warn("[HLIG] {} signal dropped: postId={} userId={} reason={}",
                    signalType, postId, userId, e.getMessage());
        }
    }

    private SocialPost requireSocialPost(Long id) {
        return socialPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SocialPost not found with id: " + id));
    }

    private Post requirePost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + id));
    }
}