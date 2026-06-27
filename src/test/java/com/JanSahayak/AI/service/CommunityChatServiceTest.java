package com.JanSahayak.AI.service;

import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.CommunityMember;
import com.JanSahayak.AI.model.CommunityMessage;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommunityMemberRepo;
import com.JanSahayak.AI.repository.CommunityMessageRepo;
import com.JanSahayak.AI.repository.CommunityRepo;
import com.JanSahayak.AI.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommunityChatServiceTest {

    @Mock
    private CommunityRepo communityRepo;

    @Mock
    private CommunityMemberRepo communityMemberRepo;

    @Mock
    private CommunityMessageRepo communityMessageRepo;

    @Mock
    private UserRepo userRepo;

    @Mock
    private CommunityChatModerator chatModerator;

    @Mock
    private com.JanSahayak.AI.service.PlanEnforcementService planEnforcementService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private com.JanSahayak.AI.repository.ContentReportRepository contentReportRepository;

    @InjectMocks
    private CommunityChatService communityChatService;

    private Community testCommunity;
    private User ownerUser;
    private User adminUser;
    private CommunityMember adminMember;

    @BeforeEach
    void setUp() {
        ownerUser = new User();
        ownerUser.setId(1L);

        adminUser = new User();
        adminUser.setId(2L);

        testCommunity = new Community();
        testCommunity.setId(100L);
        testCommunity.setOwner(ownerUser);
        testCommunity.setIsGroupChatEnabled(null); // Simulated old data
        testCommunity.setChatRetentionDays(null);   // Simulated old data

        adminMember = new CommunityMember();
        adminMember.setUser(adminUser);
        adminMember.setCommunity(testCommunity);
        adminMember.setMemberRole(CommunityMember.MemberRole.ADMIN);

        lenient().when(planEnforcementService.canSetDisappearingMessages(anyLong())).thenReturn(true);
        lenient().when(planEnforcementService.isCommunityFrozen(anyLong())).thenReturn(false);
    }

    @Test
    void testUpdateChatSettings_AsOwner_Success() {
        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));
        when(userRepo.findById(1L)).thenReturn(Optional.of(ownerUser));

        communityChatService.updateChatSettings(100L, 1L, true, 7);

        assertTrue(testCommunity.getIsGroupChatEnabled());
        assertEquals(7, testCommunity.getChatRetentionDays());
        verify(communityRepo, times(1)).save(testCommunity);
        verify(communityMessageRepo, times(2)).save(any(CommunityMessage.class)); // system messages
    }

    @Test
    void testUpdateChatSettings_AsAdmin_Success() {
        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));
        when(communityMemberRepo.findByCommunityIdAndUserId(100L, 2L)).thenReturn(Optional.of(adminMember));
        when(userRepo.findById(2L)).thenReturn(Optional.of(adminUser));

        communityChatService.updateChatSettings(100L, 2L, false, 0);

        assertFalse(testCommunity.getIsGroupChatEnabled());
        assertEquals(0, testCommunity.getChatRetentionDays());
        verify(communityRepo, times(1)).save(testCommunity);
    }

    @Test
    void testUpdateChatSettings_PartialUpdateEnabledOnly_Success() {
        testCommunity.setChatRetentionDays(30);
        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));

        // only update `enabled`, keep `days` as null
        communityChatService.updateChatSettings(100L, 1L, false, null);

        assertFalse(testCommunity.getIsGroupChatEnabled());
        assertEquals(30, testCommunity.getChatRetentionDays()); // unchanged
        verify(communityRepo, times(1)).save(testCommunity);
    }

    @Test
    void testUpdateChatSettings_PartialUpdateDaysOnly_Success() {
        testCommunity.setIsGroupChatEnabled(true);
        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));

        // only update `days`, keep `enabled` as null
        communityChatService.updateChatSettings(100L, 1L, null, 14);

        assertTrue(testCommunity.getIsGroupChatEnabled()); // unchanged
        assertEquals(14, testCommunity.getChatRetentionDays());
        verify(communityRepo, times(1)).save(testCommunity);
    }

    @Test
    void testUpdateChatSettings_NegativeRetentionDays_ThrowsException() {
        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));

        assertThrows(ValidationException.class, () -> {
            communityChatService.updateChatSettings(100L, 1L, true, -5);
        });

        verify(communityRepo, never()).save(any());
    }

    @Test
    void testUpdateChatSettings_NotMember_ThrowsException() {
        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));
        when(communityMemberRepo.findByCommunityIdAndUserId(100L, 99L)).thenReturn(Optional.empty());

        assertThrows(SecurityException.class, () -> {
            communityChatService.updateChatSettings(100L, 99L, true, 30);
        });
    }

    @Test
    void testUpdateChatSettings_RegularMember_ThrowsException() {
        CommunityMember regularMember = new CommunityMember();
        regularMember.setMemberRole(CommunityMember.MemberRole.MEMBER);
        
        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));
        when(communityMemberRepo.findByCommunityIdAndUserId(100L, 3L)).thenReturn(Optional.of(regularMember));

        assertThrows(SecurityException.class, () -> {
            communityChatService.updateChatSettings(100L, 3L, true, 30);
        });
    }

    @Test
    void testUpdateChatSettings_NullOwnerAndFallback_Success() {
        testCommunity.setOwner(null); // Simulate null owner
        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));
        when(communityMemberRepo.findByCommunityIdAndUserId(100L, 2L)).thenReturn(Optional.of(adminMember));
        when(userRepo.findById(2L)).thenReturn(Optional.of(adminUser)); // Fallback works

        communityChatService.updateChatSettings(100L, 2L, false, 0);

        assertFalse(testCommunity.getIsGroupChatEnabled());
        verify(communityRepo, times(1)).save(testCommunity);
        verify(communityMessageRepo, times(2)).save(any(CommunityMessage.class));
    }

    @Test
    void testUpdateChatSettings_SimultaneousUpdate_BroadcastsTwoSystemMessages() {
        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));
        when(userRepo.findById(1L)).thenReturn(Optional.of(ownerUser));

        // Update both settings simultaneously
        communityChatService.updateChatSettings(100L, 1L, false, 30);

        // Verify two distinct system messages were saved
        verify(communityMessageRepo, times(2)).save(any(CommunityMessage.class));
        
        // Verify messaging template broadcasted twice
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/community.100.messages"), any(Object.class));
    }
    @Test
    void testToggleMessagePin_AsAdmin_Success() {
        CommunityMessage msg = new CommunityMessage();
        msg.setId(500L);
        msg.setCommunityId(100L);
        msg.setPinned(false);
        msg.setSender(adminUser);

        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));
        when(communityMemberRepo.findByCommunityIdAndUserId(100L, 2L)).thenReturn(Optional.of(adminMember));
        when(planEnforcementService.canPinMessages(2L)).thenReturn(true);
        when(communityMessageRepo.findById(500L)).thenReturn(Optional.of(msg));

        com.JanSahayak.AI.DTO.CommunityMessageDto result = communityChatService.toggleMessagePin(100L, 500L, 2L);

        assertTrue(result.isPinned());
        assertTrue(msg.isPinned());
        verify(communityMessageRepo, times(1)).unpinAllMessagesInCommunity(100L);
        verify(communityMessageRepo, times(1)).save(msg);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/community.100.messages"), any(Object.class));
    }

    @Test
    void testToggleMessagePin_UnpinsOldMessages() {
        CommunityMessage msg = new CommunityMessage();
        msg.setId(500L);
        msg.setCommunityId(100L);
        msg.setPinned(false);
        msg.setSender(adminUser);

        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));
        when(communityMemberRepo.findByCommunityIdAndUserId(100L, 2L)).thenReturn(Optional.of(adminMember));
        when(planEnforcementService.canPinMessages(2L)).thenReturn(true);
        when(communityMessageRepo.findById(500L)).thenReturn(Optional.of(msg));

        communityChatService.toggleMessagePin(100L, 500L, 2L);

        verify(communityMessageRepo, times(1)).unpinAllMessagesInCommunity(100L);
    }

    @Test
    void testToggleMessagePin_NotVIP_ThrowsException() {
        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));
        when(communityMemberRepo.findByCommunityIdAndUserId(100L, 2L)).thenReturn(Optional.of(adminMember));
        when(planEnforcementService.canPinMessages(2L)).thenReturn(false);

        assertThrows(com.JanSahayak.AI.exception.PlanLimitExceededException.class, () -> {
            communityChatService.toggleMessagePin(100L, 500L, 2L);
        });
    }

    @Test
    void testGetPinnedMessages_ReturnsDtoList() {
        CommunityMessage msg1 = new CommunityMessage();
        msg1.setId(500L);
        msg1.setCommunityId(100L);
        msg1.setPinned(true);
        msg1.setSender(adminUser);

        when(communityMessageRepo.findPinnedMessages(100L)).thenReturn(java.util.List.of(msg1));

        java.util.List<com.JanSahayak.AI.DTO.CommunityMessageDto> result = communityChatService.getPinnedMessages(100L);

        assertEquals(1, result.size());
        assertEquals(500L, result.get(0).getId());
    }

    @Test
    void testReportMessage_BroadcastsOnlyOnThirdReport() {
        CommunityMessage msg = new CommunityMessage();
        msg.setId(600L);
        msg.setCommunityId(100L);
        msg.setReportCount(2); // Already reported twice
        msg.setSender(ownerUser);

        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));
        when(userRepo.findById(2L)).thenReturn(Optional.of(adminUser));
        when(communityMessageRepo.findById(600L)).thenReturn(Optional.of(msg));
        
        CommunityMember activeMember = new CommunityMember();
        activeMember.setIsActive(true);
        when(communityMemberRepo.findByCommunityIdAndUserId(100L, 2L)).thenReturn(Optional.of(activeMember));
        
        when(contentReportRepository.existsByReporter_IdAndTargetTypeAndTargetId(2L, "COMMUNITY_MESSAGE", 600L)).thenReturn(false);

        // Third report - should broadcast
        communityChatService.reportMessage(100L, 600L, 2L, com.JanSahayak.AI.enums.ReportCategory.SPAM, "Spam");

        assertTrue(msg.isFlagged());
        assertEquals(3, msg.getReportCount());
        verify(communityMessageRepo, times(1)).save(msg);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/community.100.messages"), any(java.util.Map.class));

        // Reset mock
        clearInvocations(messagingTemplate);
        clearInvocations(communityMessageRepo);

        // Fourth report - should NOT broadcast again
        when(contentReportRepository.existsByReporter_IdAndTargetTypeAndTargetId(3L, "COMMUNITY_MESSAGE", 600L)).thenReturn(false);
        when(userRepo.findById(3L)).thenReturn(Optional.of(adminUser)); // Reuse user mock for simplicity
        when(communityMemberRepo.findByCommunityIdAndUserId(100L, 3L)).thenReturn(Optional.of(activeMember));

        communityChatService.reportMessage(100L, 600L, 3L, com.JanSahayak.AI.enums.ReportCategory.SPAM, "Spam again");

        assertTrue(msg.isFlagged());
        assertEquals(4, msg.getReportCount());
        verify(communityMessageRepo, times(1)).save(msg);
        // Ensure no broadcast happened this time!
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/community.100.messages"), any(java.util.Map.class));
    }
}
