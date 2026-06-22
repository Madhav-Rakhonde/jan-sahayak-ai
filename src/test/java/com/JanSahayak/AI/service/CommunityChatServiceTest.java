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
    private SimpMessagingTemplate messagingTemplate;

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
}
