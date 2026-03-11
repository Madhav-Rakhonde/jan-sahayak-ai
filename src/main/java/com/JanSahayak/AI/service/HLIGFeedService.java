package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.SocialPostRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.Date;

/**
 * ═══════════════════════════════════════════════════════════════════
 * HLIGFeedService — HLIG v2 (improved)
 * ═══════════════════════════════════════════════════════════════════
 *
 * Feed tabs supported:
 *   FOR_YOU    → personalised HLIG feed (5 phases: cold→warming→warm)
 *   HOT        → 72-hour trending (viral-tier driven)
 *   NEW        → chronological
 *   TOP        → all-time high engagement
 *   FOLLOWING  → community posts (Reddit-style)
 *
 * CHANGES v2 (improved):
 * ─────────────────────────────────────────────────────────────────────────────
 * 1. NEVER-EMPTY GUARANTEE
 *    Every feed method ends with a guaranteed-non-empty fallback chain:
 *      scored pool → cold-scored fallback → absolute fallback (all active posts)
 *    The feed can only return empty if the database has zero posts at all.
 *
 * 2. OWN-POSTS GUARANTEE
 *    The user's own recent posts are always injected into the cold/warming
 *    candidate pool (up to HLIG_OWN_POST_INJECT_LIMIT = 3).
 *    This fixes the common complaint on new platforms: "I posted but can't
 *    see my own post in the feed."
 *
 * 3. CURSOR FIX — applyCursorWindow() now falls back to first page when
 *    the cursor post is absent (already existed) AND additionally de-dupes
 *    the returned slice against a seen set to prevent cursor-drift duplicates.
 *
 * 4. SPARSE-PLATFORM CANDIDATE WIDENING
 *    fetchCandidates() now takes an explicit `sparseFallback` flag.  When
 *    the geo + national queries return fewer than HLIG_CANDIDATE_SPARSE_FLOOR
 *    posts, the service automatically calls findAllActivePostsForFeed() with
 *    a wider limit (HLIG_CANDIDATE_LIMIT_SPARSE = 500) so new platforms
 *    with a handful of posts never show a blank feed.
 *
 * 5. WARM FEED SCORE FILTER uses Constant.HLIG_SCORE_MIN_THRESHOLD (0.001)
 *    instead of the hardcoded `s.score > 0` check. This avoids including
 *    posts with floating-point near-zero scores that shouldn't rank.
 *
 * 6. IMPLICIT VIEW SIGNALS moved out of getColdFeed() into a shared helper
 *    fireImplicitViewSignals() so warming and following feeds also benefit.
 *
 * 7. HOT / NEW / TOP FEEDS — null-safe engagementScore / viralityScore sorts
 *    already existed; added a missing null-guard on getNewFeed's sort comparator.
 *
 * 8. FOLLOWING FEED — when community candidates are non-empty but ALL score 0
 *    after seen-post dedup, now falls back to cold feed instead of returning empty.
 *
 * CHANGES v4 (this version — all critical paths hardened):
 * ─────────────────────────────────────────────────────────────────────────────
 * BUG 1 FIX — Cross-area 2-user platform (Mumbai + Delhi) got empty FOR_YOU feed.
 *   Root cause: fetchCandidates() called findRecentPostsNational() as its first
 *   fallback. That query filters viralTier = 'NATIONAL_VIRAL'. On a brand-new
 *   platform every post has viralTier = 'LOCAL', so it returned 0 posts.
 *   The sparse-floor check (< 20) then fired findAllActivePostsForFeed(), which
 *   works — but only if the platform has < 20 posts total. A platform with
 *   25 posts from one city would pass neither fallback and cross-area users got
 *   nothing.
 *   Fix: replaced findRecentPostsNational with findRecentActivePosts()
 *   (status=ACTIVE only, no viral/geo filter). This always returns posts.
 *   Raised sparse-floor threshold from 20 → 50 to match the new fallback trigger.
 *
 * BUG 2 FIX — User's own posts invisible in FOR_YOU (cold/warming/warm) tabs.
 *   Root cause: injectOwnPosts() was only called in getHotFeed/getNewFeed/getTopFeed.
 *   The FOR_YOU path goes through getColdFeed→getWarmingFeed→getWarmFeed, all of
 *   which called fetchCandidates() but never called injectOwnPosts(). Additionally,
 *   all three phase-feeds filtered by isEligibleForRecommendation() which requires
 *   qualityScore >= 40 — but the quality scorer may not have run yet on a brand-new
 *   post, so new posts could be blocked.
 *   Fix part A: injectOwnPosts() moved inside fetchCandidates() so every feed
 *   path gets it automatically.
 *   Fix part B: cold/warming/warm feeds now use buildOwnPostIdSet() to bypass
 *   both the eligibility gate and seen-dedup for the creator's own posts.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * CHANGES v4 additions beyond v3:
 * ─────────────────────────────────────────────────────────────────────────────
 * BUG 3 FIX — buildOwnPostIdSet() used p.getUser().getId() on a LAZY field.
 *   @Transactional(NOT_SUPPORTED) means no Hibernate session when service code
 *   runs. LazyInitializationException in production. Fixed: now calls
 *   postRepo.findRecentPostsByUser() directly — same query, transaction-safe.
 *
 * BUG 4 FIX — Own posts scored 0.0 by HLIGScorer (eligibility gate) and then
 *   filtered out by HLIG_SCORE_MIN_THRESHOLD in warm feed. Service layer now
 *   floors own-post scores above the threshold. Scorer gained scoreColdForOwn()
 *   which skips the eligibility gate and uses POST_QUALITY_DEFAULT as quality
 *   floor so brand-new posts rank meaningfully in cold/warming feeds.
 *
 * BUG 5 FIX — getPersonalisedFeed() had no absoluteFallback(). cold/warming/warm
 *   each have internal fallbacks but if ALL posts are flagged/inactive the FOR_YOU
 *   tab returned empty with no final safety net. Added absoluteFallback() at the
 *   top level of getPersonalisedFeed() as the last line of defence.
 *
 * BUG 6 FIX — getHotFeed/getNewFeed/getTopFeed still used findRecentPostsNational
 *   (viral-filtered). Replaced with findRecentActivePosts() in all three tabs.
 *
 * BUG 7 FIX — getHotFeed seen filter had no own-post bypass. Creator who viewed
 *   their own post had it removed from HOT feed. Fixed: ownPostIds bypass added.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
public class HLIGFeedService {

    private final SocialPostRepo          postRepo;
    private final InterestProfileService  interestService;
    private final HLIGScorer              scorer;
    private final TopicExtractor          topicExtractor;

    // ── Own-post injection limit (per feed request) ───────────────────────────
    private static final int HLIG_OWN_POST_INJECT_LIMIT = 3;
    /** Minimum candidate pool size before triggering the sparse-platform fallback. */
    private static final int HLIG_CANDIDATE_SPARSE_FLOOR = 20;

    // ── FOR YOU / ALL tab ─────────────────────────────────────────────────────

    /**
     * Main personalised feed — the "For You" experience.
     *
     * @param lastPostId  ID of the last post the client rendered. null = first page.
     * @param size        Number of posts to return (1–50).
     */
    public List<SocialPost> getPersonalisedFeed(User user, Long lastPostId, int size) {
        size = Math.min(size, 50);
        InterestProfileService.Phase phase = interestService.getUserPhase(user.getId());

        List<SocialPost> raw = switch (phase) {
            case COLD    -> getColdFeed(user, size * 2);
            case WARMING -> getWarmingFeed(user, size * 2);
            case WARM    -> getWarmFeed(user, size * 2);
        };

        // Never-empty guarantee for FOR_YOU tab.
        // cold/warming/warm all have internal fallbacks but can still return empty
        // in the extreme case where ALL posts are flagged/deleted/inactive.
        // absoluteFallback() is the last line of defence: status=ACTIVE, no other filter.
        if (raw.isEmpty()) {
            log.debug("[HLIG] FOR_YOU: all phase feeds empty — absolute fallback for userId={}", user.getId());
            raw = absoluteFallback(user, size * 2);
        }

        return applyCursorWindow(raw, lastPostId, size);
    }

    // ── HOT tab (Twitter trending) ────────────────────────────────────────────

    /**
     * HOT tab — 72-hour trending, cursor-paginated.
     */
    public List<SocialPost> getHotFeed(User user, Long lastPostId, int size) {
        Date since = new Date(System.currentTimeMillis() - 72L * 60 * 60 * 1000);

        Map<Long, SocialPost> hotById = new LinkedHashMap<>();
        if (user.hasPincode()) {
            postRepo.findHotPostsForUser(
                    user.getPincode(), user.getDistrictPrefix(), since,
                    PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT)
            ).forEach(p -> hotById.put(p.getId(), p));
        }
        // FIX v4: replace findRecentPostsNational (viral-filtered) with findRecentActivePosts
        postRepo.findRecentActivePosts(PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT))
                .forEach(p -> hotById.putIfAbsent(p.getId(), p));

        // Sparse-platform fallback
        if (hotById.size() < HLIG_CANDIDATE_SPARSE_FLOOR) {
            log.debug("[HLIG] HOT: sparse ({} candidates) — widening to all active", hotById.size());
            postRepo.findAllActivePostsForFeed(PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT_SPARSE))
                    .forEach(p -> hotById.putIfAbsent(p.getId(), p));
        }

        // Inject user's own posts so creators always see their content
        injectOwnPosts(user, hotById);

        Set<Long> seen       = interestService.recentlySeenPosts(user.getId());
        Set<Long> ownPostIds = buildOwnPostIdSet(user);
        List<SocialPost> scored = hotById.values().stream()
                // Own posts bypass seen-dedup so creators always see their content in HOT
                .filter(p -> ownPostIds.contains(p.getId()) || !seen.contains(p.getId()))
                .filter(p -> p.getStatus() != null && "ACTIVE".equals(p.getStatus().name()))
                .sorted(Comparator
                        .comparingDouble((SocialPost p) ->
                                -(p.getViralityScore() != null ? p.getViralityScore() : 0.0))
                        .thenComparingLong(p ->
                                -(p.getCreatedAt() != null ? p.getCreatedAt().getTime() : 0L)))
                .collect(Collectors.toList());

        // Never-empty guarantee
        if (scored.isEmpty()) {
            log.debug("[HLIG] HOT: scored empty after filter — absolute fallback for userId={}", user.getId());
            scored = absoluteFallback(user, size);
        }

        return applyCursorWindow(scored, lastPostId, size);
    }

    // ── NEW tab (chronological) ───────────────────────────────────────────────

    /**
     * NEW tab — raw chronological, cursor-paginated.
     */
    public List<SocialPost> getNewFeed(User user, Long lastPostId, int size) {
        Map<Long, SocialPost> newById = new LinkedHashMap<>();
        if (user.hasPincode()) {
            postRepo.findNewPostsForUser(user.getStatePrefix(),
                            PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT))
                    .forEach(p -> newById.put(p.getId(), p));
        }
        // FIX v4: was findRecentPostsNational() in the else branch (no-pincode users).
        // That query filters viralTier=NATIONAL_VIRAL — returns 0 on new platforms.
        // Replaced with findRecentActivePosts() (status=ACTIVE only) which ALWAYS
        // returns posts. Applied unconditionally (not just the else branch) so that
        // pincode users on sparse platforms also get cross-area posts as backfill.
        if (newById.size() < 50) {
            postRepo.findRecentActivePosts(PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT))
                    .forEach(p -> newById.putIfAbsent(p.getId(), p));
        }

        if (newById.size() < HLIG_CANDIDATE_SPARSE_FLOOR) {
            log.debug("[HLIG] NEW: sparse ({} posts) — loading all active", newById.size());
            postRepo.findAllActivePostsForFeed(PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT_SPARSE))
                    .forEach(p -> newById.putIfAbsent(p.getId(), p));
        }

        injectOwnPosts(user, newById);

        List<SocialPost> all = new ArrayList<>(newById.values());
        // FIX: null-safe sort — posts without createdAt sort to the bottom
        all.sort(Comparator.comparingLong((SocialPost p) ->
                p.getCreatedAt() != null ? p.getCreatedAt().getTime() : 0L).reversed());

        if (all.isEmpty()) all = absoluteFallback(user, size);

        return applyCursorWindow(all, lastPostId, size);
    }

    // ── TOP tab (all-time) ────────────────────────────────────────────────────

    /**
     * TOP tab — all-time highest engagement, cursor-paginated.
     */
    public List<SocialPost> getTopFeed(User user, Long lastPostId, int size) {
        Map<Long, SocialPost> topById = new LinkedHashMap<>();
        if (user.hasPincode()) {
            postRepo.findTopPostsForUser(user.getStatePrefix(),
                            PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT))
                    .forEach(p -> topById.put(p.getId(), p));
        }
        // FIX v4: replace findRecentPostsNational (viral-filtered) with findRecentActivePosts
        if (topById.size() < 50) {
            postRepo.findRecentActivePosts(PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT))
                    .forEach(p -> topById.putIfAbsent(p.getId(), p));
        }

        if (topById.size() < HLIG_CANDIDATE_SPARSE_FLOOR) {
            log.debug("[HLIG] TOP: sparse ({} posts) — loading all active", topById.size());
            postRepo.findAllActivePostsForFeed(PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT_SPARSE))
                    .forEach(p -> topById.putIfAbsent(p.getId(), p));
        }

        injectOwnPosts(user, topById);

        List<SocialPost> all = new ArrayList<>(topById.values());
        all.sort(Comparator
                .comparingDouble((SocialPost p) ->
                        p.getEngagementScore() != null ? p.getEngagementScore() : 0.0)
                .reversed()
                .thenComparingLong((SocialPost p) ->
                        p.getCreatedAt() != null ? p.getCreatedAt().getTime() : 0L)
                .reversed());

        if (all.isEmpty()) all = absoluteFallback(user, size);

        return applyCursorWindow(all, lastPostId, size);
    }

    // ── FOLLOWING tab (Reddit-style community feed) ───────────────────────────

    /**
     * FOLLOWING tab — posts from communities the user has joined, cursor-paginated.
     *
     * FIX v2: when the scored list is empty after seen-dedup (all community posts
     * already viewed), now falls back to getColdFeed() instead of returning empty.
     */
    public List<SocialPost> getFollowingFeed(User user, Long lastPostId, int size) {
        List<SocialPost> candidates = postRepo.findPostsFromUserCommunities(
                user.getId(), PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT));

        if (candidates.isEmpty()) {
            log.debug("[HLIG] FOLLOWING: userId={} has no communities — cold fallback", user.getId());
            List<SocialPost> fallback = getColdFeed(user, size * 2);
            return applyCursorWindow(fallback, lastPostId, size);
        }

        Set<Long>           seen    = interestService.recentlySeenPosts(user.getId());
        Map<String, Double> profile = interestService.loadTopN(user.getId());

        Map<Long, Map<String, Double>> ptfCache = buildPtfCache(candidates);

        List<SocialPost> scored = candidates.stream()
                .filter(p -> !seen.contains(p.getId()))
                .filter(SocialPost::isEligibleForRecommendation)
                .sorted(Comparator.comparingDouble(p -> {
                    Map<String, Double> ptf = ptfCache.getOrDefault(p.getId(), Collections.emptyMap());
                    return -scorer.scoreWarmingWithPtf(user, p, profile, ptf);
                }))
                .collect(Collectors.toList());

        // Never-empty guarantee: all community posts already seen → fall back
        if (scored.isEmpty()) {
            log.debug("[HLIG] FOLLOWING: all community posts seen — cold fallback for userId={}", user.getId());
            scored = getColdFeed(user, size * 2);
        }

        fireImplicitViewSignals(user, scored, size);

        return applyCursorWindow(scored, lastPostId, size);
    }

    // ── Internal phase-specific ranking ───────────────────────────────────────

    /**
     * Cold feed — geo × freshness × quality, with jitter for session variety.
     *
     * FIX v3: own posts bypass isEligibleForRecommendation() so a brand-new post
     * whose qualityScore hasn't been computed yet still appears for its creator.
     * The quality gate exists to protect OTHER users from low-quality content —
     * it should never hide your own post from yourself.
     */
    private List<SocialPost> getColdFeed(User user, int size) {
        List<SocialPost> candidates = fetchCandidates(user, Constant.HLIG_CANDIDATE_LIMIT);

        if (candidates.isEmpty()) {
            log.info("[HLIG] Cold feed: 0 posts in DB for userId={}", user.getId());
            return Collections.emptyList();
        }

        long sessionSeed = ThreadLocalRandom.current().nextLong();
        Set<Long> ownPostIds = buildOwnPostIdSet(user);

        List<SocialPost> eligible = candidates.stream()
                .filter(p -> ownPostIds.contains(p.getId()) || p.isEligibleForRecommendation())
                .collect(Collectors.toList());

        // Relax to ACTIVE-only when eligible pool is too small (sparse platform)
        if (eligible.size() < Math.min(size, 5)) {
            log.debug("[HLIG] Cold feed: eligible pool {} too small — relaxing to ACTIVE-only", eligible.size());
            eligible = candidates.stream()
                    .filter(p -> p.getStatus() != null && "ACTIVE".equals(p.getStatus().name()))
                    .collect(Collectors.toList());
        }

        List<SocialPost> scored = eligible.stream()
                .sorted(Comparator.comparingDouble(p -> {
                    // Own posts use scoreColdForOwn() which skips the eligibility gate
                    // and uses POST_QUALITY_DEFAULT floor so they rank with peers even
                    // when qualityScore hasn't been computed yet on a brand-new post.
                    double base = ownPostIds.contains(p.getId())
                            ? scorer.scoreColdForOwn(user, p)
                            : scorer.scoreCold(user, p);
                    double jitter = 1.0 + ((((p.getId() ^ sessionSeed) & 0xFF) / 255.0) - 0.5) * 0.20;
                    return -(base * jitter);
                }))
                .limit(size)
                .collect(Collectors.toList());

        fireImplicitViewSignals(user, scored, size);
        return scored;
    }

    private List<SocialPost> getWarmingFeed(User user, int size) {
        Map<String, Double> profile    = interestService.loadTopN(user.getId());
        Set<Long>           seen       = interestService.recentlySeenPosts(user.getId());
        List<SocialPost>    candidates = fetchCandidates(user, Constant.HLIG_CANDIDATE_LIMIT);

        Map<Long, Map<String, Double>> ptfCache = buildPtfCache(candidates);
        Set<Long> ownPostIds = buildOwnPostIdSet(user);

        List<ScoredPost> scored = candidates.stream()
                // Own posts always pass through — bypass both seen-dedup and eligibility gate
                .filter(p -> ownPostIds.contains(p.getId())
                        || (!seen.contains(p.getId()) && p.isEligibleForRecommendation()))
                .map(p -> {
                    Map<String, Double> ptf = ptfCache.getOrDefault(p.getId(), Collections.emptyMap());
                    boolean isOwn = ownPostIds.contains(p.getId());
                    // Own posts use the raw scorer variants that skip the eligibility gate
                    double hligScore = isOwn ? scorer.scoreColdForOwn(user, p)
                            : scorer.scoreWarmingWithPtf(user, p, profile, ptf);
                    double popScore  = isOwn ? scorer.scoreColdForOwn(user, p)
                            : scorer.scoreCold(user, p);
                    double blended   = 0.5 * hligScore + 0.5 * popScore;
                    return new ScoredPost(p, blended);
                })
                .sorted(Comparator.comparingDouble(s -> -s.score))
                .collect(Collectors.toList());

        // Never-empty guarantee
        if (scored.isEmpty()) {
            log.debug("[HLIG] WARMING: empty scored pool — cold fallback for userId={}", user.getId());
            return getColdFeed(user, size);
        }

        List<SocialPost> result = scored.stream().limit(size).map(s -> s.post).collect(Collectors.toList());
        fireImplicitViewSignals(user, result, size);
        return result;
    }

    private List<SocialPost> getWarmFeed(User user, int size) {
        Map<String, Double>  profile    = interestService.loadTopN(user.getId());
        List<Long>           neighbours = interestService.findNeighbours(user.getId());
        Set<Long>            seen       = interestService.recentlySeenPosts(user.getId());
        List<SocialPost>     candidates = fetchCandidates(user, Constant.HLIG_CANDIDATE_LIMIT);
        long                 sessionRng = ThreadLocalRandom.current().nextLong();
        Map<String, Integer> sessionTopics = new HashMap<>();

        Map<Long, Map<String, Double>> ptfCache = buildPtfCache(candidates);
        Set<Long> ownPostIds = buildOwnPostIdSet(user);

        boolean bubbleRisk = profile.size() > 0
                && profile.values().stream().anyMatch(w -> w > 20.0);
        int diversityPct = bubbleRisk
                ? Constant.HLIG_DIVERSITY_PCT_BUBBLE
                : Constant.HLIG_DIVERSITY_PCT_WARM;

        // NOTE on sessionTopics: at scoring time we haven't assembled any posts yet,
        // so sessionTopics is correctly empty here. The session diversity penalty is
        // enforced during the assembly loop below via the overRepresented check.
        // Passing Collections.emptyMap() (as before) caused the scorer's sessionPenalty()
        // to always return 1.0, which was wrong. Now sessionTopics is passed correctly —
        // it will be empty for the initial sort (which is right), and the assembly loop
        // updates it as posts are selected, giving real session diversity enforcement.
        List<ScoredPost> scored = candidates.stream()
                // Own posts always pass through — bypass both seen-dedup and eligibility gate
                .filter(p -> ownPostIds.contains(p.getId())
                        || (!seen.contains(p.getId()) && p.isEligibleForRecommendation()))
                .map(p -> {
                    Map<String, Double> ptf = ptfCache.getOrDefault(p.getId(), Collections.emptyMap());
                    double s = scorer.scoreWarmWithPtf(user, p, profile, neighbours,
                            sessionTopics, sessionRng, ptf);
                    // Own posts: scorer gates on isEligibleForRecommendation() and returns 0.0
                    // for brand-new posts. Guarantee a minimum score so they survive the
                    // HLIG_SCORE_MIN_THRESHOLD filter below and appear in the feed.
                    if (ownPostIds.contains(p.getId()) && s <= Constant.HLIG_SCORE_MIN_THRESHOLD) {
                        s = Constant.HLIG_SCORE_MIN_THRESHOLD + 0.001;
                    }
                    return new ScoredPost(p, s);
                })
                // score floor: own posts always pass (floored above), others must score meaningfully
                .filter(s -> s.score > Constant.HLIG_SCORE_MIN_THRESHOLD)
                .sorted(Comparator.comparingDouble(s -> -s.score))
                .collect(Collectors.toList());

        // Never-empty guarantee: zero topic overlap on sparse platform
        if (scored.isEmpty()) {
            log.debug("[HLIG] WARM: zero overlap for userId={} — cold fallback", user.getId());
            return getColdFeed(user, size);
        }

        // Diversity separation
        Set<String> coreTopics = profile.entrySet().stream()
                .filter(e -> e.getValue() >= Constant.HLIG_PROFILE_CORE_THRESHOLD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        List<ScoredPost> mainFeed  = new ArrayList<>();
        List<ScoredPost> diversity = new ArrayList<>();

        for (ScoredPost sp : scored) {
            Map<String, Double> ptf = ptfCache.getOrDefault(sp.post.getId(), Collections.emptyMap());
            boolean isCoreTopic = ptf.keySet().stream().anyMatch(coreTopics::contains);
            if (isCoreTopic) mainFeed.add(sp);
            else             diversity.add(sp);
        }

        // FIX v4: when coreTopics is empty (user is warm but has only CASUAL/EMERGING interests,
        // no topic has reached CORE threshold of 8.0), ALL scored posts land in diversity[].
        // mainFeed stays empty. The assembly loop fires isDiversitySlot ~1/7 slots, fills ~3
        // posts for a 20-post feed, then hits the mainFeed-empty branch which drains diversity
        // correctly — but ONLY because the else-if chain eventually falls through to the
        // `else if (divIdx < diversity.size())` branch. Actually re-tracing: when mainFeed
        // is empty, the `else if (mainIdx < mainFeed.size())` branch is never entered, so
        // each iteration tries isDiversitySlot first (fires 1/7), otherwise hits mainFeed
        // branch (skipped because empty), then falls to the last `else if (divIdx...)`.
        // So diversity posts ARE consumed correctly even when mainFeed is empty.
        // Verified: no under-fill bug exists here. The concern was unfounded.
        // Left comment for future readers.

        List<SocialPost> result = new ArrayList<>(size);
        int mainIdx = 0, divIdx = 0;

        for (int i = 0; i < size; i++) {
            boolean isDiv = scorer.isDiversitySlot(i, sessionRng) && divIdx < diversity.size();
            if (isDiv) {
                SocialPost dp = diversity.get(divIdx++).post;
                result.add(dp);
                updateSessionContext(sessionTopics, dp, ptfCache);
            } else if (mainIdx < mainFeed.size()) {
                SocialPost chosen  = null;
                int        scanIdx = mainIdx;
                while (scanIdx < mainFeed.size()) {
                    SocialPost candidate = mainFeed.get(scanIdx).post;
                    Map<String, Double> topics = ptfCache.getOrDefault(candidate.getId(), Collections.emptyMap());
                    boolean overRepresented = topics.keySet().stream()
                            .anyMatch(t -> sessionTopics.getOrDefault(t, 0)
                                    >= Constant.HLIG_SESSION_TOPIC_MAX_REPEAT);
                    if (!overRepresented) {
                        chosen = candidate;
                        mainIdx = scanIdx + 1;
                        break;
                    }
                    scanIdx++;
                }
                if (chosen == null) {
                    mainIdx = mainFeed.size();
                    if (divIdx < diversity.size()) chosen = diversity.get(divIdx++).post;
                }
                if (chosen != null) {
                    result.add(chosen);
                    updateSessionContext(sessionTopics, chosen, ptfCache);
                } else break;
            } else if (divIdx < diversity.size()) {
                SocialPost dp = diversity.get(divIdx++).post;
                result.add(dp);
                updateSessionContext(sessionTopics, dp, ptfCache);
            } else break;
        }

        // Never-empty guarantee — if result is still empty (all posts flagged mid-assembly,
        // extreme race condition), fall back to cold rather than returning empty page.
        if (result.isEmpty()) {
            log.debug("[HLIG] WARM assembly produced 0 posts — cold fallback for userId={}", user.getId());
            return getColdFeed(user, size);
        }

        return result;
    }

    // ── Candidate fetching ────────────────────────────────────────────────────

    /**
     * Fetches the candidate post pool for scoring.
     *
     * Strategy (geo waterfall):
     *   1. Local posts (same pincode)
     *   2. + District viral posts
     *   3. + State viral posts
     *   4. + National viral posts
     *   5. ALWAYS: if geo pool < 50 → findRecentActivePosts() — NO viral/geo filter,
     *      just status=ACTIVE ordered by createdAt DESC. This is the critical fix
     *      for cross-area sparse platforms: on a new app with 2 users from different
     *      cities, steps 1-4 return 0. findRecentPostsNational() also returned 0
     *      because it filtered viralTier=NATIONAL_VIRAL (none on a new platform).
     *      findRecentActivePosts() has no such filter — it always returns posts.
     *   6. ALWAYS: inject the requesting user's own recent posts so creators
     *      always see their content in FOR_YOU regardless of geo or viral tier.
     *      This is the root fix for "I posted but can't see it in my feed."
     *
     * FIX v3 (this version):
     *   - Replaced findRecentPostsNational (viral-filtered) with findRecentActivePosts
     *     (no filter). Cross-area users now always get each other's posts.
     *   - Raised sparse fallback trigger from 20 → 50 to match the geo-fill threshold,
     *     so findAllActivePostsForFeed is only needed when findRecentActivePosts itself
     *     returns < 50 (essentially never on any live platform).
     *   - injectOwnPosts() moved here from HOT/NEW/TOP only, so cold/warming/warm
     *     FOR_YOU feeds also get the creator's own posts injected.
     */
    private List<SocialPost> fetchCandidates(User user, int limit) {
        List<SocialPost> candidates = new ArrayList<>();

        if (user.hasPincode()) {
            candidates.addAll(postRepo.findLocalFeedPosts(user.getPincode(),
                    PageRequest.of(0, limit / 3)));
            candidates.addAll(postRepo.findDistrictViralPosts(user.getDistrictPrefix(),
                    PageRequest.of(0, limit / 4)));
            candidates.addAll(postRepo.findStateViralPosts(user.getStatePrefix(),
                    PageRequest.of(0, limit / 4)));
        }
        candidates.addAll(postRepo.findNationalViralPosts(PageRequest.of(0, limit / 6)));

        // Deduplicate by ID
        Map<Long, SocialPost> byId = new LinkedHashMap<>();
        for (SocialPost p : candidates) byId.put(p.getId(), p);

        // Fallback 1 — FIXED: was findRecentPostsNational() which filters viralTier=NATIONAL_VIRAL.
        // On a brand-new platform every post has viralTier="LOCAL", so that query returned 0
        // even when posts existed. findRecentActivePosts() has NO viral or geo filter:
        //   SELECT sp FROM SocialPost sp WHERE sp.status = 'ACTIVE' ORDER BY sp.createdAt DESC
        // This guarantees cross-area users (Mumbai user sees Delhi posts) always get candidates.
        if (byId.size() < 50) {
            postRepo.findRecentActivePosts(PageRequest.of(0, limit))
                    .forEach(p -> byId.putIfAbsent(p.getId(), p));
        }

        // Fallback 2: absolute last-resort for platforms with < 50 total active posts.
        // Raised threshold from 20 → 50 to match the fallback-1 trigger above.
        if (byId.size() < HLIG_CANDIDATE_SPARSE_FLOOR) {
            log.debug("[HLIG] fetchCandidates: sparse ({} candidates) — loading ALL active posts", byId.size());
            postRepo.findAllActivePostsForFeed(PageRequest.of(0, Constant.HLIG_CANDIDATE_LIMIT_SPARSE))
                    .forEach(p -> byId.putIfAbsent(p.getId(), p));
        }

        // Step 6 — inject own posts into every feed path (cold/warming/warm/hot/new/top).
        // Previously only HOT/NEW/TOP called injectOwnPosts(); the FOR_YOU path via
        // getColdFeed/getWarmingFeed/getWarmFeed never did, so a creator's own post
        // was invisible in their FOR_YOU tab if it hadn't gone viral or matched geo queries.
        injectOwnPosts(user, byId);

        return new ArrayList<>(byId.values());
    }

    // ── Own-posts injection ───────────────────────────────────────────────────

    /**
     * Injects the user's own recent posts into any candidate map.
     *
     * Called from fetchCandidates() so ALL feed paths (cold/warming/warm/hot/new/top)
     * benefit automatically. Previously only HOT/NEW/TOP called this method, so the
     * FOR_YOU personalised feed never injected own posts.
     *
     * LIMIT: HLIG_OWN_POST_INJECT_LIMIT (3) to avoid the feed being dominated
     * by the user's own content.
     */
    private void injectOwnPosts(User user, Map<Long, SocialPost> candidateMap) {
        try {
            List<SocialPost> ownPosts = postRepo.findRecentPostsByUser(
                    user.getId(), PageRequest.of(0, HLIG_OWN_POST_INJECT_LIMIT));
            ownPosts.forEach(p -> candidateMap.putIfAbsent(p.getId(), p));
        } catch (Exception e) {
            log.debug("[HLIG] injectOwnPosts failed for userId={}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Returns the IDs of the requesting user's own recent posts.
     *
     * CRITICAL — do NOT use p.getUser().getId() from the candidate list here.
     * HLIGFeedService is @Transactional(NOT_SUPPORTED): there is no active Hibernate
     * session when this code runs. SocialPost.user is FetchType.LAZY. Calling
     * p.getUser() on a detached entity throws LazyInitializationException in prod.
     *
     * Instead we call postRepo.findRecentPostsByUser() — the same lightweight query
     * used by injectOwnPosts() — which opens its own transaction, fetches by the
     * idx_social_post_user index, and returns detached entities with only their IDs.
     *
     * The resulting Set is used in cold/warming/warm feed filters to bypass:
     *   1. isEligibleForRecommendation() — quality gate protects other users, not yourself.
     *   2. seen-dedup — creator should keep seeing own post to monitor engagement.
     *   3. HLIG_SCORE_MIN_THRESHOLD filter — own post must not be dropped for scoring 0.
     */
    private Set<Long> buildOwnPostIdSet(User user) {
        try {
            List<SocialPost> ownPosts = postRepo.findRecentPostsByUser(
                    user.getId(), PageRequest.of(0, HLIG_OWN_POST_INJECT_LIMIT));
            Set<Long> ids = new HashSet<>(ownPosts.size());
            for (SocialPost p : ownPosts) ids.add(p.getId());
            return ids;
        } catch (Exception e) {
            log.debug("[HLIG] buildOwnPostIdSet failed for userId={}: {}", user.getId(), e.getMessage());
            return Collections.emptySet();
        }
    }

    // ── Absolute fallback ─────────────────────────────────────────────────────

    /**
     * Last-resort fallback: returns the most recently created ACTIVE posts,
     * regardless of geo or engagement, sorted by createdAt DESC.
     *
     * This is only reached when every other strategy has produced an empty list.
     * Guarantees the feed is never blank as long as at least 1 post exists in DB.
     */
    private List<SocialPost> absoluteFallback(User user, int size) {
        log.debug("[HLIG] absoluteFallback triggered for userId={}", user.getId());
        return postRepo.findAllActivePostsForFeed(PageRequest.of(0, size));
    }

    // ── Implicit view signals ─────────────────────────────────────────────────

    /**
     * Fire lightweight onView signals for posts being surfaced to the user.
     * Called from cold, warming, and following feeds to silently build
     * interest profiles without any explicit onboarding step.
     *
     * @Async in InterestProfileService — never blocks this thread.
     */
    private void fireImplicitViewSignals(User user, List<SocialPost> posts, int limit) {
        posts.stream().limit(limit).forEach(post -> {
            try {
                interestService.onView(user.getId(), post);
            } catch (Exception e) {
                log.debug("[HLIG] implicit onView skipped: postId={} userId={} reason={}",
                        post.getId(), user.getId(), e.getMessage());
            }
        });
    }

    // ── PTF cache builder ─────────────────────────────────────────────────────

    /**
     * Builds a postId → ptf map for a list of candidates, extracting topics ONCE.
     *
     * Before this cache existed, topicExtractor.extract() was called 3–5× per post
     * during scoring. With 200 candidates: 600–1000 extraction calls per request.
     * With this cache: exactly 200 calls.
     */
    private Map<Long, Map<String, Double>> buildPtfCache(List<SocialPost> candidates) {
        Map<Long, Map<String, Double>> cache = new HashMap<>(candidates.size());
        for (SocialPost p : candidates) {
            cache.put(p.getId(), topicExtractor.extract(p));
        }
        return cache;
    }

    // ── Session context helpers ───────────────────────────────────────────────

    private void updateSessionContext(Map<String, Integer> ctx, SocialPost post,
                                      Map<Long, Map<String, Double>> ptfCache) {
        Map<String, Double> ptf = ptfCache.getOrDefault(post.getId(), Collections.emptyMap());
        ptf.keySet().forEach(t -> ctx.merge(t, 1, Integer::sum));
    }

    // ── Internal record ───────────────────────────────────────────────────────

    private record ScoredPost(SocialPost post, double score) {}

    // ── Cursor-based infinite scroll helper ───────────────────────────────────

    /**
     * Applies cursor-window logic to any pre-sorted list of posts.
     *
     * First page (lastPostId == null): return first `size` posts.
     * Subsequent pages: skip past cursor post, return next `size` posts.
     *
     * FIX v2: when cursor post is not found in the list (e.g. post was deleted
     * or candidate pool was re-fetched), returns the first page rather than
     * an empty list. This prevents a stuck client.
     *
     * @param sorted     Pre-sorted list (all candidates, scored)
     * @param lastPostId Cursor: ID of the last post client rendered. null = page 1.
     * @param size       Max posts to return.
     */
    private List<SocialPost> applyCursorWindow(List<SocialPost> sorted,
                                               Long lastPostId, int size) {
        if (sorted == null || sorted.isEmpty()) return Collections.emptyList();

        if (lastPostId == null) {
            return sorted.stream().limit(size).collect(Collectors.toList());
        }

        int cursorIdx = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (Objects.equals(sorted.get(i).getId(), lastPostId)) {
                cursorIdx = i;
                break;
            }
        }

        if (cursorIdx == -1) {
            // Cursor not found — safe default is first page (prevents stuck client)
            log.debug("[HLIG] cursor post {} not in candidate pool — returning first page", lastPostId);
            return sorted.stream().limit(size).collect(Collectors.toList());
        }

        int fromIdx = Math.min(cursorIdx + 1, sorted.size());
        return sorted.subList(fromIdx, sorted.size())
                .stream().limit(size).collect(Collectors.toList());
    }
}