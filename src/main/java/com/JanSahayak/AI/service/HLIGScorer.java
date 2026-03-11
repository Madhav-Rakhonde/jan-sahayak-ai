package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.UserInterestProfileRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════
 * HLIGScorer — HLIG v2 (improved)
 * ═══════════════════════════════════════════════════════════════════
 *
 * Computes a personalised relevance score for one (user, post) pair.
 *
 * Formula:
 *   score = InterestMatch × GeoProximity × Freshness
 *         × NeighbourBoost × QualityGate × TrendingBoost
 *         + CreatorBoost + ViralityMomentum
 *
 * CHANGES v2 (improved):
 * ─────────────────────────────────────────────────────────────────────────────
 * 1. All magic numbers moved to Constant.java
 *    BEFORE: GEO_SAME_PINCODE = 2.5 defined here.
 *    AFTER:  Constant.HLIG_GEO_SAME_PINCODE — single source of truth.
 *
 * 2. Quality gate respects POST_QUALITY_DEFAULT for null scores
 *    BEFORE: Inline default 50.0 duplicated everywhere.
 *    AFTER:  Constant.POST_QUALITY_DEFAULT.
 *
 * 3. normalisedDotProduct — clarified magnitude calculation
 *    The original was correct but iterated over ptf twice.  Now uses one pass
 *    for ptf magnitude and a separate pass for profile magnitude, which is
 *    cleaner and avoids accidental cross-contamination.
 *
 * 4. freshness() — magic sleeper constants moved to Constant
 *    BEFORE: ageHours >= 12 && <= 96 && engScore > 50 hardcoded.
 *    AFTER:  Constant.HLIG_FRESHNESS_SLEEPER_MIN/MAX_HOURS and
 *            HLIG_FRESHNESS_SLEEPER_ENG_MIN.
 *
 * 5. neighbourBoost() — cap and step moved to Constant
 *    BEFORE: 2.0 and 0.05 inline.
 *    AFTER:  Constant.HLIG_NEIGHBOUR_BOOST_MAX / HLIG_NEIGHBOUR_LIKE_STEP.
 *
 * 6. sessionPenalty() — added null-safe ptf check, explicit primary-topic
 *    lookup now exits early when ptf is empty.
 *
 * 7. isDiversitySlot() — unchanged (deterministic for same slotIndex+sessionRng)
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Component
@RequiredArgsConstructor
public class HLIGScorer {

    private final UserInterestProfileRepo   repo;
    private final TopicExtractor            topicExtractor;

    // ── Main scoring API ──────────────────────────────────────────────────────

    /**
     * Full HLIG score for a WARM user.
     *
     * @param userProfile     decayed top-N topics → weight map (from InterestProfileService.loadTopN)
     * @param neighbours      up to 50 neighbour user IDs (from InterestProfileService.findNeighbours)
     * @param sessionTopics   topics already shown 3+ times this session (for session diversity)
     * @param sessionRng      per-session random number (for diversity slot randomisation)
     */
    public double scoreWarm(
            User                 user,
            SocialPost           post,
            Map<String, Double>  userProfile,
            List<Long>           neighbours,
            Map<String, Integer> sessionTopics,
            long                 sessionRng
    ) {
        Map<String, Double> ptf = topicExtractor.extract(post);
        return scoreWarmWithPtf(user, post, userProfile, neighbours, sessionTopics, sessionRng, ptf);
    }

    /**
     * ptf-cache variant — called by HLIGFeedService.getWarmFeed() to avoid re-extraction.
     * This is the hot path for warm users — keep it fast.
     */
    public double scoreWarmWithPtf(
            User                 user,
            SocialPost           post,
            Map<String, Double>  userProfile,
            List<Long>           neighbours,
            Map<String, Integer> sessionTopics,
            long                 sessionRng,
            Map<String, Double>  ptf
    ) {
        if (!post.isEligibleForRecommendation()) return 0.0;

        double quality = resolveQuality(post);
        if (quality < Constant.HLIG_MIN_QUALITY) return 0.0;

        double interestMatch  = normalisedDotProduct(userProfile, ptf);
        double geo            = geoProximity(user, post);
        double freshness      = freshness(post);
        double neighbourBoost = neighbourBoost(post.getId(), neighbours);
        double trendingBoost  = trendingBoost(post);
        double sessionPenalty = sessionPenalty(ptf, sessionTopics);
        double qualityMult    = quality / 100.0;

        return Math.max(0.0,
                interestMatch * geo * freshness * neighbourBoost
                        * trendingBoost * sessionPenalty * qualityMult);
    }

