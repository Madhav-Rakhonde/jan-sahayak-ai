package com.JanSahayak.AI.model;

import lombok.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a single message in an anonymous chat session.
 * Not persisted to database - only exists during active session.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique message ID
     */
    private String messageId;

    /**
     * Session this message belongs to
     */
    private String sessionId;

    /**
     * Anonymous sender identifier (e.g., "User1", "User2")
     */
    private String senderId;

    /**
     * Message content
     */
    private String content;

    /**
     * Message type (TEXT, SYSTEM, USER_JOINED, USER_LEFT)
     */
    private MessageType messageType;

    /**
     * When the message was sent
     */
    private Instant timestamp;

    /**
     * Whether this message has been delivered
     */
    private boolean delivered;

    public enum MessageType {
        TEXT,           // Regular user message
        SYSTEM,         // System notification
        USER_JOINED,    // Partner joined notification
        USER_LEFT,      // Partner left notification
        CHAT_ENDED      // Chat session ended
    }

    /**
     * Create a system message
     */
    public static ChatMessage systemMessage(String sessionId, String content) {
        return ChatMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .sessionId(sessionId)
                .senderId("SYSTEM")
                .content(content)
                .messageType(MessageType.SYSTEM)
                .timestamp(Instant.now())
                .delivered(false)
                .build();
    }

    /**
     * Create a user message
     */
    public static ChatMessage userMessage(String sessionId, String senderId, String content) {
        return ChatMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .sessionId(sessionId)
                .senderId(senderId)
                .content(content)
                .messageType(MessageType.TEXT)
                .timestamp(Instant.now())
                .delivered(false)
                .build();
    }
}