package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.AuthResponse;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.RefreshToken;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.security.CustomUserDetailsService;
import com.JanSahayak.AI.security.JwtUtil;
import com.JanSahayak.AI.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthController authController;

    private User testUser;
    private RefreshToken testRefreshToken;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setUsername("TestUser123");

        testRefreshToken = new RefreshToken();
        testRefreshToken.setId(10L);
        testRefreshToken.setToken("valid-refresh-token");
        testRefreshToken.setUser(testUser);
    }

    @Test
    void testRefreshToken_Success() {
        String mockNewJwt = "new-jwt-token";

        when(refreshTokenService.findByToken("valid-refresh-token"))
                .thenReturn(Optional.of(testRefreshToken));
        when(refreshTokenService.verifyExpiration(testRefreshToken))
                .thenReturn(testRefreshToken);
        when(userDetailsService.loadUserByUsername("test@example.com"))
                .thenReturn(testUser); // User implements UserDetails
        when(jwtUtil.generateToken(testUser))
                .thenReturn(mockNewJwt);

        ResponseEntity<ApiResponse<AuthResponse>> response = authController.refreshtoken("valid-refresh-token");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(mockNewJwt, response.getBody().getData().getToken());

        // Verify that loadUserByUsername was called with the user's email, NOT the display username
        verify(userDetailsService, times(1)).loadUserByUsername("test@example.com");
    }

    @Test
    void testRefreshToken_EmptyToken() {
        ResponseEntity<ApiResponse<AuthResponse>> response = authController.refreshtoken(null);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Refresh Token is empty!", response.getBody().getMessage());
    }

    @Test
    void testRefreshToken_InvalidOrExpiredToken() {
        when(refreshTokenService.findByToken("invalid-token"))
                .thenReturn(Optional.empty());

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            authController.refreshtoken("invalid-token");
        });

        assertEquals("Refresh token is invalid or missing!", exception.getMessage());
    }
}
