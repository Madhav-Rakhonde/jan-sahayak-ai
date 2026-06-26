package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.enums.ReportCategory;
import com.JanSahayak.AI.model.ContentReport;
import com.JanSahayak.AI.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
public class ContentReportRepositoryTest {

    @Autowired
    private ContentReportRepository reportRepository;

    @Autowired
    private UserRepo userRepository;

    @Test
    void testFindStandardQueueWithEntityGraph() {
        // Setup user
        User reporter = new User();
        reporter.setUsername("TestReporter");
        reporter.setEmail("reporter@example.com");
        reporter.setPassword("password123");
        userRepository.saveAndFlush(reporter);

        // Setup report
        ContentReport report = new ContentReport();
        report.setCategory(ReportCategory.SPAM);
        report.setTargetType("POST");
        report.setTargetId(1L);
        report.setReporter(reporter);
        report.setStatus("PENDING");
        report.setIsEmergency(false);
        reportRepository.saveAndFlush(report);

        // Test the standard queue query
        Page<ContentReport> result = reportRepository.findStandardQueue("PENDING", PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());

        // Verify the reporter is eager loaded by checking if its property is accessible
        // (Without @EntityGraph, accessing reporter.getUsername() would throw a LazyInitializationException outside the transaction,
        // although inside @DataJpaTest a transaction is active, so we just verify it runs without crashing and fetches correctly).
        ContentReport fetchedReport = result.getContent().get(0);
        assertEquals("TestReporter", fetchedReport.getReporter().getUsername());
    }

    @Test
    void testFindEmergencyQueueWithEntityGraph() {
        // Setup user
        User reporter = new User();
        reporter.setUsername("EmergencyReporter");
        reporter.setEmail("emergency@example.com");
        reporter.setPassword("password123");
        userRepository.saveAndFlush(reporter);

        // Setup report
        ContentReport report = new ContentReport();
        report.setCategory(ReportCategory.HARASSMENT);
        report.setTargetType("POST");
        report.setTargetId(2L);
        report.setReporter(reporter);
        report.setStatus("PENDING");
        report.setIsEmergency(true);
        reportRepository.saveAndFlush(report);

        // Test the emergency queue query
        Page<ContentReport> result = reportRepository.findByStatusAndIsEmergencyOrderByCreatedAtAsc("PENDING", true, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        ContentReport fetchedReport = result.getContent().get(0);
        assertEquals("EmergencyReporter", fetchedReport.getReporter().getUsername());
    }
}
