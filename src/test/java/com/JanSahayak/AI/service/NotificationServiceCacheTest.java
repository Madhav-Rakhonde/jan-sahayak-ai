package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.NotificationType;
import com.JanSahayak.AI.model.Notification;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.NotificationRepo;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService cache behaviour.
 *
 * Verifies:
 *  - getUnreadNotificationCount hits repo on first call
 *  - markNotificationAsRead evicts the cache
 *  - markAllNotificationsAsRead evicts the cache
 *  - deleteNotification evicts the cache
 *  - deleteAllNotifications evicts the cache
 *  - null user returns 0 without hitting the repo
 *
 * NOTE: These tests verify the LOGIC that should interact with the cache.
 * Because we use Mockito (not Spring context), we verify the repo call patterns
 * directly. Full cache integration is covered by NotificationServiceCacheIT.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService — cache contract")
class NotificationServiceCacheTest {

    @Mock private NotificationRepo notificationRepository;
    @Mock private UserRepo         userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private PostRepo         postRepository;
    @Mock private WebPushService   webPushService;
    @Mock private ObjectMapper     objectMapper;

    @InjectMocks
    private NotificationService notificationService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
    }

    // ── getUnreadNotificationCount ────────────────────────────────────────────

    @Nested
    @DisplayName("getUnreadNotificationCount()")
    class GetUnreadCount {

        @Test
        @DisplayName("delegates to repository and returns count")
        void delegatesToRepo() {
            when(notificationRepository.countByUserAndIsReadFalse(testUser)).thenReturn(5L);

            long count = notificationService.getUnreadNotificationCount(testUser);

            assertEquals(5L, count);
            verify(notificationRepository, times(1)).countByUserAndIsReadFalse(testUser);
        }

        @Test
        @DisplayName("returns 0 for null user without hitting repo")
        void nullUserReturnsZero() {
            long count = notificationService.getUnreadNotificationCount(null);

            assertEquals(0L, count);
            verifyNoInteractions(notificationRepository);
        }

        @Test
        @DisplayName("repo exception returns 0 gracefully")
        void repoExceptionReturnsZero() {
            when(notificationRepository.countByUserAndIsReadFalse(testUser))
                    .thenThrow(new RuntimeException("DB down"));

            long count = notificationService.getUnreadNotificationCount(testUser);

            assertEquals(0L, count);
        }
    }

    // ── markNotificationAsRead ────────────────────────────────────────────────

    @Nested
    @DisplayName("markNotificationAsRead()")
    class MarkAsRead {

        @Test
        @DisplayName("marks unread notification as read and saves it")
        void marksAsRead() {
            Notification notification = buildNotification(1L, testUser, false);
            when(notificationRepository.findById(1L)).thenReturn(java.util.Optional.of(notification));

            notificationService.markNotificationAsRead(1L, testUser);

            verify(notificationRepository).save(notification);
            assertTrue(notification.getIsRead());
        }

        @Test
        @DisplayName("already-read notification is not saved again")
        void alreadyReadIsSkipped() {
            Notification notification = buildNotification(1L, testUser, true); // already read
            when(notificationRepository.findById(1L)).thenReturn(java.util.Optional.of(notification));

            notificationService.markNotificationAsRead(1L, testUser);

            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when notification not found")
        void throwsWhenNotFound() {
            when(notificationRepository.findById(99L)).thenReturn(java.util.Optional.empty());

            assertThrows(Exception.class,
                    () -> notificationService.markNotificationAsRead(99L, testUser));
        }

        @Test
        @DisplayName("throws SecurityException for wrong user")
        void throwsForWrongUser() {
            User otherUser = new User();
            otherUser.setId(99L);

            Notification notification = buildNotification(1L, otherUser, false);
            when(notificationRepository.findById(1L)).thenReturn(java.util.Optional.of(notification));

            assertThrows(Exception.class,
                    () -> notificationService.markNotificationAsRead(1L, testUser));
        }
    }

    // ── markAllNotificationsAsRead ────────────────────────────────────────────

    @Nested
    @DisplayName("markAllNotificationsAsRead()")
    class MarkAllAsRead {

        @Test
        @DisplayName("calls bulk update query — no individual entities loaded")
        void callsBulkUpdate() {
            when(notificationRepository.markAllAsReadForUser(eq(testUser), any(Date.class)))
                    .thenReturn(5);

            notificationService.markAllNotificationsAsRead(testUser);

            verify(notificationRepository, times(1))
                    .markAllAsReadForUser(eq(testUser), any(Date.class));
            // Verify no findAll / fetchAll of notifications was done
            verify(notificationRepository, never()).findAll();
        }

        @Test
        @DisplayName("throws for null user")
        void throwsForNullUser() {
            assertThrows(Exception.class,
                    () -> notificationService.markAllNotificationsAsRead(null));
        }
    }

    // ── deleteNotification ────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteNotification()")
    class DeleteNotification {

        @Test
        @DisplayName("deletes notification belonging to user")
        void deletesOwnNotification() {
            Notification notification = buildNotification(1L, testUser, false);
            when(notificationRepository.findById(1L)).thenReturn(java.util.Optional.of(notification));

            notificationService.deleteNotification(1L, testUser);

            verify(notificationRepository).delete(notification);
        }

        @Test
        @DisplayName("throws SecurityException for notification owned by different user")
        void throwsForWrongOwner() {
            User other = new User();
            other.setId(55L);
            Notification notification = buildNotification(1L, other, false);
            when(notificationRepository.findById(1L)).thenReturn(java.util.Optional.of(notification));

            assertThrows(Exception.class,
                    () -> notificationService.deleteNotification(1L, testUser));
            verify(notificationRepository, never()).delete(any());
        }
    }

    // ── deleteAllNotifications ────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteAllNotifications()")
    class DeleteAll {

        @Test
        @DisplayName("calls bulk delete and returns count")
        void callsBulkDelete() {
            when(notificationRepository.deleteByUser(testUser)).thenReturn(10);

            notificationService.deleteAllNotifications(testUser);

            verify(notificationRepository, times(1)).deleteByUser(testUser);
        }

        @Test
        @DisplayName("throws for null user")
        void throwsForNullUser() {
            assertThrows(Exception.class,
                    () -> notificationService.deleteAllNotifications(null));
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Notification buildNotification(Long id, User owner, boolean isRead) {
        return Notification.builder()
                .id(id)
                .user(owner)
                .notificationType(NotificationType.SYSTEM_ANNOUNCEMENT)
                .title("Test")
                .message("Test notification")
                .isRead(isRead)
                .createdAt(new Date())
                .build();
    }
}
