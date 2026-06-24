package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.CommunityDto.CommunityPostResponse;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommunityServiceTest {

    @Mock private CommunityRepo communityRepo;
    @Mock private CommunityMemberRepo memberRepo;
    @Mock private SocialPostRepo socialPostRepo;
    @Mock private PostLikeRepo postLikeRepo;
    @Mock private SavedPostRepo savedPostRepo;

    @InjectMocks
    private CommunityService communityService;

    private Community testCommunity;
    private User testUser;
    private SocialPost testPost;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);

        testCommunity = new Community();
        testCommunity.setId(100L);
        testCommunity.setPrivacy(Community.CommunityPrivacy.PUBLIC);

        testPost = new SocialPost();
        testPost.setId(500L);
        testPost.setContent("Test content");
        testPost.setUser(testUser);
        testPost.setCommunity(testCommunity);
    }

    @Test
    void testGetCommunityPosts_SavedStatusMappedSuccessfully() {
        // Mock Community fetch
        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));

        // Mock Post fetch
        when(socialPostRepo.findCommunityPostsCursor(eq(100L), any(), any(Pageable.class)))
                .thenReturn(List.of(testPost));

        // Mock Like status - User hasn't liked this post
        when(postLikeRepo.findLikedSocialPostIdsByUser(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        // Mock Saved status - User has saved this post
        when(savedPostRepo.findSavedSocialPostIdsByUser(eq(1L), any()))
                .thenReturn(List.of(500L));

        var response = communityService.getCommunityPosts(100L, 1L, null, 10);

        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
        
        CommunityPostResponse dto = response.getData().get(0);
        assertEquals(500L, dto.getId());
        assertFalse(dto.isLikedByMe(), "isLikedByMe should be false since the post ID wasn't returned by postLikeRepo");
        assertTrue(dto.isSavedByMe(), "isSavedByMe should be true since we mocked the savedPostRepo to return the post ID");
    }

    @Test
    void testGetCommunityPosts_BothLikedAndSaved() {
        // Mock Community fetch
        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));

        // Mock Post fetch
        when(socialPostRepo.findCommunityPostsCursor(eq(100L), any(), any(Pageable.class)))
                .thenReturn(List.of(testPost));

        // Mock Like status - User HAS liked this post
        when(postLikeRepo.findLikedSocialPostIdsByUser(eq(1L), any(), any()))
                .thenReturn(List.of(500L));

        // Mock Saved status - User HAS saved this post
        when(savedPostRepo.findSavedSocialPostIdsByUser(eq(1L), any()))
                .thenReturn(List.of(500L));

        var response = communityService.getCommunityPosts(100L, 1L, null, 10);

        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
        
        CommunityPostResponse dto = response.getData().get(0);
        assertEquals(500L, dto.getId());
        assertTrue(dto.isLikedByMe(), "isLikedByMe should be true");
        assertTrue(dto.isSavedByMe(), "isSavedByMe should be true");
    }
}
