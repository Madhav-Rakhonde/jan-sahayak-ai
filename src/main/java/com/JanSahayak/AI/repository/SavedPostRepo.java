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

@Repository
public interface SavedPostRepo extends JpaRepository<SavedPost, Long> {

    // =========================================================================
    // SINGLE-RECORD LOOKUPS
    // =========================================================================

    Optional<SavedPost> findByUserAndSocialPost(User user, SocialPost socialPost);

    Optional<SavedPost> findByUserAndPost(User user, Post post);

    // =========================================================================
    // EXISTENCE CHECKS
    // =========================================================================

    boolean existsByUserAndSocialPost(User user, SocialPost socialPost);

    boolean existsByUserAndPost(User user, Post post);

    // =========================================================================
    // PAGINATED LISTING
    // =========================================================================

    Page<SavedPost> findByUserOrderBySavedAtDesc(User user, Pageable pageable);

    Page<SavedPost> findByUserAndIdLessThanOrderBySavedAtDesc(User user, Long id, Pageable pageable);

    List<SavedPost> findByUserOrderBySavedAtDesc(User user);

    @Query("SELECT sp FROM SavedPost sp WHERE sp.user = :user AND sp.socialPost IS NOT NULL ORDER BY sp.savedAt DESC")
    Page<SavedPost> findSocialPostSavesByUserOrderBySavedAtDesc(@Param("user") User user, Pageable pageable);

    @Query("SELECT sp FROM SavedPost sp WHERE sp.user = :user AND sp.post IS NOT NULL ORDER BY sp.savedAt DESC")
    Page<SavedPost> findBroadcastPostSavesByUserOrderBySavedAtDesc(@Param("user") User user, Pageable pageable);

    // =========================================================================
    // COUNT QUERIES
    // =========================================================================

    long countByUser(User user);

    long countBySocialPost(SocialPost socialPost);

    long countByPost(Post post);

    // =========================================================================
    // BULK DELETE
    // =========================================================================

    @Modifying
    @Transactional
    void deleteAllBySocialPost(SocialPost socialPost);

    @Modifying
    @Transactional
    void deleteAllByPost(Post post);

    @Modifying
    @Transactional
    @Query("DELETE FROM SavedPost sp WHERE sp.user = :user")
    void deleteAllByUser(@Param("user") User user);

    // =========================================================================
    // UTILITY — admin / reconciliation
    // =========================================================================

    @Query("SELECT sp FROM SavedPost sp WHERE sp.socialPost = :socialPost")
    List<SavedPost> findAllBySocialPost(@Param("socialPost") SocialPost socialPost);

    @Query("SELECT sp FROM SavedPost sp WHERE sp.post = :post")
    List<SavedPost> findAllByPost(@Param("post") Post post);

    // =========================================================================
    // BATCH INTERACTION QUERIES — eliminates N+1 in feed
    // =========================================================================

    /**
     * Returns IDs of social posts (from given list) that the user has SAVED.
     * Used by PostInteractionService.getBatchSavedSocialPostIds()
     */
    @Query("SELECT s.socialPost.id FROM SavedPost s " +
            "WHERE s.user.id = :userId " +
            "AND s.socialPost.id IN :postIds")
    List<Long> findSavedSocialPostIdsByUser(
            @Param("userId") Long userId,
            @Param("postIds") List<Long> postIds);
}