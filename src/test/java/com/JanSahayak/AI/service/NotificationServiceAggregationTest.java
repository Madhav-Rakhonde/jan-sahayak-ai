package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.NotificationType;
import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.repository.NotificationRepo;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceAggregationTest {

    @Mock
    private NotificationRepo notificationRepository;

    @Mock
    private UserRepo userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PostRepo postRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User postOwner;
    private User liker1;
    private User liker2;
    private Post post;
    private SocialPost socialPost;

    @BeforeEach
    void setUp() {
        postOwner = new User();
        postOwner.setId(1L);
        postOwner.setUsername("postOwner");

        liker1 = new User();
        liker1.setId(2L);
        liker1.setUsername("liker1");

        liker2 = new User();
        liker2.setId(3L);
        liker2.setUsername("liker2");

        post = new Post();
        post.setId(10L);
        post.setUser(postOwner);

        socialPost = new SocialPost();
        socialPost.setId(20L);
        socialPost.setUser(postOwner);
    }

    @Test
    void testNotifyPostLiked_CreatesNewNotificationIfNoneExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(postOwner));
        when(userRepository.findById(2L)).thenReturn(Optional.of(liker1));
        when(notificationRepository.findByUserAndReferenceIdAndReferenceType(postOwner, post.getId(), "POST"))
                .thenReturn(Collections.emptyList());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.notifyPostLiked(post, liker1);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals("liker1 liked your post", saved.getMessage());
        assertEquals(NotificationType.POST_LIKE, saved.getNotificationType());
        assertEquals(liker1, saved.getTriggeredBy());
    }

    @Test
    void testNotifyPostLiked_AggregatesIfNotificationExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(postOwner));
        when(userRepository.findById(3L)).thenReturn(Optional.of(liker2));

        Notification existingNotification = new Notification();
        existingNotification.setId(100L);
        existingNotification.setNotificationType(NotificationType.POST_LIKE);
        existingNotification.setMessage("liker1 liked your post");
        existingNotification.setTriggeredBy(liker1);
        existingNotification.setCreatedAt(new Date(System.currentTimeMillis() - 10000));
        existingNotification.setIsRead(true);

        when(notificationRepository.findByUserAndReferenceIdAndReferenceType(postOwner, post.getId(), "POST"))
                .thenReturn(Collections.singletonList(existingNotification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        post.setLikeCount(2); // liker1 and liker2
        notificationService.notifyPostLiked(post, liker2);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(100L, saved.getId(), "Should update the existing notification");
        assertEquals("liker2 and 1 others liked your post", saved.getMessage());
        assertEquals(liker2, saved.getTriggeredBy());
        assertFalse(saved.getIsRead(), "Should be marked as unread");
    }

    @Test
    void testNotifySocialPostLiked_WithCommunity_UpdatesActionUrl() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(postOwner));
        when(userRepository.findById(2L)).thenReturn(Optional.of(liker1));
        when(notificationRepository.findByUserAndReferenceIdAndReferenceType(postOwner, socialPost.getId(), "SOCIAL_POST"))
                .thenReturn(Collections.emptyList());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        Community community = new Community();
        community.setId(5L);
        community.setSlug("test-community");
        socialPost.setCommunity(community);

        notificationService.notifySocialPostLiked(socialPost, liker1);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals("/communities/test-community?postId=20", saved.getActionUrl());
    }

    @Test
    void testNotifySocialPostCommented_AggregatesIfNotificationExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(postOwner));
        when(userRepository.findById(3L)).thenReturn(Optional.of(liker2));

        Notification existingNotification = new Notification();
        existingNotification.setId(200L);
        existingNotification.setNotificationType(NotificationType.POST_COMMENT);
        existingNotification.setMessage("liker1 commented: \"Hello\"");
        existingNotification.setTriggeredBy(liker1);
        existingNotification.setCreatedAt(new Date(System.currentTimeMillis() - 10000));
        
        Community community = new Community();
        community.setId(5L);
        community.setSlug("test-community");
        socialPost.setCommunity(community);

        when(notificationRepository.findByUserAndReferenceIdAndReferenceType(postOwner, socialPost.getId(), "SOCIAL_POST"))
                .thenReturn(Collections.singletonList(existingNotification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        socialPost.setCommentCount(5);
        
        Comment comment = new Comment();
        comment.setId(50L);
        comment.setText("Great post");

        notificationService.notifySocialPostCommented(socialPost, comment, liker2);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(200L, saved.getId());
        assertEquals("liker2 and 4 others commented on your post", saved.getMessage());
        assertEquals("/communities/test-community?postId=20#comment-50", saved.getActionUrl());
    }
}
