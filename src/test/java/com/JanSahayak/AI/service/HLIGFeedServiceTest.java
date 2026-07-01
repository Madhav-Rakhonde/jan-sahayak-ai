package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.FeedScope;
import com.JanSahayak.AI.enums.FeedSort;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HLIGFeedServiceTest {

    @Mock
    private SocialPostRepo postRepo;

    @Mock
    private InterestProfileService interestService;

    @Mock
    private HLIGScorer scorer;

    @Mock
    private TopicExtractor topicExtractor;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private HLIGFeedService hligFeedService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
    }

    @Test
    void testGetBrowseFeed_Following_Pagination_NoAbsoluteFallback() {
        // Arrange
        // Simulate fetchBrowsePool returning empty for FOLLOWING
        when(postRepo.findPostsFromUserCommunities(eq(user.getId()), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        
        // Cold fallback in fetchBrowsePool
        when(postRepo.findAllActivePostsForFeed(any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        
        // Act
        // Pass lastPostId = 100L (pagination request)
        List<SocialPost> result = hligFeedService.getBrowseFeed(user, FeedScope.FOLLOWING, FeedSort.HOT, 100L, 20);

        // Assert
        assertTrue(result.isEmpty());
        // Verify absolute fallback was NOT triggered. absoluteFallback calls findHotActivePostsForFeed.
        verify(postRepo, never()).findHotActivePostsForFeed(any(), any(Pageable.class));
    }
}
