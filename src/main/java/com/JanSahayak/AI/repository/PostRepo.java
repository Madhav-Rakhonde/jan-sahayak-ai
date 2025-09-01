package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.PostView;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepo extends JpaRepository<Post, Long> {

    // 1. Find posts by location and status
    List<Post> findByLocationAndStatus(String location, PostStatus status);

    // 2. Find posts by location ordered by created date
    List<Post> findByLocationOrderByCreatedAtDesc(String location);

    // 4. Find posts by user ordered by created date
    List<Post> findByUserOrderByCreatedAtDesc(User user);

    // 5. Count posts by status
    Long countByStatus(PostStatus status);

    List<Post> findByStatusOrderByCreatedAtDesc(PostStatus status);

    // Enhanced Feed System Methods
    /**
     * Find posts by multiple users and status, ordered by creation date
     */
    List<Post> findByUserInAndStatusOrderByCreatedAtDesc(List<User> users, PostStatus status);

    /**
     * Find posts by multiple users, ordered by creation date
     */
    List<Post> findByUserInOrderByCreatedAtDesc(List<User> users);

    /**
     * Find posts by status with pagination, ordered by creation date desc
     */
    @Query("SELECT p FROM Post p WHERE p.status = :status ORDER BY p.createdAt DESC")
    List<Post> findByStatusOrderByCreatedAtDesc(@Param("status") PostStatus status, Pageable pageable);

    /**
     * Find all posts with pagination, ordered by creation date desc
     */
    @Query("SELECT p FROM Post p ORDER BY p.createdAt DESC")
    List<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find posts by multiple locations
     */
    @Query("SELECT p FROM Post p WHERE p.location IN :locations " +
            "AND (:status IS NULL OR p.status = :status) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByLocationInAndStatus(@Param("locations") List<String> locations,
                                         @Param("status") PostStatus status);

    long countByLocation(String location);
    List<Post> findByUserAndStatusOrderByCreatedAtDesc(User user, PostStatus status);

    // Count posts by users (list of users) and status
    long countByUserInAndStatus(List<User> users, PostStatus status);

    // Count posts by location and status
    long countByLocationAndStatus(String location, PostStatus status);

    // Count posts by users, location and status
    long countByUserInAndLocationAndStatus(List<User> users, String location, PostStatus status);

    // Count posts by user + status
    long countByUserAndStatus(User user, PostStatus status);

    // Count all posts by user
    long countByUser(User user);

    // Count posts by user created after a given timestamp (for "recent posts")
    long countByUserAndCreatedAtAfter(User user, Timestamp createdAt);

    List<Post> findByLocationInOrderByCreatedAtDesc(List<String> locations);

    @Query("SELECT p FROM Post p WHERE SIZE(p.userTags) > 1")
    List<Post> findPostsWithMultipleUserTags();

    // FIXED: Changed size() to SIZE() for JPQL consistency
    @Query("SELECT p FROM Post p WHERE p.createdAt >= :startDate ORDER BY (SIZE(p.likes) + SIZE(p.comments) + SIZE(p.views)) DESC")
    List<Post> findTrendingPosts(@Param("startDate") Timestamp startDate, Pageable pageable);

    // FIXED: Changed size() to SIZE() for JPQL consistency
    @Query("SELECT " +
            "SUM(CASE WHEN SIZE(p.userTags) > 0 THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN SIZE(p.userTags) = 0 THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN SIZE(p.userTags) > 1 THEN 1 ELSE 0 END), " +
            "AVG(SIZE(p.userTags)) " +
            "FROM Post p")
    Object[] getTaggedPostsStatistics();

    // FIXED: Keep original method signature to match service usage
    @Query("SELECT DISTINCT p FROM Post p " +
            "JOIN p.userTags ut " +
            "WHERE ut.taggedUser = :user " +
            "AND ut.isActive = true " +
            "AND (p.status = com.JanSahayak.AI.enums.PostStatus.ACTIVE OR p.status = com.JanSahayak.AI.enums.PostStatus.RESOLVED) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findPostsTaggedWithUser(@Param("user") User user);

}