package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.ChatMessage;
import com.JanSahayak.AI.model.ChatSession;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages chat session lifecycle and message delivery.
 *
 * ── Reconnect flow (page-refresh fix) ────────────────────────────────────────
 *
 * Problem:
 *   When a user refreshes the browser the WebSocket drops, but endSession()
 *   is never called.  On the next findMatch() call isUserInSession() returns
 *   true and throws "already in an active chat", leaving the user locked out.
 *
 * Fix:
 *   1. WebSocket disconnect  → handleUserDisconnect()
 *      • Keeps the session alive in DISCONNECTED state for a short grace period
 *        (Constant.CHAT_RECONNECT_GRACE_PERIOD_SECONDS, e.g. 30 s).
 *      • Notifies the partner with a "partner disconnected" system message so
 *        their UI can show a reconnecting indicator instead of a hard leave.
 *
 *   2. User reconnects within grace period → reconnectToSession()
 *      • Restores status to ACTIVE, re-registers userSessionMap entry.
 *      • Notifies the partner that the user is back.
 *      • Returns the full session so the frontend can restore chat history.
 *
 *   3. Grace period expires → cleanupDisconnectedSessions() (scheduled)
 *      • If the user never came back, the session is ended and the partner
 *        gets the usual "partner has left" notification.
 *
 * ── WebSocket routing ─────────────────────────────────────────────────────────
 *   convertAndSendToUser(name, dest, payload) routes to /user/{name}/queue/...
 *   The STOMP principal name is the user's EMAIL, not the numeric userId.
 *   All routing therefore goes through getUserEmail(Long userId).
 *
 * ── Rich media (new) ──────────────────────────────────────────────────────────
 *   addMediaMessage() creates a transient ChatMessage for IMAGE / VIDEO /
 *   STICKER / VOICE_NOTE.  It does NOT add the message to session.recentMessages
 *   — media is relayed once and then dropped, consistent with the "never store"
 *   policy.  The caller (ChatMediaController) must immediately pass the returned
 *   object to ChatMessagingService.sendMediaToSession().
 */
@Service
@Slf4j
public class ChatSessionService {

    private final UserRepo userRepository;

    // @Lazy breaks the circular dependency:
    // ChatSessionService → ChatMessagingService → ChatSessionService
    private final ChatMessagingService chatMessagingService;

    public ChatSessionService(
            UserRepo userRepository,
            @Lazy ChatMessagingService chatMessagingService) {
        this.userRepository      = userRepository;
        this.chatMessagingService = chatMessagingService;
    }

    // sessionId → session  (includes DISCONNECTED sessions during grace period)
    private final Map<String, ChatSession> activeSessions = new ConcurrentHashMap<>();

    // userId → sessionId   (maintained for both ACTIVE and DISCONNECTED sessions)
    private final Map<Long, String> userSessionMap = new ConcurrentHashMap<>();

    /**
     * FIX MEMORY LEAK #1 — recentMessages cap.
     * Without this cap the in-memory message list grows without bound for long-lived
     * sessions.  We keep the last MAX_RECENT_MESSAGES messages only.
     * Constant.CHAT_MAX_RECENT_MESSAGES should be set to ~200.
     */
    private static final int MAX_RECENT_MESSAGES =
            Constant.CHAT_MAX_RECENT_MESSAGES > 0 ? Constant.CHAT_MAX_RECENT_MESSAGES : 200;

    // FIX MEMORY LEAK #2 — per-session email lookup cache.
    // getUserEmail() is called on every WebSocket send; hitting the DB every time
    // creates GC pressure from short-lived User objects.  We cache email by userId
    // for the lifetime of the session and evict when the session is removed.
    // Bounded by the number of concurrent sessions * 2 users = negligible.
    private final Map<Long, String> emailCache = new ConcurrentHashMap<>();

    // ── Session lifecycle ─────────────────────────────────────────────────────

