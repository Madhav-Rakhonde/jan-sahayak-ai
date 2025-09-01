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

    @Query("SELECT c FROM Comment c " +
            "JOIN FETCH c.post p " +
            "JOIN FETCH c.user u " +
            "WHERE c.user = :user " +
            "AND p.status IN ('ACTIVE', 'RESOLVED') " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findByUserWithVisiblePostsOrderByCreatedAtDesc(@Param("user") User user);

}