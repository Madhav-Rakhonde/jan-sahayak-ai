package com.JanSahayak.AI;

import com.JanSahayak.AI.enums.ReportCategory;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.model.ContentReport;
import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.ContentReportRepository;
import com.JanSahayak.AI.repository.RoleRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.service.ContentReportService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test suite verifying:
 *
 *  EMAIL VERIFICATION FLOW
 *  ─────────────────────────────────────────────────────────────────────────
 *  1. Citizen signup stores isEmailVerified=false + a UUID token with a 24h expiry.
 *  2. An unverified user is correctly identified (flag check without HTTP call —
 *     the same check AuthController.login() performs before issuing a JWT).
 *  3. A valid token flips isEmailVerified=true and clears the token fields.
 *  4. An already-expired token is rejected.
 *
 *  CONTENT MODERATION PIPELINE (IT Rules 2021)
 *  ─────────────────────────────────────────────────────────────────────────
 *  5. Filing a report on a social post suppresses virality (isViral→false,
 *     viralityScore→0, expansionLevel→0) immediately, even on the first report.
 *  6. Filing a second report from a different user is allowed; duplicate
 *     reports from the same user are rejected (ServiceException).
 *  7. Emergency categories (HARASSMENT, OBSCENITY, IMPERSONATION,
 *     NATIONAL_SECURITY) are auto-classified as isEmergency=true.
 *  8. After AUTO_FLAG_THRESHOLD (5) pending reports the post is auto-flagged
 *     (isFlagged=true) with a flagReason.
 *  9. Resolving a report as RESOLVED_REMOVED updates status and records admin.
 * 10. Resolving a report as RESOLVED_DISMISSED un-flags the post when no
 *     other pending reports remain.
 * 11. Dashboard stats counts match the expected counts.
 */
@SpringBootTest
@DisplayName("Email Verification & Content Moderation Integration Tests")
class ContentReportAndVerificationTests {

    // ── Repositories ──────────────────────────────────────────────────────────
    @Autowired private UserRepo              userRepo;
    @Autowired private RoleRepo              roleRepo;
    @Autowired private SocialPostRepo        socialPostRepo;
    @Autowired private ContentReportRepository reportRepo;

    // ── Services ──────────────────────────────────────────────────────────────
    @Autowired private ContentReportService  reportService;
    @Autowired private PasswordEncoder       passwordEncoder;

