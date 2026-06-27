package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.CommentCreateDto;
import com.JanSahayak.AI.DTO.CommentDto;
import com.JanSahayak.AI.model.Comment;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommentRepo;
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
class CommentServiceIdempotencyTest {

    @Mock
    private CommentRepo commentRepository;

    @Mock
    private ContentValidationService contentValidationService;

    @InjectMocks
    private CommentService commentService;

    @BeforeEach
    void setUp() {
        IdempotencyContext.clear();
    }

    @AfterEach
    void tearDown() {
        IdempotencyContext.clear();
    }

    @Test
    void testCreateCommentOnPost_WithExistingIdempotencyKey_ReturnsExistingComment() {
        // Arrange
        String idempotencyKey = "comment-key-123";
        IdempotencyContext.setKey(idempotencyKey);

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        Post post = new Post();
        post.setId(10L);
        post.setStatus(PostStatus.ACTIVE);
        post.setContent("Test Content");

        CommentCreateDto createDto = new CommentCreateDto();
        createDto.setText("Test Comment");

        Comment existingComment = new Comment();
        existingComment.setId(400L);
        existingComment.setIdempotencyKey(idempotencyKey);
        existingComment.setUser(user);
        existingComment.setPost(post);
        existingComment.setText("Test Comment");

        when(contentValidationService.sanitizeAndValidateContent(anyString())).thenReturn("Test Comment");
        when(commentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingComment));

        // Act
        CommentDto response = commentService.createCommentOnPost(createDto, user, post);

        // Assert
        assertNotNull(response);
        assertEquals(400L, response.getId());
        
        // Ensure no save was performed
        verify(commentRepository, never()).save(any(Comment.class));
    }
}
