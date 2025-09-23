package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Comment;
import com.JanSahayak.AI.model.Post;
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

    // Basic comment lookup methods
    List<Comment> findByPostOrderByCreatedAtAsc(Post post);

    // Find top-level comments (no parent comment)
    @Query("SELECT c FROM Comment c WHERE c.post = :post AND c.parentComment IS NULL ORDER BY c.createdAt ASC")
    List<Comment> findTopLevelCommentsByPost(@Param("post") Post post);

    // Count comments
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.post = :post")
    Long countByPost(@Param("post") Post post);

    List<Comment> findByParentCommentOrderByCreatedAtAsc(Comment parentComment);

    // FIXED: Replace the problematic method with proper @Query
    @Query("SELECT c FROM Comment c " +
            "JOIN FETCH c.post p " +
            "JOIN FETCH c.user u " +
            "WHERE c.user = :user " +
            "AND p.status IN ('ACTIVE', 'RESOLVED') " +
            "AND p.user.isActive = true " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findByUserWithVisiblePostsOrderByCreatedAtDesc(@Param("user") User user);

    // Paginated versions with proper @Query annotations
    List<Comment> findByPostOrderByCreatedAtAsc(Post post, Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.post = :post AND c.id < :beforeId ORDER BY c.createdAt ASC")
    List<Comment> findByPostAndIdLessThanOrderByCreatedAtAsc(@Param("post") Post post, @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.post = :post AND c.parentComment IS NULL ORDER BY c.createdAt ASC")
    List<Comment> findTopLevelCommentsByPost(@Param("post") Post post, Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.post = :post AND c.parentComment IS NULL AND c.id < :beforeId ORDER BY c.createdAt ASC")
    List<Comment> findTopLevelCommentsByPostAndIdLessThan(@Param("post") Post post, @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("SELECT c FROM Comment c " +
            "JOIN FETCH c.post p " +
            "JOIN FETCH c.user u " +
            "WHERE c.user = :user " +
            "AND p.status IN ('ACTIVE', 'RESOLVED') " +
            "AND p.user.isActive = true " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findByUserWithVisiblePostsOrderByCreatedAtDesc(@Param("user") User user, Pageable pageable);

    // FIXED: The problematic method with cursor support
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