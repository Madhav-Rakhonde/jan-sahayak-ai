package com.JanSahayak.AI.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class BadWordServiceTest {

    private BadWordService badWordService;

    @BeforeEach
    void setUp() {
        badWordService = new BadWordService();
        
        // Inject fake bad words using ReflectionTestUtils instead of reading from file
        Set<String> words = new HashSet<>();
        words.add("badword");
        words.add("uglyword");
        
        Set<Pattern> patterns = new HashSet<>();
        patterns.add(Pattern.compile("\\b" + Pattern.quote("badword") + "\\b", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("\\b" + Pattern.quote("uglyword") + "\\b", Pattern.CASE_INSENSITIVE));
        
        ReflectionTestUtils.setField(badWordService, "badWords", words);
        ReflectionTestUtils.setField(badWordService, "badWordPatterns", Collections.unmodifiableSet(patterns));
    }

    @Test
    void testCheckText_CleanText() {
        BadWordService.BadWordCheckResult result = badWordService.checkText("This is a clean text.");
        assertTrue(result.isAllowed());
        assertNull(result.getBadWord());
    }

    @Test
    void testCheckText_BadWordFound() {
        BadWordService.BadWordCheckResult result = badWordService.checkText("This contains a bAdWorD here.");
        assertFalse(result.isAllowed());
        assertEquals("badword", result.getBadWord());
        assertEquals("ERROR", result.getStatus());
    }

    @Test
    void testCheckText_WordBoundary() {
        // "badwords" is not the same as "badword" because of word boundary \b
        BadWordService.BadWordCheckResult result = badWordService.checkText("This is a badwords list.");
        assertTrue(result.isAllowed(), "Should allow words that contain badword as substring but aren't exact match due to word boundaries");
    }

    @Test
    void testContainsBadWord() {
        assertTrue(badWordService.containsBadWord("You uglyword!"));
        assertFalse(badWordService.containsBadWord("You are nice!"));
    }
}
