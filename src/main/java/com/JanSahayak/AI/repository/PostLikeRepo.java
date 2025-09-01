package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.PostLike;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.*;
import java.util.Optional;

@Repository
public interface PostLikeRepo extends JpaRepository<PostLike, Long> {
    boolean existsByPostAndUser(Post post, User user);


}
