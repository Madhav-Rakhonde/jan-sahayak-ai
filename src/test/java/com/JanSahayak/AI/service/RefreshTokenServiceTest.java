package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.RefreshToken;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.RefreshTokenRepository;
import com.JanSahayak.AI.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepo userRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDurationMs", 604800000L);

        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setUsername("testuser");

        refreshToken = new RefreshToken();
        refreshToken.setId(1L);
        refreshToken.setUser(user);
        refreshToken.setToken("random-uuid-token");
        refreshToken.setExpiryDate(Instant.now().plusMillis(604800000L));
    }

    @Test
    void createRefreshToken_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken savedToken = invocation.getArgument(0);
            savedToken.setId(2L);
            return savedToken;
        });

        RefreshToken createdToken = refreshTokenService.createRefreshToken(1L);

        assertNotNull(createdToken);
        assertNotNull(createdToken.getToken());
        assertEquals(user, createdToken.getUser());
        assertTrue(createdToken.getExpiryDate().isAfter(Instant.now()));
        verify(refreshTokenRepository, times(1)).deleteByUser(user);
        verify(refreshTokenRepository, times(1)).flush();
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    void findByToken_Success() {
        when(refreshTokenRepository.findByToken("random-uuid-token")).thenReturn(Optional.of(refreshToken));

        Optional<RefreshToken> foundToken = refreshTokenService.findByToken("random-uuid-token");

        assertTrue(foundToken.isPresent());
        assertEquals("random-uuid-token", foundToken.get().getToken());
    }

    @Test
    void verifyExpiration_NotExpired_ReturnsToken() {
        RefreshToken verifiedToken = refreshTokenService.verifyExpiration(refreshToken);

        assertNotNull(verifiedToken);
        assertEquals(refreshToken, verifiedToken);
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
    }

    @Test
    void verifyExpiration_Expired_ThrowsExceptionAndDeletes() {
        refreshToken.setExpiryDate(Instant.now().minusMillis(1000));

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            refreshTokenService.verifyExpiration(refreshToken);
        });

        assertEquals("Refresh token was expired. Please make a new signin request", exception.getMessage());
        verify(refreshTokenRepository, times(1)).delete(refreshToken);
    }

    @Test
    void deleteByUserId_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.deleteByUser(user)).thenReturn(1);

        int result = refreshTokenService.deleteByUserId(1L);

        assertEquals(1, result);
        verify(refreshTokenRepository, times(1)).deleteByUser(user);
    }
}