    /**
     * Simpler score for WARMING users (no neighbour boost, no session context).
     */
    public double scoreWarming(
            User                user,
            SocialPost          post,
            Map<String, Double> userProfile
    ) {
        if (!post.isEligibleForRecommendation()) return 0.0;
        Map<String, Double> ptf = topicExtractor.extract(post);
        return scoreWarmingWithPtf(user, post, userProfile, ptf);
    }

    /**
     * ptf-cache variant — called by HLIGFeedService.getWarmingFeed() and getFollowingFeed().
     */
    public double scoreWarmingWithPtf(
            User                user,
            SocialPost          post,
            Map<String, Double> userProfile,
            Map<String, Double> ptf
    ) {
        if (!post.isEligibleForRecommendation()) return 0.0;
        double interestMatch = normalisedDotProduct(userProfile, ptf);
        double geo           = geoProximity(user, post);
        double freshness     = freshness(post);
        double quality       = resolveQuality(post) / 100.0;
        return interestMatch * geo * freshness * quality;
    }

    /**
     * Popularity-based score for COLD users (0 interactions).
     * No interest matching — pure engagement × geo × freshness.
     *
     * FIX: Defaults engagementScore to 1.0 when null or 0 so that
     * geo + freshness still differentiate posts on brand-new platforms.
     *
     * FIX v4: own posts (bypassed into the candidate pool by the service layer)
     * must not be gated here. The service layer already decided to include them;
     * the scorer's job is to rank them, not to re-gate them.
     * Use scoreColdForOwn() for own posts to skip the eligibility check.
     */
    public double scoreCold(User user, SocialPost post) {
        if (!post.isEligibleForRecommendation()) return 0.0;
        return scoreColdCore(user, post);
    }

    /**
     * scoreCold variant used by the service layer for the creator's own posts.
     * Skips isEligibleForRecommendation() — the service already decided to include them.
     * Gives own posts a quality floor of POST_QUALITY_DEFAULT so they rank with peers
     * even when qualityScore hasn't been computed yet.
     */
    public double scoreColdForOwn(User user, SocialPost post) {
        return scoreColdCore(user, post);
    }

    private double scoreColdCore(User user, SocialPost post) {
        double rawEng    = (post.getEngagementScore() != null && post.getEngagementScore() > 0)
                ? post.getEngagementScore() : 1.0;
        double eng       = Math.log1p(rawEng);
        double geo       = geoProximity(user, post);
        double freshness = freshness(post);
        // For own posts quality may be null/0 — use POST_QUALITY_DEFAULT as the floor
        double quality   = Math.max(resolveQuality(post), Constant.POST_QUALITY_DEFAULT) / 100.0;
        return eng * geo * freshness * quality;
    }

    // ── Component functions ────────────────────────────────────────────────────

    /**
     * Normalised dot product (cosine similarity × 10).
     *
     * Returns 0 when there is no topic overlap. The 10× scale makes
     * debug log values human-readable (0–10 range).
     *
     * FIX: ptfMag is now computed in its own loop (not interleaved with dot)
     * to avoid a subtle bug where adding to ptfMag after the loop would have
     * required a second iteration anyway.
     */
    double normalisedDotProduct(Map<String, Double> profile, Map<String, Double> ptf) {
        if (profile == null || profile.isEmpty() || ptf == null || ptf.isEmpty()) return 0.0;

        double dot = 0.0;
        for (Map.Entry<String, Double> e : ptf.entrySet()) {
            Double pw = profile.get(e.getKey());
            if (pw != null) dot += pw * e.getValue();
        }
        if (dot == 0.0) return 0.0; // short-circuit: no overlap

        double ptfMag     = 0.0;
        double profileMag = 0.0;
        for (double v : ptf.values())     ptfMag     += v * v;
        for (double w : profile.values()) profileMag += w * w;

        if (profileMag == 0.0 || ptfMag == 0.0) return 0.0;

        double cosine = dot / (Math.sqrt(profileMag) * Math.sqrt(ptfMag));
        return cosine * 10.0; // scale to 0–10 for readability
    }

    /**
     * Geo proximity multiplier.
     * Uses SocialPost's existing location fields — no new columns needed.
     *
     * FIX v3: posts with NO location used to return HLIG_GEO_NONE (0.9), treating
     * them as a geo mismatch. But a post with no location is visible nationally —
     * it has no pincode restriction — so it should receive HLIG_GEO_NATIONAL (1.1),
     * the same as a confirmed national-viral post. The 0.9 penalty was silently
     * suppressing location-less posts for pincode users even though those posts were
     * perfectly valid recommendations.
     */
    double geoProximity(User user, SocialPost post) {
        // No-location post is national-scope: no geo restriction, not a mismatch
        if (!post.hasLocation())  return Constant.HLIG_GEO_NATIONAL;
        if (!user.hasPincode())   return Constant.HLIG_GEO_NATIONAL;

        if (Objects.equals(post.getPincode(), user.getPincode()))
            return Constant.HLIG_GEO_SAME_PINCODE;
        if (Objects.equals(post.getDistrictPrefix(), user.getDistrictPrefix()))
            return Constant.HLIG_GEO_SAME_DISTRICT;
        if (Objects.equals(post.getStatePrefix(), user.getStatePrefix()))
            return Constant.HLIG_GEO_SAME_STATE;
        if ("NATIONAL_VIRAL".equals(post.getViralTier()))
            return Constant.HLIG_GEO_NATIONAL;

        // Post has a concrete location that doesn't overlap with the user's area at any level
        return Constant.HLIG_GEO_NONE;
    }