    public ChatSession createSession(Long user1Id, Long user2Id) {
        log.info("Creating chat session between users {} and {}", user1Id, user2Id);

        if (isUserInSession(user1Id) || isUserInSession(user2Id)) {
            throw new RuntimeException("One or both users are already in an active chat");
        }

        String sessionId        = UUID.randomUUID().toString();
        String user1DisplayName = getActualDisplayName(user1Id);
        String user2DisplayName = getActualDisplayName(user2Id);

        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .user1Id(user1Id)
                .user2Id(user2Id)
                .user1AnonymousId(user1DisplayName)
                .user2AnonymousId(user2DisplayName)
                .status(ChatSession.SessionStatus.ACTIVE)
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .recentMessages(new ArrayList<>())
                .build();

        session.addMessage(ChatMessage.systemMessage(sessionId, "You are now connected. Say hello!"));

        activeSessions.put(sessionId, session);
        userSessionMap.put(user1Id, sessionId);
        userSessionMap.put(user2Id, sessionId);

        // FIX MEMORY LEAK #2 — pre-populate email cache so WebSocket sends are DB-free
        try { emailCache.put(user1Id, fetchEmail(user1Id)); } catch (Exception ignored) {}
        try { emailCache.put(user2Id, fetchEmail(user2Id)); } catch (Exception ignored) {}

        log.info("Chat session {} created successfully", sessionId);
        return session;
    }

    public ChatSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    public ChatSession getUserSession(Long userId) {
        String sessionId = userSessionMap.get(userId);
        return sessionId != null ? getSession(sessionId) : null;
    }

    // ── Text message ──────────────────────────────────────────────────────────

    public ChatMessage addMessage(String sessionId, Long senderId, String content) {
        ChatSession session = getSession(sessionId);
        if (session == null)            throw new RuntimeException("Session not found: " + sessionId);
        if (!session.hasUser(senderId)) throw new RuntimeException("User not part of this session");
        if (!session.isActive())        throw new RuntimeException("Session is not active");

        String senderAnonymousId = session.getUserAnonymousId(senderId);
        ChatMessage message = ChatMessage.userMessage(sessionId, senderAnonymousId, content);
        session.addMessage(message);

        // FIX MEMORY LEAK #1 — trim message list to cap so it never grows unbounded
        List<ChatMessage> msgs = session.getRecentMessages();
        if (msgs != null && msgs.size() > MAX_RECENT_MESSAGES) {
            msgs.subList(0, msgs.size() - MAX_RECENT_MESSAGES).clear();
        }

        log.debug("Message added to session {} by user {}", sessionId, senderId);
        return message;
    }

    // ── Rich media message (IMAGE / VIDEO / STICKER / VOICE_NOTE) ─────────────

