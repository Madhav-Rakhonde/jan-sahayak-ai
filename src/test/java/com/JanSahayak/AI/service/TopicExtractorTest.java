package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.SocialPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class TopicExtractorTest {

    private DynamicNLPProcessor dynamicNLPProcessor;
    private TopicAggregationWorker topicAggregationWorker;
    private TopicExtractor topicExtractor;

    @BeforeEach
    void setUp() {
        dynamicNLPProcessor = Mockito.mock(DynamicNLPProcessor.class);
        topicAggregationWorker = Mockito.mock(TopicAggregationWorker.class);
        topicExtractor = new TopicExtractor(dynamicNLPProcessor, topicAggregationWorker);
    }

    @Test
    void testExtract_OnlyReturnsEstablishedTopics() {
        SocialPost post = new SocialPost();
        Map<String, Double> mockCandidates = new HashMap<>();
        mockCandidates.put("pothole", 0.6);
        mockCandidates.put("typo_word", 0.6);
        
        when(dynamicNLPProcessor.parseCandidates(any(SocialPost.class))).thenReturn(mockCandidates);
        
        // Only pothole is established
        when(topicAggregationWorker.isEstablishedTopic("pothole")).thenReturn(true);
        when(topicAggregationWorker.isEstablishedTopic("typo_word")).thenReturn(false);

        Map<String, Double> topics = topicExtractor.extract(post);
        
        assertTrue(topics.containsKey("pothole"));
        assertFalse(topics.containsKey("typo_word"));
        assertEquals(0.6, topics.get("pothole"));
    }

    @Test
    void testExtractFromHashtagsOnly() {
        SocialPost post = new SocialPost();
        post.setHashtagsList(Arrays.asList("#education", "#randomTag"));
        
        when(topicAggregationWorker.isEstablishedTopic("education")).thenReturn(true);
        when(topicAggregationWorker.isEstablishedTopic("randomtag")).thenReturn(false);

        Map<String, Double> topics = topicExtractor.extractFromHashtagsOnly(post);
        
        assertTrue(topics.containsKey("education"));
        assertEquals(1.00, topics.get("education"));
        assertFalse(topics.containsKey("randomtag"));
    }
}
