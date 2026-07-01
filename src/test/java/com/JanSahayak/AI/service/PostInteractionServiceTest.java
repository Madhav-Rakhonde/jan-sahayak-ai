package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.PostView;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.PostViewRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PostInteractionServiceTest {

    @Mock
    private PostRepo postRepository;

    @Mock
    private SocialPostRepo socialPostRepository;

    @Mock
    private PostViewRepo postViewRepository;

    @Mock
    private InterestProfileService interestProfileService;

    @Mock
    private PostInteractionService selfProxy;

    @InjectMocks
    private PostInteractionService postInteractionService;

    private User user;
    private Post post;
    private SocialPost socialPost;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("test_user");

        post = new Post();
        post.setId(100L);
        post.setContent("This is a test post content");
        post.setStatus(PostStatus.ACTIVE);

        socialPost = new SocialPost();
        socialPost.setId(200L);
        socialPost.setContent("This is a test social post content");
        socialPost.setStatus(PostStatus.ACTIVE);
        socialPost.setUser(user);

        ReflectionTestUtils.setField(postInteractionService, "self", selfProxy);
    }

    @Test
    void recordPostView_ShouldDispatchToAsyncAndNotSavePostEntity_PreventingLostUpdate() {
        // Arrange
        when(postViewRepository.findByPostAndUserIdAndViewedAtAfter(any(), anyLong(), any()))
                .thenReturn(Optional.empty());
        when(postViewRepository.save(any(PostView.class))).thenReturn(new PostView());

        // Act
        PostView result = postInteractionService.recordPostView(post, user);

        // Assert
        assertNotNull(result);
        
        // Ensure that the view row is saved
        verify(postViewRepository, times(1)).save(any(PostView.class));
        
        // Ensure that the increment is dispatched to the @Async proxy method
        verify(selfProxy, times(1)).executeIncrementPostViewAsync(post.getId());
        
        // CRITICAL CHECK: Ensure the main post entity is NEVER saved via JPA save(), preventing "Lost Update"
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void recordSocialPostView_ShouldDispatchToAsyncAndNotSaveSocialPostEntity_PreventingLostUpdate() {
        // Arrange
        when(postViewRepository.findBySocialPostAndUserIdAndViewedAtAfter(any(), anyLong(), any()))
                .thenReturn(Optional.empty());
        when(postViewRepository.save(any(PostView.class))).thenReturn(new PostView());

        // Act
        PostView result = postInteractionService.recordSocialPostView(socialPost, user);

        // Assert
        assertNotNull(result);
        
        // Ensure that the view row is saved
        verify(postViewRepository, times(1)).save(any(PostView.class));
        
        // Ensure that the increment is dispatched to the @Async proxy method
        verify(selfProxy, times(1)).executeIncrementSocialPostViewAsync(socialPost.getId());
        
        // CRITICAL CHECK: Ensure the main social post entity is NEVER saved via JPA save(), preventing "Lost Update"
        verify(socialPostRepository, never()).save(any(SocialPost.class));
    }
}
