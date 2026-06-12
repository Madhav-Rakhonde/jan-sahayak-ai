package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.UserTag;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PostServiceTest {

    @Mock
    private PostRepo postRepository;

    @Mock
    private UserRepo userRepository;

    @Mock
    private TextSimilarityService textSimilarityService;

    @InjectMocks
    private PostService postService;

    @Test
    void testCheckDuplicatePosts_NoDuplicates() {
        String pincode = "411001";
        String content = "There is a huge pothole";
        List<String> tags = Collections.singletonList("dept1");

        User deptUser = mock(User.class);
        when(deptUser.isDepartment()).thenReturn(true);
        when(userRepository.findByUsernameInAndIsActiveTrue(anyList())).thenReturn(Collections.singletonList(deptUser));

        Post oldPost = new Post();
        oldPost.setContent("Water leak on main street");
        
        UserTag oldTag = mock(UserTag.class);
        User oldTaggedUser = mock(User.class);
        when(oldTag.getTaggedUser()).thenReturn(oldTaggedUser);
        when(oldTaggedUser.getActualUsername()).thenReturn("dept1");
        oldPost.setUserTags(Collections.singletonList(oldTag));

        when(postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByIdDesc(
                any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(oldPost));

        when(textSimilarityService.calculateSimilarity(content, oldPost.getContent())).thenReturn(0.2);

        Post duplicate = postService.checkDuplicatePosts(pincode, content, tags);

        assertNull(duplicate, "Should return null when similarity is below threshold");
    }

    @Test
    void testCheckDuplicatePosts_DuplicateFound() {
        String pincode = "411001";
        String content = "There is a huge pothole";
        List<String> tags = Collections.singletonList("dept1");

        User deptUser = mock(User.class);
        when(deptUser.isDepartment()).thenReturn(true);
        when(userRepository.findByUsernameInAndIsActiveTrue(anyList())).thenReturn(Collections.singletonList(deptUser));

        Post oldPost = new Post();
        oldPost.setContent("Huge pothole reported here");
        
        UserTag oldTag = mock(UserTag.class);
        User oldTaggedUser = mock(User.class);
        when(oldTag.getTaggedUser()).thenReturn(oldTaggedUser);
        when(oldTaggedUser.getActualUsername()).thenReturn("dept1");
        oldPost.setUserTags(Collections.singletonList(oldTag));

        when(postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByIdDesc(
                any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(oldPost));

        when(textSimilarityService.calculateSimilarity(content, oldPost.getContent())).thenReturn(0.8);

        Post duplicate = postService.checkDuplicatePosts(pincode, content, tags);

        assertNotNull(duplicate, "Should return the duplicate post when similarity is above threshold");
        assertEquals(oldPost, duplicate);
    }
    
    @Test
    void testCheckDuplicatePosts_NotADepartment() {
        String pincode = "411001";
        String content = "There is a huge pothole";
        List<String> tags = Collections.singletonList("normalUser");

        User normalUser = mock(User.class);
        when(normalUser.isDepartment()).thenReturn(false);
        when(userRepository.findByUsernameInAndIsActiveTrue(anyList())).thenReturn(Collections.singletonList(normalUser));

        Post duplicate = postService.checkDuplicatePosts(pincode, content, tags);

        assertNull(duplicate, "Should return null because no department is tagged");
        verify(postRepository, never()).findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByIdDesc(any(), any(), any(), any());
    }

    @Test
    void testGetPostsByUser_WithCursor() {
        Long userId = 1L;
        Long cursor = 50L;
        Integer limit = 10;
        
        Post post = new Post();
        post.setId(49L);
        List<Post> mockedPosts = Collections.singletonList(post);
        
        when(postRepository.findByUserIdWithUserAndStatusInAndIdLessThanOrderByCreatedAtDesc(
                eq(userId), anyList(), eq(cursor), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(mockedPosts);
                
        com.JanSahayak.AI.DTO.PaginatedResponse<Post> response = postService.getPostsByUser(userId, cursor, limit);
        
        assertNotNull(response);
        assertEquals(1, response.getData().size());
        assertEquals(49L, response.getData().get(0).getId());
        
        verify(postRepository, times(1)).findByUserIdWithUserAndStatusInAndIdLessThanOrderByCreatedAtDesc(
                eq(userId), anyList(), eq(cursor), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void testGetPostsByUser_WithoutCursor() {
        Long userId = 1L;
        Integer limit = 10;
        
        Post post = new Post();
        post.setId(50L);
        List<Post> mockedPosts = Collections.singletonList(post);
        
        when(postRepository.findByUserIdWithUserAndStatusInOrderByCreatedAtDesc(
                eq(userId), anyList()))
                .thenReturn(mockedPosts);
                
        com.JanSahayak.AI.DTO.PaginatedResponse<Post> response = postService.getPostsByUser(userId, null, limit);
        
        assertNotNull(response);
        assertEquals(1, response.getData().size());
        assertEquals(50L, response.getData().get(0).getId());
        
        verify(postRepository, times(1)).findByUserIdWithUserAndStatusInOrderByCreatedAtDesc(
                eq(userId), anyList());
    }
    @Test
    void testGetPostsTaggedWithUser_WithCursor() {
        Long userId = 1L;
        Long cursor = 100L;
        Integer limit = 10;

        User user = new User();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));

        Post post = new Post();
        post.setId(99L);
        List<Post> mockedPosts = Collections.singletonList(post);

        when(postRepository.findPostsTaggedWithUserIdAndIdLessThan(
                eq(userId), eq(cursor), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(mockedPosts);

        com.JanSahayak.AI.DTO.PaginatedResponse<Post> response = postService.getPostsTaggedWithUser(userId, cursor, limit);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
        assertEquals(99L, response.getData().get(0).getId());

        verify(postRepository, times(1)).findPostsTaggedWithUserIdAndIdLessThan(
                eq(userId), eq(cursor), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void testCountPostsByUser() {
        Long userId = 1L;
        User user = new User();
        user.setId(userId);

        when(postRepository.countByUserId(userId)).thenReturn(42L);

        Long count = postService.countPostsByUser(user);

        assertEquals(42L, count);
        verify(postRepository, times(1)).countByUserId(userId);
    }
    @Test
    void testGetBroadcastStatistics_FiltersDeletedPosts() {
        when(postRepository.countByBroadcastScopeIsNotNullAndStatusNot(PostStatus.DELETED)).thenReturn(100L);
        when(postRepository.countByBroadcastScopeIsNotNullAndStatus(PostStatus.ACTIVE)).thenReturn(90L);
        when(postRepository.countByBroadcastScopeAndTargetCountryAndStatusNot(eq(BroadcastScope.COUNTRY), anyString(), eq(PostStatus.DELETED))).thenReturn(20L);

        for (BroadcastScope scope : BroadcastScope.values()) {
            when(postRepository.countByBroadcastScopeAndStatusNot(scope, PostStatus.DELETED)).thenReturn(10L);
            when(postRepository.countByBroadcastScopeAndStatus(scope, PostStatus.ACTIVE)).thenReturn(8L);
        }

        java.util.Map<String, Long> stats = postService.getBroadcastStatistics();

        assertEquals(100L, stats.get("totalBroadcasts"));
        assertEquals(90L, stats.get("activeBroadcasts"));
        assertEquals(20L, stats.get("countryWideBroadcasts"));
        
        for (BroadcastScope scope : BroadcastScope.values()) {
            assertEquals(10L, stats.get("broadcasts" + scope.name()));
            assertEquals(8L, stats.get("activeBroadcasts" + scope.name()));
        }

        verify(postRepository).countByBroadcastScopeIsNotNullAndStatusNot(PostStatus.DELETED);
    }

    @Test
    void testGetBroadcastAnalytics_FiltersDeletedPosts() {
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        user.setUsername("testUser");
        com.JanSahayak.AI.model.Role role = new com.JanSahayak.AI.model.Role();
        role.setName("ROLE_ADMIN");
        user.setRole(role);

        when(postRepository.countByUserIdAndBroadcastScopeIsNotNullAndStatusNot(userId, PostStatus.DELETED)).thenReturn(50L);
        when(postRepository.countByUserIdAndBroadcastScopeIsNotNullAndCreatedAtAfterAndStatusNot(eq(userId), any(), eq(PostStatus.DELETED))).thenReturn(10L);
        when(postRepository.countByUserIdAndBroadcastScopeAndTargetCountryAndStatusNot(eq(userId), eq(BroadcastScope.COUNTRY), anyString(), eq(PostStatus.DELETED))).thenReturn(5L);

        for (BroadcastScope scope : BroadcastScope.values()) {
            when(postRepository.countByUserIdAndBroadcastScopeAndStatusNot(userId, scope, PostStatus.DELETED)).thenReturn(2L);
        }
        
        Post mockPost = new Post();
        mockPost.setLikeCount(10);
        mockPost.setCommentCount(5);
        mockPost.setViewCount(100);
        mockPost.setShareCount(2);
        
        when(postRepository.findByUserIdAndBroadcastScopeIsNotNullAndStatusNot(userId, PostStatus.DELETED))
            .thenReturn(Collections.singletonList(mockPost));

        java.util.Map<String, Object> analytics = postService.getBroadcastAnalytics(user, 7);

        assertEquals(50L, analytics.get("totalBroadcastsCreated"));
        assertEquals(10L, analytics.get("recentBroadcasts"));
        assertEquals(5L, analytics.get("countryWideBroadcasts"));
        
        assertEquals(10.0, analytics.get("averageLikes"));
        assertEquals(5.0, analytics.get("averageComments"));

        verify(postRepository).countByUserIdAndBroadcastScopeIsNotNullAndStatusNot(userId, PostStatus.DELETED);
    }
}
