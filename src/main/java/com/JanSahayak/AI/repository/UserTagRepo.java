package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.UserTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;

import jakarta.persistence.QueryHint;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserTagRepo extends JpaRepository<UserTag, Long> {

    // ===== Basic UserTag Queries =====

    /**
     * Find tags by post and active status
     */
    List<UserTag> findByPostAndIsActiveTrue(@NonNull Post post);

    /**
     * Find specific user tag in post
     */
    Optional<UserTag> findByPostAndTaggedUserAndIsActiveTrue(@NonNull Post post, @NonNull User taggedUser);

    /**
     * Check if user tag exists and is active
     */
    boolean existsByPostAndTaggedUserAndIsActiveTrue(@NonNull Post post, @NonNull User taggedUser);

    /**
     * Find all active tags for a user
     */
    List<UserTag> findByTaggedUserAndIsActiveTrue(@NonNull User taggedUser);

    /**
     * Find all tags created by a user
     */
    List<UserTag> findByTaggedByAndIsActiveTrue(@NonNull User taggedBy);

    // ===== Optimized Post Discovery Queries =====

    /**
     * Find posts where user is tagged with performance optimization
     */
    @Query(value = """
            SELECT DISTINCT p FROM Post p 
            JOIN UserTag ut ON ut.post = p 
            WHERE ut.taggedUser = :user 
            AND ut.isActive = true 
            AND p.status IN ('ACTIVE', 'RESOLVED')
            ORDER BY p.id DESC
            """)
    @QueryHints({
            @QueryHint(name = "org.hibernate.fetchSize", value = "50"),
            @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Post> findPostsWhereUserIsTagged(@NonNull @Param("user") User user);

    /**
     * Find posts where user is tagged by specific status
     */
    @Query(value = """
            SELECT DISTINCT p FROM Post p 
            JOIN UserTag ut ON ut.post = p 
            WHERE ut.taggedUser = :user 
            AND ut.isActive = true 
            AND p.status = :status
            ORDER BY p.id DESC
            """)
    @QueryHints({
            @QueryHint(name = "org.hibernate.fetchSize", value = "50"),
            @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Post> findPostsWhereUserIsTaggedByStatus(@NonNull @Param("user") User user, @NonNull @Param("status") PostStatus status);

    /**
     * Find posts where user is tagged by multiple statuses
     */
    @Query(value = """
            SELECT DISTINCT p FROM Post p 
            JOIN UserTag ut ON ut.post = p 
            WHERE ut.taggedUser = :user 
            AND ut.isActive = true 
            AND p.status IN :statuses
            ORDER BY p.id DESC
            """)
    @QueryHints({
            @QueryHint(name = "org.hibernate.fetchSize", value = "50"),
            @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Post> findPostsWhereUserIsTaggedByStatuses(@NonNull @Param("user") User user, @NonNull @Param("statuses") List<PostStatus> statuses);

    /**
     * Find recent posts where user is tagged (within last N days)
     */
    @Query(value = """
            SELECT DISTINCT p FROM Post p 
            JOIN UserTag ut ON ut.post = p 
            WHERE ut.taggedUser = :user 
            AND ut.isActive = true 
            AND ut.taggedAt >= :fromDate
            ORDER BY p.id DESC
            """)
    @QueryHints({
            @QueryHint(name = "org.hibernate.fetchSize", value = "50"),
            @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Post> findRecentPostsWhereUserIsTagged(@NonNull @Param("user") User user, @NonNull @Param("fromDate") Date fromDate);

    // ===== Optimized Count Queries =====

    /**
     * Count total tags for a user - Optimized
     */
    @Query("SELECT COUNT(ut.id) FROM UserTag ut WHERE ut.taggedUser = :user AND ut.isActive = true")
    Long countByTaggedUser(@NonNull @Param("user") User user);

    /**
     * Count tagged posts by user and post status - Optimized
     */
    @Query("""
            SELECT COUNT(DISTINCT ut.post.id) FROM UserTag ut 
            WHERE ut.taggedUser = :user 
            AND ut.post.status = :status 
            AND ut.isActive = true
            """)
    Long countByTaggedUserAndPostStatus(@NonNull @Param("user") User user, @NonNull @Param("status") PostStatus status);

    /**
     * Count tags created after a certain date (for recent activity tracking)
     */
    @Query("""
            SELECT COUNT(ut.id) FROM UserTag ut 
            WHERE ut.taggedUser = :user 
            AND ut.taggedAt >= :date 
            AND ut.isActive = true
            """)
    Long countByTaggedUserAndTaggedAtAfter(@NonNull @Param("user") User user, @NonNull @Param("date") Date date);

    /**
     * Count tags created by a user
     */
    @Query("SELECT COUNT(ut.id) FROM UserTag ut WHERE ut.taggedBy = :user AND ut.isActive = true")
    Long countByTaggedBy(@NonNull @Param("user") User user);

    /**
     * Count total active tags in the system
     */
    @Query("SELECT COUNT(ut.id) FROM UserTag ut WHERE ut.isActive = true")
    Long countActiveUserTags();

    // ===== Statistics and Analytics Queries - Optimized =====

    /**
     * Find most tagged users (for analytics) - with limit for performance
     */
    @Query(value = """
            SELECT ut.taggedUser, COUNT(ut.id) as tagCount 
            FROM UserTag ut 
            WHERE ut.isActive = true 
            GROUP BY ut.taggedUser 
            ORDER BY tagCount DESC
            """)
    List<Object[]> findMostTaggedUsers(Pageable pageable);

    /**
     * Find users who tag others most frequently - with limit for performance
     */
    @Query(value = """
            SELECT ut.taggedBy, COUNT(ut.id) as tagCount 
            FROM UserTag ut 
            WHERE ut.isActive = true 
            GROUP BY ut.taggedBy 
            ORDER BY tagCount DESC
            """)
    List<Object[]> findMostActiveTaggers(Pageable pageable);

    /**
     * Get tagging statistics by date range - Optimized
     */
    @Query(value = """
            SELECT DATE(ut.taggedAt) as tagDate, COUNT(ut.id) as tagCount 
            FROM UserTag ut 
            WHERE ut.isActive = true 
            AND ut.taggedAt BETWEEN :startDate AND :endDate 
            GROUP BY DATE(ut.taggedAt) 
            ORDER BY tagDate DESC
            """)
    List<Object[]> getTaggingStatisticsByDateRange(@NonNull @Param("startDate") Date startDate,
                                                   @NonNull @Param("endDate") Date endDate);

    /**
     * Get post status distribution for tagged posts
     */
    @Query("""
            SELECT ut.post.status, COUNT(DISTINCT ut.post.id) as postCount 
            FROM UserTag ut 
            WHERE ut.taggedUser = :user 
            AND ut.isActive = true 
            GROUP BY ut.post.status
            """)
    List<Object[]> getPostStatusDistributionForUser(@NonNull @Param("user") User user);

    // ===== Geographic and Targeted Queries - Optimized =====

    /**
     * Find users tagged in posts from specific pincode
     */
    @Query("""
            SELECT DISTINCT ut.taggedUser 
            FROM UserTag ut 
            JOIN ut.post p 
            JOIN p.user u 
            WHERE u.pincode = :pincode 
            AND ut.isActive = true
            """)
    List<User> findUsersTaggedInPostsFromPincode(@NonNull @Param("pincode") String pincode);

    /**
     * Find posts where users from specific pincode are tagged
     */
    @Query(value = """
            SELECT DISTINCT p FROM Post p 
            JOIN UserTag ut ON ut.post = p 
            JOIN ut.taggedUser u 
            WHERE u.pincode = :pincode 
            AND ut.isActive = true
            ORDER BY p.id DESC
            """)
    List<Post> findPostsWithTaggedUsersFromPincode(@NonNull @Param("pincode") String pincode);

    /**
     * Find department users tagged in posts (for resolution tracking)
     */
    @Query("""
            SELECT ut FROM UserTag ut 
            JOIN ut.taggedUser u 
            JOIN u.role r 
            WHERE r.name = :roleName 
            AND ut.isActive = true 
            AND ut.post.status = :status 
            ORDER BY ut.taggedAt DESC
            """)
    List<UserTag> findDepartmentUserTagsByPostStatus(@NonNull @Param("status") PostStatus status,
                                                     @NonNull @Param("roleName") String roleName);

    // ===== Cleanup and Maintenance Queries - Safe =====

    /**
     * Find orphaned tags (tags where post or user no longer exists or is inactive)
     */
    @Query("""
            SELECT ut FROM UserTag ut 
            WHERE ut.isActive = true 
            AND (ut.post IS NULL 
                 OR ut.taggedUser IS NULL 
                 OR ut.taggedBy IS NULL 
                 OR ut.taggedUser.isActive = false 
                 OR ut.taggedBy.isActive = false)
            """)
    List<UserTag> findOrphanedTags();

    /**
     * Find tags older than specified date - with limit for safety
     */
    @Query("SELECT ut FROM UserTag ut WHERE ut.taggedAt < :cutoffDate ORDER BY ut.taggedAt ASC")
    List<UserTag> findTagsOlderThan(@NonNull @Param("cutoffDate") Date cutoffDate, Pageable pageable);

    // ===== Performance Optimization Queries =====

    /**
     * Find tags by post IDs (for batch operations)
     */
    @Query("SELECT ut FROM UserTag ut WHERE ut.post.id IN :postIds AND ut.isActive = true")
    List<UserTag> findByPostIdsAndIsActiveTrue(@NonNull @Param("postIds") List<Long> postIds);

    /**
     * Find tags by user IDs (for batch operations)
     */
    @Query("SELECT ut FROM UserTag ut WHERE ut.taggedUser.id IN :userIds AND ut.isActive = true")
    List<UserTag> findByTaggedUserIdsAndIsActiveTrue(@NonNull @Param("userIds") List<Long> userIds);

    // ===== Resolution Tracking Queries - Parameterized =====

    /**
     * Find tags in resolved posts for performance analysis
     */
    @Query("""
            SELECT ut FROM UserTag ut 
            WHERE ut.isActive = true 
            AND ut.post.status = :status 
            AND ut.post.resolvedAt IS NOT NULL 
            ORDER BY ut.post.resolvedAt DESC
            """)
    List<UserTag> findTagsInResolvedPosts(@NonNull @Param("status") PostStatus status);

    /**
     * Legacy method for backward compatibility
     */
    @Query("""
            SELECT ut FROM UserTag ut 
            WHERE ut.isActive = true 
            AND ut.post.status = 'RESOLVED' 
            AND ut.post.resolvedAt IS NOT NULL 
            ORDER BY ut.post.resolvedAt DESC
            """)
    List<UserTag> findTagsInResolvedPosts();

    /**
     * Calculate average resolution time for posts where user is tagged
     */
    @Query("""
            SELECT AVG(TIMESTAMPDIFF(HOUR, ut.post.createdAt, ut.post.resolvedAt)) 
            FROM UserTag ut 
            WHERE ut.taggedUser = :user 
            AND ut.isActive = true 
            AND ut.post.status = :status 
            AND ut.post.resolvedAt IS NOT NULL
            """)
    Double getAverageResolutionTimeForUser(@NonNull @Param("user") User user, @NonNull @Param("status") PostStatus status);

    /**
     * Find unresolved tags older than specified days
     */
    @Query("""
            SELECT ut FROM UserTag ut 
            WHERE ut.isActive = true 
            AND ut.post.status = :status 
            AND ut.taggedAt < :cutoffDate 
            ORDER BY ut.taggedAt ASC
            """)
    List<UserTag> findUnresolvedTagsOlderThan(@NonNull @Param("cutoffDate") Date cutoffDate,
                                              @NonNull @Param("status") PostStatus status,
                                              Pageable pageable);

    // ===== Production-Ready Cursor-based Post Discovery Queries =====

    /**
     * Find posts where user is tagged with cursor pagination - Production optimized
     */
    @Query(value = """
            SELECT DISTINCT p FROM Post p 
            JOIN UserTag ut ON ut.post = p 
            WHERE ut.taggedUser = :user 
            AND ut.isActive = true 
            AND p.id < :beforeId
            ORDER BY p.id DESC
            """)
    @QueryHints({
            @QueryHint(name = "org.hibernate.fetchSize", value = "50"),
            @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Post> findPostsWhereUserIsTaggedWithCursor(@NonNull @Param("user") User user,
                                                    @NonNull @Param("beforeId") Long beforeId,
                                                    @NonNull Pageable pageable);

    /**
     * Find posts where user is tagged without cursor (first page) - Production optimized
     */
    @Query(value = """
            SELECT DISTINCT p FROM Post p 
            JOIN UserTag ut ON ut.post = p 
            WHERE ut.taggedUser = :user 
            AND ut.isActive = true
            ORDER BY p.id DESC
            """)
    @QueryHints({
            @QueryHint(name = "org.hibernate.fetchSize", value = "50"),
            @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Post> findPostsWhereUserIsTaggedOrderByIdDesc(@NonNull @Param("user") User user, @NonNull Pageable pageable);

    /**
     * Find posts where user is tagged by status with cursor pagination - Production optimized
     */
    @Query(value = """
            SELECT DISTINCT p FROM Post p 
            JOIN UserTag ut ON ut.post = p 
            WHERE ut.taggedUser = :user 
            AND ut.isActive = true 
            AND p.status = :status 
            AND p.id < :beforeId
            ORDER BY p.id DESC
            """)
    @QueryHints({
            @QueryHint(name = "org.hibernate.fetchSize", value = "50"),
            @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Post> findPostsWhereUserIsTaggedByStatusWithCursor(@NonNull @Param("user") User user,
                                                            @NonNull @Param("status") PostStatus status,
                                                            @NonNull @Param("beforeId") Long beforeId,
                                                            @NonNull Pageable pageable);

    /**
     * Find posts where user is tagged by status without cursor (first page) - Production optimized
     */
    @Query(value = """
            SELECT DISTINCT p FROM Post p 
            JOIN UserTag ut ON ut.post = p 
            WHERE ut.taggedUser = :user 
            AND ut.isActive = true 
            AND p.status = :status
            ORDER BY p.id DESC
            """)
    @QueryHints({
            @QueryHint(name = "org.hibernate.fetchSize", value = "50"),
            @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Post> findPostsWhereUserIsTaggedByStatusOrderByIdDesc(@NonNull @Param("user") User user,
                                                               @NonNull @Param("status") PostStatus status,
                                                               @NonNull Pageable pageable);

    // ===== Cursor-based Tagged Users in Post Queries - Enhanced =====

    /**
     * Find active user tags by post with cursor pagination
     */
    List<UserTag> findByPostAndIsActiveTrueAndIdLessThanOrderByIdDesc(@NonNull Post post, @NonNull Long beforeId, @NonNull Pageable pageable);

    /**
     * Find active user tags by post without cursor (first page)
     */
    List<UserTag> findByPostAndIsActiveTrueOrderByIdDesc(@NonNull Post post, @NonNull Pageable pageable);

    // ===== Additional Production Safety Methods =====

    /**
     * Batch update user tags for deactivation (safer than individual updates)
     */
    @Query("SELECT ut FROM UserTag ut WHERE ut.post.id IN :postIds AND ut.isActive = true")
    List<UserTag> findActiveTagsByPostIds(@NonNull @Param("postIds") List<Long> postIds);

    /**
     * Find user tags that need cleanup based on business rules
     */
    @Query("""
            SELECT ut FROM UserTag ut 
            WHERE ut.isActive = true 
            AND ut.taggedAt < :cleanupDate 
            AND NOT EXISTS (
                SELECT 1 FROM Post p 
                WHERE p.id = ut.post.id 
                AND p.status IN ('ACTIVE', 'RESOLVED')
            )
            """)
    List<UserTag> findTagsForCleanup(@NonNull @Param("cleanupDate") Date cleanupDate, @NonNull Pageable pageable);
}