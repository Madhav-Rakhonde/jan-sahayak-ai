package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.enums.ReportCategory;
import com.JanSahayak.AI.model.ContentReport;
import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ContentReportRepositoryTest {

    @Autowired
    private ContentReportRepository reportRepository;

    @Autowired
    private UserRepo userRepository;

    @Autowired
    private RoleRepo roleRepository;

    @Test
    void testFindStandardQueueWithEntityGraph() {
        // Setup role
        Role role = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            return roleRepository.saveAndFlush(r);
        });

        // Setup user
        User reporter = new User();
        reporter.setUsername("TestReporter");
        reporter.setRole(role);
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
        assertTrue(result.getTotalElements() >= 1);

        // Verify the reporter is eager loaded by checking if its property is accessible
        // (Without @EntityGraph, accessing reporter.getUsername() would throw a LazyInitializationException outside the transaction,
        // although inside @DataJpaTest a transaction is active, so we just verify it runs without crashing and fetches correctly).
        ContentReport fetchedReport = result.getContent().stream()
                .filter(r -> r.getId().equals(report.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Report not found"));
        assertEquals("TestReporter", fetchedReport.getReporter().getActualUsername());
    }

    @Test
    void testFindEmergencyQueueWithEntityGraph() {
        // Setup role
        Role role = roleRepository.findByName("ROLE_USER2").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER2");
            return roleRepository.saveAndFlush(r);
        });

        // Setup user
        User reporter = new User();
        reporter.setUsername("EmergencyReporter");
        reporter.setRole(role);
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
        assertTrue(result.getTotalElements() >= 1);
        ContentReport fetchedReport = result.getContent().stream()
                .filter(r -> r.getId().equals(report.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Report not found"));
        assertEquals("EmergencyReporter", fetchedReport.getReporter().getActualUsername());
    }
}