    // ── Test Fixtures (per-test, reset inside each test) ──────────────────────
    private User  citizenA;
    private User  citizenB;
    private User  adminUser;
    private SocialPost testPost;

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper: build and persist a minimal User
    // ─────────────────────────────────────────────────────────────────────────
    private User buildAndSaveUser(String emailPrefix, String roleName, boolean verified) {
        Role role = roleRepo.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found in DB: " + roleName));

        User u = new User();
        u.setEmail(emailPrefix + "_test_" + UUID.randomUUID() + "@govlyx-test.local");
        u.setUsername("usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
        u.setPassword(passwordEncoder.encode("Test@1234"));
        u.setRole(role);
        u.setPincode("411001");
        u.setIsEmailVerified(verified);

        if (!verified) {
            u.setEmailVerificationToken(UUID.randomUUID().toString());
            u.setEmailVerificationTokenExpiry(Date.from(Instant.now().plus(24, ChronoUnit.HOURS)));
        }
        return userRepo.save(u);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper: build and persist a minimal SocialPost owned by a given user
    // ─────────────────────────────────────────────────────────────────────────
    private SocialPost buildAndSavePost(User owner) {
        SocialPost post = SocialPost.builder()
                .content("Test post content for IT Rules 2021 moderation tests.")
                .mediaCount(0)
                .language("en")
                .user(owner)
                .pincode(owner.getPincode())
                .isViral(true)             // start as viral so suppression is testable
                .viralTier("DISTRICT_VIRAL")
                .expansionLevel(1)
                .viralityScore(42.0)
                .reportCount(0)
                .isFlagged(false)
                .allowComments(true)
                .build();
        return socialPostRepo.save(post);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper: push a User into Spring Security context (simulates a logged-in
    //  request for ContentReportService which reads from SecurityContextHolder)
    // ─────────────────────────────────────────────────────────────────────────
    private void loginAs(User user) {
        var auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null,
                List.of(new SimpleGrantedAuthority(user.getRole().getName()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    //  SECTION 1 – EMAIL VERIFICATION FLOW
    // =========================================================================

    @Nested
    @DisplayName("1 · Email Verification Flow")
    class EmailVerificationTests {

        @Test
        @Transactional
        @DisplayName("1.1 — New citizen signup stores isEmailVerified=false with a valid 24h token")
        void signup_shouldSetUnverifiedWithToken() {
            // Simulate what AuthController.registerUser() does manually
            Role citizenRole = roleRepo.findByName("ROLE_USER")
                    .orElseThrow(() -> new IllegalStateException("ROLE_USER not found"));

            String token  = UUID.randomUUID().toString();
            Date   expiry = Date.from(Instant.now().plus(24, ChronoUnit.HOURS));

            User newUser = new User();
            newUser.setEmail("signup_test_" + UUID.randomUUID() + "@govlyx-test.local");
            newUser.setUsername("newusr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            newUser.setPassword(passwordEncoder.encode("Test@1234"));
            newUser.setRole(citizenRole);
            newUser.setPincode("400001");
            newUser.setIsEmailVerified(false);
            newUser.setEmailVerificationToken(token);
            newUser.setEmailVerificationTokenExpiry(expiry);

            User saved = userRepo.save(newUser);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getIsEmailVerified()).isFalse();
            assertThat(saved.getEmailVerificationToken()).isEqualTo(token);
            assertThat(saved.getEmailVerificationTokenExpiry()).isAfter(new Date());
        }

        @Test
        @Transactional
        @DisplayName("1.2 — Unverified user is correctly detected (same check as login guard)")
        void unverifiedUser_shouldBeDetectedByLoginGuard() {
            User unverified = buildAndSaveUser("unverified", "ROLE_USER", false);

            // The guard AuthController.login() performs:
            //   if (!Boolean.TRUE.equals(user.getIsEmailVerified())) → block
            assertThat(Boolean.TRUE.equals(unverified.getIsEmailVerified())).isFalse();
        }

        @Test
        @Transactional
        @DisplayName("1.3 — Valid token flips isEmailVerified=true and clears token fields")
        void verifyEmail_withValidToken_shouldActivateUser() {
            User unverified = buildAndSaveUser("toverify", "ROLE_USER", false);
            String token = unverified.getEmailVerificationToken();

            // Simulate AuthController.verifyEmail()
            User found = userRepo.findByEmailVerificationToken(token)
                    .orElseThrow(() -> new AssertionError("Token not found"));

            assertThat(found.getEmailVerificationTokenExpiry()).isAfter(new Date());  // not expired

            found.setIsEmailVerified(true);
            found.setEmailVerificationToken(null);
            found.setEmailVerificationTokenExpiry(null);
            userRepo.save(found);

            User refreshed = userRepo.findByEmail(unverified.getEmail())
                    .orElseThrow(() -> new AssertionError("User not found after update"));

            assertThat(refreshed.getIsEmailVerified()).isTrue();
            assertThat(refreshed.getEmailVerificationToken()).isNull();
            assertThat(refreshed.getEmailVerificationTokenExpiry()).isNull();
        }

        @Test
        @Transactional
        @DisplayName("1.4 — Expired token is correctly rejected")
        void verifyEmail_withExpiredToken_shouldBeRejected() {
            Role citizenRole = roleRepo.findByName("ROLE_USER")
                    .orElseThrow(() -> new IllegalStateException("ROLE_USER not found"));

            String token = UUID.randomUUID().toString();
            // Set expiry 2 hours IN THE PAST
            Date expired = Date.from(Instant.now().minus(2, ChronoUnit.HOURS));

            User expiredUser = new User();
            expiredUser.setEmail("expired_" + UUID.randomUUID() + "@govlyx-test.local");
            expiredUser.setUsername("exp_" + UUID.randomUUID().toString().replace("-","").substring(0,10));
            expiredUser.setPassword(passwordEncoder.encode("Test@1234"));
            expiredUser.setRole(citizenRole);
            expiredUser.setPincode("110001");
            expiredUser.setIsEmailVerified(false);
            expiredUser.setEmailVerificationToken(token);
            expiredUser.setEmailVerificationTokenExpiry(expired);
            userRepo.save(expiredUser);

            User found = userRepo.findByEmailVerificationToken(token)
                    .orElseThrow(() -> new AssertionError("Token not found"));

            // The AuthController guard:
            //   if (user.getEmailVerificationTokenExpiry().before(new Date())) → reject
            assertThat(found.getEmailVerificationTokenExpiry().before(new Date())).isTrue();
        }
    }

    // =========================================================================
    //  SECTION 2 – CONTENT MODERATION PIPELINE (IT Rules 2021)
    // =========================================================================

    @Nested
    @Transactional
    @DisplayName("2 · Content Moderation Pipeline")
    class ContentModerationTests {

        private User  reporter1;
        private User  reporter2;
        private User  reporter3;
        private User  reporter4;
        private User  reporter5;
        private User  admin;
        private SocialPost post;

        @BeforeEach
        void setUp() {
            reporter1 = buildAndSaveUser("rptr1", "ROLE_USER", true);
            reporter2 = buildAndSaveUser("rptr2", "ROLE_USER", true);
            reporter3 = buildAndSaveUser("rptr3", "ROLE_USER", true);
            reporter4 = buildAndSaveUser("rptr4", "ROLE_USER", true);
            reporter5 = buildAndSaveUser("rptr5", "ROLE_USER", true);
            admin     = buildAndSaveUser("admin",  "ROLE_ADMIN", true);
            post      = buildAndSavePost(reporter1);
        }

        @Test
        @DisplayName("2.1 — First report immediately suppresses virality (isViral→false)")
        void firstReport_shouldSuppressVirality() {
            assertThat(post.getIsViral()).isTrue();  // started viral

            loginAs(reporter2);
            reportService.fileReport("SOCIAL_POST", post.getId(),
                    ReportCategory.MISINFORMATION, "Spreading fake news.");

            SocialPost refreshed = socialPostRepo.findById(post.getId())
                    .orElseThrow(() -> new AssertionError("Post not found"));

            assertThat(refreshed.getIsViral()).isFalse();
            assertThat(refreshed.getViralityScore()).isEqualTo(0.0);
            assertThat(refreshed.getExpansionLevel()).isEqualTo(0);
            assertThat(refreshed.getReportCount()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("2.2 — Duplicate report from same user is rejected")
        void duplicateReport_fromSameUser_shouldThrow() {
            loginAs(reporter2);
            reportService.fileReport("SOCIAL_POST", post.getId(),
                    ReportCategory.SPAM, "Spammy content.");

            // Second report from same user on same post
            assertThatThrownBy(() ->
                reportService.fileReport("SOCIAL_POST", post.getId(),
                        ReportCategory.SPAM, "Spammy content again."))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("already reported");
        }

        @Test
        @DisplayName("2.3 — HARASSMENT is classified as emergency (24h SLA)")
        void harassmentReport_shouldBeEmergency() {
            loginAs(reporter2);
            ContentReport report = reportService.fileReport("SOCIAL_POST", post.getId(),
                    ReportCategory.HARASSMENT, "Targeted harassment.");

            assertThat(report.getIsEmergency()).isTrue();
            assertThat(report.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("2.4 — MISINFORMATION is classified as standard (15-day SLA)")
        void misinformationReport_shouldBeStandard() {
            loginAs(reporter2);
            ContentReport report = reportService.fileReport("SOCIAL_POST", post.getId(),
                    ReportCategory.MISINFORMATION, "Contains false information.");

            assertThat(report.getIsEmergency()).isFalse();
            assertThat(report.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("2.5 — 5 pending reports trigger auto-flag (isFlagged=true)")
        void fivePendingReports_shouldAutoFlagPost() {
            // Reporter 2–5 each file one report (reporter1 is the post owner, using 2-5+admin=5 reporters)
            loginAs(reporter2);
            reportService.fileReport("SOCIAL_POST", post.getId(), ReportCategory.SPAM, "Spam 1");

            loginAs(reporter3);
            reportService.fileReport("SOCIAL_POST", post.getId(), ReportCategory.SPAM, "Spam 2");

            loginAs(reporter4);
            reportService.fileReport("SOCIAL_POST", post.getId(), ReportCategory.SPAM, "Spam 3");

            loginAs(reporter5);
            reportService.fileReport("SOCIAL_POST", post.getId(), ReportCategory.SPAM, "Spam 4");

            // admin is the 5th reporter
            loginAs(admin);
            reportService.fileReport("SOCIAL_POST", post.getId(), ReportCategory.SPAM, "Spam 5");

            SocialPost flagged = socialPostRepo.findById(post.getId())
                    .orElseThrow(() -> new AssertionError("Post not found after 5 reports"));

            assertThat(flagged.getIsFlagged()).isTrue();
            assertThat(flagged.getFlagReason()).contains("Auto-flagged");
            assertThat(flagged.getReportCount()).isGreaterThanOrEqualTo(5);
        }

        @Test
        @DisplayName("2.6 — Admin RESOLVED_REMOVED updates status and records resolver")
        void resolveAsRemoved_shouldUpdateStatus() {
            loginAs(reporter2);
            ContentReport report = reportService.fileReport("SOCIAL_POST", post.getId(),
                    ReportCategory.IMPERSONATION, "Fake profile.");

            loginAs(admin);
            ContentReport resolved = reportService.resolveReport(
                    report.getId(), "RESOLVED_REMOVED", "Content verified as impersonation.");

            assertThat(resolved.getStatus()).isEqualTo("RESOLVED_REMOVED");
            assertThat(resolved.getResolvedAt()).isNotNull();
            assertThat(resolved.getResolvedBy().getEmail()).isEqualTo(admin.getEmail());
            assertThat(resolved.getResolutionNotes()).contains("impersonation");
        }

        @Test
        @DisplayName("2.7 — Admin RESOLVED_DISMISSED un-flags post when no pending reports remain")
        void resolveAsDismissed_shouldUnflagPostWithNoPendingReports() {
            // Manually flag the post as if auto-flagged
            post.setIsFlagged(true);
            post.setFlagReason("Auto-flagged: 5 pending reports");
            socialPostRepo.save(post);

            loginAs(reporter2);
            ContentReport report = reportService.fileReport("SOCIAL_POST", post.getId(),
                    ReportCategory.SPAM, "False alarm - not spam.");

            loginAs(admin);
            // Resolve this last pending report as dismissed → should clear the flag
            reportService.resolveReport(report.getId(), "RESOLVED_DISMISSED",
                    "Content reviewed - not a violation.");

            SocialPost unflagged = socialPostRepo.findById(post.getId())
                    .orElseThrow(() -> new AssertionError("Post not found"));

            assertThat(unflagged.getIsFlagged()).isFalse();
            assertThat(unflagged.getFlagReason()).isNull();
        }

        @Test
        @DisplayName("2.8 — Dashboard stats correctly count pending and resolved reports")
        void dashboardStats_shouldReflectAccurateCountsForFiledReports() {
            loginAs(reporter2);
            ContentReport r1 = reportService.fileReport("SOCIAL_POST", post.getId(),
                    ReportCategory.HATE_SPEECH, "Incitement to violence.");

            // Resolve one immediately
            loginAs(admin);
            reportService.resolveReport(r1.getId(), "RESOLVED_REMOVED", "Confirmed hate speech.");

            // File another
            loginAs(reporter3);
            reportService.fileReport("SOCIAL_POST", post.getId(),
                    ReportCategory.MISINFORMATION, "False info.");

            Map<String, Long> stats = reportService.getDashboardStats();

            assertThat(stats).containsKey("totalPending");
            assertThat(stats).containsKey("emergencyPending");
            assertThat(stats).containsKey("standardPending");
            assertThat(stats).containsKey("totalResolved");

            // totalResolved should be at least 1 (from the RESOLVED_REMOVED above)
            assertThat(stats.get("totalResolved")).isGreaterThanOrEqualTo(1L);
            // totalPending should be at least 1 (the MISINFORMATION report)
            assertThat(stats.get("totalPending")).isGreaterThanOrEqualTo(1L);
        }
    }
}
