package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.PostLike;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.*;
import java.util.Optional;

@Repository
public interface PostLikeRepo extends JpaRepository<PostLike, Long> {
    // For duplicate like prevention
    Optional<PostLike> findByPostAndUser(Post post, User user);

    // For checking if user liked post
    boolean existsByPostAndUser(Post post, User user);

    // For statistics
    Long countByPostAndCreatedAtAfter(Post post, Date date);

    // For getting users who liked a post (with pagination)
    @Query("SELECT pl.user FROM PostLike pl WHERE pl.post = :post AND pl.id < :beforeId ORDER BY pl.id DESC")
    List<User> findUsersWhoLikedPostWithCursor(@Param("post") Post post,
                                               @Param("beforeId") Long beforeId,
                                               Pageable pageable);

    @Query("SELECT pl.user FROM PostLike pl WHERE pl.post = :post ORDER BY pl.id DESC")
    List<User> findUsersWhoLikedPost(@Param("post") Post post, Pageable pageable);

    // For admin/cleanup purposes
    List<PostLike> findByPostOrderByCreatedAtDesc(Post post, Pageable pageable);

    /**
     * Count total likes for a post
     */
    @Query("SELECT COUNT(pl) FROM PostLike pl WHERE pl.post = :post")
    Long countByPost(@Param("post") Post post);

    // Add these methods to PostLikeRepo.java

    Optional<PostLike> findBySocialPostAndUser(SocialPost socialPost, User user);

    List<PostLike> findBySocialPost(SocialPost socialPost);

    Long countBySocialPost(SocialPost socialPost);


}
