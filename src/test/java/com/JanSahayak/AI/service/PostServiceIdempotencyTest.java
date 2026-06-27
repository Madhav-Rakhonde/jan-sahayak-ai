package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PostCreateDto;
import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.util.IdempotencyContext;
import com.JanSahayak.AI.payload.PostUtility;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceIdempotencyTest {

    @Mock
    private PostRepo postRepository;

    @Mock
    private ContentValidationService contentValidationService;

    @Mock
    private PinCodeLookupService pinCodeLookupService;

    @Mock
    private PostUtility postUtility;
    
    // Injecting into PostService
    @InjectMocks
    private PostService postService;

    @BeforeEach
    void setUp() {
        IdempotencyContext.clear();
    }

    @AfterEach
    void tearDown() {
        IdempotencyContext.clear();
    }

    @Test
    void testCreatePost_WithExistingIdempotencyKey_ReturnsExistingPost() {
        // Arrange
        String idempotencyKey = "test-idem-key-123";
        IdempotencyContext.setKey(idempotencyKey);

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        PostCreateDto createDto = new PostCreateDto();
        createDto.setContent("Test Content");
        createDto.setTargetPincode("110001");

        Post existingPost = new Post();
        existingPost.setId(100L);
        existingPost.setIdempotencyKey(idempotencyKey);
        existingPost.setUser(user);

        when(contentValidationService.sanitizeAndValidateContent(anyString())).thenReturn("Test Content");
        when(postRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPost));

        // Act
        Post response = postService.createPost(createDto, user);

        // Assert
        assertNotNull(response);
        assertEquals(100L, response.getId());
        
        // Ensure no save or further processing was done
        verify(postRepository, never()).save(any(Post.class));
    }
}
