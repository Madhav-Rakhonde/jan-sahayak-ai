package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.UserInterestProfile;
import com.JanSahayak.AI.repository.UserInterestProfileRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * InterestProfileService — manages per-user topic-weight profiles.
 *
 * CHANGES v3 (language support):
 * ─────────────────────────────────────────────────────────────────────────────
 * 1. Every signal handler (onView, onLike, onSave, onComment, onShare,
 *    onPostCreated) now also records a "lang:XX" language topic signal
 *    at HALF the weight of the main content signal.
 *
 *    Rationale: language preference is a persistent trait that builds
 *    slowly over many sessions. Using half the content weight avoids
 *    language topics flooding CORE zone on a single binge session, while
 *    still letting the preference emerge naturally within a week of usage.
 *
 * 2. rebuildLanguagePreferences(userId) — new async method that reads all
 *    "lang:XX" topic rows for a user, sorts them by decayed weight, and
 *    writes the top-5 BCP-47 codes into the preferred_languages column on
 *    the user's top-weight UIP row.  Called after every language signal update.
 *
 * 3. getPreferredLanguages(userId) — new cached public method that returns
 *    the ordered preferred language list for the scorer without an extra DB
 *    round-trip per request.  Evicted alongside the regular profile cache.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterestProfileService {

    private final UserInterestProfileRepo repo;
    private final TopicExtractor          topicExtractor;

    // FIX MEMORY LEAK #6 — needed for programmatic lang:: and nb:: cache eviction
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    // ── Signal handlers (all @Async) ──────────────────────────────────────────

    @Async
    public void onLike(Long userId, SocialPost post) {
        Map<String, Double> topics = topicExtractor.extract(post);
        applySignal(userId, post, Constant.HLIG_W_LIKE, topics, true);
        applyLanguageSignal(userId, post, Constant.HLIG_W_LIKE * 0.5);
    }

    @Async
    public void onDislike(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_DISLIKE, topicExtractor.extract(post), false);
        // Dislike = negative content signal but neutral on language (user read it = they can read the script)
    }

    @Async
    public void onUnlike(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_UNLIKE, topicExtractor.extract(post), false);
    }

    @Async
    public void onComment(Long userId, SocialPost post) {
        Map<String, Double> topics = topicExtractor.extract(post);
        applySignal(userId, post, Constant.HLIG_W_COMMENT, topics, true);
        // Commenting = strong language signal (user can write/respond in that language's context)
        applyLanguageSignal(userId, post, Constant.HLIG_W_COMMENT * 0.5);
    }

    @Async
    public void onSave(Long userId, SocialPost post) {
        Map<String, Double> topics = topicExtractor.extract(post);
        applySignal(userId, post, Constant.HLIG_W_SAVE, topics, true);
        applyLanguageSignal(userId, post, Constant.HLIG_W_SAVE * 0.5);
    }

    @Async
    public void onUnsave(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_UNSAVE, topicExtractor.extract(post), false);
    }

    @Async
    public void onShare(Long userId, SocialPost post) {
        Map<String, Double> topics = topicExtractor.extract(post);
        applySignal(userId, post, Constant.HLIG_W_SHARE, topics, true);
        applyLanguageSignal(userId, post, Constant.HLIG_W_SHARE * 0.5);
    }

    /**
     * View signal — applied for every post surfaced to the user (including cold-start).
     * Weakest signal (HLIG_W_VIEW ≈ 0.5). After ~30 views across diverse topics
     * the user exits COLD and enters WARMING automatically.
     *
     * Language signal at 0.25× (half of view weight) so a user who views many
     * posts in Tamil gradually builds "lang:ta" preference without a single
     * heavy scroll session dominating the profile.
     */
    @Async
    public void onView(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_VIEW, topicExtractor.extract(post), true);
        applyLanguageSignal(userId, post, Constant.HLIG_W_VIEW * 0.5);
    }

    /**
     * Negative signal: user scrolled past without engaging (weight -0.3).
     * No language signal — scrolling past gives no information about whether
     * the user can or cannot read the script.
     */
    @Async
    public void onScrolledPast(Long userId, SocialPost post) {
        Map<String, Double> topics = topicExtractor.extractFromHashtagsOnly(post);
        if (topics.isEmpty()) return;
        applySignalWithTopics(userId, Constant.HLIG_W_SCROLL_PAST, topics, false);
    }

    /**
     * Strong negative: user explicitly tapped "Not Interested".
     * Penalises all topics on the post at full −8.0 weight.
     * No language penalty — "not interested" reflects topic, not script.
     */
    @Async
    public void onNotInterested(Long userId, SocialPost post) {
        applySignal(userId, post, Constant.HLIG_W_NOT_INTERESTED, topicExtractor.extract(post), false);
        log.info("User {} marked post {} as not interested", userId, post.getId());
    }

    /**
     * Author signal: posting about a topic is the strongest possible signal (+5.0).
     * If you write a post in Tamil, that's the strongest possible language signal too.
     */
    @Async
    public void onPostCreated(Long userId, SocialPost post) {
        Map<String, Double> topics = topicExtractor.extract(post);
        applySignal(userId, post, Constant.HLIG_W_POST_CREATED, topics, true);
        applyLanguageSignal(userId, post, Constant.HLIG_W_POST_CREATED * 0.5);
    }

    // ── Profile reads (cached) ────────────────────────────────────────────────

    /**
     * Returns decayed top-N topics for feed scoring.
     *
     * Cached in-process via Caffeine (CacheConfig) — TTL 5 min, max 100k entries.
     * Evicted eagerly on every interaction signal via @CacheEvict.
     * Key: uip_profile::{userId}
     */
    @Cacheable(value = Constant.HLIG_CACHE_PROFILE, key = "#userId")
    public Map<String, Double> loadTopN(Long userId) {
        List<UserInterestProfile> rows = repo.findTopNByUserId(
                userId, PageRequest.of(0, Constant.HLIG_TOP_N));
        return rows.stream()
                .filter(r -> r.decayedWeight() > Constant.HLIG_SCORE_MIN_THRESHOLD)
                .collect(Collectors.toMap(
                        UserInterestProfile::getTopic,
                        UserInterestProfile::getDecayedWeight,
                        Math::max,
                        LinkedHashMap::new
                ));
    }

    /**
     * Returns CORE topics (decayed weight ≥ HLIG_PROFILE_CORE_THRESHOLD)
     * from the cached profile. Used for collaborative-filter neighbour lookup.
     *
     * NOTE: Language topics ("lang:XX") are intentionally INCLUDED here so
     * that users who share both content AND language preferences are matched
     * as neighbours — improving the collaborative filter for regional-language content.
     */
    public List<String> loadCoreTopics(Long userId) {
        return loadTopN(userId).entrySet().stream()
                .filter(e -> e.getValue() >= Constant.HLIG_PROFILE_CORE_THRESHOLD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // ── Language preference reads ─────────────────────────────────────────────

    /**
     * Returns the user's preferred languages as an ordered BCP-47 list,
     * most preferred first.
     *
     * Reads from the denormalized {@code preferred_languages} column on the
     * top-weight UIP row (maintained by rebuildLanguagePreferences()).
     *
     * Cached under a separate key so it survives content-topic evictions
     * if the TTL allows — evicted explicitly by evictProfileCache() which
     * is called after every signal update.
     *
     * Returns empty list for COLD users or users who have only consumed English.
     *
     * @param userId requesting user's ID
     * @return ordered list of BCP-47 codes, e.g. ["hi", "en", "mr"]
     */
    @Cacheable(value = Constant.HLIG_CACHE_PROFILE, key = "'lang::' + #userId")
    public List<String> getPreferredLanguages(Long userId) {
        // Reads the preferred_languages column from whichever row for this user
        // has the highest weight (the "anchor row"). The repo method returns a
        // single String or null.
        String raw = repo.getPreferredLanguages(userId);
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(UserInterestProfile.MAX_PREFERRED_LANGUAGES)
                .collect(Collectors.toList());
    }

    // ── Phase detection (O(1)) ────────────────────────────────────────────────

    /**
     * Determines feed personalisation phase from cached column.
     * Zero DB overhead — reads one integer column from idx_uip_signals.
     */
    public Phase getUserPhase(Long userId) {
        int total = repo.getTotalSignalsCache(userId);
        if (total <= Constant.HLIG_WARMING_PHASE_THRESHOLD) return Phase.COLD;
        if (total <= Constant.HLIG_WARM_PHASE_THRESHOLD)    return Phase.WARMING;
        return Phase.WARM;
    }

    public enum Phase { COLD, WARMING, WARM }

    // ── Collaborative filtering ────────────────────────────────────────────────

    /**
     * Finds up to 50 users who share CORE topics with the given user.
     * Single DB query — not N queries per topic.
     *
     * Language topics ("lang:XX") in coreTopics improve neighbour matching
     * for regional-language users who wouldn't otherwise share content topics
     * with each other.
     */
    @Cacheable(value = Constant.HLIG_CACHE_PROFILE, key = "'nb::' + #userId")
    public List<Long> findNeighbours(Long userId) {
        List<String> coreTopics = loadCoreTopics(userId);
        if (coreTopics.isEmpty()) return Collections.emptyList();
        return repo.findNeighboursByTopics(coreTopics, Constant.HLIG_PROFILE_CORE_THRESHOLD,
                userId, Constant.HLIG_MAX_NEIGHBOURS);
    }

    // ── Seen-post dedup ────────────────────────────────────────────────────────

    /**
     * Returns post IDs seen by this user in the last 72 hours.
     */
    public Set<Long> recentlySeenPosts(Long userId) {
        int total = repo.getTotalSignalsCache(userId);
        if (total <= Constant.HLIG_WARMING_PHASE_THRESHOLD) return Collections.emptySet();
        List<Long> ids = repo.findRecentlyViewedPostIds(userId);
        return ids != null ? new HashSet<>(ids) : Collections.emptySet();
    }

    // ── Onboarding seed (optional) ────────────────────────────────────────────

    /**
     * Seeds initial interest profile from explicit topic selection.
     * Each topic is seeded at weight 3.0.
     * NOT required — silent onView signals work without this.
     */
    public void seedFromOnboarding(Long userId, List<String> topics) {
        for (String topic : topics) {
            repo.seedTopic(userId, topic.toLowerCase().trim(), 3.0);
        }
        evictProfileCache(userId);
        log.info("Seeded {} topics for new user {}", topics.size(), userId);
    }

    /**
     * Seeds language preferences from explicit onboarding selection.
     * Each language is seeded at weight 5.0 (higher than content topics
     * to immediately reflect the user's declared language preference).
     *
     * Called from the onboarding flow when the user selects preferred languages
     * via the UI. Works in combination with seedFromOnboarding().
     *
     * @param userId    user being onboarded
     * @param langCodes list of BCP-47 codes the user selected, e.g. ["hi", "ta"]
     */
    public void seedLanguagePreferencesFromOnboarding(Long userId, List<String> langCodes) {
        if (langCodes == null || langCodes.isEmpty()) return;
        for (String code : langCodes) {
            if (code == null || code.isBlank()) continue;
            String topic = "lang:" + code.toLowerCase().trim();
            repo.seedTopic(userId, topic, 5.0);
        }
        evictProfileCache(userId);
        rebuildLanguagePreferences(userId);
        log.info("Seeded {} language preferences for user {}", langCodes.size(), userId);
    }

    // ── Nightly decay + prune ─────────────────────────────────────────────────

    /**
     * Decays all rows older than 1 day in batches of 10,000.
     * After decay, prunes rows whose decayed weight is negligible.
     *
     * Language topic rows ("lang:XX") are pruned by the same threshold as
     * content topics. A user who hasn't read Tamil content for several months
     * will have their "lang:ta" preference naturally decay and eventually be
     * pruned, reflecting genuine shift in reading behaviour.
     *
     * Runs at 02:00 AM daily.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void applyNightlyDecay() {
        log.info("HLIG nightly decay starting...");
        int totalDecayed = 0;
        int decayBatch;
        int attempt  = 0;
        final int MAX_BATCHES = 10_000;

        // ── Phase 1: decay ────────────────────────────────────────────────────
        do {
            try {
                decayBatch   = repo.bulkDecayBatch(1); // rows older than 1 day
                totalDecayed += decayBatch;
                attempt++;
            } catch (Exception e) {
                log.error("HLIG decay batch {} failed — stopping safely. {} rows updated so far.",
                        attempt, totalDecayed, e);
                break;
            }
        } while (decayBatch > 0 && attempt < MAX_BATCHES);

        log.info("HLIG nightly decay complete — {} rows updated in {} batches", totalDecayed, attempt);

        // ── Phase 2: prune negligible rows ────────────────────────────────────
        try {
            int pruned = repo.pruneNegligibleRows(Constant.HLIG_SCORE_MIN_THRESHOLD);
            log.info("HLIG nightly prune complete — {} near-zero rows removed", pruned);
        } catch (Exception e) {
            log.warn("HLIG nightly prune failed (non-fatal): {}", e.getMessage());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    void applySignal(Long userId, SocialPost post, double baseWeight,
                     Map<String, Double> topics, boolean isPositive) {
        if (topics.isEmpty()) return;
        applySignalWithTopics(userId, baseWeight, topics, isPositive);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    void applySignalWithTopics(Long userId, double baseWeight,
                               Map<String, Double> topics, boolean isPositive) {
        if (topics.isEmpty()) return;
        int sigDelta = isPositive ? 1 : 0;
        topics.forEach((topic, strength) -> {
            double delta = baseWeight * strength;
            repo.upsertWeight(userId, topic, delta, sigDelta);
        });
        evictProfileCache(userId);
    }

    /**
     * Records a language preference signal for the post's detected language.
     *
     * Language topics are stored as "lang:XX" in the same user_interest_profiles
     * table as content topics. They participate in the same decay / prune cycle.
     *
     * After the upsert, rebuildLanguagePreferences() is triggered asynchronously
     * to keep the denormalized preferred_languages column fresh.
     *
     * @param userId      user who interacted
     * @param post        post that was interacted with
     * @param langWeight  weight to apply (positive, caller computes fraction)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void applyLanguageSignal(Long userId, SocialPost post, double langWeight) {
        String lang = post.safeLanguage();

        // "mixed" posts contribute to all declared languages — skip the language signal
        // because we can't assign the credit to a single language code.
        if ("mixed".equals(lang)) return;

        // English is the default — don't inflate "lang:en" weight for English posts
        // because that would make en-vs-other comparisons meaningless.
        if ("en".equals(lang)) return;

        String langTopic = "lang:" + lang;
        try {
            repo.upsertWeight(userId, langTopic, langWeight, 1);
            evictProfileCache(userId);
            // Rebuild the denormalized column asynchronously so the scorer
            // sees the updated preference on the next request.
            rebuildLanguagePreferences(userId);
        } catch (Exception e) {
            log.warn("[HLIG] applyLanguageSignal failed: userId={} lang={} reason={}",
                    userId, lang, e.getMessage());
        }
    }

    /**
     * Rebuilds the denormalized preferred_languages column for the given user.
     *
     * Reads all "lang:XX" topic rows, sorts by decayed weight descending,
     * takes the top MAX_PREFERRED_LANGUAGES codes, and writes them as a
     * comma-separated string into the preferred_languages column on the
     * user's top-weight row.
     *
     * Called asynchronously after every language signal update.
     * Also called after seedLanguagePreferencesFromOnboarding().
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void rebuildLanguagePreferences(Long userId) {
        try {
            List<UserInterestProfile> langRows = repo.findLanguageTopicsByUserId(userId);
            if (langRows.isEmpty()) return;

            String preferredLangs = langRows.stream()
                    .filter(r -> r.isLanguageTopic() && r.decayedWeight() > Constant.HLIG_SCORE_MIN_THRESHOLD)
                    .sorted(Comparator.comparingDouble(UserInterestProfile::getDecayedWeight).reversed())
                    .limit(UserInterestProfile.MAX_PREFERRED_LANGUAGES)
                    .map(UserInterestProfile::languageCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(","));

            if (!preferredLangs.isBlank()) {
                // Write to the anchor row (the row with the highest overall weight for this user).
                // The repo method updates preferred_languages for the user's top-weight row.
                repo.updatePreferredLanguages(userId, preferredLangs);
                // Evict so the scorer picks up the new preference list on next request
                evictProfileCache(userId);
            }
        } catch (Exception e) {
            log.warn("[HLIG] rebuildLanguagePreferences failed: userId={} reason={}", userId, e.getMessage());
        }
    }

    /**
     * Evicts ALL cached entries for this user: content profile, language preference,
     * and neighbour list.
     *
     * FIX MEMORY LEAK #6 — the original method was annotated with
     * @CacheEvict(key="#userId") which only removes the content-profile key.
     * The "lang::{userId}" and "nb::{userId}" keys were left in the cache
     * indefinitely (until TTL) even after profile updates, causing stale data
     * and unbounded cache growth for high-traffic users.
     *
     * We now use allEntries=false with a composite key set via a programmatic
     * CacheManager call for the two extra keys, keeping the annotation for the
     * primary key so Spring's proxy advice chain still fires.
     */
    @CacheEvict(value = Constant.HLIG_CACHE_PROFILE, key = "#userId")
    public void evictProfileCache(Long userId) {
        // Spring handles the primary key eviction via @CacheEvict above.
        // Manually evict the language and neighbour cache keys.
        // Both keys share the same cache region (HLIG_CACHE_PROFILE).
        try {
            org.springframework.cache.CacheManager cm =
                    applicationContext.getBean(org.springframework.cache.CacheManager.class);
            org.springframework.cache.Cache cache = cm.getCache(Constant.HLIG_CACHE_PROFILE);
            if (cache != null) {
                cache.evict("lang::" + userId);
                cache.evict("nb::"   + userId);
            }
        } catch (Exception e) {
            log.warn("[HLIG] Secondary cache eviction failed for userId={}: {}", userId, e.getMessage());
        }
    }
}