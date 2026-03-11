package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe bad word filtering service using in-memory HashSet.
 * Loads bad words once at startup and provides O(1) lookup performance.
 *
 * Design Decisions:
 * - HashSet for O(1) lookup (better than Trie for exact word matching)
 * - ReadWriteLock for thread-safety without blocking concurrent reads
 * - Word boundaries (\b) to avoid false positives
 * - Case-insensitive matching
 * - Only blocks raw/full bad words, allows masked variations
 */
@Service
@Slf4j
public class BadWordService {

    // Thread-safe Set to store bad words in lowercase
    private final Set<String> badWords = new HashSet<>();

    // ReadWriteLock allows multiple concurrent reads, exclusive writes
    // Better performance than synchronized for read-heavy operations
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // Cached patterns for efficient regex matching
    private volatile Set<Pattern> badWordPatterns = new HashSet<>();

    // Configuration

    /**
     * Loads bad words from file at application startup.
     * @PostConstruct ensures this runs once after dependency injection.
     * Thread-safe initialization guaranteed by Spring's singleton bean lifecycle.
     */
    @PostConstruct
    public void loadBadWords() {
        lock.writeLock().lock(); // Exclusive lock for initialization
        try {
            log.info("Loading bad words from file: {}", Constant.BAD_WORDS_FILE_PATH);

            ClassPathResource resource = new ClassPathResource(Constant.BAD_WORDS_FILE_PATH);

            if (!resource.exists()) {
                log.error("Bad words file not found: {}", Constant.BAD_WORDS_FILE_PATH);
                throw new IllegalStateException("Bad words file not found: " + Constant.BAD_WORDS_FILE_PATH);
            }

            // Use try-with-resources for automatic resource cleanup
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                int lineCount = 0;

                while ((line = reader.readLine()) != null) {
                    String trimmedLine = line.trim().toLowerCase();

                    // Skip empty lines and comments
                    if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#")) {
                        badWords.add(trimmedLine);
                        lineCount++;
                    }
                }

                // Pre-compile regex patterns for better performance
                buildBadWordPatterns();

                log.info("Successfully loaded {} bad words into memory", lineCount);
                log.info("Memory usage - Bad words set size: {} entries", badWords.size());

            } catch (Exception e) {
                log.error("Failed to read bad words file: {}", Constant.BAD_WORDS_FILE_PATH, e);
                throw new IllegalStateException("Failed to load bad words", e);
            }

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Pre-compiles regex patterns with word boundaries for each bad word.
     * Word boundaries (\b) ensure we match whole words only:
     * - "fuck" matches, but "assignment" with "ass" does not
     * - "class" is not blocked even though it contains "ass"
     */
    private void buildBadWordPatterns() {
        Set<Pattern> patterns = new HashSet<>(badWords.size());

        for (String badWord : badWords) {
            // (?i) = case insensitive, \b = word boundary
            // Pattern is thread-safe once compiled
            Pattern pattern = Pattern.compile(
                    "\\b" + Pattern.quote(badWord) + "\\b",
                    Pattern.CASE_INSENSITIVE
            );
            patterns.add(pattern);
        }

        // Volatile ensures visibility across threads
        this.badWordPatterns = patterns;

        log.debug("Compiled {} regex patterns for bad word matching", patterns.size());
    }

    /**
     * Checks if text contains any raw/full bad words.
     * Thread-safe for millions of concurrent reads.
     *
     * @param text Text to check for bad words
     * @return BadWordCheckResult containing status and found bad word (if any)
     */
    public BadWordCheckResult checkText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return BadWordCheckResult.allow();
        }

        lock.readLock().lock(); // Shared read lock - allows concurrent reads
        try {
            // Normalize text for consistent matching
            String normalizedText = text.trim();

            // Check against all pre-compiled patterns
            for (Pattern pattern : badWordPatterns) {
                Matcher matcher = pattern.matcher(normalizedText);

                if (matcher.find()) {
                    String foundWord = matcher.group().toLowerCase();
                    log.debug("Bad word detected in text: '{}'", foundWord);
                    return BadWordCheckResult.block(foundWord);
                }
            }

            return BadWordCheckResult.allow();

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Fast path check using HashSet O(1) lookup for simple cases.
     * Falls back to regex for more complex matching.
     *
     * @param text Text to check
     * @return true if bad word found, false otherwise
     */
    public boolean containsBadWord(String text) {
        return !checkText(text).isAllowed();
    }

    /**
     * Returns count of loaded bad words (for monitoring/health checks).
     * Thread-safe read operation.
     */
    public int getBadWordCount() {
        lock.readLock().lock();
        try {
            return badWords.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Reloads bad words from file (useful for hot-reload without restart).
     * FIX BUG-07: Load into temp sets first, then swap atomically under write lock.
     * This eliminates the blind window where badWords was empty between clear and reload.
     */
    public void reloadBadWords() {
        log.info("Reloading bad words from file...");

        // Step 1: Load new words into temp collections WITHOUT holding any lock
        Set<String> newWords = new HashSet<>();
        Set<Pattern> newPatterns = new HashSet<>();
        try {
            ClassPathResource resource = new ClassPathResource(Constant.BAD_WORDS_FILE_PATH);
            if (!resource.exists()) {
                log.error("Bad words file not found during reload: {} — keeping current list", Constant.BAD_WORDS_FILE_PATH);
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim().toLowerCase();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        newWords.add(trimmed);
                    }
                }
            }
            // Pre-compile patterns for new word set
            for (String word : newWords) {
                newPatterns.add(Pattern.compile(
                        "\\b" + Pattern.quote(word) + "\\b",
                        Pattern.CASE_INSENSITIVE));
            }
        } catch (Exception e) {
            log.error("Failed to reload bad words from file — keeping current list active", e);
            return; // abort: old list stays active, zero blind window
        }

        // Step 2: Swap atomically — readers blocked for microseconds only
        lock.writeLock().lock();
        try {
            badWords.clear();
            badWords.addAll(newWords);
            badWordPatterns = newPatterns; // volatile — visible immediately after unlock
        } finally {
            lock.writeLock().unlock();
        }

        log.info("Bad words reloaded successfully — {} words now active", newWords.size());
    }

    /**
     * Result class for bad word checking.
     * Immutable and thread-safe by design.
     */
    public static class BadWordCheckResult {
        private final boolean allowed;
        private final String badWord;
        private final String status;
        private final String message;

        private BadWordCheckResult(boolean allowed, String badWord, String status, String message) {
            this.allowed = allowed;
            this.badWord = badWord;
            this.status = status;
            this.message = message;
        }

        public static BadWordCheckResult allow() {
            return new BadWordCheckResult(true, null, "ALLOW", "OK");
        }

        public static BadWordCheckResult block(String badWord) {
            return new BadWordCheckResult(
                    false,
                    badWord,
                    "ERROR",
                    "Avoid using bad word: " + badWord
            );
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getBadWord() {
            return badWord;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }
}