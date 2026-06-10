package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.UserInterestProfileRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HLIGScorerTest {

    @Mock
    private UserInterestProfileRepo userInterestProfileRepo;

    @InjectMocks
    private HLIGScorer hligScorer;

    private User user;
    private SocialPost post;
    private Map<String, Double> userProfile;
    private Map<String, Double> ptf;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setPincode("110001");
        user.setIsActive(true);

        post = new SocialPost();
        post.setId(100L);
        post.setPincode("110001");
        post.setUser(user);
        post.setCreatedAt(new java.util.Date());
        post.setLanguage("hi");
        post.setQualityScore(80.0);
        post.setStatus(com.JanSahayak.AI.enums.PostStatus.ACTIVE);

        userProfile = new HashMap<>();
        userProfile.put("politics", 0.8);
        userProfile.put("sports", 0.5);

        ptf = new HashMap<>();
        ptf.put("politics", 1.0);
    }

    @Test
    void testScoreWarmWithPtf_ExactMatch() {
        when(userInterestProfileRepo.countNeighbourLikes(eq(100L), any())).thenReturn(5);

        List<String> preferredLangs = Arrays.asList("hi", "en");
        
        // Populate profile with enough signals to trigger language preference
        for(int i=0; i<10; i++) userProfile.put("topic"+i, 0.1);

        double score = hligScorer.scoreWarmWithPtf(
                user, post, userProfile, Arrays.asList(2L, 3L),
                new HashMap<>(), 123L, ptf, preferredLangs);

        assertTrue(score > 0.0, "Score should be positive for an eligible post");
    }

    @Test
    void testLanguageBoost_ExactMatch() {
        List<String> prefLangs = Arrays.asList("hi", "en");
        
        // Need >= 10 topics to bypass cold start check
        Map<String, Double> profile = new HashMap<>();
        for(int i=0; i<10; i++) profile.put("t"+i, 0.1);
        
        double boost = ReflectionTestUtils.invokeMethod(hligScorer, "languageBoost", post, prefLangs, 10);
        assertEquals(2.0, boost); // LANG_BOOST_EXACT_MATCH
    }

    @Test
    void testLanguageBoost_Mismatch() {
        List<String> prefLangs = Arrays.asList("ta", "en"); // user prefers Tamil/English
        post.setLanguage("hi"); // post is Hindi
        
        double boost = ReflectionTestUtils.invokeMethod(hligScorer, "languageBoost", post, prefLangs, 10);
        assertEquals(0.5, boost); // LANG_BOOST_MISMATCH
    }

    @Test
    void testFreshnessDecay() {
        post.setCreatedAt(java.util.Date.from(Instant.now().minus(46, ChronoUnit.HOURS))); // 1 half-life
        
        double freshness = ReflectionTestUtils.invokeMethod(hligScorer, "freshness", post);
        // e^(-ln2/46 * 46) = e^(-ln2) = 0.5
        assertTrue(freshness > 0.45 && freshness < 0.55, "Freshness should be around 0.5 after 46 hours");
    }

    @Test
    void testGeoProximity_SamePincode() {
        double geo = ReflectionTestUtils.invokeMethod(hligScorer, "geoProximity", user, post);
        assertEquals(Constant.HLIG_GEO_SAME_PINCODE, geo);
    }

    // =========================================================================
    // JANSAHAYAK DISCOVERY ENGINE TESTS
    // =========================================================================

    @Test
    void testDiscoveryEngine_QualityScore_SparseNetwork() {
        // 0 likes, 0 views => 1 / 10 = 10%
        double score0 = HLIGScorer.calculateQualityScore(0, 0, 0);
        assertEquals(10.0, score0, 0.1);
        
        // 1 like, 10 views => 2 / 20 = 10%
        double score1 = HLIGScorer.calculateQualityScore(1, 10, 0);
        assertEquals(10.0, score1, 0.1);

        // 5 likes, 5 views => 6 / 15 = 40%
        double score2 = HLIGScorer.calculateQualityScore(5, 5, 0);
        assertEquals(40.0, score2, 0.1);
    }

    @Test
    void testDiscoveryEngine_QualityScore_MassiveNetwork() {
        // 1000 likes, 10000 views => 1001 / 10010 = ~10%
        double score1 = HLIGScorer.calculateQualityScore(1000, 10000, 0);
        assertEquals(10.0, score1, 0.1);
        
        // 9000 likes, 10000 views => 9001 / 10010 = ~89.9%
        double score2 = HLIGScorer.calculateQualityScore(9000, 10000, 0);
        assertEquals(89.9, score2, 0.1);
    }

    @Test
    void testDiscoveryEngine_MomentumScore_GravityDecay() {
        // High engagement post
        double scoreHour0 = HLIGScorer.calculateMomentumScore(100, 50, 10, 5, 0);
        double scoreHour24 = HLIGScorer.calculateMomentumScore(100, 50, 10, 5, 24);
        
        assertTrue(scoreHour0 > scoreHour24, "Score should decay over 24 hours");
        assertTrue(scoreHour24 > 100.0, "Highly viral post should survive gravity after 24h");
    }
}
