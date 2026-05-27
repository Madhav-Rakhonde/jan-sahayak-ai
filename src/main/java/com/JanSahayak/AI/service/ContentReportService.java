package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.ReportCategory;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.model.ContentReport;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.ContentReportRepository;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ContentReportService {

    // Number of PENDING reports on a single item that triggers auto-flag + virality suppression
    private static final long AUTO_FLAG_THRESHOLD = 5;

    @Autowired private ContentReportRepository reportRepo;
    @Autowired private SocialPostRepo socialPostRepo;
    @Autowired private UserRepo userRepo;

    // ── Helper ────────────────────────────────────────────────────────────────

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ServiceException("Authenticated user not found"));
    }

    // ── File a report ─────────────────────────────────────────────────────────

    @Transactional
    public ContentReport fileReport(String targetType, Long targetId,
                                    ReportCategory category, String description) {

        User reporter = currentUser();

        // Prevent duplicate reports from same user on same content
        if (reportRepo.existsByReporter_IdAndTargetTypeAndTargetId(
                reporter.getId(), targetType, targetId)) {
            throw new ServiceException("You have already reported this content.");
        }

        ContentReport report = ContentReport.builder()
                .reporter(reporter)
                .targetType(targetType.toUpperCase())
                .targetId(targetId)
                .category(category)
                .description(description)
                .status("PENDING")
                .build();
        // isEmergency is set automatically in @PrePersist
        report = reportRepo.save(report);

        // Virality suppression & auto-flag if threshold exceeded
        applyAutoModeration(targetType.toUpperCase(), targetId);

        log.info("Report filed: type={} id={} category={} emergency={}",
                targetType, targetId, category, report.getIsEmergency());

        return report;
    }

    private void applyAutoModeration(String targetType, Long targetId) {
        long pendingCount = reportRepo.countByTargetTypeAndTargetIdAndStatus(
                targetType, targetId, "PENDING");

        if ("SOCIAL_POST".equals(targetType)) {
            SocialPost post = socialPostRepo.findById(targetId).orElse(null);
            if (post == null) return;

            // Increment report count on the post
            post.setReportCount((post.getReportCount() == null ? 0 : post.getReportCount()) + 1);

            // Immediately suppress virality on ANY report
            if (post.getReportCount() > 0) {
                post.setIsViral(false);
                post.setViralTier("LOCAL");
                post.setExpansionLevel(0);
                post.setViralityScore(0.0);
            }

            // Auto-flag & hide when threshold crossed
            if (pendingCount >= AUTO_FLAG_THRESHOLD && !Boolean.TRUE.equals(post.getIsFlagged())) {
                post.setIsFlagged(true);
                post.setFlaggedAt(new Date());
                post.setFlagReason("Auto-flagged: " + pendingCount + " pending reports");
                log.warn("SOCIAL_POST {} auto-flagged after {} reports", targetId, pendingCount);
            }

            socialPostRepo.save(post);
        }
        // Future: handle POST and COMMENT types similarly
    }

    // ── Admin: resolve / dismiss ──────────────────────────────────────────────

    @Transactional
    public ContentReport resolveReport(Long reportId, String resolution, String notes) {
        User admin = currentUser();
        ContentReport report = reportRepo.findById(reportId)
                .orElseThrow(() -> new ServiceException("Report not found: " + reportId));

        if (!("RESOLVED_REMOVED".equals(resolution) || "RESOLVED_DISMISSED".equals(resolution))) {
            throw new ServiceException("Invalid resolution. Use RESOLVED_REMOVED or RESOLVED_DISMISSED.");
        }

        report.setStatus(resolution);
        report.setResolvedAt(new Date());
        report.setResolvedBy(admin);
        report.setResolutionNotes(notes);

        // If dismissed (false positive) and post was auto-flagged, restore it
        if ("RESOLVED_DISMISSED".equals(resolution) && "SOCIAL_POST".equals(report.getTargetType())) {
            SocialPost post = socialPostRepo.findById(report.getTargetId()).orElse(null);
            if (post != null && Boolean.TRUE.equals(post.getIsFlagged())) {
                // Only un-flag if no remaining pending reports
                long remaining = reportRepo.countByTargetTypeAndTargetIdAndStatus(
                        report.getTargetType(), report.getTargetId(), "PENDING");
                if (remaining == 0) {
                    post.setIsFlagged(false);
                    post.setFlagReason(null);
                    socialPostRepo.save(post);
                }
            }
        }

        return reportRepo.save(report);
    }

    // ── Admin: paginated queues ───────────────────────────────────────────────

    public Page<ContentReport> getPendingEmergencyQueue(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return reportRepo.findByStatusAndIsEmergencyOrderByCreatedAtAsc("PENDING", true, pageable);
    }

    public Page<ContentReport> getPendingStandardQueue(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return reportRepo.findStandardQueue("PENDING", pageable);
    }

    public Page<ContentReport> getAllReports(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return reportRepo.findAllByOrderByCreatedAtDesc(pageable);
    }

    // ── Dashboard stats ───────────────────────────────────────────────────────

    public Map<String, Long> getDashboardStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalPending",    reportRepo.countByStatus("PENDING"));
        stats.put("emergencyPending", reportRepo.countByStatusAndIsEmergency("PENDING", true));
        stats.put("standardPending",  reportRepo.countByStatusAndIsEmergency("PENDING", false));
        stats.put("totalResolved",   reportRepo.countByStatus("RESOLVED_REMOVED")
                                   + reportRepo.countByStatus("RESOLVED_DISMISSED"));
        return stats;
    }
}
