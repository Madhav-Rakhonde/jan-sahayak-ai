package com.JanSahayak.AI.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Represents a persistent message inside a community group chat.
 */
@Entity
@Table(name = "community_messages", indexes = {
        @Index(name = "idx_comm_msg_composite", columnList = "community_id, created_at DESC, id DESC"),
        @Index(name = "idx_comm_msg_expiry",    columnList = "expires_at"),
        @Index(name = "idx_comm_msg_shared",    columnList = "shared_post_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    /**
     * User who sent the message. Mapped as FetchType.LAZY to optimize performance.
     * To prevent LazyInitializationException during DTO mapping, the repository queries
     * MUST explicitly use JOIN FETCH to load the sender.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false, foreignKey = @ForeignKey(name = "fk_message_sender"))
    private User sender;

    @Column(length = 3000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    /**
     * Reference to a post shared from the same community.
     * Mapped as FetchType.LAZY.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_post_id", foreignKey = @ForeignKey(name = "fk_message_shared_post"))
    private SocialPost sharedPost;

    @Column(name = "reply_to_id")
    private Long replyToId;

    @Column(name = "is_edited", nullable = false, columnDefinition = "boolean")
    @Builder.Default
    private boolean isEdited = false;

    @Column(name = "is_pinned", nullable = false, columnDefinition = "boolean")
    @Builder.Default
    private boolean isPinned = false;

    @Column(name = "is_flagged", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean isFlagged = false;

    @Column(name = "is_quarantined", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean isQuarantined = false;

    @Column(name = "report_count", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private int reportCount = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    public enum MessageType {
        TEXT,
        SYSTEM,
        SHARE_POST
    }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
