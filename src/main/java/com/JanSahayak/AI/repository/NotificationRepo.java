package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Notification;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface NotificationRepo extends JpaRepository<Notification, Long> {

    /**
     * Find all notifications for a user ordered by creation date
     */
    List<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    /**
     * Find notifications with cursor-based pagination
     */
    List<Notification> findByUserAndIdLessThanOrderByCreatedAtDesc(User user, Long beforeId, Pageable pageable);

    /**
     * Find unread notifications for a user
     */
    List<Notification> findByUserAndIsReadFalseOrderByCreatedAtDesc(User user);

    /**
     * Find unread notifications (limited)
     */
    List<Notification> findByUserAndIsReadFalse(User user);

    /**
     * Count unread notifications for a user
     */
    long countByUserAndIsReadFalse(User user);

    /**
     * Count total notifications for a user
     */
    long countByUser(User user);

    /**
     * Find notifications by type
     */
    List<Notification> findByUserAndNotificationTypeOrderByCreatedAtDesc(
            User user,
            com.JanSahayak.AI.enums.NotificationType type,
            Pageable pageable);

    /**
     * Find notifications by reference
     */
    List<Notification> findByUserAndReferenceIdAndReferenceType(
            User user,
            Long referenceId,
            String referenceType);

    /**
     * Delete all notifications for a user
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user = :user")
    int deleteByUser(@Param("user") User user);

    /**
     * Delete notifications older than a specific date
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoffDate")
    int deleteByCreatedAtBefore(@Param("cutoffDate") Date cutoffDate);

    /**
     * Find notifications created after a specific date
     */
    List<Notification> findByUserAndCreatedAtAfterOrderByCreatedAtDesc(
            User user,
            Date afterDate);

    /**
     * Mark all user notifications as read
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.user = :user AND n.isRead = false")
    int markAllAsReadForUser(@Param("user") User user, @Param("readAt") Date readAt);

    /**
     * Get recent notifications for user (last N days)
     */
    @Query("SELECT n FROM Notification n WHERE n.user = :user AND n.createdAt >= :since ORDER BY n.createdAt DESC")
    List<Notification> findRecentNotifications(
            @Param("user") User user,
            @Param("since") Date since,
            Pageable pageable);

    /**
     * Check if notification exists for specific reference
     */
    boolean existsByUserAndReferenceIdAndReferenceTypeAndNotificationType(
            User user,
            Long referenceId,
            String referenceType,
            com.JanSahayak.AI.enums.NotificationType notificationType);

    /**
     * Find latest notification by type
     */
    Notification findFirstByUserAndNotificationTypeOrderByCreatedAtDesc(
            User user,
            com.JanSahayak.AI.enums.NotificationType type);

    /**
     * Count notifications by type
     */
    long countByUserAndNotificationType(
            User user,
            com.JanSahayak.AI.enums.NotificationType type);

    /**
     * Delete specific notification by ID and user
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.user = :user")
    int deleteByIdAndUser(@Param("id") Long id, @Param("user") User user);
}