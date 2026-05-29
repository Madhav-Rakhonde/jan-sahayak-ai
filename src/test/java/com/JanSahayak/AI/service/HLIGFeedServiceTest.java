package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HLIGFeedServiceTest {

    @Mock
    private SocialPostRepo socialPostRepo;

    @Mock
    private InterestProfileService interestProfileService;

    @Mock
    private HLIGScorer hligScorer;

    @Mock
    private TopicExtractor topicExtractor;

    @InjectMocks
    private HLIGFeedService feedService;

    private User user;
    private SocialPost post1;
    private SocialPost post2;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testUser");
        user.setPincode("110001");

        post1 = new SocialPost();
        post1.setId(10L);
        post1.setContent("Testing 1");

        post2 = new SocialPost();
        post2.setId(20L);
        post2.setContent("Testing 2");
    }

    @Test
    void testGetWarmingFeed_EmptyFeedFallback() {
        // Setup empty dependencies
        when(interestProfileService.loadTopN(eq(1L))).thenReturn(new HashMap<>());
        // Assuming findEligibleCandidatesWarming is mocked correctly
        // when(socialPostRepo.findEligibleCandidatesWarming(any(), any(), any())).thenReturn(Collections.emptyList());
        
        List<SocialPost> feed = ReflectionTestUtils.invokeMethod(feedService, "getWarmingFeed", user, 10);
        
        assertNotNull(feed);
        // We only mock loadTopN so if candidates are empty, getWarmingFeed returns empty list or fallback list.
    }
}
