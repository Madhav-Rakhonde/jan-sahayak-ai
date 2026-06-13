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
import static org.mockito.Mockito.*;

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

    @Mock
    private org.springframework.cache.CacheManager cacheManager;

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

    @Test
    void testGetBrowseFeed_WarmPhase_BatchesNeighbourLikes() {
        User testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testUser");
        testUser.setPincode("110001");

        List<Long> neighbours = Arrays.asList(2L, 3L);
        when(interestProfileService.getUserPhase(testUser.getId())).thenReturn(InterestProfileService.Phase.WARM);
        when(interestProfileService.loadTopN(testUser.getId())).thenReturn(new HashMap<>());
        when(interestProfileService.findNeighbours(testUser.getId())).thenReturn(neighbours);
        when(interestProfileService.recentlySeenPosts(testUser.getId())).thenReturn(new HashSet<>());

        User creator = new User();
        creator.setId(99L);
        creator.setIsActive(true);

        SocialPost candidate1 = new SocialPost();
        candidate1.setId(101L);
        candidate1.setUser(creator);
        candidate1.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);
        candidate1.setQualityScore(80.0);
        candidate1.setReportCount(0);

        SocialPost candidate2 = new SocialPost();
        candidate2.setId(102L);
        candidate2.setUser(creator);
        candidate2.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);
        candidate2.setQualityScore(80.0);
        candidate2.setReportCount(0);
        List<SocialPost> candidates = Arrays.asList(candidate1, candidate2);

        // Stub fetchCandidates indirect calls
        when(socialPostRepo.findLocalFeedPosts(anyString(), any())).thenReturn(candidates);

        // Stub batched neighbour likes lookup
        List<Object[]> batchRows = new ArrayList<>();
        batchRows.add(new Object[]{101L, 5L});
        batchRows.add(new Object[]{102L, 2L});
        when(socialPostRepo.countNeighbourLikesForPosts(anyList(), eq(neighbours))).thenReturn(batchRows);

        // Stub scoring
        when(hligScorer.scoreWarmWithPtf(eq(testUser), eq(candidate1), anyMap(), eq(5), anyMap(), anyLong(), anyMap(), anyList()))
                .thenReturn(10.0);
        when(hligScorer.scoreWarmWithPtf(eq(testUser), eq(candidate2), anyMap(), eq(2), anyMap(), anyLong(), anyMap(), anyList()))
                .thenReturn(5.0);

        List<SocialPost> result = feedService.getBrowseFeed(
                testUser, com.JanSahayak.AI.enums.FeedScope.FOR_YOU, com.JanSahayak.AI.enums.FeedSort.NEW, null, 10);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(101L, result.get(0).getId());
        assertEquals(102L, result.get(1).getId());

        // Verify batch query was called and not individual count queries
        verify(socialPostRepo, times(1)).countNeighbourLikesForPosts(anyList(), eq(neighbours));
    }

    @Test
    void testGetBrowseFeed_ParallelCandidateFetching_Success() {
        User testUser = new User();
        testUser.setId(2L);
        testUser.setUsername("parallelUser");
        testUser.setPincode("110001");

        SocialPost localPost = new SocialPost();
        localPost.setId(201L);
        localPost.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);

        SocialPost districtPost = new SocialPost();
        districtPost.setId(202L);
        districtPost.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);

        SocialPost statePost = new SocialPost();
        statePost.setId(203L);
        statePost.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);

        SocialPost nationalPost = new SocialPost();
        nationalPost.setId(204L);
        nationalPost.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);

        // Under user.pincode = "110001", getDistrictPrefix() = "110", getStatePrefix() = "11"
        when(socialPostRepo.findLocalFeedPosts(eq("110001"), any())).thenReturn(List.of(localPost));
        when(socialPostRepo.findDistrictViralPosts(eq("110"), any())).thenReturn(List.of(districtPost));
        when(socialPostRepo.findStateViralPosts(eq("11"), any())).thenReturn(List.of(statePost));
        when(socialPostRepo.findNationalViralPosts(any())).thenReturn(List.of(nationalPost));
        when(socialPostRepo.findRecentPostsByUser(anyLong(), any())).thenReturn(Collections.emptyList());
        when(hligScorer.scoreCold(any(), any())).thenReturn(1.0);

        // Fetch candidates through Cold Feed implementation
        List<SocialPost> feed = ReflectionTestUtils.invokeMethod(feedService, "getColdFeed", testUser, 10);

        assertNotNull(feed);
        assertFalse(feed.isEmpty());
        // Verify all parallel queries were invoked
        verify(socialPostRepo, times(1)).findLocalFeedPosts(eq("110001"), any());
        verify(socialPostRepo, times(1)).findDistrictViralPosts(eq("110"), any());
        verify(socialPostRepo, times(1)).findStateViralPosts(eq("11"), any());
        verify(socialPostRepo, times(1)).findNationalViralPosts(any());
    }
}
