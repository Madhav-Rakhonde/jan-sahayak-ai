package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.SocialPostDto;
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
}
