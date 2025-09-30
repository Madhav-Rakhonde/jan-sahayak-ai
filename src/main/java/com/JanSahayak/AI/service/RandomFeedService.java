package com.JanSahayak.AI.service;

import java.util.*;
import java.util.stream.Collectors;
import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.repository.PostRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RandomFeedService {

    @Autowired
    private PostRepo postRepository;

    @Autowired
    private UserTaggingService userTaggingService;

    /**
     * Get random active posts from across all locations in India for non-logged-in users
     * Shows only ACTIVE posts from any state, district, or pincode with cursor-based pagination
     * No user context required - designed for anonymous browsing with infinite scroll
     *
     * @param beforeId Cursor for pagination (post ID to start from)
     * @param limit Maximum number of posts to return (optional, defaults to 20)
     * @return PaginatedResponse of random active posts from various locations across India
     */
    public PaginatedResponse<PostResponse> getRandomActiveFeedForAnonymous(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getRandomActiveFeedForAnonymous", beforeId, limit,
                    Constant.DEFAULT_FEED_LIMIT, 50); // Max 50 posts for anonymous users

            log.info("Getting random active feed for anonymous user, beforeId: {}, limit: {}",
                    setup.getSanitizedCursor(), setup.getValidatedLimit());


            int fetchMultiplier = 6; // Fetch 6x more posts for better randomization
            int fetchLimit = setup.getValidatedLimit() * fetchMultiplier;

            List<Post> activePosts;
            if (setup.hasCursor()) {
                activePosts = postRepository.findByStatusAndIdLessThanOrderByCreatedAtDesc(
                        PostStatus.ACTIVE, setup.getSanitizedCursor(),
                        PaginationUtils.createPageable(fetchLimit));
            } else {
                activePosts = postRepository.findByStatusOrderByCreatedAtDesc(
                        PostStatus.ACTIVE, PaginationUtils.createPageable(fetchLimit));
            }

            if (activePosts == null || activePosts.isEmpty()) {
                log.info("No active posts found for anonymous random feed");
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            log.info("Found {} active posts, filtering and shuffling for random selection", activePosts.size());

            // Filter posts before randomization for better performance
            List<Post> eligiblePosts = activePosts.stream()
                    .filter(post -> post != null && PostUtility.isPostVisibleToAnonymousUser(post))
                    .collect(Collectors.toList());

            // Shuffle for complete randomness across all locations in India
            Collections.shuffle(eligiblePosts);

            // Take only the requested number of posts after shuffling
            List<Post> selectedPosts = eligiblePosts.stream()
                    .distinct()
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());

            // Convert to response objects - no user context needed for anonymous users
            List<PostResponse> postResponses = selectedPosts.stream()
                    .map(this::convertToAnonymousPostResponse)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // For random feeds, we determine hasMore based on whether we got the full requested amount
            // and there were more eligible posts available
            boolean hasMore = (selectedPosts.size() == setup.getValidatedLimit()) &&
                    (eligiblePosts.size() > setup.getValidatedLimit()) &&
                    (activePosts.size() == fetchLimit); // Indicates more posts exist in DB

            Long nextCursor = hasMore && !selectedPosts.isEmpty() ?
                    selectedPosts.get(selectedPosts.size() - 1).getId() : null;

            PaginatedResponse<PostResponse> response = PaginatedResponse.of(
                    postResponses, hasMore, nextCursor, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getRandomActiveFeedForAnonymous",
                    postResponses, hasMore, nextCursor);

            log.info("Returning {} random posts to anonymous user, hasMore: {}",
                    postResponses.size(), hasMore);

            return response;

        } catch (Exception e) {
            log.error("Failed to get random active feed for anonymous user", e);
            return PaginationUtils.handlePaginationError("getRandomActiveFeedForAnonymous", e,
                    PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Convert Post to PostResponse for anonymous users (no user-specific data)
     */
    private PostResponse convertToAnonymousPostResponse(Post post) {
        try {
            if (post == null) {
                return null;
            }

            return PostResponse.builder()
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

                    // User Information (limited for anonymous users)
                    .userId(post.getUser() != null ? post.getUser().getId() : null)
                    .username(post.getUser() != null ? post.getUser().getActualUsername() : null)
                    .userDisplayName(post.getUser() != null ? post.getUser().getDisplayName() : null)
                    .userProfileImage(post.getUser() != null ? post.getUser().getProfileImage() : null)
                    // Don't expose user pincode for anonymous users
                    .userPincode(null)

                    // Broadcasting Information
                    .broadcastScope(post.getBroadcastScope())
                    .broadcastScopeDescription(post.getBroadcastScopeDescription())
                    .isBroadcastPost(post.isBroadcastPost())
                    .targetCountry(post.getTargetCountry())
                    // Limit broadcast targeting details for anonymous users
                    .targetStates(post.isBroadcastPost() && post.getBroadcastScope() == BroadcastScope.STATE ?
                            PostUtility.getStateNamesFromPrefixes(post.getTargetStatesList()) : null)
                    .targetDistricts(null) // Don't expose district details
                    .targetPincodes(null)  // Don't expose pincode details

                    // Engagement Metrics
                    .likeCount(post.getLikeCount())
                    .commentCount(post.getCommentCount())
                    .viewCount(post.getViewCount())
                    .taggedUserCount(post.getTaggedUserCount())

                    // User Interaction Status (anonymous users can't interact)
                    .isLikedByCurrentUser(false)
                    .isViewedByCurrentUser(false)

                    // Status Information
                    .statusDisplayName(post.getStatus() != null ? post.getStatus().getDisplayName() : null)
                    .canBeResolved(false) // Anonymous users can't resolve posts
                    .allowsUpdates(false) // Anonymous users can't update posts
                    .isEligibleForDisplay(PostUtility.isPostEligibleForDisplay(post))

                    // Additional Metadata
                    .timeAgo(PostUtility.calculateTimeAgo(post.getCreatedAt()))
                    .isVisibleToCurrentUser(true) // All posts in this feed are visible to anonymous users

                    // Tagged users (limited info for anonymous users)
                    .taggedUsernames(getPublicTaggedUsernames(post))
                    .taggedUsers(Collections.emptyList()) // Don't expose detailed tag info to anonymous users

                    .build();

        } catch (Exception e) {
            log.error("Failed to convert post to anonymous response: {}",
                    post != null ? post.getId() : "null", e);
            throw new ServiceException("Failed to convert post to response: " + e.getMessage(), e);
        }
    }

    /**
     * Get public usernames of tagged users (for anonymous viewing)
     */
    private List<String> getPublicTaggedUsernames(Post post) {
        try {
            // Use paginated method with small limit for tagged users
            PaginatedResponse<User> taggedUsersResponse =
                    userTaggingService.getTaggedUsersInPost(post, null, 5);

            return taggedUsersResponse.getData().stream()
                    .filter(user -> user != null && user.getIsActive())
                    .map(User::getActualUsername)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get public tagged usernames for post: {}", post.getId(), e);
            return Collections.emptyList();
        }
    }
}

