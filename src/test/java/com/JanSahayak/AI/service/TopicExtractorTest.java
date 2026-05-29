package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.SocialPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TopicExtractorTest {

    private TopicExtractor topicExtractor;

    @BeforeEach
    void setUp() {
        topicExtractor = new TopicExtractor();
    }

    @Test
    void testResolveToken_ExactMatch() {
        assertEquals("roads", topicExtractor.resolveToken("roads"));
        assertEquals("water_supply", topicExtractor.resolveToken("water"));
    }

    @Test
    void testResolveToken_PrefixMatch() {
        // "water_supply_problem" should map to "water_supply" via prefix "water"
        assertEquals("water_supply", topicExtractor.resolveToken("water_supply_problem"));
    }

    @Test
    void testResolveToken_NativeScript() {
        // Hindi: सड़क (roads)
        SocialPost post = new SocialPost();
        post.setContent("\u0938\u095C\u0915 बहुत खराब है");
        
        Map<String, Double> topics = topicExtractor.extract(post);
        assertTrue(topics.containsKey("roads"));
        assertEquals(0.60, topics.get("roads")); // Content strength
    }

    @Test
    void testExtract_Hashtags() {
        SocialPost post = new SocialPost();
        post.setHashtagsList(Arrays.asList("#roads", "#water"));
        
        Map<String, Double> topics = topicExtractor.extract(post);
        
        assertTrue(topics.containsKey("roads"));
        assertEquals(1.00, topics.get("roads")); // Hashtag strength
        assertTrue(topics.containsKey("water_supply"));
        assertEquals(1.00, topics.get("water_supply"));
    }

    @Test
    void testExtractFromHashtagsOnly() {
        SocialPost post = new SocialPost();
        post.setHashtagsList(Arrays.asList("#education"));
        
        Map<String, Double> topics = topicExtractor.extractFromHashtagsOnly(post);
        
        assertTrue(topics.containsKey("education"));
        assertEquals(1.00, topics.get("education"));
    }

    @Test
    void testLocationTokensAreIgnored() {
        assertNull(topicExtractor.resolveToken("mumbai"));
        assertNull(topicExtractor.resolveToken("delhi"));
    }
}
