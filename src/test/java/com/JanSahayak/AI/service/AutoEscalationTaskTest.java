package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.repository.PostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoEscalationTaskTest {

    @Mock
    private PostRepo postRepo;

    @InjectMocks
    private AutoEscalationTask autoEscalationTask;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testEscalatePosts_EmptyList() {
        when(postRepo.findPostsEligibleForEscalation()).thenReturn(Collections.emptyList());

        autoEscalationTask.escalatePosts();

        verify(postRepo, never()).save(any());
    }

    @Test
    void testEscalatePosts_AreaToNearby() {
        Post post = new Post();
        post.setId(1L);
        post.setBroadcastScope(BroadcastScope.AREA);
        // Score = 2 * likes = 22 (> 20)
        for (int i = 0; i < 11; i++) post.incrementLikeCount();
        
        when(postRepo.findPostsEligibleForEscalation()).thenReturn(Arrays.asList(post));

        autoEscalationTask.escalatePosts();

        verify(postRepo, times(1)).save(post);
        assertEquals(BroadcastScope.NEARBY, post.getBroadcastScope());
        assertTrue(post.isAutoEscalated());
    }

    @Test
    void testEscalatePosts_NearbyToDistrict() {
        Post post = new Post();
        post.setId(2L);
        post.setBroadcastScope(BroadcastScope.NEARBY);
        // Score = 3 * comments = 51 (> 50)
        for (int i = 0; i < 17; i++) post.incrementCommentCount();
        
        when(postRepo.findPostsEligibleForEscalation()).thenReturn(Arrays.asList(post));

        autoEscalationTask.escalatePosts();

        verify(postRepo, times(1)).save(post);
        assertEquals(BroadcastScope.DISTRICT, post.getBroadcastScope());
        assertTrue(post.isAutoEscalated());
    }
    
    @Test
    void testEscalatePosts_DistrictToState() {
        Post post = new Post();
        post.setId(3L);
        post.setBroadcastScope(BroadcastScope.DISTRICT);
        // Score = 4 * shares = 204 (> 200)
        for (int i = 0; i < 51; i++) post.incrementShareCount();
        
        when(postRepo.findPostsEligibleForEscalation()).thenReturn(Arrays.asList(post));

        autoEscalationTask.escalatePosts();

        verify(postRepo, times(1)).save(post);
        assertEquals(BroadcastScope.STATE, post.getBroadcastScope());
        assertTrue(post.isAutoEscalated());
    }
    
    @Test
    void testEscalatePosts_StateToCountry() {
        Post post = new Post();
        post.setId(4L);
        post.setBroadcastScope(BroadcastScope.STATE);
        // Score = 2 * likes = 1002 (> 1000)
        for (int i = 0; i < 501; i++) post.incrementLikeCount();
        
        when(postRepo.findPostsEligibleForEscalation()).thenReturn(Arrays.asList(post));

        autoEscalationTask.escalatePosts();

        verify(postRepo, times(1)).save(post);
        assertEquals(BroadcastScope.COUNTRY, post.getBroadcastScope());
        assertTrue(post.isAutoEscalated());
    }
}
