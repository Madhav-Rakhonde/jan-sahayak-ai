package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.CommunityMessageDto;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.CommunityMember;
import com.JanSahayak.AI.model.CommunityMessage;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommunityMessageRepo;
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
class CommunityChatServiceIdempotencyTest {

    @Mock
    private CommunityMessageRepo communityMessageRepo;

    @Mock
    private com.JanSahayak.AI.repository.CommunityRepo communityRepo;

    @Mock
    private com.JanSahayak.AI.repository.CommunityMemberRepo communityMemberRepo;

    @Mock
    private CommunityChatModerator chatModerator;

    @InjectMocks
    private CommunityChatService communityChatService;

    @BeforeEach
    void setUp() {
        IdempotencyContext.clear();
    }

    @AfterEach
    void tearDown() {
        IdempotencyContext.clear();
    }

    @Test
    void testSendMessage_WithExistingIdempotencyKey_ReturnsExistingMessage() {
        // Arrange
        String idempotencyKey = "chat-key-123";
        IdempotencyContext.setKey(idempotencyKey);

        User user = new User();
        user.setId(1L);

        Community community = new Community();
        community.setId(10L);

        CommunityMember member = new CommunityMember();
        member.setUser(user);

        CommunityMessage existingMessage = new CommunityMessage();
        existingMessage.setId(500L);
        existingMessage.setIdempotencyKey(idempotencyKey);
        existingMessage.setSender(user);
        existingMessage.setContent("Test Message");
        existingMessage.setCommunityId(10L);
        existingMessage.setMessageType(CommunityMessage.MessageType.TEXT);

        when(chatModerator.scanMessage(anyString())).thenReturn(false);
        when(communityMessageRepo.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingMessage));

        when(communityRepo.findById(10L)).thenReturn(Optional.of(community));
        
        member.setIsActive(true);
        member.setIsBanned(false);
        member.setIsMuted(false);
        when(communityMemberRepo.findByCommunityIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));

        // Act
        CommunityMessageDto response = communityChatService.processNewMessage(10L, 1L, "Test Message", null, null);

        // Assert
        assertNotNull(response);
        assertEquals(500L, response.getId());
        
        // Ensure no save was performed
        verify(communityMessageRepo, never()).save(any(CommunityMessage.class));
    }
}
