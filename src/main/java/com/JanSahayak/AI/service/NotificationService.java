package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;

import com.JanSahayak.AI.DTO.NotificationDto;
import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.enums.NotificationType;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.repository.NotificationRepo;
import com.JanSahayak.AI.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comprehensive Notification Service
 * Handles all types of notifications: Post likes, comments, tags, broadcasts, etc.
 * Supports real-time WebSocket delivery and persistent storage
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepo notificationRepository;
    private final UserRepo userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ===== POST INTERACTION NOTIFICATIONS =====

    /**
     * Send notification when someone likes a post
     */
    @Async
    @Transactional
    public void notifyPostLiked(Post post, User likedBy) {
        try {
            if (post == null || post.getUser() == null || likedBy == null) {
                log.warn("Invalid parameters for post like notification");
                return;
            }

            // Don't notify if user liked their own post
            if (post.getUser().getId().equals(likedBy.getId())) {
                return;
            }

            String title = "New Like on Your Post";
            String message = String.format("%s liked your post", likedBy.getActualUsername());
            String actionUrl = "/posts/" + post.getId();

            Notification notification = createNotification(
                    post.getUser(),
                    NotificationType.POST_LIKE,
                    title,
                    message,
                    post.getId(),
                    "POST",
                    actionUrl,
                    likedBy
            );

            sendRealtimeNotification(notification);

            log.debug("Post like notification sent: postId={}, likedBy={}",
                    post.getId(), likedBy.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send post like notification", e);
        }
    }

    /**
     * Send notification when someone likes a social post
     */
    @Async
    @Transactional
    public void notifySocialPostLiked(SocialPost socialPost, User likedBy) {
        try {
            if (socialPost == null || socialPost.getUser() == null || likedBy == null) {
                log.warn("Invalid parameters for social post like notification");
                return;
            }

            // Don't notify if user liked their own post
            if (socialPost.getUser().getId().equals(likedBy.getId())) {
                return;
            }

            String title = "New Like on Your Post";
            String message = String.format("%s liked your social post", likedBy.getActualUsername());
            String actionUrl = "/social-posts/" + socialPost.getId();

            Notification notification = createNotification(
                    socialPost.getUser(),
                    NotificationType.POST_LIKE,
                    title,
                    message,
                    socialPost.getId(),
                    "SOCIAL_POST",
                    actionUrl,
                    likedBy
            );

            sendRealtimeNotification(notification);

            log.debug("Social post like notification sent: postId={}, likedBy={}",
                    socialPost.getId(), likedBy.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send social post like notification", e);
        }
    }

    /**
     * Send notification when someone comments on a post
     */
    @Async
    @Transactional
    public void notifyPostCommented(Post post, Comment comment, User commentedBy) {
        try {
            if (post == null || post.getUser() == null || commentedBy == null) {
                log.warn("Invalid parameters for post comment notification");
                return;
            }

            // Don't notify if user commented on their own post
            if (post.getUser().getId().equals(commentedBy.getId())) {
                return;
            }

            String title = "New Comment on Your Post";
            String message = String.format("%s commented: \"%s\"",
                    commentedBy.getActualUsername(),
                    truncateText(comment.getText(), 50));
            String actionUrl = "/posts/" + post.getId() + "#comment-" + comment.getId();

            Notification notification = createNotification(
                    post.getUser(),
                    NotificationType.POST_COMMENT,
                    title,
                    message,
                    post.getId(),
                    "POST",
                    actionUrl,
                    commentedBy
            );

            sendRealtimeNotification(notification);

            log.debug("Post comment notification sent: postId={}, commentedBy={}",
                    post.getId(), commentedBy.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send post comment notification", e);
        }
    }

    /**
     * Send notification when someone comments on a social post
     */
    @Async
    @Transactional
    public void notifySocialPostCommented(SocialPost socialPost, Comment comment, User commentedBy) {
        try {
            if (socialPost == null || socialPost.getUser() == null || commentedBy == null) {
                log.warn("Invalid parameters for social post comment notification");
                return;
            }

            // Don't notify if user commented on their own post
            if (socialPost.getUser().getId().equals(commentedBy.getId())) {
                return;
            }

            String title = "New Comment on Your Post";
            String message = String.format("%s commented: \"%s\"",
                    commentedBy.getActualUsername(),
                    truncateText(comment.getText(), 50));
            String actionUrl = "/social-posts/" + socialPost.getId() + "#comment-" + comment.getId();

            Notification notification = createNotification(
                    socialPost.getUser(),
                    NotificationType.POST_COMMENT,
                    title,
                    message,
                    socialPost.getId(),
                    "SOCIAL_POST",
                    actionUrl,
                    commentedBy
            );

            sendRealtimeNotification(notification);

            log.debug("Social post comment notification sent: postId={}, commentedBy={}",
                    socialPost.getId(), commentedBy.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send social post comment notification", e);
        }
    }

    /**
     * Send notification when someone replies to a comment
     */
    @Async
    @Transactional
    public void notifyCommentReplied(Comment originalComment, Comment reply, User repliedBy) {
        try {
            if (originalComment == null || originalComment.getUser() == null || repliedBy == null) {
                log.warn("Invalid parameters for comment reply notification");
                return;
            }

            // Don't notify if user replied to their own comment
            if (originalComment.getUser().getId().equals(repliedBy.getId())) {
                return;
            }

            String title = "New Reply to Your Comment";
            String message = String.format("%s replied: \"%s\"",
                    repliedBy.getActualUsername(),
                    truncateText(reply.getText(), 50));

            // Determine action URL based on whether it's a post or social post comment
            String actionUrl;
            Long referenceId;
            if (originalComment.getPost() != null) {
                actionUrl = "/posts/" + originalComment.getPost().getId() + "#comment-" + reply.getId();
                referenceId = originalComment.getPost().getId();
            } else if (originalComment.getSocialPost() != null) {
                actionUrl = "/social-posts/" + originalComment.getSocialPost().getId() + "#comment-" + reply.getId();
                referenceId = originalComment.getSocialPost().getId();
            } else {
                log.warn("Comment has no associated post or social post");
                return;
            }

            Notification notification = createNotification(
                    originalComment.getUser(),
                    NotificationType.COMMENT_REPLY,
                    title,
                    message,
                    referenceId,
                    "COMMENT",
                    actionUrl,
                    repliedBy
            );

            sendRealtimeNotification(notification);

            log.debug("Comment reply notification sent: commentId={}, repliedBy={}",
                    originalComment.getId(), repliedBy.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send comment reply notification", e);
        }
    }

    // ===== TAGGING NOTIFICATIONS =====

    /**
     * Send notification when user is tagged in a post
     */
    @Async
    @Transactional
    public void notifyUserTagged(Post post, User taggedUser, User taggedBy) {
        try {
            if (post == null || taggedUser == null || taggedBy == null) {
                log.warn("Invalid parameters for user tag notification");
                return;
            }

            // Don't notify if user tagged themselves
            if (taggedUser.getId().equals(taggedBy.getId())) {
                return;
            }

            String title = "You Were Tagged in a Post";
            String message = String.format("%s tagged you in their post", taggedBy.getActualUsername());
            String actionUrl = "/posts/" + post.getId();

            Notification notification = createNotification(
                    taggedUser,
                    NotificationType.POST_TAGGED,
                    title,
                    message,
                    post.getId(),
                    "POST",
                    actionUrl,
                    taggedBy
            );

            sendRealtimeNotification(notification);

            log.debug("User tag notification sent: postId={}, taggedUser={}",
                    post.getId(), taggedUser.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send user tag notification", e);
        }
    }

    /**
     * Send notifications to multiple tagged users
     */
    @Async
    @Transactional
    public void notifyMultipleUsersTagged(Post post, List<User> taggedUsers, User taggedBy) {
        try {
            if (post == null || taggedUsers == null || taggedUsers.isEmpty() || taggedBy == null) {
                return;
            }

            for (User taggedUser : taggedUsers) {
                notifyUserTagged(post, taggedUser, taggedBy);
            }

            log.info("Sent {} tag notifications for post: {}", taggedUsers.size(), post.getId());

        } catch (Exception e) {
            log.error("Failed to send multiple user tag notifications", e);
        }
    }

    // ===== POST STATUS NOTIFICATIONS =====

    /**
     * Send notification when post is resolved
     */
    @Async
    @Transactional
    public void notifyPostResolved(Post post, User resolvedBy) {
        try {
            if (post == null || post.getUser() == null) {
                log.warn("Invalid parameters for post resolved notification");
                return;
            }

            String title = "Your Post Was Resolved";
            String message = resolvedBy != null
                    ? String.format("Your post was marked as resolved by %s", resolvedBy.getActualUsername())
                    : "Your post was marked as resolved";
            String actionUrl = "/posts/" + post.getId();

            Notification notification = createNotification(
                    post.getUser(),
                    NotificationType.POST_RESOLVED,
                    title,
                    message,
                    post.getId(),
                    "POST",
                    actionUrl,
                    resolvedBy
            );

            sendRealtimeNotification(notification);

            log.debug("Post resolved notification sent: postId={}", post.getId());

        } catch (Exception e) {
            log.error("Failed to send post resolved notification", e);
        }
    }

    // ===== BROADCAST NOTIFICATIONS =====

    /**
     * Send broadcast notification to multiple users based on location
     */
    @Async
    @Transactional
    public void notifyBroadcast(Post broadcastPost, List<User> targetUsers) {
        try {
            if (broadcastPost == null || targetUsers == null || targetUsers.isEmpty()) {
                log.warn("Invalid parameters for broadcast notification");
                return;
            }

            NotificationType notificationType = determineBroadcastNotificationType(broadcastPost);
            String title = getBroadcastTitle(broadcastPost);
            String message = String.format("New broadcast from %s: %s",
                    broadcastPost.getUser().getActualUsername(),
                    truncateText(broadcastPost.getContent(), 100));
            String actionUrl = "/posts/" + broadcastPost.getId();

            // FIX MEMORY LEAK #9 — previously built the entire List<Notification> in heap
            // before calling saveAll().  For country-level broadcasts this can be tens of
            // thousands of objects alive simultaneously.
            // We now process in batches of NOTIFICATION_BROADCAST_BATCH_SIZE (500) so only
            // one batch is live at a time; each batch is eligible for GC after saveAll().
            final int BATCH_SIZE = 500;
            List<Notification> batch = new ArrayList<>(BATCH_SIZE);
            int totalSent = 0;

            for (User user : targetUsers) {
                if (user.getId().equals(broadcastPost.getUser().getId())) continue;

                Notification notification = Notification.builder()
                        .user(user)
                        .notificationType(notificationType)
                        .title(title)
                        .message(message)
                        .referenceId(broadcastPost.getId())
                        .referenceType("BROADCAST_POST")
                        .actionUrl(actionUrl)
                        .triggeredBy(broadcastPost.getUser())
                        .isRead(false)
                        .createdAt(new Date())
                        .build();

                batch.add(notification);

                if (batch.size() == BATCH_SIZE) {
                    List<Notification> saved = notificationRepository.saveAll(batch);
                    saved.forEach(this::sendRealtimeNotification);
                    totalSent += saved.size();
                    batch = new ArrayList<>(BATCH_SIZE); // release previous batch for GC
                }
            }

            // Flush remaining
            if (!batch.isEmpty()) {
                List<Notification> saved = notificationRepository.saveAll(batch);
                saved.forEach(this::sendRealtimeNotification);
                totalSent += saved.size();
            }

            log.info("Sent {} broadcast notifications for post: {}", totalSent, broadcastPost.getId());

        } catch (Exception e) {
            log.error("Failed to send broadcast notifications", e);
        }
    }

    // ===== SYSTEM NOTIFICATIONS =====

    /**
     * Send system announcement to user
     */
    @Async
    @Transactional
    public void sendSystemAnnouncement(User user, String title, String message) {
        try {
            if (user == null) {
                log.warn("Cannot send system announcement to null user");
                return;
            }

            Notification notification = createNotification(
                    user,
                    NotificationType.SYSTEM_ANNOUNCEMENT,
                    title,
                    message,
                    null,
                    "SYSTEM",
                    null,
                    null
            );

            sendRealtimeNotification(notification);

            log.debug("System announcement sent to user: {}", user.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send system announcement", e);
        }
    }

    /**
     * Send system announcement to multiple users
     */
    @Async
    @Transactional
    public void sendSystemAnnouncementToUsers(List<User> users, String title, String message) {
        try {
            if (users == null || users.isEmpty()) {
                return;
            }

            // FIX MEMORY LEAK #9 — same batching fix as notifyBroadcast
            final int BATCH_SIZE = 500;
            List<Notification> batch = new ArrayList<>(BATCH_SIZE);

            for (User user : users) {
                Notification notification = Notification.builder()
                        .user(user)
                        .notificationType(NotificationType.SYSTEM_ANNOUNCEMENT)
                        .title(title)
                        .message(message)
                        .referenceType("SYSTEM")
                        .isRead(false)
                        .createdAt(new Date())
                        .build();
                batch.add(notification);

                if (batch.size() == BATCH_SIZE) {
                    List<Notification> saved = notificationRepository.saveAll(batch);
                    saved.forEach(this::sendRealtimeNotification);
                    batch = new ArrayList<>(BATCH_SIZE);
                }
            }
            if (!batch.isEmpty()) {
                List<Notification> saved = notificationRepository.saveAll(batch);
                saved.forEach(this::sendRealtimeNotification);
            }

            log.info("Sent system announcement to {} users", users.size());

        } catch (Exception e) {
            log.error("Failed to send system announcement to multiple users", e);
        }
    }

    /**
     * Send account update notification
     */
    @Async
    @Transactional
    public void notifyAccountUpdate(User user, String updateMessage) {
        try {
            if (user == null) {
                return;
            }

            String title = "Account Update";
            Notification notification = createNotification(
                    user,
                    NotificationType.ACCOUNT_UPDATE,
                    title,
                    updateMessage,
                    null,
                    "SYSTEM",
                    "/profile",
                    null
            );

            sendRealtimeNotification(notification);

            log.debug("Account update notification sent to user: {}", user.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send account update notification", e);
        }
    }

    // ===== DEPARTMENT NOTIFICATIONS =====

    /**
     * Send department message to user
     */
    @Async
    @Transactional
    public void sendDepartmentMessage(User user, String departmentName, String message, User sentBy) {
        try {
            if (user == null) {
                return;
            }

            String title = String.format("Message from %s", departmentName);

            Notification notification = createNotification(
                    user,
                    NotificationType.DEPARTMENT_MESSAGE,
                    title,
                    message,
                    null,
                    "DEPARTMENT",
                    null,
                    sentBy
            );

            sendRealtimeNotification(notification);

            log.debug("Department message sent to user: {}", user.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send department message", e);
        }
    }

    /**
     * Notify post requires attention from authorities
     */
    @Async
    @Transactional
    public void notifyPostAttentionRequired(Post post, User notifyUser, String reason) {
        try {
            if (post == null || notifyUser == null) {
                return;
            }

            String title = "Post Requires Attention";
            String message = String.format("A post requires your attention. Reason: %s", reason);
            String actionUrl = "/posts/" + post.getId();

            Notification notification = createNotification(
                    notifyUser,
                    NotificationType.POST_ATTENTION_REQUIRED,
                    title,
                    message,
                    post.getId(),
                    "POST",
                    actionUrl,
                    null
            );

            sendRealtimeNotification(notification);

            log.debug("Post attention notification sent to user: {}", notifyUser.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to send post attention notification", e);
        }
    }

    // ===== NOTIFICATION MANAGEMENT =====

    /**
     * Get user's notifications with pagination
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<NotificationDto> getUserNotifications(User user, Long beforeId, Integer limit) {
        try {
            if (user == null) {
                throw new ValidationException("User cannot be null");
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getUserNotifications", beforeId, limit,
                    Constant.NOTIFICATION_DEFAULT_LIMIT, Constant.NOTIFICATION_MAX_LIMIT);

            List<Notification> notifications;
            if (setup.hasCursor()) {
                notifications = notificationRepository.findByUserAndIdLessThanOrderByCreatedAtDesc(
                        user, setup.getSanitizedCursor(),
                        PageRequest.of(0, setup.getValidatedLimit()));
            } else {
                notifications = notificationRepository.findByUserOrderByCreatedAtDesc(
                        user, PageRequest.of(0, setup.getValidatedLimit()));
            }

            List<NotificationDto> notificationDtos = notifications.stream()
                    .map(NotificationDto::fromNotification)
                    .collect(Collectors.toList());

            boolean hasMore = notifications.size() == setup.getValidatedLimit();
            Long nextCursor = hasMore && !notifications.isEmpty()
                    ? notifications.get(notifications.size() - 1).getId()
                    : null;

            return PaginatedResponse.of(notificationDtos, hasMore, nextCursor, setup.getValidatedLimit());

        } catch (Exception e) {
            log.error("Failed to get user notifications", e);
            throw new ServiceException("Failed to get notifications: " + e.getMessage(), e);
        }
    }

    /**
     * Get unread notifications for user
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> getUnreadNotifications(User user) {
        try {
            if (user == null) {
                throw new ValidationException("User cannot be null");
            }

            List<Notification> notifications = notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(user);

            return notifications.stream()
                    .map(NotificationDto::fromNotification)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to get unread notifications", e);
            throw new ServiceException("Failed to get unread notifications: " + e.getMessage(), e);
        }
    }

    /**
     * Get unread notification count
     */
    @Transactional(readOnly = true)
    public long getUnreadNotificationCount(User user) {
        try {
            if (user == null) {
                return 0;
            }

            return notificationRepository.countByUserAndIsReadFalse(user);

        } catch (Exception e) {
            log.error("Failed to get unread notification count", e);
            return 0;
        }
    }

    /**
     * Mark notification as read
     */
    @Transactional
    public void markNotificationAsRead(Long notificationId, User user) {
        try {
            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new ValidationException("Notification not found"));

            if (!notification.getUser().getId().equals(user.getId())) {
                throw new SecurityException("User does not have permission to modify this notification");
            }

            if (!notification.getIsRead()) {
                notification.markAsRead();
                notificationRepository.save(notification);

                log.debug("Notification marked as read: id={}", notificationId);
            }

        } catch (Exception e) {
            log.error("Failed to mark notification as read: id={}", notificationId, e);
            throw new ServiceException("Failed to mark notification as read: " + e.getMessage(), e);
        }
    }

    /**
     * Mark all notifications as read for user
     */
    @Transactional
    public void markAllNotificationsAsRead(User user) {
        try {
            if (user == null) {
                throw new ValidationException("User cannot be null");
            }

            // MEMORY LEAK FIX: the original code called findByUserAndIsReadFalse() which
            // loaded EVERY unread notification for the user into a List<Notification> in heap,
            // mutated each one in a Java loop, then called saveAll() on the entire list.
            // For an active user with hundreds of unread notifications this could hold thousands
            // of fully-hydrated Notification objects live simultaneously inside one @Transactional
            // method, creating massive GC pressure and risking OOM under concurrent load.
            //
            // Fix: single bulk UPDATE executed entirely in the DB — zero entities loaded into JVM.
            // The repository method is:
            //   @Modifying
            //   @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now WHERE n.user = :user AND n.isRead = false")
            //   int markAllAsReadForUser(@Param("user") User user, @Param("now") Date now);
            Date now = new Date();
            int updatedCount = notificationRepository.markAllAsReadForUser(user, now);

            log.info("Marked {} notifications as read for user: {}",
                    updatedCount, user.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to mark all notifications as read", e);
            throw new ServiceException("Failed to mark all notifications as read: " + e.getMessage(), e);
        }
    }

    /**
     * Delete notification
     */
    @Transactional
    public void deleteNotification(Long notificationId, User user) {
        try {
            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new ValidationException("Notification not found"));

            if (!notification.getUser().getId().equals(user.getId())) {
                throw new SecurityException("User does not have permission to delete this notification");
            }

            notificationRepository.delete(notification);

            log.debug("Notification deleted: id={}", notificationId);

        } catch (Exception e) {
            log.error("Failed to delete notification: id={}", notificationId, e);
            throw new ServiceException("Failed to delete notification: " + e.getMessage(), e);
        }
    }

    /**
     * Delete all notifications for user
     */
    @Transactional
    public void deleteAllNotifications(User user) {
        try {
            if (user == null) {
                throw new ValidationException("User cannot be null");
            }

            int deletedCount = notificationRepository.deleteByUser(user);

            log.info("Deleted {} notifications for user: {}", deletedCount, user.getActualUsername());

        } catch (Exception e) {
            log.error("Failed to delete all notifications", e);
            throw new ServiceException("Failed to delete all notifications: " + e.getMessage(), e);
        }
    }

    /**
     * Clean up old notifications (older than 30 days)
     */
    @Transactional
    public void cleanupOldNotifications() {
        try {
            Date cutoffDate = new Date(System.currentTimeMillis() -
                    (long) Constant.NOTIFICATION_MAX_AGE_DAYS * 24 * 60 * 60 * 1000);

            int deletedCount = notificationRepository.deleteByCreatedAtBefore(cutoffDate);

            log.info("Cleaned up {} old notifications (older than {} days)",
                    deletedCount, Constant.NOTIFICATION_MAX_AGE_DAYS);

        } catch (Exception e) {
            log.error("Failed to cleanup old notifications", e);
        }
    }

    // ===== HELPER METHODS =====

    /**
     * Create and save a notification
     */
    private Notification createNotification(
            User user,
            NotificationType type,
            String title,
            String message,
            Long referenceId,
            String referenceType,
            String actionUrl,
            User triggeredBy) {

        Notification notification = Notification.builder()
                .user(user)
                .notificationType(type)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .actionUrl(actionUrl)
                .triggeredBy(triggeredBy)
                .isRead(false)
                .createdAt(new Date())
                .build();

        return notificationRepository.save(notification);
    }

    /**
     * Send real-time notification via WebSocket
     */
    private void sendRealtimeNotification(Notification notification) {
        try {
            NotificationDto dto = NotificationDto.fromNotification(notification);

            messagingTemplate.convertAndSendToUser(
                    notification.getUser().getId().toString(),
                    "/queue/notifications",
                    dto
            );

            log.debug("Real-time notification sent to user: {}",
                    notification.getUser().getActualUsername());

        } catch (Exception e) {
            log.warn("Failed to send real-time notification (notification still saved): {}",
                    e.getMessage());
        }
    }

    /**
     * Determine notification type for broadcast
     */
    private NotificationType determineBroadcastNotificationType(Post post) {
        if (post.getBroadcastScope() == null) {
            return NotificationType.BROADCAST_NEW;
        }

        switch (post.getBroadcastScope()) {
            case COUNTRY:
                return NotificationType.BROADCAST_COUNTRY;
            case STATE:
                return NotificationType.BROADCAST_STATE;
            case DISTRICT:
                return NotificationType.BROADCAST_DISTRICT;
            default:
                return NotificationType.BROADCAST_NEW;
        }
    }

    /**
     * Get broadcast title based on scope
     */
    private String getBroadcastTitle(Post post) {
        if (post.getBroadcastScope() == null) {
            return "New Broadcast";
        }

        switch (post.getBroadcastScope()) {
            case COUNTRY:
                return "Country-wide Broadcast";
            case STATE:
                return "State-level Broadcast";
            case DISTRICT:
                return "District-level Broadcast";
            default:
                return "New Broadcast in Your Area";
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}