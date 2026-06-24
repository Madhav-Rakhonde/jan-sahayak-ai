package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.ChatMessage;
import com.JanSahayak.AI.model.ChatSession;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.ChatSessionAuditRepo;
import com.JanSahayak.AI.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatSessionServiceTest {

    @Mock
    private UserRepo userRepo;

    @Mock
    private ChatSessionAuditRepo chatSessionAuditRepo;

    @Mock
    private ChatMessagingService chatMessagingService;

    @InjectMocks
    private ChatSessionService chatSessionService;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = new User();
        user1.setId(1L);
        user1.setUsername("User1");

        user2 = new User();
        user2.setId(2L);
        user2.setUsername("User2");

        lenient().when(userRepo.findById(1L)).thenReturn(Optional.of(user1));
        lenient().when(userRepo.findById(2L)).thenReturn(Optional.of(user2));
    }

    @Test
    void testMarkMessageAsDelivered_Success() {
        // Create session
        ChatSession createdSession = chatSessionService.createSession(1L, 2L);
        String sessionId = createdSession.getSessionId();

        // Add a message
        ChatMessage msg = chatSessionService.addMessage(sessionId, 1L, "Hello", null);
        String msgId = msg.getMessageId();

        // Initially not delivered
        assertFalse(msg.isDelivered(), "Message should initially be marked as not delivered");

        // Mark as delivered
        boolean updated = chatSessionService.markMessageAsDelivered(sessionId, msgId);
        
        assertTrue(updated, "Method should return true upon successful update");
        assertTrue(msg.isDelivered(), "Message should now be marked as delivered");
    }

    @Test
    void testMarkMessageAsDelivered_AlreadyDelivered() {
        ChatSession createdSession = chatSessionService.createSession(1L, 2L);
        String sessionId = createdSession.getSessionId();

        ChatMessage msg = chatSessionService.addMessage(sessionId, 1L, "Hello", null);
        String msgId = msg.getMessageId();

        // Mark as delivered once
        chatSessionService.markMessageAsDelivered(sessionId, msgId);
        
        // Attempt to mark again
        boolean updatedAgain = chatSessionService.markMessageAsDelivered(sessionId, msgId);
        
        assertFalse(updatedAgain, "Method should return false if message was already delivered");
        assertTrue(msg.isDelivered(), "Message should still be delivered");
    }

    @Test
    void testMarkMessageAsDelivered_MessageNotFound() {
        ChatSession createdSession = chatSessionService.createSession(1L, 2L);
        String sessionId = createdSession.getSessionId();

        boolean updated = chatSessionService.markMessageAsDelivered(sessionId, "non-existent-id");
        assertFalse(updated, "Method should return false for a non-existent message ID");
    }

    @Test
    void testMarkMessageAsSeen_Success() {
        ChatSession createdSession = chatSessionService.createSession(1L, 2L);
        String sessionId = createdSession.getSessionId();

        ChatMessage msg = chatSessionService.addMessage(sessionId, 1L, "Hello", null);
        String msgId = msg.getMessageId();

        assertFalse(msg.isSeen(), "Message should initially be marked as not seen");

        boolean updated = chatSessionService.markMessageAsSeen(sessionId, msgId);
        
        assertTrue(updated, "Method should return true upon successful update");
        assertTrue(msg.isSeen(), "Message should now be marked as seen");
    }
}
