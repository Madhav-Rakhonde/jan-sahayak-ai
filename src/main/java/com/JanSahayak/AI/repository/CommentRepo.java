package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Comment;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface CommentRepo extends JpaRepository<Comment, Long> {

    // Count comments for a post
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.post = :post")
    Long countByPost(@Param("post") Post post);

    // ── SocialPost comment queries ────────────────────────────────────────────

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.socialPost = :socialPost")
    Long countBySocialPost(@Param("socialPost") SocialPost socialPost);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.socialPost.id = :socialPostId AND c.parentComment IS NULL ORDER BY c.createdAt DESC")
    List<Comment> findTopLevelCommentsBySocialPostId(
            @Param("socialPostId") Long socialPostId, Pageable pageable);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.socialPost.id = :socialPostId AND c.parentComment IS NULL AND c.id < :id ORDER BY c.createdAt DESC")
    List<Comment> findTopLevelCommentsBySocialPostIdAndIdLessThan(
            @Param("socialPostId") Long socialPostId,
            @Param("id") Long id,
            Pageable pageable);

    // ── All comments for SocialPost (Fixed for performance & pagination) ──

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.socialPost.id = :socialPostId ORDER BY c.createdAt DESC")
    List<Comment> findBySocialPostIdOrderByCreatedAtDesc(@Param("socialPostId") Long socialPostId, Pageable pageable);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.socialPost.id = :socialPostId AND c.id < :beforeId ORDER BY c.createdAt DESC")
    List<Comment> findBySocialPostIdAndIdLessThanOrderByCreatedAtDesc(@Param("socialPostId") Long socialPostId, @Param("beforeId") Long beforeId, Pageable pageable);


    // ── Top-level comment queries (with JOIN FETCH) ───────────────────────────

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.post = :post AND c.parentComment IS NULL ORDER BY c.createdAt DESC")
    List<Comment> findTopLevelCommentsByPost(@Param("post") Post post);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.post = :post AND c.parentComment IS NULL ORDER BY c.createdAt DESC")
    List<Comment> findTopLevelCommentsByPost(@Param("post") Post post, Pageable pageable);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.post = :post AND c.parentComment IS NULL AND c.id < :beforeId ORDER BY c.createdAt DESC")
    List<Comment> findTopLevelCommentsByPostAndIdLessThan(@Param("post") Post post, @Param("beforeId") Long beforeId, Pageable pageable);

    // ── Paginated post comment queries ────────────────────────────────────────

    /**
     * FIX: Added JOIN FETCH c.user to eliminate N lazy-load queries.
     *
     * The original findByPostOrderByCreatedAtAsc() returned Comment entities without
     * fetching the user. Every call to comment.getUser().getActualUsername() (in
     * CommentDto.fromComment()) then triggered a separate SELECT — one per comment.
     * For a page of 20 comments = 20 extra queries.
     *
     * JOIN FETCH loads the user in the same query, reducing 20+1 queries to 1.
     */
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.post = :post ORDER BY c.createdAt DESC")
    List<Comment> findByPostOrderByCreatedAtDesc(@Param("post") Post post, Pageable pageable);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.post = :post AND c.id < :beforeId ORDER BY c.createdAt DESC")
    List<Comment> findByPostAndIdLessThanOrderByCreatedAtDesc(@Param("post") Post post, @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("SELECT c FROM Comment c " +
            "JOIN FETCH c.post p " +
            "JOIN FETCH c.user u " +
            "WHERE c.user = :user " +
            "AND p.status IN (com.JanSahayak.AI.enums.PostStatus.ACTIVE, com.JanSahayak.AI.enums.PostStatus.RESOLVED) " +
            "AND p.user.isActive = true " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findByUserWithVisiblePostsOrderByCreatedAtDesc(@Param("user") User user, Pageable pageable);

    @Query("SELECT c FROM Comment c " +
            "JOIN FETCH c.post p " +
            "JOIN FETCH c.user u " +
            "WHERE c.user = :user " +
            "AND p.status IN (com.JanSahayak.AI.enums.PostStatus.ACTIVE, com.JanSahayak.AI.enums.PostStatus.RESOLVED) " +
            "AND p.user.isActive = true " +
            "AND c.id < :beforeId " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findByUserWithVisiblePostsAndIdLessThanOrderByCreatedAtDesc(
            @Param("user") User user,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    /**
     * FIX: Added JOIN FETCH c.user to eliminate N lazy-load queries when iterating replies.
     */
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.parentComment = :parentComment ORDER BY c.createdAt DESC")
    List<Comment> findByParentCommentOrderByCreatedAtDesc(@Param("parentComment") Comment parentComment, Pageable pageable);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.parentComment = :parentComment AND c.id < :beforeId ORDER BY c.createdAt DESC")
    List<Comment> findByParentCommentAndIdLessThanOrderByCreatedAtDesc(@Param("parentComment") Comment parentComment, @Param("beforeId") Long beforeId, Pageable pageable);

    List<Comment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.id < :beforeId ORDER BY c.createdAt DESC")
    List<Comment> findByIdLessThanOrderByCreatedAtDesc(@Param("beforeId") Long beforeId, Pageable pageable);

    List<Comment> findByCreatedAtAfterOrderByCreatedAtDesc(Date fromDate, Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.createdAt > :fromDate AND c.id < :beforeId ORDER BY c.createdAt DESC")
    List<Comment> findByCreatedAtAfterAndIdLessThanOrderByCreatedAtDesc(@Param("fromDate") Date fromDate, @Param("beforeId") Long beforeId, Pageable pageable);

    List<Comment> findByTextContainingIgnoreCaseOrderByCreatedAtDesc(String searchTerm, Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE LOWER(c.text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND c.id < :beforeId ORDER BY c.createdAt DESC")
    List<Comment> findByTextContainingIgnoreCaseAndIdLessThanOrderByCreatedAtDesc(@Param("searchTerm") String searchTerm, @Param("beforeId") Long beforeId, Pageable pageable);
}