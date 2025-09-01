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



    // FIXED: Removed duplicate method - keeping only the Timestamp version
    @Query("SELECT pv FROM PostView pv WHERE pv.user = :user AND pv.post = :post AND pv.viewedAt >= :recentTime")
    Optional<PostView> findRecentViewByUserAndPost(@Param("user") User user,
                                                   @Param("post") Post post,
                                                   @Param("recentTime") Timestamp recentTime);


}
