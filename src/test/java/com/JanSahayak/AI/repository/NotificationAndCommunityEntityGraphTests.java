package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.enums.NotificationType;
import com.JanSahayak.AI.model.*;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationAndCommunityEntityGraphTests {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private NotificationRepo notificationRepo;

    @Autowired
    private CommentRepo commentRepo;

    @Autowired
    private CommunityRepo communityRepo;

    @Autowired
    private CommunityMemberRepo communityMemberRepo;

    @Autowired
    private CommunityJoinRequestRepo communityJoinRequestRepo;

    @Autowired
    private CommunityInviteRepo communityInviteRepo;

    @Autowired
    private SocialPostRepo socialPostRepo;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User testUser1;
    private User testUser2;
    private Community testCommunity;
    private Notification testNotification;
    private Comment testComment;
    private CommunityMember testMember;
    private CommunityJoinRequest testJoinRequest;
    private CommunityInvite testInvite;
    private SocialPost testSocialPost;

    @BeforeEach
    void setUp() {
        transactionTemplate.execute(status -> {
            // Cleanup in dependency order
            communityInviteRepo.deleteAll();
            communityJoinRequestRepo.deleteAll();
            communityMemberRepo.deleteAll();
            commentRepo.deleteAll();
            socialPostRepo.deleteAll();
            notificationRepo.deleteAll();
            communityRepo.deleteAll();

            Role userRole = roleRepo.findByName("ROLE_USER")
                    .orElseGet(() -> {
                        Role r = new Role();
                        r.setName("ROLE_USER");
                        return roleRepo.save(r);
                    });

            testUser1 = new User();
            testUser1.setUsername("user1_" + UUID.randomUUID().toString().substring(0, 8));
            testUser1.setEmail("user1_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
            testUser1.setPassword("password123");
            testUser1.setRole(userRole);
            testUser1.setPincode("411001");
            testUser1.setIsEmailVerified(true);
            testUser1 = userRepo.save(testUser1);

            testUser2 = new User();
            testUser2.setUsername("user2_" + UUID.randomUUID().toString().substring(0, 8));
            testUser2.setEmail("user2_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
            testUser2.setPassword("password123");
            testUser2.setRole(userRole);
            testUser2.setPincode("411001");
            testUser2.setIsEmailVerified(true);
            testUser2 = userRepo.save(testUser2);

            // Create Community
            testCommunity = Community.builder()
                    .name("Test Community " + UUID.randomUUID().toString().substring(0, 8))
                    .slug("test-community-" + UUID.randomUUID().toString().substring(0, 8))
                    .description("Test description")
                    .owner(testUser1)
                    .privacy(Community.CommunityPrivacy.PUBLIC)
                    .status(Community.CommunityStatus.ACTIVE)
                    .isSystemSeeded(true)
                    .pincode("411001")
                    .build();
            testCommunity = communityRepo.save(testCommunity);

            // Create Notification triggered by user2 for user1
            testNotification = Notification.builder()
                    .user(testUser1)
                    .triggeredBy(testUser2)
                    .notificationType(NotificationType.SYSTEM_ANNOUNCEMENT)
                    .title("Test Title")
                    .message("Test Message")
                    .isRead(false)
                    .createdAt(new Date())
                    .build();
            testNotification = notificationRepo.save(testNotification);

            // Create a SocialPost
            testSocialPost = SocialPost.builder()
                    .user(testUser1)
                    .content("Test Social Post")
                    .pincode("411001")
                    .isViral(false)
                    .reportCount(0)
                    .isFlagged(false)
                    .allowComments(true)
                    .build();
            testSocialPost = socialPostRepo.save(testSocialPost);

            // Create Comment by user1 associated with the social post
            testComment = new Comment();
            testComment.setUser(testUser1);
            testComment.setSocialPost(testSocialPost);
            testComment.setText("Test Comment");
            testComment.setCreatedAt(new Date());
            testComment = commentRepo.save(testComment);

            // Create Community Member
            testMember = CommunityMember.builder()
                    .community(testCommunity)
                    .user(testUser2)
                    .memberRole(CommunityMember.MemberRole.MEMBER)
                    .isActive(true)
                    .isBanned(false)
                    .joinedAt(new Date())
                    .build();
            testMember = communityMemberRepo.save(testMember);

            // Create Community Join Request
            testJoinRequest = CommunityJoinRequest.builder()
                    .community(testCommunity)
                    .user(testUser2)
                    .status(CommunityJoinRequest.RequestStatus.PENDING)
                    .requestedAt(new Date())
                    .build();
            testJoinRequest = communityJoinRequestRepo.save(testJoinRequest);

            // Create Community Invite
            testInvite = CommunityInvite.builder()
                    .community(testCommunity)
                    .inviter(testUser1)
                    .invitee(testUser2)
                    .status(CommunityInvite.InviteStatus.PENDING)
                    .singleUse(true)
                    .useCount(0)
                    .createdAt(new Date())
                    .expiresAt(new Date(System.currentTimeMillis() + 3600000))
                    .build();
            testInvite = communityInviteRepo.save(testInvite);

            return null;
        });
    }

    @Test
    @DisplayName("Verify NotificationRepo queries eagerly fetch triggeredBy association using EntityGraph")
    void testNotificationRepoEntityGraph() {
        transactionTemplate.execute(status -> {
            List<Notification> notifications = notificationRepo.findByUserOrderByCreatedAtDesc(testUser1, PageRequest.of(0, 10));
            assertThat(notifications).isNotEmpty();
            assertThat(Hibernate.isInitialized(notifications.get(0).getTriggeredBy())).isTrue();
            assertThat(notifications.get(0).getTriggeredBy().getUsername()).isEqualTo(testUser2.getUsername());

            List<Notification> unreadList = notificationRepo.findByUserAndIsReadFalseOrderByCreatedAtDesc(testUser1);
            assertThat(unreadList).isNotEmpty();
            assertThat(Hibernate.isInitialized(unreadList.get(0).getTriggeredBy())).isTrue();

            return null;
        });
    }

    @Test
    @DisplayName("Verify CommentRepo queries eagerly fetch user association using EntityGraph")
    void testCommentRepoEntityGraph() {
        transactionTemplate.execute(status -> {
            List<Comment> comments = commentRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10));
            assertThat(comments).isNotEmpty();
            assertThat(Hibernate.isInitialized(comments.get(0).getUser())).isTrue();
            assertThat(comments.get(0).getUser().getUsername()).isEqualTo(testUser1.getUsername());

            return null;
        });
    }

    @Test
    @DisplayName("Verify CommunityRepo queries eagerly fetch owner association using EntityGraph")
    void testCommunityRepoEntityGraph() {
        transactionTemplate.execute(status -> {
            Community communityBySlug = communityRepo.findBySlug(testCommunity.getSlug()).orElse(null);
            assertThat(communityBySlug).isNotNull();
            assertThat(Hibernate.isInitialized(communityBySlug.getOwner())).isTrue();
            assertThat(communityBySlug.getOwner().getUsername()).isEqualTo(testUser1.getUsername());

            Community communityByName = communityRepo.findByName(testCommunity.getName()).orElse(null);
            assertThat(communityByName).isNotNull();
            assertThat(Hibernate.isInitialized(communityByName.getOwner())).isTrue();

            Community communitySeeded = communityRepo.findByPincodeAndIsSystemSeededTrue(testCommunity.getPincode()).orElse(null);
            assertThat(communitySeeded).isNotNull();
            assertThat(Hibernate.isInitialized(communitySeeded.getOwner())).isTrue();

            return null;
        });
    }

    @Test
    @DisplayName("Verify CommunityMemberRepo queries eagerly fetch user and community associations using EntityGraph")
    void testCommunityMemberRepoEntityGraph() {
        transactionTemplate.execute(status -> {
            List<CommunityMember> members = communityMemberRepo.findActiveMembersCursor(testCommunity.getId(), null, PageRequest.of(0, 10));
            assertThat(members).isNotEmpty();
            assertThat(Hibernate.isInitialized(members.get(0).getUser())).isTrue();
            assertThat(members.get(0).getUser().getUsername()).isEqualTo(testUser2.getUsername());

            List<CommunityMember> userCommunities = communityMemberRepo.findUserCommunitiesCursor(testUser2.getId(), null, PageRequest.of(0, 10));
            assertThat(userCommunities).isNotEmpty();
            assertThat(Hibernate.isInitialized(userCommunities.get(0).getCommunity())).isTrue();
            assertThat(userCommunities.get(0).getCommunity().getName()).isEqualTo(testCommunity.getName());

            return null;
        });
    }

    @Test
    @DisplayName("Verify CommunityJoinRequestRepo queries eagerly fetch user association using EntityGraph")
    void testCommunityJoinRequestRepoEntityGraph() {
        transactionTemplate.execute(status -> {
            List<CommunityJoinRequest> requests = communityJoinRequestRepo.findPendingRequestsCursor(testCommunity.getId(), null, PageRequest.of(0, 10));
            assertThat(requests).isNotEmpty();
            assertThat(Hibernate.isInitialized(requests.get(0).getUser())).isTrue();
            assertThat(requests.get(0).getUser().getUsername()).isEqualTo(testUser2.getUsername());

            return null;
        });
    }

    @Test
    @DisplayName("Verify CommunityInviteRepo queries eagerly fetch invitee, inviter, and community associations using EntityGraph")
    void testCommunityInviteRepoEntityGraph() {
        transactionTemplate.execute(status -> {
            CommunityInvite inviteByToken = communityInviteRepo.findByToken(testInvite.getToken()).orElse(null);
            assertThat(inviteByToken).isNotNull();
            assertThat(Hibernate.isInitialized(inviteByToken.getInvitee())).isTrue();
            assertThat(Hibernate.isInitialized(inviteByToken.getInviter())).isTrue();
            assertThat(Hibernate.isInitialized(inviteByToken.getCommunity())).isTrue();
            assertThat(inviteByToken.getInvitee().getUsername()).isEqualTo(testUser2.getUsername());
            assertThat(inviteByToken.getInviter().getUsername()).isEqualTo(testUser1.getUsername());
            assertThat(inviteByToken.getCommunity().getName()).isEqualTo(testCommunity.getName());

            List<CommunityInvite> pendingInvites = communityInviteRepo.findPendingByCommunityIdCursor(testCommunity.getId(), null, PageRequest.of(0, 10));
            assertThat(pendingInvites).isNotEmpty();
            assertThat(Hibernate.isInitialized(pendingInvites.get(0).getInvitee())).isTrue();
            assertThat(Hibernate.isInitialized(pendingInvites.get(0).getInviter())).isTrue();
            assertThat(Hibernate.isInitialized(pendingInvites.get(0).getCommunity())).isTrue();

            return null;
        });
    }
}
