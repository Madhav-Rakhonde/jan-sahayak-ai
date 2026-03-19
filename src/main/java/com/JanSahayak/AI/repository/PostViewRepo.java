package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.PostView;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostViewRepo extends JpaRepository<PostView, Long> {

    // =========================================================================
    // SINGLE-RECORD LOOKUPS
    // =========================================================================

    Optional<PostView> findByPostAndUserAndViewedAtAfter(Post post, User user, Date date);

    Optional<PostView> findByPostAndUser(Post post, User user);

    Optional<PostView> findBySocialPostAndUserAndViewedAtAfter(
            SocialPost socialPost, User user, Date viewedAt);

    // =========================================================================
    // COUNT QUERIES
    // =========================================================================

    @Query("SELECT COUNT(pv) FROM PostView pv WHERE pv.post = :post")
    Long countByPost(@Param("post") Post post);

    Long countBySocialPost(SocialPost socialPost);

    Long countByPostAndViewedAtAfter(Post post, Date date);

    // =========================================================================
    // LIST QUERIES
    // =========================================================================

    List<PostView> findBySocialPost(SocialPost socialPost);

    List<PostView> findByPostOrderByViewedAtDesc(Post post, Pageable pageable);

    // =========================================================================
    // BATCH INTERACTION QUERIES — eliminates N+1 in feed
    // =========================================================================

    /**
     * Returns IDs of social posts (from given list) that the user has VIEWED.
     * Used by PostInteractionService.getBatchViewedSocialPostIds()
     */
    @Query("SELECT v.socialPost.id FROM PostView v " +
            "WHERE v.user.id = :userId " +
            "AND v.socialPost.id IN :postIds")
    List<Long> findViewedSocialPostIdsByUser(
            @Param("userId") Long userId,
            @Param("postIds") List<Long> postIds);
}