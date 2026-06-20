package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.SocialPostDto;
import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
public class SocialPostServiceTest {

    @Mock
    private SocialPostRepo socialPostRepository;

    @Mock
    private UserRepo userRepository;

    @Mock
    private PollRepository pollRepository;

    @Mock
    private PollVoteRepository pollVoteRepository;

    @Mock
    private CommunityMemberRepo communityMemberRepository;

    @Mock
    private ContentValidationService contentValidationService;

    @Mock
    private SocialPostMediaService mediaService;

    @Mock
    private PostInteractionService postInteractionService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CommunityMemberRepo communityMemberRepo;

    @Mock
    private TranslationService translationService;

    @Mock
    private TopicAggregationWorker topicAggregationWorker;

    @Mock
    private CommunityService communityService;

    @Mock
    private InterestProfileService interestProfileService;

    @Mock
    private HLIGFeedService hligFeedService;

    @Mock
    private CommunityRepo communityRepository;

    @InjectMocks
    private SocialPostService socialPostService;

    @Test
    void testCountSocialPostsByUserId() {
        Long userId = 101L;

        when(socialPostRepository.countByUserIdAndStatus(userId, PostStatus.ACTIVE)).thenReturn(15L);

        Long count = socialPostService.countSocialPostsByUserId(userId);

        assertEquals(15L, count);
        verify(socialPostRepository, times(1)).countByUserIdAndStatus(userId, PostStatus.ACTIVE);
    }

    @Test
    void testConvertToDtoBatch_WithPreFetchedRoles() {
        // Arrange
        User author = new User();
        author.setId(101L);
        author.setUsername("author1");
        author.setIsActive(true);

        SocialPost post = new SocialPost();
        post.setId(1L);
        post.setUser(author);
        post.setContent("This is a social post");
        post.setCreatedAt(new Date());

        List<SocialPost> posts = List.of(post);
        User currentUser = new User();
        currentUser.setId(201L);

        // Mock userRepository.findUserRolesByUserIds
        List<Object[]> mockRolesData = new ArrayList<>();
        mockRolesData.add(new Object[]{101L, "ROLE_ADMIN"});
        when(userRepository.findUserRolesByUserIds(List.of(101L))).thenReturn(mockRolesData);

        // Act
        SocialPostDto dto = socialPostService.convertToDto(post, currentUser);

        // Assert
        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertNotNull(dto.getAuthor());
        assertEquals("ROLE_ADMIN", dto.getAuthor().getRoleName());
        verify(userRepository, times(1)).findUserRolesByUserIds(List.of(101L));
    }

    @Test
    void testConvertToDtoBatch_WhenRoleQueryThrowsError_Fallback() {
        // Arrange
        User author = new User();
        author.setId(102L);
        author.setUsername("author2");
        author.setIsActive(true);

        Role mockRole = mock(Role.class);
        lenient().when(mockRole.getName()).thenThrow(new org.hibernate.LazyInitializationException("Lazy load failed"));
        author.setRole(mockRole);

        SocialPost post = new SocialPost();
        post.setId(2L);
        post.setUser(author);
        post.setContent("Another post");
        post.setCreatedAt(new Date());

        List<SocialPost> posts = List.of(post);
        User currentUser = new User();
        currentUser.setId(201L);

        when(userRepository.findUserRolesByUserIds(List.of(102L)))
                .thenThrow(new RuntimeException("Database down"));

        // Act
        SocialPostDto dto = socialPostService.convertToDto(post, currentUser);

        // Assert
        assertNotNull(dto, "Should convert post even if role loading fails");
        assertEquals(2L, dto.getId());
        assertNotNull(dto.getAuthor());
        assertNull(dto.getAuthor().getRoleName(), "Role name should fallback to null on error");
        verify(userRepository, times(1)).findUserRolesByUserIds(List.of(102L));
    }

