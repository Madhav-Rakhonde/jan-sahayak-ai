package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.FeedbackCategory;
import com.JanSahayak.AI.enums.FeedbackStatus;
import com.JanSahayak.AI.model.Feedback;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.payload.FeedbackRequest;
import com.JanSahayak.AI.repository.FeedbackRepository;
import com.JanSahayak.AI.repository.UserRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private UserRepo userRepo;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private FeedbackService feedbackService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitFeedback_AnonymousUser_Success() {
        // Arrange
        FeedbackRequest request = new FeedbackRequest();
        request.setRating(4);
        request.setCategory(FeedbackCategory.UI_UX);
        request.setMessage("Great app, but can be faster.");
        request.setAppVersion("1.0.0");
        request.setDeviceInfo("Windows/Chrome");

        when(securityContext.getAuthentication()).thenReturn(null);

        Feedback savedFeedback = new Feedback();
        savedFeedback.setId(UUID.randomUUID());
        savedFeedback.setRating(4);
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(savedFeedback);

        // Act
        Feedback result = feedbackService.submitFeedback(request);

        // Assert
        assertNotNull(result);
        assertEquals(4, result.getRating());

        ArgumentCaptor<Feedback> feedbackCaptor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(feedbackCaptor.capture());
        Feedback captured = feedbackCaptor.getValue();

        assertNull(captured.getUser());
        assertEquals(4, captured.getRating());
        assertEquals(FeedbackCategory.UI_UX, captured.getCategory());
        assertEquals(FeedbackStatus.UNREAD, captured.getStatus());
        assertEquals("Great app, but can be faster.", captured.getMessage());
    }

    @Test
    void submitFeedback_AuthenticatedUser_Success() {
        // Arrange
        FeedbackRequest request = new FeedbackRequest();
        request.setRating(5);
        request.setCategory(FeedbackCategory.FEATURE_REQUEST);
        request.setMessage("Please add dark mode");

        String email = "user@test.com";
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail(email);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("testPrincipal");
        when(authentication.getName()).thenReturn(email);

        when(userRepo.findByEmail(email)).thenReturn(Optional.of(mockUser));

        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        Feedback result = feedbackService.submitFeedback(request);

        // Assert
        assertNotNull(result);
        assertEquals(5, result.getRating());

        ArgumentCaptor<Feedback> feedbackCaptor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(feedbackCaptor.capture());
        Feedback captured = feedbackCaptor.getValue();

        assertNotNull(captured.getUser());
        assertEquals(email, captured.getUser().getEmail());
        assertEquals(FeedbackCategory.FEATURE_REQUEST, captured.getCategory());
    }
}
