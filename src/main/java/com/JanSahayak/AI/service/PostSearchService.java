package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.payload.PostUtility;
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
public class PostSearchService {

    private final PostRepo postRepository;
    private final UserRepo userRepository;
    private final PinCodeLookupService pincodeLookupService;
    private final PostService postService;

    // ===== Main Search Methods with Cursor-Based Pagination =====

    /**
     * Universal search function for posts with cursor-based pagination
     * Supports search by: state, district, pincode, and content keywords
     * Returns only ACTIVE posts, sorted by most recent first
     */
    public PaginatedResponse<PostResponse> searchPosts(String searchQuery, User currentUser,
                                                       Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                throw new ValidationException("Search query cannot be empty");
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("searchPosts", beforePostId, limit);
            String query = searchQuery.trim();

            log.info("Searching posts for query: '{}' by user: {} (role: {})",
                    query, currentUser.getActualUsername(),
                    currentUser.getRole() != null ? currentUser.getRole().getName() : "unknown");

            List<Post> searchResults = new ArrayList<>();

            // 1. Check if query is a hashtag search
            if (query.startsWith("#")) {
                searchResults.addAll(searchByHashtag(query, currentUser, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            }
            // 2. Check if query is a 6-digit pincode
            else if (Constant.isValidIndianPincode(query)) {
                searchResults.addAll(searchByPincode(query, currentUser, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            }
            // 3. Check if query is a 2-digit state prefix
            else if (Constant.isValidIndianStatePrefix(query)) {
                searchResults.addAll(searchByStatePrefix(query, currentUser, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            }
            // 4. Check if query is a 3-digit district prefix
            else if (query.matches(Constant.INDIAN_DISTRICT_PREFIX_PATTERN)) {
                searchResults.addAll(searchByDistrictPrefix(query, currentUser, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            }
            // 5. Search by state name
            else if (isStateName(query)) {
                searchResults.addAll(searchByStateName(query, currentUser, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            }
            // 6. Search by district name (if pincode lookup service can help)
            else if (isDistrictName(query)) {
                searchResults.addAll(searchByDistrictName(query, currentUser, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            }
            // 7. Search by content keywords (including hashtags within content)
            else {
                searchResults.addAll(searchByContent(query, currentUser, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            }

            // Remove duplicates, apply filters, and convert to responses
            List<PostResponse> postResponses = searchResults.stream()
                    .filter(post -> post != null &&
                            post.getStatus() == PostStatus.ACTIVE &&
                            post.getUser() != null &&
                            post.getUser().getIsActive() &&
                            PostUtility.isPostVisibleToUser(post, currentUser))
                    .distinct()
                    .sorted((p1, p2) -> p2.getId().compareTo(p1.getId()))
                    .limit(setup.getValidatedLimit())
                    .map(post -> postService.convertToPostResponse(post, currentUser))
                    .collect(Collectors.toList());

            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            PaginationUtils.logPaginationResults("searchPosts", postResponses,
                    response.isHasMore(), response.getNextCursor());

            return response;

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to search posts for query: '{}' by user: {}",
                    searchQuery, currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchPosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Universal search function for posts with cursor-based pagination for anonymous users
     */
    public PaginatedResponse<PostResponse> searchPostsAnonymous(String searchQuery, Long beforePostId, Integer limit) {
        try {
            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                throw new ValidationException("Search query cannot be empty");
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("searchPostsAnonymous", beforePostId, limit);
            String query = searchQuery.trim();

            log.info("Searching posts for query: '{}' by anonymous user", query);

            List<Post> searchResults = new ArrayList<>();

            // Same search logic as authenticated users
            if (query.startsWith("#")) {
                searchResults.addAll(searchByHashtag(query, null, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            } else if (Constant.isValidIndianPincode(query)) {
                searchResults.addAll(searchByPincode(query, null, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            } else if (Constant.isValidIndianStatePrefix(query)) {
                searchResults.addAll(searchByStatePrefix(query, null, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            } else if (query.matches(Constant.INDIAN_DISTRICT_PREFIX_PATTERN)) {
                searchResults.addAll(searchByDistrictPrefix(query, null, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            } else if (isStateName(query)) {
                searchResults.addAll(searchByStateName(query, null, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            } else if (isDistrictName(query)) {
                searchResults.addAll(searchByDistrictName(query, null, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            } else {
                searchResults.addAll(searchByContent(query, null, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            }

            List<PostResponse> postResponses = searchResults.stream()
                    .filter(post -> post != null &&
                            post.getStatus() == PostStatus.ACTIVE &&
                            post.getUser() != null &&
                            post.getUser().getIsActive() &&
                            PostUtility.isPostVisibleToAnonymousUser(post))
                    .distinct()
                    .sorted((p1, p2) -> p2.getId().compareTo(p1.getId()))
                    .limit(setup.getValidatedLimit())
                    .map(post -> postService.convertToPostResponse(post, null))
                    .collect(Collectors.toList());

            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            PaginationUtils.logPaginationResults("searchPostsAnonymous", postResponses,
                    response.isHasMore(), response.getNextCursor());

            return response;

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to search posts for query: '{}' by anonymous user", searchQuery, e);
            return PaginationUtils.handlePaginationError("searchPostsAnonymous", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Advanced search with multiple filters and cursor-based pagination
     */
    public PaginatedResponse<PostResponse> advancedSearchPosts(String query, String state, String district,
                                                               String pincode, User currentUser,
                                                               Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("advancedSearchPosts", beforePostId, limit);
            List<Post> searchResults = new ArrayList<>();

            log.info("Advanced search - query: '{}', state: '{}', district: '{}', pincode: '{}', user: {}",
                    query, state, district, pincode, currentUser.getActualUsername());

            // Search by pincode if provided
            if (pincode != null && Constant.isValidIndianPincode(pincode)) {
                searchResults.addAll(searchByPincode(pincode, currentUser, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            }
            // Search by district if provided
            else if (district != null && !district.trim().isEmpty()) {
                searchResults.addAll(searchByDistrictName(district.trim(), currentUser, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            }
            // Search by state if provided
            else if (state != null && !state.trim().isEmpty()) {
                searchResults.addAll(searchByStateName(state.trim(), currentUser, setup.getSanitizedCursor(), setup.getValidatedLimit()));
            }

            // Filter by content if query is provided
            if (query != null && !query.trim().isEmpty()) {
                List<Post> contentFilteredPosts = searchResults.stream()
                        .filter(post -> post.getContent().toLowerCase()
                                .contains(query.toLowerCase()))
                        .collect(Collectors.toList());

                if (!searchResults.isEmpty()) {
                    searchResults = contentFilteredPosts;
                } else {
                    searchResults.addAll(searchByContent(query, currentUser, setup.getSanitizedCursor(), setup.getValidatedLimit()));
                }
            }

            List<PostResponse> postResponses = searchResults.stream()
                    .filter(post -> post != null &&
                            post.getStatus() == PostStatus.ACTIVE &&
                            post.getUser() != null &&
                            post.getUser().getIsActive() &&
                            PostUtility.isPostVisibleToUser(post, currentUser))
                    .distinct()
                    .sorted((p1, p2) -> p2.getId().compareTo(p1.getId()))
                    .limit(setup.getValidatedLimit())
                    .map(post -> postService.convertToPostResponse(post, currentUser))
                    .collect(Collectors.toList());

            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            PaginationUtils.logPaginationResults("advancedSearchPosts", postResponses,
                    response.isHasMore(), response.getNextCursor());

            return response;

        } catch (Exception e) {
            log.error("Failed advanced search for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("advancedSearchPosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Get trending hashtags with cursor-based pagination
     */
    public PaginatedResponse<Map<String, Object>> getTrendingHashtags(User currentUser, Long beforeId,
                                                                      Integer limit, Integer days) {
        try {
            PostUtility.validateUser(currentUser);

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getTrendingHashtags", beforeId,
                    limit, Constant.DEFAULT_FEED_LIMIT, 50);
            int validatedDays = Math.min(Math.max(days != null ? days : 7, 1), 30);

            // Get recent active posts with cursor
            Date fromDate = new Date(System.currentTimeMillis() - (validatedDays * 24L * 60 * 60 * 1000));
            Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() * 3);

            List<Post> recentPosts;
            if (setup.hasCursor()) {
                recentPosts = postRepository.findByStatusAndCreatedAtAfterAndIdBeforeOrderByIdDesc(
                        PostStatus.ACTIVE, fromDate, setup.getSanitizedCursor(), pageable);
            } else {
                recentPosts = postRepository.findByStatusAndCreatedAtAfterOrderByIdDesc(
                        PostStatus.ACTIVE, fromDate, pageable);
            }

            // Count hashtag occurrences using our own method
            Map<String, Integer> hashtagCounts = new HashMap<>();
            for (Post post : recentPosts) {
                if (post != null && post.getContent() != null && PostUtility.isPostVisibleToUser(post, currentUser)) {
                    List<String> hashtags = extractHashtags(post.getContent());
                    for (String hashtag : hashtags) {
                        hashtagCounts.put(hashtag, hashtagCounts.getOrDefault(hashtag, 0) + 1);
                    }
                }
            }

            // Sort by count and prepare response with IDs for cursor
            List<Map<String, Object>> hashtagList = hashtagCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(setup.getValidatedLimit())
                    .map(entry -> {
                        Map<String, Object> hashtagInfo = new HashMap<>();
                        hashtagInfo.put("id", (long) entry.getKey().hashCode());
                        hashtagInfo.put("hashtag", entry.getKey());
                        hashtagInfo.put("count", entry.getValue());
                        hashtagInfo.put("searchQuery", entry.getKey());
                        return hashtagInfo;
                    })
                    .collect(Collectors.toList());

            PaginatedResponse<Map<String, Object>> response = PaginationUtils.createIdBasedResponse(
                    hashtagList, setup.getValidatedLimit(), item -> (Long) item.get("id"));

            PaginationUtils.logPaginationResults("getTrendingHashtags", hashtagList,
                    response.isHasMore(), response.getNextCursor());

            return response;

        } catch (Exception e) {
            log.error("Failed to get trending hashtags for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("getTrendingHashtags", e, 0);
        }
    }

    /**
     * Get search suggestions based on user's location and query
     */
    public Map<String, Object> getSearchSuggestions(String query, User currentUser) {
        try {
            PostUtility.validateUser(currentUser);

            Map<String, Object> suggestions = new HashMap<>();

            if (query == null || query.trim().isEmpty()) {
                // Provide location-based suggestions
                if (currentUser.hasPincode()) {
                    String userStatePrefix = PostUtility.getUserStatePrefix(currentUser);
                    String userState = PostUtility.getStateFromPincodePrefix(userStatePrefix);
                    suggestions.put("userState", userState);
                    suggestions.put("userPincode", currentUser.getPincode());
                    suggestions.put("suggestedSearches", Arrays.asList(
                            currentUser.getPincode(),
                            userState,
                            "nearby posts"
                    ));
                }
                return suggestions;
            }

            String searchTerm = query.trim().toLowerCase();
            List<String> stateSuggestions = new ArrayList<>();
            List<String> districtSuggestions = new ArrayList<>();

            // State suggestions
            for (String state : PostUtility.STATE_TO_PINCODE_PREFIX.keySet()) {
                if (state.toLowerCase().contains(searchTerm)) {
                    stateSuggestions.add(state);
                    if (stateSuggestions.size() >= 5) break;
                }
            }

            // District suggestions
            try {
                List<String> allDistricts = pincodeLookupService.getAllDistricts();
                districtSuggestions = allDistricts.stream()
                        .filter(district -> district.toLowerCase().contains(searchTerm))
                        .limit(5)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.debug("Could not get district suggestions", e);
            }

            suggestions.put("states", stateSuggestions);
            suggestions.put("districts", districtSuggestions);
            suggestions.put("isPincode", Constant.isValidIndianPincode(query));
            suggestions.put("isStatePrefix", Constant.isValidIndianStatePrefix(query));
            suggestions.put("isDistrictPrefix", query.matches(Constant.INDIAN_DISTRICT_PREFIX_PATTERN));
            suggestions.put("isHashtag", query.startsWith("#"));

            return suggestions;

        } catch (Exception e) {
            log.error("Failed to get search suggestions for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Search posts by location proximity with cursor-based pagination
     */
    public PaginatedResponse<PostResponse> searchPostsByProximity(User currentUser, Double radiusKm,
                                                                  Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            if (!currentUser.hasPincode()) {
                throw new ValidationException("User must have a valid pincode for proximity search");
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("searchPostsByProximity", beforePostId, limit);
            double searchRadius = radiusKm != null ? Math.min(radiusKm, Constant.COUNTRY_DISTANCE_KM) : Constant.STATE_DISTANCE_KM;

            String userStatePrefix = PostUtility.getUserStatePrefix(currentUser);
            List<User> nearbyUsers = userRepository.findByPincodeStartingWithAndIsActiveTrue(userStatePrefix);

            List<Post> proximityPosts;
            if (setup.hasCursor()) {
                proximityPosts = postRepository.findByUserInAndStatusAndIdBeforeOrderByIdDesc(
                        nearbyUsers, PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                proximityPosts = postRepository.findByUserInAndStatusOrderByIdDesc(
                        nearbyUsers, PostStatus.ACTIVE, setup.toPageable());
            }

            List<PostResponse> postResponses = proximityPosts.stream()
                    .filter(post -> post != null && PostUtility.isPostVisibleToUser(post, currentUser))
                    .limit(setup.getValidatedLimit())
                    .map(post -> postService.convertToPostResponse(post, currentUser))
                    .collect(Collectors.toList());

            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            PaginationUtils.logPaginationResults("searchPostsByProximity", postResponses,
                    response.isHasMore(), response.getNextCursor());

            return response;

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed proximity search for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchPostsByProximity", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Search posts by user role with cursor-based pagination
     */
    public PaginatedResponse<PostResponse> searchPostsByUserRole(String roleName, User currentUser,
                                                                 Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            if (roleName == null || roleName.trim().isEmpty()) {
                throw new ValidationException("Role name cannot be empty");
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("searchPostsByUserRole", beforePostId, limit);

            List<User> usersWithRole = userRepository.findByRoleNameAndIsActiveTrueOrderByIdDesc(roleName);

            if (usersWithRole.isEmpty()) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<Post> rolePosts;
            if (setup.hasCursor()) {
                rolePosts = postRepository.findByUserInAndStatusAndIdBeforeOrderByIdDesc(
                        usersWithRole, PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                rolePosts = postRepository.findByUserInAndStatusOrderByIdDesc(
                        usersWithRole, PostStatus.ACTIVE, setup.toPageable());
            }

            List<PostResponse> postResponses = rolePosts.stream()
                    .filter(post -> post != null && PostUtility.isPostVisibleToUser(post, currentUser))
                    .limit(setup.getValidatedLimit())
                    .map(post -> postService.convertToPostResponse(post, currentUser))
                    .collect(Collectors.toList());

            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            PaginationUtils.logPaginationResults("searchPostsByUserRole", postResponses,
                    response.isHasMore(), response.getNextCursor());

            return response;

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed role-based search for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchPostsByUserRole", e, PaginationUtils.validateLimit(limit));
        }
    }

    // ===== Private Helper Methods with Cursor Support =====

    private List<Post> searchByHashtag(String hashtag, User currentUser, Long beforePostId, int limit) {
        try {
            log.debug("Searching posts by hashtag: {}", hashtag);

            String searchHashtag = hashtag.startsWith("#") ? hashtag : "#" + hashtag;
            Pageable pageable = PaginationUtils.createPageable(limit);

            List<Post> posts;
            if (beforePostId != null) {
                posts = postRepository.findByStatusAndContentContainingIgnoreCaseAndIdBeforeOrderByIdDesc(
                        PostStatus.ACTIVE, searchHashtag, beforePostId, pageable);
            } else {
                posts = postRepository.findByStatusAndContentContainingIgnoreCaseOrderByIdDesc(
                        PostStatus.ACTIVE, searchHashtag, pageable);
            }

            log.debug("Found {} posts containing hashtag: '{}'", posts.size(), searchHashtag);
            return posts != null ? posts : Collections.emptyList();

        } catch (Exception e) {
            log.error("Failed to search posts by hashtag: {}", hashtag, e);
            return Collections.emptyList();
        }
    }

    private List<Post> searchByPincode(String pincode, User currentUser, Long beforePostId, int limit) {
        try {
            log.debug("Searching posts by pincode: {}", pincode);

            List<User> usersInPincode = userRepository.findByPincodeAndIsActiveTrue(pincode);
            List<Post> posts = new ArrayList<>();

            if (!usersInPincode.isEmpty()) {
                Pageable pageable = PaginationUtils.createPageable(limit);
                List<Post> userPosts;

                if (beforePostId != null) {
                    userPosts = postRepository.findByUserInAndStatusAndIdBeforeOrderByIdDesc(
                            usersInPincode, PostStatus.ACTIVE, beforePostId, pageable);
                } else {
                    userPosts = postRepository.findByUserInAndStatusOrderByIdDesc(
                            usersInPincode, PostStatus.ACTIVE, pageable);
                }

                if (userPosts != null) {
                    posts.addAll(userPosts);
                }
            }

            // Add broadcast posts targeting this pincode
            Pageable broadcastPageable = PaginationUtils.createPageable(limit / 2);
            List<Post> broadcastPosts;

            if (beforePostId != null) {
                broadcastPosts = postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingAndIdBeforeOrderByIdDesc(
                        BroadcastScope.AREA, PostStatus.ACTIVE, pincode, beforePostId, broadcastPageable);
            } else {
                broadcastPosts = postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByIdDesc(
                        BroadcastScope.AREA, PostStatus.ACTIVE, pincode, broadcastPageable);
            }

            if (broadcastPosts != null) {
                posts.addAll(broadcastPosts);
            }

            log.debug("Found {} posts for pincode: {}", posts.size(), pincode);
            return posts;

        } catch (Exception e) {
            log.error("Failed to search posts by pincode: {}", pincode, e);
            return Collections.emptyList();
        }
    }

    private List<Post> searchByStatePrefix(String statePrefix, User currentUser, Long beforePostId, int limit) {
        try {
            log.debug("Searching posts by state prefix: {}", statePrefix);

            List<User> usersInState = userRepository.findByPincodeStartingWithAndIsActiveTrue(statePrefix);
            List<Post> posts = new ArrayList<>();

            if (!usersInState.isEmpty()) {
                Pageable pageable = PaginationUtils.createPageable(limit);
                List<Post> userPosts;

                if (beforePostId != null) {
                    userPosts = postRepository.findByUserInAndStatusAndIdBeforeOrderByIdDesc(
                            usersInState, PostStatus.ACTIVE, beforePostId, pageable);
                } else {
                    userPosts = postRepository.findByUserInAndStatusOrderByIdDesc(
                            usersInState, PostStatus.ACTIVE, pageable);
                }

                if (userPosts != null) {
                    posts.addAll(userPosts);
                }
            }

            // Add state-level broadcast posts
            Pageable broadcastPageable = PaginationUtils.createPageable(limit / 2);
            List<Post> broadcastPosts;

            if (beforePostId != null) {
                broadcastPosts = postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingAndIdBeforeOrderByIdDesc(
                        BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix, beforePostId, broadcastPageable);
            } else {
                broadcastPosts = postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByIdDesc(
                        BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix, broadcastPageable);
            }

            if (broadcastPosts != null) {
                posts.addAll(broadcastPosts);
            }

            log.debug("Found {} posts for state prefix: {}", posts.size(), statePrefix);
            return posts;

        } catch (Exception e) {
            log.error("Failed to search posts by state prefix: {}", statePrefix, e);
            return Collections.emptyList();
        }
    }

    private List<Post> searchByDistrictPrefix(String districtPrefix, User currentUser, Long beforePostId, int limit) {
        try {
            log.debug("Searching posts by district prefix: {}", districtPrefix);

            List<User> usersInDistrict = userRepository.findByPincodeStartingWithAndIsActiveTrue(districtPrefix);
            List<Post> posts = new ArrayList<>();

            if (!usersInDistrict.isEmpty()) {
                Pageable pageable = PaginationUtils.createPageable(limit);
                List<Post> userPosts;

                if (beforePostId != null) {
                    userPosts = postRepository.findByUserInAndStatusAndIdBeforeOrderByIdDesc(
                            usersInDistrict, PostStatus.ACTIVE, beforePostId, pageable);
                } else {
                    userPosts = postRepository.findByUserInAndStatusOrderByIdDesc(
                            usersInDistrict, PostStatus.ACTIVE, pageable);
                }

                if (userPosts != null) {
                    posts.addAll(userPosts);
                }
            }

            // Add district-level broadcast posts
            Pageable broadcastPageable = PaginationUtils.createPageable(limit / 2);
            List<Post> broadcastPosts;

            if (beforePostId != null) {
                broadcastPosts = postRepository.findByBroadcastScopeAndStatusAndTargetDistrictsContainingAndIdBeforeOrderByIdDesc(
                        BroadcastScope.DISTRICT, PostStatus.ACTIVE, districtPrefix, beforePostId, broadcastPageable);
            } else {
                broadcastPosts = postRepository.findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByIdDesc(
                        BroadcastScope.DISTRICT, PostStatus.ACTIVE, districtPrefix, broadcastPageable);
            }

            if (broadcastPosts != null) {
                posts.addAll(broadcastPosts);
            }

            log.debug("Found {} posts for district prefix: {}", posts.size(), districtPrefix);
            return posts;

        } catch (Exception e) {
            log.error("Failed to search posts by district prefix: {}", districtPrefix, e);
            return Collections.emptyList();
        }
    }

    private List<Post> searchByStateName(String stateName, User currentUser, Long beforePostId, int limit) {
        try {
            log.debug("Searching posts by state name: {}", stateName);

            List<String> statePrefixes = PostUtility.convertStatesToPincodePrefixes(Arrays.asList(stateName));
            if (statePrefixes.isEmpty()) {
                log.warn("No pincode prefixes found for state: {}", stateName);
                return Collections.emptyList();
            }

            List<Post> allPosts = new ArrayList<>();

            for (String prefix : statePrefixes) {
                List<User> usersInState = userRepository.findByPincodeStartingWithAndIsActiveTrue(prefix);
                if (!usersInState.isEmpty()) {
                    Pageable pageable = PaginationUtils.createPageable(limit / statePrefixes.size());
                    List<Post> posts;

                    if (beforePostId != null) {
                        posts = postRepository.findByUserInAndStatusAndIdBeforeOrderByIdDesc(
                                usersInState, PostStatus.ACTIVE, beforePostId, pageable);
                    } else {
                        posts = postRepository.findByUserInAndStatusOrderByIdDesc(
                                usersInState, PostStatus.ACTIVE, pageable);
                    }

                    if (posts != null) {
                        allPosts.addAll(posts);
                    }
                }

                // Add state-level broadcast posts
                Pageable broadcastPageable = PaginationUtils.createPageable(limit / (statePrefixes.size() * 2));
                List<Post> broadcastPosts;

                if (beforePostId != null) {
                    broadcastPosts = postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingAndIdBeforeOrderByIdDesc(
                            BroadcastScope.STATE, PostStatus.ACTIVE, prefix, beforePostId, broadcastPageable);
                } else {
                    broadcastPosts = postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByIdDesc(
                            BroadcastScope.STATE, PostStatus.ACTIVE, prefix, broadcastPageable);
                }

                if (broadcastPosts != null) {
                    allPosts.addAll(broadcastPosts);
                }
            }

            log.debug("Found {} posts for state: {} (prefixes: {})",
                    allPosts.size(), stateName, statePrefixes);
            return allPosts;

        } catch (Exception e) {
            log.error("Failed to search posts by state name: {}", stateName, e);
            return Collections.emptyList();
        }
    }

    private List<Post> searchByDistrictName(String districtName, User currentUser, Long beforePostId, int limit) {
        try {
            log.debug("Searching posts by district name: {}", districtName);

            List<com.JanSahayak.AI.model.PincodeLookup> districtPincodes =
                    pincodeLookupService.findByDistrict(districtName);

            if (districtPincodes.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> pincodes = districtPincodes.stream()
                    .map(com.JanSahayak.AI.model.PincodeLookup::getPincode)
                    .collect(Collectors.toList());

            List<Post> allPosts = new ArrayList<>();

            for (String pincode : pincodes) {
                List<User> usersInPincode = userRepository.findByPincodeAndIsActiveTrue(pincode);
                if (!usersInPincode.isEmpty()) {
                    Pageable pageable = PaginationUtils.createPageable(limit / pincodes.size());
                    List<Post> posts;

                    if (beforePostId != null) {
                        posts = postRepository.findByUserInAndStatusAndIdBeforeOrderByIdDesc(
                                usersInPincode, PostStatus.ACTIVE, beforePostId, pageable);
                    } else {
                        posts = postRepository.findByUserInAndStatusOrderByIdDesc(
                                usersInPincode, PostStatus.ACTIVE, pageable);
                    }

                    if (posts != null) {
                        allPosts.addAll(posts);
                    }
                }
            }

            log.debug("Found {} posts for district: {} ({} pincodes)",
                    allPosts.size(), districtName, pincodes.size());
            return allPosts;

        } catch (Exception e) {
            log.error("Failed to search posts by district name: {}", districtName, e);
            return Collections.emptyList();
        }
    }

    private List<Post> searchByContent(String keywords, User currentUser, Long beforePostId, int limit) {
        try {
            log.debug("Searching posts by content keywords: {}", keywords);

            Pageable pageable = PaginationUtils.createPageable(limit);
            List<Post> posts;

            if (beforePostId != null) {
                posts = postRepository.findByStatusAndContentContainingIgnoreCaseAndIdBeforeOrderByIdDesc(
                        PostStatus.ACTIVE, keywords, beforePostId, pageable);
            } else {
                posts = postRepository.findByStatusAndContentContainingIgnoreCaseOrderByIdDesc(
                        PostStatus.ACTIVE, keywords, pageable);
            }

            List<Post> allPosts = new ArrayList<>();
            if (posts != null) {
                allPosts.addAll(posts);
            }

            // Also search for hashtag version if not already a hashtag
            if (!keywords.startsWith("#")) {
                String hashtagVersion = "#" + keywords;
                List<Post> hashtagPosts;

                if (beforePostId != null) {
                    hashtagPosts = postRepository.findByStatusAndContentContainingIgnoreCaseAndIdBeforeOrderByIdDesc(
                            PostStatus.ACTIVE, hashtagVersion, beforePostId, pageable);
                } else {
                    hashtagPosts = postRepository.findByStatusAndContentContainingIgnoreCaseOrderByIdDesc(
                            PostStatus.ACTIVE, hashtagVersion, pageable);
                }

                if (hashtagPosts != null) {
                    allPosts.addAll(hashtagPosts);
                }
            }

            log.debug("Found {} posts containing keywords: '{}'", allPosts.size(), keywords);
            return allPosts;

        } catch (Exception e) {
            log.error("Failed to search posts by content: {}", keywords, e);
            return Collections.emptyList();
        }
    }

    // ===== Helper Methods =====

    private boolean isStateName(String query) {
        return PostUtility.STATE_TO_PINCODE_PREFIX.containsKey(query) ||
                PostUtility.STATE_TO_PINCODE_PREFIX.keySet().stream()
                        .anyMatch(stateName -> stateName.toLowerCase().contains(query.toLowerCase()));
    }

    private boolean isDistrictName(String query) {
        return query.length() > 3 &&
                !query.matches("\\d+") &&
                Character.isUpperCase(query.charAt(0));
    }

    /**
     * Extract hashtags from content - keeping original implementation
     */
    public List<String> extractHashtags(String content) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> hashtags = new ArrayList<>();
        String[] words = content.split("\\s+");

        for (String word : words) {
            if (word.startsWith("#") && word.length() > 1) {
                String cleanHashtag = word.replaceAll("[^#\\w]$", "").toLowerCase();
                if (cleanHashtag.length() > 1 && !hashtags.contains(cleanHashtag)) {
                    hashtags.add(cleanHashtag);
                }
            }
        }

        return hashtags;
    }
}