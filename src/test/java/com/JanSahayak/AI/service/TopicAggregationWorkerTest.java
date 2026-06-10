package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.TopicNode;
import com.JanSahayak.AI.repository.TopicNodeRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TopicAggregationWorkerTest {

    private TopicNodeRepo topicNodeRepo;
    private DynamicNLPProcessor dynamicNLPProcessor;
    private TopicAggregationWorker worker;

    @BeforeEach
    void setUp() {
        topicNodeRepo = Mockito.mock(TopicNodeRepo.class);
        dynamicNLPProcessor = Mockito.mock(DynamicNLPProcessor.class);
        worker = new TopicAggregationWorker(topicNodeRepo, dynamicNLPProcessor);
        worker.init(); // Initialize cache and memory set
    }

    @Test
    void testAccumulateCandidates_UnderThreshold_NotPromoted() {
        Map<String, Double> candidates = new HashMap<>();
        candidates.put("pothole", 0.6);

        // Call 1st time
        worker.accumulateCandidates(candidates);
        verify(topicNodeRepo, never()).save(any(TopicNode.class));
        assertFalse(worker.isEstablishedTopic("pothole"));

        // Call 2nd time
        worker.accumulateCandidates(candidates);
        verify(topicNodeRepo, never()).save(any(TopicNode.class));
    }

    @Test
    void testAccumulateCandidates_HitsThreshold_Promoted() {
        Map<String, Double> candidates = new HashMap<>();
        candidates.put("water", 0.6);

        when(topicNodeRepo.findByName("water")).thenReturn(Optional.empty());

        // Call 3 times (Threshold = 3)
        worker.accumulateCandidates(candidates);
        worker.accumulateCandidates(candidates);
        worker.accumulateCandidates(candidates);

        ArgumentCaptor<TopicNode> captor = ArgumentCaptor.forClass(TopicNode.class);
        verify(topicNodeRepo, times(1)).save(captor.capture());
        
        TopicNode savedNode = captor.getValue();
        assertEquals("water", savedNode.getName());
        assertFalse(savedNode.isHashtag());
        assertTrue(worker.isEstablishedTopic("water"));
    }

    @Test
    void testAccumulateCandidates_HashtagBypassesThreshold() {
        Map<String, Double> candidates = new HashMap<>();
        candidates.put("roadsafety", 1.0); // Hashtag strength

        when(topicNodeRepo.findByName("roadsafety")).thenReturn(Optional.empty());

        // Call 1 time
        worker.accumulateCandidates(candidates);

        verify(topicNodeRepo, times(1)).save(any(TopicNode.class));
        assertTrue(worker.isEstablishedTopic("roadsafety"));
    }

    @Test
    void testAccumulateCandidates_TypoIsMergedToEstablishedTopic() {
        // "cricket" is already an established topic
        TopicNode established = new TopicNode();
        established.setName("cricket");
        established.setHashtag(false);
        established.setVelocityScore(5);
        when(topicNodeRepo.findAll()).thenReturn(Collections.singletonList(established));
        
        // Re-init so the mock data loads into the fast-lookup set
        worker.init();

        // New candidate is a typo "criket"
        Map<String, Double> candidates = new HashMap<>();
        candidates.put("criket", 0.6); // missing 'c'

        worker.accumulateCandidates(candidates);

        // It should NOT save "criket" as a new topic.
        verify(topicNodeRepo, never()).save(any(TopicNode.class));
        
        // It SHOULD increment the velocity of the established "cricket"
        verify(topicNodeRepo, times(1)).incrementVelocityAndLastSeen(eq("cricket"), any());
    }
}
