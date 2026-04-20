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
import com.JanSahayak.AI.model.PostShare.ShareType;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepo                 postRepository;
    private final UserTaggingService       userTaggingService;
    private final UserRepo                 userRepository;
    private final CommentRepo              commentRepository;
    private final PinCodeLookupService     pinCodeLookupService;
    private final UserTagRepo              userTagRepository;
    private final ContentValidationService contentValidationService;
    private final NotificationService      notificationService;
    private final PostInteractionService   postInteractionService;

    // ── Cloudinary media adapter (replaces DrivePostMediaAdapter / local disk) ─
    // Bean name kept as drivePostMedia so no controller or other caller needs updating.
    private final DrivePostMediaAdapter    drivePostMedia;

    // uploadDir kept only so PostUtility helper methods that read the value compile.
    // NOT used for actual file storage — Cloudinary handles all uploads.
    @Value("${app.upload.dir:${user.home}/uploads/posts}")
    private String uploadDir;

    @Value("${app.upload.max-image-size:5242880}")
    private long maxImageSize;

    @Value("${app.upload.max-video-size:536870912}")
    private long maxVideoSize;

    // Cloudinary URL cleanup retry queue
    // FIX 1 — THREAD SAFETY: CompletableFuture.exceptionally() callbacks write to this
    // queue from ForkJoin worker threads, while processFileCleanupQueue() reads from it
    // on the scheduler thread.  LinkedList is NOT thread-safe; concurrent offer()+poll()
    // can corrupt the internal node links, causing infinite loops or lost entries.
    // Replaced with ConcurrentLinkedQueue which is lock-free and thread-safe.
    //
    // FIX 2 — UNBOUNDED GROWTH: processFileCleanupQueue() was never @Scheduled, so the
    // queue was filled by every failed async delete but NEVER drained, leaking one String
    // entry per failure indefinitely.  The @Scheduled annotation is added below.
    private final Queue<String> fileCleanupQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

    // =========================================================================
    // CREATE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public Post createPost(PostCreateDto postDto, User user, MultipartFile mediaFile) {
        log.info("Creating post: user={} (id={})", user.getActualUsername(), user.getId());
        try {
            PostUtility.validateUser(user);
            contentValidationService.validateContent(postDto.getContent());
            PostUtility.validatePostContent(postDto.getContent());

            if (postDto.getTargetPincode() == null || postDto.getTargetPincode().trim().isEmpty()) {
                throw new ValidationException("Target pincode is required for creating posts");
            }
            PostUtility.validateTargetPincodeForUser(postDto.getTargetPincode());
            pinCodeLookupService.populateUserLocationData(user);

            // CLOUDINARY: upload returns secure URL; null when no file provided
            String fileName = null;
            if (mediaFile != null && !mediaFile.isEmpty()) {
                fileName = drivePostMedia.upload(mediaFile, user.getId());
            }

            Post post = new Post();
            post.setContent(postDto.getContent().trim());
            post.setUser(user);
            post.setImageName(fileName);   // stores Cloudinary URL or null
            post.setStatus(PostStatus.ACTIVE);
            post.setCreatedAt(new Date());

            if (PostUtility.isNormalUser(user)) {
                post.setBroadcastScope(BroadcastScope.AREA);
                String targetPincode = postDto.getTargetPincode().trim();
                if (!pinCodeLookupService.isValidPincode(targetPincode)) {
                    throw new ValidationException("Target pincode not found in system: " + targetPincode);
                }
                if (!Constant.isValidIndianPincode(targetPincode)) {
                    throw new ValidationException("Invalid Indian pincode format: " + targetPincode);
                }
                post.setTargetPincodes(targetPincode);
                log.info("User post created with target pincode: {}", targetPincode);
            }

            post.setTargetCountry(Constant.DEFAULT_TARGET_COUNTRY);
            post = postRepository.save(post);

            try {
                userTaggingService.processUserTags(post);
            } catch (Exception e) {
                log.warn("Failed to process user tags for post: {}", post.getId(), e);
            }

            log.info("Post created: id={} status={} scope={} pincode={}",
                    post.getId(), post.getStatus().getDisplayName(),
                    post.getBroadcastScope() != null ? post.getBroadcastScope().getDescription() : "None",
                    post.getTargetPincodes());
            return post;
        } catch (ValidationException | MediaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create post: user={} (id={})", user.getActualUsername(), user.getId(), e);
            throw new ServiceException("Failed to create post: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post createPost(PostCreateDto postDto, User user) {
        return createPost(postDto, user, null);
    }


    @Transactional(rollbackFor = Exception.class)
    public Post createBroadcastPost(PostCreateDto postDto, User user, BroadcastScope broadcastScope,
                                    String targetCountry, List<String> targetStates,
                                    List<String> targetDistricts, List<String> targetPincodes,
                                    MultipartFile mediaFile) {
        log.info("Creating broadcast post: user={} (id={}) scope={}", user.getActualUsername(), user.getId(), broadcastScope);
        try {
            PostUtility.validateUser(user);
            PostUtility.validateBroadcastPermission(user);
            PostUtility.validatePostContent(postDto.getContent());
            PostUtility.validateBroadcastScope(broadcastScope, targetCountry, targetStates, targetDistricts, targetPincodes);
            pinCodeLookupService.populateUserLocationData(user);

            // CLOUDINARY: upload returns secure URL
            String fileName = null;
            if (mediaFile != null && !mediaFile.isEmpty()) {
                fileName = drivePostMedia.upload(mediaFile, user.getId());
            }

            Post post = new Post();
            post.setContent(postDto.getContent().trim());
            post.setUser(user);
            post.setImageName(fileName);
            post.setStatus(PostStatus.ACTIVE);
            post.setCreatedAt(new Date());
            post.setBroadcastScope(broadcastScope);
            post.setTargetCountry(Constant.DEFAULT_TARGET_COUNTRY);

            if (targetStates != null && !targetStates.isEmpty()) {
                post.setTargetStates(PostUtility.convertStatesToTargetString(targetStates));
            }
            if (targetDistricts != null && !targetDistricts.isEmpty()) {
                post.setTargetDistricts(PostUtility.convertDistrictsToTargetString(targetDistricts, pinCodeLookupService));
            }
            if (targetPincodes != null && !targetPincodes.isEmpty()) {
                post.setTargetPincodes(PostUtility.convertPincodesToTargetString(targetPincodes));
            }

            post = postRepository.save(post);

            try {
                userTaggingService.processUserTags(post);
            } catch (Exception e) {
                log.warn("Failed to process user tags for broadcast post: {}", post.getId(), e);
            }

            if (post.isCountryWideGovernmentBroadcast()) {
                PostUtility.logCountryBroadcast(post, user);
            }

            log.info("Broadcast post created: id={} scope={}", post.getId(), broadcastScope.getDescription());
            return post;
        } catch (ValidationException | MediaValidationException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create broadcast post: user={} (id={})", user.getActualUsername(), user.getId(), e);
            throw new ServiceException("Failed to create broadcast post: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post createCountryWideBroadcast(PostCreateDto postDto, User user, MultipartFile mediaFile) {
        PostUtility.validateBroadcastPermission(user);
        Post post = createBroadcastPost(postDto, user, BroadcastScope.COUNTRY, Constant.DEFAULT_TARGET_COUNTRY,
                null, null, null, mediaFile);
        log.info("COUNTRY-WIDE BROADCAST CREATED: id={} user={} ({})",
                post.getId(), user.getActualUsername(), user.getRole().getName());
        return post;
    }

    @Transactional(rollbackFor = Exception.class)
    public Post createStateLevelBroadcast(PostCreateDto postDto, User user,
                                          List<String> targetStates, MultipartFile mediaFile) {
        PostUtility.validateBroadcastPermission(user);
        PostUtility.validateTargetStates(targetStates);
        return createBroadcastPost(postDto, user, BroadcastScope.STATE, Constant.DEFAULT_TARGET_COUNTRY,
                targetStates, null, null, mediaFile);
    }

    @Transactional(rollbackFor = Exception.class)
    public Post createDistrictLevelBroadcast(PostCreateDto postDto, User user,
                                             List<String> targetStates, List<String> targetDistricts,
                                             MultipartFile mediaFile) {
        PostUtility.validateBroadcastPermission(user);
        PostUtility.validateTargetDistricts(targetDistricts);
        return createBroadcastPost(postDto, user, BroadcastScope.DISTRICT, Constant.DEFAULT_TARGET_COUNTRY,
                targetStates, targetDistricts, null, mediaFile);
    }

    @Transactional(rollbackFor = Exception.class)
    public Post createAreaLevelBroadcast(PostCreateDto postDto, User user,
                                         List<String> targetPincodes, MultipartFile mediaFile) {
        PostUtility.validateBroadcastPermission(user);
        PostUtility.validateTargetPincodesWithLookup(targetPincodes, pinCodeLookupService);
        return createBroadcastPost(postDto, user, BroadcastScope.AREA, Constant.DEFAULT_TARGET_COUNTRY,
                null, null, targetPincodes, mediaFile);
    }

    // =========================================================================
    // BROADCAST QUERIES
    // =========================================================================

    public PaginatedResponse<Post> getAllBroadcastPosts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllBroadcastPosts", beforeId, limit);
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByBroadcastScopeIsNotNullAndStatusAndIdLessThanOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByBroadcastScopeIsNotNullAndStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.toPageable());
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
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByBroadcastScopeIsNotNullAndStatusAndIdLessThanOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByBroadcastScopeIsNotNullAndStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.toPageable());
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
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByBroadcastScopeAndStatusAndIdLessThanOrderByCreatedAtDesc(scope, PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByBroadcastScopeAndStatusOrderByCreatedAtDesc(scope, PostStatus.ACTIVE, setup.toPageable());
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getBroadcastPostsByScope", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get broadcast posts by scope: {}", scope, e);
            return PaginationUtils.handlePaginationError("getBroadcastPostsByScope", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getVisiblePostsForUser(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getVisiblePostsForUser", beforeId, limit);
            List<Post> allPosts = setup.hasCursor()
                    ? postRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.toPageable());
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

    public PaginatedResponse<Post> getBroadcastPostsVisibleToUser(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getBroadcastPostsVisibleToUser", beforeId, limit);
            PaginatedResponse<Post> broadcastPosts = getActiveBroadcastPosts(beforeId, setup.getValidatedLimit());
            List<Post> visiblePosts = broadcastPosts.getData().stream()
                    .filter(post -> PostUtility.isPostVisibleToUser(post, user))
                    .collect(Collectors.toList());
            PaginatedResponse<Post> response = PaginatedResponse.of(visiblePosts, broadcastPosts.isHasMore(), broadcastPosts.getNextCursor(), setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getBroadcastPostsVisibleToUser", visiblePosts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get broadcast posts visible to user: {}", user.getActualUsername(), e);
            return PaginationUtils.handlePaginationError("getBroadcastPostsVisibleToUser", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<Post> getAllCountryWideBroadcasts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllCountryWideBroadcasts", beforeId, limit);
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByBroadcastScopeAndStatusAndTargetCountryAndIdLessThanOrderByCreatedAtDesc(BroadcastScope.COUNTRY, PostStatus.ACTIVE, Constant.DEFAULT_TARGET_COUNTRY, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByBroadcastScopeAndStatusAndTargetCountryOrderByCreatedAtDesc(BroadcastScope.COUNTRY, PostStatus.ACTIVE, Constant.DEFAULT_TARGET_COUNTRY, setup.toPageable());
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
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByBroadcastScopeAndStatusAndTargetCountryAndIdLessThanOrderByCreatedAtDesc(BroadcastScope.COUNTRY, PostStatus.ACTIVE, Constant.DEFAULT_TARGET_COUNTRY, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByBroadcastScopeAndStatusAndTargetCountryOrderByCreatedAtDesc(BroadcastScope.COUNTRY, PostStatus.ACTIVE, Constant.DEFAULT_TARGET_COUNTRY, setup.toPageable());
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
            if (state == null || state.trim().isEmpty()) throw new ValidationException("State cannot be empty");
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getStateLevelBroadcasts", beforeId, limit);
            List<String> statePrefixes = PostUtility.convertStatesToPincodePrefixes(Arrays.asList(state.trim()));
            if (statePrefixes.isEmpty()) return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            String statePrefix = statePrefixes.get(0);
            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingAndIdLessThanOrderByCreatedAtDesc(BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByCreatedAtDesc(BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix, setup.toPageable());
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
            if (district == null || district.trim().isEmpty()) throw new ValidationException("District cannot be empty");
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getDistrictLevelBroadcasts", beforeId, limit);
            List<String> districtPrefixes = PostUtility.convertDistrictsToPincodePrefixes(Arrays.asList(district.trim()), pinCodeLookupService);
            if (districtPrefixes.isEmpty()) return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            List<Post> allDistrictPosts = new ArrayList<>();
            for (String prefix : districtPrefixes) {
                List<Post> posts = setup.hasCursor()
                        ? postRepository.findByBroadcastScopeAndStatusAndTargetDistrictsContainingAndIdLessThanOrderByCreatedAtDesc(BroadcastScope.DISTRICT, PostStatus.ACTIVE, prefix, setup.getSanitizedCursor(), setup.toPageable())
                        : postRepository.findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByCreatedAtDesc(BroadcastScope.DISTRICT, PostStatus.ACTIVE, prefix, setup.toPageable());
                if (posts != null) allDistrictPosts.addAll(posts);
            }
            List<Post> distinctPosts = allDistrictPosts.stream().distinct().limit(setup.getValidatedLimit()).collect(Collectors.toList());
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
            if (!Constant.isValidIndianPincode(pincode)) throw new ValidationException("Invalid Indian pincode format");
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAreaLevelBroadcasts", beforeId, limit);
            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingAndIdLessThanOrderByCreatedAtDesc(BroadcastScope.AREA, PostStatus.ACTIVE, pincode, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByCreatedAtDesc(BroadcastScope.AREA, PostStatus.ACTIVE, pincode, setup.toPageable());
            }
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getAreaLevelBroadcasts", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (Exception e) {
            log.error("Failed to get area level broadcasts for pincode: {}", pincode, e);
            return PaginationUtils.handlePaginationError("getAreaLevelBroadcasts", e, PaginationUtils.validateLimit(limit));
        }
    }

    public Map<String, Long> getBroadcastStatistics() {
        try {
            Map<String, Long> stats = new HashMap<>();
            stats.put("totalBroadcasts", postRepository.countByBroadcastScopeIsNotNull());
            stats.put("activeBroadcasts", postRepository.countByBroadcastScopeIsNotNullAndStatus(PostStatus.ACTIVE));
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
            if (days <= 0) throw new ValidationException("Days must be positive");
            LocalDateTime startDate = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
            Timestamp timestamp = Timestamp.valueOf(startDate);
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("totalBroadcastsCreated", postRepository.countByUserAndBroadcastScopeIsNotNull(user));
            analytics.put("recentBroadcasts", postRepository.countByUserAndBroadcastScopeIsNotNullAndCreatedAtAfter(user, timestamp));
            if (PostUtility.canCreateBroadcast(user)) {
                Long cb = postRepository.countByUserAndBroadcastScopeAndTargetCountry(user, BroadcastScope.COUNTRY, Constant.DEFAULT_TARGET_COUNTRY);
                analytics.put("countryWideBroadcasts", cb != null ? cb : 0L);
            }
            Map<String, Long> scopeBreakdown = new HashMap<>();
            for (BroadcastScope scope : BroadcastScope.values()) {
                Long count = postRepository.countByUserAndBroadcastScope(user, scope);
                scopeBreakdown.put(scope.name(), count != null ? count : 0L);
            }
            analytics.put("scopeBreakdown", scopeBreakdown);
            List<Post> userBroadcasts = postRepository.findByUserAndBroadcastScopeIsNotNull(user);
            if (userBroadcasts != null && !userBroadcasts.isEmpty()) {
                analytics.put("averageLikes",    Math.round(userBroadcasts.stream().mapToInt(Post::getLikeCount).average().orElse(0.0)    * 100.0) / 100.0);
                analytics.put("averageComments", Math.round(userBroadcasts.stream().mapToInt(Post::getCommentCount).average().orElse(0.0) * 100.0) / 100.0);
                analytics.put("averageViews",    Math.round(userBroadcasts.stream().mapToInt(Post::getViewCount).average().orElse(0.0)    * 100.0) / 100.0);
                analytics.put("averageShares",   Math.round(userBroadcasts.stream().mapToInt(Post::getShareCount).average().orElse(0.0)   * 100.0) / 100.0);
            }
            return analytics;
        } catch (Exception e) {
            log.error("Failed to get broadcast analytics for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get broadcast analytics: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post updateBroadcastTargets(Long postId, BroadcastScope newScope,
                                       List<String> targetStates, List<String> targetDistricts,
                                       List<String> targetPincodes, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            PostUtility.validateBroadcastPermission(currentUser);
            Post post = findById(postId);
            if (!post.isBroadcastPost()) throw new ValidationException("Post is not a broadcast post");
            if (!PostUtility.isPostOwner(post, currentUser) && !PostUtility.isAdmin(currentUser)) {
                throw new SecurityException("Only post creator or admin can update broadcast targets");
            }
            if (!PostUtility.postAllowsUpdates(post)) {
                throw new SecurityException("Cannot update broadcast targets for posts with status: " + post.getStatus().getDisplayName());
            }
            PostUtility.validateBroadcastScope(newScope, Constant.DEFAULT_TARGET_COUNTRY, targetStates, targetDistricts, targetPincodes);
            post.setBroadcastScope(newScope);
            post.setTargetStates(PostUtility.convertStatesToTargetString(targetStates));
            post.setTargetDistricts(PostUtility.convertDistrictsToTargetString(targetDistricts, pinCodeLookupService));
            post.setTargetPincodes(PostUtility.convertPincodesToTargetString(targetPincodes));
            post.setTargetCountry(Constant.DEFAULT_TARGET_COUNTRY);
            post.setUpdatedAt(new Date());
            Post updatedPost = postRepository.save(post);
            if (PostUtility.isAllIndiaGovernmentBroadcast(updatedPost)) {
                log.info("CRITICAL: Government country-wide broadcast updated: id={} user={} ({})",
                        postId, currentUser.getActualUsername(), currentUser.getRole().getName());
            }
            log.info("Broadcast targets updated: id={} scope={}", postId, newScope.getDescription());
            return updatedPost;
        } catch (ValidationException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update broadcast targets for post: {}", postId, e);
            throw new ServiceException("Failed to update broadcast targets: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // SHARE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordShare(Long postId, User user, ShareType shareType) {
        PostUtility.validatePostId(postId);
        Post post = findById(postId);
        PostShare share = postInteractionService.recordPostShare(post, user, shareType);
        log.info("Share recorded: postId={} userId={} type={}",
                postId, user != null ? user.getId() : "anon", shareType);
        return share;
    }

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordShare(Long postId, User user) {
        return recordShare(postId, user, ShareType.LINK_COPY);
    }

    public long getShareCount(Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) return 0L;
        return postInteractionService.getShareCountForPost(post);
    }

    public List<Object[]> getShareBreakdown(Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) return List.of();
        return postInteractionService.getShareBreakdownForPost(post);
    }

    // =========================================================================
    // MEDIA UPDATE / REMOVE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public Post updatePostMedia(Long postId, MultipartFile mediaFile, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            Post post = findById(postId);
            if (!PostUtility.isPostOwner(post, currentUser)) throw new SecurityException("Only post creator can update post media");
            if (!PostUtility.postAllowsUpdates(post)) throw new SecurityException("Cannot update media for posts with status: " + post.getStatus().getDisplayName());

            // CLOUDINARY: upload new file, delete old one asynchronously
            String fileName    = drivePostMedia.upload(mediaFile, currentUser.getId());
            String oldFileName = post.getImageName();
            post.setImageName(fileName);
            post.setUpdatedAt(new Date());
            Post updatedPost = postRepository.save(post);
            if (oldFileName != null && !oldFileName.trim().isEmpty()) {
                // FIX THREAD LEAK: runAsync with no timeout holds a ForkJoin thread
                // indefinitely if Cloudinary hangs — risks pool exhaustion under load.
                // orTimeout(10s) interrupts and falls back to the retry cleanup queue.
                final String urlToDelete = oldFileName;
                CompletableFuture
                        .runAsync(() -> drivePostMedia.delete(urlToDelete))
                        .orTimeout(10, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            log.warn("[Cloudinary] Async delete timed out/failed url={}: {}", urlToDelete, ex.getMessage());
                            fileCleanupQueue.offer(urlToDelete);
                            return null;
                        });
            }
            log.info("Media updated: postId={} newMedia={}", post.getId(), fileName != null ? fileName : "removed");
            return updatedPost;
        } catch (SecurityException | MediaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update post media: id={} user={}", postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to update post media: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post removePostMedia(Long postId, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            Post post = findById(postId);
            if (!PostUtility.isPostOwner(post, currentUser)) throw new SecurityException("Only post creator can remove post media");
            if (!PostUtility.postAllowsUpdates(post)) throw new SecurityException("Cannot remove media for posts with status: " + post.getStatus().getDisplayName());
            String oldFileName = post.getImageName();
            if (post.hasImage()) {
                post.setImageName(null);
                post.setUpdatedAt(new Date());
            }
            Post updatedPost = postRepository.save(post);
            if (oldFileName != null && !oldFileName.trim().isEmpty()) {
                // FIX THREAD LEAK: same timeout guard as updatePostMedia (see above)
                final String urlToDelete = oldFileName;
                CompletableFuture
                        .runAsync(() -> drivePostMedia.delete(urlToDelete))
                        .orTimeout(10, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            log.warn("[Cloudinary] Async delete timed out/failed url={}: {}", urlToDelete, ex.getMessage());
                            fileCleanupQueue.offer(urlToDelete);
                            return null;
                        });
            }
            log.info("Media removed from post: id={}", post.getId());
            return updatedPost;
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to remove post media: id={} user={}", postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to remove post media: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // CONTENT UPDATE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public Post updatePostContent(Long postId, String newContent, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            PostUtility.validatePostContent(newContent);
            Post post = findById(postId);
            if (!PostUtility.isPostOwner(post, currentUser)) throw new SecurityException("Only post creator can update post content");
            if (!PostUtility.postAllowsUpdates(post)) throw new SecurityException("Cannot update content for posts with status: " + post.getStatus().getDisplayName());
            String oldContent = post.getContent();
            post.setContent(newContent.trim());
            post.setUpdatedAt(new Date());
            try {
                userTaggingService.updatePostTags(post, newContent.trim());
            } catch (Exception e) {
                log.warn("Failed to update tags for post: {}", postId, e);
            }
            Post updatedPost = postRepository.save(post);
            if (PostUtility.isAllIndiaGovernmentBroadcast(updatedPost)) {
                log.info("CRITICAL: Government country-wide broadcast content updated: id={} user={} ({})",
                        postId, currentUser.getActualUsername(), currentUser.getRole().getName());
            }
            log.info("Post content updated: id={} changed={}", post.getId(), !Objects.equals(oldContent, newContent));
            return updatedPost;
        } catch (PostNotFoundException | SecurityException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update post content: id={} user={}", postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to update post content: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post updatePostContent(Long postId, PostContentUpdateDto contentUpdateDto, User currentUser) {
        if (contentUpdateDto == null) throw new ValidationException("Content update data cannot be null");
        return updatePostContent(postId, contentUpdateDto.getContent(), currentUser);
    }

    @Transactional(rollbackFor = Exception.class)
    public Post updatePostResolution(Long postId, Boolean isResolved, User user, String updateMessage) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(user);
            Post post = findById(postId);
            boolean canUpdate = userTaggingService.isUserTaggedInPost(post, user)
                    || PostUtility.isDepartment(user) || PostUtility.isAdmin(user);
            if (!canUpdate) {
                throw new SecurityException("Only tagged users, department users, or admin can update post resolution status");
            }
            if (post.getStatus() == null) throw new ServiceException("Post status is invalid");
            PostStatus newStatus = isResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
            if (!post.getStatus().canTransitionTo(newStatus)) {
                throw new SecurityException("Cannot transition from " + post.getStatus().getDisplayName() + " to " + newStatus.getDisplayName());
            }
            if (isResolved) {
                post.markAsResolved(updateMessage != null ? updateMessage.trim() : null);
                log.info("Post id={} marked RESOLVED by user={} (id={})", postId, user.getActualUsername(), user.getId());
            } else {
                post.markAsUnresolved();
                log.info("Post id={} marked ACTIVE by user={} (id={})", postId, user.getActualUsername(), user.getId());
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
            log.error("Failed to update post resolution: id={} user={}", postId, user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to update post resolution: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // USER TAGGING
    // =========================================================================

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

    // =========================================================================
    // FIND / PAGING
    // =========================================================================

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

    public PostResponse getPostByIdForUser(Long postId, User currentUser) {
        try {
            Post post = findById(postId);
            if (post.getStatus() == PostStatus.DELETED || post.getStatus() == PostStatus.FLAGGED) {
                log.debug("[Access] Deleted/Flagged post={} denied for user={}",
                        postId, currentUser != null ? currentUser.getActualUsername() : "anonymous");
                throw new PostNotFoundException("Post not found with ID: " + postId);
            }
            if (post.getStatus() == PostStatus.RESOLVED && !canViewResolvedPost(post, currentUser)) {
                log.debug("[Access] Resolved post={} denied for user={}",
                        postId, currentUser != null ? currentUser.getActualUsername() : "anonymous");
                throw new PostNotFoundException("Post not found with ID: " + postId);
            }
            return convertToPostResponse(post, currentUser);
        } catch (PostNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed getPostByIdForUser: id={}", postId, e);
            throw new ServiceException("Failed to get post: " + e.getMessage(), e);
        }
    }

    private boolean canViewResolvedPost(Post post, User viewer) {
        if (viewer == null) return false;
        if (PostUtility.isAdmin(viewer)) return true;
        if (post.getUser() != null && post.getUser().getId().equals(viewer.getId())) return true;
        if (PostUtility.isDepartment(viewer)) return userTaggingService.isUserTaggedInPost(post, viewer);
        return false;
    }

    public void assertPostAcceptsInteractions(Post post) {
        if (post == null) {
            throw new ValidationException("Post not found.");
        }
        if (post.getStatus() == PostStatus.RESOLVED) {
            throw new ValidationException("This issue has been resolved and no longer accepts likes or comments.");
        }
        if (post.getStatus() == PostStatus.DELETED || post.getStatus() == PostStatus.FLAGGED) {
            throw new ValidationException("This post is not available for interactions.");
        }
    }

    public PaginatedResponse<Post> getAllPosts(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getAllPosts", beforeId, limit);
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.toPageable());
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
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, setup.toPageable());
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
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(PostStatus.RESOLVED, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.RESOLVED, setup.toPageable());
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
            List<PostStatus> visibleStatuses = Arrays.asList(PostStatus.ACTIVE, PostStatus.RESOLVED);
            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByUserWithUserAndStatusInAndIdLessThanOrderByCreatedAtDesc(
                        user, visibleStatuses, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByUserWithUserAndStatusInOrderByCreatedAtDesc(user, visibleStatuses);
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
                posts = postRepository.findByUserWithUserAndStatusAndIdLessThanOrderByCreatedAtDesc(
                        user, PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByUserWithUserAndStatusOrderByCreatedAtDesc(user, PostStatus.ACTIVE);
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

    public PaginatedResponse<Post> getResolvedPostsByUser(User requestingUser, User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            if (requestingUser == null) throw new SecurityException("Authentication required to view resolved posts.");
            boolean isSelf  = requestingUser.getId().equals(user.getId());
            boolean isAdmin = PostUtility.isAdmin(requestingUser);
            if (!isSelf && !isAdmin) throw new SecurityException("You can only view your own resolved posts.");
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getResolvedPostsByUser", beforeId, limit);
            List<Post> posts;
            if (setup.hasCursor()) {
                posts = postRepository.findByUserWithUserAndStatusAndIdLessThanOrderByCreatedAtDesc(
                        user, PostStatus.RESOLVED, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findByUserWithUserAndStatusOrderByCreatedAtDesc(user, PostStatus.RESOLVED);
                posts = posts.stream().limit(setup.getValidatedLimit()).collect(Collectors.toList());
            }
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getResolvedPostsByUser", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (SecurityException e) {
            throw e;
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
                posts = postRepository.findPostsWithMultipleUserTagsAndIdLessThan(PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                posts = postRepository.findPostsWithMultipleUserTags(PostStatus.ACTIVE);
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
            if (days <= 0) throw new ValidationException("Days must be positive");
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getTrendingPosts", beforeId, limit, Constant.DEFAULT_FEED_LIMIT, 1000);
            LocalDateTime startDate = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
            List<Post> posts = setup.hasCursor()
                    ? postRepository.findTrendingPostsWithCursor(Timestamp.valueOf(startDate), PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : postRepository.findTrendingPosts(Timestamp.valueOf(startDate), PostStatus.ACTIVE, setup.toPageable());
            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(posts, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getTrendingPosts", posts, response.isHasMore(), response.getNextCursor());
            return response;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get trending posts: days={} beforeId={} limit={}", days, beforeId, limit, e);
            return PaginationUtils.handlePaginationError("getTrendingPosts", e, PaginationUtils.validateLimit(limit));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post tagUsersToPost(Long postId, List<Long> userIds, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            if (userIds == null || userIds.isEmpty()) throw new ValidationException("User IDs list cannot be empty");
            for (Long userId : userIds) PostUtility.validateUserId(userId);
            Post post = findById(postId);
            if (!PostUtility.postAllowsUpdates(post)) throw new SecurityException("Cannot add tags to posts with status: " + post.getStatus().getDisplayName());
            if (!PostUtility.canUserModifyPostTags(post, currentUser)) throw new SecurityException("Only post creator, department users, or admin can add user tags");
            List<User> usersToTag = userRepository.findAllById(userIds);
            if (usersToTag.size() != userIds.size()) {
                List<Long> foundIds   = usersToTag.stream().map(User::getId).collect(Collectors.toList());
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
            log.info("Added {} user tags to post id={} (attempted: {})", successCount, post.getId(), usersToTag.size());
            for (User taggedUser : usersToTag) {
                try {
                    notificationService.notifyUserTagged(post, taggedUser, currentUser);
                } catch (Exception e) {
                    log.warn("Failed to send tag notification to user={}: {}", taggedUser.getActualUsername(), e.getMessage());
                }
            }
            return post;
        } catch (PostNotFoundException | SecurityException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to tag users to post: {} by user: {}", postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to tag users to post: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Post removeUserTagFromPost(Long postId, Long userId, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUserId(userId);
            PostUtility.validateUser(currentUser);
            Post post = findById(postId);
            User userToRemove = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
            if (!PostUtility.postAllowsUpdates(post)) throw new SecurityException("Cannot remove tags from posts with status: " + post.getStatus().getDisplayName());
            if (!PostUtility.canUserRemovePostTag(post, currentUser, userId)) throw new SecurityException("Insufficient permissions to remove user tag");
            userTaggingService.removeUserTag(post, userToRemove);
            log.info("Removed user tag: userId={} from post id={} by user={}", userId, post.getId(), currentUser.getActualUsername());
            return post;
        } catch (PostNotFoundException | UserNotFoundException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to remove user tag: postId={} userId={} by user={}", postId, userId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to remove user tag from post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // MEDIA HELPERS
    // =========================================================================

    /**
     * Returns the Cloudinary URL as-is (it IS the public CDN path).
     */
    public String getMediaFilePath(String fileName) {
        return drivePostMedia.getPath(fileName);
    }

    public boolean isImageFile(String fileName)      { return PostUtility.isImageFile(fileName); }
    public boolean isVideoFile(String fileName)      { return PostUtility.isVideoFile(fileName); }
    public String getMediaType(String fileName)      { return PostUtility.getMediaType(fileName); }
    public Map<String, Object> getMediaConstraints() { return PostUtility.createMediaConstraints(maxImageSize, maxVideoSize); }
    
    public Long countPostsByUser(User user) {
        return postRepository.countByUser(user);
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public void softDeletePost(Long postId, User currentUser) {
        try {
            PostUtility.validatePostId(postId);
            PostUtility.validateUser(currentUser);
            Post post = findById(postId);
            if (!PostUtility.isPostOwner(post, currentUser) && !PostUtility.isAdmin(currentUser)) {
                throw new SecurityException("Only the post creator or an admin can delete this post.");
            }
            if (!PostUtility.postAllowsUpdates(post)) {
                throw new SecurityException("Cannot delete posts with status: " + post.getStatus().getDisplayName());
            }
            try {
                postInteractionService.cleanupForPostDeletion(post);
            } catch (Exception e) {
                log.warn("Failed to clean up interactions for post={}: {}", postId, e.getMessage());
            }
            post.setStatus(PostStatus.DELETED);
            post.setUpdatedAt(new Date());
            postRepository.save(post);
            log.info("Post soft-deleted: id={} by user={}", postId, currentUser.getActualUsername());
        } catch (SecurityException | PostNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to soft-delete post: id={} user={}", postId,
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to delete post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // FILE CLEANUP QUEUE  (deletes from Cloudinary)
    // =========================================================================

    // FIX 2 — drain the retry queue every 5 minutes (previously never called).
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300_000)
    public void processFileCleanupQueue() {
        int processedCount = 0;
        while (!fileCleanupQueue.isEmpty() && processedCount < 10) {
            String urlOrId = fileCleanupQueue.poll();
            if (urlOrId != null) {
                try {
                    drivePostMedia.delete(urlOrId);
                    log.info("Cleaned up Cloudinary file from retry queue: {}", urlOrId);
                    processedCount++;
                } catch (Exception e) {
                    log.warn("Failed to cleanup Cloudinary file: {}", urlOrId, e);
                }
            }
        }
        if (processedCount > 0) {
            log.info("Processed {} files from cleanup queue, {} remaining", processedCount, fileCleanupQueue.size());
        }
    }

    @SuppressWarnings("unused")
    private void scheduleFileCleanupRetry(String urlOrId) {
        if (urlOrId != null && !urlOrId.trim().isEmpty()) {
            fileCleanupQueue.offer(urlOrId);
            if (fileCleanupQueue.size() > 1000) {
                log.warn("Cleanup queue full, dropping oldest entry: {}", fileCleanupQueue.poll());
            }
        }
    }

    // =========================================================================
    // DTO CONVERSION
    // =========================================================================

    public PostResponse convertToPostResponse(Post post, User currentUser) {
        try {
            if (post == null) return null;

            boolean isLiked    = currentUser != null && postInteractionService.hasUserLikedPost(post, currentUser);
            boolean isDisliked = currentUser != null && postInteractionService.hasUserDislikedPost(post, currentUser);
            boolean isSaved    = currentUser != null && postInteractionService.hasSavedBroadcastPost(post, currentUser);

            int shareCount = post.getShareCount();

            PostResponse.PostResponseBuilder builder = PostResponse.builder()
                    .id(post.getId())
                    .content(post.getContent())
                    .status(post.getStatus())
                    .createdAt(post.getCreatedAt())
                    .updatedAt(post.getUpdatedAt())

                    // Cloudinary secure URL stored here (or null)
                    .imageName(post.getImageName())
                    .hasImage(post.hasImage())
                    .mediaType(post.hasImage() ? PostUtility.getMediaType(post.getImageName()) : null)

                    .isResolved(post.isResolved())
                    .resolvedAt(post.getResolvedAt())

                    .userId(post.getUser() != null ? post.getUser().getId() : null)
                    .username(post.getUser() != null ? post.getUser().getActualUsername() : null)
                    .userDisplayName(post.getUser() != null ? post.getUser().getDisplayName() : null)
                    .userProfileImage(post.getUser() != null ? post.getUser().getProfileImage() : null)
                    .userPincode(post.getUser() != null ? post.getUser().getPincode() : null)

                    .broadcastScope(post.getBroadcastScope())
                    .broadcastScopeDescription(post.getBroadcastScopeDescription())
                    .isBroadcastPost(post.isBroadcastPost())
                    .isGovernmentBroadcast(post.isGovernmentBroadcast())
                    .countryWideBroadcast(post.isCountryWideBroadcast())
                    .targetCountry(post.getTargetCountry())
                    .targetStates(PostUtility.resolvePrefixesToStateNames(post.getTargetStates()))
                    .targetDistricts(PostUtility.resolvePrefixesToDistrictNames(post.getTargetDistricts(), pinCodeLookupService))
                    .targetPincodes(post.getTargetPincodesList())

                    .likeCount(post.getLikeCount())
                    .dislikeCount(post.getDislikeCount())
                    .commentCount(post.getCommentCount())
                    .viewCount(post.getViewCount())
                    .shareCount(shareCount)
                    .saveCount(post.getSaveCount())
                    .taggedUserCount(post.getTaggedUserCount())

                    .isLikedByCurrentUser(isLiked)
                    .isDislikedByCurrentUser(isDisliked)
                    .isSavedByCurrentUser(isSaved)
                    .isViewedByCurrentUser(false)

                    .statusDisplayName(post.getStatus() != null ? post.getStatus().getDisplayName() : null)
                    .canBeResolved(post.getStatus() == PostStatus.ACTIVE)
                    .allowsUpdates(PostUtility.postAllowsUpdates(post))
                    .isEligibleForDisplay(PostUtility.isPostEligibleForDisplay(post))

                    .canLike(post.getStatus() == PostStatus.ACTIVE)
                    .canComment(post.getStatus() == PostStatus.ACTIVE)
                    .canShare(post.getStatus() == PostStatus.ACTIVE)
                    .canSave(post.isGovernmentBroadcast() && post.getStatus() == PostStatus.ACTIVE)
                    .canDelete(currentUser != null && (PostUtility.isPostOwner(post, currentUser) || PostUtility.isAdmin(currentUser)))

                    .timeAgo(PostUtility.calculateTimeAgo(post.getCreatedAt()))
                    .isVisibleToCurrentUser(currentUser != null && PostUtility.isPostVisibleToUser(post, currentUser));

            try {
                PaginatedResponse<User> taggedUsersResponse = userTaggingService.getTaggedUsersInPost(post, null, Constant.MAX_TAGS_PER_POST * 10);
                builder.taggedUsernames(taggedUsersResponse.getData().stream().map(User::getActualUsername).collect(Collectors.toList()));
                builder.taggedUsers(convertToTaggedUserInfos(post));
            } catch (Exception e) {
                log.warn("Failed to get tagged users for post: {}", post.getId(), e);
                builder.taggedUsernames(Collections.emptyList());
                builder.taggedUsers(Collections.emptyList());
            }

            return builder.build();

        } catch (Exception e) {
            log.error("Failed to convert post {} to response", post != null ? post.getId() : "null", e);
            return null;
        }
    }

    private List<PostResponse.TaggedUserInfo> convertToTaggedUserInfos(Post post) {
        try {
            return userTaggingService.getTaggedUsersInPost(post, null, Constant.MAX_TAGS_PER_POST * 10)
                    .getData().stream()
                    .map(u -> PostResponse.TaggedUserInfo.builder()
                            .userId(u.getId())
                            .username(u.getActualUsername())
                            .profileImage(u.getProfileImage())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to convert tagged users for post: {}", post.getId(), e);
            return Collections.emptyList();
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PostResponse> convertToPostResponses(List<Post> posts, User currentUser, int limit) {
        if (posts == null || posts.isEmpty()) return PaginationUtils.createEmptyResponse(limit);
        List<PostResponse> postResponses = posts.stream()
                .filter(Objects::nonNull)
                .map(p -> convertToPostResponse(p, currentUser))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return PaginationUtils.createIdBasedResponse(postResponses, limit, PostResponse::getId);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PostResponse> convertPaginatedPostsToResponses(PaginatedResponse<Post> paginatedPosts, User currentUser) {
        if (paginatedPosts == null || paginatedPosts.getData() == null) return PaginationUtils.createEmptyResponse(0);
        List<PostResponse> postResponses = paginatedPosts.getData().stream()
                .filter(Objects::nonNull)
                .map(p -> convertToPostResponse(p, currentUser))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return PaginatedResponse.of(postResponses, paginatedPosts.isHasMore(), paginatedPosts.getNextCursor(), paginatedPosts.getLimit());
    }

    // =========================================================================
    // LOCAL FEED / RECOMMENDATION
    // =========================================================================

    public PaginatedResponse<PostResponse> getLocalFeed(User user, Long beforeId, int limit) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);

            int validLimit = Math.max(1, Math.min(limit, 50));
            int candidatePool = validLimit * 2; // fetch extra for scoring headroom

            // ─── No pincode → national citizen fallback ──────────────────────
            if (!user.hasPincode()) {
                log.debug("[LocalFeed] userId={} has no pincode — returning all citizen posts nationally", user.getId());
                List<Post> allCitizen = postRepository.findAllCitizenPosts(
                        PostStatus.ACTIVE, PageRequest.of(0, candidatePool));
                return buildCitizenFeedResponse(allCitizen, user, null, null, null, validLimit, "NATIONAL");
            }

            String userPincode    = user.getPincode();
            String districtPrefix = userPincode.length() >= 3 ? userPincode.substring(0, 3) : null;
            String statePrefix    = userPincode.length() >= 2 ? userPincode.substring(0, 2) : null;

            int enoughThreshold = Math.max(1, validLimit / 2);

            Map<Long, Post> merged = new LinkedHashMap<>();
            String scopeLabel = "AREA";

            // ─── Tier 1: EXACT pincode match (same neighbourhood) ────────────
            postRepository.findCitizenPostsByPincode(
                    PostStatus.ACTIVE, userPincode, PageRequest.of(0, candidatePool)
            ).forEach(p -> merged.put(p.getId(), p));

            // ─── Tier 2: DISTRICT (first 3 digits) ──────────────────────────
            if (merged.size() < enoughThreshold && districtPrefix != null) {
                scopeLabel = "DISTRICT";
                postRepository.findCitizenPostsByDistrictPrefix(
                        PostStatus.ACTIVE, districtPrefix, PageRequest.of(0, candidatePool)
                ).forEach(p -> merged.putIfAbsent(p.getId(), p));
            }

            // ─── Tier 3: STATE (first 2 digits) ─────────────────────────────
            if (merged.size() < enoughThreshold && statePrefix != null) {
                scopeLabel = "STATE";
                postRepository.findCitizenPostsByStatePrefix(
                        PostStatus.ACTIVE, statePrefix, PageRequest.of(0, candidatePool)
                ).forEach(p -> merged.putIfAbsent(p.getId(), p));
            }

            // ─── Tier 4: NATIONAL fallback ──────────────────────────────────
            if (merged.size() < enoughThreshold) {
                scopeLabel = "NATIONAL";
                postRepository.findAllCitizenPosts(
                        PostStatus.ACTIVE, PageRequest.of(0, candidatePool)
                ).forEach(p -> merged.putIfAbsent(p.getId(), p));
            }

            // ─── Inject user's own posts so they always see their content ───
            try {
                postRepository.findByUserWithUserAndStatusOrderByCreatedAtDesc(user, PostStatus.ACTIVE)
                        .forEach(p -> merged.putIfAbsent(p.getId(), p));
            } catch (Exception e) {
                log.warn("[LocalFeed] Own-post injection failed for userId={}: {}", user.getId(), e.getMessage());
            }

            if (merged.isEmpty()) {
                log.info("[LocalFeed] All citizen tiers empty — returning empty for userId={}", user.getId());
                return PaginationUtils.createEmptyResponse(validLimit);
            }

            return buildCitizenFeedResponse(
                    new ArrayList<>(merged.values()), user,
                    userPincode, districtPrefix, statePrefix,
                    validLimit, scopeLabel);

        } catch (Exception e) {
            log.error("[LocalFeed] Failed for user={}", user != null ? user.getActualUsername() : "null", e);
            int safe = Math.max(1, Math.min(limit, 50));
            return PaginationUtils.createEmptyResponse(safe);
        }
    }

    /**
     * Scores, ranks, and paginates a pool of citizen issue posts for the Location tab.
     */
    private PaginatedResponse<PostResponse> buildCitizenFeedResponse(
            List<Post> pool, User user,
            String userPincode, String districtPrefix, String statePrefix,
            int validLimit, String scopeLabel) {

        List<Post> ranked = pool.stream()
                .filter(p -> p.getStatus() == PostStatus.ACTIVE)
                .sorted(Comparator.comparingDouble(
                        (Post p) -> computeIssueScore(p, userPincode, districtPrefix, statePrefix)).reversed())
                .limit(validLimit)
                .collect(Collectors.toList());

        log.debug("[LocalFeed] userId={} pincode={} scope={} pool={} returned={}",
                user.getId(), userPincode, scopeLabel, pool.size(), ranked.size());

        List<PostResponse> responses = ranked.stream()
                .map(p -> convertToPostResponse(p, user))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        boolean hasMore    = responses.size() == validLimit;
        Long    nextCursor = hasMore ? responses.get(responses.size() - 1).getId() : null;
        return PaginatedResponse.of(responses, hasMore, nextCursor, validLimit);
    }

    /**
     * Official government feed — exclusively departments/admins, strictly geo-targeted.
     * Waterfall: User's Pincode -> User's District -> User's State -> National.
     */
    public PaginatedResponse<PostResponse> getOfficialFeed(User user, Long beforeId, int limit) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);

            int validLimit = Math.max(1, Math.min(limit, 50));

            // Resolve user's pincode
            String userPincode = user.getPincode();

            // Resolved prefixes (for matching against target_districts and target_states)
            String districtPrefix = (userPincode != null && userPincode.length() >= 3)
                    ? userPincode.substring(0, 3) : null;
            String statePrefix    = (userPincode != null && userPincode.length() >= 2)
                    ? userPincode.substring(0, 2) : null;

            log.debug("[OfficialFeed] userId={} pincode={} districtPrefix={} statePrefix={}",
                    user.getId(), userPincode, districtPrefix, statePrefix);

            Map<Long, Post> merged = new LinkedHashMap<>();

            // 1. Area Level (Pincode match) — Only if user has pincode
            if (userPincode != null) {
                List<Post> areaGov = postRepository.findOfficialAreaBroadcasts(
                        BroadcastScope.AREA, PostStatus.ACTIVE, userPincode);
                areaGov.forEach(p -> merged.put(p.getId(), p));
            }

            // 2. District Level — match by 3-digit district prefix (e.g. "411")
            if (merged.size() < validLimit && districtPrefix != null) {
                List<Post> districtGov = postRepository.findOfficialDistrictBroadcasts(
                        BroadcastScope.DISTRICT, PostStatus.ACTIVE, districtPrefix);
                districtGov.forEach(p -> merged.putIfAbsent(p.getId(), p));
            }

            // 3. State Level — match by 2-digit state prefix (e.g. "40")
            if (merged.size() < validLimit && statePrefix != null) {
                List<Post> stateGov = postRepository.findOfficialStateBroadcasts(
                        BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix);
                stateGov.forEach(p -> merged.putIfAbsent(p.getId(), p));
            }

            // 4. National Level
            if (merged.size() < validLimit) {
                List<Post> nationalGov = postRepository.findOfficialCountryBroadcasts(
                        BroadcastScope.COUNTRY, PostStatus.ACTIVE);
                nationalGov.forEach(p -> merged.putIfAbsent(p.getId(), p));
            }

            // 5. Absolute fallback — ALL department/admin broadcasts (catches posts with
            //    null targetCountry or non-standard broadcastScope configurations)
            if (merged.isEmpty()) {
                log.debug("[OfficialFeed] All geo-tiers empty — loading all official broadcasts for userId={}", user.getId());
                postRepository.findAllOfficialBroadcasts(
                        PostStatus.ACTIVE, PageRequest.of(0, validLimit * 2)
                ).forEach(p -> merged.putIfAbsent(p.getId(), p));
            }

            // Filter by cursor, sort by ID desc, and limit
            List<Post> finalPosts = merged.values().stream()
                    .filter(p -> beforeId == null || p.getId() < beforeId)
                    .sorted(Comparator.comparing(Post::getId).reversed())
                    .limit(validLimit)
                    .collect(Collectors.toList());

            List<PostResponse> responses = finalPosts.stream()
                    .map(p -> convertToPostResponse(p, user))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            boolean hasMore = responses.size() == validLimit;
            Long nextCursor = hasMore ? responses.get(responses.size() - 1).getId() : null;
            return PaginatedResponse.of(responses, hasMore, nextCursor, validLimit);

        } catch (Exception e) {
            log.error("[OfficialFeed] Failed for user={}", user != null ? user.getActualUsername() : "null", e);
            return PaginationUtils.createEmptyResponse(Math.max(1, Math.min(limit, 50)));
        }
    }


    public PaginatedResponse<PostResponse> getIssuePostFeed(User user, Integer limit) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);

            int validLimit = (limit == null || limit <= 0) ? Constant.DEFAULT_FEED_LIMIT : Math.min(limit, 100);

            String userPincode    = user.getPincode();
            String districtPrefix = (userPincode != null && userPincode.length() >= 3) ? userPincode.substring(0, 3) : null;
            String statePrefix    = (userPincode != null && userPincode.length() >= 2) ? userPincode.substring(0, 2) : null;

            List<Post> candidates = new ArrayList<>();

            if (userPincode != null && !userPincode.isBlank()) {
                candidates.addAll(postRepository.findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByCreatedAtDesc(
                        BroadcastScope.AREA, PostStatus.ACTIVE, userPincode));
                if (districtPrefix != null) {
                    candidates.addAll(postRepository.findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByCreatedAtDesc(
                            BroadcastScope.DISTRICT, PostStatus.ACTIVE, districtPrefix));
                }
                if (statePrefix != null) {
                    candidates.addAll(postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByCreatedAtDesc(
                            BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix));
                }
            } else {
                log.debug("[IssueFeed] userId={} has no pincode — skipping geo tiers", user.getId());
            }

            candidates.addAll(postRepository.findByBroadcastScopeAndStatusOrderByCreatedAtDesc(
                    BroadcastScope.COUNTRY, PostStatus.ACTIVE, PageRequest.of(0, 50)));

            Map<Long, Post> deduped = candidates.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(Post::getId, p -> p, (a, b) -> a, LinkedHashMap::new),
                            map -> map));

            try {
                List<Post> ownPosts = postRepository.findByUserWithUserAndStatusOrderByCreatedAtDesc(user, PostStatus.ACTIVE);
                ownPosts.forEach(p -> deduped.putIfAbsent(p.getId(), p));
                if (!ownPosts.isEmpty()) {
                    log.debug("[IssueFeed] Injected {} own posts for userId={}", ownPosts.size(), user.getId());
                }
            } catch (Exception e) {
                log.warn("[IssueFeed] Own-post injection failed for userId={}: {}", user.getId(), e.getMessage());
            }

            if (deduped.size() < validLimit) {
                log.info("[IssueFeed] Thin pool ({}) for userId={} — loading all active posts (sparse platform)",
                        deduped.size(), user.getId());
                try {
                    PaginatedResponse<Post> allActive = getAllActivePosts(null, validLimit * 2);
                    if (allActive != null && allActive.getData() != null) {
                        allActive.getData().forEach(p -> deduped.putIfAbsent(p.getId(), p));
                    }
                } catch (Exception e) {
                    log.warn("[IssueFeed] Sparse fallback failed for userId={}: {}", user.getId(), e.getMessage());
                }
            }

            final String fp = userPincode, fd = districtPrefix, fs = statePrefix;
            List<Post> ranked = deduped.values().stream()
                    .sorted(Comparator.comparingDouble((Post p) -> computeIssueScore(p, fp, fd, fs)).reversed())
                    .limit(validLimit)
                    .collect(Collectors.toList());

            List<PostResponse> responses = ranked.stream()
                    .map(p -> convertToPostResponse(p, user))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("[IssueFeed] user={} pincode={} candidates={} deduped={} returned={}",
                    user.getActualUsername(), userPincode != null ? userPincode : "none",
                    candidates.size(), deduped.size(), responses.size());

            return PaginatedResponse.of(responses, false, null, validLimit);

        } catch (Exception e) {
            log.error("[IssueFeed] Failed for user={}", user != null ? user.getActualUsername() : "null", e);
            try {
                int fallback = (limit == null || limit <= 0) ? Constant.DEFAULT_FEED_LIMIT : Math.min(limit, 100);
                PaginatedResponse<Post> allActive = getAllActivePosts(null, fallback);
                return convertPaginatedPostsToResponses(allActive, user);
            } catch (Exception ex) {
                int fallback = (limit == null || limit <= 0) ? Constant.DEFAULT_FEED_LIMIT : Math.min(limit, 100);
                return PaginationUtils.createEmptyResponse(fallback);
            }
        }
    }

    public PaginatedResponse<PostResponse> getIssueRecommendationFeed(User user, Integer limit) {
        try {
            PostUtility.validateUser(user);
            pinCodeLookupService.populateUserLocationData(user);

            int validLimit = (limit == null || limit <= 0)
                    ? Constant.ISSUE_RECOMMENDATION_DEFAULT_LIMIT
                    : Math.min(limit, Constant.ISSUE_RECOMMENDATION_MAX_LIMIT);

            String userPincode    = user.getPincode();
            String districtPrefix = (userPincode != null && userPincode.length() >= 3) ? userPincode.substring(0, 3) : null;
            String statePrefix    = (userPincode != null && userPincode.length() >= 2) ? userPincode.substring(0, 2) : null;

            Map<Long, Post> merged = new LinkedHashMap<>();

            if (userPincode != null && !userPincode.isBlank()) {
                List<Post> tier1ExactArea = postRepository
                        .findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByCreatedAtDesc(
                                BroadcastScope.AREA, PostStatus.ACTIVE, userPincode);
                tier1ExactArea.forEach(p -> merged.put(p.getId(), p));

                boolean nearbyNeeded = tier1ExactArea.size() < Constant.ISSUE_NEARBY_EXPANSION_THRESHOLD;
                if (districtPrefix != null) {
                    List<Post> districtAreaCandidates = postRepository
                            .findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByCreatedAtDesc(
                                    BroadcastScope.AREA, PostStatus.ACTIVE, districtPrefix);
                    List<Post> tier2NearbyArea = districtAreaCandidates.stream()
                            .filter(p -> {
                                if (merged.containsKey(p.getId())) return false;
                                String tp = p.getTargetPincodes();
                                if (tp == null) return false;
                                for (String pc : tp.split(",")) {
                                    String pc2 = pc.trim();
                                    if (pc2.length() >= 3 && pc2.substring(0, 3).equals(districtPrefix)
                                            && !pc2.equals(userPincode)) {
                                        return true;
                                    }
                                }
                                return false;
                            })
                            .limit(nearbyNeeded
                                    ? Constant.ISSUE_NEARBY_BLEND_LIMIT
                                    : Constant.ISSUE_NEARBY_BLEND_LIMIT / 2)
                            .collect(Collectors.toList());
                    tier2NearbyArea.forEach(p -> merged.putIfAbsent(p.getId(), p));
                }

                if (districtPrefix != null) {
                    postRepository.findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByCreatedAtDesc(
                                    BroadcastScope.DISTRICT, PostStatus.ACTIVE, districtPrefix)
                            .forEach(p -> merged.putIfAbsent(p.getId(), p));
                }

                if (statePrefix != null) {
                    postRepository.findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByCreatedAtDesc(
                                    BroadcastScope.STATE, PostStatus.ACTIVE, statePrefix)
                            .forEach(p -> merged.putIfAbsent(p.getId(), p));
                }
            } else {
                log.debug("[IssueRec] userId={} has no pincode — skipping geo tiers, going straight to national", user.getId());
            }

            int nationalCap = validLimit * Constant.ISSUE_RECOMMENDATION_CANDIDATE_MULTIPLIER;
            postRepository.findByBroadcastScopeAndStatusOrderByCreatedAtDesc(
                            BroadcastScope.COUNTRY, PostStatus.ACTIVE,
                            PageRequest.of(0, Math.min(nationalCap, 50)))
                    .forEach(p -> merged.putIfAbsent(p.getId(), p));

            try {
                List<Post> ownPosts = postRepository.findByUserWithUserAndStatusOrderByCreatedAtDesc(user, PostStatus.ACTIVE);
                ownPosts.forEach(p -> merged.putIfAbsent(p.getId(), p));
                if (!ownPosts.isEmpty()) {
                    log.debug("[IssueRec] Injected {} own posts for userId={}", ownPosts.size(), user.getId());
                }
            } catch (Exception e) {
                log.warn("[IssueRec] Own-post injection failed for userId={}: {}", user.getId(), e.getMessage());
            }

            if (merged.size() < validLimit) {
                log.info("[IssueRec] Thin pool ({} posts) for userId={} — loading all active posts (sparse platform)",
                        merged.size(), user.getId());
                try {
                    PaginatedResponse<Post> allActive = getAllActivePosts(null, validLimit * 2);
                    if (allActive != null && allActive.getData() != null) {
                        allActive.getData().forEach(p -> merged.putIfAbsent(p.getId(), p));
                    }
                } catch (Exception e) {
                    log.warn("[IssueRec] Sparse-platform fallback failed for userId={}: {}", user.getId(), e.getMessage());
                }
            }

            final String fp = userPincode, fd = districtPrefix, fs = statePrefix;
            List<Post> ranked = merged.values().stream()
                    .filter(p -> p.getStatus() == PostStatus.ACTIVE)
                    .sorted(Comparator
                            .comparingDouble((Post p) -> computeIssueRecommendationScore(p, fp, fd, fs))
                            .reversed())
                    .limit(validLimit)
                    .collect(Collectors.toList());

            List<PostResponse> responses = ranked.stream()
                    .map(p -> convertToPostResponse(p, user))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("[IssueRec] user={} pincode={} merged={} returned={}",
                    user.getActualUsername(), userPincode != null ? userPincode : "none",
                    merged.size(), responses.size());

            return PaginatedResponse.of(responses, false, null, validLimit);

        } catch (Exception e) {
            log.error("[IssueRec] Failed for user={}", user != null ? user.getActualUsername() : "null", e);
            try {
                int fallback = (limit == null || limit <= 0)
                        ? Constant.ISSUE_RECOMMENDATION_DEFAULT_LIMIT
                        : Math.min(limit, Constant.ISSUE_RECOMMENDATION_MAX_LIMIT);
                PaginatedResponse<Post> allActive = getAllActivePosts(null, fallback);
                return convertPaginatedPostsToResponses(allActive, user);
            } catch (Exception ex) {
                int fallback = (limit == null || limit <= 0)
                        ? Constant.ISSUE_RECOMMENDATION_DEFAULT_LIMIT
                        : Math.min(limit, Constant.ISSUE_RECOMMENDATION_MAX_LIMIT);
                return PaginationUtils.createEmptyResponse(fallback);
            }
        }
    }

    // =========================================================================
    // SCORING
    // =========================================================================

    private double computeIssueRecommendationScore(
            Post post, String userPincode, String districtPrefix, String statePrefix) {
        // FIX: Post.likeCount / commentCount / viewCount are primitive int — comparing to
        // null is a compile error ("bad operand types for binary operator '!='").
        // Primitives are always initialised (default 0), so the null-safe ternary is wrong.
        int    likes    = post.getLikeCount();
        int    comments = post.getCommentCount();
        int    views    = post.getViewCount();
        double engagement = (likes    * Constant.POST_WEIGHT_LIKE)
                + (comments * Constant.POST_WEIGHT_COMMENT)
                + (views    * Constant.POST_WEIGHT_VIEW);
        long   ageHours  = post.getCreatedAt() != null
                ? TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - post.getCreatedAt().getTime()) : 0L;
        double freshness = 1.0 / (1.0 + ageHours * Constant.POST_DECAY_RATE);
        double geoBoost  = resolveIssueGeoBoost(post, userPincode, districtPrefix, statePrefix);
        return engagement * freshness * geoBoost;
    }

    private double resolveIssueGeoBoost(
            Post post, String userPincode, String districtPrefix, String statePrefix) {
        BroadcastScope scope = post.getBroadcastScope();
        if (scope == null) return Constant.ISSUE_GEO_BOOST_NATIONAL;
        switch (scope) {
            case AREA: {
                String targetPincodes = post.getTargetPincodes();
                if (targetPincodes == null) return Constant.ISSUE_GEO_BOOST_NATIONAL;
                if (targetPincodes.contains(userPincode)) return Constant.ISSUE_GEO_BOOST_SAME_PINCODE;
                if (districtPrefix != null) {
                    for (String pc : targetPincodes.split(",")) {
                        String pc2 = pc.trim();
                        if (pc2.length() >= 3 && pc2.substring(0, 3).equals(districtPrefix)) {
                            return Constant.ISSUE_GEO_BOOST_NEARBY;
                        }
                    }
                }
                return Constant.ISSUE_GEO_BOOST_NATIONAL;
            }
            case DISTRICT: {
                String targetDistricts = post.getTargetDistricts();
                boolean inDistrict = districtPrefix != null && targetDistricts != null && targetDistricts.contains(districtPrefix);
                return inDistrict ? Constant.ISSUE_GEO_BOOST_DISTRICT : Constant.ISSUE_GEO_BOOST_STATE;
            }
            case STATE: {
                String targetStates = post.getTargetStates();
                boolean inState = statePrefix != null && targetStates != null && targetStates.contains(statePrefix);
                return inState ? Constant.ISSUE_GEO_BOOST_STATE : Constant.ISSUE_GEO_BOOST_NATIONAL;
            }
            case COUNTRY: return Constant.ISSUE_GEO_BOOST_NATIONAL;
            default:      return Constant.ISSUE_GEO_BOOST_NATIONAL;
        }
    }

    private double computeIssueScore(Post post, String userPincode, String districtPrefix, String statePrefix) {
        // FIX: same primitive int issue as computeIssueRecommendationScore above.
        int    likes    = post.getLikeCount();
        int    comments = post.getCommentCount();
        int    views    = post.getViewCount();
        double engagement = (likes * Constant.POST_WEIGHT_LIKE) + (comments * Constant.POST_WEIGHT_COMMENT) + (views * Constant.POST_WEIGHT_VIEW);
        long ageHours = post.getCreatedAt() != null
                ? TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - post.getCreatedAt().getTime()) : 0L;
        double freshness = 1.0 / (1.0 + ageHours * Constant.POST_DECAY_RATE);
        return engagement * freshness * resolveGeoBoost(post, userPincode, districtPrefix, statePrefix);
    }

    private double resolveGeoBoost(Post post, String userPincode, String districtPrefix, String statePrefix) {
        BroadcastScope scope = post.getBroadcastScope();
        if (scope == null) return Constant.POST_BOOST_NATIONAL;
        switch (scope) {
            case AREA: {
                String p = post.getTargetPincodes();
                return (p != null && p.contains(userPincode)) ? Constant.POST_BOOST_AREA : Constant.POST_BOOST_DISTRICT;
            }
            case DISTRICT: {
                String d = post.getTargetDistricts();
                return (districtPrefix != null && d != null && d.contains(districtPrefix)) ? Constant.POST_BOOST_DISTRICT : Constant.POST_BOOST_STATE;
            }
            case STATE: {
                String s = post.getTargetStates();
                return (statePrefix != null && s != null && s.contains(statePrefix)) ? Constant.POST_BOOST_STATE : Constant.POST_BOOST_NATIONAL;
            }
            case COUNTRY: return Constant.POST_BOOST_NATIONAL;
            default:      return Constant.POST_BOOST_NATIONAL;
        }
    }

    // =========================================================================
    // PROMOTION / DEMOTION
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public void checkAndPromoteIssuePost(Long postId) {
        try {
            Post post = postRepository.findById(postId).orElse(null);
            if (post == null || !isUserIssuePost(post) || post.getStatus() != PostStatus.ACTIVE) return;

            BroadcastScope current = post.getBroadcastScope();
            if (current == null) return;

            long   ageHours = getAgeInHours(post);
            // FIX: primitive int — null check is illegal, use directly.
            int    likes    = post.getLikeCount();
            int    comments = post.getCommentCount();
            double velocity = ageHours > 0 ? (double)(likes + comments) / ageHours : (likes + comments);

            switch (current) {
                case AREA:
                    if (ageHours <= Constant.ISSUE_DISTRICT_PROMOTE_MAX_AGE_HOURS
                            && (likes >= Constant.ISSUE_DISTRICT_PROMOTE_LIKES
                            || comments >= Constant.ISSUE_DISTRICT_PROMOTE_COMMENTS)) {
                        String pincode = getFirstPincode(post);
                        if (pincode != null && pincode.length() >= 3) {
                            post.setBroadcastScope(BroadcastScope.DISTRICT);
                            post.setTargetDistricts(pincode.substring(0, 3));
                            post.setUpdatedAt(new Date());
                            postRepository.save(post);
                            log.info("[Promotion] Post={} AREA→DISTRICT likes={} comments={} vel={}/hr",
                                    postId, likes, comments, String.format("%.2f", velocity));
                        }
                    }
                    break;
                case DISTRICT:
                    if (ageHours <= Constant.ISSUE_STATE_PROMOTE_MAX_AGE_HOURS
                            && (likes >= Constant.ISSUE_STATE_PROMOTE_LIKES
                            || comments >= Constant.ISSUE_STATE_PROMOTE_COMMENTS)) {
                        String pincode = getFirstPincode(post);
                        if (pincode != null && pincode.length() >= 2) {
                            post.setBroadcastScope(BroadcastScope.STATE);
                            post.setTargetStates(pincode.substring(0, 2));
                            post.setUpdatedAt(new Date());
                            postRepository.save(post);
                            log.info("[Promotion] Post={} DISTRICT→STATE likes={} comments={} vel={}/hr",
                                    postId, likes, comments, String.format("%.2f", velocity));
                        }
                    }
                    break;
                case STATE:
                    if (ageHours <= Constant.ISSUE_NATIONAL_PROMOTE_MAX_AGE_HOURS
                            && (likes >= Constant.ISSUE_NATIONAL_PROMOTE_LIKES
                            || comments >= Constant.ISSUE_NATIONAL_PROMOTE_COMMENTS)) {
                        post.setBroadcastScope(BroadcastScope.COUNTRY);
                        post.setTargetCountry(Constant.DEFAULT_TARGET_COUNTRY);
                        post.setUpdatedAt(new Date());
                        postRepository.save(post);
                        log.info("[Promotion] Post={} STATE→NATIONAL likes={} comments={} vel={}/hr",
                                postId, likes, comments, String.format("%.2f", velocity));
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error("[Promotion] Failed for post={}: {}", postId, e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void demoteStaleIssuePosts() {
        log.info("[Demotion] Starting stale issue post demotion...");
        int demoted = 0;
        try {
            for (Post post : postRepository.findByBroadcastScopeAndStatusOrderByCreatedAtDesc(BroadcastScope.DISTRICT, PostStatus.ACTIVE, PageRequest.of(0, 500))) {
                if (!isUserIssuePost(post) || !isStale(post)) continue;
                post.setBroadcastScope(BroadcastScope.AREA);
                post.setTargetDistricts(null);
                post.setUpdatedAt(new Date());
                postRepository.save(post);
                log.info("[Demotion] Post={} DISTRICT→AREA", post.getId());
                demoted++;
            }
            for (Post post : postRepository.findByBroadcastScopeAndStatusOrderByCreatedAtDesc(BroadcastScope.STATE, PostStatus.ACTIVE, PageRequest.of(0, 300))) {
                if (!isUserIssuePost(post) || !isStale(post)) continue;
                String pincode = getFirstPincode(post);
                if (pincode != null && pincode.length() >= 3) {
                    post.setBroadcastScope(BroadcastScope.DISTRICT);
                    post.setTargetStates(null);
                    post.setTargetDistricts(pincode.substring(0, 3));
                    post.setUpdatedAt(new Date());
                    postRepository.save(post);
                    log.info("[Demotion] Post={} STATE→DISTRICT", post.getId());
                    demoted++;
                }
            }
            for (Post post : postRepository.findByBroadcastScopeAndStatusOrderByCreatedAtDesc(BroadcastScope.COUNTRY, PostStatus.ACTIVE, PageRequest.of(0, 100))) {
                if (!isUserIssuePost(post) || !isStale(post)) continue;
                String pincode = getFirstPincode(post);
                if (pincode != null && pincode.length() >= 2) {
                    post.setBroadcastScope(BroadcastScope.STATE);
                    post.setTargetStates(pincode.substring(0, 2));
                    post.setUpdatedAt(new Date());
                    postRepository.save(post);
                    log.info("[Demotion] Post={} NATIONAL→STATE", post.getId());
                    demoted++;
                }
            }
            log.info("[Demotion] Complete — demoted={}", demoted);
        } catch (Exception e) {
            log.error("[Demotion] Job failed", e);
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private boolean isUserIssuePost(Post post) {
        String p = post.getTargetPincodes();
        return p != null && !p.isBlank();
    }

    private String getFirstPincode(Post post) {
        String raw = post.getTargetPincodes();
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(",");
        return parts.length > 0 ? parts[0].trim() : null;
    }

    private long getAgeInHours(Post post) {
        return post.getCreatedAt() == null ? 0
                : TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - post.getCreatedAt().getTime());
    }

    private boolean isStale(Post post) {
        Date last = post.getUpdatedAt() != null ? post.getUpdatedAt() : post.getCreatedAt();
        return last != null && TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - last.getTime()) >= Constant.POST_DEMOTION_INACTIVE_DAYS;
    }
}