package com.JanSahayak.AI.service;

import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Unit tests for ViewCountFlusher.
 *
 * Covers:
 *  - flush() drains ViewCountBuffer and calls incrementViewCountBy on both repos
 *  - No DB call is made when buffers are empty
 *  - A repo failure on one ID does not stop flushing the rest
 *  - flushOnShutdown() delegates correctly to the same logic
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ViewCountFlusher")
class ViewCountFlusherTest {

    @Mock
    private PostRepo postRepo;

    @Mock
    private SocialPostRepo socialPostRepo;

    // Use real ViewCountBuffer — no mock, no spy needed
    private final ViewCountBuffer viewCountBuffer = new ViewCountBuffer();

    @InjectMocks
    private ViewCountFlusher flusher;

    @BeforeEach
    void injectBuffer() {
        // InjectMocks creates the flusher; manually inject real buffer
        flusher = new ViewCountFlusher(viewCountBuffer, postRepo, socialPostRepo);
    }

    // ── flush() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("flush()")
    class Flush {

        @Test
        @DisplayName("calls incrementViewCountBy for each buffered post")
        void flushesPostViews() {
            viewCountBuffer.recordPostView(1L);
            viewCountBuffer.recordPostView(1L);
            viewCountBuffer.recordPostView(2L);

            flusher.flush();

            verify(postRepo).incrementViewCountBy(1L, 2);
            verify(postRepo).incrementViewCountBy(2L, 1);
            verifyNoInteractions(socialPostRepo);
        }

        @Test
        @DisplayName("calls incrementViewCountBy for each buffered social post")
        void flushesSocialPostViews() {
            viewCountBuffer.recordSocialPostView(10L);
            viewCountBuffer.recordSocialPostView(10L);
            viewCountBuffer.recordSocialPostView(10L);

            flusher.flush();

            verify(socialPostRepo).incrementViewCountBy(10L, 3);
            verifyNoInteractions(postRepo);
        }

        @Test
        @DisplayName("no DB call when both buffers are empty")
        void emptyBufferNoDbCall() {
            flusher.flush();

            verifyNoInteractions(postRepo);
            verifyNoInteractions(socialPostRepo);
        }

        @Test
        @DisplayName("buffer is empty after flush — second flush hits no repo")
        void bufferClearedAfterFlush() {
            viewCountBuffer.recordPostView(5L);
            flusher.flush();      // first flush — clears buffer

            reset(postRepo);
            flusher.flush();      // second flush — buffer empty
            verifyNoInteractions(postRepo);
        }

        @Test
        @DisplayName("repo failure on one ID does not stop other IDs being flushed")
        void partialFailureDoesNotStopOtherFlushes() {
            viewCountBuffer.recordPostView(1L);
            viewCountBuffer.recordPostView(2L);
            viewCountBuffer.recordPostView(3L);

            // ID 2 throws — IDs 1 and 3 should still be processed
            doThrow(new RuntimeException("DB error"))
                    .when(postRepo).incrementViewCountBy(eq(2L), anyInt());

            // Should not throw
            flusher.flush();

            // 3 IDs were attempted (order non-deterministic in HashMap)
            verify(postRepo, times(3)).incrementViewCountBy(anyLong(), anyInt());
        }
    }

    // ── flushOnShutdown() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("flushOnShutdown()")
    class FlushOnShutdown {

        @Test
        @DisplayName("flushOnShutdown drains pending views before JVM exit")
        void flushesOnShutdown() {
            viewCountBuffer.recordPostView(99L);
            viewCountBuffer.recordSocialPostView(88L);

            flusher.flushOnShutdown();

            verify(postRepo).incrementViewCountBy(99L, 1);
            verify(socialPostRepo).incrementViewCountBy(88L, 1);
        }
    }
}
