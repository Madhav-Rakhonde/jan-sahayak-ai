package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.PostView;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.sql.Timestamp;

@Repository
public interface PostViewRepo extends JpaRepository<PostView, Long> {

    // For duplicate view prevention
    Optional<PostView> findByPostAndUserAndViewedAtAfter(Post post, User user, Date date);

    // For checking recent views
    Optional<PostView> findByPostAndUser(Post post, User user);

    // For statistics
    Long countByPostAndViewedAtAfter(Post post, Date date);

    // For cleanup or admin purposes
    List<PostView> findByPostOrderByViewedAtDesc(Post post, Pageable pageable);
    /**
     * Count total views for a post
     */
    @Query("SELECT COUNT(pv) FROM PostView pv WHERE pv.post = :post")
    Long countByPost(@Param("post") Post post);
}
