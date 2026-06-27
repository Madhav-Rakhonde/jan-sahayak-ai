package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.SocialPostCreateDto;
import com.JanSahayak.AI.DTO.SocialPostDto;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.util.IdempotencyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocialPostServiceIdempotencyTest {

    @Mock
    private SocialPostRepo socialPostRepository;

    @Mock
    private com.JanSahayak.AI.repository.PollRepository pollRepository;

    @Mock
    private com.JanSahayak.AI.repository.CommunityRepo communityRepository;

    @Mock
    private com.JanSahayak.AI.repository.CommunityMemberRepo communityMemberRepository;

    @Mock
    private com.JanSahayak.AI.service.PostInteractionService postInteractionService;

    @Mock
    private ContentValidationService contentValidationService;

    @InjectMocks
    private SocialPostService socialPostService;

    @BeforeEach
    void setUp() {
        IdempotencyContext.clear();
    }

    @AfterEach
    void tearDown() {
        IdempotencyContext.clear();
    }

    @Test
    void testCreateSocialPost_WithExistingIdempotencyKey_ReturnsExistingPost() {
        // Arrange
        String idempotencyKey = "social-post-key-123";
        IdempotencyContext.setKey(idempotencyKey);

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        SocialPostCreateDto createDto = new SocialPostCreateDto();
        createDto.setContent("Test Social Content");

        SocialPost existingPost = new SocialPost();
        existingPost.setId(200L);
        existingPost.setIdempotencyKey(idempotencyKey);
        existingPost.setUser(user);
        existingPost.setContent("Test Social Content");

        when(contentValidationService.sanitizeAndValidateContent(anyString())).thenReturn("Test Social Content");
        when(socialPostRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPost));

        // Act
        SocialPostDto response = socialPostService.createSocialPost(createDto, null, user);

        // Assert
        assertNotNull(response);
        assertEquals(200L, response.getId());
        
        // Ensure no save was performed
        verify(socialPostRepository, never()).save(any(SocialPost.class));
    }
}
