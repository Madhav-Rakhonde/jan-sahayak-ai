package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.repository.CommunityRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityHealthScoreServiceTest {

    @Mock
    private CommunityRepo communityRepo;

    @InjectMocks
    private CommunityHealthScoreService healthScoreService;

    private Community community;

    @BeforeEach
    void setUp() {
        community = new Community();
        community.setId(1L);
        community.setName("Test Community");
        
        // Ideal stats for 100 score
        community.setPostsLast7d(Constant.COMMUNITY_HEALTH_IDEAL_POSTS_PER_WEEK); // post freq = 100
        community.setPostCount(100);
        community.setTotalCommentCount((int) (100 * Constant.COMMUNITY_HEALTH_IDEAL_ENGAGEMENT_RATIO)); // engagement = 100
        community.setTotalLikeCount(0);
        community.setNewMembersLast7d(Constant.COMMUNITY_HEALTH_IDEAL_NEW_MEMBERS); // growth = 100
        community.setLastActiveAt(new java.util.Date()); // mod activity = 100
        community.setMemberCount(1000);
        community.setActivePostersLast7d((int) (1000 * Constant.COMMUNITY_HEALTH_IDEAL_RETENTION_RATIO)); // retention = 100
    }

    @Test
    void testRecalculate_PerfectScore() {
        when(communityRepo.findById(1L)).thenReturn(Optional.of(community));
        
        healthScoreService.recalculateNow(1L);
        
        assertEquals(100.0, community.getHealthScore());
        assertEquals(100.0, community.getHealthPostFreqScore());
        assertEquals(100.0, community.getHealthEngagementScore());
        assertEquals(100.0, community.getHealthMemberGrowthScore());
        assertEquals(100.0, community.getHealthModActivityScore());
        assertEquals(100.0, community.getHealthRetentionScore());
    }

    @Test
    void testRecalculate_LowScore() {
        community.setPostsLast7d(0);
        community.setTotalCommentCount(0);
        community.setNewMembersLast7d(0);
        community.setActivePostersLast7d(0);
        // Dormant
        community.setLastActiveAt(new java.util.Date(System.currentTimeMillis() - (Constant.COMMUNITY_HEALTH_DORMANT_DAYS * 24L * 60L * 60L * 1000L)));

        when(communityRepo.findById(1L)).thenReturn(Optional.of(community));
        
        healthScoreService.recalculateNow(1L);
        
        assertEquals(0.0, community.getHealthScore());
    }

    @Test
    void testRecalculate_PartialScore() {
        // Half ideal posts
        community.setPostsLast7d(Constant.COMMUNITY_HEALTH_IDEAL_POSTS_PER_WEEK / 2);
        
        when(communityRepo.findById(1L)).thenReturn(Optional.of(community));
        
        healthScoreService.recalculateNow(1L);
        
        assertEquals(50.0, community.getHealthPostFreqScore());
        
        // Formula: 50 * 0.30 + 100 * 0.70 = 15 + 70 = 85
        assertEquals(85.0, community.getHealthScore());
    }
}
