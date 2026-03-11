package com.JanSahayak.AI.service;

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

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * PostInteractionService (merged)
 *
 * Single service that owns ALL post interactions:
 *
 *  ┌──────────┬─────────────────────────────────────────────────────────────┐
 *  │ VIEWS    │ recordPostView, recordSocialPostView                        │
 *  │          │ Deduplication: 1 view per user per post per hour           │
 *  ├──────────┼─────────────────────────────────────────────────────────────┤
 *  │ LIKES    │ likePost / likeSocialPost (two-button UI)                  │
 *  │ DISLIKES │ dislikePost / dislikeSocialPost (two-button UI)            │
 *  │          │ togglePostLike / toggleSocialPostLike (legacy, kept for BC)│
 *  │          │ Like ↔ Dislike flip is done in-place on the PostLike row. │
 *  ├──────────┼─────────────────────────────────────────────────────────────┤
 *  │ SAVE     │ toggleSocialPostSave (SocialPost only)                     │
 *  │          │ toggleBroadcastPostSave (gov. broadcast Post only)         │
 *  │          │ getSavedPostsForUser / getSavedPostsForUserWithCursor      │
 *  ├──────────┼─────────────────────────────────────────────────────────────┤
 *  │ SHARE    │ recordPostShare (Post — blocks resolved issue posts)       │
 *  │          │ recordSocialPostShare                                       │
 *  │          │ getShareBreakdownForPost / getShareBreakdownForSocialPost  │
 *  ├──────────┼─────────────────────────────────────────────────────────────┤
 *  │ CLEANUP  │ cleanupForPostDeletion, cleanupForSocialPostDeletion        │
 *  │          │ cleanupForUserDeletion                                      │
 *  └──────────┴─────────────────────────────────────────────────────────────┘
 *
 * ─── HLIG v2 INTEGRATION ────────────────────────────────────────────────────
 * Every SocialPost interaction fires an @Async signal to InterestProfileService.
 * These calls never block the HTTP thread (fire-and-forget).
 * Failures are caught and logged — they never fail the actual user interaction.
 * Regular Post (gov broadcast) interactions do NOT fire HLIG signals.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * All write methods are @Transactional and race-condition safe via DB unique
 * constraints + DataIntegrityViolationException handling.
 *
 * Migration note: PostSaveShareService is now deprecated. Inject this class
 * wherever PostSaveShareService was previously used — all method signatures
 * are identical.
 */
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

    // FIX #6: CommunityService was not injected — like counters on communities
    // always stayed 0. @Lazy breaks the circular dependency:
    // CommunityService → SocialPostRepo,
    // PostInteractionService → CommunityService.
    @Lazy
    @Autowired
    private CommunityService communityService;

    // ── HLIG v2: Interest-profile signal bus ──────────────────────────────────
    // @Lazy breaks circular dependency:
    //   InterestProfileService → UserInterestProfileRepo (no loop here)
    //   PostInteractionService → InterestProfileService
    // All methods on InterestProfileService are @Async — never block this thread.
    @Lazy
    @Autowired
    private InterestProfileService interestProfileService;

    // =========================================================================
    // VIEWS — REGULAR POST
    // =========================================================================

    /**
     * Record a view on a regular Post.
     * No-op if the same user already viewed within the deduplication window (1 hour).
     */
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

    /**
     * Record a view on a SocialPost.
     * No-op if the same user already viewed within the deduplication window (1 hour).
     *
     * HLIG v2: fires onView() signal after a successful (non-duplicate) view.
     */
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

            // HLIG v2: fire async view signal (weight +0.5)
            fireHligSignal(() -> interestProfileService.onView(user.getId(), socialPost),
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
    // LIKE — REGULAR POST (two-button UI)
    // =========================================================================

    /**
     * Like a regular Post (two-button UI).
     *
     *  No reaction   → LIKE added,   likeCount++                          → true
     *  Already LIKED → LIKE removed, likeCount--                          → false
     *  Had DISLIKE   → flipped,      dislikeCount--, likeCount++          → true
     */
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
                    // Toggle off — user un-likes
                    postLikeRepository.delete(row);
                    post.decrementLikeCount();
                    postRepository.save(post);
                    log.info("[Like] Removed: post={} user={} likeCount={}", post.getId(), user.getActualUsername(), post.getLikeCount());
                    return false;
                } else {
                    // Flip DISLIKE → LIKE in-place (preserves unique constraint)
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
    // DISLIKE — REGULAR POST (two-button UI)
    // =========================================================================

    /**
     * Dislike a regular Post (two-button UI).
     *
     *  No reaction      → DISLIKE added,   dislikeCount++                 → true
     *  Already DISLIKED → DISLIKE removed, dislikeCount--                 → false
     *  Had LIKE         → flipped,         likeCount--, dislikeCount++    → true
     */
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
                    // Toggle off — user un-dislikes
                    postLikeRepository.delete(row);
                    post.decrementDislikeCount();
                    postRepository.save(post);
                    log.info("[Dislike] Removed: post={} user={} dislikeCount={}", post.getId(), user.getActualUsername(), post.getDislikeCount());
                    return false;
                } else {
                    // Flip LIKE → DISLIKE in-place
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
    // LIKE — SOCIAL POST (two-button UI)
    // =========================================================================

    /**
     * Like a SocialPost (two-button UI).
     * Same semantics as likePost().
     *
     * HLIG v2:
     *   New like    → fires onLike()   (+3.0)
     *   Un-like     → fires onUnlike() (−3.0)
     *   Flip D→L    → fires onLike()   (+3.0)
     */
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
                    // Un-like
                    postLikeRepository.delete(row);
                    socialPost.decrementLikeCount();
                    socialPostRepository.save(socialPost);
                    // HLIG: negative signal for un-like
                    fireHligSignal(() -> interestProfileService.onUnlike(user.getId(), socialPost),
                            "UNLIKE", socialPost.getId(), user.getId());
                    log.info("[Like] Removed: socialPost={} user={} likeCount={}", socialPost.getId(), user.getActualUsername(), socialPost.getLikeCount());
                    return false;
                } else {
                    // Flip DISLIKE → LIKE in-place
                    row.setReactionType(PostLike.ReactionType.LIKE);
                    postLikeRepository.save(row);
                    socialPost.decrementDislikeCount();
                    socialPost.incrementLikeCount();
                    socialPostRepository.save(socialPost);
                    // HLIG: treat flip as fresh like
                    fireHligSignal(() -> interestProfileService.onLike(user.getId(), socialPost),
                            "LIKE", socialPost.getId(), user.getId());
                    // Community counter
                    if (socialPost.getCommunity() != null) {
                        try { communityService.onLikeAdded(socialPost.getCommunity().getId()); }
                        catch (Exception e) { log.warn("[Community] onLikeAdded (flip) failed: {}", e.getMessage()); }
                    }
                    log.info("[Like] Flipped DISLIKE→LIKE: socialPost={} user={}", socialPost.getId(), user.getActualUsername());
                    return true;
                }
            } else {
                // New like
                PostLike newLike = buildLike(null, socialPost, user, PostLike.ReactionType.LIKE);
                socialPost.incrementLikeCount();
                postLikeRepository.save(newLike);
                socialPostRepository.save(socialPost);
                // HLIG: strong positive signal
                fireHligSignal(() -> interestProfileService.onLike(user.getId(), socialPost),
                        "LIKE", socialPost.getId(), user.getId());
                // Community counter
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
    // DISLIKE — SOCIAL POST (two-button UI)
    // =========================================================================

    /**
     * Dislike a SocialPost (two-button UI).
     * Same semantics as dislikePost().
     *
     * HLIG v2: fires onDislike() (−5.0) on new dislike or flip from LIKE.
     */
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
                    // Un-dislike (remove)
                    postLikeRepository.delete(row);
                    socialPost.decrementDislikeCount();
                    socialPostRepository.save(socialPost);
                    // FIX BUG-03: fire compensating signal to reverse the -5.0 dislike weight
                    fireHligSignal(() -> interestProfileService.onUnlike(user.getId(), socialPost),
                            "UN-DISLIKE", socialPost.getId(), user.getId());
                    log.info("[Dislike] Removed: socialPost={} user={} dislikeCount={}", socialPost.getId(), user.getActualUsername(), socialPost.getDislikeCount());
                    return false;
                } else {
                    // Flip LIKE → DISLIKE in-place
                    row.setReactionType(PostLike.ReactionType.DISLIKE);
                    postLikeRepository.save(row);
                    socialPost.decrementLikeCount();
                    socialPost.incrementDislikeCount();
                    socialPostRepository.save(socialPost);
                    // HLIG: strong negative signal
                    fireHligSignal(() -> interestProfileService.onDislike(user.getId(), socialPost),
                            "DISLIKE", socialPost.getId(), user.getId());
                    log.info("[Dislike] Flipped LIKE→DISLIKE: socialPost={} user={}", socialPost.getId(), user.getActualUsername());
                    return true;
                }
            } else {
                PostLike newDislike = buildLike(null, socialPost, user, PostLike.ReactionType.DISLIKE);
                socialPost.incrementDislikeCount();
                postLikeRepository.save(newDislike);
                socialPostRepository.save(socialPost);
                // HLIG: strong negative signal
                fireHligSignal(() -> interestProfileService.onDislike(user.getId(), socialPost),
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
    // LIKE / DISLIKE LEGACY TOGGLES (backward compatibility — do not remove)
    // =========================================================================

    /**
     * @deprecated Use {@link #likePost(Post, User)} for the two-button UI.
     * Kept for backward compatibility with callers that relied on the original toggle.
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean togglePostLike(Post post, User user) {
        return likePost(post, user);
    }

    /**
     * @deprecated Use {@link #likeSocialPost(SocialPost, User)} for the two-button UI.
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleSocialPostLike(SocialPost socialPost, User user) {
        return likeSocialPost(socialPost, user);
    }

    /** @deprecated Use {@link #recordPostView(Post, User)} */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public PostView recordPostViewWithCounterUpdate(Post post, User user) {
        return recordPostView(post, user);
    }

    /** @deprecated Use {@link #togglePostLike(Post, User)} */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean togglePostLikeWithCounterUpdate(Post post, User user) {
        return togglePostLike(post, user);
    }

    // =========================================================================
    // REACTION STATUS CHECKS
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
    // SAVE — SOCIAL POST
    // =========================================================================

    /**
     * Toggle save / un-save for a SocialPost.
     *
     * HLIG v2: fires onSave() (+3.5) when saved, onUnsave() (−3.5) when removed.
     *
     * @return true  → post is now SAVED
     *         false → post is now UN-SAVED
     */
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
                // HLIG: un-save is a moderate negative signal
                fireHligSignal(() -> interestProfileService.onUnsave(user.getId(), socialPost),
                        "UNSAVE", socialPost.getId(), user.getId());
                log.info("[Save] Removed: socialPost={} user={} saveCount={}", socialPost.getId(), user.getActualUsername(), socialPost.getSaveCount());
                return false;
            } else {
                SavedPost sp = SavedPost.builder().user(user).socialPost(socialPost).savedAt(new Date()).build();
                socialPost.incrementSaveCount();
                savedPostRepo.save(sp);
                socialPostRepository.save(socialPost);
                // HLIG: save is a strong intent signal (intentional bookmark)
                fireHligSignal(() -> interestProfileService.onSave(user.getId(), socialPost),
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

    /**
     * Toggle save / un-save for a government broadcast Post.
     * Rejects plain citizen issue posts with a ValidationException.
     * NOTE: Gov broadcast Post saves do NOT fire HLIG signals.
     *
     * @return true  → post is now SAVED
     *         false → post is now UN-SAVED
     */
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

    /**
     * Paginated saved posts for the current user, newest first.
     * Mix of SocialPost and broadcast Post saves — check isSocialPostSave() / isBroadcastPostSave().
     */
    @Transactional(readOnly = true)
    public Page<SavedPost> getSavedPostsForUser(User user, int page, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        return savedPostRepo.findByUserOrderBySavedAtDesc(user, pageable);
    }

    /**
     * Cursor-based saved posts (id &lt; beforeId) for infinite scroll.
     * Pass null/0 for beforeId to get the first page.
     */
    @Transactional(readOnly = true)
    public Page<SavedPost> getSavedPostsForUserWithCursor(User user, Long beforeId, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(0, Math.min(size, 100));
        if (beforeId == null || beforeId <= 0) {
            return savedPostRepo.findByUserOrderBySavedAtDesc(user, pageable);
        }
        return savedPostRepo.findByUserAndIdLessThanOrderBySavedAtDesc(user, beforeId, pageable);
    }

    /**
     * Paginated SocialPost saves only — for a dedicated "Saved Social Posts" tab.
     */
    @Transactional(readOnly = true)
    public Page<SavedPost> getSavedSocialPostsForUser(User user, int page, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        return savedPostRepo.findSocialPostSavesByUserOrderBySavedAtDesc(user, pageable);
    }

    /**
     * Paginated broadcast Post saves only — for a dedicated "Saved Government Posts" tab.
     */
    @Transactional(readOnly = true)
    public Page<SavedPost> getSavedBroadcastPostsForUser(User user, int page, int size) {
        validateUser(user);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        return savedPostRepo.findBroadcastPostSavesByUserOrderBySavedAtDesc(user, pageable);
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
    // SHARE — REGULAR POST (issue / broadcast)
    // =========================================================================

    /**
     * Record a share event for a regular Post.
     * NOTE: Regular Post shares do NOT fire HLIG signals.
     *
     * Business rules enforced:
     *  - Resolved issue Posts CANNOT be shared.
     *  - Non-eligible (inactive/deleted) Posts CANNOT be shared.
     *
     * @param shareType platform used; defaults to LINK_COPY when null
     */
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

    /** Convenience overload — defaults to LINK_COPY. */
    @Transactional(rollbackFor = Exception.class)
    public PostShare recordPostShare(Post post, User user) {
        return recordPostShare(post, user, ShareType.LINK_COPY);
    }

    // =========================================================================
    // SHARE — SOCIAL POST
    // =========================================================================

    /**
     * Record a share event for a SocialPost and increment its share_count.
     *
     * HLIG v2: fires onShare() (+3.0) after a successful share.
     *
     * @param shareType platform used; defaults to LINK_COPY when null
     */
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

            // HLIG: share is a strong positive signal — user wants others to see this topic
            if (user != null) {
                fireHligSignal(() -> interestProfileService.onShare(user.getId(), socialPost),
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

    /** Convenience overload — defaults to LINK_COPY. */
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

    /** Returns Object[]{ShareType, Long count} rows per platform for a Post.
     *  FIX BUG-14: filter rows where shareType is null (legacy data) to prevent NPE/ClassCastException. */
    public List<Object[]> getShareBreakdownForPost(Post post) {
        if (post == null) return List.of();
        return postShareRepo.countByPostGroupByShareType(post)
                .stream()
                .filter(row -> row[0] != null) // skip legacy rows with null shareType
                .collect(java.util.stream.Collectors.toList());
    }

    /** Returns Object[]{ShareType, Long count} rows per platform for a SocialPost.
     *  FIX BUG-14: filter rows where shareType is null (legacy data) to prevent NPE/ClassCastException. */
    public List<Object[]> getShareBreakdownForSocialPost(SocialPost socialPost) {
        if (socialPost == null) return List.of();
        return postShareRepo.countBySocialPostGroupByShareType(socialPost)
                .stream()
                .filter(row -> row[0] != null) // skip legacy rows with null shareType
                .collect(java.util.stream.Collectors.toList());
    }

    // =========================================================================
    // HLIG v2 — "NOT INTERESTED" & "SCROLLED PAST" (new endpoints)
    // =========================================================================

    /**
     * User explicitly marks a post as "Not Interested".
     * Fires HLIG onNotInterested() signal (−8.0 — strongest negative weight).
     * Call from: RecommendationController.markNotInterested()
     */
    public void markNotInterested(SocialPost socialPost, User user) {
        validateSocialPost(socialPost);
        validateUser(user);
        fireHligSignal(() -> interestProfileService.onNotInterested(user.getId(), socialPost),
                "NOT_INTERESTED", socialPost.getId(), user.getId());
        log.info("[HLIG] Not-interested: socialPost={} user={}", socialPost.getId(), user.getId());
    }

    /**
     * User scrolled past a post without engaging.
     * Fires HLIG onScrolledPast() signal (−0.3 — mild negative weight).
     * Call from: RecommendationController.recordScrollPast()
     */
    public void recordScrollPast(SocialPost socialPost, User user) {
        validateSocialPost(socialPost);
        validateUser(user);
        fireHligSignal(() -> interestProfileService.onScrolledPast(user.getId(), socialPost),
                "SCROLL_PAST", socialPost.getId(), user.getId());
    }

    // =========================================================================
    // CLEANUP — called from deletion flows
    // =========================================================================

    /**
     * Hard-delete cleanup for a SocialPost.
     * Removes all save and share records. Soft-delete does NOT need this.
     */
    @Transactional(rollbackFor = Exception.class)
    public void cleanupForSocialPostDeletion(SocialPost socialPost) {
        if (socialPost == null) return;
        savedPostRepo.deleteAllBySocialPost(socialPost);
        postShareRepo.deleteAllBySocialPost(socialPost);
        log.info("Cleaned up saves and shares for socialPost={}", socialPost.getId());
    }

    /**
     * Hard-delete cleanup for a regular Post.
     * Removes shares and (for broadcast posts) save records.
     */
    @Transactional(rollbackFor = Exception.class)
    public void cleanupForPostDeletion(Post post) {
        if (post == null) return;
        postShareRepo.deleteAllByPost(post);
        savedPostRepo.deleteAllByPost(post);
        log.info("Cleaned up shares and saves for post={}", post.getId());
    }

    /**
     * Account deletion cleanup.
     * Removes all saves and shares created by the user.
     *
     * FIX: savedPostRepo.deleteAllByUser(user) was previously commented out,
     * which left orphaned SavedPost rows in the DB after account deletion.
     * Re-enabled — GDPR compliant.
     */
    @Transactional(rollbackFor = Exception.class)
    public void cleanupForUserDeletion(User user) {
        if (user == null) return;
        savedPostRepo.deleteAllByUser(user);   // FIX: was commented out — now active
        postShareRepo.deleteAllByUser(user);
        log.info("Cleaned up saves and shares for user={}", user.getActualUsername());
    }

    // =========================================================================
    // ById ENTRY POINTS — called by the controller
    // =========================================================================
    //
    // ROOT CAUSE FIX: The controller was calling socialPostRepo.findById() in its
    // own implicit transaction (outside the service @Transactional boundary), then
    // passing the now-DETACHED SocialPost entity into the service. When Hibernate
    // tried to INSERT a PostLike referencing that detached entity, the FK
    // (social_post_id / post_id) resolved to null → "Column 'post_id' cannot be null".
    //
    // Fix: the controller passes only the ID. These ById methods load the entity
    // with socialPostRepository.findById() INSIDE the @Transactional boundary,
    // so the entity is managed throughout the entire operation.

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

    // Read-only ById helpers for counts / status checks (controller GET endpoints)

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
        // FIX BUG-12: null-check status first to avoid NPE when getDisplayName() is called
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

    /**
     * Fire a HLIG signal safely — never throws, never blocks the caller.
     * Failures are caught, logged as WARN, and silently dropped.
     * The user's actual interaction (like/save/share) must never fail due to HLIG.
     */
    private void fireHligSignal(Runnable signal, String signalType, Long postId, Long userId) {
        try {
            signal.run();
        } catch (Exception e) {
            log.warn("[HLIG] {} signal dropped: postId={} userId={} reason={}",
                    signalType, postId, userId, e.getMessage());
        }
    }

    /**
     * Load a SocialPost by id INSIDE the current transaction.
     * Throws ResourceNotFoundException (→ 404) if not found.
     */
    private SocialPost requireSocialPost(Long id) {
        return socialPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SocialPost not found with id: " + id));
    }

    /**
     * Load a Post by id INSIDE the current transaction.
     * Throws ResourceNotFoundException (→ 404) if not found.
     */
    private Post requirePost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + id));
    }
}