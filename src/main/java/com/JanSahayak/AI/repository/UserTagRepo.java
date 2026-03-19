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
     * FIX: Added JOIN FETCH taggedUser and taggedBy to eliminate N lazy-load
     * queries when callers access tag.getTaggedUser().getActualUsername() in a
     * stream. The old version caused 1 SELECT per tag in every convertToPostResponse call.
     */
    @Query("SELECT t FROM UserTag t JOIN FETCH t.taggedUser JOIN FETCH t.taggedBy " +
            "WHERE t.post = :post AND t.isActive = true ORDER BY t.taggedAt ASC")
    List<UserTag> findByPostAndIsActiveTrue(@NonNull @Param("post") Post post);

    /**
     * FIX: Batch version — loads all active tags for a list of post IDs in one query.
     * Use this in feed/list rendering instead of calling findByPostAndIsActiveTrue()
     * once per post. Callers group the result by post ID:
     *   Map<Long, List<UserTag>> tagsByPost = repo.findByPostIdsAndIsActiveTrueFetch(ids)
     *       .stream().collect(Collectors.groupingBy(t -> t.getPost().getId()));
     */
    @Query("SELECT t FROM UserTag t JOIN FETCH t.taggedUser JOIN FETCH t.post " +
            "WHERE t.post.id IN :postIds AND t.isActive = true")
    List<UserTag> findByPostIdsAndIsActiveTrueFetch(@NonNull @Param("postIds") List<Long> postIds);

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

    @Query("SELECT COUNT(ut.id) FROM UserTag ut WHERE ut.taggedUser = :user AND ut.isActive = true")
    Long countByTaggedUser(@NonNull @Param("user") User user);

    @Query("""
            SELECT COUNT(DISTINCT ut.post.id) FROM UserTag ut 
            WHERE ut.taggedUser = :user 
            AND ut.post.status = :status 
            AND ut.isActive = true
            """)
    Long countByTaggedUserAndPostStatus(@NonNull @Param("user") User user, @NonNull @Param("status") PostStatus status);

    @Query("""
            SELECT COUNT(ut.id) FROM UserTag ut 
            WHERE ut.taggedUser = :user 
            AND ut.taggedAt >= :date 
            AND ut.isActive = true
            """)
    Long countByTaggedUserAndTaggedAtAfter(@NonNull @Param("user") User user, @NonNull @Param("date") Date date);

    @Query("SELECT COUNT(ut.id) FROM UserTag ut WHERE ut.taggedBy = :user AND ut.isActive = true")
    Long countByTaggedBy(@NonNull @Param("user") User user);

    @Query("SELECT COUNT(ut.id) FROM UserTag ut WHERE ut.isActive = true")
    Long countActiveUserTags();

    // ===== Statistics and Analytics Queries =====

    @Query(value = """
            SELECT ut.taggedUser, COUNT(ut.id) as tagCount 
            FROM UserTag ut 
            WHERE ut.isActive = true 
            GROUP BY ut.taggedUser 
            ORDER BY tagCount DESC
            """)
    List<Object[]> findMostTaggedUsers(Pageable pageable);

    @Query(value = """
            SELECT ut.taggedBy, COUNT(ut.id) as tagCount 
            FROM UserTag ut 
            WHERE ut.isActive = true 
            GROUP BY ut.taggedBy 
            ORDER BY tagCount DESC
            """)
    List<Object[]> findMostActiveTaggers(Pageable pageable);

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

    @Query("""
            SELECT ut.post.status, COUNT(DISTINCT ut.post.id) as postCount 
            FROM UserTag ut 
            WHERE ut.taggedUser = :user 
            AND ut.isActive = true 
            GROUP BY ut.post.status
            """)
    List<Object[]> getPostStatusDistributionForUser(@NonNull @Param("user") User user);

    // ===== Geographic and Targeted Queries =====

    @Query("""
            SELECT DISTINCT ut.taggedUser 
            FROM UserTag ut 
            JOIN ut.post p 
            JOIN p.user u 
            WHERE u.pincode = :pincode 
            AND ut.isActive = true
            """)
    List<User> findUsersTaggedInPostsFromPincode(@NonNull @Param("pincode") String pincode);

    @Query(value = """
            SELECT DISTINCT p FROM Post p 
            JOIN UserTag ut ON ut.post = p 
            JOIN ut.taggedUser u 
            WHERE u.pincode = :pincode 
            AND ut.isActive = true
            ORDER BY p.id DESC
            """)
    List<Post> findPostsWithTaggedUsersFromPincode(@NonNull @Param("pincode") String pincode);

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

    // ===== Cleanup and Maintenance Queries =====

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

    @Query("SELECT ut FROM UserTag ut WHERE ut.taggedAt < :cutoffDate ORDER BY ut.taggedAt ASC")
    List<UserTag> findTagsOlderThan(@NonNull @Param("cutoffDate") Date cutoffDate, Pageable pageable);

    // ===== Performance Optimization Queries =====

    /**
     * Batch lookup by post IDs (no JOIN FETCH — used for existence checks only).
     */
    @Query("SELECT ut FROM UserTag ut WHERE ut.post.id IN :postIds AND ut.isActive = true")
    List<UserTag> findByPostIdsAndIsActiveTrue(@NonNull @Param("postIds") List<Long> postIds);

    @Query("SELECT ut FROM UserTag ut WHERE ut.taggedUser.id IN :userIds AND ut.isActive = true")
    List<UserTag> findByTaggedUserIdsAndIsActiveTrue(@NonNull @Param("userIds") List<Long> userIds);

    // ===== Resolution Tracking Queries =====

    @Query("""
            SELECT ut FROM UserTag ut 
            WHERE ut.isActive = true 
            AND ut.post.status = :status 
            AND ut.post.resolvedAt IS NOT NULL 
            ORDER BY ut.post.resolvedAt DESC
            """)
    List<UserTag> findTagsInResolvedPosts(@NonNull @Param("status") PostStatus status);

    @Query("""
            SELECT ut FROM UserTag ut 
            WHERE ut.isActive = true 
            AND ut.post.status = 'RESOLVED' 
            AND ut.post.resolvedAt IS NOT NULL 
            ORDER BY ut.post.resolvedAt DESC
            """)
    List<UserTag> findTagsInResolvedPosts();

    @Query("""
            SELECT AVG(TIMESTAMPDIFF(HOUR, ut.post.createdAt, ut.post.resolvedAt)) 
            FROM UserTag ut 
            WHERE ut.taggedUser = :user 
            AND ut.isActive = true 
            AND ut.post.status = :status 
            AND ut.post.resolvedAt IS NOT NULL
            """)
    Double getAverageResolutionTimeForUser(@NonNull @Param("user") User user, @NonNull @Param("status") PostStatus status);

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

    // ===== Cursor-based Tagged Users in Post Queries =====

    /**
     * FIX: Both paginated variants now use explicit @Query with JOIN FETCH taggedUser
     * to avoid lazy-load queries when iterating UserTag.getTaggedUser().
     */
    @Query("SELECT t FROM UserTag t JOIN FETCH t.taggedUser " +
            "WHERE t.post = :post AND t.isActive = true AND t.id < :beforeId ORDER BY t.id DESC")
    List<UserTag> findByPostAndIsActiveTrueAndIdLessThanOrderByIdDesc(@NonNull @Param("post") Post post,
                                                                      @NonNull @Param("beforeId") Long beforeId,
                                                                      @NonNull Pageable pageable);

    @Query("SELECT t FROM UserTag t JOIN FETCH t.taggedUser " +
            "WHERE t.post = :post AND t.isActive = true ORDER BY t.id DESC")
    List<UserTag> findByPostAndIsActiveTrueOrderByIdDesc(@NonNull @Param("post") Post post, @NonNull Pageable pageable);

    // ===== Additional Production Safety Methods =====

    @Query("SELECT ut FROM UserTag ut WHERE ut.post.id IN :postIds AND ut.isActive = true")
    List<UserTag> findActiveTagsByPostIds(@NonNull @Param("postIds") List<Long> postIds);

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