    /**
     * Create a transient rich-media message and return it for immediate relay.
     *
     * ── Storage policy ────────────────────────────────────────────────────────
     * Media messages are intentionally NOT added to session.recentMessages.
     * They exist only as an in-flight WebSocket frame — once delivered, dropped.
     * This means they are never replayed in chat history and never accumulate in
     * the in-memory session object, keeping heap pressure predictable.
     *
     * ── Payload size guard ────────────────────────────────────────────────────
     * Rejects payloads over 50 MB (base64 string length) to prevent a single
     * large video from exhausting heap while it is buffered in the WebSocket
     * send queue.  50 MB base64 ≈ 37.5 MB raw binary.
     *
     * ── Timer / view-once ─────────────────────────────────────────────────────
     * viewTimer is clamped to 0–60 s in ChatMessage.mediaMessage().
     * viewOnce takes precedence over viewTimer on the client side.
     * The server only relays these values; enforcement is client-side.
     *
     * @param sessionId    active session
     * @param senderId     userId of the sender
     * @param type         IMAGE | VIDEO | STICKER | VOICE_NOTE
     * @param mediaPayload base64 data-URI, e.g. "data:image/jpeg;base64,..."
     * @param mimeType     MIME type hint, e.g. "image/jpeg", "video/mp4"
     * @param mediaName    optional display name (may be null)
     * @param viewTimer    0 = no timer; 1–60 = seconds visible after first open
     * @param viewOnce     true = destroy on first open
     * @return transient ChatMessage — caller MUST immediately pass to
     *         ChatMessagingService.sendMediaToSession()
     */
    public ChatMessage addMediaMessage(
            String sessionId,
            Long senderId,
            ChatMessage.MessageType type,
            String mediaPayload,
            String mimeType,
            String mediaName,
            int viewTimer,
            boolean viewOnce) {

        ChatSession session = getSession(sessionId);
        if (session == null)            throw new RuntimeException("Session not found: " + sessionId);
        if (!session.hasUser(senderId)) throw new RuntimeException("User not part of this session");
        if (!session.isActive())        throw new RuntimeException("Session is not active");

        // Guard: reject over-large payloads before they hit the WebSocket send queue
        // 50 MB base64 string ≈ 37.5 MB raw — generous enough for video clips
        if (mediaPayload != null && mediaPayload.length() > 50L * 1024 * 1024) {
            throw new RuntimeException("Media payload exceeds 50 MB limit");
        }

        String senderAnonymousId = session.getUserAnonymousId(senderId);
        ChatMessage message = ChatMessage.mediaMessage(
                sessionId, senderAnonymousId, type,
                mediaPayload, mimeType, mediaName,
                viewTimer, viewOnce);

        // Touch lastActivityAt so the session doesn't time out during media exchange
        session.setLastActivityAt(Instant.now());

        // NOTE: message is NOT added to session.recentMessages — media is ephemeral
        log.debug("Media message {} ({}) created in session {} by user {} | timer={}s viewOnce={}",
                message.getMessageId(), type, sessionId, senderId, viewTimer, viewOnce);
        return message;
    }

    // ── Session end ───────────────────────────────────────────────────────────

    /**
     * Normal, intentional leave (user clicks "Leave chat").
     *
     * Order matters:
     *  1. notifyUserLeft()  — partner notified while session still ACTIVE
     *  2. session.end()     — mark ENDED
     *  3. remove mappings   — clean up
     */
    public void endSession(String sessionId, Long userId) {
        ChatSession session = getSession(sessionId);
        if (session == null) {
            log.warn("Attempted to end non-existent session: {}", sessionId);
            return;
        }
        if (!session.hasUser(userId)) {
            throw new RuntimeException("User not authorized to end this session");
        }

        log.info("Ending chat session {} by user {}", sessionId, userId);

        try {
            chatMessagingService.notifyUserLeft(sessionId, userId);
        } catch (Exception e) {
            log.error("Failed to notify partner in session {} that user {} left", sessionId, userId, e);
        }

        String userAnonymousId = session.getUserAnonymousId(userId);
        session.addMessage(ChatMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .senderId("SYSTEM")
                .content(userAnonymousId + " has left the chat")
                .messageType(ChatMessage.MessageType.USER_LEFT)
                .timestamp(Instant.now())
                .build());
        session.end();

        userSessionMap.remove(session.getUser1Id());
        userSessionMap.remove(session.getUser2Id());
        activeSessions.remove(sessionId);
        // FIX MEMORY LEAK #2 — evict cached emails for users whose session ended
        emailCache.remove(session.getUser1Id());
        emailCache.remove(session.getUser2Id());
    }

    // ── Reconnect flow ────────────────────────────────────────────────────────

    /**
     * Called when a WebSocket disconnect event is detected for this user.
     *
     * Behaviour:
     *  • If the user has an ACTIVE session  → mark DISCONNECTED (grace period starts).
     *  • If already DISCONNECTED / no session → no-op.
     *
     * The partner is sent a soft "partner disconnected" system message so their
     * UI can show a reconnecting spinner rather than treating it as a hard leave.
     */
    public void handleUserDisconnect(Long userId) {
        ChatSession session = getUserSession(userId);
        if (session == null || !session.isActive()) {
            return;
        }

        String sessionId = session.getSessionId();
        log.info("User {} disconnected from session {} — starting grace period of {}s",
                userId, sessionId, Constant.CHAT_RECONNECT_GRACE_PERIOD_SECONDS);

        session.markDisconnected(userId);

        Long partnerId = session.getPartnerId(userId);
        if (partnerId != null) {
            try {
                chatMessagingService.sendSystemMessage(sessionId,
                        "Your partner lost connection. Waiting for them to reconnect...");
            } catch (Exception e) {
                log.warn("Could not send disconnect notification to partner in session {}", sessionId, e);
            }
        }
    }

