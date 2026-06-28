package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CopyrightModerationServiceTest {

    @Mock
    private PostRepo postRepository;

    @Mock
    private SocialPostRepo socialPostRepository;

    @Mock
    private UserRepo userRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private CopyrightModerationService copyrightModerationService;

    private User testUser;
    private Post testPost;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("testuser@example.com");
        testUser.setUsername("Test User");
        testUser.setCopyrightStrikes(0);

        testPost = new Post();
        testPost.setId(10L);
        testPost.setUser(testUser);
        testPost.setContent("Infringing content");
        testPost.setStatus(PostStatus.ACTIVE);
    }

    @Test
    void executeTakedownOnPost_FirstStrike_AppliesTakedownAndSendsWarningEmail() {
        // Arrange
        when(postRepository.findById(10L)).thenReturn(Optional.of(testPost));
        when(postRepository.save(any(Post.class))).thenReturn(testPost);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        copyrightModerationService.executeTakedownOnPost(10L, 99L, "DMCA Notice");

        // Assert
        assertEquals(PostStatus.TAKEN_DOWN, testPost.getStatus());
        assertEquals("DMCA Notice", testPost.getTakedownReason());
        assertNotNull(testPost.getRetentionExpiresAt()); // 180 days retention

        assertEquals(1, testUser.getCopyrightStrikes());
        assertNull(testUser.getBannedAt());

        verify(postRepository).save(testPost);
        verify(userRepository).save(testUser);
        verify(emailService).sendCopyrightTakedownEmail(eq(testUser), eq("Infringing content"), eq(1), eq("DMCA Notice"));
        verify(emailService, never()).sendCopyrightBanEmail(any(), any());
    }

    @Test
    void executeTakedownOnPost_ThirdStrike_AppliesTakedownAndBansUser() {
        // Arrange
        testUser.setCopyrightStrikes(2); // Already has 2 strikes
        when(postRepository.findById(10L)).thenReturn(Optional.of(testPost));
        when(postRepository.save(any(Post.class))).thenReturn(testPost);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        copyrightModerationService.executeTakedownOnPost(10L, 99L, "DMCA Notice 3");

        // Assert
        assertEquals(PostStatus.TAKEN_DOWN, testPost.getStatus());
        assertEquals(3, testUser.getCopyrightStrikes());
        assertNotNull(testUser.getBannedAt());
        assertTrue(testUser.getBanReason().contains("3 strikes"));

        verify(postRepository).save(testPost);
        verify(userRepository).save(testUser);
        verify(emailService).sendCopyrightBanEmail(eq(testUser), eq("DMCA Notice 3"));
        verify(emailService, never()).sendCopyrightTakedownEmail(any(), any(), anyInt(), any());
    }

    @Test
    void executeTakedownOnPost_PostNotFound_ThrowsException() {
        // Arrange
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            copyrightModerationService.executeTakedownOnPost(99L, 1L, "DMCA");
        });
        assertEquals("Post not found", ex.getMessage());
    }
}
