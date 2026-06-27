package com.JanSahayak.AI.service;

import com.JanSahayak.AI.payload.request.CreatePollRequest;
import com.JanSahayak.AI.payload.request.PollResponse;
import com.JanSahayak.AI.model.Poll;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.PollRepository;
import com.JanSahayak.AI.util.IdempotencyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PollServiceIdempotencyTest {

    @Mock
    private PollRepository pollRepository;

    @InjectMocks
    private PollService pollService;

    @BeforeEach
    void setUp() {
        IdempotencyContext.clear();
    }

    @AfterEach
    void tearDown() {
        IdempotencyContext.clear();
    }

    @Test
    void testCreatePoll_WithExistingIdempotencyKey_ReturnsExistingPoll() {
        // Arrange
        String idempotencyKey = "poll-key-123";
        IdempotencyContext.setKey(idempotencyKey);

        User user = new User();
        user.setId(1L);

        CreatePollRequest createDto = new CreatePollRequest();
        createDto.setQuestion("Test Question?");
        createDto.setOptions(List.of("Option A", "Option B"));

        Poll existingPoll = new Poll();
        existingPoll.setId(300L);
        existingPoll.setIdempotencyKey(idempotencyKey);
        existingPoll.setQuestion("Test Question?");
        existingPoll.setOptions(List.of());

        when(pollRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPoll));

        // Act
        PollResponse response = pollService.createPollPost(createDto, user);

        // Assert
        assertNotNull(response);
        assertEquals(300L, response.getPollId());
        
        // Ensure no save was performed
        verify(pollRepository, never()).save(any(Poll.class));
    }
}