    @Test
    void testTransactionalAnnotationsOnFeedMethods() {
        Class<SocialPostService> clazz = SocialPostService.class;
        
        List<String> feedMethods = List.of(
            "getBrowseFeed", "getLocalPosts", "getPersonalisedFeed", 
            "getHotFeed", "getNewFeed", "getTopFeed", "getFollowingFeed"
        );
        
        for (String methodName : feedMethods) {
            java.lang.reflect.Method method = null;
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    method = m;
                    break;
                }
            }
            assertNotNull(method, "Method " + methodName + " should exist in SocialPostService");
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertNotNull(transactional, "Method " + methodName + " should be annotated with @Transactional");
            assertTrue(transactional.readOnly(), "Method " + methodName + " should be read-only transaction");
            assertNotEquals(org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED, transactional.propagation(),
                    "Method " + methodName + " should NOT suspend transactions");
        }
    }

    @Test
    void testConvertToDto_WithCommunityMappedSuccessfully() {
        // Arrange
        User author = new User();
        author.setId(101L);
        author.setUsername("author1");
        author.setIsActive(true);

        Community community = new Community();
        community.setId(5L);
        community.setName("Test Community");
        community.setAvatarUrl("avatar.png");
        community.setMemberCount(15);

        SocialPost post = new SocialPost();
        post.setId(1L);
        post.setUser(author);
        post.setContent("Post in community");
        post.setCreatedAt(new Date());
        post.setCommunity(community);

        when(userRepository.findUserRolesByUserIds(anyList())).thenReturn(Collections.emptyList());
        when(communityRepository.findAllById(List.of(5L))).thenReturn(List.of(community));

        // Act
        SocialPostDto dto = socialPostService.convertToDto(post, null);

        // Assert
        assertNotNull(dto);
        assertEquals("community", dto.getVariant());
        assertEquals(5L, dto.getCommunityId());
        assertEquals("Test Community", dto.getCommunityName());
        assertEquals("avatar.png", dto.getCommunityAvatar());
        assertEquals(15, dto.getCommunityMemberCount());
    }

    @Test
    void testConvertToDto_WithCommunityLazyInitializationException() {
        // Arrange
        User author = new User();
        author.setId(101L);
        author.setUsername("author1");
        author.setIsActive(true);

        SocialPost post = mock(SocialPost.class);
        when(post.getId()).thenReturn(1L);
        when(post.getUser()).thenReturn(author);
        when(post.getContent()).thenReturn("Post in community");
        when(post.getCreatedAt()).thenReturn(new Date());
        when(post.getCommunityId()).thenReturn(5L);

        when(userRepository.findUserRolesByUserIds(anyList())).thenReturn(Collections.emptyList());
        when(communityRepository.findAllById(List.of(5L))).thenReturn(Collections.emptyList());

        // Act
        SocialPostDto dto = socialPostService.convertToDto(post, null);

        // Assert
        assertNotNull(dto);
        assertEquals("community", dto.getVariant());
        assertEquals(5L, dto.getCommunityId());
        assertNull(dto.getCommunityName());
        assertNull(dto.getCommunityAvatar());
        assertNull(dto.getCommunityMemberCount());
    }

    @Test
    void testGetUserPosts_UsesBatchMapping() {
        // Arrange
        User author = new User();
        author.setId(101L);
        author.setUsername("author1");
        author.setIsActive(true);

        SocialPost p1 = new SocialPost();
        p1.setId(1L);
        p1.setUser(author);
        p1.setStatus(PostStatus.ACTIVE);
        p1.setContent("User post 1");

        SocialPost p2 = new SocialPost();
        p2.setId(2L);
        p2.setUser(author);
        p2.setStatus(PostStatus.ACTIVE);
        p2.setContent("User post 2");

        List<SocialPost> posts = List.of(p1, p2);
        when(socialPostRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(eq(101L), anyList(), any()))
                .thenReturn(posts);

        // We mock eager refetching in convertToDtoBatch
        when(socialPostRepository.findAllByIdsWithUserAndCommunity(List.of(1L, 2L)))
                .thenReturn(posts);

        when(userRepository.findUserRolesByUserIds(List.of(101L)))
                .thenReturn(Collections.emptyList());

        // Act
        PaginatedResponse<SocialPostDto> response = socialPostService.getUserPosts(101L, author, null, 10);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getData().size());
        verify(userRepository, times(1)).findUserRolesByUserIds(anyList()); // should be batched (1 invocation)
    }

    @Test
    void testGetSavedPosts_UsesBatchMapping() {
        // Arrange
        User user = new User();
        user.setId(201L);
        user.setUsername("testuser");
        user.setIsActive(true);

        com.JanSahayak.AI.DTO.SavedPostDto sd1 = new com.JanSahayak.AI.DTO.SavedPostDto();
        sd1.setSocialPostId(10L);
        com.JanSahayak.AI.DTO.SavedPostDto sd2 = new com.JanSahayak.AI.DTO.SavedPostDto();
        sd2.setSocialPostId(11L);

        org.springframework.data.domain.Page<com.JanSahayak.AI.DTO.SavedPostDto> page = 
                new org.springframework.data.domain.PageImpl<>(List.of(sd1, sd2));

        when(postInteractionService.getSavedPostsForUser(eq(user), eq(0), anyInt()))
                .thenReturn(page);

        SocialPost sp1 = new SocialPost();
        sp1.setId(10L);
        sp1.setUser(user);
        sp1.setStatus(PostStatus.ACTIVE);
        sp1.setContent("Saved post 1");

        SocialPost sp2 = new SocialPost();
        sp2.setId(11L);
        sp2.setUser(user);
        sp2.setStatus(PostStatus.ACTIVE);
        sp2.setContent("Saved post 2");

        when(socialPostRepository.findAllById(List.of(10L, 11L)))
                .thenReturn(List.of(sp1, sp2));

        // Mock eager refetching in convertToDtoBatch
        when(socialPostRepository.findAllByIdsWithUserAndCommunity(List.of(10L, 11L)))
                .thenReturn(List.of(sp1, sp2));

        when(userRepository.findUserRolesByUserIds(anyList())).thenReturn(Collections.emptyList());

        // Act
        PaginatedResponse<SocialPostDto> response = socialPostService.getSavedPosts(user, null, 10);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getData().size());
        verify(userRepository, times(1)).findUserRolesByUserIds(anyList()); // batch mapping verified
    }
}
