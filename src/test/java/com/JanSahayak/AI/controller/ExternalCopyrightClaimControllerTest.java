package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.request.CopyrightClaimRequest;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.ExternalCopyrightClaim;
import com.JanSahayak.AI.repository.ExternalCopyrightClaimRepository;
import com.JanSahayak.AI.service.ContentReportService;
import com.JanSahayak.AI.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalCopyrightClaimControllerTest {

    @Mock
    private ExternalCopyrightClaimRepository claimRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private ContentReportService contentReportService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private ExternalCopyrightClaimController controller;

    private CopyrightClaimRequest requestDto;

    @BeforeEach
    void setUp() {
        requestDto = new CopyrightClaimRequest();
        requestDto.setClaimantName("John Doe");
        requestDto.setCompanyName("T-Series");
        requestDto.setClaimantEmail("legal@tseries.com");
        requestDto.setClaimantPhone("+91-9876543210");
        requestDto.setPostalAddress("Mumbai, India");
        requestDto.setInfringingUrls(java.util.List.of("https://govlyx.com/post/123"));
        requestDto.setInfringementDescription("Unauthorized use of song");
        requestDto.setOriginalWorkUrl("https://youtube.com/tseries/song");
        requestDto.setOriginalWorkDescription("Original Song Music Video");
        requestDto.setContentType("Video");
        requestDto.setIsCopyrightOwner(true);
        requestDto.setGoodFaithBelief(true);
        requestDto.setAccuracyDeclaration(true);
        requestDto.setElectronicSignature("John Doe");
    }

    @Test
    void submitClaim_ValidRequest_SavesClaimAndReturnsReferenceId() {
        // Arrange
        when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.1.1");
        when(claimRepository.save(any(ExternalCopyrightClaim.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        ResponseEntity<ApiResponse<String>> response = controller.submitClaim(requestDto, httpServletRequest);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getData()); // Reference ID
        assertTrue(response.getBody().getData().startsWith("GOVLYX-CR-"));

        verify(claimRepository).save(any(ExternalCopyrightClaim.class));
        verify(emailService).sendExternalClaimConfirmationEmail(eq("legal@tseries.com"), eq("John Doe"), anyString());
    }

    @Test
    void acknowledgeClaim_PendingClaim_UpdatesStatusToAcknowledged() {
        // Arrange
        ExternalCopyrightClaim pendingClaim = new ExternalCopyrightClaim();
        pendingClaim.setId(1L);
        pendingClaim.setReferenceId("GOVLYX-CR-TEST");
        pendingClaim.setStatus("PENDING");
        pendingClaim.setClaimantEmail("legal@tseries.com");
        pendingClaim.setClaimantName("John Doe");

        when(claimRepository.findById(1L)).thenReturn(Optional.of(pendingClaim));
        when(claimRepository.save(any(ExternalCopyrightClaim.class))).thenReturn(pendingClaim);

        // Act
        ResponseEntity<ApiResponse<String>> response = controller.acknowledgeClaim(1L);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("ACKNOWLEDGED", pendingClaim.getStatus());
        assertNotNull(pendingClaim.getAcknowledgedAt());

        verify(claimRepository).save(pendingClaim);
        verify(emailService).sendExternalClaimAcknowledgedEmail("legal@tseries.com", "John Doe", "GOVLYX-CR-TEST");
    }

    @Test
    void acknowledgeClaim_AlreadyAcknowledged_ReturnsBadRequest() {
        // Arrange
        ExternalCopyrightClaim ackClaim = new ExternalCopyrightClaim();
        ackClaim.setId(1L);
        ackClaim.setStatus("ACKNOWLEDGED");

        when(claimRepository.findById(1L)).thenReturn(Optional.of(ackClaim));

        // Act
        ResponseEntity<ApiResponse<String>> response = controller.acknowledgeClaim(1L);

        // Assert
        assertNotNull(response);
        assertEquals(400, response.getStatusCode().value());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Claim is not pending", response.getBody().getError());

        verify(claimRepository, never()).save(any());
        verify(emailService, never()).sendExternalClaimAcknowledgedEmail(anyString(), anyString(), anyString());
    }
}
