package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.PassTier;
import com.JanSahayak.AI.exception.PlanLimitExceededException;
import com.JanSahayak.AI.model.UserPass;
import com.JanSahayak.AI.repository.ChatSessionAuditRepo;
import com.JanSahayak.AI.repository.UserPassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlanEnforcementServiceTest {

    @Mock
    private UserPassRepository userPassRepository;

    @Mock
    private ChatSessionAuditRepo chatSessionAuditRepo;

    @InjectMocks
    private PlanEnforcementService planEnforcementService;

    private UserPass activeProPass;
    private UserPass activeVipPass;

    @BeforeEach
    void setUp() {
        activeProPass = UserPass.builder()
                .userId(1L)
                .tier(PassTier.GOVLYX_PRO)
                .validUntil(LocalDateTime.now().plusDays(10))
                .build();

        activeVipPass = UserPass.builder()
                .userId(2L)
                .tier(PassTier.GOVLYX_VIP)
                .validUntil(LocalDateTime.now().plusDays(10))
                .build();
    }

    @Test
    void testFreeUserExceedsMatchmakingLimit() {
        // Arrange
        Long freeUserId = 3L;
        when(userPassRepository.findActivePassByUserId(freeUserId)).thenReturn(Optional.empty());
        when(chatSessionAuditRepo.countSessionsForUserSince(eq(freeUserId), any(Date.class))).thenReturn(3);

        // Act & Assert
        PlanLimitExceededException exception = assertThrows(PlanLimitExceededException.class, () -> {
            planEnforcementService.enforceDailyMatchmakingLimit(freeUserId);
        });

        assertTrue(exception.getMessage().contains("daily matchmaking limit"));
    }

    @Test
    void testProUserBypassesMatchmakingLimit() {
        // Arrange
        Long proUserId = 1L;
        when(userPassRepository.findActivePassByUserId(proUserId)).thenReturn(Optional.of(activeProPass));

        // Act
        planEnforcementService.enforceDailyMatchmakingLimit(proUserId);

        // Assert - Should not throw an exception and should not query the audit repo
        verify(chatSessionAuditRepo, never()).countSessionsForUserSince(anyLong(), any(Date.class));
    }

    @Test
    void testFreeUserCannotSendRichMedia() {
        // Arrange
        Long freeUserId = 3L;
        when(userPassRepository.findActivePassByUserId(freeUserId)).thenReturn(Optional.empty());

        // Act
        boolean canSend = planEnforcementService.canSendChatMedia(freeUserId);

        // Assert
        assertFalse(canSend);
    }

    @Test
    void testVipUserCanSetDisappearingMessages() {
        // Arrange
        Long vipUserId = 2L;
        when(userPassRepository.findActivePassByUserId(vipUserId)).thenReturn(Optional.of(activeVipPass));

        // Act
        boolean canSet = planEnforcementService.canSetDisappearingMessages(vipUserId);

        // Assert
        assertTrue(canSet);
    }

    @Test
    void testProUserCannotSetDisappearingMessages() {
        // Arrange
        Long proUserId = 1L;
        when(userPassRepository.findActivePassByUserId(proUserId)).thenReturn(Optional.of(activeProPass));

        // Act
        boolean canSet = planEnforcementService.canSetDisappearingMessages(proUserId);

        // Assert
        assertFalse(canSet);
    }
}
