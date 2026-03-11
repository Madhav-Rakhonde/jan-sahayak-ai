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
    // Add these methods to CommentRepo.java

    List<Comment> findBySocialPostAndIdLessThanOrderByCreatedAtAsc(
            SocialPost socialPost, Long id, Pageable pageable);

    List<Comment> findBySocialPostOrderByCreatedAtAsc(
            SocialPost socialPost, Pageable pageable);

    Long countBySocialPost(SocialPost socialPost);

    @Query("SELECT c FROM Comment c WHERE c.socialPost = :socialPost AND c.parentComment IS NULL")
    List<Comment> findTopLevelCommentsBySocialPost(
            @Param("socialPost") SocialPost socialPost, Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.socialPost = :socialPost AND c.parentComment IS NULL AND c.id < :id")
    List<Comment> findTopLevelCommentsBySocialPostAndIdLessThan(
            @Param("socialPost") SocialPost socialPost,
            @Param("id") Long id,
            Pageable pageable);

    // ======== CORRECTED TOP-LEVEL COMMENT METHODS ========
    // This is the corrected non-paginated method for fetching top-level comments.
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.post = :post AND c.parentComment IS NULL ORDER BY c.createdAt ASC")
    List<Comment> findTopLevelCommentsByPost(@Param("post") Post post);

    // This is the corrected paginated method for fetching top-level comments.
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.post = :post AND c.parentComment IS NULL ORDER BY c.createdAt ASC")
    List<Comment> findTopLevelCommentsByPost(@Param("post") Post post, Pageable pageable);

    // This is the corrected paginated method with a cursor for fetching top-level comments.
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.post = :post AND c.parentComment IS NULL AND c.id < :beforeId ORDER BY c.createdAt ASC")
    List<Comment> findTopLevelCommentsByPostAndIdLessThan(@Param("post") Post post, @Param("beforeId") Long beforeId, Pageable pageable);

    // ======== OTHER PAGINATED METHODS ========
    List<Comment> findByPostOrderByCreatedAtAsc(Post post, Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.post = :post AND c.id < :beforeId ORDER BY c.createdAt ASC")
    List<Comment> findByPostAndIdLessThanOrderByCreatedAtAsc(@Param("post") Post post, @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("SELECT c FROM Comment c " +
            "JOIN FETCH c.post p " +
            "JOIN FETCH c.user u " +
            "WHERE c.user = :user " +
            "AND p.status IN ('ACTIVE', 'RESOLVED') " +
            "AND p.user.isActive = true " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findByUserWithVisiblePostsOrderByCreatedAtDesc(@Param("user") User user, Pageable pageable);

    @Query("SELECT c FROM Comment c " +
            "JOIN FETCH c.post p " +
            "JOIN FETCH c.user u " +
            "WHERE c.user = :user " +
            "AND p.status IN ('ACTIVE', 'RESOLVED') " +
            "AND p.user.isActive = true " +
            "AND c.id < :beforeId " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findByUserWithVisiblePostsAndIdLessThanOrderByCreatedAtDesc(
            @Param("user") User user,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    List<Comment> findByParentCommentOrderByCreatedAtAsc(Comment parentComment, Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.parentComment = :parentComment AND c.id < :beforeId ORDER BY c.createdAt ASC")
    List<Comment> findByParentCommentAndIdLessThanOrderByCreatedAtAsc(@Param("parentComment") Comment parentComment, @Param("beforeId") Long beforeId, Pageable pageable);

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