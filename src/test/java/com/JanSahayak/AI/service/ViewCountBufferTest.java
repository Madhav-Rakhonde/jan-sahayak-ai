package com.JanSahayak.AI.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ViewCountBuffer.
 *
 * Covers:
 *  - recordPostView / recordSocialPostView accumulate correctly
 *  - drainPostViews / drainSocialPostViews return correct counts and reset buffer
 *  - concurrent writes are safe (no lost updates)
 *  - null IDs are silently ignored
 *  - draining an empty buffer returns empty map
 */
@DisplayName("ViewCountBuffer")
class ViewCountBufferTest {

    private ViewCountBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new ViewCountBuffer();
    }

    // ── Basic accumulation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordPostView")
    class RecordPostView {

        @Test
        @DisplayName("single view is counted")
        void singleView() {
            buffer.recordPostView(42L);
            Map<Long, Integer> drained = buffer.drainPostViews();
            assertEquals(1, drained.get(42L));
        }

        @Test
        @DisplayName("multiple views on same post are summed")
        void multipleViewsSamePost() {
            buffer.recordPostView(1L);
            buffer.recordPostView(1L);
            buffer.recordPostView(1L);
            Map<Long, Integer> drained = buffer.drainPostViews();
            assertEquals(3, drained.get(1L));
        }

        @Test
        @DisplayName("views on different posts are tracked independently")
        void differentPosts() {
            buffer.recordPostView(1L);
            buffer.recordPostView(2L);
            buffer.recordPostView(2L);
            Map<Long, Integer> drained = buffer.drainPostViews();
            assertEquals(1, drained.get(1L));
            assertEquals(2, drained.get(2L));
        }

        @Test
        @DisplayName("null postId is silently ignored")
        void nullId() {
            buffer.recordPostView(null);
            Map<Long, Integer> drained = buffer.drainPostViews();
            assertTrue(drained.isEmpty());
        }
    }

    @Nested
    @DisplayName("recordSocialPostView")
    class RecordSocialPostView {

        @Test
        @DisplayName("social post views accumulate correctly")
        void accumulates() {
            buffer.recordSocialPostView(99L);
            buffer.recordSocialPostView(99L);
            Map<Long, Integer> drained = buffer.drainSocialPostViews();
            assertEquals(2, drained.get(99L));
        }

        @Test
        @DisplayName("social post and regular post buffers are independent")
        void independentBuffers() {
            buffer.recordPostView(5L);
            buffer.recordSocialPostView(5L);

            Map<Long, Integer> posts       = buffer.drainPostViews();
            Map<Long, Integer> socialPosts = buffer.drainSocialPostViews();

            assertEquals(1, posts.get(5L));
            assertEquals(1, socialPosts.get(5L));
        }
    }

    // ── Drain behaviour ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("drainPostViews")
    class DrainPostViews {

        @Test
        @DisplayName("drain resets the buffer — second drain returns empty")
        void drainResetsBuffer() {
            buffer.recordPostView(10L);
            buffer.drainPostViews();                         // first drain
            Map<Long, Integer> second = buffer.drainPostViews(); // second drain
            assertTrue(second.isEmpty(), "Buffer should be empty after drain");
        }

        @Test
        @DisplayName("drain on empty buffer returns empty map (no NPE)")
        void drainEmptyBuffer() {
            Map<Long, Integer> result = buffer.drainPostViews();
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("views recorded after drain go into fresh buffer")
        void viewsAfterDrainAccumulate() {
            buffer.recordPostView(7L);
            buffer.drainPostViews();               // drain clears

            buffer.recordPostView(7L);             // new view after drain
            buffer.recordPostView(7L);
            Map<Long, Integer> second = buffer.drainPostViews();
            assertEquals(2, second.get(7L));
        }
    }

    // ── Concurrency ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("concurrent writes")
    class ConcurrentWrites {

        @Test
        @DisplayName("100 concurrent threads each record 100 views — total must be 10000")
        void concurrentViewsAreNotLost() throws InterruptedException {
            int threads    = 100;
            int viewsEach  = 100;
            Long postId    = 1L;

            Thread[] workers = new Thread[threads];
            for (int i = 0; i < threads; i++) {
                workers[i] = new Thread(() -> {
                    for (int v = 0; v < viewsEach; v++) {
                        buffer.recordPostView(postId);
                    }
                });
            }
            for (Thread t : workers) t.start();
            for (Thread t : workers) t.join();

            Map<Long, Integer> result = buffer.drainPostViews();
            assertEquals(threads * viewsEach, result.get(postId),
                    "No view updates should be lost under concurrent writes");
        }
    }
}
