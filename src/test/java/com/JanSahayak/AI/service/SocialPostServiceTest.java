package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.SocialPostDto;
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

    @InjectMocks
    private SocialPostService socialPostService;

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
        
        // Mock getCommunity to throw LazyInitializationException
        when(post.getCommunity()).thenThrow(new org.hibernate.LazyInitializationException("Lazy initialization failed"));

        when(userRepository.findUserRolesByUserIds(anyList())).thenReturn(Collections.emptyList());

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
}
