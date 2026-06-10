package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.UserTag;
import com.JanSahayak.AI.repository.PostRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PostServiceTest {

    @Mock
    private PostRepo postRepository;

    @Mock
    private TextSimilarityService textSimilarityService;

    @InjectMocks
    private PostService postService;

    @Test
    void testCheckDuplicatePosts_NoDuplicates() {
        String pincode = "411001";
        String content = "There is a huge pothole";

        Post oldPost = new Post();
        oldPost.setContent("Water leak on main street");

        when(postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByIdDesc(
                any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(oldPost));

        when(textSimilarityService.calculateSimilarity(content, oldPost.getContent())).thenReturn(0.2);

        Post duplicate = postService.checkDuplicatePosts(pincode, content, null);

        assertNull(duplicate, "Should return null when similarity is below threshold");
    }

    @Test
    void testCheckDuplicatePosts_DuplicateFound() {
        String pincode = "411001";
        String content = "There is a huge pothole";

        Post oldPost = new Post();
        oldPost.setContent("Huge pothole reported here");

        when(postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByIdDesc(
                any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(oldPost));

        when(textSimilarityService.calculateSimilarity(content, oldPost.getContent())).thenReturn(0.8);

        Post duplicate = postService.checkDuplicatePosts(pincode, content, null);

        assertNotNull(duplicate, "Should return the duplicate post when similarity is above threshold");
        assertEquals(oldPost, duplicate);
    }

    @Test
    void testCheckDuplicatePosts_WithTaggedDepartment_NoOverlap() {
        String pincode = "411001";
        String content = "There is a huge pothole";
        List<String> newPostTags = Arrays.asList("WaterDepartment");

        Post oldPost = new Post();
        oldPost.setContent("Huge pothole reported here");
        
        User oldTagUser = new User();
        oldTagUser.setUsername("ElectricityDepartment");
        UserTag oldUserTag = new UserTag();
        oldUserTag.setTaggedUser(oldTagUser);
        oldPost.setUserTags(Collections.singletonList(oldUserTag));

        when(postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByIdDesc(
                any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(oldPost));

        // Note: textSimilarityService should NOT be called because tags do not overlap
        Post duplicate = postService.checkDuplicatePosts(pincode, content, newPostTags);

        assertNull(duplicate, "Should return null because tagged departments do not overlap");
        verify(textSimilarityService, never()).calculateSimilarity(anyString(), anyString());
    }

    @Test
    void testCheckDuplicatePosts_WithTaggedDepartment_WithOverlap() {
        String pincode = "411001";
        String content = "There is a huge pothole";
        List<String> newPostTags = Arrays.asList("RoadDepartment");

        Post oldPost = new Post();
        oldPost.setContent("Huge pothole reported here");
        
        User oldTagUser = new User();
        oldTagUser.setUsername("RoadDepartment");
        UserTag oldUserTag = new UserTag();
        oldUserTag.setTaggedUser(oldTagUser);
        oldPost.setUserTags(Collections.singletonList(oldUserTag));

        when(postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByIdDesc(
                any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(oldPost));

        when(textSimilarityService.calculateSimilarity(content, oldPost.getContent())).thenReturn(0.7);

        Post duplicate = postService.checkDuplicatePosts(pincode, content, newPostTags);

        assertNotNull(duplicate, "Should return duplicate because tags overlap and similarity is high");
        assertEquals(oldPost, duplicate);
    }
}
