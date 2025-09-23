package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.UserTagSuggestionDto;
import com.JanSahayak.AI.DTO.UserTagValidationResult;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.*;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.UserTag;
import com.JanSahayak.AI.repository.*;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.payload.PostUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.ValidationException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserTaggingService {

    private final UserRepo userRepository;
    private final PostRepo postRepository;
    private final UserTagRepo userTagRepository;
    private final PinCodeLookupService pincodeLookupService;

    @Transactional
    public void processUserTags(Post post) {
        try {
            PostUtility.validatePost(post);
            log.info("Processing user tags for post ID: {} (status: {})", post.getId(), post.getStatus().getDisplayName());

            List<String> extractedUsernames = PostUtility.extractUserTags(post.getContent());

            if (extractedUsernames.size() > Constant.MAX_TAGS_PER_POST) {
                log.warn("Post {} has {} user tags, which exceeds the maximum of {}. Processing first {} tags.",
                        post.getId(), extractedUsernames.size(), Constant.MAX_TAGS_PER_POST, Constant.MAX_TAGS_PER_POST);
                extractedUsernames = extractedUsernames.subList(0, Constant.MAX_TAGS_PER_POST);
            }

            if (!extractedUsernames.isEmpty()) {
                List<User> validUsers = userRepository.findByUsernameInAndIsActiveTrue(extractedUsernames);
                if (validUsers == null) {
                    validUsers = Collections.emptyList();
                }

                // Clear existing active tags
                List<UserTag> existingTags = userTagRepository.findByPostAndIsActiveTrue(post);
                if (existingTags == null) {
                    existingTags = Collections.emptyList();
                }

                existingTags.forEach(tag -> tag.deactivate(post.getUser(), "Post content updated"));

                if (!existingTags.isEmpty()) {
                    userTagRepository.saveAll(existingTags);
                }

                // Create new UserTag entities with location awareness using pincode prefix logic
                List<UserTag> newTags = new ArrayList<>();
                for (User user : validUsers) {
                    String content = post.getContent();
                    String username = "@" + user.getActualUsername();
                    int startPos = content.indexOf(username);

                    if (startPos != -1) {
                        UserTag userTag = UserTag.builder()
                                .post(post)
                                .taggedUser(user)
                                .taggedBy(post.getUser())
                                .isActive(true)
                                .taggedAt(new Date())
                                .build();

                        newTags.add(userTag);
                    }
                }

                // Save new tags
                if (!newTags.isEmpty()) {
                    userTagRepository.saveAll(newTags);
                    log.info("Created {} user tags for post ID: {} (status: {})",
                            newTags.size(), post.getId(), post.getStatus().getDisplayName());
                }
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to process user tags for post: {}", post.getId(), e);
            throw new ServiceException("Failed to process user tags: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void addUserTag(Post post, User user) {
        try {
            PostUtility.validatePost(post);
            PostUtility.validateUser(user);

            // Check if post allows tag updates based on status
            if (!PostUtility.postAllowsUpdates(post)) {
                log.warn("Attempted to add tag to post {} with status: {}",
                        post.getId(), post.getStatus().getDisplayName());
                throw new ServiceException("Cannot add tags to posts with status: " + post.getStatus().getDisplayName());
            }

            // Check if tag already exists and is active
            Optional<UserTag> existingTag = userTagRepository.findByPostAndTaggedUserAndIsActiveTrue(post, user);

            if (existingTag.isEmpty()) {
                UserTag userTag = UserTag.builder()
                        .post(post)
                        .taggedUser(user)
                        .taggedBy(post.getUser())
                        .isActive(true)
                        .taggedAt(new Date())
                        .build();

                userTagRepository.save(userTag);
                log.info("Added user tag: {} to post ID: {} (status: {})",
                        user.getActualUsername(), post.getId(), post.getStatus().getDisplayName());
            } else {
                log.debug("User tag already exists for user: {} on post: {}", user.getActualUsername(), post.getId());
            }
        } catch (ValidationException | ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to add user tag for user: {} to post: {}", user.getActualUsername(), post.getId(), e);
            throw new ServiceException("Failed to add user tag: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void removeUserTag(Post post, User user) {
        try {
            PostUtility.validatePost(post);
            PostUtility.validateUser(user);

            // Check if post allows tag updates based on status
            if (!PostUtility.postAllowsUpdates(post)) {
                log.warn("Attempted to remove tag from post {} with status: {}",
                        post.getId(), post.getStatus().getDisplayName());
                throw new ServiceException("Cannot remove tags from posts with status: " + post.getStatus().getDisplayName());
            }

            Optional<UserTag> userTag = userTagRepository.findByPostAndTaggedUserAndIsActiveTrue(post, user);

            if (userTag.isPresent()) {
                userTag.get().deactivate(post.getUser(), "Tag removed");
                userTagRepository.save(userTag.get());
                log.info("Removed user tag: {} from post ID: {} (status: {})",
                        user.getActualUsername(), post.getId(), post.getStatus().getDisplayName());
            } else {
                log.warn("Attempted to remove non-existent user tag: {} from post: {}",
                        user.getActualUsername(), post.getId());
            }
        } catch (ValidationException | ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to remove user tag for user: {} from post: {}", user.getActualUsername(), post.getId(), e);
            throw new ServiceException("Failed to remove user tag: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void updatePostTags(Post post, String newContent) {
        try {
            PostUtility.validatePost(post);
            if (newContent == null) {
                throw new ValidationException("New content cannot be null");
            }

            // Check if post allows tag updates based on status
            if (!PostUtility.postAllowsUpdates(post)) {
                log.warn("Attempted to update tags for post {} with status: {}",
                        post.getId(), post.getStatus().getDisplayName());
                throw new ServiceException("Cannot update tags for posts with status: " + post.getStatus().getDisplayName());
            }

            log.info("Updating post tags for post ID: {} (status: {})", post.getId(), post.getStatus().getDisplayName());

            // Get current active tags
            List<UserTag> currentTags = userTagRepository.findByPostAndIsActiveTrue(post);
            if (currentTags == null) {
                currentTags = Collections.emptyList();
            }

            // Extract new usernames from updated content
            List<String> newUsernames = PostUtility.extractUserTags(newContent);

            if (newUsernames.size() > Constant.MAX_TAGS_PER_POST) {
                log.warn("Updated content has {} user tags, which exceeds the maximum of {}. Processing first {} tags.",
                        newUsernames.size(), Constant.MAX_TAGS_PER_POST, Constant.MAX_TAGS_PER_POST);
                newUsernames = newUsernames.subList(0, Constant.MAX_TAGS_PER_POST);
            }

            List<User> newTaggedUsers = userRepository.findByUsernameInAndIsActiveTrue(newUsernames);
            if (newTaggedUsers == null) {
                newTaggedUsers = Collections.emptyList();
            }

            // Deactivate current tags
            currentTags.forEach(tag -> tag.deactivate(post.getUser(), "Post content updated"));
            if (!currentTags.isEmpty()) {
                userTagRepository.saveAll(currentTags);
            }

            // Create new tags with location awareness using pincode prefix logic
            List<UserTag> newTags = new ArrayList<>();
            for (User user : newTaggedUsers) {
                String content = newContent;
                String username = "@" + user.getActualUsername();
                int startPos = content.indexOf(username);

                if (startPos != -1) {
                    UserTag userTag = UserTag.builder()
                            .post(post)
                            .taggedUser(user)
                            .taggedBy(post.getUser())
                            .isActive(true)
                            .taggedAt(new Date())
                            .build();

                    newTags.add(userTag);
                }
            }

            // Save new tags
            if (!newTags.isEmpty()) {
                userTagRepository.saveAll(newTags);
            }

            log.info("Updated post tags - removed {} old tags, added {} new tags for post ID: {} (status: {})",
                    currentTags.size(), newTags.size(), post.getId(), post.getStatus().getDisplayName());
        } catch (ValidationException | ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update post tags for post: {}", post.getId(), e);
            throw new ServiceException("Failed to update post tags: " + e.getMessage(), e);
        }
    }

    // ===== Geographic-Aware Query Methods Using Pincode Prefix Logic =====

    /**
     * Get posts visible to user with cursor-based pagination
     * Updated method signature to include cursor and limit parameters
     */
    public PaginatedResponse<Post> getPostsVisibleToUser(User user, Boolean isResolved, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getPostsVisibleToUser", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                if (isResolved != null) {
                    PostStatus status = isResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                    posts = userTagRepository.findPostsWhereUserIsTaggedByStatusWithCursor(user, status, setup.getSanitizedCursor(), setup.toPageable());
                } else {
                    posts = userTagRepository.findPostsWhereUserIsTaggedWithCursor(user, setup.getSanitizedCursor(), setup.toPageable());
                }
            } else {
                if (isResolved != null) {
                    PostStatus status = isResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                    posts = userTagRepository.findPostsWhereUserIsTaggedByStatusOrderByIdDesc(user, status, setup.toPageable());
                } else {
                    posts = userTagRepository.findPostsWhereUserIsTaggedOrderByIdDesc(user, setup.toPageable());
                }
            }

            if (posts == null) {
                posts = Collections.emptyList();
            }

            // Filter posts based on geographic visibility using pincode prefix logic
            List<Post> filteredPosts = posts.stream()
                    .filter(post -> post != null &&
                            PostUtility.isPostStatusVisible(post) &&
                            PostUtility.isPostGeographicallyRelevantToUser(post, user))
                    .collect(Collectors.toList());

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(filteredPosts, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getPostsVisibleToUser",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get posts visible to user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get posts visible to user: " + e.getMessage(), e);
        }
    }

    /**
     * Get active posts visible to user with cursor-based pagination
     * Updated method signature to include cursor and limit parameters
     */
    public PaginatedResponse<Post> getActivePostsVisibleToUser(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getActivePostsVisibleToUser", beforeId, limit);

            List<Post> activePosts;
            if (setup.hasCursor()) {
                activePosts = userTagRepository.findPostsWhereUserIsTaggedByStatusWithCursor(user, PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                activePosts = userTagRepository.findPostsWhereUserIsTaggedByStatusOrderByIdDesc(user, PostStatus.ACTIVE, setup.toPageable());
            }

            if (activePosts == null) {
                activePosts = Collections.emptyList();
            }

            // Filter posts based on geographic visibility using pincode prefix logic
            List<Post> filteredPosts = activePosts.stream()
                    .filter(post -> post != null &&
                            post.getStatus() == PostStatus.ACTIVE &&
                            PostUtility.isPostStatusVisible(post) &&
                            PostUtility.isPostGeographicallyRelevantToUser(post, user))
                    .collect(Collectors.toList());

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(filteredPosts, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getActivePostsVisibleToUser",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get active posts visible to user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get active posts visible to user: " + e.getMessage(), e);
        }
    }

    /**
     * Get resolved posts visible to user with cursor-based pagination
     * Updated method signature to include cursor and limit parameters
     */
    public PaginatedResponse<Post> getResolvedPostsVisibleToUser(User user, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getResolvedPostsVisibleToUser", beforeId, limit);

            List<Post> resolvedPosts;
            if (setup.hasCursor()) {
                resolvedPosts = userTagRepository.findPostsWhereUserIsTaggedByStatusWithCursor(user, PostStatus.RESOLVED, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                resolvedPosts = userTagRepository.findPostsWhereUserIsTaggedByStatusOrderByIdDesc(user, PostStatus.RESOLVED, setup.toPageable());
            }

            if (resolvedPosts == null) {
                resolvedPosts = Collections.emptyList();
            }

            // Filter posts based on geographic visibility using pincode prefix logic
            List<Post> filteredPosts = resolvedPosts.stream()
                    .filter(post -> post != null &&
                            post.getStatus() == PostStatus.RESOLVED &&
                            PostUtility.isPostStatusVisible(post) &&
                            PostUtility.isPostGeographicallyRelevantToUser(post, user))
                    .collect(Collectors.toList());

            PaginatedResponse<Post> response = PaginationUtils.createPostResponse(filteredPosts, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getResolvedPostsVisibleToUser",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get resolved posts visible to user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get resolved posts visible to user: " + e.getMessage(), e);
        }
    }

    /**
     * Get tagged users in post with cursor-based pagination
     * Updated method signature to include cursor and limit parameters
     */
    public PaginatedResponse<User> getTaggedUsersInPost(Post post, Long beforeId, Integer limit) {
        try {
            PostUtility.validatePost(post);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getTaggedUsersInPost", beforeId, limit);

            List<UserTag> activeTags;
            if (setup.hasCursor()) {
                activeTags = userTagRepository.findByPostAndIsActiveTrueAndIdLessThanOrderByIdDesc(post, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                activeTags = userTagRepository.findByPostAndIsActiveTrueOrderByIdDesc(post, setup.toPageable());
            }

            if (activeTags == null) {
                activeTags = Collections.emptyList();
            }

            List<User> taggedUsers = activeTags.stream()
                    .filter(Objects::nonNull)
                    .map(UserTag::getTaggedUser)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(taggedUsers, setup.getValidatedLimit());

            log.debug("Found {} tagged users in post ID: {} (status: {})",
                    taggedUsers.size(), post.getId(), post.getStatus().getDisplayName());

            PaginationUtils.logPaginationResults("getTaggedUsersInPost",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get tagged users in post: {}", post.getId(), e);
            throw new ServiceException("Failed to get tagged users in post: " + e.getMessage(), e);
        }
    }

    public boolean isUserTaggedInPost(Post post, User user) {
        try {
            PostUtility.validatePost(post);
            PostUtility.validateUser(user);

            Boolean isTagged = userTagRepository.existsByPostAndTaggedUserAndIsActiveTrue(post, user);
            boolean tagged = isTagged != null && isTagged;

            log.debug("User {} {} tagged in post ID: {} (status: {})",
                    user.getActualUsername(), tagged ? "IS" : "IS NOT",
                    post.getId(), post.getStatus().getDisplayName());

            return tagged;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to check if user {} is tagged in post: {}", user.getActualUsername(), post.getId(), e);
            return false; // Return false on error to be safe
        }
    }

    // ===== Statistics Methods with Geographic Awareness =====

    public Map<String, Long> getTaggingStatistics(User user) {
        try {
            PostUtility.validateUser(user);

            // Get first page of tagged posts for statistics
            PaginatedResponse<Post> taggedPosts = getPostsVisibleToUser(user, null, null, 1000);
            PaginatedResponse<Post> activePosts = getActivePostsVisibleToUser(user, null, 1000);
            PaginatedResponse<Post> resolvedPosts = getResolvedPostsVisibleToUser(user, null, 1000);

            Map<String, Long> result = new HashMap<>();
            result.put("totalTaggedPosts", (long) taggedPosts.getData().size());
            result.put("activeTaggedPosts", (long) activePosts.getData().size());
            result.put("resolvedTaggedPosts", (long) resolvedPosts.getData().size());

            // Calculate resolution rate
            double resolutionRate = taggedPosts.getData().isEmpty() ? 0.0 :
                    (double) resolvedPosts.getData().size() / taggedPosts.getData().size() * 100;
            result.put("resolutionRate", Math.round(resolutionRate));

            // Additional statistics by post status
            Map<PostStatus, Long> statusCounts = taggedPosts.getData().stream()
                    .filter(Objects::nonNull)
                    .filter(post -> post.getStatus() != null)
                    .collect(Collectors.groupingBy(Post::getStatus, Collectors.counting()));

            for (PostStatus status : PostStatus.values()) {
                result.put("posts" + status.name(), statusCounts.getOrDefault(status, 0L));
            }

            log.debug("Generated tagging statistics for user: {} - {} total tagged posts",
                    user.getActualUsername(), taggedPosts.getData().size());

            return result;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get tagging statistics for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get tagging statistics: " + e.getMessage(), e);
        }
    }

    // ===== Geographic-Aware User Tag Suggestions =====

    /**
     * Get user tag suggestions with cursor-based pagination
     * Updated method signature to include cursor and limit parameters
     */
    public PaginatedResponse<UserTagSuggestionDto> getUserTagSuggestions(String query, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupTaggingPagination("getUserTagSuggestions", beforeId, limit);

            if (query == null || query.trim().length() < 2) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            // Remove @ if present
            String cleanQuery = query.startsWith("@") ? query.substring(1) : query;

            if (cleanQuery.trim().length() < 2) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<User> users;
            if (setup.hasCursor()) {
                users = userRepository.searchUsersForTaggingWithCursor(cleanQuery.trim(), setup.getSanitizedCursor(), setup.toPageable());
            } else {
                users = userRepository.searchUsersForTagging(cleanQuery.trim(), setup.toPageable());
            }

            if (users == null) {
                users = Collections.emptyList();
            }

            List<UserTagSuggestionDto> userDtos = users.stream()
                    .filter(Objects::nonNull)
                    .map(user -> UserTagSuggestionDto.builder()
                            .id(user.getId())
                            .username(user.getActualUsername())
                            .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getActualUsername())
                            .pincode(user.getPincode())
                            .profileImage(user.getProfileImage())
                            .isActive(user.getIsActive())
                            .role(user.getRole() != null ? user.getRole().getName() : null)
                            .bio(PostUtility.truncateText(user.getBio(), 100))
                            .taggableName("@" + user.getActualUsername())
                            .hasLocation(user.hasLocation())
                            .totalTaggedPosts(getUserTagCount(user))
                            .resolutionRate(calculateUserResolutionRate(user))
                            .build())
                    .collect(Collectors.toList());

            PaginatedResponse<UserTagSuggestionDto> response = PaginationUtils.createIdBasedResponse(
                    userDtos, setup.getValidatedLimit(), UserTagSuggestionDto::getId);

            PaginationUtils.logPaginationResults("getUserTagSuggestions",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get user tag suggestions for query: {}", query, e);
            throw new ServiceException("Failed to get user tag suggestions: " + e.getMessage(), e);
        }
    }

    public UserTagValidationResult validateUserTags(String content) {
        try {
            if (content == null) {
                content = "";
            }

            List<String> extractedUsernames = PostUtility.extractUserTags(content);

            if (extractedUsernames.isEmpty()) {
                return UserTagValidationResult.builder()
                        .isValid(true)
                        .validUsernames(Collections.emptyList())
                        .invalidUsernames(Collections.emptyList())
                        .warnings(Collections.emptyList())
                        .totalTaggedUsers(0)
                        .message("No user tags found")
                        .exceedsMaxTags(false)
                        .maxTagsAllowed(Constant.MAX_TAGS_PER_POST)
                        .build();
            }

            List<User> validUsers = userRepository.findByUsernameInAndIsActiveTrue(extractedUsernames);
            if (validUsers == null) {
                validUsers = Collections.emptyList();
            }

            List<String> validUsernames = validUsers.stream()
                    .filter(Objects::nonNull)
                    .map(User::getActualUsername)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<String> invalidUsernames = extractedUsernames.stream()
                    .filter(username -> !validUsernames.contains(username))
                    .collect(Collectors.toList());

            // Check for inactive users
            List<User> allUsers = userRepository.findByUsernameIn(extractedUsernames);
            if (allUsers == null) {
                allUsers = Collections.emptyList();
            }

            List<String> allValidUsernames = allUsers.stream()
                    .filter(Objects::nonNull)
                    .map(User::getActualUsername)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<String> inactiveUsernames = allValidUsernames.stream()
                    .filter(username -> !validUsernames.contains(username))
                    .collect(Collectors.toList());

            // Check for duplicates
            List<String> duplicateUsernames = extractedUsernames.stream()
                    .filter(username -> Collections.frequency(extractedUsernames, username) > 1)
                    .distinct()
                    .collect(Collectors.toList());

            List<String> warnings = new ArrayList<>();
            if (extractedUsernames.size() > Constant.MAX_TAGS_PER_POST) {
                warnings.add("Tagging more than " + Constant.MAX_TAGS_PER_POST + " users may result in notification spam");
            }
            if (!inactiveUsernames.isEmpty()) {
                warnings.add("Some tagged users are inactive: " + String.join(", ", inactiveUsernames));
            }
            if (!duplicateUsernames.isEmpty()) {
                warnings.add("Duplicate tags found: " + String.join(", ", duplicateUsernames));
            }

            String message = "";
            if (invalidUsernames.isEmpty() && validUsernames.size() > 0) {
                message = "All " + validUsernames.size() + " user tags are valid";
            } else if (invalidUsernames.size() > 0 && validUsernames.size() > 0) {
                message = validUsernames.size() + " valid tags, " + invalidUsernames.size() + " invalid tags";
            } else if (invalidUsernames.size() > 0) {
                message = "All " + invalidUsernames.size() + " user tags are invalid";
            }

            return UserTagValidationResult.builder()
                    .isValid(invalidUsernames.isEmpty())
                    .validUsernames(validUsernames)
                    .invalidUsernames(invalidUsernames)
                    .warnings(warnings)
                    .totalTaggedUsers(validUsernames.size())
                    .message(message)
                    .inactiveUsernames(inactiveUsernames)
                    .duplicateUsernames(duplicateUsernames)
                    .maxTagsAllowed(Constant.MAX_TAGS_PER_POST)
                    .exceedsMaxTags(extractedUsernames.size() > Constant.MAX_TAGS_PER_POST)
                    .build();
        } catch (Exception e) {
            log.error("Failed to validate user tags", e);
            throw new ServiceException("Failed to validate user tags: " + e.getMessage(), e);
        }
    }

    /**
     * Get comprehensive tagging analytics for a user with geographic context
     */
    public Map<String, Object> getComprehensiveTaggingAnalytics(User user) {
        try {
            PostUtility.validateUser(user);

            Map<String, Object> analytics = new HashMap<>();

            // Basic tagging statistics (already geographic-aware)
            Map<String, Long> basicStats = getTaggingStatistics(user);
            analytics.putAll(basicStats);

            // Post status breakdown for tagged posts
            PaginatedResponse<Post> allTaggedPosts = getPostsVisibleToUser(user, null, null, 1000);
            Map<String, Object> statusBreakdown = new HashMap<>();

            for (PostStatus status : PostStatus.values()) {
                long count = allTaggedPosts.getData().stream()
                        .filter(post -> post != null && post.getStatus() == status)
                        .count();

                Map<String, Object> statusInfo = new HashMap<>();
                statusInfo.put("count", count);
                statusInfo.put("displayName", status.getDisplayName());
                statusInfo.put("allowsUpdates", status.allowsUpdates());
                statusInfo.put("isVisible", status.isVisible());

                statusBreakdown.put(status.name(), statusInfo);
            }
            analytics.put("statusBreakdown", statusBreakdown);

            // Recent tagging activity (last 30 days)
            Date thirtyDaysAgo = new Date(System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000));
            Long recentTags = userTagRepository.countByTaggedUserAndTaggedAtAfter(user, thirtyDaysAgo);
            analytics.put("recentTags30Days", recentTags != null ? recentTags : 0L);

            // Average resolution time for resolved posts
            PaginatedResponse<Post> resolvedPosts = getResolvedPostsVisibleToUser(user, null, 1000);
            OptionalDouble avgResolutionTime = resolvedPosts.getData().stream()
                    .filter(post -> post != null &&
                            post.getResolvedAt() != null &&
                            post.getCreatedAt() != null)
                    .mapToLong(post -> post.getResolvedAt().getTime() - post.getCreatedAt().getTime())
                    .average();

            if (avgResolutionTime.isPresent()) {
                long avgHours = (long) avgResolutionTime.getAsDouble() / (1000 * 60 * 60);
                analytics.put("averageResolutionTimeHours", avgHours);
            }

            return analytics;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get comprehensive tagging analytics for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get comprehensive tagging analytics: " + e.getMessage(), e);
        }
    }

    // ===== Helper Methods =====

    private Long getUserTagCount(User user) {
        try {
            if (user == null) {
                return 0L;
            }
            Long count = userTagRepository.countByTaggedUser(user);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("Failed to get tag count for user: {}", user.getActualUsername(), e);
            return 0L;
        }
    }

    private double calculateUserResolutionRate(User user) {
        try {
            Long totalTagged = userTagRepository.countByTaggedUser(user);
            if (totalTagged == null || totalTagged == 0) {
                return 0.0;
            }

            Long resolved = userTagRepository.countByTaggedUserAndPostStatus(user, PostStatus.RESOLVED);
            if (resolved == null) {
                resolved = 0L;
            }

            return PostUtility.calculateUserResolutionRate(user, totalTagged, resolved);
        } catch (Exception e) {
            log.warn("Failed to calculate resolution rate for user: {}", user.getActualUsername(), e);
            return 0.0;
        }
    }
}