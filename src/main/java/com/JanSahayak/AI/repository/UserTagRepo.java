package com.JanSahayak.AI.repository;


import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.UserTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserTagRepo extends JpaRepository<UserTag, Long> {

    // Find tags by post and active status
    List<UserTag> findByPostAndIsActiveTrue(Post post);

    // Find specific user tag in post
    Optional<UserTag> findByPostAndTaggedUserAndIsActiveTrue(Post post, User taggedUser);

    // Check if user tag exists and is active
    boolean existsByPostAndTaggedUserAndIsActiveTrue(Post post, User taggedUser);

    // Find posts where user is tagged
    @Query("SELECT DISTINCT ut.post FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.isActive = true " +
            "ORDER BY ut.post.createdAt DESC")
    List<Post> findPostsWhereUserIsTagged(@Param("user") User user);

    // Find posts where user is tagged by status
    @Query("SELECT DISTINCT ut.post FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.isActive = true " +
            "AND ut.post.status = :status " +
            "ORDER BY ut.post.createdAt DESC")
    List<Post> findPostsWhereUserIsTaggedByStatus(@Param("user") User user, @Param("status") PostStatus status);

    long countByTaggedUser(User user);

    // FIXED: Count tagged posts by user + post status - using custom query for clarity
    @Query("SELECT COUNT(ut) FROM UserTag ut WHERE ut.taggedUser = :user AND ut.post.status = :status AND ut.isActive = true")
    long countByTaggedUserAndPostStatus(@Param("user") User user, @Param("status") PostStatus status);

    // NEW: Count tagged posts after a certain date (for recent 30-day activity)
    long countByTaggedUserAndTaggedAtAfter(User user, Date date);
}