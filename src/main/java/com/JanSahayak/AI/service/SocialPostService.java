package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.SocialPostCreateDto;
import com.JanSahayak.AI.DTO.SocialPostDto;
import com.JanSahayak.AI.DTO.SocialPostUpdateDto;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.FeedScope;
import com.JanSahayak.AI.enums.FeedSort;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.PostNotFoundException;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.Poll;
import com.JanSahayak.AI.model.SavedPost;
import com.JanSahayak.AI.model.PostShare;
import com.JanSahayak.AI.model.PostShare.ShareType;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.payload.SocialPostUtility;
import com.JanSahayak.AI.repository.PollRepository;
import com.JanSahayak.AI.repository.PollVoteRepository;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialPostService {


    private final SocialPostRepo           socialPostRepository;
    private final UserRepo                 userRepository;
    private final PollRepository           pollRepository;
    private final PollVoteRepository       pollVoteRepository;


    private final ContentValidationService contentValidationService;
    private final SocialPostMediaService   mediaService;
    private final PostInteractionService   postInteractionService;
    private final NotificationService      notificationService;

    @Lazy
    @Autowired
    private CommunityService communityService;


    @Lazy
    @Autowired
    private InterestProfileService interestProfileService;

    @Lazy
    @Autowired
    private HLIGFeedService hligFeedService;

    // =========================================================================
    // CREATE
    // =========================================================================

    /**
     * Create a text-only social post (no media).
     */
    @Transactional(rollbackFor = Exception.class)
    public SocialPostDto createTextPost(SocialPostCreateDto createDto, User user) {
        return createSocialPost(createDto, Collections.emptyList(), user);
    }

    /**
     * Create a new social post with optional media files and user tagging.
     */
    @Transactional(rollbackFor = Exception.class)
    public SocialPostDto createSocialPost(
            SocialPostCreateDto createDto,
            List<MultipartFile> mediaFiles,
            User user) {
        try {
            PostUtility.validateUser(user);
            SocialPostUtility.validateSocialPostContent(createDto.getContent());
            contentValidationService.validateContent(createDto.getContent());

            List<String> extractedHashtags = extractAndValidateHashtags(
                    createDto.getContent(), createDto.getHashtags());
            List<Long> mentionedUserIds = extractAndValidateMentions(
                    createDto.getContent(), createDto.getMentionedUserIds());

            List<String> uploadedMediaUrls = new ArrayList<>();
            if (mediaFiles != null && !mediaFiles.isEmpty()) {
                uploadedMediaUrls = uploadMediaFilesWithValidation(mediaFiles, user.getId());
            }

            SocialPost socialPost = buildSocialPost(
                    createDto, user, extractedHashtags, mentionedUserIds, uploadedMediaUrls);
            SocialPost savedPost = socialPostRepository.save(socialPost);

            // HLIG v2: post creation is the strongest interest signal (+5.0 weight).
            try {
                interestProfileService.onPostCreated(savedPost.getUser().getId(), savedPost);
            } catch (Exception e) {
                log.warn("[HLIG] onPostCreated failed: postId={} reason={}", savedPost.getId(), e.getMessage());
            }

            if (savedPost.getCommunityId() != null) {
                try {
                    communityService.onPostPublished(savedPost, savedPost.getCommunityId());
                } catch (Exception e) {
                    log.warn("[Community] onPostPublished failed for post={} community={}: {}",
                            savedPost.getId(), savedPost.getCommunityId(), e.getMessage());
                }
            }

            log.info("Social post created: ID={} User={} Media={} Hashtags={} Mentions={}",
                    savedPost.getId(), user.getActualUsername(), savedPost.getMediaCount(),
                    savedPost.getHashtagCount(), savedPost.getMentionCount());

            return convertToDto(savedPost, user);

        } catch (ValidationException | IllegalArgumentException e) {
            cleanupMediaFiles(null);
            throw e;
        } catch (Exception e) {
            log.error("Failed to create social post for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to create social post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public SocialPostDto updateSocialPost(Long postId, SocialPostUpdateDto updateDto, User user) {
        try {
            SocialPostUtility.validateSocialPostId(postId);
            PostUtility.validateUser(user);
            SocialPostUtility.validateSocialPostContent(updateDto.getContent());

            SocialPost socialPost = findById(postId);

            if (!SocialPostUtility.canUserModifySocialPost(socialPost, user)) {
                throw new SecurityException(
                        "User does not have permission to modify this social post");
            }

            contentValidationService.validateContent(updateDto.getContent());
            socialPost.setContent(updateDto.getContent().trim());

            if (updateDto.getHashtags() != null) {
                List<String> processedHashtags = extractAndValidateHashtags(
                        updateDto.getContent(), updateDto.getHashtags());
                socialPost.setHashtagsList(processedHashtags);
            }

            if (updateDto.getAllowComments() != null) {
                socialPost.setAllowComments(updateDto.getAllowComments());
            }

            SocialPost updatedPost = socialPostRepository.save(socialPost);
            log.info("Social post updated: ID={} User={}", postId, user.getActualUsername());
            return convertToDto(updatedPost, user);

        } catch (ValidationException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update social post {} by user: {}", postId, user.getActualUsername(), e);
            throw new ServiceException("Failed to update social post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public void deleteSocialPost(Long postId, User user) {
        try {
            SocialPostUtility.validateSocialPostId(postId);
            PostUtility.validateUser(user);

            SocialPost socialPost = findById(postId);

            if (!SocialPostUtility.canUserDeleteSocialPost(socialPost, user)) {
                throw new SecurityException(
                        "User does not have permission to delete this social post");
            }

            if (socialPost.hasMedia()) {
                cleanupMediaFiles(socialPost.getMediaUrlsList());
            }

            try {
                postInteractionService.cleanupForSocialPostDeletion(socialPost);
            } catch (Exception e) {
                log.warn("Failed to clean up interactions for socialPost={}: {}",
                        postId, e.getMessage());
            }

            Long communityId = socialPost.getCommunityId();
            socialPost.softDelete();
            socialPostRepository.save(socialPost);

            if (communityId != null) {
                try {
                    communityService.onPostDeleted(communityId);
                } catch (Exception e) {
                    log.warn("[Community] onPostDeleted failed for post={} community={}: {}",
                            postId, communityId, e.getMessage());
                }
            }

            log.info("Social post soft-deleted: ID={} Media={} User={}",
                    postId, socialPost.getMediaCount(), user.getActualUsername());

        } catch (ValidationException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete social post {} by user: {}", postId, user.getActualUsername(), e);
            throw new ServiceException("Failed to delete social post: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // GET SINGLE POST
    // =========================================================================

    @Transactional(readOnly = true)
    public SocialPostDto getSocialPostById(Long postId, User user) {
        try {
            SocialPostUtility.validateSocialPostId(postId);
            SocialPost socialPost = findById(postId);

            if (user != null && !SocialPostUtility.isSocialPostVisibleToUser(socialPost, user)) {
                throw new SecurityException(
                        "User does not have permission to view this social post");
            }

            return convertToDto(socialPost, user);

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get social post {}", postId, e);
            throw new ServiceException("Failed to retrieve social post: " + e.getMessage(), e);
        }
    }

    public SocialPost findById(Long postId) {
        return socialPostRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(
                        "Social post not found with ID: " + postId));
    }

    // =========================================================================
    // SAVE FEATURE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleSave(Long postId, User user) {
        PostUtility.validateUser(user);
        SocialPostUtility.validateSocialPostId(postId);
        SocialPost socialPost = findById(postId);
        boolean isSaved = postInteractionService.toggleSocialPostSave(socialPost, user);
        log.info("Toggle save: postId={} userId={} isSaved={}", postId, user.getId(), isSaved);
        return isSaved;
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getSavedPosts(User user, Long beforeId, Integer limit) {
        PostUtility.validateUser(user);
        int validLimit = PaginationUtils.validateLimit(limit);

        try {
            Page<SavedPost> page;
            if (beforeId != null && beforeId > 0) {
                page = postInteractionService.getSavedPostsForUserWithCursor(
                        user, beforeId, validLimit);
            } else {
                page = postInteractionService.getSavedPostsForUser(user, 0, validLimit);
            }

            List<SocialPostDto> dtos = page.getContent().stream()
                    .filter(SavedPost::isSocialPostSave)
                    .map(SavedPost::getSocialPost)
                    .filter(Objects::nonNull)
                    .map(sp -> convertToDto(sp, user))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            boolean hasMore    = page.hasNext();
            Long    nextCursor = hasMore && !dtos.isEmpty()
                    ? dtos.get(dtos.size() - 1).getId()
                    : null;

            return PaginatedResponse.of(dtos, hasMore, nextCursor, validLimit);

        } catch (Exception e) {
            log.error("Failed to get saved posts for user {}", user.getActualUsername(), e);
            return PaginationUtils.createEmptyResponse(validLimit);
        }
    }

    public boolean isSavedByUser(Long postId, User user) {
        if (user == null || postId == null) return false;
        SocialPost socialPost = socialPostRepository.findById(postId).orElse(null);
        if (socialPost == null) return false;
        return postInteractionService.hasSavedSocialPost(socialPost, user);
    }

    // =========================================================================
    // SHARE FEATURE
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordShare(Long postId, User user, ShareType shareType) {
        SocialPostUtility.validateSocialPostId(postId);
        SocialPost socialPost = findById(postId);
        PostShare share = postInteractionService.recordSocialPostShare(socialPost, user, shareType);
        log.info("Share recorded: socialPostId={} userId={} type={}",
                postId, user != null ? user.getId() : "anon", shareType);
        return share;
    }

    @Transactional(rollbackFor = Exception.class)
    public PostShare recordShare(Long postId, User user) {
        return recordShare(postId, user, ShareType.LINK_COPY);
    }

    public long getShareCount(Long postId) {
        SocialPost socialPost = socialPostRepository.findById(postId).orElse(null);
        if (socialPost == null) return 0L;
        return postInteractionService.getShareCountForSocialPost(socialPost);
    }

    // =========================================================================
    // LEGACY FEED / LISTING (pre-HLIG, kept for SocialPostController)
    // =========================================================================

    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getHomeFeed(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupSocialPostFeedPagination(
                    "getHomeFeed", beforeId, limit);

            String userStatePrefix    = user.hasPincode() ? user.getStatePrefix()    : null;
            String userDistrictPrefix = user.hasPincode() ? user.getDistrictPrefix() : null;

            List<SocialPost> posts = fetchRecommendedPosts(
                    userStatePrefix, userDistrictPrefix, setup);
            List<SocialPostDto> postDtos = convertToDtoBatch(posts, user);

            PaginatedResponse<SocialPostDto> response = PaginationUtils
                    .createSocialPostDtoResponse(postDtos, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getHomeFeed", postDtos,
                    response.isHasMore(), response.getNextCursor());
            return response;

        } catch (DataAccessException e) {
            log.error("Database error in getHomeFeed for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to retrieve home feed due to database error", e);
        } catch (Exception e) {
            log.error("Error getting home feed for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to retrieve home feed", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getTrendingPosts(
            User user, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupTrendingPostsPagination(
                    "getTrendingPosts", beforeId, limit);

            List<SocialPost> posts = fetchTrendingPosts(setup);

            List<SocialPostDto> postDtos = user != null
                    ? convertToDtoBatch(posts, user)
                    : convertToDtoSimple(posts);

            PaginatedResponse<SocialPostDto> response = PaginationUtils
                    .createSocialPostDtoResponse(postDtos, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getTrendingPosts", postDtos,
                    response.isHasMore(), response.getNextCursor());
            return response;

        } catch (Exception e) {
            log.error("Error getting trending posts", e);
            throw new ServiceException("Failed to retrieve trending posts", e);
        }
    }

    /**
     * Local posts feed — geographic pool sorted by engagement, scoped to the
     * user's pincode / district / state waterfall.
     *
     * Called by SocialPostController GET /api/social-posts/feed/local.
     * Delegates to getBrowseFeed(LOCATION, NEW) so it inherits the full
     * geo waterfall, own-post injection, sparse widening, and never-empty guarantees.
     *
     * Uses LOCATION + NEW (chronological) as the default sort for local posts
     * because locality is the primary filter — users browsing "local" care most
     * about what is happening right now near them, not what is trending nationally.
     */
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public PaginatedResponse<SocialPostDto> getLocalPosts(
            User user, Long beforeId, Integer limit) {
        int size = (limit != null && limit > 0 && limit <= Constant.MAX_PAGE_SIZE)
                ? limit : Constant.DEFAULT_PAGE_SIZE;
        return getBrowseFeed(user, FeedScope.LOCATION, FeedSort.NEW, beforeId, size);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> getUserPosts(
            Long userId, User currentUser, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUserId(userId);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupSocialPostFeedPagination(
                    "getUserPosts", beforeId, limit);

            List<SocialPost> posts = fetchUserPosts(userId, setup);

            List<SocialPostDto> postDtos = posts.stream()
                    .filter(post -> post.getStatus() == PostStatus.ACTIVE ||
                            (currentUser != null &&
                                    SocialPostUtility.isSocialPostOwner(post, currentUser)))
                    .map(post -> currentUser != null
                            ? convertToDto(post, currentUser)
                            : SocialPostDto.fromSocialPost(post))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            PaginatedResponse<SocialPostDto> response = PaginationUtils
                    .createSocialPostDtoResponse(postDtos, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("getUserPosts", postDtos,
                    response.isHasMore(), response.getNextCursor());
            return response;

        } catch (Exception e) {
            log.error("Error getting posts for user: {}", userId, e);
            throw new ServiceException("Failed to retrieve user posts", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SocialPostDto> searchByHashtag(
            String hashtag, User user, Long beforeId, Integer limit) {
        try {
            if (hashtag == null || hashtag.trim().isEmpty()) {
                throw new ValidationException("Hashtag cannot be empty");
            }

            String cleanHashtag = normalizeHashtag(hashtag);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupHashtagSearchPagination(
                    "searchByHashtag", beforeId, limit);

            List<SocialPost> posts = fetchPostsByHashtag(cleanHashtag, setup);

            List<SocialPostDto> postDtos = user != null
                    ? convertToDtoBatch(posts, user)
                    : convertToDtoSimple(posts);

            PaginatedResponse<SocialPostDto> response = PaginationUtils
                    .createSocialPostDtoResponse(postDtos, setup.getValidatedLimit());
            PaginationUtils.logPaginationResults("searchByHashtag", postDtos,
                    response.isHasMore(), response.getNextCursor());
            return response;

        } catch (Exception e) {
            log.error("Error searching posts by hashtag: {}", hashtag, e);
            throw new ServiceException("Failed to search posts by hashtag", e);
        }
    }

    // =========================================================================
    // HLIG v2 — BROWSE FEED (3 tabs × 3 sort modes)
    // =========================================================================
    //
    // Every tab endpoint in FeedController calls one of the 5 convenience methods
    // below. All 5 delegate to getBrowseFeed() which delegates to HLIGFeedService.
    //
    // Tab → scope + sort mapping:
    //
    //   /for-you   → FOR_YOU  + HOT   personalised pool, trending within interests
    //   /hot       → LOCATION + HOT   geo pool, 72h window, viralityScore DESC
    //   /new       → LOCATION + NEW   geo pool, createdAt DESC
    //   /top       → LOCATION + TOP   geo pool, all-time engagementScore DESC
    //   /following → FOLLOWING + HOT  community pool, trending sort
    //
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Core delegate for all 9 scope × sort combinations.
     * Called by every HLIG tab method. Passes size+1 so buildPagedResponse()
     * can detect hasMore without a separate COUNT query.
     */
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public PaginatedResponse<SocialPostDto> getBrowseFeed(
            User user, FeedScope scope, FeedSort sort, Long lastPostId, int size) {
        PostUtility.validateUser(user);
        try {
            List<SocialPost> posts = hligFeedService.getBrowseFeed(
                    user, scope, sort, lastPostId, size + 1);

            if (posts == null || posts.isEmpty()) {
                log.debug("[HLIG] getBrowseFeed empty scope={} sort={} userId={} — homeFeed fallback",
                        scope, sort, user.getId());
                return getHomeFeed(user, lastPostId, size);
            }

            return buildPagedResponse(posts, user, size);

        } catch (Exception e) {
            log.error("[HLIG] getBrowseFeed failed scope={} sort={} user={} — trending fallback",
                    scope, sort, user.getActualUsername(), e);
            return getTrendingPostsFallback(user, lastPostId, size);
        }
    }

    /**
     * FOR YOU tab — HLIG personalised pool, hot sort.
     * Cold users (no interactions) receive a geographic fallback inside HLIGFeedService.
     */
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public PaginatedResponse<SocialPostDto> getPersonalisedFeed(
            User user, Long lastPostId, int size) {
        return getBrowseFeed(user, FeedScope.FOR_YOU, FeedSort.HOT, lastPostId, size);
    }

    /**
     * HOT tab — geographic pool (pincode→district→state waterfall), trending in 72 hours.
     * viralityScore DESC within the 72h window; extends to 7-day on sparse areas.
     */
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public PaginatedResponse<SocialPostDto> getHotFeed(
            User user, Long lastPostId, int size) {
        return getBrowseFeed(user, FeedScope.LOCATION, FeedSort.HOT, lastPostId, size);
    }

    /**
     * NEW tab — geographic pool, pure chronological (createdAt DESC).
     * No time window; all active posts in the user's state + national.
     */
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public PaginatedResponse<SocialPostDto> getNewFeed(
            User user, Long lastPostId, int size) {
        return getBrowseFeed(user, FeedScope.LOCATION, FeedSort.NEW, lastPostId, size);
    }

    /**
     * TOP tab — geographic pool, all-time highest engagement.
     * engagementScore DESC, no time window; surfaces the best posts ever in the region.
     */
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public PaginatedResponse<SocialPostDto> getTopFeed(
            User user, Long lastPostId, int size) {
        return getBrowseFeed(user, FeedScope.LOCATION, FeedSort.TOP, lastPostId, size);
    }

    /**
     * FOLLOWING tab — posts from communities the user has joined, hot sort.
     * Falls back to cold-feed candidates inside HLIGFeedService when the user
     * has not joined any community yet.
     */
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public PaginatedResponse<SocialPostDto> getFollowingFeed(
            User user, Long lastPostId, int size) {
        return getBrowseFeed(user, FeedScope.FOLLOWING, FeedSort.HOT, lastPostId, size);
    }

    // =========================================================================
    // HLIG v2 — NEGATIVE SIGNALS
    // =========================================================================

    public void recordScrolledPast(Long postId, User user) {
        if (postId == null || user == null) return;
        try {
            SocialPost post = socialPostRepository.findById(postId).orElse(null);
            if (post != null) interestProfileService.onScrolledPast(user.getId(), post);
        } catch (Exception e) {
            log.warn("[HLIG] onScrolledPast failed: postId={} userId={} reason={}",
                    postId, user.getId(), e.getMessage());
        }
    }

    public void recordNotInterested(Long postId, User user) {
        if (postId == null || user == null) return;
        try {
            SocialPost post = socialPostRepository.findById(postId).orElse(null);
            if (post != null) {
                interestProfileService.onNotInterested(user.getId(), post);
                log.info("[HLIG] onNotInterested: postId={} userId={}", postId, user.getId());
            }
        } catch (Exception e) {
            log.warn("[HLIG] onNotInterested failed: postId={} userId={} reason={}",
                    postId, user.getId(), e.getMessage());
        }
    }

    // =========================================================================
    // PRIVATE — DTO CONVERSION
    // =========================================================================

    private SocialPostDto convertToDto(SocialPost post, User user) {
        if (post == null) return null;
        List<SocialPostDto> result = convertToDtoBatch(List.of(post), user);
        return result.isEmpty() ? null : result.get(0);
    }

    private Map<Long, Poll> loadPollMap(List<SocialPost> posts) {
        if (posts == null || posts.isEmpty()) return Collections.emptyMap();
        List<Long> postIds = posts.stream().map(SocialPost::getId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        List<Poll> polls = pollRepository.findBySocialPostIdIn(postIds);
        Map<Long, Poll> map = new HashMap<>();
        for (Poll poll : polls) {
            if (poll.getSocialPost() != null)
                map.put(poll.getSocialPost().getId(), poll);
        }
        return map;
    }

    private List<SocialPostDto> convertToDtoBatch(List<SocialPost> posts, User user) {
        if (posts == null || posts.isEmpty()) return Collections.emptyList();

        Map<Long, Poll> pollMap = loadPollMap(posts);

        Set<Long>             votedPollIds    = Collections.emptySet();
        Map<Long, List<Long>> votedOptionsMap = Collections.emptyMap();

        if (user != null && !pollMap.isEmpty()) {
            List<Long> pollIds = pollMap.values().stream()
                    .map(Poll::getId).collect(Collectors.toList());
            votedPollIds = new HashSet<>(
                    pollVoteRepository.findVotedPollIdsByUserAndPollIds(user.getId(), pollIds));
            List<Object[]> rows =
                    pollVoteRepository.findVotedOptionsByUserAndPollIds(user.getId(), pollIds);
            votedOptionsMap = new HashMap<>();
            for (Object[] row : rows) {
                Long pollId   = ((Number) row[0]).longValue();
                Long optionId = ((Number) row[1]).longValue();
                votedOptionsMap.computeIfAbsent(pollId, k -> new ArrayList<>()).add(optionId);
            }
        }

        final Map<Long, Poll>       fp  = pollMap;
        final Set<Long>             fvp = votedPollIds;
        final Map<Long, List<Long>> fvo = votedOptionsMap;

        return posts.stream()
                .map(p -> convertToDtoFromMaps(p, user, fp, fvp, fvo))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<SocialPostDto> convertToDtoSimple(List<SocialPost> posts) {
        if (posts == null || posts.isEmpty()) return Collections.emptyList();
        Map<Long, Poll> pollMap = loadPollMap(posts);
        return posts.stream()
                .map(post -> {
                    if (post == null) return null;
                    Poll poll = pollMap.get(post.getId());
                    return SocialPostDto.fromSocialPostWithInteractions(
                            post, false, false, false, poll, false, List.of());
                })
                .filter(Objects::nonNull).collect(Collectors.toList());
    }

    private SocialPostDto convertToDtoFromMaps(
            SocialPost post, User user,
            Map<Long, Poll> pollMap,
            Set<Long> votedPollIds,
            Map<Long, List<Long>> votedOptionsMap) {
        try {
            if (post == null) return null;
            boolean isLiked  = user != null && postInteractionService.hasUserLikedSocialPost(post, user);
            boolean isSaved  = user != null && postInteractionService.hasSavedSocialPost(post, user);
            boolean isViewed = user != null && postInteractionService.hasUserViewedSocialPostRecently(post, user);
            Poll       poll         = pollMap.get(post.getId());
            boolean    userHasVoted = false;
            List<Long> votedIds     = List.of();
            if (poll != null && user != null) {
                userHasVoted = votedPollIds.contains(poll.getId());
                if (userHasVoted) votedIds = votedOptionsMap.getOrDefault(poll.getId(), List.of());
            }
            return SocialPostDto.fromSocialPostWithInteractions(
                    post, isLiked, isSaved, isViewed, poll, userHasVoted, votedIds);
        } catch (Exception e) {
            log.warn("Failed to convert social post {} to DTO: {}", post.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Shared cursor-page builder used by getBrowseFeed and all legacy feed methods.
     * Caller passes size+1 posts; this method trims and computes hasMore + nextCursor.
     */
    private PaginatedResponse<SocialPostDto> buildPagedResponse(
            List<SocialPost> posts, User user, int size) {
        List<SocialPostDto> dtos = convertToDtoBatch(
                posts != null ? posts : Collections.emptyList(), user);
        boolean hasMore = dtos.size() > size;
        if (hasMore) dtos = new ArrayList<>(dtos.subList(0, size));
        Long nextCursor = hasMore && !dtos.isEmpty()
                ? dtos.get(dtos.size() - 1).getId()
                : null;
        return PaginatedResponse.of(dtos, hasMore, nextCursor, size);
    }

    // =========================================================================
    // PRIVATE — FETCH HELPERS
    // =========================================================================

    private List<SocialPost> fetchRecommendedPosts(
            String statePrefix, String districtPrefix, PaginationUtils.PaginationSetup setup) {

        List<SocialPost> posts;
        if (setup.hasCursor()) {
            posts = socialPostRepository.findRecommendedPostsForUserWithCursor(
                    statePrefix, districtPrefix,
                    setup.getSanitizedCursor(), setup.toPageable());
        } else {
            posts = socialPostRepository.findRecommendedPostsForUser(
                    statePrefix, districtPrefix, setup.toPageable());
        }

        if (posts == null || posts.size() < Math.max(1, setup.getValidatedLimit() / 2)) {
            log.debug("[Feed] fetchRecommendedPosts: only {} — falling back to all active",
                    posts == null ? 0 : posts.size());
            List<SocialPost> fallback = setup.hasCursor()
                    ? socialPostRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(
                    PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : socialPostRepository.findByStatusOrderByCreatedAtDesc(
                    PostStatus.ACTIVE, setup.toPageable());

            if (fallback != null && !fallback.isEmpty()) {
                Map<Long, SocialPost> merged = new LinkedHashMap<>();
                if (posts != null) posts.forEach(p -> merged.put(p.getId(), p));
                fallback.forEach(p -> merged.putIfAbsent(p.getId(), p));
                posts = new ArrayList<>(merged.values());
            }
        }

        return posts != null ? posts : Collections.emptyList();
    }

    private List<SocialPost> fetchTrendingPosts(PaginationUtils.PaginationSetup setup) {
        List<SocialPost> posts;
        if (setup.hasCursor()) {
            posts = socialPostRepository
                    .findByIsViralTrueAndStatusAndIdLessThanOrderByViralityScoreDesc(
                            PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable());
        } else {
            posts = socialPostRepository.findByIsViralTrueAndStatusOrderByViralityScoreDesc(
                    PostStatus.ACTIVE, setup.toPageable());
        }

        if (posts == null || posts.size() < Math.max(1, setup.getValidatedLimit() / 2)) {
            log.debug("[Feed] fetchTrendingPosts: only {} viral — fallback to active",
                    posts == null ? 0 : posts.size());
            List<SocialPost> fallback = setup.hasCursor()
                    ? socialPostRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(
                    PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable())
                    : socialPostRepository.findByStatusOrderByCreatedAtDesc(
                    PostStatus.ACTIVE, setup.toPageable());

            if (fallback != null && !fallback.isEmpty()) {
                Map<Long, SocialPost> merged = new LinkedHashMap<>();
                if (posts != null) posts.forEach(p -> merged.put(p.getId(), p));
                fallback.forEach(p -> merged.putIfAbsent(p.getId(), p));
                posts = new ArrayList<>(merged.values());
            }
        }

        return posts != null ? posts : Collections.emptyList();
    }

    private List<SocialPost> fetchUserPosts(Long userId, PaginationUtils.PaginationSetup setup) {
        return setup.hasCursor()
                ? socialPostRepository.findByUserIdAndIdLessThanOrderByCreatedAtDesc(
                userId, setup.getSanitizedCursor(), setup.toPageable())
                : socialPostRepository.findByUserIdOrderByCreatedAtDesc(
                userId, setup.toPageable());
    }

    private List<SocialPost> fetchPostsByHashtag(
            String hashtag, PaginationUtils.PaginationSetup setup) {
        return setup.hasCursor()
                ? socialPostRepository.findByHashtagContainingAndIdLessThan(
                hashtag, PostStatus.ACTIVE,
                setup.getSanitizedCursor(), setup.toPageable())
                : socialPostRepository.findByHashtagContaining(
                hashtag, PostStatus.ACTIVE, setup.toPageable());
    }

    /**
     * Emergency last-resort when getBrowseFeed() itself throws.
     * Degrades to trending (viral) posts so the tab is never a 500.
     */
    private PaginatedResponse<SocialPostDto> getTrendingPostsFallback(
            User user, Long lastPostId, int size) {
        try {
            return getTrendingPosts(user, lastPostId, size);
        } catch (Exception e) {
            log.error("[HLIG] getTrendingPostsFallback also failed userId={}",
                    user != null ? user.getId() : "null", e);
            return PaginatedResponse.of(Collections.emptyList(), false, null, size);
        }
    }

    // =========================================================================
    // PRIVATE — BUILD / VALIDATE HELPERS
    // =========================================================================

    private List<String> extractAndValidateHashtags(
            String content, List<String> providedHashtags) {

        List<String> extractedFromContent =
                SocialPostUtility.extractHashtagsFromContent(content);
        Set<String> allHashtags = new HashSet<>(extractedFromContent);

        if (providedHashtags != null && !providedHashtags.isEmpty()) {
            allHashtags.addAll(providedHashtags.stream()
                    .map(this::normalizeHashtag)
                    .collect(Collectors.toList()));
        }

        List<String> finalHashtags = new ArrayList<>(allHashtags);
        SocialPostUtility.validateHashtags(finalHashtags);
        return finalHashtags;
    }

    private List<Long> extractAndValidateMentions(
            String content, List<Long> providedMentions) {

        List<String> extractedUsernames =
                SocialPostUtility.extractMentionsFromContent(content);
        Set<Long> allMentionedUserIds = new HashSet<>();

        if (!extractedUsernames.isEmpty()) {
            List<User> mentionedUsers =
                    userRepository.findByUsernameIn(extractedUsernames);
            allMentionedUserIds.addAll(mentionedUsers.stream()
                    .map(User::getId)
                    .collect(Collectors.toList()));
        }

        if (providedMentions != null && !providedMentions.isEmpty()) {
            allMentionedUserIds.addAll(providedMentions);
        }

        List<Long> finalMentions = new ArrayList<>(allMentionedUserIds);
        if (finalMentions.size() > Constant.MAX_MENTIONS_PER_POST) {
            throw new ValidationException("Cannot mention more than " +
                    Constant.MAX_MENTIONS_PER_POST + " users");
        }
        return finalMentions;
    }

    private List<String> uploadMediaFilesWithValidation(
            List<MultipartFile> files, Long userId) {
        try {
            SocialPostUtility.validateMediaFiles(files);
            return mediaService.uploadMediaFiles(files, userId);
        } catch (Exception e) {
            log.error("Failed to upload media files for user: {}", userId, e);
            throw new ServiceException(
                    "Failed to upload media files: " + e.getMessage(), e);
        }
    }

    private SocialPost buildSocialPost(
            SocialPostCreateDto createDto,
            User user,
            List<String> hashtags,
            List<Long> mentionedUserIds,
            List<String> mediaUrls) {

        SocialPost socialPost = SocialPost.builder()
                .content(createDto.getContent().trim())
                .user(user)
                .status(PostStatus.ACTIVE)
                .allowComments(createDto.getAllowComments() != null
                        ? createDto.getAllowComments() : true)
                .showLocation(createDto.getShowLocation() != null
                        ? createDto.getShowLocation() : false)
                .createdAt(new Date())
                .build();

        socialPost.inheritLocationFromUser(user);

        if (!mediaUrls.isEmpty())        socialPost.setMediaUrlsList(mediaUrls);
        if (!hashtags.isEmpty())         socialPost.setHashtagsList(hashtags);
        if (!mentionedUserIds.isEmpty()) socialPost.setMentionedUserIdsList(mentionedUserIds);

        return socialPost;
    }

    private String normalizeHashtag(String hashtag) {
        String cleaned = hashtag.trim().toLowerCase();
        return cleaned.startsWith("#") ? cleaned : "#" + cleaned;
    }

    private void cleanupMediaFiles(List<String> mediaUrls) {
        if (mediaUrls != null && !mediaUrls.isEmpty()) {
            try {
                mediaService.deleteMediaFiles(mediaUrls);
            } catch (Exception e) {
                log.warn("Failed to clean up media files, will be handled by cleanup job", e);
            }
        }
    }
}