    /**
     * Called when a user reconnects (e.g. after a page refresh).
     *
     * Returns the existing ChatSession if the user is within the grace period,
     * or null if there is nothing to reconnect to.
     */
    public ChatSession reconnectToSession(Long userId) {
        String sessionId = userSessionMap.get(userId);
        if (sessionId == null) {
            log.debug("reconnectToSession: no session mapping for user {}", userId);
            return null;
        }

        ChatSession session = activeSessions.get(sessionId);
        if (session == null) {
            userSessionMap.remove(userId);
            log.debug("reconnectToSession: stale mapping for user {}, removed", userId);
            return null;
        }

        if (!session.isInReconnectWindow()) {
            log.info("reconnectToSession: grace period expired for user {} session {}", userId, sessionId);
            forceEndSessionForDisconnectedUser(session, userId);
            return null;
        }

        log.info("User {} reconnected to session {} within grace period", userId, sessionId);
        session.markReconnected();
        userSessionMap.put(userId, sessionId);

        String anonId = session.getUserAnonymousId(userId);
        session.addMessage(ChatMessage.systemMessage(sessionId, anonId + " reconnected."));

        try {
            chatMessagingService.sendSystemMessage(sessionId, "Your partner has reconnected!");
        } catch (Exception e) {
            log.warn("Could not send reconnect notification in session {}", sessionId, e);
        }

        return session;
    }

    /**
     * Check if a user has a disconnected-but-reconnectable session.
     * Used by MatchmakingService to skip the "already in session" error.
     */
    public boolean isUserDisconnectedWithActiveSession(Long userId) {
        ChatSession session = getUserSession(userId);
        if (session == null) return false;
        return ChatSession.SessionStatus.DISCONNECTED.equals(session.getStatus())
                && session.isInReconnectWindow();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<ChatMessage> getRecentMessages(String sessionId, Long userId, int limit) {
        ChatSession session = getSession(sessionId);
        if (session == null) return Collections.emptyList();
        if (!session.hasUser(userId)) throw new RuntimeException("User not part of this session");

        List<ChatMessage> messages = session.getRecentMessages();
        if (messages == null || messages.isEmpty()) return Collections.emptyList();

        int startIndex = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(startIndex, messages.size()));
    }

    public boolean isUserInSession(Long userId) {
        ChatSession session = getUserSession(userId);
        return session != null && session.isActive();
    }

    public int getActiveSessionCount() {
        return (int) activeSessions.values().stream()
                .filter(ChatSession::isActive)
                .count();
    }

