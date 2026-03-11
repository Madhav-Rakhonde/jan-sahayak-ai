package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.ChatMessageDto;
import com.JanSahayak.AI.model.ChatMessage;
import com.JanSahayak.AI.model.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sends real-time events via WebSocket.
 *
 * ── Routing rule ──────────────────────────────────────────────────────────────
 * WebSocketConfig sets the STOMP principal as:
 *   new UsernamePasswordAuthenticationToken(userDetails, ...)
 * so Authentication.getName() == userDetails.getUsername() == EMAIL.
 *
 * convertAndSendToUser(name, dest, payload) routes to /user/{name}/queue/...
 * We MUST pass getUserEmail(userId), NOT userId.toString().
 *
 * ── Message-ID consistency ────────────────────────────────────────────────────
 * sendMessageToSession() now receives the ChatMessage already persisted by
 * ChatSessionService.addMessage() and reuses its messageId in the outgoing DTO.
 * This guarantees the WebSocket echo and the REST history endpoint (/api/chat/messages)
 * always carry the SAME messageId, so the frontend merge/dedup is correct.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessagingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatSessionService    chatSessionService;

    private static final String DEST_MESSAGES = "/queue/messages";
    private static final String DEST_MATCH    = "/queue/match";
    private static final String DEST_TYPING   = "/queue/typing";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Send a TEXT message to both users in the session.
     *
     * @param storedMessage The ChatMessage already added to the session via
     *                      ChatSessionService.addMessage(). Reusing its
     *                      messageId keeps echo and history in sync.
     */
    public void sendMessageToSession(String sessionId, ChatMessage storedMessage) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null) {
            log.warn("sendMessageToSession: session {} not found", sessionId);
            return;
        }

        ChatMessageDto messageDto = ChatMessageDto.builder()
                .messageId(storedMessage.getMessageId())
                .senderId(storedMessage.getSenderId())
                .content(storedMessage.getContent())
                .messageType(storedMessage.getMessageType().name())
                .timestamp(storedMessage.getTimestamp())
                .build();

        sendToUser(session.getUser1Id(), messageDto);
        sendToUser(session.getUser2Id(), messageDto);

        log.debug("Message {} delivered in session {}", storedMessage.getMessageId(), sessionId);
    }

    /**
     * Notify the partner when the other user leaves.
     * Must be called BEFORE endSession() marks the session ENDED.
     */
    public void notifyUserLeft(String sessionId, Long leavingUserId) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null) {
            log.warn("notifyUserLeft: session {} not found", sessionId);
            return;
        }

        String leavingAnonymousId = session.getUserAnonymousId(leavingUserId);
        Long   partnerId          = session.getPartnerId(leavingUserId);
        if (partnerId == null) {
            log.warn("notifyUserLeft: no partner in session {}", sessionId);
            return;
        }

        String partnerEmail = chatSessionService.getUserEmail(partnerId);
        if (partnerEmail == null) {
            log.warn("notifyUserLeft: cannot resolve email for partner {} session {}", partnerId, sessionId);
            return;
        }

        // 1. USER_LEFT chat bubble
        ChatMessageDto leaveMsg = ChatMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .senderId("SYSTEM")
                .content("Your chat partner has left. Find someone new or exit.")
                .messageType(ChatMessage.MessageType.USER_LEFT.name())
                .timestamp(Instant.now())
                .build();
        sendToUser(partnerId, leaveMsg);

        // 2. Match-state event → switches partner UI to PARTNER_LEFT
        Map<String, Object> matchEvent = new HashMap<>();
        matchEvent.put("matched", false);
        matchEvent.put("sessionId", sessionId);
        matchEvent.put("yourAnonymousId", session.getUserAnonymousId(partnerId));
        matchEvent.put("partnerAnonymousId", leavingAnonymousId);
        messagingTemplate.convertAndSendToUser(partnerEmail, DEST_MATCH, matchEvent);

        log.info("Partner-left event sent to userId={} email={} session={}",
                partnerId, partnerEmail, sessionId);
    }

    /**
     * Notify both users that a match was found.
     */
    public void notifyMatchFound(String sessionId, Long user1Id, Long user2Id) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null) {
            log.warn("notifyMatchFound: session {} not found", sessionId);
            return;
        }
        sendMatchEvent(session, user1Id, user2Id);
        sendMatchEvent(session, user2Id, user1Id);
        log.info("Match notifications sent to users {} / {} session {}", user1Id, user2Id, sessionId);
    }

    /**
     * Send typing indicator to the partner.
     */
    public void sendTypingIndicator(String sessionId, Long typingUserId) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null || !session.isActive()) return;

        Long partnerId = session.getPartnerId(typingUserId);
        if (partnerId == null) return;

        String partnerEmail = chatSessionService.getUserEmail(partnerId);
        if (partnerEmail == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("typing", true);
        payload.put("senderId", session.getUserAnonymousId(typingUserId));
        messagingTemplate.convertAndSendToUser(partnerEmail, DEST_TYPING, payload);
    }

    /**
     * Send a system message to both users.
     */
    public void sendSystemMessage(String sessionId, String content) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null) return;

        ChatMessageDto msg = ChatMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .senderId("SYSTEM")
                .content(content)
                .messageType(ChatMessage.MessageType.SYSTEM.name())
                .timestamp(Instant.now())
                .build();

        sendToUser(session.getUser1Id(), msg);
        sendToUser(session.getUser2Id(), msg);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void sendToUser(Long userId, ChatMessageDto message) {
        if (userId == null) return;
        try {
            String email = chatSessionService.getUserEmail(userId);
            messagingTemplate.convertAndSendToUser(email, DEST_MESSAGES, message);
            log.debug("Sent type={} id={} to userId={}", message.getMessageType(), message.getMessageId(), userId);
        } catch (Exception e) {
            log.error("Failed to deliver message to userId={}", userId, e);
        }
    }

    private void sendMatchEvent(ChatSession session, Long recipientId, Long otherId) {
        String email = chatSessionService.getUserEmail(recipientId);
        if (email == null) return;

        Map<String, Object> event = new HashMap<>();
        event.put("matched", true);
        event.put("sessionId", session.getSessionId());
        event.put("yourAnonymousId", session.getUserAnonymousId(recipientId));
        event.put("partnerAnonymousId", session.getUserAnonymousId(otherId));
        messagingTemplate.convertAndSendToUser(email, DEST_MATCH, event);
    }
}