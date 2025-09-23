package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.UserTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserTagRepo extends JpaRepository<UserTag, Long> {

    // ===== Basic UserTag Queries =====

    /**
     * Find tags by post and active status
     */
    List<UserTag> findByPostAndIsActiveTrue(Post post);

    /**
     * Find specific user tag in post
     */
    Optional<UserTag> findByPostAndTaggedUserAndIsActiveTrue(Post post, User taggedUser);

    /**
     * Check if user tag exists and is active
     */
    boolean existsByPostAndTaggedUserAndIsActiveTrue(Post post, User taggedUser);

    /**
     * Find all active tags for a user
     */
    List<UserTag> findByTaggedUserAndIsActiveTrue(User taggedUser);

    /**
     * Find all tags created by a user
     */
    List<UserTag> findByTaggedByAndIsActiveTrue(User taggedBy);

    // ===== Post Discovery Queries =====

    /**
     * Find posts where user is tagged
     */
    @Query("SELECT DISTINCT ut.post FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.isActive = true " +
            "ORDER BY ut.post.createdAt DESC")
    List<Post> findPostsWhereUserIsTagged(@Param("user") User user);

    /**
     * Find posts where user is tagged by status
     */
    @Query("SELECT DISTINCT ut.post FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.isActive = true " +
            "AND ut.post.status = :status " +
            "ORDER BY ut.post.createdAt DESC")
    List<Post> findPostsWhereUserIsTaggedByStatus(@Param("user") User user, @Param("status") PostStatus status);

    /**
     * Find posts where user is tagged by multiple statuses
     */
    @Query("SELECT DISTINCT ut.post FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.isActive = true " +
            "AND ut.post.status IN :statuses " +
            "ORDER BY ut.post.createdAt DESC")
    List<Post> findPostsWhereUserIsTaggedByStatuses(@Param("user") User user, @Param("statuses") List<PostStatus> statuses);

    /**
     * Find recent posts where user is tagged (within last N days)
     */
    @Query("SELECT DISTINCT ut.post FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.isActive = true " +
            "AND ut.taggedAt >= :fromDate " +
            "ORDER BY ut.post.createdAt DESC")
    List<Post> findRecentPostsWhereUserIsTagged(@Param("user") User user, @Param("fromDate") Date fromDate);

    // ===== Count Queries =====

    /**
     * Count total tags for a user
     */
    @Query("SELECT COUNT(ut) FROM UserTag ut WHERE ut.taggedUser = :user AND ut.isActive = true")
    Long countByTaggedUser(@Param("user") User user);

    /**
     * Count tagged posts by user and post status
     */
    @Query("SELECT COUNT(DISTINCT ut.post) FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.post.status = :status AND ut.isActive = true")
    Long countByTaggedUserAndPostStatus(@Param("user") User user, @Param("status") PostStatus status);

    /**
     * Count tags created after a certain date (for recent activity tracking)
     */
    @Query("SELECT COUNT(ut) FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.taggedAt >= :date AND ut.isActive = true")
    Long countByTaggedUserAndTaggedAtAfter(@Param("user") User user, @Param("date") Date date);

    /**
     * Count tags created by a user
     */
    @Query("SELECT COUNT(ut) FROM UserTag ut WHERE ut.taggedBy = :user AND ut.isActive = true")
    Long countByTaggedBy(@Param("user") User user);

    /**
     * Count total active tags in the system
     */
    @Query("SELECT COUNT(ut) FROM UserTag ut WHERE ut.isActive = true")
    Long countActiveUserTags();

    // ===== Statistics and Analytics Queries =====

    /**
     * Find most tagged users (for analytics)
     */
    @Query("SELECT ut.taggedUser, COUNT(ut) as tagCount FROM UserTag ut " +
            "WHERE ut.isActive = true " +
            "GROUP BY ut.taggedUser " +
            "ORDER BY tagCount DESC")
    List<Object[]> findMostTaggedUsers();

    /**
     * Find users who tag others most frequently
     */
    @Query("SELECT ut.taggedBy, COUNT(ut) as tagCount FROM UserTag ut " +
            "WHERE ut.isActive = true " +
            "GROUP BY ut.taggedBy " +
            "ORDER BY tagCount DESC")
    List<Object[]> findMostActiveTaggers();

    /**
     * Get tagging statistics by date range
     */
    @Query("SELECT DATE(ut.taggedAt) as tagDate, COUNT(ut) as tagCount FROM UserTag ut " +
            "WHERE ut.isActive = true AND ut.taggedAt BETWEEN :startDate AND :endDate " +
            "GROUP BY DATE(ut.taggedAt) " +
            "ORDER BY tagDate DESC")
    List<Object[]> getTaggingStatisticsByDateRange(@Param("startDate") Date startDate, @Param("endDate") Date endDate);

    /**
     * Get post status distribution for tagged posts
     */
    @Query("SELECT ut.post.status, COUNT(DISTINCT ut.post) as postCount FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.isActive = true " +
            "GROUP BY ut.post.status")
    List<Object[]> getPostStatusDistributionForUser(@Param("user") User user);

    // ===== Geographic and Targeted Queries =====

    /**
     * Find users tagged in posts from specific pincode
     */
    @Query("SELECT DISTINCT ut.taggedUser FROM UserTag ut " +
            "JOIN ut.post p JOIN p.user u " +
            "WHERE u.pincode = :pincode AND ut.isActive = true")
    List<User> findUsersTaggedInPostsFromPincode(@Param("pincode") String pincode);

    /**
     * Find posts where users from specific pincode are tagged
     */
    @Query("SELECT DISTINCT ut.post FROM UserTag ut " +
            "JOIN ut.taggedUser u " +
            "WHERE u.pincode = :pincode AND ut.isActive = true " +
            "ORDER BY ut.post.createdAt DESC")
    List<Post> findPostsWithTaggedUsersFromPincode(@Param("pincode") String pincode);

    /**
     * Find department users tagged in posts (for resolution tracking)
     */
    @Query("SELECT DISTINCT ut FROM UserTag ut " +
            "JOIN ut.taggedUser u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND ut.isActive = true " +
            "AND ut.post.status = :status " +
            "ORDER BY ut.taggedAt DESC")
    List<UserTag> findDepartmentUserTagsByPostStatus(@Param("status") PostStatus status);

    // ===== Cleanup and Maintenance Queries =====

    /**
     * Find orphaned tags (tags where post or user no longer exists or is inactive)
     */
    @Query("SELECT ut FROM UserTag ut " +
            "WHERE ut.isActive = true " +
            "AND (ut.post IS NULL OR ut.taggedUser IS NULL OR ut.taggedBy IS NULL " +
            "     OR ut.taggedUser.isActive = false OR ut.taggedBy.isActive = false)")
    List<UserTag> findOrphanedTags();

    /**
     * Find tags older than specified date
     */
    @Query("SELECT ut FROM UserTag ut " +
            "WHERE ut.taggedAt < :cutoffDate")
    List<UserTag> findTagsOlderThan(@Param("cutoffDate") Date cutoffDate);

    // ===== Performance Optimization Queries =====

    /**
     * Find tags by post IDs (for batch operations)
     */
    @Query("SELECT ut FROM UserTag ut " +
            "WHERE ut.post.id IN :postIds AND ut.isActive = true")
    List<UserTag> findByPostIdsAndIsActiveTrue(@Param("postIds") List<Long> postIds);

    /**
     * Find tags by user IDs (for batch operations)
     */
    @Query("SELECT ut FROM UserTag ut " +
            "WHERE ut.taggedUser.id IN :userIds AND ut.isActive = true")
    List<UserTag> findByTaggedUserIdsAndIsActiveTrue(@Param("userIds") List<Long> userIds);

    // ===== Resolution Tracking Queries =====

    /**
     * Find tags in resolved posts for performance analysis
     */
    @Query("SELECT ut FROM UserTag ut " +
            "WHERE ut.isActive = true AND ut.post.status = 'RESOLVED' " +
            "AND ut.post.resolvedAt IS NOT NULL " +
            "ORDER BY ut.post.resolvedAt DESC")
    List<UserTag> findTagsInResolvedPosts();

    /**
     * Calculate average resolution time for posts where user is tagged
     */
    @Query("SELECT AVG(TIMESTAMPDIFF(HOUR, ut.post.createdAt, ut.post.resolvedAt)) " +
            "FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.isActive = true " +
            "AND ut.post.status = 'RESOLVED' AND ut.post.resolvedAt IS NOT NULL")
    Double getAverageResolutionTimeForUser(@Param("user") User user);

    /**
     * Find unresolved tags older than specified days
     */
    @Query("SELECT ut FROM UserTag ut " +
            "WHERE ut.isActive = true " +
            "AND ut.post.status = 'ACTIVE' " +
            "AND ut.taggedAt < :cutoffDate " +
            "ORDER BY ut.taggedAt ASC")
    List<UserTag> findUnresolvedTagsOlderThan(@Param("cutoffDate") Date cutoffDate);

    // ===== Cursor-based Post Discovery Queries =====

    /**
     * Find posts where user is tagged with cursor pagination
     */
    @Query("SELECT DISTINCT ut.post FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.isActive = true " +
            "AND ut.post.id < :beforeId " +
            "ORDER BY ut.post.id DESC")
    List<Post> findPostsWhereUserIsTaggedWithCursor(@Param("user") User user,
                                                    @Param("beforeId") Long beforeId,
                                                    Pageable pageable);

    /**
     * Find posts where user is tagged without cursor (first page)
     */
    @Query("SELECT DISTINCT ut.post FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.isActive = true " +
            "ORDER BY ut.post.id DESC")
    List<Post> findPostsWhereUserIsTaggedOrderByIdDesc(@Param("user") User user, Pageable pageable);

    /**
     * Find posts where user is tagged by status with cursor pagination
     */
    @Query("SELECT DISTINCT ut.post FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.isActive = true " +
            "AND ut.post.status = :status " +
            "AND ut.post.id < :beforeId " +
            "ORDER BY ut.post.id DESC")
    List<Post> findPostsWhereUserIsTaggedByStatusWithCursor(@Param("user") User user,
                                                            @Param("status") PostStatus status,
                                                            @Param("beforeId") Long beforeId,
                                                            Pageable pageable);

    /**
     * Find posts where user is tagged by status without cursor (first page)
     */
    @Query("SELECT DISTINCT ut.post FROM UserTag ut " +
            "WHERE ut.taggedUser = :user AND ut.isActive = true " +
            "AND ut.post.status = :status " +
            "ORDER BY ut.post.id DESC")
    List<Post> findPostsWhereUserIsTaggedByStatusOrderByIdDesc(@Param("user") User user,
                                                               @Param("status") PostStatus status,
                                                               Pageable pageable);

// ===== Cursor-based Tagged Users in Post Queries =====

    /**
     * Find active user tags by post with cursor pagination
     */
    List<UserTag> findByPostAndIsActiveTrueAndIdLessThanOrderByIdDesc(Post post, Long beforeId, Pageable pageable);

    /**
     * Find active user tags by post without cursor (first page)
     */
    List<UserTag> findByPostAndIsActiveTrueOrderByIdDesc(Post post, Pageable pageable);
}