    public int getOnlineUserCount() {
        return getActiveSessionCount() * 2;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void cleanupExpiredSessions() {
        List<String> toExpire = new ArrayList<>();
        for (Map.Entry<String, ChatSession> entry : activeSessions.entrySet()) {
            if (entry.getValue().hasExpired(Constant.CHAT_MAX_INACTIVE_MINUTES)) {
                toExpire.add(entry.getKey());
            }
        }
        for (String sid : toExpire) {
            ChatSession s = activeSessions.get(sid);
            if (s != null) {
                log.info("Expiring inactive session {}", sid);
                s.setStatus(ChatSession.SessionStatus.EXPIRED);
                userSessionMap.remove(s.getUser1Id());
                userSessionMap.remove(s.getUser2Id());
                activeSessions.remove(sid);
            }
        }
        if (!toExpire.isEmpty()) log.info("Expired {} inactive sessions", toExpire.size());
    }

    public void cleanupEndedSessions() {
        List<String> toRemove = new ArrayList<>();
        Instant cutoff = Instant.now().minusSeconds(60);
        for (Map.Entry<String, ChatSession> entry : activeSessions.entrySet()) {
            ChatSession s = entry.getValue();
            if (!s.isActive() && s.getEndedAt() != null && s.getEndedAt().isBefore(cutoff)) {
                toRemove.add(entry.getKey());
            }
        }
        for (String sid : toRemove) {
            ChatSession s = activeSessions.remove(sid);
            if (s != null) {
                userSessionMap.remove(s.getUser1Id());
                userSessionMap.remove(s.getUser2Id());
                emailCache.remove(s.getUser1Id());
                emailCache.remove(s.getUser2Id());
            }
        }
        if (!toRemove.isEmpty()) log.info("Removed {} ended sessions from memory", toRemove.size());
    }

    /**
     * Called by the scheduler every 15 seconds.
     * Ends sessions whose disconnected user's grace period has expired.
     */
    public void cleanupDisconnectedSessions() {
        List<ChatSession> expired = new ArrayList<>();
        for (ChatSession session : activeSessions.values()) {
            if (ChatSession.SessionStatus.DISCONNECTED.equals(session.getStatus())
                    && !session.isInReconnectWindow()) {
                expired.add(session);
            }
        }

        for (ChatSession session : expired) {
            Long disconnectedUser = session.getDisconnectedUserId();
            log.info("Grace period expired for user {} in session {} — ending session",
                    disconnectedUser, session.getSessionId());
            forceEndSessionForDisconnectedUser(session, disconnectedUser);
        }

        if (!expired.isEmpty()) {
            log.info("Cleaned up {} grace-period-expired sessions", expired.size());
        }
    }

    // ── Principal / routing helpers ───────────────────────────────────────────

    /**
     * Returns the WebSocket principal name (= email) for a user.
     * convertAndSendToUser() routes to /user/{email}/queue/...
     *
     * FIX MEMORY LEAK #2 — served from emailCache when available.
     */
    public String getUserEmail(Long userId) {
        String cached = emailCache.get(userId);
        if (cached != null) return cached;
        String email = fetchEmail(userId);
        emailCache.put(userId, email);
        return email;
    }

    /**
     * Resolve a userId from an email address.
     * Used by ChatMediaController to identify the authenticated caller.
     */
    public Long resolveUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("User not found for email: " + email));
    }

    /**
     * Called by WebSocketDisconnectListener when a WebSocket session drops.
     *
     * @param email the principal name from the disconnect event (always an email
     *              in this project — User.getUsername() returns email)
     */
    public void handleUserDisconnectByEmail(String email) {
        userRepository.findByEmail(email).ifPresentOrElse(
                user -> handleUserDisconnect(user.getId()),
                () -> log.debug("handleUserDisconnectByEmail: no user found for email {}", email)
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * End a session because a disconnected user never reconnected.
     * Notifies the remaining partner and cleans up all mappings.
     */
    private void forceEndSessionForDisconnectedUser(ChatSession session, Long disconnectedUserId) {
        String sessionId = session.getSessionId();
        try {
            // Temporarily restore ACTIVE so notifyUserLeft() can resolve the partner
            session.setStatus(ChatSession.SessionStatus.ACTIVE);
            chatMessagingService.notifyUserLeft(sessionId, disconnectedUserId);
        } catch (Exception e) {
            log.error("Error notifying partner about disconnected user {} in session {}",
                    disconnectedUserId, sessionId, e);
        } finally {
            session.end();
            userSessionMap.remove(session.getUser1Id());
            userSessionMap.remove(session.getUser2Id());
            activeSessions.remove(sessionId);
            emailCache.remove(session.getUser1Id());
            emailCache.remove(session.getUser2Id());
        }
    }

    /** Raw DB lookup — only called on cache miss. */
    private String fetchEmail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return user.getUsername(); // getUsername() returns EMAIL in this project
    }

    private String getActualDisplayName(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        String username = user.getActualUsername();
        if (username == null || username.trim().isEmpty()) {
            String email = user.getEmail();
            username = (email != null && !email.trim().isEmpty())
                    ? email.split("@")[0]
                    : "User" + userId;
        }
        return username;
    }
}