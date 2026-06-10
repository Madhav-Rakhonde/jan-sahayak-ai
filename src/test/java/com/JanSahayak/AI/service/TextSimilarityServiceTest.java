package com.JanSahayak.AI.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TextSimilarityServiceTest {

    private TextSimilarityService textSimilarityService;

    @BeforeEach
    void setUp() {
        textSimilarityService = new TextSimilarityService();
    }

    @Test
    void testExactMatch() {
        String text1 = "A huge pothole has formed outside the SBI bank";
        String text2 = "A huge pothole has formed outside the SBI bank";

        double similarity = textSimilarityService.calculateSimilarity(text1, text2);
        assertEquals(1.0, similarity, 0.01);
    }

    @Test
    void testHighSimilarity() {
        String text1 = "A huge pothole has formed outside the SBI bank";
        String text2 = "huge pothole near sbi bank please fix";

        double similarity = textSimilarityService.calculateSimilarity(text1, text2);
        assertTrue(similarity >= 0.40, "Similarity should be high for very similar texts");
    }

    @Test
    void testLowSimilarity() {
        String text1 = "A huge pothole has formed outside the SBI bank";
        String text2 = "The street lights are not working in mg road";

        double similarity = textSimilarityService.calculateSimilarity(text1, text2);
        assertTrue(similarity < 0.20, "Similarity should be low for completely different texts");
    }

    @Test
    void testStopWordsAndShortWordsFiltered() {
        // "is", "a", "the", "to", "it" are stop words or < 2 length
        String text1 = "it is a big problem";
        String text2 = "the big problem to fix";

        double similarity = textSimilarityService.calculateSimilarity(text1, text2);
        // Both reduce to [big, problem] or similar.
        assertTrue(similarity > 0.50, "Similarity should be high because stop words are filtered");
    }

    @Test
    void testEmptyOrNull() {
        assertEquals(0.0, textSimilarityService.calculateSimilarity(null, "test"));
        assertEquals(0.0, textSimilarityService.calculateSimilarity("test", ""));
        assertEquals(1.0, textSimilarityService.calculateSimilarity("", ""));
    }
}
