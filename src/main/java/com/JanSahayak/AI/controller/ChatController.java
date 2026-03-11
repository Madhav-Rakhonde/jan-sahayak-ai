package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.ChatMessageDto;
import com.JanSahayak.AI.DTO.ChatSessionDto;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.ChatMessage;
import com.JanSahayak.AI.model.ChatSession;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.service.ChatMessagingService;
import com.JanSahayak.AI.service.ChatSessionService;
import com.JanSahayak.AI.service.MatchmakingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final MatchmakingService   matchmakingService;
    private final ChatSessionService   chatSessionService;
    private final ChatMessagingService chatMessagingService;

    // ── WebSocket handlers ────────────────────────────────────────────────────
    // @AuthenticationPrincipal does NOT work in @MessageMapping handlers.
    // Use Principal parameter — Spring always injects Authentication there for STOMP.

    @MessageMapping("/chat.send")
    public void sendMessage(
            @Payload ChatMessageDto messageDto,
            Principal principal) {

        User user = getUserFromPrincipal(principal);
        if (user == null) {
            log.warn("chat.send: could not resolve user from principal");
            return;
        }
        if (messageDto.getContent() == null || messageDto.getContent().isBlank()) return;

        try {
            ChatSession session = chatSessionService.getUserSession(user.getId());
            if (session == null || !session.isActive()) {
                log.warn("chat.send from user {} but no active session", user.getId());
                return;
            }
            ChatMessage stored = chatSessionService.addMessage(
                    session.getSessionId(), user.getId(), messageDto.getContent());
            chatMessagingService.sendMessageToSession(session.getSessionId(), stored);
        } catch (Exception e) {
            log.error("Error processing message from user {}", user.getId(), e);
        }
    }

    @MessageMapping("/chat.typing")
    public void sendTypingIndicator(Principal principal) {
        User user = getUserFromPrincipal(principal);
        if (user == null) return;

        try {
            ChatSession session = chatSessionService.getUserSession(user.getId());
            if (session != null && session.isActive()) {
                chatMessagingService.sendTypingIndicator(session.getSessionId(), user.getId());
            }
        } catch (Exception e) {
            log.error("Error processing typing indicator from user {}", user.getId(), e);
        }
    }

    // ── REST endpoints ────────────────────────────────────────────────────────

    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startSearch(
            @AuthenticationPrincipal User user) {
        try {
            log.info("User {} starting chat search", user.getId());
            if (chatSessionService.isUserInSession(user.getId())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<Map<String, Object>>error(
                                "Already in session", "You are already in an active chat session"));
            }
            String sessionId = matchmakingService.findMatch(user);
            Map<String, Object> response = new HashMap<>();
            if (sessionId != null) {
                ChatSession session = chatSessionService.getSession(sessionId);
                chatMessagingService.notifyMatchFound(sessionId, session.getUser1Id(), session.getUser2Id());
                response.put("matched", true);
                response.put("sessionId", sessionId);
                response.put("session", convertToDto(session, user.getId()));
                return ResponseEntity.ok(ApiResponse.success("Match found!", response));
            }
            response.put("matched", false);
            response.put("searching", true);
            response.put("queueSize", matchmakingService.getQueueSize());
            return ResponseEntity.ok(ApiResponse.success("Searching for a chat partner...", response));
        } catch (Exception e) {
            log.error("Error starting chat search for user {}", user.getId(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Map<String, Object>>error(
                            "Search failed", "Failed to start search: " + e.getMessage()));
        }
    }

    @PostMapping("/search/cancel")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelSearch(
            @AuthenticationPrincipal User user) {
        try {
            matchmakingService.cancelSearch(user.getId());
            Map<String, Object> response = new HashMap<>();
            response.put("cancelled", true);
            return ResponseEntity.ok(ApiResponse.success("Search cancelled", response));
        } catch (Exception e) {
            log.error("Error cancelling search for user {}", user.getId(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Map<String, Object>>error("Cancel failed", "Failed to cancel search"));
        }
    }

    @GetMapping("/session")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ChatSessionDto>> getCurrentSession(
            @AuthenticationPrincipal User user) {
        try {
            ChatSession session = chatSessionService.getUserSession(user.getId());
            if (session == null) {
                return ResponseEntity.ok(ApiResponse.success("No active session", null));
            }
            return ResponseEntity.ok(ApiResponse.success("Active session", convertToDto(session, user.getId())));
        } catch (Exception e) {
            log.error("Error getting session for user {}", user.getId(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<ChatSessionDto>error("Get session failed", "Failed to get session"));
        }
    }

    @GetMapping("/online-count")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOnlineCount() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("onlineCount", chatSessionService.getOnlineUserCount());
            response.put("queueSize", matchmakingService.getQueueSize());
            return ResponseEntity.ok(ApiResponse.success("Online count", response));
        } catch (Exception e) {
            log.error("Error getting online count", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Map<String, Object>>error("Count failed", "Failed to get count"));
        }
    }

    @PostMapping("/session/leave")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> leaveSession(
            @AuthenticationPrincipal User user) {
        try {
            ChatSession session = chatSessionService.getUserSession(user.getId());
            if (session == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<Map<String, Object>>error(
                                "No active session", "No active session to leave"));
            }
            chatSessionService.endSession(session.getSessionId(), user.getId());
            Map<String, Object> response = new HashMap<>();
            response.put("left", true);
            return ResponseEntity.ok(ApiResponse.success("Left chat session", response));
        } catch (Exception e) {
            log.error("Error leaving session for user {}", user.getId(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Map<String, Object>>error("Leave failed", "Failed to leave session"));
        }
    }

    @GetMapping("/messages")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<ChatMessageDto>>> getMessages(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            ChatSession session = chatSessionService.getUserSession(user.getId());
            if (session == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<List<ChatMessageDto>>error("No active session", "No active session"));
            }
            List<ChatMessage> messages = chatSessionService.getRecentMessages(
                    session.getSessionId(), user.getId(), limit);
            List<ChatMessageDto> dtos = messages.stream()
                    .map(this::convertMessageToDto)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Messages retrieved", dtos));
        } catch (Exception e) {
            log.error("Error getting messages for user {}", user.getId(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<List<ChatMessageDto>>error("Get messages failed", "Failed to get messages"));
        }
    }

    @GetMapping("/admin/statistics")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChatStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("activeSessions", chatSessionService.getActiveSessionCount());
            stats.put("queueSize", matchmakingService.getQueueSize());
            stats.put("timestamp", java.time.Instant.now());
            return ResponseEntity.ok(ApiResponse.success("Chat statistics", stats));
        } catch (Exception e) {
            log.error("Error getting chat statistics", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Map<String, Object>>error("Statistics failed", "Failed to get statistics"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts User from the STOMP Principal.
     * WebSocketConfig stores: new UsernamePasswordAuthenticationToken(user, null, authorities)
     * So: Principal → UsernamePasswordAuthenticationToken → getPrincipal() → User
     */
    private User getUserFromPrincipal(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            Object p = auth.getPrincipal();
            if (p instanceof User u) return u;
        }
        log.warn("Could not extract User from principal: {}",
                principal != null ? principal.getClass().getName() : "null");
        return null;
    }

    private ChatSessionDto convertToDto(ChatSession session, Long userId) {
        return ChatSessionDto.builder()
                .sessionId(session.getSessionId())
                .yourAnonymousId(session.getUserAnonymousId(userId))
                .partnerAnonymousId(session.getPartnerAnonymousId(userId))
                .status(session.getStatus().name())
                .createdAt(session.getCreatedAt())
                .lastActivityAt(session.getLastActivityAt())
                .build();
    }

    private ChatMessageDto convertMessageToDto(ChatMessage message) {
        return ChatMessageDto.builder()
                .messageId(message.getMessageId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .timestamp(message.getTimestamp())
                .build();
    }
}