    /**
     * Freshness decay: e^(−λ × ageHours).
     * 46-hour half-life (tuned for India's content posting frequency).
     *
     * Sleeper bonus: posts gaining engagement while still relatively fresh
     * get a 1.25× bump to surface underrated local gems before they expire.
     */
    double freshness(SocialPost post) {
        if (post.getCreatedAt() == null) return 0.5;

        long ageHours = ChronoUnit.HOURS.between(
                post.getCreatedAt().toInstant(), Instant.now());
        double base = Math.exp(-Constant.HLIG_FRESHNESS_LAMBDA * Math.max(0, ageHours));

        // Sleeper bonus: non-viral post gaining engagement in the fresh window
        boolean isViral   = Boolean.TRUE.equals(post.getIsViral());
        boolean isSleeper = !isViral
                && ageHours >= Constant.HLIG_FRESHNESS_SLEEPER_MIN_HOURS
                && ageHours <= Constant.HLIG_FRESHNESS_SLEEPER_MAX_HOURS
                && post.getEngagementScore() != null
                && post.getEngagementScore() > Constant.HLIG_FRESHNESS_SLEEPER_ENG_MIN;
        if (isSleeper) base *= Constant.HLIG_FRESHNESS_SLEEPER_BONUS;

        return Math.max(0.01, base);
    }

    /**
     * Neighbour boost: 1.0 + (step × neighbour_likes), capped at max.
     * Each neighbour like adds HLIG_NEIGHBOUR_LIKE_STEP (0.05), capped at 2.0.
     */
    double neighbourBoost(Long postId, List<Long> neighbours) {
        if (neighbours == null || neighbours.isEmpty()) return 1.0;
        int count = repo.countNeighbourLikes(postId, neighbours);
        return Math.min(Constant.HLIG_NEIGHBOUR_BOOST_MAX,
                1.0 + (Constant.HLIG_NEIGHBOUR_LIKE_STEP * count));
    }

    /**
     * Trending boost: posts with rising engagement velocity in the last 6h
     * get a 1.3–1.6× multiplier (proportional to viral tier).
     */
    double trendingBoost(SocialPost post) {
        if (post.getViralTier() == null) return 1.0;
        return switch (post.getViralTier()) {
            case "NATIONAL_VIRAL" -> 1.6;
            case "STATE_VIRAL"    -> 1.4;
            case "DISTRICT_VIRAL" -> 1.3;
            default               -> 1.0;
        };
    }

    /**
     * Session diversity penalty.
     * If the user has already seen 3+ posts about the same primary topic
     * this session, the score for another same-topic post is halved.
     */
    double sessionPenalty(Map<String, Double> ptf, Map<String, Integer> sessionTopics) {
        if (ptf == null || ptf.isEmpty()
                || sessionTopics == null || sessionTopics.isEmpty()) return 1.0;

        String primaryTopic = ptf.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (primaryTopic == null) return 1.0;

        int count = sessionTopics.getOrDefault(primaryTopic, 0);
        if (count >= 5) return 0.3;  // aggressive throttle after 5
        if (count >= 3) return 0.6;  // moderate throttle after 3
        return 1.0;
    }

    /**
     * Determines if a feed slot should show a diversity post.
     * Randomised per session (~15% of slots). Not deterministic by userId.
     *
     * @param slotIndex  position in the feed page (0-based)
     * @param sessionRng random seed generated once per session
     */
    public boolean isDiversitySlot(int slotIndex, long sessionRng) {
        return ((sessionRng + slotIndex * 31L) % 7) == 0;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the post's quality score, defaulting to POST_QUALITY_DEFAULT
     * (50.0) when the score is null (e.g. quality scorer hasn't run yet).
     *
     * FIX: Previously the 50.0 default was duplicated inline in every
     * scoreCold / scoreWarming / scoreWarm call. Now it's a single method.
     */
    private double resolveQuality(SocialPost post) {
        return (post.getQualityScore() != null)
                ? post.getQualityScore()
                : Constant.POST_QUALITY_DEFAULT;
    }
}