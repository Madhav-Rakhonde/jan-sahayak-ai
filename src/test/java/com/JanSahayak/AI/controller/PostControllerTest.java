package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.PostCreateDto;
import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.exception.DuplicatePostException;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.ratelimit.ClientIpResolver;
import com.JanSahayak.AI.ratelimit.RateLimiterService;
import com.JanSahayak.AI.security.CustomUserDetailsService;
import com.JanSahayak.AI.security.JwtUtil;
import com.JanSahayak.AI.service.PostService;
import com.JanSahayak.AI.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters to easily test the controller logic
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostService postService;

    @MockBean
    private UserService userService;

    @MockBean
    private RateLimiterService rateLimiterService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Test createPost with duplicate throws 409 Conflict")
    void createPost_whenDuplicate_shouldReturn409() throws Exception {
        // Arrange
        PostCreateDto postDto = PostCreateDto.builder()
                .content("Duplicate content test")
                .targetPincode("411001")
                .build();

        PostResponse duplicateResponse = new PostResponse();
        duplicateResponse.setId(99L);
        duplicateResponse.setContent("Duplicate content test");

        // Mock the postService to throw DuplicatePostException
        Mockito.when(postService.createPost(any(PostCreateDto.class), any()))
                .thenThrow(new DuplicatePostException("Duplicate issue detected.", duplicateResponse));

        // Mock rate limiter to allow the request
        Mockito.when(rateLimiterService.tryConsume(any())).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post( "/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Duplicate issue detected."))
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.content").value("Duplicate content test"));
    }
}
