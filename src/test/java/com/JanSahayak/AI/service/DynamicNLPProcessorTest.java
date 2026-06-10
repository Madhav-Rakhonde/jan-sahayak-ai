package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.SocialPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DynamicNLPProcessorTest {

    private DynamicNLPProcessor dynamicNLPProcessor;

    @BeforeEach
    void setUp() {
        dynamicNLPProcessor = new DynamicNLPProcessor();
    }

    @Test
    void testParseCandidates_RemovesNoiseAndStopWords() {
        SocialPost post = new SocialPost();
        post.setContent("This is a very huge pothole near the road. please fix it.");
        
        Map<String, Double> candidates = dynamicNLPProcessor.parseCandidates(post);
        
        assertTrue(candidates.containsKey("huge"));
        assertTrue(candidates.containsKey("pothole"));
        assertTrue(candidates.containsKey("road"));
        assertFalse(candidates.containsKey("please"));
        assertFalse(candidates.containsKey("near"));
    }

    @Test
    void testParseCandidates_SupportsIndianRegionalLanguages() {
        SocialPost post = new SocialPost();
        // Hindi: "जल की समस्या बहुत है"
        // 'जल' is length 2, but it's non-ASCII so it should be kept!
        // 'की' and 'है' are stop words and should be dropped.
        post.setContent("जल की समस्या बहुत है");
        
        Map<String, Double> candidates = dynamicNLPProcessor.parseCandidates(post);
        
        assertTrue(candidates.containsKey("जल"), "Short non-ascii word like जल should be kept");
        assertTrue(candidates.containsKey("समस्या"));
        assertTrue(candidates.containsKey("बहुत"));
        assertFalse(candidates.containsKey("की"), "Native stop word should be removed");
        assertFalse(candidates.containsKey("है"), "Native stop word should be removed");
    }

    @Test
    void testParseCandidates_MarathiAndTelugu() {
        SocialPost post = new SocialPost();
        // Marathi: पाणी येत नाहीये
        post.setContent("पाणी येत नाहीये पण काम आहे");
        Map<String, Double> marathiCandidates = dynamicNLPProcessor.parseCandidates(post);
        assertTrue(marathiCandidates.containsKey("पाणी"));
        assertFalse(marathiCandidates.containsKey("आहे"), "Marathi stop word 'आहे' should be removed");
        assertFalse(marathiCandidates.containsKey("पण"), "Marathi stop word 'पण' should be removed");

        SocialPost teluguPost = new SocialPost();
        // Telugu: నీళ్ళు లేదా మరియు
        teluguPost.setContent("నీళ్ళు లేదా మరియు");
        Map<String, Double> teluguCandidates = dynamicNLPProcessor.parseCandidates(teluguPost);
        assertTrue(teluguCandidates.containsKey("నీళ్ళు"));
        assertFalse(teluguCandidates.containsKey("లేదా"), "Telugu stop word 'లేదా' should be removed");
        assertFalse(teluguCandidates.containsKey("మరియు"), "Telugu stop word 'మరియు' should be removed");
    }

    @Test
    void testParseCandidates_SupportsHinglish() {
        SocialPost post = new SocialPost();
        // Hinglish: "pani ki samasya bahut jyada hai aur jal bhi nahi aa raha"
        post.setContent("pani ki samasya bahut jyada hai aur jal bhi nahi aa raha");
        
        Map<String, Double> candidates = dynamicNLPProcessor.parseCandidates(post);
        
        // Nouns: "pani", "samasya", "jyada", "jal" (jal is length 3, should be kept now!)
        assertTrue(candidates.containsKey("pani"));
        assertTrue(candidates.containsKey("samasya"));
        assertTrue(candidates.containsKey("jyada"));
        assertTrue(candidates.containsKey("jal"), "Length 3 nouns like jal should be kept");
        
        // Stop words: "ki", "bahut", "hai", "aur", "bhi", "nahi", "aa", "raha"
        // (Wait, 'aa' is length 2, so it's dropped by length filter. 'bahut' is length 5, so it's kept unless added to stop words. We didn't add 'bahut' to stop words, so it's kept).
        assertFalse(candidates.containsKey("ki"), "Stop word should be removed");
        assertFalse(candidates.containsKey("hai"), "Stop word should be removed");
        assertFalse(candidates.containsKey("aur"), "Stop word should be removed");
        assertFalse(candidates.containsKey("bhi"), "Stop word should be removed");
        assertFalse(candidates.containsKey("nahi"), "Stop word should be removed");
        assertFalse(candidates.containsKey("raha"), "Stop word should be removed");
        assertFalse(candidates.containsKey("aa"), "Length 2 words should be dropped");
    }
}
