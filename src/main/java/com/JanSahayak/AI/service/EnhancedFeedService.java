package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EnhancedFeedService {

    private final PostRepo postRepository;
    private final UserRepo userRepository;
    private final PostService postService;
    private final PinCodeLookupService pincodeLookupService;

    // ===== Country-Wide Broadcast Feed =====

    /**
     * Get all active country-wide broadcasts visible to all users in India
     * These are always visible regardless of user's location
     */
    public PaginatedResponse<PostResponse> getCountryWideBroadcastFeed(User user, Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(user);

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getCountryWideBroadcastFeed", beforePostId, limit);

            Pageable pageable = setup.toPageable();
            List<Post> countryPosts;

            if (setup.hasCursor()) {
                countryPosts = postRepository.findByBroadcastScopeAndStatusAndTargetCountryAndIdLessThanOrderByCreatedAtDesc(
                        BroadcastScope.COUNTRY, PostStatus.ACTIVE, Constant.DEFAULT_TARGET_COUNTRY,
                        setup.getSanitizedCursor(), pageable);
            } else {
                countryPosts = postRepository.findByBroadcastScopeAndStatusAndTargetCountryOrderByCreatedAtDesc(
                        BroadcastScope.COUNTRY, PostStatus.ACTIVE, Constant.DEFAULT_TARGET_COUNTRY, pageable);
            }

            List<PostResponse> postResponses = convertToPostResponses(countryPosts, user);

            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            log.info("Retrieved {} country-wide broadcasts for user: {} (total available: {})",
                    postResponses.size(), user.getActualUsername(), response.getCount());

            return response;

        } catch (Exception e) {
            log.error("Failed to get country-wide broadcast feed for user: {}",
                    user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to get country-wide broadcast feed: " + e.getMessage(), e);
        }
    }

    // ===== State-Level Broadcast Feed =====

    /**
     * Get state-level broadcasts for user's state (based on pincode prefix)
     */
    public PaginatedResponse<PostResponse> getStateLevelBroadcastFeed(User user, Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(user);

            if (!user.hasPincode()) {
                log.debug("User {} has no pincode, returning empty state feed", user.getActualUsername());
                return PaginationUtils.createEmptyResponse(PaginationUtils.validateLimit(limit));
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getStateLevelBroadcastFeed", beforePostId, limit);

            String userStatePrefix = user.getStatePrefix();
            Pageable pageable = setup.toPageable();
            List<Post> statePosts;

            if (setup.hasCursor()) {
                statePosts = postRepository.findByBroadcastScopeAndTargetStatesContainingAndIdLessThanOrderByCreatedAtDesc(
                        BroadcastScope.STATE, userStatePrefix, setup.getSanitizedCursor(), pageable);
            } else {
                statePosts = postRepository.findByBroadcastScopeAndTargetStatesContainingOrderByCreatedAtDesc(
                        BroadcastScope.STATE, userStatePrefix, pageable);
            }

            // Filter by status after retrieval
            statePosts = statePosts.stream()
                    .filter(post -> post != null && post.getStatus() == PostStatus.ACTIVE)
                    .collect(Collectors.toList());

            List<PostResponse> postResponses = convertToPostResponses(statePosts, user);

            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            log.info("Retrieved {} state-level broadcasts for user: {} (state prefix: {})",
                    postResponses.size(), user.getActualUsername(), userStatePrefix);

            return response;

        } catch (Exception e) {
            log.error("Failed to get state-level broadcast feed for user: {}",
                    user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to get state-level broadcast feed: " + e.getMessage(), e);
        }
    }

    // ===== District-Level Broadcast Feed =====

    /**
     * Get district-level broadcasts for user's district (based on pincode prefix)
     */
    public PaginatedResponse<PostResponse> getDistrictLevelBroadcastFeed(User user, Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(user);

            if (!user.hasPincode()) {
                log.debug("User {} has no pincode, returning empty district feed", user.getActualUsername());
                return PaginationUtils.createEmptyResponse(PaginationUtils.validateLimit(limit));
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getDistrictLevelBroadcastFeed", beforePostId, limit);

            String userDistrictPrefix = user.getDistrictPrefix();
            Pageable pageable = setup.toPageable();
            List<Post> districtPosts;

            if (setup.hasCursor()) {
                districtPosts = postRepository.findByBroadcastScopeAndTargetDistrictsContainingAndIdLessThanOrderByCreatedAtDesc(
                        BroadcastScope.DISTRICT, userDistrictPrefix, setup.getSanitizedCursor(), pageable);
            } else {
                districtPosts = postRepository.findByBroadcastScopeAndTargetDistrictsContainingOrderByCreatedAtDesc(
                        BroadcastScope.DISTRICT, userDistrictPrefix, pageable);
            }

            // Filter by status after retrieval
            districtPosts = districtPosts.stream()
                    .filter(post -> post != null && post.getStatus() == PostStatus.ACTIVE)
                    .collect(Collectors.toList());

            List<PostResponse> postResponses = convertToPostResponses(districtPosts, user);

            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            log.info("Retrieved {} district-level broadcasts for user: {} (district prefix: {})",
                    postResponses.size(), user.getActualUsername(), userDistrictPrefix);

            return response;

        } catch (Exception e) {
            log.error("Failed to get district-level broadcast feed for user: {}",
                    user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to get district-level broadcast feed: " + e.getMessage(), e);
        }
    }

    // ===== Area/Pincode-Level Broadcast Feed =====

    /**
     * Get area-level broadcasts for user's specific pincode
     * Also includes active normal posts from other users in the same pincode/area
     */
    public PaginatedResponse<PostResponse> getAreaLevelBroadcastFeed(User user, Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(user);

            if (!user.hasPincode()) {
                log.debug("User {} has no pincode, returning empty area feed", user.getActualUsername());
                return PaginationUtils.createEmptyResponse(PaginationUtils.validateLimit(limit));
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getAreaLevelBroadcastFeed", beforePostId, limit);

            String userPincode = user.getPincode();
            List<Post> allAreaPosts = new ArrayList<>();

            // 1. Get area-level broadcasts for this pincode
            Pageable broadcastPageable = PageRequest.of(0, setup.getValidatedLimit() / 2); // Reserve half for broadcasts
            List<Post> areaBroadcasts;

            if (setup.hasCursor()) {
                areaBroadcasts = postRepository.findByBroadcastScopeAndTargetPincodesContainingAndIdLessThanOrderByCreatedAtDesc(
                        BroadcastScope.AREA, userPincode, setup.getSanitizedCursor(), broadcastPageable);
            } else {
                areaBroadcasts = postRepository.findByBroadcastScopeAndTargetPincodesContainingOrderByCreatedAtDesc(
                        BroadcastScope.AREA, userPincode, broadcastPageable);
            }

            // Filter by status after retrieval
            if (areaBroadcasts != null) {
                areaBroadcasts = areaBroadcasts.stream()
                        .filter(post -> post != null && post.getStatus() == PostStatus.ACTIVE)
                        .collect(Collectors.toList());
                allAreaPosts.addAll(areaBroadcasts);
            }

            // 2. Get active normal posts from other users in the same pincode
            List<User> usersInSamePincode = userRepository.findByPincodeAndIsActiveTrue(userPincode);

            if (usersInSamePincode != null && !usersInSamePincode.isEmpty()) {
                // Exclude current user from the list
                List<User> otherUsersInArea = usersInSamePincode.stream()
                        .filter(u -> u != null && !u.getId().equals(user.getId()))
                        .collect(Collectors.toList());

                if (!otherUsersInArea.isEmpty()) {
                    Pageable normalPostsPageable = PageRequest.of(0, setup.getValidatedLimit() / 2); // Reserve half for normal posts
                    List<Post> normalPostsInArea;

                    if (setup.hasCursor()) {
                        // Get all posts from users in same area (includes both broadcast and normal posts)
                        normalPostsInArea = postRepository.findByUserInAndStatusAndIdLessThanOrderByCreatedAtDesc(
                                otherUsersInArea, PostStatus.ACTIVE, setup.getSanitizedCursor(), normalPostsPageable);
                    } else {
                        normalPostsInArea = postRepository.findByUserInAndStatusOrderByCreatedAtDesc(
                                otherUsersInArea, PostStatus.ACTIVE, normalPostsPageable);
                    }

                    // Since normal posts have AREA scope by default, we include all posts from area users
                    // No need to filter out broadcast posts since normal posts are also area-scoped
                    if (normalPostsInArea != null) {
                        allAreaPosts.addAll(normalPostsInArea);
                    }
                }
            }

            // 3. Remove duplicates, sort by creation date (newest first), and limit results
            List<Post> distinctPosts = allAreaPosts.stream()
                    .distinct()
                    .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());

            List<PostResponse> postResponses = convertToPostResponses(distinctPosts, user);

            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            long broadcastCount = areaBroadcasts != null ? areaBroadcasts.size() : 0;
            long normalPostCount = postResponses.size() - broadcastCount;

            log.info("Retrieved {} total area posts for user: {} (pincode: {}) - {} broadcasts, {} normal posts from area users",
                    postResponses.size(), user.getActualUsername(), userPincode, broadcastCount, normalPostCount);

            return response;

        } catch (Exception e) {
            log.error("Failed to get area-level broadcast feed for user: {}",
                    user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to get area-level broadcast feed: " + e.getMessage(), e);
        }
    }

    // ===== Mixed Geographic Feed =====

    /**
     * Get a mixed feed combining posts from all geographic levels in random order
     * Distribution: Country (25%) + State (25%) + District (25%) + Area+Local (25%)
     * Posts are shuffled randomly to provide diverse, unpredictable content mix
     */
    public PaginatedResponse<PostResponse> getMixedGeographicFeed(User user, Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(user);

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getMixedGeographicFeed", beforePostId, limit);

            List<Post> mixedPosts = new ArrayList<>();

            // Calculate proportional limits for each geographic level
            int countryLimit = Math.max(1, setup.getValidatedLimit() / 4);    // 25%
            int stateLimit = Math.max(1, setup.getValidatedLimit() / 4);      // 25%
            int districtLimit = Math.max(1, setup.getValidatedLimit() / 4);   // 25%
            int areaLimit = setup.getValidatedLimit() - countryLimit - stateLimit - districtLimit; // Remaining ~25%

            // 1. Get country-wide broadcasts (always available)
            try {
                PaginatedResponse<PostResponse> countryFeed = getCountryWideBroadcastFeed(user, setup.getSanitizedCursor(), countryLimit);
                mixedPosts.addAll(convertToPostEntities(countryFeed.getData()));
                log.debug("Added {} country-wide posts", countryFeed.getData().size());
            } catch (Exception e) {
                log.warn("Failed to get country-wide posts: {}", e.getMessage());
            }

            // 2. Get state-level content (if user has pincode)
            if (user.hasPincode()) {
                try {
                    PaginatedResponse<PostResponse> stateFeed = getStateLevelBroadcastFeed(user, setup.getSanitizedCursor(), stateLimit);
                    mixedPosts.addAll(convertToPostEntities(stateFeed.getData()));
                    log.debug("Added {} state-level posts for state prefix: {}",
                            stateFeed.getData().size(), PostUtility.getUserStatePrefix(user));
                } catch (Exception e) {
                    log.warn("Failed to get state-level posts: {}", e.getMessage());
                }

                // 3. Get district-level content
                try {
                    PaginatedResponse<PostResponse> districtFeed = getDistrictLevelBroadcastFeed(user, setup.getSanitizedCursor(), districtLimit);
                    mixedPosts.addAll(convertToPostEntities(districtFeed.getData()));
                    log.debug("Added {} district-level posts for district prefix: {}",
                            districtFeed.getData().size(), PostUtility.getUserDistrictPrefix(user));
                } catch (Exception e) {
                    log.warn("Failed to get district-level posts: {}", e.getMessage());
                }

                // 4. Get area-level content (broadcasts + local posts)
                try {
                    PaginatedResponse<PostResponse> areaFeed = getAreaLevelBroadcastFeed(user, setup.getSanitizedCursor(), areaLimit);
                    mixedPosts.addAll(convertToPostEntities(areaFeed.getData()));
                    log.debug("Added {} area-level posts for pincode: {}",
                            areaFeed.getData().size(), user.getPincode());
                } catch (Exception e) {
                    log.warn("Failed to get area-level posts: {}", e.getMessage());
                }
            } else {
                log.debug("User {} has no pincode - only showing country-wide content", user.getActualUsername());
            }

            // 5. Remove duplicates and shuffle randomly for mixed presentation
            List<Post> distinctPosts = mixedPosts.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            // RANDOM SHUFFLE: Mix posts from all geographic levels randomly
            Collections.shuffle(distinctPosts, new Random());

            // Limit after shuffling to maintain randomness
            List<Post> finalPosts = distinctPosts.stream()
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());

            List<PostResponse> postResponses = convertToPostResponses(finalPosts, user);

            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            // Log detailed statistics
            Map<BroadcastScope, Long> scopeCounts = finalPosts.stream()
                    .filter(post -> post.getBroadcastScope() != null)
                    .collect(Collectors.groupingBy(Post::getBroadcastScope, Collectors.counting()));

            long normalPostCount = finalPosts.stream()
                    .filter(post -> post.getBroadcastScope() == null)
                    .count();

            log.info("Mixed geographic feed for user: {} - Total: {} posts (RANDOMLY SHUFFLED) " +
                            "(Country: {}, State: {}, District: {}, Area: {}, Normal: {}, Has Location: {})",
                    user.getActualUsername(), postResponses.size(),
                    scopeCounts.getOrDefault(BroadcastScope.COUNTRY, 0L),
                    scopeCounts.getOrDefault(BroadcastScope.STATE, 0L),
                    scopeCounts.getOrDefault(BroadcastScope.DISTRICT, 0L),
                    scopeCounts.getOrDefault(BroadcastScope.AREA, 0L),
                    normalPostCount,
                    user.hasPincode());

            return response;

        } catch (Exception e) {
            log.error("Failed to get mixed geographic feed for user: {}",
                    user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to get mixed geographic feed: " + e.getMessage(), e);
        }
    }

    // ===== Combined Geographic Broadcast Feed =====

    /**
     * Get all relevant broadcasts for user based on their location
     * Includes: Country-wide + State + District + Area broadcasts
     */
    public PaginatedResponse<PostResponse> getAllRelevantBroadcastFeed(User user, Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(user);

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getAllRelevantBroadcastFeed", beforePostId, limit);

            List<Post> allRelevantPosts = new ArrayList<>();

            // 1. Country-wide broadcasts (always included)
            PaginatedResponse<PostResponse> countryPosts = getCountryWideBroadcastFeed(user, setup.getSanitizedCursor(), setup.getValidatedLimit() / 4);
            allRelevantPosts.addAll(convertToPostEntities(countryPosts.getData()));

            // 2. State-level broadcasts (if user has pincode)
            if (user.hasPincode()) {
                PaginatedResponse<PostResponse> statePosts = getStateLevelBroadcastFeed(user, setup.getSanitizedCursor(), setup.getValidatedLimit() / 4);
                allRelevantPosts.addAll(convertToPostEntities(statePosts.getData()));

                // 3. District-level broadcasts
                PaginatedResponse<PostResponse> districtPosts = getDistrictLevelBroadcastFeed(user, setup.getSanitizedCursor(), setup.getValidatedLimit() / 4);
                allRelevantPosts.addAll(convertToPostEntities(districtPosts.getData()));

                // 4. Area-level broadcasts
                PaginatedResponse<PostResponse> areaPosts = getAreaLevelBroadcastFeed(user, setup.getSanitizedCursor(), setup.getValidatedLimit() / 4);
                allRelevantPosts.addAll(convertToPostEntities(areaPosts.getData()));
            }

            // Remove duplicates and sort by creation date (newest first)
            List<Post> distinctPosts = allRelevantPosts.stream()
                    .distinct()
                    .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());

            List<PostResponse> postResponses = convertToPostResponses(distinctPosts, user);

            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            log.info("Retrieved {} total relevant broadcasts for user: {} (country: {}, has pincode: {})",
                    postResponses.size(), user.getActualUsername(),
                    countryPosts.getData().size(), user.hasPincode());

            return response;

        } catch (Exception e) {
            log.error("Failed to get all relevant broadcast feed for user: {}",
                    user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to get all relevant broadcast feed: " + e.getMessage(), e);
        }
    }

    // ===== Geographic Feed by Specific Location =====

    /**
     * Get broadcasts for a specific pincode (useful for location-based queries)
     */
    public PaginatedResponse<PostResponse> getBroadcastFeedForPincode(String pincode, User currentUser,
                                                                      Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);
            PostUtility.validateTargetPincodeForUser(pincode);

            if (!Constant.isValidIndianPincode(pincode)) {
                throw new ServiceException("Invalid Indian pincode format: " + pincode);
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getBroadcastFeedForPincode", beforePostId, limit);

            List<Post> allPosts = new ArrayList<>();

            // 1. Country-wide broadcasts
            PaginatedResponse<PostResponse> countryPosts = getCountryWideBroadcastFeed(currentUser, setup.getSanitizedCursor(), setup.getValidatedLimit());
            allPosts.addAll(convertToPostEntities(countryPosts.getData()));

            // 2. State-level broadcasts for this pincode
            String statePrefix = Constant.getStatePrefixFromPincode(pincode);
            List<Post> statePosts = postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByCreatedAtDesc(
                    BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix);
            allPosts.addAll(statePosts);

            // 3. District-level broadcasts for this pincode
            String districtPrefix = Constant.getDistrictPrefixFromPincode(pincode);
            List<Post> districtPosts = postRepository.findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByCreatedAtDesc(
                    BroadcastScope.DISTRICT, PostStatus.ACTIVE, districtPrefix);
            allPosts.addAll(districtPosts);

            // 4. Area-level broadcasts for this pincode
            List<Post> areaPosts = postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByCreatedAtDesc(
                    BroadcastScope.AREA, PostStatus.ACTIVE, pincode);
            allPosts.addAll(areaPosts);

            // Remove duplicates and sort
            List<Post> distinctPosts = allPosts.stream()
                    .distinct()
                    .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());

            List<PostResponse> postResponses = convertToPostResponses(distinctPosts, currentUser);

            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            log.info("Retrieved {} broadcasts for pincode: {} (state: {}, district: {})",
                    postResponses.size(), pincode, statePrefix, districtPrefix);

            return response;

        } catch (Exception e) {
            log.error("Failed to get broadcast feed for pincode: {}", pincode, e);
            throw new ServiceException("Failed to get broadcast feed for pincode: " + e.getMessage(), e);
        }
    }

    /**
     * Get broadcasts for a specific state (by state name)
     */
    public PaginatedResponse<PostResponse> getBroadcastFeedForState(String stateName, User currentUser,
                                                                    Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            if (stateName == null || stateName.trim().isEmpty()) {
                throw new ServiceException("State name cannot be empty");
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getBroadcastFeedForState", beforePostId, limit);

            // Convert state name to pincode prefixes using utility method
            List<String> statePrefixes = PostUtility.convertStatesToPincodePrefixes(Arrays.asList(stateName.trim()));

            if (statePrefixes.isEmpty()) {
                log.warn("No pincode prefixes found for state: {}", stateName);
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<Post> allPosts = new ArrayList<>();

            // 1. Country-wide broadcasts
            PaginatedResponse<PostResponse> countryPosts = getCountryWideBroadcastFeed(currentUser, setup.getSanitizedCursor(), setup.getValidatedLimit());
            allPosts.addAll(convertToPostEntities(countryPosts.getData()));

            // 2. State-level broadcasts for all prefixes of this state
            for (String statePrefix : statePrefixes) {
                List<Post> statePosts = postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByCreatedAtDesc(
                        BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix);
                allPosts.addAll(statePosts);
            }

            // Remove duplicates and sort
            List<Post> distinctPosts = allPosts.stream()
                    .distinct()
                    .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());

            List<PostResponse> postResponses = convertToPostResponses(distinctPosts, currentUser);

            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            log.info("Retrieved {} broadcasts for state: {} (prefixes: {})",
                    postResponses.size(), stateName, statePrefixes);

            return response;

        } catch (Exception e) {
            log.error("Failed to get broadcast feed for state: {}", stateName, e);
            throw new ServiceException("Failed to get broadcast feed for state: " + e.getMessage(), e);
        }
    }

    // ===== Feed Statistics =====

    /**
     * Get broadcast feed statistics for user
     */
    public Map<String, Object> getBroadcastFeedStatistics(User user) {
        try {
            PostUtility.validateUser(user);

            Map<String, Object> stats = new HashMap<>();

            // Count country-wide broadcasts
            long countryCount = postRepository.countByBroadcastScopeAndStatusAndTargetCountry(
                    BroadcastScope.COUNTRY, PostStatus.ACTIVE, Constant.DEFAULT_TARGET_COUNTRY);
            stats.put("countryWideBroadcasts", countryCount);

            if (user.hasPincode()) {
                String userStatePrefix = PostUtility.getUserStatePrefix(user);
                String userDistrictPrefix = PostUtility.getUserDistrictPrefix(user);
                String userPincode = user.getPincode();

                // Count state-level broadcasts
                long stateCount = postRepository.countByBroadcastScopeAndStatusAndTargetStatesContaining(
                        BroadcastScope.STATE, PostStatus.ACTIVE, userStatePrefix);
                stats.put("stateLevelBroadcasts", stateCount);

                // Count district-level broadcasts
                long districtCount = postRepository.countByBroadcastScopeAndStatusAndTargetDistrictsContaining(
                        BroadcastScope.DISTRICT, PostStatus.ACTIVE, userDistrictPrefix);
                stats.put("districtLevelBroadcasts", districtCount);

                // Count area-level broadcasts
                long areaCount = postRepository.countByBroadcastScopeAndStatusAndTargetPincodesContaining(
                        BroadcastScope.AREA, PostStatus.ACTIVE, userPincode);
                stats.put("areaLevelBroadcasts", areaCount);

                stats.put("totalRelevantBroadcasts", countryCount + stateCount + districtCount + areaCount);

                stats.put("userStatePrefix", userStatePrefix);
                stats.put("userDistrictPrefix", userDistrictPrefix);
                stats.put("userPincode", userPincode);
            } else {
                stats.put("stateLevelBroadcasts", 0L);
                stats.put("districtLevelBroadcasts", 0L);
                stats.put("areaLevelBroadcasts", 0L);
                stats.put("totalRelevantBroadcasts", countryCount);
            }

            stats.put("hasLocation", user.hasPincode());
            stats.put("feedType", "geographic_broadcast");

            return stats;

        } catch (Exception e) {
            log.error("Failed to get broadcast feed statistics for user: {}",
                    user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to get broadcast feed statistics: " + e.getMessage(), e);
        }
    }

    // ===== Helper Methods =====

    private List<PostResponse> convertToPostResponses(List<Post> posts, User currentUser) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        return posts.stream()
                .filter(Objects::nonNull)
                .filter(post -> PostUtility.isPostVisibleToUser(post, currentUser))
                .map(post -> postService.convertToPostResponse(post, currentUser))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Post> convertToPostEntities(List<PostResponse> postResponses) {
        if (postResponses == null || postResponses.isEmpty()) {
            return Collections.emptyList();
        }

        return postResponses.stream()
                .filter(Objects::nonNull)
                .map(postResponse -> {
                    try {
                        return postService.findById(postResponse.getId());
                    } catch (Exception e) {
                        log.warn("Failed to find post by ID: {}", postResponse.getId());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}