package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SavedPost;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link SavedPost} — handles bookmarks for both SocialPosts
 * and government broadcast Posts.
 *
 * Naming conventions used throughout:
 *  - findByUser*          → read queries scoped to a user
 *  - existsBy*            → lightweight boolean existence checks (no entity load)
 *  - countBy*             → aggregate count queries
 *  - deleteAllBy*         → bulk hard-delete operations (all @Modifying + @Transactional)
 */
@Repository
public interface SavedPostRepo extends JpaRepository<SavedPost, Long> {

    // =========================================================================
    // LOOKUP — single-record finders
    // =========================================================================

    /**
     * Find a specific saved-social-post record for a user.
     * Used by toggleSocialPostSave() to detect existing saves before insert/delete.
     */
    Optional<SavedPost> findByUserAndSocialPost(User user, SocialPost socialPost);

    /**
     * Find a specific saved-broadcast-post record for a user.
     * Used by toggleBroadcastPostSave() to detect existing saves before insert/delete.
     */
    Optional<SavedPost> findByUserAndPost(User user, Post post);

    // =========================================================================
    // EXISTENCE CHECKS — for hasUserSaved* helpers
    // =========================================================================

    /**
     * Returns true if the user has already bookmarked this SocialPost.
     * Cheaper than findByUserAndSocialPost() because no entity is loaded.
     */
    boolean existsByUserAndSocialPost(User user, SocialPost socialPost);

    /**
     * Returns true if the user has already bookmarked this broadcast Post.
     */
    boolean existsByUserAndPost(User user, Post post);

    // =========================================================================
    // PAGINATED LISTING — for saved-posts feed
    // =========================================================================

    /**
     * All saves for a user, newest first — offset pagination.
     * Returned entities may be either SocialPost or Post saves; the caller
     * checks isSocialPostSave() / isBroadcastPostSave() to build the correct DTO.
     */
    Page<SavedPost> findByUserOrderBySavedAtDesc(User user, Pageable pageable);

    /**
     * Cursor-based listing: saves older than the given ID, newest first.
     * Pass the last SavedPost.id seen on the current page to get the next page.
     * Used by getSavedPostsForUserWithCursor().
     */
    Page<SavedPost> findByUserAndIdLessThanOrderBySavedAtDesc(User user, Long id, Pageable pageable);

    /**
     * All saves for a user as a plain List — use only when pagination is not needed
     * (e.g. export or test scenarios). Prefer the paginated variants in production.
     */
    List<SavedPost> findByUserOrderBySavedAtDesc(User user);

    // =========================================================================
    // TYPED LISTING — split SocialPost vs broadcast Post saves
    // =========================================================================

    /**
     * Paginated SocialPost saves only.
     * Useful when the UI shows saved social posts in a dedicated tab.
     */
    @Query("SELECT sp FROM SavedPost sp WHERE sp.user = :user AND sp.socialPost IS NOT NULL ORDER BY sp.savedAt DESC")
    Page<SavedPost> findSocialPostSavesByUserOrderBySavedAtDesc(@Param("user") User user, Pageable pageable);

    /**
     * Paginated broadcast Post saves only.
     * Useful when the UI shows saved government posts in a dedicated tab.
     */
    @Query("SELECT sp FROM SavedPost sp WHERE sp.user = :user AND sp.post IS NOT NULL ORDER BY sp.savedAt DESC")
    Page<SavedPost> findBroadcastPostSavesByUserOrderBySavedAtDesc(@Param("user") User user, Pageable pageable);

    // =========================================================================
    // COUNT QUERIES
    // =========================================================================

    /** Total saves by a user (both types combined). */
    long countByUser(User user);

    /** Number of users who saved a SocialPost. */
    long countBySocialPost(SocialPost socialPost);

    /** Number of users who saved a broadcast Post. */
    long countByPost(Post post);

    // =========================================================================
    // BULK DELETE — for cleanup flows
    // =========================================================================

    /**
     * Hard-delete all saves for a SocialPost.
     * Called by PostInteractionService.cleanupForSocialPostDeletion() when a
     * SocialPost is hard-deleted. Soft-deletes do NOT need this — the UI hides
     * saves automatically via SocialPost.isEligibleForDisplay().
     */
    @Modifying
    @Transactional
    void deleteAllBySocialPost(SocialPost socialPost);

    /**
     * Hard-delete all saves for a broadcast Post.
     * Called by PostInteractionService.cleanupForPostDeletion() when a Post is
     * hard-deleted.
     */
    @Modifying
    @Transactional
    void deleteAllByPost(Post post);

    /**
     * Hard-delete ALL saves created by a user.
     *
     * Called by PostInteractionService.cleanupForUserDeletion() during account deletion.
     *
     * Design note: previously commented out in the service. Re-enabled here so that
     * account deletion is clean. If an audit trail is required, replace this with a
     * soft-delete/anonymisation strategy at the service layer instead of removing the
     * method from the repo.
     *
     * GDPR / data-retention note: call this method when the user exercises their
     * right to erasure. The method is intentionally bulk so a single DB round-trip
     * removes all records.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM SavedPost sp WHERE sp.user = :user")
    void deleteAllByUser(@Param("user") User user);

    // =========================================================================
    // UTILITY — admin / reconciliation use only
    // =========================================================================

    /**
     * Returns all SavedPost records that reference a SocialPost (not null).
     * Intended for scheduled jobs that reconcile denormalized save_count columns.
     * Do NOT use in hot paths — no pagination.
     */
    @Query("SELECT sp FROM SavedPost sp WHERE sp.socialPost = :socialPost")
    List<SavedPost> findAllBySocialPost(@Param("socialPost") SocialPost socialPost);

    /**
     * Returns all SavedPost records that reference a broadcast Post (not null).
     * Intended for scheduled reconciliation jobs only.
     */
    @Query("SELECT sp FROM SavedPost sp WHERE sp.post = :post")
    List<SavedPost> findAllByPost(@Param("post") Post post);
}