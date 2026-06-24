package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.ChatMessageDto;
import com.JanSahayak.AI.model.ChatMessage;
import com.JanSahayak.AI.model.ChatSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatMessagingServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ChatSessionService chatSessionService;

    @InjectMocks
    private ChatMessagingService chatMessagingService;

    private ChatSession activeSession;

    @BeforeEach
    void setUp() {
        activeSession = new ChatSession();
        activeSession.setSessionId("test-session");
        activeSession.setUser1Id(1L);
        activeSession.setUser2Id(2L);
        activeSession.setUser1AnonymousId("User1");
        activeSession.setUser2AnonymousId("User2");
        activeSession.setStatus(ChatSession.SessionStatus.ACTIVE);
    }

    @Test
    void testSendDeliveredReceipt_Success() {
        // Mock session and user emails
        when(chatSessionService.getSession("test-session")).thenReturn(activeSession);
        // user 1 sends the receipt for a message they received from user 2
        when(chatSessionService.getUserEmail(2L)).thenReturn("user2@example.com");

        chatMessagingService.sendDeliveredReceipt("test-session", 1L, "msg-123");

        ArgumentCaptor<ChatMessageDto> dtoCaptor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("user2@example.com"), eq("/queue/messages"), dtoCaptor.capture());

        ChatMessageDto sentDto = dtoCaptor.getValue();
        assertNotNull(sentDto);
        assertEquals("msg-123", sentDto.getMessageId());
        assertEquals("User1", sentDto.getSenderId());
        assertEquals(ChatMessage.MessageType.MESSAGE_DELIVERED.name(), sentDto.getMessageType());
        assertTrue(sentDto.isDelivered());
    }

    @Test
    void testSendSeenReceipt_Success() {
        // Mock session and user emails
        when(chatSessionService.getSession("test-session")).thenReturn(activeSession);
        // user 2 sends the receipt for a message they received from user 1
        when(chatSessionService.getUserEmail(1L)).thenReturn("user1@example.com");

        chatMessagingService.sendSeenReceipt("test-session", 2L, "msg-456");

        ArgumentCaptor<ChatMessageDto> dtoCaptor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("user1@example.com"), eq("/queue/messages"), dtoCaptor.capture());

        ChatMessageDto sentDto = dtoCaptor.getValue();
        assertNotNull(sentDto);
        assertEquals("msg-456", sentDto.getMessageId());
        assertEquals("User2", sentDto.getSenderId());
        assertEquals(ChatMessage.MessageType.MESSAGE_SEEN.name(), sentDto.getMessageType());
        assertTrue(sentDto.isSeen());
    }

    @Test
    void testSendDeliveredReceipt_SessionInactive() {
        activeSession.setStatus(ChatSession.SessionStatus.ENDED);
        when(chatSessionService.getSession("test-session")).thenReturn(activeSession);

        chatMessagingService.sendDeliveredReceipt("test-session", 1L, "msg-123");

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }
}
