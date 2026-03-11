package com.JanSahayak.AI.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDto {
    private String messageId;
    private String senderId;
    private String content;
    private String messageType;
    private Instant timestamp;
}