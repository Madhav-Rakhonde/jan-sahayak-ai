package com.JanSahayak.AI.DTO;

import com.JanSahayak.AI.model.CommunityMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityMessageDto implements Serializable {

    private Long id;
    private Long communityId;
    private String content;
    private String messageType;
    private AuthorDto sender;
    private SocialPostDto sharedPost;
    private Long replyToId;
    private boolean isEdited;
    private boolean isPinned;
    private Instant createdAt;
    private Instant expiresAt;

    /**
     * Converts a CommunityMessage entity to DTO. Eagerly extracts properties 
     * using safe checks to prevent lazy loading issues outside transaction scope.
     */
    public static CommunityMessageDto fromEntity(CommunityMessage msg) {
        if (msg == null) return null;

        String senderRole = null;
        try {
            if (msg.getSender() != null && msg.getSender().getRole() != null) {
                senderRole = msg.getSender().getRole().getName();
            }
        } catch (Exception ignored) {
            // Safe guard against closed session context
        }

        SocialPostDto sharedPostDto = null;
        try {
            if (msg.getSharedPost() != null) {
                sharedPostDto = SocialPostDto.fromSocialPost(msg.getSharedPost());
            }
        } catch (Exception ignored) {
            // Safe guard against uninitialized sharedPost proxy context
        }

        return CommunityMessageDto.builder()
                .id(msg.getId())
                .communityId(msg.getCommunityId())
                .content(msg.getContent())
                .messageType(msg.getMessageType() != null ? msg.getMessageType().name() : null)
                .sender(AuthorDto.fromUser(msg.getSender(), senderRole))
                .sharedPost(sharedPostDto)
                .replyToId(msg.getReplyToId())
                .isEdited(msg.isEdited())
                .isPinned(msg.isPinned())
                .createdAt(msg.getCreatedAt())
                .expiresAt(msg.getExpiresAt())
                .build();
    }
}
