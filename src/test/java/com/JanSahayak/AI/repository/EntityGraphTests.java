package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EntityGraphTests {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private SocialPostRepo socialPostRepo;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User savedUser;
    private Long savedPostId;
    private Long savedSocialPostId;

    @BeforeEach
    void setUp() {
        transactionTemplate.execute(status -> {
            // Cleanup previous test data to prevent interference
            postRepo.deleteAll();
            socialPostRepo.deleteAll();

            Role userRole = roleRepo.findByName("ROLE_USER")
                    .orElseGet(() -> {
                        Role r = new Role();
                        r.setName("ROLE_USER");
                        return roleRepo.save(r);
                    });

            User user = new User();
            user.setUsername("testuser_" + UUID.randomUUID().toString().substring(0, 8));
            user.setEmail("test_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
            user.setPassword("password123");
            user.setRole(userRole);
            user.setPincode("411001");
            user.setIsEmailVerified(true);
            savedUser = userRepo.save(user);

            Post post = new Post();
            post.setUser(savedUser);
            post.setContent("This is a test broadcast post.");
            post.setStatus(PostStatus.ACTIVE);
            post.setBroadcastScope(BroadcastScope.COUNTRY);
            post.setTargetCountry("IN");
            post.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));
            post.setLikeCount(0);
            post.setDislikeCount(0);
            post.setCommentCount(0);
            post.setViewCount(0);
            post.setShareCount(0);
            post.setSaveCount(0);
            Post savedPost = postRepo.save(post);
            savedPostId = savedPost.getId();

            SocialPost socialPost = SocialPost.builder()
                    .user(savedUser)
                    .content("This is a test social post.")
                    .pincode("411001")
                    .isViral(false)
                    .reportCount(0)
                    .isFlagged(false)
                    .allowComments(true)
                    .build();
            SocialPost savedSocial = socialPostRepo.save(socialPost);
            savedSocialPostId = savedSocial.getId();

            return null;
        });
    }

    @Test
    @DisplayName("Verify PostRepo queries eagerly fetch user association using EntityGraph")
    void testPostRepoEntityGraph() {
        transactionTemplate.execute(status -> {
            // Test findByUserIdOrderByCreatedAtDesc
            List<Post> postsByUserId = postRepo.findByUserIdOrderByCreatedAtDesc(savedUser.getId());
            assertThat(postsByUserId).isNotEmpty();
            assertThat(Hibernate.isInitialized(postsByUserId.get(0).getUser())).isTrue();
            assertThat(postsByUserId.get(0).getUser().getUsername()).isEqualTo(savedUser.getUsername());

            // Test findByBroadcastScopeIsNotNullOrderByCreatedAtDesc
            List<Post> broadcastPosts = postRepo.findByBroadcastScopeIsNotNullOrderByCreatedAtDesc();
            assertThat(broadcastPosts).isNotEmpty();
            assertThat(Hibernate.isInitialized(broadcastPosts.get(0).getUser())).isTrue();

            // Test findByTargetCountry
            List<Post> countryPosts = postRepo.findByTargetCountry("IN");
            assertThat(countryPosts).isNotEmpty();
            assertThat(Hibernate.isInitialized(countryPosts.get(0).getUser())).isTrue();

            return null;
        });
    }

    @Test
    @DisplayName("Verify SocialPostRepo queries eagerly fetch user association using EntityGraph")
    void testSocialPostRepoEntityGraph() {
        transactionTemplate.execute(status -> {
            // Test findRecentPostsByUser
            List<SocialPost> socialPosts = socialPostRepo.findRecentPostsByUser(savedUser.getId(), PageRequest.of(0, 10));
            assertThat(socialPosts).isNotEmpty();
            assertThat(Hibernate.isInitialized(socialPosts.get(0).getUser())).isTrue();
            assertThat(socialPosts.get(0).getUser().getUsername()).isEqualTo(savedUser.getUsername());

            return null;
        });
    }
}
