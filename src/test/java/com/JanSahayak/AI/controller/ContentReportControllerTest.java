package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.enums.ReportCategory;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.ContentReport;
import com.JanSahayak.AI.service.ContentReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ContentReportControllerTest {

    @Mock
    private ContentReportService reportService;

    @InjectMocks
    private ContentReportController reportController;

    private ContentReport sampleReport;
    private Page<ContentReport> samplePage;

    @BeforeEach
    void setUp() {
        sampleReport = new ContentReport();
        sampleReport.setId(1L);
        sampleReport.setCategory(ReportCategory.SPAM);
        sampleReport.setStatus("PENDING");

        samplePage = new PageImpl<>(Collections.singletonList(sampleReport));
    }

    @Test
    void testGetStats() {
        when(reportService.getDashboardStats()).thenReturn(Collections.singletonMap("totalPending", 5L));

        ResponseEntity<ApiResponse<Map<String, Long>>> response = reportController.getStats();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(5L, response.getBody().getData().get("totalPending"));
    }

    @Test
    void testGetEmergencyQueue() {
        when(reportService.getPendingEmergencyQueue(anyInt(), anyInt())).thenReturn(samplePage);

        ResponseEntity<ApiResponse<Page<ContentReport>>> response = reportController.getEmergencyQueue(0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getData().getContent().size());
        assertEquals(1L, response.getBody().getData().getContent().get(0).getId());
    }

    @Test
    void testGetStandardQueue() {
        when(reportService.getPendingStandardQueue(anyInt(), anyInt())).thenReturn(samplePage);

        ResponseEntity<ApiResponse<Page<ContentReport>>> response = reportController.getStandardQueue(0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getData().getContent().size());
    }

    @Test
    void testGetAllReports() {
        when(reportService.getAllReports(anyInt(), anyInt())).thenReturn(samplePage);

        ResponseEntity<ApiResponse<Page<ContentReport>>> response = reportController.getAllReports(0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getData().getContent().size());
    }
}
