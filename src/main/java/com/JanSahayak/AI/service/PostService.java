package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.PostContentUpdateDto;
import com.JanSahayak.AI.DTO.PostCreateDto;
import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.exception.*;
import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepo postRepository;
    private final UserTaggingService userTaggingService;
    private final UserRepo userRepository;
    private final PostViewRepo postViewRepository;
    private final PostLikeRepo postLikeRepository;
    private final CommentRepo commentRepository;
    private final PinCodeLookupService pinCodeLookupService;
    private final UserTagRepo userTagRepository;

    @Value("${app.upload.dir:${user.home}/uploads/posts}")
    private String uploadDir;

    @Value("${app.upload.max-image-size:5242880}") // 5MB for images
    private long maxImageSize;

    @Value("${app.upload.max-video-size:536870912}") // 512MB for videos
    private long maxVideoSize;

    // File cleanup retry queue
    private final Queue<String> fileCleanupQueue = new LinkedList<>();

    // ===== Regular Post Creation Methods =====

    @Transactional(rollbackOn = Exception.class)
    public Post createPost(PostCreateDto postDto, User user, MultipartFile mediaFile) {
        log.info("Creating new post by user: {} (ID: {})", user.getActualUsername(), user.getId());

        try {
            PostUtility.validateUser(user);
            PostUtility.validatePostContent(postDto.getContent());

            // REQUIRED: Target pincode must be provided by user (no fallback to user's pincode)
            if (postDto.getTargetPincode() == null || postDto.getTargetPincode().trim().isEmpty()) {
                throw new ValidationException("Target pincode is required for creating posts");
            }

            PostUtility.validateTargetPincodeForUser(postDto.getTargetPincode());

            // Populate user location data
            pinCodeLookupService.populateUserLocationData(user);

            String fileName = null;
            if (mediaFile != null && !mediaFile.isEmpty()) {
                fileName = PostUtility.uploadMediaFile(mediaFile, user.getId(), uploadDir);
            }

            Post post = new Post();
            post.setContent(postDto.getContent().trim());
            post.setUser(user);
            post.setImageName(fileName);
            post.setStatus(PostStatus.ACTIVE);
            post.setCreatedAt(new Date());

            // For normal users, set AREA broadcast scope with user-provided pincode
            if (PostUtility.isNormalUser(user)) {
                post.setBroadcastScope(BroadcastScope.AREA);

                // Use the REQUIRED target pincode provided by user
                String targetPincode = postDto.getTargetPincode().trim();

                // Additional validation to ensure pincode exists in system
                if (!pinCodeLookupService.isValidPincode(targetPincode)) {
                    throw new ValidationException("Target pincode not found in system: " + targetPincode);
                }

                // Validate it's a valid Indian pincode format
                if (!Constant.isValidIndianPincode(targetPincode)) {
                    throw new ValidationException("Invalid Indian pincode format: " + targetPincode);
                }

                post.setTargetPincodes(targetPincode);

                log.info("Normal user post created with user-provided target pincode: {}", targetPincode);
            }

            // CRITICAL: Always set India as target country for this India-only app
            post.setTargetCountry(Constant.DEFAULT_TARGET_COUNTRY);

            // Save post first
            post = postRepository.save(post);

            // Process user tags in content
            try {
                userTaggingService.processUserTags(post);
            } catch (Exception e) {
                log.warn("Failed to process user tags for post: {}", post.getId(), e);
            }

            log.info("Post created successfully with ID: {}, status: {}, broadcast scope: {}, country: {}, target pincode: {}",
                    post.getId(), post.getStatus().getDisplayName(),
                    post.getBroadcastScope() != null ? post.getBroadcastScope().getDescription() : "None",
                    post.getTargetCountry(),
                    post.getTargetPincodes());

            return post;
        } catch (ValidationException | MediaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create post for user: {} (ID: {})", user.getActualUsername(), user.getId(), e);
            throw new ServiceException("Failed to create post: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackOn = Exception.class)
    public Post createPost(PostCreateDto postDto, User user) {
        return createPost(postDto, user, null);
    }

    // ===== Broadcasting Post Creation Methods =====

    @Transactional(rollbackOn = Exception.class)
    public Post createBroadcastPost(PostCreateDto postDto, User user, BroadcastScope broadcastScope,
                                    String targetCountry, List<String> targetStates,
                                    List<String> targetDistricts, List<String> targetPincodes,
                                    MultipartFile mediaFile) {
        log.info("Creating broadcast post by user: {} (ID: {}) with scope: {}",
                user.getActualUsername(), user.getId(), broadcastScope);

        try {
            PostUtility.validateUser(user);
            PostUtility.validateBroadcastPermission(user);
            PostUtility.validatePostContent(postDto.getContent());
            PostUtility.validateBroadcastScope(broadcastScope, targetCountry, targetStates, targetDistricts, targetPincodes);

            // Populate user location data
            pinCodeLookupService.populateUserLocationData(user);

            String fileName = null;
            if (mediaFile != null && !mediaFile.isEmpty()) {
                fileName = PostUtility.uploadMediaFile(mediaFile, user.getId(), uploadDir);
            }

            Post post = new Post();
            post.setContent(postDto.getContent().trim());
            post.setUser(user);
            post.setImageName(fileName);
            post.setStatus(PostStatus.ACTIVE);
            post.setCreatedAt(new Date());

            // Set broadcasting parameters with pincode prefixes
            post.setBroadcastScope(broadcastScope);

            // CRITICAL: Always set India as target country for this India-only app
            post.setTargetCountry(Constant.DEFAULT_TARGET_COUNTRY);

            // Convert state names to pincode prefixes
            if (targetStates != null && !targetStates.isEmpty()) {
                String statePrefixesString = PostUtility.convertStatesToTargetString(targetStates);
                post.setTargetStates(statePrefixesString);
            }

            // Convert district names to pincode prefixes
            if (targetDistricts != null && !targetDistricts.isEmpty()) {
                String districtPrefixesString = PostUtility.convertDistrictsToTargetString(targetDistricts, pinCodeLookupService);
                post.setTargetDistricts(districtPrefixesString);
            }

            // Convert pincode list to target string
            if (targetPincodes != null && !targetPincodes.isEmpty()) {
                String pincodesString = PostUtility.convertPincodesToTargetString(targetPincodes);
                post.setTargetPincodes(pincodesString);
            }

            // Save post first
            post = postRepository.save(post);

            // Process user tags in content
            try {
                userTaggingService.processUserTags(post);
            } catch (Exception e) {
                log.warn("Failed to process user tags for broadcast post: {}", post.getId(), e);
            }

            // CRITICAL: Log government/department country-wide broadcasts
            if (post.isCountryWideGovernmentBroadcast()) {
                PostUtility.logCountryBroadcast(post, user);
            }

            log.info("Broadcast post created successfully - ID: {}, scope: {}, country: {}, targets: states={}, districts={}, pincodes={}",
                    post.getId(), broadcastScope.getDescription(), Constant.DEFAULT_TARGET_COUNTRY,
                    targetStates != null ? targetStates.size() : 0,
                    targetDistricts != null ? targetDistricts.size() : 0,
                    targetPincodes != null ? targetPincodes.size() : 0);

            return post;
        } catch (ValidationException | MediaValidationException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create broadcast post for user: {} (ID: {})", user.getActualUsername(), user.getId(), e);
            throw new ServiceException("Failed to create broadcast post: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackOn = Exception.class)
    public Post createCountryWideBroadcast(PostCreateDto postDto, User user, MultipartFile mediaFile) {
        PostUtility.validateBroadcastPermission(user);

        // CRITICAL: Create country-wide broadcast that will be visible to ALL users in India
        Post post = createBroadcastPost(postDto, user, BroadcastScope.COUNTRY, Constant.DEFAULT_TARGET_COUNTRY,
                null, null, null, mediaFile);

        // Log this critical broadcast that affects all users
        log.info("COUNTRY-WIDE BROADCAST CREATED - Post ID: {}, User: {} ({}), Visible to ALL Indian users",
                post.getId(), user.getActualUsername(), user.getRole().getName());

        return post;
    }

    @Transactional(rollbackOn = Exception.class)
    public Post createStateLevelBroadcast(PostCreateDto postDto, User user, List<String> targetStates,
                                          MultipartFile mediaFile) {
        PostUtility.validateBroadcastPermission(user);
        PostUtility.validateTargetStates(targetStates);
        return createBroadcastPost(postDto, user, BroadcastScope.STATE, Constant.DEFAULT_TARGET_COUNTRY,
                targetStates, null, null, mediaFile);
    }

    @Transactional(rollbackOn = Exception.class)
    public Post createDistrictLevelBroadcast(PostCreateDto postDto, User user,
                                             List<String> targetStates, List<String> targetDistricts,
                                             MultipartFile mediaFile) {
        PostUtility.validateBroadcastPermission(user);
        PostUtility.validateTargetDistricts(targetDistricts);
        return createBroadcastPost(postDto, user, BroadcastScope.DISTRICT, Constant.DEFAULT_TARGET_COUNTRY,
                targetStates, targetDistricts, null, mediaFile);
    }

    @Transactional(rollbackOn = Exception.class)
    public Post createAreaLevelBroadcast(PostCreateDto postDto, User user, List<String> targetPincodes,
                                         MultipartFile mediaFile) {
        PostUtility.validateBroadcastPermission(user);
        PostUtility.validateTargetPincodesWithLookup(targetPincodes, pinCodeLookupService);
        return createBroadcastPost(postDto, user, BroadcastScope.AREA, Constant.DEFAULT_TARGET_COUNTRY,
                null, null, targetPincodes, mediaFile);
    }

    // ===== Broadcasting Query Methods (Updated with Pagination) =====

    public PaginatedResponse<Post> getAllBroadcastPosts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllBroadcastPosts", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByBroadcastScopeIsNotNullAndIdLessThanOrderByCreatedAtDesc(
                        setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByBroadcastScopeIsNotNullOrderByCreatedAtDesc(setup.toPageable());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getAllBroadcastPosts", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get all broadcast posts", e);
            return PaginationUtils.handlePaginationError("getAllBroadcastPosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getActiveBroadcastPosts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getActiveBroadcastPosts", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByBroadcastScopeIsNotNullAndStatusAndIdLessThanOrderByCreatedAtDesc(
                        PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByBroadcastScopeIsNotNullAndStatusOrderByCreatedAtDesc(
                        PostStatus.ACTIVE, setup.toPageable());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getActiveBroadcastPosts", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get active broadcast posts", e);
            return PaginationUtils.handlePaginationError("getActiveBroadcastPosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getBroadcastPostsByScope(BroadcastScope scope, Long beforeId, Integer limit) {
        try {
            PostUtility.validateBroadcastScope(scope);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getBroadcastPostsByScope", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByBroadcastScopeAndIdLessThanOrderByCreatedAtDesc(
                        scope, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByBroadcastScopeOrderByCreatedAtDesc(scope, setup.toPageable());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getBroadcastPostsByScope", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get broadcast posts by scope: {}", scope, e);
            return PaginationUtils.handlePaginationError("getBroadcastPostsByScope", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * CRITICAL: Get posts visible to user - Government country-wide broadcasts are visible to ALL users
     */
    public PaginatedResponse<Post> getVisiblePostsForUser(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getVisiblePostsForUser", beforeId, limit);

            List<Post> allPosts;
            if (setup.hasCursor()) {
                allPosts = postRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(
                        PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                allPosts = postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.toPageable());
            }

            List<Post> visiblePosts = allPosts.stream()
                    .filter(post -> PostUtility.isPostVisibleToUser(post, user))
                    .collect(Collectors.toList());

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(visiblePosts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getVisiblePostsForUser", visiblePosts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get visible posts for user: {}", user.getActualUsername(), e);
            return PaginationUtils.handlePaginationError("getVisiblePostsForUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * CRITICAL: Get broadcast posts visible to user - Government country-wide broadcasts are visible to ALL users
     */
    public PaginatedResponse<Post> getBroadcastPostsVisibleToUser(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getBroadcastPostsVisibleToUser", beforeId, limit);

            PaginatedResponse<Post> broadcastPosts = getActiveBroadcastPosts(beforeId, setup.getValidatedLimit());

            List<Post> visiblePosts = broadcastPosts.getData().stream()
                    .filter(post -> PostUtility.isPostVisibleToUser(post, user))
                    .collect(Collectors.toList());

            PaginatedResponse<Post> response = PaginatedResponse.of(
                    visiblePosts, broadcastPosts.isHasMore(), broadcastPosts.getNextCursor(), setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getBroadcastPostsVisibleToUser", visiblePosts,
                    response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get broadcast posts visible to user: {}", user.getActualUsername(), e);
            return PaginationUtils.handlePaginationError("getBroadcastPostsVisibleToUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getAllCountryWideBroadcasts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllCountryWideBroadcasts", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByBroadcastScopeAndTargetCountryAndIdLessThanOrderByCreatedAtDesc(
                        BroadcastScope.COUNTRY, Constant.DEFAULT_TARGET_COUNTRY, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByBroadcastScopeAndTargetCountryOrderByCreatedAtDesc(
                        BroadcastScope.COUNTRY, Constant.DEFAULT_TARGET_COUNTRY, setup.toPageable());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getAllCountryWideBroadcasts", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get all country-wide broadcasts", e);
            return PaginationUtils.handlePaginationError("getAllCountryWideBroadcasts", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getActiveCountryWideBroadcasts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getActiveCountryWideBroadcasts", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByBroadcastScopeAndStatusAndTargetCountryAndIdLessThanOrderByCreatedAtDesc(
                        BroadcastScope.COUNTRY, PostStatus.ACTIVE, Constant.DEFAULT_TARGET_COUNTRY,
                        setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByBroadcastScopeAndStatusAndTargetCountryOrderByCreatedAtDesc(
                        BroadcastScope.COUNTRY, PostStatus.ACTIVE, Constant.DEFAULT_TARGET_COUNTRY, setup.toPageable());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getActiveCountryWideBroadcasts", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get active country-wide broadcasts", e);
            return PaginationUtils.handlePaginationError("getActiveCountryWideBroadcasts", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getCountryWideBroadcasts(Long beforeId, Integer limit) {
        return getBroadcastPostsByScope(BroadcastScope.COUNTRY, beforeId, limit);
    }

    public PaginatedResponse<Post> getStateLevelBroadcasts(String state, Long beforeId, Integer limit) {
        try {
            if (state == null || state.trim().isEmpty()) {
                throw new ValidationException("State cannot be empty");
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getStateLevelBroadcasts", beforeId, limit);

            // Convert state name to pincode prefix
            List<String> statePrefixes = PostUtility.convertStatesToPincodePrefixes(Arrays.asList(state.trim()));
            if (statePrefixes.isEmpty()) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            String statePrefix = statePrefixes.get(0);
            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByBroadcastScopeAndTargetStatesContainingAndIdLessThanOrderByCreatedAtDesc(
                        BroadcastScope.STATE, statePrefix, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByBroadcastScopeAndTargetStatesContainingOrderByCreatedAtDesc(
                        BroadcastScope.STATE, statePrefix);
                // Manually limit results since this method doesn't support Pageable
                posts = posts.stream().limit(setup.getValidatedLimit()).collect(Collectors.toList());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getStateLevelBroadcasts", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get state level broadcasts for state: {}", state, e);
            return PaginationUtils.handlePaginationError("getStateLevelBroadcasts", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getDistrictLevelBroadcasts(String district, Long beforeId, Integer limit) {
        try {
            if (district == null || district.trim().isEmpty()) {
                throw new ValidationException("District cannot be empty");
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getDistrictLevelBroadcasts", beforeId, limit);

            // For districts, we need to use 3-digit prefix lookup
            List<String> districtPrefixes = PostUtility.convertDistrictsToPincodePrefixes(
                    Arrays.asList(district.trim()), pinCodeLookupService);

            if (districtPrefixes.isEmpty()) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            // Search for posts containing any of the district prefixes
            List<Post> allDistrictPosts = new ArrayList<>();
            for (String prefix : districtPrefixes) {
                List<Post> posts;
                if (setup.hasCursor()) {
                    posts = postRepository.findByBroadcastScopeAndTargetDistrictsContainingAndIdLessThanOrderByCreatedAtDesc(
                            BroadcastScope.DISTRICT, prefix, setup.getSanitizedCursor(), setup.toPageable());
                } else {
                    posts = postRepository.findByBroadcastScopeAndTargetDistrictsContainingOrderByCreatedAtDesc(
                            BroadcastScope.DISTRICT, prefix);
                }
                if (posts != null) {
                    allDistrictPosts.addAll(posts);
                }
            }

            // Remove duplicates and limit
            List<Post> distinctPosts = allDistrictPosts.stream()
                    .distinct()
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(distinctPosts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getDistrictLevelBroadcasts", distinctPosts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get district level broadcasts for district: {}", district, e);
            return PaginationUtils.handlePaginationError("getDistrictLevelBroadcasts", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getAreaLevelBroadcasts(String pincode, Long beforeId, Integer limit) {
        try {
            if (!Constant.isValidIndianPincode(pincode)) {
                throw new ValidationException("Invalid Indian pincode format");
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAreaLevelBroadcasts", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByBroadcastScopeAndTargetPincodesContainingAndIdLessThanOrderByCreatedAtDesc(
                        BroadcastScope.AREA, pincode, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByBroadcastScopeAndTargetPincodesContainingOrderByCreatedAtDesc(
                        BroadcastScope.AREA, pincode);
                // Manually limit results since this method doesn't support Pageable
                posts = posts.stream().limit(setup.getValidatedLimit()).collect(Collectors.toList());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getAreaLevelBroadcasts", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get area level broadcasts for pincode: {}", pincode, e);
            return PaginationUtils.handlePaginationError("getAreaLevelBroadcasts", e, PaginationUtils.validateLimit(limit));
        }
    }

    // ===== Broadcasting Statistics Methods =====

    public Map<String, Long> getBroadcastStatistics() {
        try {
            Map<String, Long> stats = new HashMap<>();

            stats.put("totalBroadcasts", postRepository.countByBroadcastScopeIsNotNull());
            stats.put("activeBroadcasts", postRepository.countByBroadcastScopeIsNotNullAndStatus(PostStatus.ACTIVE));

            // CRITICAL: Add specific stats for government country-wide broadcasts
            Long countryBroadcasts = postRepository.countByBroadcastScopeAndTargetCountry(BroadcastScope.COUNTRY, Constant.DEFAULT_TARGET_COUNTRY);
            stats.put("countryWideBroadcasts", countryBroadcasts != null ? countryBroadcasts : 0L);

            for (BroadcastScope scope : BroadcastScope.values()) {
                Long count = postRepository.countByBroadcastScope(scope);
                stats.put("broadcasts" + scope.name(), count != null ? count : 0L);

                Long activeCount = postRepository.countByBroadcastScopeAndStatus(scope, PostStatus.ACTIVE);
                stats.put("activeBroadcasts" + scope.name(), activeCount != null ? activeCount : 0L);
            }

            return stats;
        } catch (Exception e) {
            log.error("Failed to get broadcast statistics", e);
            throw new ServiceException("Failed to get broadcast statistics: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getBroadcastAnalytics(User user, int days) {
        try {
            PostUtility.validateUser(user);
            PostUtility.validateBroadcastPermission(user);

            if (days <= 0) {
                throw new ValidationException("Days must be positive");
            }

            LocalDateTime startDate = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
            Timestamp timestamp = Timestamp.valueOf(startDate);

            Map<String, Object> analytics = new HashMap<>();

            // Basic statistics
            analytics.put("totalBroadcastsCreated", postRepository.countByUserAndBroadcastScopeIsNotNull(user));
            analytics.put("recentBroadcasts", postRepository.countByUserAndBroadcastScopeIsNotNullAndCreatedAtAfter(user, timestamp));

            // CRITICAL: Add country-wide broadcast statistics for government users
            if (PostUtility.canCreateBroadcast(user)) {
                Long countryBroadcasts = postRepository.countByUserAndBroadcastScopeAndTargetCountry(user, BroadcastScope.COUNTRY, Constant.DEFAULT_TARGET_COUNTRY);
                analytics.put("countryWideBroadcasts", countryBroadcasts != null ? countryBroadcasts : 0L);
            }

            // Scope breakdown
            Map<String, Long> scopeBreakdown = new HashMap<>();
            for (BroadcastScope scope : BroadcastScope.values()) {
                Long count = postRepository.countByUserAndBroadcastScope(user, scope);
                scopeBreakdown.put(scope.name(), count != null ? count : 0L);
            }
            analytics.put("scopeBreakdown", scopeBreakdown);

            // Engagement metrics
            List<Post> userBroadcasts = postRepository.findByUserAndBroadcastScopeIsNotNull(user);
            if (userBroadcasts != null && !userBroadcasts.isEmpty()) {
                double avgLikes = userBroadcasts.stream().mapToInt(Post::getLikeCount).average().orElse(0.0);
                double avgComments = userBroadcasts.stream().mapToInt(Post::getCommentCount).average().orElse(0.0);
                double avgViews = userBroadcasts.stream().mapToInt(Post::getViewCount).average().orElse(0.0);

                analytics.put("averageLikes", Math.round(avgLikes * 100.0) / 100.0);
                analytics.put("averageComments", Math.round(avgComments * 100.0) / 100.0);
                analytics.put("averageViews", Math.round(avgViews * 100.0) / 100.0);
            }

            return analytics;
        } catch (Exception e) {
            log.error("Failed to get broadcast analytics for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get broadcast analytics: " + e.getMessage(), e);
        }
    }

    // ===== Broadcasting Update Methods =====

    @Transactional(rollbackOn = Exception.class)
    public Post updateBroadcastTargets(Long postId, BroadcastScope newScope,
                                       List<String> targetStates, List<String> targetDistricts,
                                       List<String> targetPincodes, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            PostUtility.validateBroadcastPermission(currentUser);

            Post post = findById(postId);

            if (!post.isBroadcastPost()) {
                throw new ValidationException("Post is not a broadcast post");
            }

            if (!PostUtility.isPostOwner(post, currentUser) && !PostUtility.isAdmin(currentUser)) {
                throw new SecurityException("Only post creator or admin can update broadcast targets");
            }

            if (!PostUtility.postAllowsUpdates(post)) {
                throw new SecurityException("Cannot update broadcast targets for posts with status: " +
                        post.getStatus().getDisplayName());
            }

            PostUtility.validateBroadcastScope(newScope, Constant.DEFAULT_TARGET_COUNTRY, targetStates, targetDistricts, targetPincodes);

            // Update broadcast parameters with pincode prefixes
            post.setBroadcastScope(newScope);

            // Convert and set target data using utility methods
            String statePrefixesString = PostUtility.convertStatesToTargetString(targetStates);
            String districtPrefixesString = PostUtility.convertDistrictsToTargetString(targetDistricts, pinCodeLookupService);
            String pincodesString = PostUtility.convertPincodesToTargetString(targetPincodes);

            post.setTargetStates(statePrefixesString);
            post.setTargetDistricts(districtPrefixesString);
            post.setTargetPincodes(pincodesString);
            post.setUpdatedAt(new Date());

            // CRITICAL: Always ensure India as target country
            post.setTargetCountry(Constant.DEFAULT_TARGET_COUNTRY);

            Post updatedPost = postRepository.save(post);

            // Log critical country-wide broadcast updates
            if (PostUtility.isAllIndiaGovernmentBroadcast(updatedPost)) {
                log.info("CRITICAL: Government country-wide broadcast updated - Post ID: {}, User: {} ({}), Now visible to ALL users",
                        postId, currentUser.getActualUsername(), currentUser.getRole().getName());
            }

            log.info("Updated broadcast targets for post ID: {} - new scope: {}, states: {}, districts: {}, pincodes: {}",
                    postId, newScope.getDescription(),
                    targetStates != null ? targetStates.size() : 0,
                    targetDistricts != null ? targetDistricts.size() : 0,
                    targetPincodes != null ? targetPincodes.size() : 0);

            return updatedPost;
        } catch (ValidationException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update broadcast targets for post: {}", postId, e);
            throw new ServiceException("Failed to update broadcast targets: " + e.getMessage(), e);
        }
    }

    // ===== Post Management Methods =====

    @Transactional(rollbackOn = Exception.class)
    public Post updatePostMedia(Long postId, MultipartFile mediaFile, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);

            Post post = findById(postId);

            if (!PostUtility.isPostOwner(post, currentUser)) {
                throw new SecurityException("Only post creator can update post media");
            }

            if (!PostUtility.postAllowsUpdates(post)) {
                throw new SecurityException("Cannot update media for posts with status: " + post.getStatus().getDisplayName());
            }

            String oldFileName = post.getImageName();

            String fileName = null;
            if (mediaFile != null && !mediaFile.isEmpty()) {
                fileName = PostUtility.uploadMediaFile(mediaFile, currentUser.getId(), uploadDir);
            }

            post.setImageName(fileName);
            post.setUpdatedAt(new Date());

            Post updatedPost = postRepository.save(post);

            if (oldFileName != null && !oldFileName.trim().isEmpty()) {
                CompletableFuture.runAsync(() -> PostUtility.deleteMediaFile(oldFileName, uploadDir));
            }

            log.info("Updated media for post ID: {}, status: {}, new media: {}",
                    post.getId(), post.getStatus().getDisplayName(), fileName != null ? fileName : "removed");

            return updatedPost;
        } catch (SecurityException | MediaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update post media for post ID: {} by user: {}", postId,
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to update post media: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackOn = Exception.class)
    public Post removePostMedia(Long postId, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);

            Post post = findById(postId);

            if (!PostUtility.isPostOwner(post, currentUser)) {
                throw new SecurityException("Only post creator can remove post media");
            }

            if (!PostUtility.postAllowsUpdates(post)) {
                throw new SecurityException("Cannot remove media for posts with status: " + post.getStatus().getDisplayName());
            }

            String oldFileName = post.getImageName();

            if (post.hasImage()) {
                post.setImageName(null);
                post.setUpdatedAt(new Date());
            }

            Post updatedPost = postRepository.save(post);

            if (oldFileName != null && !oldFileName.trim().isEmpty()) {
                CompletableFuture.runAsync(() -> PostUtility.deleteMediaFile(oldFileName, uploadDir));
            }

            log.info("Removed media from post ID: {}, status: {}", post.getId(), post.getStatus().getDisplayName());

            return updatedPost;
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to remove post media for post ID: {} by user: {}", postId,
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to remove post media: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackOn = Exception.class)
    public Post updatePostContent(Long postId, String newContent, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            PostUtility.validatePostContent(newContent);

            Post post = findById(postId);

            if (!PostUtility.isPostOwner(post, currentUser)) {
                throw new SecurityException("Only post creator can update post content");
            }

            if (!PostUtility.postAllowsUpdates(post)) {
                throw new SecurityException("Cannot update content for posts with status: " + post.getStatus().getDisplayName());
            }

            String oldContent = post.getContent();
            post.setContent(newContent.trim());
            post.setUpdatedAt(new Date());

            try {
                userTaggingService.updatePostTags(post, newContent.trim());
            } catch (Exception e) {
                log.warn("Failed to update tags for post: {}", postId, e);
            }

            Post updatedPost = postRepository.save(post);

            // Log critical updates to government broadcasts
            if (PostUtility.isAllIndiaGovernmentBroadcast(updatedPost)) {
                log.info("CRITICAL: Government country-wide broadcast content updated - Post ID: {}, User: {} ({})",
                        postId, currentUser.getActualUsername(), currentUser.getRole().getName());
            }

            log.info("Updated content for post ID: {}, status: {}, content changed: {}",
                    post.getId(), post.getStatus().getDisplayName(), !Objects.equals(oldContent, newContent));

            return updatedPost;
        } catch (PostNotFoundException | SecurityException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update post content: {} by user: {}",
                    postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to update post content: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackOn = Exception.class)
    public Post updatePostContent(Long postId, PostContentUpdateDto contentUpdateDto, User currentUser) {
        try {
            if (contentUpdateDto == null) {
                throw new ValidationException("Content update data cannot be null");
            }
            return updatePostContent(postId, contentUpdateDto.getContent(), currentUser);
        } catch (Exception e) {
            log.error("Failed to update post content via DTO: {} by user: {}",
                    postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw e;
        }
    }

    @Transactional(rollbackOn = Exception.class)
    public Post updatePostResolution(Long postId, Boolean isResolved, User user, String updateMessage) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(user);

            Post post = findById(postId);

            // Check if user has permission to update resolution (tagged users, departments, admins)
            boolean canUpdate = false;
            if (userTaggingService.isUserTaggedInPost(post, user) ||
                    PostUtility.isDepartment(user) || PostUtility.isAdmin(user)) {
                canUpdate = true;
            }

            if (!canUpdate) {
                throw new SecurityException("Only tagged users, department users, or admin can update post resolution status");
            }

            if (post.getStatus() == null) {
                throw new ServiceException("Post status is invalid");
            }

            PostStatus newStatus = isResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;

            if (!post.getStatus().canTransitionTo(newStatus)) {
                throw new SecurityException("Cannot transition from " + post.getStatus().getDisplayName() +
                        " to " + newStatus.getDisplayName());
            }

            if (isResolved) {
                post.markAsResolved(updateMessage != null ? updateMessage.trim() : null);
                log.info("Post ID: {} marked as resolved by user: {} (ID: {})",
                        postId, user.getActualUsername(), user.getId());
            } else {
                post.markAsUnresolved();
                log.info("Post ID: {} marked as active by user: {} (ID: {})",
                        postId, user.getActualUsername(), user.getId());
            }

            if (updateMessage != null && !updateMessage.trim().isEmpty()) {
                try {
                    Comment statusComment = new Comment();
                    statusComment.setText("Status Update (" + post.getStatus().getDisplayName() + "): " + updateMessage.trim());
                    statusComment.setUser(user);
                    statusComment.setPost(post);
                    statusComment.setCreatedAt(new Date());
                    commentRepository.save(statusComment);
                } catch (Exception e) {
                    log.warn("Failed to create status update comment for post: {}", postId, e);
                }
            }

            return postRepository.save(post);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update post resolution for post ID: {} by user: {}",
                    postId, user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to update post resolution: " + e.getMessage(), e);
        }
    }

    // ===== Query Methods (Updated with Pagination) =====

    public PaginatedResponse<Post> getPostsTaggedWithUser(Long userId, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUserId(userId);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getPostsTaggedWithUser", beforeId, limit);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findPostsTaggedWithUserAndIdLessThan(user, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findPostsTaggedWithUser(user);
                // Manually limit results since this method doesn't support Pageable
                posts = posts.stream().limit(setup.getValidatedLimit()).collect(Collectors.toList());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getPostsTaggedWithUser", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get posts tagged with user: {}", userId, e);
            return PaginationUtils.handlePaginationError("getPostsTaggedWithUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    public Post findById(Long postId) {
        try {
            PostUtility.validatePostId(postId);
            return postRepository.findById(postId)
                    .orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));
        } catch (PostNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to find post by ID: {}", postId, e);
            throw new ServiceException("Failed to find post: " + e.getMessage(), e);
        }
    }

    public PaginatedResponse<Post> getAllPosts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllPosts", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByIdLessThanOrderByCreatedAtDesc(setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findAllOrderByCreatedAtDesc(setup.toPageable());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getAllPosts", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get all posts", e);
            return PaginationUtils.handlePaginationError("getAllPosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getAllActivePosts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllActivePosts", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(
                        PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.toPageable());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getAllActivePosts", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get all active posts", e);
            return PaginationUtils.handlePaginationError("getAllActivePosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getAllResolvedPosts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllResolvedPosts", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(
                        PostStatus.RESOLVED, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.RESOLVED, setup.toPageable());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getAllResolvedPosts", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get all resolved posts", e);
            return PaginationUtils.handlePaginationError("getAllResolvedPosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getPostsByUser(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getPostsByUser", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByUserAndIdLessThanOrderByCreatedAtDesc(user, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByUserOrderByCreatedAtDesc(user);
                // Manually limit results since this method doesn't support Pageable
                posts = posts.stream().limit(setup.getValidatedLimit()).collect(Collectors.toList());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getPostsByUser", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get posts by user: {}", user != null ? user.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("getPostsByUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getActivePostsByUser(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getActivePostsByUser", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByUserAndStatusAndIdLessThanOrderByCreatedAtDesc(user, PostStatus.ACTIVE,
                        setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByUserAndStatusOrderByCreatedAtDesc(user, PostStatus.ACTIVE);
                // Manually limit results since this method doesn't support Pageable
                posts = posts.stream().limit(setup.getValidatedLimit()).collect(Collectors.toList());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getActivePostsByUser", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get active posts by user: {}", user != null ? user.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("getActivePostsByUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getResolvedPostsByUser(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getResolvedPostsByUser", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByUserAndStatusAndIdLessThanOrderByCreatedAtDesc(user, PostStatus.RESOLVED,
                        setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByUserAndStatusOrderByCreatedAtDesc(user, PostStatus.RESOLVED);
                // Manually limit results since this method doesn't support Pageable
                posts = posts.stream().limit(setup.getValidatedLimit()).collect(Collectors.toList());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getResolvedPostsByUser", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get resolved posts by user: {}", user != null ? user.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("getResolvedPostsByUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    public Long countActivePosts() {
        try {
            Long count = postRepository.countByStatus(PostStatus.ACTIVE);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("Failed to count active posts", e);
            throw new ServiceException("Failed to count active posts: " + e.getMessage(), e);
        }
    }

    public Long countResolvedPosts() {
        try {
            Long count = postRepository.countByStatus(PostStatus.RESOLVED);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("Failed to count resolved posts", e);
            throw new ServiceException("Failed to count resolved posts: " + e.getMessage(), e);
        }
    }

    public PaginatedResponse<Post> getPostsWithMultipleUserTags(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getPostsWithMultipleUserTags", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findPostsWithMultipleUserTagsAndIdLessThan(setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findPostsWithMultipleUserTags();
                // Manually limit results since this method doesn't support Pageable
                posts = posts.stream().limit(setup.getValidatedLimit()).collect(Collectors.toList());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getPostsWithMultipleUserTags", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get posts with multiple user tags", e);
            return PaginationUtils.handlePaginationError("getPostsWithMultipleUserTags", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getTrendingPosts(int days, Long beforeId, Integer limit) {
        try {
            if (days <= 0) {
                throw new ValidationException("Days must be positive");
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getTrendingPosts", beforeId,
                    limit, Constant.DEFAULT_FEED_LIMIT, 1000); // Cap at 1000 for trending posts

            LocalDateTime startDate = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
            List<Post> posts;

            if (setup.hasCursor()) {
                posts = postRepository.findTrendingPostsWithCursor(
                        Timestamp.valueOf(startDate), setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findTrendingPosts(Timestamp.valueOf(startDate), setup.toPageable());
            }

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getTrendingPosts", posts, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get trending posts (days: {}, beforeId: {}, limit: {})", days, beforeId, limit, e);
            return PaginationUtils.handlePaginationError("getTrendingPosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    // ===== User Tagging Methods =====

    @Transactional(rollbackOn = Exception.class)
    public Post tagUsersToPost(Long postId, List<Long> userIds, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);

            if (userIds == null || userIds.isEmpty()) {
                throw new ValidationException("User IDs list cannot be empty");
            }

            for (Long userId : userIds) {
                PostUtility.validateUserId(userId);
            }

            Post post = findById(postId);

            if (!PostUtility.postAllowsUpdates(post)) {
                throw new SecurityException("Cannot add tags to posts with status: " + post.getStatus().getDisplayName());
            }

            if (!PostUtility.canUserModifyPostTags(post, currentUser)) {
                throw new SecurityException("Only post creator, department users, or admin can add user tags");
            }

            List<User> usersToTag = userRepository.findAllById(userIds);
            if (usersToTag.size() != userIds.size()) {
                List<Long> foundIds = usersToTag.stream().map(User::getId).collect(Collectors.toList());
                List<Long> missingIds = userIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
                throw new ValidationException("Users not found with IDs: " + missingIds);
            }

            int successCount = 0;
            for (User userToTag : usersToTag) {
                try {
                    userTaggingService.addUserTag(post, userToTag);
                    successCount++;
                } catch (Exception e) {
                    log.warn("Failed to tag user: {} to post: {}", userToTag.getActualUsername(), postId, e);
                }
            }

            log.info("Added {} user tags to post ID: {} (status: {}), attempted: {}",
                    successCount, post.getId(), post.getStatus().getDisplayName(), usersToTag.size());

            return post;
        } catch (PostNotFoundException | SecurityException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to tag users to post: {} by user: {}",
                    postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to tag users to post: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackOn = Exception.class)
    public Post removeUserTagFromPost(Long postId, Long userId, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUserId(userId);
            PostUtility.validateUser(currentUser);

            Post post = findById(postId);
            User userToRemove = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));

            if (!PostUtility.postAllowsUpdates(post)) {
                throw new SecurityException("Cannot remove tags from posts with status: " + post.getStatus().getDisplayName());
            }

            if (!PostUtility.canUserRemovePostTag(post, currentUser, userId)) {
                throw new SecurityException("Insufficient permissions to remove user tag");
            }

            try {
                userTaggingService.removeUserTag(post, userToRemove);

                log.info("Removed user tag for user: {} (ID: {}) from post ID: {} (status: {}) by user: {}",
                        userToRemove.getActualUsername(), userId, post.getId(),
                        post.getStatus().getDisplayName(), currentUser.getActualUsername());
            } catch (Exception e) {
                log.warn("Failed to remove user tag: {} from post: {}", userId, postId, e);
                throw new ServiceException("Failed to remove user tag: " + e.getMessage(), e);
            }

            return post;
        } catch (PostNotFoundException | UserNotFoundException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to remove user tag from post: {} by user: {}",
                    postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to remove user tag from post: " + e.getMessage(), e);
        }
    }

    // ===== Media Utility Methods =====

    public String getMediaFilePath(String fileName) {
        return PostUtility.getMediaFilePath(fileName, uploadDir);
    }

    public boolean isImageFile(String fileName) {
        return PostUtility.isImageFile(fileName);
    }

    public boolean isVideoFile(String fileName) {
        return PostUtility.isVideoFile(fileName);
    }

    public String getMediaType(String fileName) {
        return PostUtility.getMediaType(fileName);
    }

    public Map<String, Object> getMediaConstraints() {
        return PostUtility.createMediaConstraints(maxImageSize, maxVideoSize);
    }

    // ===== File Cleanup Methods =====

    public void processFileCleanupQueue() {
        int processedCount = 0;
        int maxRetries = 10;

        while (!fileCleanupQueue.isEmpty() && processedCount < maxRetries) {
            String fileName = fileCleanupQueue.poll();
            if (fileName != null) {
                try {
                    PostUtility.deleteMediaFile(fileName, uploadDir);
                    log.info("Successfully cleaned up file from retry queue: {}", fileName);
                    processedCount++;
                } catch (Exception e) {
                    log.warn("Failed to cleanup file from retry queue: {}", fileName, e);
                }
            }
        }

        if (processedCount > 0) {
            log.info("Processed {} files from cleanup queue, {} remaining",
                    processedCount, fileCleanupQueue.size());
        }
    }

    private void scheduleFileCleanupRetry(String fileName) {
        if (fileName != null && !fileName.trim().isEmpty()) {
            fileCleanupQueue.offer(fileName);
            if (fileCleanupQueue.size() > 1000) {
                String oldFile = fileCleanupQueue.poll();
                log.warn("Cleanup queue full, removing oldest entry: {}", oldFile);
            }
            log.debug("Added file to cleanup retry queue: {}", fileName);
        }
    }

    // ===== Post Response Conversion Methods =====

    /**
     * Convert Post entity to PostResponse DTO with user context
     * Uses only existing fields from PostResponse DTO
     */
    public PostResponse convertToPostResponse(Post post, User currentUser) {
        try {
            if (post == null) {
                return null;
            }
            boolean isLiked = false;
            if (currentUser != null) {
                // Check the database to see if a "like" record exists for this post and user.
                isLiked = postLikeRepository.findByPostAndUser(post, currentUser).isPresent();
            }

            PostResponse.PostResponseBuilder builder = PostResponse.builder()
                    // Basic Post Information
                    .id(post.getId())
                    .content(post.getContent())
                    .status(post.getStatus())
                    .createdAt(post.getCreatedAt())
                    .updatedAt(post.getUpdatedAt())

                    // Media Information
                    .imageName(post.getImageName())
                    .hasImage(post.hasImage())
                    .mediaType(post.hasImage() ? PostUtility.getMediaType(post.getImageName()) : null)

                    // Resolution Information
                    .isResolved(post.isResolved())
                    .resolvedAt(post.getResolvedAt())

                    // User Information
                    .userId(post.getUser() != null ? post.getUser().getId() : null)
                    .username(post.getUser() != null ? post.getUser().getActualUsername() : null)
                    .userDisplayName(post.getUser() != null ? post.getUser().getDisplayName() : null)
                    .userProfileImage(post.getUser() != null ? post.getUser().getProfileImage() : null)
                    .userPincode(post.getUser() != null ? post.getUser().getPincode() : null)

                    // Broadcasting Information
                    .broadcastScope(post.getBroadcastScope())
                    .broadcastScopeDescription(post.getBroadcastScopeDescription())
                    .isBroadcastPost(post.isBroadcastPost())
                    .targetCountry(post.getTargetCountry())
                    .targetStates(post.getTargetStatesList())
                    .targetDistricts(post.getTargetDistrictsList())
                    .targetPincodes(post.getTargetPincodesList())

                    // Engagement Metrics
                    .likeCount(post.getLikeCount())
                    .commentCount(post.getCommentCount())
                    .viewCount(post.getViewCount())
                    .taggedUserCount(post.getTaggedUserCount())

                    // User Interaction Status (defaults - implement actual logic if needed)
                    .isLikedByCurrentUser(isLiked)
                    .isViewedByCurrentUser(false) // TODO: Check if current user viewed this post

                    // Status Information
                    .statusDisplayName(post.getStatus() != null ? post.getStatus().getDisplayName() : null)
                    .canBeResolved(post.getStatus() == PostStatus.ACTIVE)
                    .allowsUpdates(PostUtility.postAllowsUpdates(post))
                    .isEligibleForDisplay(PostUtility.isPostEligibleForDisplay(post))

                    // Additional Metadata
                    .timeAgo(PostUtility.calculateTimeAgo(post.getCreatedAt()))
                    .isVisibleToCurrentUser(currentUser != null ? PostUtility.isPostVisibleToUser(post, currentUser) : false);

            // Add tagged users information
            try {
                PaginatedResponse<User> taggedUsersResponse = userTaggingService.getTaggedUsersInPost(post, null, Constant.MAX_TAGS_PER_POST * 10);
                List<User> taggedUsers = taggedUsersResponse.getData();

                // Simple usernames list
                List<String> taggedUsernames = taggedUsers.stream()
                        .map(User::getActualUsername)
                        .collect(Collectors.toList());
                builder.taggedUsernames(taggedUsernames);

                // Detailed tagged user info
                List<PostResponse.TaggedUserInfo> taggedUserInfos = convertToTaggedUserInfos(post);
                builder.taggedUsers(taggedUserInfos);

            } catch (Exception e) {
                log.warn("Failed to get tagged users for post: {}", post.getId(), e);
                builder.taggedUsernames(Collections.emptyList())
                        .taggedUsers(Collections.emptyList());
            }

            return builder.build();

        } catch (Exception e) {
            log.error("Failed to convert post to response: {} for user: {}",
                    post != null ? post.getId() : "null",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to convert post to response: " + e.getMessage(), e);
        }
    }

    /**
     * Convert tagged users to TaggedUserInfo objects
     */
    private List<PostResponse.TaggedUserInfo> convertToTaggedUserInfos(Post post) {
        try {
            List<UserTag> userTags = userTagRepository.findByPostAndIsActiveTrue(post);

            return userTags.stream()
                    .map(userTag -> PostResponse.TaggedUserInfo.builder()
                            .tagId(userTag.getId())
                            .userId(userTag.getTaggedUser().getId())
                            .username(userTag.getTaggedUser().getActualUsername())
                            .displayName(userTag.getTaggedUser().getDisplayName())
                            .profileImage(userTag.getTaggedUser().getProfileImage())
                            .pincode(userTag.getTaggedUser().getPincode())
                            .isActive(userTag.getIsActive())
                            .taggedAt(userTag.getTaggedAt())
                            .deactivatedAt(userTag.getDeactivatedAt())
                            .deactivationReason(userTag.getDeactivationReason())
                            .taggedByUserId(userTag.getTaggedBy() != null ? userTag.getTaggedBy().getId() : null)
                            .taggedByUsername(userTag.getTaggedBy() != null ? userTag.getTaggedBy().getActualUsername() : null)
                            .build())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Failed to convert tagged user infos for post: {}", post.getId(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Convert list of Posts to PostResponses
     */
    public PaginatedResponse<PostResponse> convertToPostResponses(List<Post> posts, User currentUser, int limit) {
        if (posts == null || posts.isEmpty()) {
            return PaginationUtils.createEmptyResponse(limit);
        }

        List<PostResponse> postResponses = posts.stream()
                .filter(Objects::nonNull)
                .map(post -> convertToPostResponse(post, currentUser))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return PaginationUtils.createIdBasedResponse(postResponses, limit, PostResponse::getId);
    }

    /**
     * Convert PaginatedResponse<Post> to PaginatedResponse<PostResponse>
     */
    public PaginatedResponse<PostResponse> convertPaginatedPostsToResponses(
            PaginatedResponse<Post> paginatedPosts, User currentUser) {

        if (paginatedPosts == null || paginatedPosts.getData() == null) {
            return PaginationUtils.createEmptyResponse(0);
        }

        List<PostResponse> postResponses = paginatedPosts.getData().stream()
                .filter(Objects::nonNull)
                .map(post -> convertToPostResponse(post, currentUser))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return PaginatedResponse.of(
                postResponses,
                paginatedPosts.isHasMore(),
                paginatedPosts.getNextCursor(),
                paginatedPosts.getLimit()
        );
    }
}