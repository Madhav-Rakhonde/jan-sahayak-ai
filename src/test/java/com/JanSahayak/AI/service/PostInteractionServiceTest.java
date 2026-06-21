package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.DTO.SavedPostDto;
import com.JanSahayak.AI.DTO.SocialPostDto;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SavedPost;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.SavedPostRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.repository.PostViewRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.repository.PostLikeRepo;
import com.JanSahayak.AI.model.PostLike;
import com.JanSahayak.AI.service.NotificationService;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;

@ExtendWith(MockitoExtension.class)
public class PostInteractionServiceTest {

    @Mock
    private SavedPostRepo savedPostRepo;

    @Mock
    private PostService postService;

    @Mock
    private SocialPostService socialPostService;

    @Mock
    private PostRepo postRepository;

    @Mock
    private UserRepo userRepository;

    @Mock
    private PostLikeRepo postLikeRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SocialPostRepo socialPostRepository;

    @Mock
    private PostViewRepo postViewRepository;

    @Mock
    private InterestProfileService interestProfileService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private PostInteractionService selfProxy;

    @InjectMocks
    private PostInteractionService postInteractionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(postInteractionService, "socialPostService", socialPostService);
        ReflectionTestUtils.setField(postInteractionService, "postService", postService);
        ReflectionTestUtils.setField(postInteractionService, "cacheManager", cacheManager);
        ReflectionTestUtils.setField(postInteractionService, "notificationService", notificationService);
        postInteractionService.setSelf(selfProxy);
    }

    @Test
    void testGetSavedPostsForUser_HydratesNestedDtos() {
        // Arrange
        User user = new User();
        user.setId(1L);
        user.setUsername("testUser");

        SocialPost socialPost = new SocialPost();
        socialPost.setId(100L);
        socialPost.setContent("Test Social Post");

        Post issuePost = new Post();
        issuePost.setId(200L);
        issuePost.setContent("Test Issue Post");

        SavedPost spSocial = SavedPost.builder()
                .id(10L)
                .user(user)
                .socialPost(socialPost)
                .savedAt(new Date())
                .build();

        SavedPost spIssue = SavedPost.builder()
                .id(11L)
                .user(user)
                .post(issuePost)
                .savedAt(new Date())
                .build();

        Page<SavedPost> mockedPage = new PageImpl<>(java.util.Arrays.asList(spSocial, spIssue));

        when(savedPostRepo.findByUserIdOrderBySavedAtDesc(eq(user.getId()), any(Pageable.class)))
                .thenReturn(mockedPage);

        SocialPostDto mockSocialPostDto = new SocialPostDto();
        mockSocialPostDto.setId(100L);
        when(socialPostService.convertToDto(eq(socialPost), eq(user))).thenReturn(mockSocialPostDto);

        PostResponse mockPostResponse = new PostResponse();
        mockPostResponse.setId(200L);
        when(postService.convertToPostResponse(eq(issuePost), eq(user))).thenReturn(mockPostResponse);

        // Act
        Page<SavedPostDto> result = postInteractionService.getSavedPostsForUser(user, 0, 10);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getContent().size());

        SavedPostDto resultSocial = result.getContent().get(0);
        assertEquals(10L, resultSocial.getId());
        assertEquals("social", resultSocial.getType());
        assertNotNull(resultSocial.getSocialPost());
        assertEquals(100L, resultSocial.getSocialPost().getId());
        assertNull(resultSocial.getPost());

        SavedPostDto resultIssue = result.getContent().get(1);
        assertEquals(11L, resultIssue.getId());
        assertEquals("issue", resultIssue.getType());
        assertNotNull(resultIssue.getPost());
        assertEquals(200L, resultIssue.getPost().getId());
        assertNull(resultIssue.getSocialPost());

        // Verify that the hydration services were called with the correct user context
        verify(socialPostService, times(1)).convertToDto(eq(socialPost), eq(user));
        verify(postService, times(1)).convertToPostResponse(eq(issuePost), eq(user));
    }

    @Test
    void testLikePost_OptimisticUpdateAndAsyncDispatch() {
        // Arrange
        User user = new User();
        user.setId(1L);
        user.setUsername("testUser");

        Post post = new Post();
        post.setId(10L);
        post.setContent("Valid content to avoid validation exception");
        post.setLikeCount(5);
        post.setDislikeCount(2);
        post.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);

        // Mock currently unliked
        when(postLikeRepository.findByPostAndUserId(post, 1L)).thenReturn(Optional.empty());

        // Act
        boolean result = postInteractionService.likePost(post, user);

        // Assert
        assertTrue(result, "Should return true indicating the post is now liked");
        assertEquals(6, post.getLikeCount(), "Like count should be optimistically incremented");
        assertEquals(2, post.getDislikeCount(), "Dislike count should remain unchanged");

        // Verify async method was dispatched
        verify(selfProxy, times(1)).executeLikePostAsync(10L, 1L);
    }

    @Test
    void testExecuteLikePostAsync_NewLikeAndCacheUpdate() {
        // Arrange
        User user = new User();
        user.setId(1L);

        Post post = new Post();
        post.setId(10L);
        post.setLikeCount(6);
        User author = new User();
        author.setId(2L);
        post.setUser(author);

        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postLikeRepository.findByPostAndUserId(post, 1L)).thenReturn(Optional.empty());

        // Act
        postInteractionService.executeLikePostAsync(10L, 1L);

        // Assert
        verify(postLikeRepository, times(1)).save(any(PostLike.class));
        verify(postRepository, times(1)).incrementLikeCount(10L);
        try {
            verify(notificationService, times(1)).notifyPostLiked(post, user);
        } catch (Exception e) {
            fail("Exception in verification");
        }
    }

    @Test
    void testLikeSocialPost_OptimisticUpdateAndAsyncDispatch() {
        // Arrange
        User user = new User();
        user.setId(1L);
        user.setUsername("testUser");
        user.setIsActive(true);

        SocialPost socialPost = new SocialPost();
        socialPost.setId(100L);
        socialPost.setContent("Social content");
        socialPost.setLikeCount(5);
        socialPost.setDislikeCount(2);
        socialPost.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);
        socialPost.setUser(user);

        when(postLikeRepository.findBySocialPostAndUserId(socialPost, 1L)).thenReturn(Optional.empty());

        // Act
        boolean result = postInteractionService.likeSocialPost(socialPost, user);

        // Assert
        assertTrue(result, "Should return true indicating the social post is now liked");
        assertEquals(6, socialPost.getLikeCount(), "Like count should be optimistically incremented");
        assertEquals(2, socialPost.getDislikeCount(), "Dislike count should remain unchanged");

        verify(selfProxy, times(1)).executeLikeSocialPostAsync(100L, 1L, null);
    }

    @Test
    void testRecordSocialPostView_Success() {
        // Arrange
        User user = new User();
        user.setId(1L);
        user.setUsername("testUser");
        user.setIsActive(true);

        SocialPost socialPost = new SocialPost();
        socialPost.setId(100L);
        socialPost.setContent("Social content");
        socialPost.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);
        socialPost.setViewCount(10);
        socialPost.setUser(user);

        when(postViewRepository.findBySocialPostAndUserIdAndViewedAtAfter(eq(socialPost), eq(1L), any(Date.class)))
                .thenReturn(Optional.empty());
        when(postViewRepository.save(any(com.JanSahayak.AI.model.PostView.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        com.JanSahayak.AI.model.PostView result = postInteractionService.recordSocialPostView(socialPost, user);

        // Assert
        assertNotNull(result, "View should be recorded successfully");
        assertEquals(socialPost, result.getSocialPost());
        assertEquals(user, result.getUser());
        assertEquals(11, socialPost.getViewCount(), "View count should be incremented");

        verify(postViewRepository, times(1)).save(any(com.JanSahayak.AI.model.PostView.class));
        verify(socialPostRepository, times(1)).incrementViewCount(100L);
    }

    @Test
    void testToggleSocialPostSave_OptimisticUpdate() {
        // Arrange
        User user = new User();
        user.setId(1L);
        user.setUsername("testUser");
        user.setIsActive(true);

        SocialPost socialPost = new SocialPost();
        socialPost.setId(100L);
        socialPost.setContent("Social content");
        socialPost.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);
        socialPost.setSaveCount(5);
        socialPost.setUser(user);

        when(savedPostRepo.existsByUserIdAndSocialPost(1L, socialPost)).thenReturn(false);

        // Act
        boolean result = postInteractionService.toggleSocialPostSave(socialPost, user);

        // Assert
        assertTrue(result, "Should return true indicating the social post is now saved");
        assertEquals(6, socialPost.getSaveCount(), "Save count should be optimistically incremented");

        verify(selfProxy, times(1)).executeToggleSocialPostSaveAsync(100L, 1L);
    }
}
