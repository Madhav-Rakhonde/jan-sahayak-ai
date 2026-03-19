package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.ChatSession;
import com.JanSahayak.AI.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages the matchmaking queue for anonymous 1-vs-1 chats.
 *
 * ── Reconnect awareness ───────────────────────────────────────────────────────
 * Before adding a user to the queue, findMatch() first checks whether they have
 * a disconnected-but-still-valid session (e.g. they just refreshed the page).
 * If so, reconnectToSession() is called and the existing sessionId is returned
 * immediately — no new match is needed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchmakingService {

    private final ChatSessionService chatSessionService;

    private final Queue<QueueEntry>      waitingQueue   = new ConcurrentLinkedQueue<>();
    private final Map<Long, QueueEntry>  searchingUsers = new ConcurrentHashMap<>();
    private final ReentrantLock          matchmakingLock = new ReentrantLock();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Entry point called when a user wants to start / resume a chat.
     *
     * Priority order:
     *  1. User has a disconnected session within its grace period → reconnect.
     *  2. User is already queued                                  → no-op (return null).
     *  3. Another user is waiting                                 → pair them.
     *  4. No one waiting                                          → add to queue.
     *
     * @return sessionId if matched/reconnected, null if added to the waiting queue.
     */
    public String findMatch(User user) {
        Long userId = user.getId();
        log.info("User {} entering matchmaking", userId);

        // ── 1. Reconnect to an existing disconnected session ──────────────────
        if (chatSessionService.isUserDisconnectedWithActiveSession(userId)) {
            ChatSession resumed = chatSessionService.reconnectToSession(userId);
            if (resumed != null) {
                log.info("User {} reconnected to existing session {}", userId, resumed.getSessionId());
                return resumed.getSessionId();
            }
            // Grace period expired between the check and the call — fall through to normal matchmaking
        }

        // ── 2. Already queued ─────────────────────────────────────────────────
        if (isUserSearching(userId)) {
            log.warn("User {} is already in matchmaking queue", userId);
            return null;
        }

        // ── 3 & 4. Normal matchmaking ─────────────────────────────────────────
        matchmakingLock.lock();
        try {
            QueueEntry partner = pollAvailablePartner(userId);

            if (partner != null) {
                log.info("Match found: user {} paired with user {}", userId, partner.getUserId());
                // MEMORY LEAK FIX: wrap createSession() in try/finally so BOTH users are
                // always removed from searchingUsers even when createSession() throws
                // (e.g. race condition where one user is already in a session).
                // Without this, a RuntimeException leaves both entries in the map
                // permanently — isUserSearching() returns true forever, blocking
                // both users from ever matching again until a server restart.
                try {
                    ChatSession session = chatSessionService.createSession(userId, partner.getUserId());
                    return session.getSessionId();
                } finally {
                    removeFromSearch(userId);
                    removeFromSearch(partner.getUserId());
                }
            } else {
                addToQueue(userId);
                log.info("User {} added to queue — waiting for partner", userId);
                return null;
            }
        } finally {
            matchmakingLock.unlock();
        }
    }

    public void cancelSearch(Long userId) {
        log.info("User {} cancelling matchmaking search", userId);
        matchmakingLock.lock();
        try {
            removeFromQueue(userId);
            removeFromSearch(userId);
        } finally {
            matchmakingLock.unlock();
        }
    }

    public boolean isUserSearching(Long userId) {
        QueueEntry entry = searchingUsers.get(userId);
        if (entry == null) return false;
        if (entry.hasExpired()) {
            removeFromSearch(userId);
            return false;
        }
        return true;
    }

    public long getQueueSize() {
        return waitingQueue.size();
    }

    public void cleanupExpiredSearches() {
        List<Long> expiredUsers = new ArrayList<>();
        for (Map.Entry<Long, QueueEntry> entry : searchingUsers.entrySet()) {
            if (entry.getValue().hasExpired()) expiredUsers.add(entry.getKey());
        }

        matchmakingLock.lock();
        try {
            for (Long userId : expiredUsers) {
                removeFromQueue(userId);
                removeFromSearch(userId);
            }
        } finally {
            matchmakingLock.unlock();
        }

        if (!expiredUsers.isEmpty()) {
            log.info("Cleaned up {} expired matchmaking entries", expiredUsers.size());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private QueueEntry pollAvailablePartner(Long userId) {
        while (!waitingQueue.isEmpty()) {
            QueueEntry partner = waitingQueue.poll();
            if (partner == null) break;

            if (partner.hasExpired()) {
                removeFromSearch(partner.getUserId());
                continue;
            }
            if (partner.getUserId().equals(userId)) {
                log.warn("Prevented self-match for user {}", userId);
                waitingQueue.offer(partner);
                continue;
            }
            return partner;
        }
        return null;
    }

    private void addToQueue(Long userId) {
        // FIX MEMORY LEAK #3 — guard against duplicate queue entries.
        // Without this guard a race condition between two concurrent findMatch()
        // calls for the same userId could add two QueueEntry objects, causing the
        // queue to grow without the second entry ever being removed.
        if (searchingUsers.containsKey(userId)) {
            log.debug("addToQueue: userId={} already in searchingUsers — skipping duplicate add", userId);
            return;
        }
        QueueEntry entry = new QueueEntry(userId);
        waitingQueue.offer(entry);
        searchingUsers.put(userId, entry);
    }

    private void removeFromQueue(Long userId) {
        waitingQueue.removeIf(e -> e.getUserId().equals(userId));
    }

    private void removeFromSearch(Long userId) {
        searchingUsers.remove(userId);
    }

    // ── Inner class ───────────────────────────────────────────────────────────

    private static class QueueEntry {
        private final Long    userId;
        private final Instant joinedAt;

        QueueEntry(Long userId) {
            this.userId   = userId;
            this.joinedAt = Instant.now();
        }

        Long getUserId() { return userId; }

        boolean hasExpired() {
            return Instant.now().isAfter(
                    joinedAt.plusSeconds(Constant.MATCHMAKING_SEARCH_TIMEOUT_SECONDS));
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return Objects.equals(userId, ((QueueEntry) o).userId);
        }

        @Override public int hashCode() { return Objects.hash(userId); }
    }
}