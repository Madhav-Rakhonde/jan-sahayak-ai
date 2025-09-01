package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.UserTagSuggestionDto;
import com.JanSahayak.AI.DTO.UserTagValidationResult;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.*;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.UserTag;
import com.JanSahayak.AI.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public void processUserTags(Post post) {
        try {
            validatePost(post);
            log.info("Processing user tags for post ID: {} (status: {})", post.getId(), post.getStatus().getDisplayName());

            List<String> extractedUsernames = extractUserTags(post.getContent());

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

                // Create new UserTag entities
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
            validatePost(post);
            validateUser(user);

            // Check if post allows tag updates based on status
            if (!post.getStatus().allowsUpdates()) {
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
            validatePost(post);
            validateUser(user);

            // Check if post allows tag updates based on status
            if (!post.getStatus().allowsUpdates()) {
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
            validatePost(post);
            if (newContent == null) {
                throw new ValidationException("New content cannot be null");
            }

            // Check if post allows tag updates based on status
            if (!post.getStatus().allowsUpdates()) {
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
            List<String> newUsernames = extractUserTags(newContent);

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

            // Create new tags
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

    public List<Post> getPostsVisibleToUser(User user, Boolean isResolved) {
        try {
            validateUser(user);

            List<Post> posts;
            if (isResolved != null) {
                PostStatus status = isResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                posts = userTagRepository.findPostsWhereUserIsTaggedByStatus(user, status);
            } else {
                posts = userTagRepository.findPostsWhereUserIsTagged(user);
            }

            if (posts == null) {
                return Collections.emptyList();
            }

            // Filter only visible posts
            return posts.stream()
                    .filter(post -> post != null && post.getStatus() != null && post.getStatus().isVisible())
                    .collect(Collectors.toList());
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get posts visible to user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get posts visible to user: " + e.getMessage(), e);
        }
    }

    public List<Post> getActivePostsVisibleToUser(User user) {
        try {
            validateUser(user);
            List<Post> activePosts = userTagRepository.findPostsWhereUserIsTaggedByStatus(user, PostStatus.ACTIVE);
            if (activePosts == null) {
                return Collections.emptyList();
            }

            // Double-check visibility and active status
            return activePosts.stream()
                    .filter(post -> post != null &&
                            post.getStatus() == PostStatus.ACTIVE &&
                            post.getStatus().isVisible())
                    .collect(Collectors.toList());
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get active posts visible to user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get active posts visible to user: " + e.getMessage(), e);
        }
    }

    public List<Post> getResolvedPostsVisibleToUser(User user) {
        try {
            validateUser(user);
            List<Post> resolvedPosts = userTagRepository.findPostsWhereUserIsTaggedByStatus(user, PostStatus.RESOLVED);
            if (resolvedPosts == null) {
                return Collections.emptyList();
            }

            // Double-check resolved status and visibility
            return resolvedPosts.stream()
                    .filter(post -> post != null &&
                            post.getStatus() == PostStatus.RESOLVED &&
                            post.getStatus().isVisible())
                    .collect(Collectors.toList());
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get resolved posts visible to user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get resolved posts visible to user: " + e.getMessage(), e);
        }
    }

    public List<User> getTaggedUsersInPost(Post post) {
        try {
            validatePost(post);

            List<UserTag> activeTags = userTagRepository.findByPostAndIsActiveTrue(post);
            if (activeTags == null) {
                return Collections.emptyList();
            }

            List<User> taggedUsers = activeTags.stream()
                    .filter(Objects::nonNull)
                    .map(UserTag::getTaggedUser)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            log.debug("Found {} tagged users in post ID: {} (status: {})",
                    taggedUsers.size(), post.getId(), post.getStatus().getDisplayName());

            return taggedUsers;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get tagged users in post: {}", post.getId(), e);
            throw new ServiceException("Failed to get tagged users in post: " + e.getMessage(), e);
        }
    }

    public boolean isUserTaggedInPost(Post post, User user) {
        try {
            validatePost(post);
            validateUser(user);

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

    public Map<String, Long> getTaggingStatistics(User user) {
        try {
            validateUser(user);

            List<Post> taggedPosts = getPostsVisibleToUser(user, null);
            List<Post> activePosts = getActivePostsVisibleToUser(user);
            List<Post> resolvedPosts = getResolvedPostsVisibleToUser(user);

            Map<String, Long> result = new HashMap<>();
            result.put("totalTaggedPosts", (long) taggedPosts.size());
            result.put("activeTaggedPosts", (long) activePosts.size());
            result.put("resolvedTaggedPosts", (long) resolvedPosts.size());

            // Calculate resolution rate
            double resolutionRate = taggedPosts.isEmpty() ? 0.0 :
                    (double) resolvedPosts.size() / taggedPosts.size() * 100;
            result.put("resolutionRate", Math.round(resolutionRate));

            // Additional statistics by post status
            Map<PostStatus, Long> statusCounts = taggedPosts.stream()
                    .filter(Objects::nonNull)
                    .filter(post -> post.getStatus() != null)
                    .collect(Collectors.groupingBy(Post::getStatus, Collectors.counting()));

            for (PostStatus status : PostStatus.values()) {
                result.put("posts" + status.name(), statusCounts.getOrDefault(status, 0L));
            }

            log.debug("Generated tagging statistics for user: {} - {} total tagged posts",
                    user.getActualUsername(), taggedPosts.size());

            return result;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get tagging statistics for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get tagging statistics: " + e.getMessage(), e);
        }
    }

    public List<String> extractUserTags(String content) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> usernames = new ArrayList<>();
        Matcher matcher = Constant.USERNAME_TAG_PATTERN.matcher(content);

        while (matcher.find()) {
            String username = matcher.group(1);
            if (!usernames.contains(username) && isValidUsername(username)) {
                usernames.add(username);
            }
        }

        log.debug("Extracted {} user tags from content", usernames.size());
        return usernames;
    }

    public List<UserTagSuggestionDto> getUserTagSuggestions(String query) {
        try {
            if (query == null || query.trim().length() < 2) {
                return Collections.emptyList();
            }

            // Remove @ if present
            String cleanQuery = query.startsWith("@") ? query.substring(1) : query;

            if (cleanQuery.trim().length() < 2) {
                return Collections.emptyList();
            }

            List<User> users = userRepository.searchUsersForTagging(cleanQuery.trim());
            if (users == null) {
                return Collections.emptyList();
            }

            return users.stream()
                    .filter(Objects::nonNull)
                    .limit(20) // Limit suggestions to prevent performance issues
                    .map(user -> UserTagSuggestionDto.builder()
                            .id(user.getId())
                            .username(user.getActualUsername())
                            .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getActualUsername())
                            .location(user.getLocation())
                            .profileImage(user.getProfileImage())
                            .isActive(user.getIsActive())
                            .role(user.getRole() != null ? user.getRole().getName() : null)
                            .bio(truncateText(user.getBio(), 100))
                            .taggableName("@" + user.getActualUsername())
                            .hasLocation(user.hasLocation())
                            .totalTaggedPosts(getUserTagCount(user))
                            .resolutionRate(calculateUserResolutionRate(user))
                            .build())
                    .collect(Collectors.toList());
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

            List<String> extractedUsernames = extractUserTags(content);

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
     * Get comprehensive tagging analytics for a user
     */
    public Map<String, Object> getComprehensiveTaggingAnalytics(User user) {
        try {
            validateUser(user);

            Map<String, Object> analytics = new HashMap<>();

            // Basic tagging statistics
            Map<String, Long> basicStats = getTaggingStatistics(user);
            analytics.putAll(basicStats);

            // Post status breakdown for tagged posts
            List<Post> allTaggedPosts = getPostsVisibleToUser(user, null);
            Map<String, Object> statusBreakdown = new HashMap<>();

            for (PostStatus status : PostStatus.values()) {
                long count = allTaggedPosts.stream()
                        .filter(post -> post != null && post.getStatus() == status)
                        .count();

                Map<String, Object> statusInfo = new HashMap<>();
                statusInfo.put("count", count);
                statusInfo.put("displayName", status.getDisplayName());
                statusInfo.put("icon", status.getIcon());
                statusInfo.put("description", status.getDescription());
                statusInfo.put("canBeResolvedByUser", status.canBeResolvedByUsers());
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
            List<Post> resolvedPosts = getResolvedPostsVisibleToUser(user);
            OptionalDouble avgResolutionTime = resolvedPosts.stream()
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

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        // Username validation: alphanumeric and underscore only, 3-50 characters
        return username.matches("^[a-zA-Z0-9_]{3,50}$");
    }

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

            return Math.round((double) resolved / totalTagged * 100.0 * 100.0) / 100.0;
        } catch (Exception e) {
            log.warn("Failed to calculate resolution rate for user: {}", user.getActualUsername(), e);
            return 0.0;
        }
    }

    // ===== Validation Methods =====

    private void validateUser(User user) {
        if (user == null) {
            throw new ValidationException("User cannot be null");
        }
        if (user.getId() == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (user.getActualUsername() == null || user.getActualUsername().trim().isEmpty()) {
            throw new ValidationException("Username cannot be null or empty");
        }
    }

    private void validatePost(Post post) {
        if (post == null) {
            throw new ValidationException("Post cannot be null");
        }
        if (post.getId() == null) {
            throw new ValidationException("Post ID cannot be null");
        }
        if (post.getContent() == null) {
            throw new ValidationException("Post content cannot be null");
        }
        if (post.getStatus() == null) {
            throw new ValidationException("Post status cannot be null");
        }
    }
}