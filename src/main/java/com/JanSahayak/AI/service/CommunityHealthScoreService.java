package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.CommunityDto.HealthInsightResponse;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.repository.CommunityRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommunityHealthScoreService {

    private final CommunityRepo communityRepo;

    // ── Scheduler ─────────────────────────────────────────────────────────────

    /** Recalculate health score for all active communities every 6 hours. */
    @Scheduled(cron = "0 0 */6 * * *")
    public void scheduledRecalculation() {
        log.info("Community health score recalculation started");
        List<Community> communities = communityRepo.findAllActive();
        int updated = 0;
        for (Community community : communities) {
            try {
                recalculate(community);
                communityRepo.save(community);
                updated++;
            } catch (Exception e) {
                log.error("Health score recalculation failed for community {}: {}", community.getId(), e.getMessage());
            }
        }
        log.info("Health score recalculation complete — {} communities updated", updated);
    }

    /** Reset weekly counters every Sunday at midnight. */
    @Scheduled(cron = "0 0 0 * * SUN")
    public void resetWeeklyCounters() {
        communityRepo.resetWeeklyCounters();
        log.info("Weekly community counters reset");
    }

    // ── On-demand ─────────────────────────────────────────────────────────────

    public void recalculateNow(Long communityId) {
        Community community = communityRepo.findById(communityId)
                .orElseThrow(() -> new NoSuchElementException("Community not found: " + communityId));
        recalculate(community);
        communityRepo.save(community);
    }

    // ── Owner insights dashboard ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public HealthInsightResponse getInsightsForOwner(Long communityId) {
        Community c = communityRepo.findById(communityId)
                .orElseThrow(() -> new NoSuchElementException("Community not found: " + communityId));

        String suggestion = buildSuggestion(c);
        String trend      = buildTrend(c);

        return HealthInsightResponse.builder()
                .communityId(c.getId())
                .communityName(c.getName())
                .healthScore(c.getHealthScore())
                .healthTier(c.getHealthTier())
                .healthTierEmoji(c.getHealthTierEmoji())
                // Component scores
                .postFreqScore(c.getHealthPostFreqScore())
                .memberGrowthScore(c.getHealthMemberGrowthScore())
                .engagementScore(c.getHealthEngagementScore())
                .modActivityScore(c.getHealthModActivityScore())
                .retentionScore(c.getHealthRetentionScore())
                // Raw 7-day metrics
                .postsLast7d(c.getPostsLast7d())
                .newMembersLast7d(c.getNewMembersLast7d())
                .activePostersLast7d(c.getActivePostersLast7d())
                .totalMembers(c.getMemberCount())
                .totalPosts(c.getPostCount())
                // Feed surfacing (v3 — replaces sharedToFeedCount)
                .feedSurfaceCount(c.getFeedSurfaceCount())
                .feedReachLevel(deriveFeedReachLevel(c))
                // Trend & suggestion
                .weeklyPostTrend(trend)
                .suggestion(suggestion)
                .scoreUpdatedAt(c.getHealthScoreUpdatedAt())
                .build();
    }

    // ── Calculation ───────────────────────────────────────────────────────────

    private void recalculate(Community c) {
        double postFreq    = calcPostFreqScore(c);
        double engagement  = calcEngagementScore(c);
        double memberGrowth = calcMemberGrowthScore(c);
        double modActivity = calcModActivityScore(c);
        double retention   = calcRetentionScore(c);

        c.setHealthPostFreqScore(postFreq);
        c.setHealthEngagementScore(engagement);
        c.setHealthMemberGrowthScore(memberGrowth);
        c.setHealthModActivityScore(modActivity);
        c.setHealthRetentionScore(retention);

        double composite = (postFreq    * 0.30)
                + (engagement  * 0.25)
                + (memberGrowth * 0.20)
                + (modActivity * 0.15)
                + (retention   * 0.10);

        c.setHealthScore(round2(composite));
        c.setHealthScoreUpdatedAt(new Date());
        c.recalculateHealthTier();
    }

    private double calcPostFreqScore(Community c) {
        int posts = c.getPostsLast7d() != null ? c.getPostsLast7d() : 0;
        return cap100(100.0 * posts / Constant.COMMUNITY_HEALTH_IDEAL_POSTS_PER_WEEK);
    }

    private double calcEngagementScore(Community c) {
        int posts    = c.getPostCount() != null && c.getPostCount() > 0 ? c.getPostCount() : 1;
        int comments = c.getTotalCommentCount() != null ? c.getTotalCommentCount() : 0;
        int likes    = c.getTotalLikeCount()    != null ? c.getTotalLikeCount()    : 0;
        double avgPerPost = (double) (comments + likes) / posts;
        return cap100(100.0 * avgPerPost / Constant.COMMUNITY_HEALTH_IDEAL_ENGAGEMENT_RATIO);
    }

    private double calcMemberGrowthScore(Community c) {
        int newMembers = c.getNewMembersLast7d() != null ? c.getNewMembersLast7d() : 0;
        return cap100(100.0 * newMembers / Constant.COMMUNITY_HEALTH_IDEAL_NEW_MEMBERS);
    }

    private double calcModActivityScore(Community c) {
        if (c.getLastActiveAt() == null) return 0.0;
        long daysSince = (System.currentTimeMillis() - c.getLastActiveAt().getTime()) / (1000 * 60 * 60 * 24);
        if (daysSince <= 0)           return 100.0;
        if (daysSince >= Constant.COMMUNITY_HEALTH_DORMANT_DAYS) return 0.0;
        return cap100(100.0 * (Constant.COMMUNITY_HEALTH_DORMANT_DAYS - daysSince) / Constant.COMMUNITY_HEALTH_DORMANT_DAYS);
    }

    private double calcRetentionScore(Community c) {
        int total  = c.getMemberCount()       != null && c.getMemberCount()       > 0 ? c.getMemberCount()       : 1;
        int active = c.getActivePostersLast7d() != null ? c.getActivePostersLast7d() : 0;
        double ratio = (double) active / total;
        return cap100(100.0 * ratio / Constant.COMMUNITY_HEALTH_IDEAL_RETENTION_RATIO);
    }

    // ── Insight helpers ───────────────────────────────────────────────────────

    private String buildSuggestion(Community c) {
        // Find the weakest component and give a targeted tip
        double postFreq    = c.getHealthPostFreqScore()     != null ? c.getHealthPostFreqScore()     : 0;
        double engagement  = c.getHealthEngagementScore()   != null ? c.getHealthEngagementScore()   : 0;
        double memberGrowth = c.getHealthMemberGrowthScore() != null ? c.getHealthMemberGrowthScore() : 0;
        double modActivity = c.getHealthModActivityScore()  != null ? c.getHealthModActivityScore()  : 0;
        double retention   = c.getHealthRetentionScore()    != null ? c.getHealthRetentionScore()    : 0;

        double min = Math.min(postFreq, Math.min(engagement, Math.min(memberGrowth, Math.min(modActivity, retention))));

        if (min == postFreq)     return "📝 Post more often — aim for at least 2 posts/day to keep the community active.";
        if (min == engagement)   return "💬 Encourage members to comment and react — ask questions in posts to spark discussion.";
        if (min == memberGrowth) return "👥 Grow your community — share your community link or invite friends from your area.";
        if (min == modActivity)  return "⚡ Stay active as a moderator — recent admin activity signals a healthy, managed community.";
        return "🔁 Improve retention — pin an introductory post and welcome new members to keep them engaged.";
    }

    private String buildTrend(Community c) {
        if (c.getPostsLast7d() == null) return "→";
        int weekly  = c.getPostsLast7d();
        int overall = c.getPostCount() != null ? c.getPostCount() : 0;
        // If weekly posts > 20% of total lifetime posts, the community is growing
        if (overall > 0 && (double) weekly / overall > 0.20) return "↑";
        if (weekly == 0) return "↓";
        return "→";
    }

    private String deriveFeedReachLevel(Community c) {
        // Based on the community's average engagement per post
        int posts = c.getPostCount() != null && c.getPostCount() > 0 ? c.getPostCount() : 1;
        int total = (c.getTotalLikeCount() != null ? c.getTotalLikeCount() : 0)
                + (c.getTotalCommentCount() != null ? c.getTotalCommentCount() : 0);
        double avgScore = (double) total / posts;

        // FIX BUG-17: use Constant.COMMUNITY_THRESHOLD_* instead of CommunityService.THRESHOLD_*
        if      (avgScore >= Constant.COMMUNITY_THRESHOLD_NATIONAL)  return "NATIONAL";
        else if (avgScore >= Constant.COMMUNITY_THRESHOLD_STATE)      return "STATE";
        else if (avgScore >= Constant.COMMUNITY_THRESHOLD_DISTRICT)   return "DISTRICT";
        else if (avgScore >= Constant.COMMUNITY_THRESHOLD_LOCAL)      return "LOCAL";
        else return "COMMUNITY_ONLY";
    }

    private double cap100(double v) { return Math.min(100.0, Math.max(0.0, v)); }
    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}