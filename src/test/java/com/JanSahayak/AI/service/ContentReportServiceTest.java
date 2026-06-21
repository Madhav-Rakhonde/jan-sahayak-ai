package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.ReportCategory;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.model.ContentReport;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.ContentReportRepository;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ContentReportServiceTest {

    @Mock
    private ContentReportRepository reportRepo;

    @Mock
    private SocialPostRepo socialPostRepo;

    @Mock
    private UserRepo userRepo;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ContentReportService contentReportService;

    private User testUser;
    private SocialPost testPost;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");

        testPost = new SocialPost();
        testPost.setId(100L);
        testPost.setReportCount(0);
        testPost.setIsFlagged(false);
    }

    private void setupSecurityContext() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
    }

    @Test
    void testFileReport_NormalReport_BelowThreshold() {
        setupSecurityContext();
        when(reportRepo.existsByReporter_IdAndTargetTypeAndTargetId(1L, "SOCIAL_POST", 100L)).thenReturn(false);
        when(reportRepo.save(any(ContentReport.class))).thenAnswer(i -> {
            ContentReport r = i.getArgument(0);
            r.setId(10L);
            r.setIsEmergency(false); // Simulate @PrePersist logic
            return r;
        });
        when(reportRepo.countByTargetTypeAndTargetIdAndStatus("SOCIAL_POST", 100L, "PENDING")).thenReturn(1L);
        when(socialPostRepo.findById(100L)).thenReturn(Optional.of(testPost));

        ContentReport result = contentReportService.fileReport("SOCIAL_POST", 100L, ReportCategory.SPAM, "Spam content");

        assertNotNull(result);
        assertEquals(1, testPost.getReportCount());
        assertFalse(testPost.getIsViral());
        assertEquals("LOCAL", testPost.getViralTier());
        assertFalse(testPost.getIsFlagged(), "Post should not be flagged yet since reports < 5");
        verify(socialPostRepo, times(1)).save(testPost);
    }

    @Test
    void testFileReport_EmergencyReport_BypassesThreshold() {
        setupSecurityContext();
        when(reportRepo.existsByReporter_IdAndTargetTypeAndTargetId(1L, "SOCIAL_POST", 100L)).thenReturn(false);
        when(reportRepo.save(any(ContentReport.class))).thenAnswer(i -> {
            ContentReport r = i.getArgument(0);
            r.setId(11L);
            r.setIsEmergency(true); // Emergency category sets this
            return r;
        });
        // Even if there's only 1 pending report, it's emergency so it should trigger
        when(reportRepo.countByTargetTypeAndTargetIdAndStatus("SOCIAL_POST", 100L, "PENDING")).thenReturn(1L);
        when(socialPostRepo.findById(100L)).thenReturn(Optional.of(testPost));

        ContentReport result = contentReportService.fileReport("SOCIAL_POST", 100L, ReportCategory.NATIONAL_SECURITY, "Emergency content");

        assertNotNull(result);
        assertTrue(testPost.getIsFlagged(), "Post must be flagged immediately due to emergency severity");
        assertEquals("Emergency Takedown: High severity report received", testPost.getFlagReason());
        verify(socialPostRepo, times(1)).save(testPost);
    }

    @Test
    void testFileReport_NormalReport_ExceedsThreshold() {
        setupSecurityContext();
        when(reportRepo.existsByReporter_IdAndTargetTypeAndTargetId(1L, "SOCIAL_POST", 100L)).thenReturn(false);
        when(reportRepo.save(any(ContentReport.class))).thenAnswer(i -> {
            ContentReport r = i.getArgument(0);
            r.setId(12L);
            r.setIsEmergency(false);
            return r;
        });
        // Simulate 5 reports
        when(reportRepo.countByTargetTypeAndTargetIdAndStatus("SOCIAL_POST", 100L, "PENDING")).thenReturn(5L);
        when(socialPostRepo.findById(100L)).thenReturn(Optional.of(testPost));

        ContentReport result = contentReportService.fileReport("SOCIAL_POST", 100L, ReportCategory.HARASSMENT, "Harassment");

        assertNotNull(result);
        assertTrue(testPost.getIsFlagged(), "Post must be flagged because threshold (5) is reached");
        assertEquals("Auto-flagged: 5 pending reports", testPost.getFlagReason());
        verify(socialPostRepo, times(1)).save(testPost);
    }

    @Test
    void testFileReport_DuplicateReport_ThrowsException() {
        setupSecurityContext();
        when(reportRepo.existsByReporter_IdAndTargetTypeAndTargetId(1L, "SOCIAL_POST", 100L)).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class, () -> {
            contentReportService.fileReport("SOCIAL_POST", 100L, ReportCategory.SPAM, "Spam");
        });

        assertEquals("You have already reported this content.", ex.getMessage());
        verify(socialPostRepo, never()).findById(anyLong());
    }
}
