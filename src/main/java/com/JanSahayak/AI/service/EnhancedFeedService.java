package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.PostLikeRepo;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.PostViewRepo;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.config.Constant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedFeedService {

    private final PostRepo postRepository;
    private final UserRepo userRepository;
    private final LocationService locationService;
    private final UserTaggingService userTaggingService;
    private final PostLikeRepo postLikeRepository;
    private final PostViewRepo postViewRepository;




    /**
     * Universal Feed based on user role
     * - Normal Users: Posts by departments + active posts (location-based)
     * - Department Users: Tagged posts + location posts
     * - Admin Users: All posts
     */
    public List<PostResponse> getUniversalFeed(User user, Boolean includeResolved, Integer limit) {
        try {
            if (user == null || user.getRole() == null) {
                throw new IllegalArgumentException("User and user role cannot be null");
            }

            int validatedLimit = validateAndSanitizeLimit(limit);
            String userRole = user.getRole().getName();

            switch (userRole) {
                case Constant.ROLE_USER:
                    return getNormalUserFeed(user, includeResolved, validatedLimit);
                case Constant.ROLE_DEPARTMENT:
                    return getDepartmentFeed(user, includeResolved, validatedLimit);
                case Constant.ROLE_ADMIN:
                    return getAdminFeed(user, includeResolved, validatedLimit);
                default:
                    log.warn("Unknown user role: {} for user: {}", userRole, user.getUsername());
                    return getNormalUserFeed(user, includeResolved, validatedLimit);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get universal feed for user: {}",
                    user != null ? user.getUsername() : "null", e);
            throw new ServiceException("Failed to get universal feed: " + e.getMessage(), e);
        }
    }

    /**
     * Normal User Feed - Posts by departments + active posts based on location proximity
     */
    public List<PostResponse> getNormalUserFeed(User user, Boolean includeResolved, Integer limit) {
        try {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null");
            }
            if (user.getLocation() == null || user.getLocation().trim().isEmpty()) {
                throw new IllegalArgumentException("User location cannot be null or empty");
            }

            int validatedLimit = validateAndSanitizeLimit(limit);
            List<Post> feedPosts = new ArrayList<>();
            String userLocation = user.getLocation();

            // Priority 1: Department posts from same location
            List<Post> departmentPosts = getDepartmentPostsByLocation(userLocation, includeResolved);
            feedPosts.addAll(departmentPosts);

            // Priority 2: Active posts from same location (not by departments)
            if (!Boolean.TRUE.equals(includeResolved)) {
                List<Post> activePosts = getActivePostsByLocation(userLocation);
                feedPosts.addAll(activePosts);
            }

            // Priority 3: Posts from same state
            if (feedPosts.size() < validatedLimit) {
                String stateCode = locationService.extractStateCodeFromLocation(userLocation);
                if (stateCode != null) {
                    List<String> stateLocations = locationService.getLocationsByState(stateCode);
                    stateLocations.remove(userLocation);

                    if (!stateLocations.isEmpty()) {
                        List<Post> stateDeptPosts = getDepartmentPostsByLocationsBatch(stateLocations, includeResolved);
                        feedPosts.addAll(stateDeptPosts);

                        if (feedPosts.size() < validatedLimit && !Boolean.TRUE.equals(includeResolved)) {
                            List<Post> stateActivePosts = getActivePostsByLocationsBatch(stateLocations);
                            feedPosts.addAll(stateActivePosts);
                        }
                    }
                }
            }

            return feedPosts.stream()
                    .filter(post -> post != null && post.getStatus() != null && post.getStatus().isInUserFeed())
                    .distinct()
                    .sorted((p1, p2) -> {
                        if (p1 == null || p2 == null) return 0;
                        if (p1.getCreatedAt() == null || p2.getCreatedAt() == null) return 0;

                        // Department posts first, then by creation date (newest first)
                        boolean p1IsDept = isDepartmentPost(p1);
                        boolean p2IsDept = isDepartmentPost(p2);

                        if (p1IsDept && !p2IsDept) return -1;
                        if (!p1IsDept && p2IsDept) return 1;

                        return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                    })
                    .limit(validatedLimit)
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList());

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get normal user feed for user: {}",
                    user != null ? user.getUsername() : "null", e);
            throw new ServiceException("Failed to get normal user feed: " + e.getMessage(), e);
        }
    }

    /**
     * Department Feed - Tagged posts (highest priority) + location-based posts
     */
    public List<PostResponse> getDepartmentFeed(User user, Boolean includeResolved, Integer limit) {
        try {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null");
            }

            int validatedLimit = validateAndSanitizeLimit(limit);
            List<Post> feedPosts = new ArrayList<>();

            // Priority 1: Posts where department is tagged
            List<Post> taggedPosts = getDepartmentTaggedPostsList(user, includeResolved, validatedLimit / 2);
            feedPosts.addAll(taggedPosts);

            // Priority 2: Posts from same location
            List<Post> locationPosts = getDepartmentLocationPostsList(user, includeResolved, validatedLimit / 3);
            feedPosts.addAll(locationPosts);

            // Priority 3: Posts from same state
            if (feedPosts.size() < validatedLimit) {
                List<Post> statePosts = getDepartmentStatePostsList(user, includeResolved, validatedLimit / 4);
                feedPosts.addAll(statePosts);
            }

            // Priority 4: Posts from other states
            if (feedPosts.size() < validatedLimit) {
                List<Post> countryPosts = getDepartmentCountryPostsList(user, includeResolved, validatedLimit - feedPosts.size());
                feedPosts.addAll(countryPosts);
            }

            return feedPosts.stream()
                    .filter(post -> post != null && post.getStatus() != null && post.getStatus().isInUserFeed())
                    .distinct()
                    .sorted((p1, p2) -> {
                        if (p1 == null || p2 == null) return 0;
                        if (p1.getCreatedAt() == null || p2.getCreatedAt() == null) return 0;

                        // Tagged posts first, then by creation date
                        boolean p1IsTagged = isUserTaggedInPost(p1, user);
                        boolean p2IsTagged = isUserTaggedInPost(p2, user);

                        if (p1IsTagged && !p2IsTagged) return -1;
                        if (!p1IsTagged && p2IsTagged) return 1;

                        return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                    })
                    .limit(validatedLimit)
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList());

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get department feed for user: {}",
                    user != null ? user.getUsername() : "null", e);
            throw new ServiceException("Failed to get department feed: " + e.getMessage(), e);
        }
    }

    /**
     * Get posts where department user is tagged
     */
    public List<PostResponse> getDepartmentTaggedPosts(User user, Boolean includeResolved, Integer limit) {
        try {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null");
            }

            int validatedLimit = validateAndSanitizeLimit(limit);
            List<Post> taggedPosts = getDepartmentTaggedPostsList(user, includeResolved, validatedLimit);

            return taggedPosts.stream()
                    .filter(post -> post != null && post.getStatus() != null && post.getStatus().isInUserFeed())
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList());

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get tagged posts for department user: {}",
                    user != null ? user.getUsername() : "null", e);
            throw new ServiceException("Failed to get tagged posts: " + e.getMessage(), e);
        }
    }

    /**
     * Get resolved and active posts for department management
     */
    public Map<String, Object> getDepartmentResolutionPosts(User user, Integer limit) {
        try {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null");
            }

            int validatedLimit = validateAndSanitizeLimit(limit);
            Map<String, Object> result = new HashMap<>();

            // Get tagged posts that are active
            List<Post> activeTaggedPosts = getDepartmentTaggedPostsList(user, false, validatedLimit)
                    .stream()
                    .filter(post -> post != null && post.getStatus() == PostStatus.ACTIVE)
                    .collect(Collectors.toList());

            // Get tagged posts that are resolved
            List<Post> resolvedTaggedPosts = getDepartmentTaggedPostsList(user, true, validatedLimit)
                    .stream()
                    .filter(post -> post != null && post.getStatus() == PostStatus.RESOLVED)
                    .collect(Collectors.toList());

            result.put("activePosts", activeTaggedPosts.stream()
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList()));

            result.put("resolvedPosts", resolvedTaggedPosts.stream()
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList()));

            result.put("activeCount", activeTaggedPosts.size());
            result.put("resolvedCount", resolvedTaggedPosts.size());
            result.put("totalTaggedPosts", activeTaggedPosts.size() + resolvedTaggedPosts.size());

            // Calculate resolution rate
            int totalTagged = activeTaggedPosts.size() + resolvedTaggedPosts.size();
            double resolutionRate = totalTagged > 0 ? (double) resolvedTaggedPosts.size() / totalTagged * 100 : 0;
            result.put("resolutionRate", Math.round(resolutionRate * 100.0) / 100.0);

            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get resolution posts for department user: {}",
                    user != null ? user.getUsername() : "null", e);
            throw new ServiceException("Failed to get resolution posts: " + e.getMessage(), e);
        }
    }

    /**
     * Department Dashboard - Comprehensive overview
     */
    public Map<String, Object> getDepartmentDashboard(User user, Boolean includeResolved) {
        try {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null");
            }

            Map<String, Object> dashboard = new HashMap<>();

            List<Post> taggedPosts = getDepartmentTaggedPostsList(user, includeResolved, 50)
                    .stream()
                    .filter(post -> post != null && post.getStatus() != null && post.getStatus().isInUserFeed())
                    .collect(Collectors.toList());

            List<Post> locationPosts = getDepartmentLocationPostsList(user, includeResolved, 30)
                    .stream()
                    .filter(post -> post != null && post.getStatus() != null && post.getStatus().isInUserFeed())
                    .collect(Collectors.toList());

            long statePostsCount = countDepartmentStatePosts(user, includeResolved);
            long countryPostsCount = countDepartmentCountryPosts(user, includeResolved);

            dashboard.put("taggedPosts", taggedPosts.stream()
                    .limit(10)
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList()));
            dashboard.put("taggedPostsCount", taggedPosts.size());

            dashboard.put("locationPosts", locationPosts.stream()
                    .limit(10)
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList()));
            dashboard.put("locationPostsCount", locationPosts.size());

            dashboard.put("statePostsCount", statePostsCount);
            dashboard.put("countryPostsCount", countryPostsCount);

            dashboard.put("userLocation", user.getLocation());
            dashboard.put("userRole", user.getRole() != null ? user.getRole().getName() : null);
            dashboard.put("includeResolved", includeResolved);

            return dashboard;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get department dashboard for user: {}",
                    user != null ? user.getUsername() : "null", e);
            throw new ServiceException("Failed to get department dashboard: " + e.getMessage(), e);
        }
    }

    /**
     * Department Statistics
     */
    public Map<String, Object> getDepartmentStatistics(User user) {
        try {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null");
            }

            Map<String, Object> stats = new HashMap<>();

            List<Post> allTaggedPosts = getDepartmentTaggedPostsList(user, null, 1000)
                    .stream()
                    .filter(post -> post != null && post.getStatus() != null && post.getStatus().isInUserFeed())
                    .collect(Collectors.toList());

            List<Post> resolvedTaggedPosts = allTaggedPosts.stream()
                    .filter(post -> post.getStatus() == PostStatus.RESOLVED)
                    .collect(Collectors.toList());

            List<Post> activeTaggedPosts = allTaggedPosts.stream()
                    .filter(post -> post.getStatus() == PostStatus.ACTIVE)
                    .collect(Collectors.toList());

            stats.put("totalTaggedPosts", allTaggedPosts.size());
            stats.put("resolvedTaggedPosts", resolvedTaggedPosts.size());
            stats.put("activeTaggedPosts", activeTaggedPosts.size());

            double resolutionRate = allTaggedPosts.size() > 0 ?
                    (double) resolvedTaggedPosts.size() / allTaggedPosts.size() * 100 : 0;
            stats.put("resolutionRate", Math.round(resolutionRate * 100.0) / 100.0);

            stats.put("totalLocationPosts", countDepartmentLocationPosts(user, null));
            stats.put("totalStatePosts", countDepartmentStatePosts(user, null));

            stats.put("userLocation", user.getLocation());
            stats.put("feedType", "department");

            return stats;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get department statistics for user: {}",
                    user != null ? user.getUsername() : "null", e);
            throw new ServiceException("Failed to get department statistics: " + e.getMessage(), e);
        }
    }

    /**
     * Get location posts for departments
     */
    public List<PostResponse> getDepartmentLocationPosts(User user, Boolean includeResolved, Integer limit) {
        try {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null");
            }

            int validatedLimit = validateAndSanitizeLimit(limit);
            List<Post> locationPosts = getDepartmentLocationPostsList(user, includeResolved, validatedLimit);

            return locationPosts.stream()
                    .filter(post -> post != null && post.getStatus() != null && post.getStatus().isInUserFeed())
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get location posts for department user: {}",
                    user != null ? user.getUsername() : "null", e);
            throw new ServiceException("Failed to get location posts: " + e.getMessage(), e);
        }
    }

    /**
     * Get state posts for departments
     */
    public List<PostResponse> getDepartmentStatePosts(User user, Boolean includeResolved, Integer limit) {
        try {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null");
            }

            int validatedLimit = validateAndSanitizeLimit(limit);
            List<Post> statePosts = getDepartmentStatePostsList(user, includeResolved, validatedLimit);

            return statePosts.stream()
                    .filter(post -> post != null && post.getStatus() != null && post.getStatus().isInUserFeed())
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get state posts for department user: {}",
                    user != null ? user.getUsername() : "null", e);
            throw new ServiceException("Failed to get state posts: " + e.getMessage(), e);
        }
    }

    /**
     * Get country posts for departments (excluding user's state)
     */
    public List<PostResponse> getDepartmentCountryPosts(User user, Boolean includeResolved, Integer limit) {
        try {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null");
            }

            int validatedLimit = validateAndSanitizeLimit(limit);
            List<Post> countryPosts = getDepartmentCountryPostsList(user, includeResolved, validatedLimit);

            return countryPosts.stream()
                    .filter(post -> post != null && post.getStatus() != null && post.getStatus().isInUserFeed())
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get country posts for department user: {}",
                    user != null ? user.getUsername() : "null", e);
            throw new ServiceException("Failed to get country posts: " + e.getMessage(), e);
        }
    }

    /**
     * Admin Feed - All posts in the system
     */
    public List<PostResponse> getAdminFeed(User user, Boolean includeResolved, Integer limit) {
        try {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null");
            }

            int validatedLimit = validateAndSanitizeLimit(limit);
            List<Post> allPosts;

            if (includeResolved != null) {
                PostStatus status = includeResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                Pageable pageable = PageRequest.of(0, validatedLimit);
                allPosts = postRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
            } else {
                Pageable pageable = PageRequest.of(0, validatedLimit);
                allPosts = postRepository.findAllByOrderByCreatedAtDesc(pageable);
            }

            return allPosts.stream()
                    .filter(post -> post != null)
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList());

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get admin feed for user: {}",
                    user != null ? user.getUsername() : "null", e);
            throw new ServiceException("Failed to get admin feed: " + e.getMessage(), e);
        }
    }

    /**
     * Location-based feed for any user
     */
    public List<PostResponse> getLocationBasedFeed(User user, String location, Double maxDistanceKm,
                                                   Boolean includeResolved, Integer limit) {
        try {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null");
            }
            if (location == null || location.trim().isEmpty()) {
                throw new IllegalArgumentException("Location cannot be null or empty");
            }
            if (maxDistanceKm == null || maxDistanceKm < 0) {
                throw new IllegalArgumentException("Max distance must be non-negative");
            }

            int validatedLimit = validateAndSanitizeLimit(limit);
            List<Post> locationPosts = new ArrayList<>();

            // Same location posts (distance = 0)
            if (maxDistanceKm >= 0.0) {
                List<Post> exactLocationPosts = getPostsByLocationAndStatus(location, includeResolved);
                locationPosts.addAll(exactLocationPosts);
            }

            // Same state posts (distance = 25)
            if (maxDistanceKm >= Constant.STATE_DISTANCE_KM) {
                String stateCode = locationService.extractStateCodeFromLocation(location);
                if (stateCode != null) {
                    List<String> stateLocations = locationService.getLocationsByState(stateCode);
                    stateLocations.remove(location);

                    if (!stateLocations.isEmpty()) {
                        List<Post> statePosts = getPostsByLocationsBatchAndStatus(stateLocations, includeResolved);
                        locationPosts.addAll(statePosts);
                    }
                }
            }

            // Country-wide posts (distance = 200)
            if (maxDistanceKm >= Constant.COUNTRY_DISTANCE_KM) {
                String userState = locationService.extractStateCodeFromLocation(location);
                List<Post> countryPosts;

                if (includeResolved != null) {
                    PostStatus status = includeResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                    countryPosts = postRepository.findByStatusOrderByCreatedAtDesc(status);
                } else {
                    countryPosts = postRepository.findAll();
                }

                countryPosts = countryPosts.stream()
                        .filter(post -> {
                            if (post == null || post.getLocation() == null) return false;
                            String postState = locationService.extractStateCodeFromLocation(post.getLocation());
                            return !Objects.equals(userState, postState);
                        })
                        .collect(Collectors.toList());

                locationPosts.addAll(countryPosts);
            }

            return locationPosts.stream()
                    .filter(post -> post != null && post.getStatus() != null && post.getStatus().isInUserFeed())
                    .distinct()
                    .sorted((p1, p2) -> {
                        if (p1 == null || p2 == null || p1.getCreatedAt() == null || p2.getCreatedAt() == null) {
                            return 0;
                        }
                        return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                    })
                    .limit(validatedLimit)
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList());

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get location-based feed for location: {}", location, e);
            throw new ServiceException("Failed to get location-based feed: " + e.getMessage(), e);
        }
    }

    /**
     * Get feed statistics for current user
     */
    public Map<String, Object> getFeedStatistics(User user) {
        try {
            if (user == null || user.getRole() == null) {
                throw new IllegalArgumentException("User and user role cannot be null");
            }

            Map<String, Object> stats = new HashMap<>();
            String userRole = user.getRole().getName();

            switch (userRole) {
                case Constant.ROLE_USER:
                    stats.put("feedType", "normal_user");
                    stats.put("totalPosts", countNormalUserFeedPosts(user));
                    break;
                case Constant.ROLE_DEPARTMENT:
                    stats.put("feedType", "department");
                    stats.put("totalPosts", countDepartmentFeedPosts(user));
                    stats.put("taggedPostsCount", countDepartmentTaggedPosts(user, null));
                    stats.put("sameLocationPosts", countDepartmentLocationPosts(user, null));
                    break;
                case Constant.ROLE_ADMIN:
                    stats.put("feedType", "admin");
                    stats.put("totalPosts", postRepository.count());
                    stats.put("activePosts", postRepository.countByStatus(PostStatus.ACTIVE));
                    stats.put("resolvedPosts", postRepository.countByStatus(PostStatus.RESOLVED));
                    break;
            }

            stats.put("userLocation", user.getLocation());
            stats.put("userRole", userRole);

            return stats;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get feed statistics for user: {}",
                    user != null ? user.getUsername() : "null", e);
            throw new ServiceException("Failed to get feed statistics: " + e.getMessage(), e);
        }
    }

    /**
     * Convert Post entity to PostResponse DTO
     */
    public PostResponse convertToPostResponse(Post post, User currentUser) {
        try {
            if (post == null) {
                throw new IllegalArgumentException("Post cannot be null");
            }
            if (currentUser == null) {
                throw new IllegalArgumentException("Current user cannot be null");
            }

            return PostResponse.builder()
                    .id(post.getId())
                    .content(post.getContent())
                    .location(post.getLocation())
                    .status(post.getStatus())
                    .createdAt(post.getCreatedAt())
                    .imageName(post.getImageName())
                    .isResolved(post.getStatus() == PostStatus.RESOLVED)
                    .resolvedAt(post.getResolvedAt())
                    .updatedAt(post.getUpdatedAt())
                    .userId(post.getUser() != null ? post.getUser().getId() : null)
                    .username(post.getUser() != null ? post.getUser().getUsername() : null)
                    .likeCount(post.getLikeCount())
                    .commentCount(post.getCommentCount())
                    .viewCount(post.getViewCount())
                    .hasImage(post.hasImage())
                    .hasLocation(post.hasLocation())
                    .isLikedByCurrentUser(isPostLikedByUser(post, currentUser))
                    .isViewedByCurrentUser(isPostViewedByUser(post, currentUser))
                    .statusDisplayName(post.getStatus() != null ? post.getStatus().getDisplayName() : "Unknown")
                    .statusDescription(post.getStatus() != null ? post.getStatus().getDescription() : "")
                    .statusIcon(post.getStatus() != null ? post.getStatus().getIcon() : "")
                    .canBeResolved(post.getStatus() != null ? post.getStatus().canBeResolvedByUsers() : false)
                    .allowsUpdates(post.getStatus() != null ? post.getStatus().allowsUpdates() : false)
                    .isInteractable(post.getStatus() != null ? post.getStatus().isInteractable() : false)
                    .build();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to convert post to response: {}", post != null ? post.getId() : "null", e);
            throw new ServiceException("Failed to convert post to response: " + e.getMessage(), e);
        }
    }

    // ===== Helper Methods =====

    private int validateAndSanitizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return Constant.DEFAULT_FEED_LIMIT;
        }
        return Math.min(limit, Constant.MAX_FEED_LIMIT);
    }

    private List<Post> getDepartmentPostsByLocationsBatch(List<String> locations, Boolean includeResolved) {
        try {
            if (locations == null || locations.isEmpty()) {
                return Collections.emptyList();
            }

            List<User> departmentUsers = userRepository.findByRoleNameAndLocationInAndIsActiveTrue(Constant.ROLE_DEPARTMENT, locations);

            if (departmentUsers.isEmpty()) {
                return Collections.emptyList();
            }

            if (includeResolved != null) {
                PostStatus status = includeResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                return postRepository.findByUserInAndStatusOrderByCreatedAtDesc(departmentUsers, status);
            } else {
                return postRepository.findByUserInOrderByCreatedAtDesc(departmentUsers);
            }
        } catch (Exception e) {
            log.error("Failed to get department posts by locations batch: {}", locations, e);
            return Collections.emptyList();
        }
    }

    private List<Post> getActivePostsByLocationsBatch(List<String> locations) {
        try {
            if (locations == null || locations.isEmpty()) {
                return Collections.emptyList();
            }

            List<User> departmentUsers = userRepository.findByRoleNameAndLocationInAndIsActiveTrue(Constant.ROLE_DEPARTMENT, locations);
            List<Post> allActivePosts = postRepository.findByLocationInAndStatus(locations, PostStatus.ACTIVE);

            return allActivePosts.stream()
                    .filter(post -> post != null && !departmentUsers.contains(post.getUser()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get active posts by locations batch: {}", locations, e);
            return Collections.emptyList();
        }
    }

    private List<Post> getPostsByLocationsBatchAndStatus(List<String> locations, Boolean includeResolved) {
        try {
            if (locations == null || locations.isEmpty()) {
                return Collections.emptyList();
            }

            if (includeResolved != null) {
                PostStatus status = includeResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                return postRepository.findByLocationInAndStatus(locations, status);
            } else {
                return postRepository.findByLocationInOrderByCreatedAtDesc(locations);
            }
        } catch (Exception e) {
            log.error("Failed to get posts by locations batch and status: {}", locations, e);
            return Collections.emptyList();
        }
    }

    private List<Post> getDepartmentPostsByLocation(String location, Boolean includeResolved) {
        try {
            if (location == null || location.trim().isEmpty()) {
                return Collections.emptyList();
            }

            List<User> departmentUsers = userRepository.findByRoleNameAndLocationAndIsActiveTrue(Constant.ROLE_DEPARTMENT, location);

            if (departmentUsers.isEmpty()) {
                return Collections.emptyList();
            }

            if (includeResolved != null) {
                PostStatus status = includeResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                return postRepository.findByUserInAndStatusOrderByCreatedAtDesc(departmentUsers, status);
            } else {
                return postRepository.findByUserInOrderByCreatedAtDesc(departmentUsers);
            }
        } catch (Exception e) {
            log.error("Failed to get department posts by location: {}", location, e);
            return Collections.emptyList();
        }
    }

    private List<Post> getActivePostsByLocation(String location) {
        try {
            if (location == null || location.trim().isEmpty()) {
                return Collections.emptyList();
            }

            List<User> departmentUsers = userRepository.findByRoleNameAndLocationAndIsActiveTrue(Constant.ROLE_DEPARTMENT, location);
            List<Post> allActivePosts = postRepository.findByLocationAndStatus(location, PostStatus.ACTIVE);

            return allActivePosts.stream()
                    .filter(post -> post != null && !departmentUsers.contains(post.getUser()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get active posts by location: {}", location, e);
            return Collections.emptyList();
        }
    }

    private List<Post> getPostsByLocationAndStatus(String location, Boolean includeResolved) {
        try {
            if (location == null || location.trim().isEmpty()) {
                return Collections.emptyList();
            }

            if (includeResolved != null) {
                PostStatus status = includeResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                return postRepository.findByLocationAndStatus(location, status);
            } else {
                return postRepository.findByLocationOrderByCreatedAtDesc(location);
            }
        } catch (Exception e) {
            log.error("Failed to get posts by location and status: {}", location, e);
            return Collections.emptyList();
        }
    }

    private List<Post> getDepartmentTaggedPostsList(User user, Boolean includeResolved, Integer limit) {
        try {
            if (user == null) {
                return Collections.emptyList();
            }

            List<Post> taggedPosts = userTaggingService.getPostsVisibleToUser(user, includeResolved);

            return taggedPosts.stream()
                    .filter(Objects::nonNull)
                    .sorted((p1, p2) -> {
                        if (p1.getCreatedAt() == null || p2.getCreatedAt() == null) return 0;
                        return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                    })
                    .limit(limit != null ? limit : Constant.DEFAULT_FEED_LIMIT)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get department tagged posts list for user: {}",
                    user != null ? user.getUsername() : "null", e);
            return Collections.emptyList();
        }
    }

    private List<Post> getDepartmentLocationPostsList(User user, Boolean includeResolved, Integer limit) {
        try {
            if (user == null || user.getLocation() == null) {
                return Collections.emptyList();
            }

            List<Post> locationPosts = getPostsByLocationAndStatus(user.getLocation(), includeResolved);

            return locationPosts.stream()
                    .filter(Objects::nonNull)
                    .sorted((p1, p2) -> {
                        if (p1.getCreatedAt() == null || p2.getCreatedAt() == null) return 0;
                        return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                    })
                    .limit(limit != null ? limit : Constant.DEFAULT_FEED_LIMIT)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get department location posts list for user: {}",
                    user != null ? user.getUsername() : "null", e);
            return Collections.emptyList();
        }
    }

    private List<Post> getDepartmentStatePostsList(User user, Boolean includeResolved, Integer limit) {
        try {
            if (user == null || user.getLocation() == null) {
                return Collections.emptyList();
            }

            String stateCode = locationService.extractStateCodeFromLocation(user.getLocation());
            if (stateCode == null) {
                return Collections.emptyList();
            }

            List<String> stateLocations = locationService.getLocationsByState(stateCode);
            stateLocations.remove(user.getLocation());

            if (stateLocations.isEmpty()) {
                return Collections.emptyList();
            }

            List<Post> statePosts = getPostsByLocationsBatchAndStatus(stateLocations, includeResolved);

            return statePosts.stream()
                    .filter(Objects::nonNull)
                    .sorted((p1, p2) -> {
                        if (p1.getCreatedAt() == null || p2.getCreatedAt() == null) return 0;
                        return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                    })
                    .limit(limit != null ? limit : 30)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get department state posts list for user: {}",
                    user != null ? user.getUsername() : "null", e);
            return Collections.emptyList();
        }
    }

    private List<Post> getDepartmentCountryPostsList(User user, Boolean includeResolved, Integer limit) {
        try {
            if (user == null || user.getLocation() == null) {
                return Collections.emptyList();
            }

            String userState = locationService.extractStateCodeFromLocation(user.getLocation());
            List<Post> countryPosts;

            if (includeResolved != null) {
                PostStatus status = includeResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                countryPosts = postRepository.findByStatusOrderByCreatedAtDesc(status);
            } else {
                countryPosts = postRepository.findAll();
            }

            return countryPosts.stream()
                    .filter(post -> {
                        if (post == null || post.getLocation() == null) return false;
                        String postState = locationService.extractStateCodeFromLocation(post.getLocation());
                        return !Objects.equals(userState, postState);
                    })
                    .sorted((p1, p2) -> {
                        if (p1.getCreatedAt() == null || p2.getCreatedAt() == null) return 0;
                        return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                    })
                    .limit(limit != null ? limit : 50)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get department country posts list for user: {}",
                    user != null ? user.getUsername() : "null", e);
            return Collections.emptyList();
        }
    }

    // Count methods for statistics
    private long countNormalUserFeedPosts(User user) {
        try {
            if (user == null || user.getLocation() == null) {
                return 0;
            }

            String userLocation = user.getLocation();
            long count = 0;

            List<User> departmentUsers = userRepository.findByRoleNameAndLocationAndIsActiveTrue(Constant.ROLE_DEPARTMENT, userLocation);
            count += postRepository.countByUserInAndStatus(departmentUsers, PostStatus.ACTIVE);
            count += postRepository.countByUserInAndStatus(departmentUsers, PostStatus.RESOLVED);

            long allActiveLocationPosts = postRepository.countByLocationAndStatus(userLocation, PostStatus.ACTIVE);
            long deptActiveLocationPosts = postRepository.countByUserInAndLocationAndStatus(departmentUsers, userLocation, PostStatus.ACTIVE);
            count += (allActiveLocationPosts - deptActiveLocationPosts);

            return count;
        } catch (Exception e) {
            log.error("Failed to count normal user feed posts for user: {}",
                    user != null ? user.getUsername() : "null", e);
            return 0;
        }
    }

    private long countDepartmentFeedPosts(User user) {
        try {
            if (user == null) {
                return 0;
            }

            long taggedCount = countDepartmentTaggedPosts(user, null);
            long locationCount = countDepartmentLocationPosts(user, null);
            long stateCount = countDepartmentStatePosts(user, null);

            return taggedCount + locationCount + stateCount;
        } catch (Exception e) {
            log.error("Failed to count department feed posts for user: {}",
                    user != null ? user.getUsername() : "null", e);
            return 0;
        }
    }

    private long countDepartmentTaggedPosts(User user, Boolean includeResolved) {
        try {
            if (user == null) {
                return 0;
            }

            List<Post> taggedPosts = userTaggingService.getPostsVisibleToUser(user, includeResolved);
            return taggedPosts.size();
        } catch (Exception e) {
            log.error("Failed to count department tagged posts for user: {}",
                    user != null ? user.getUsername() : "null", e);
            return 0;
        }
    }

    private long countDepartmentLocationPosts(User user, Boolean includeResolved) {
        try {
            if (user == null || user.getLocation() == null) {
                return 0;
            }

            if (includeResolved != null) {
                PostStatus status = includeResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                return postRepository.countByLocationAndStatus(user.getLocation(), status);
            } else {
                return postRepository.countByLocation(user.getLocation());
            }
        } catch (Exception e) {
            log.error("Failed to count department location posts for user: {}",
                    user != null ? user.getUsername() : "null", e);
            return 0;
        }
    }

    private long countDepartmentStatePosts(User user, Boolean includeResolved) {
        try {
            if (user == null || user.getLocation() == null) {
                return 0;
            }

            String stateCode = locationService.extractStateCodeFromLocation(user.getLocation());
            if (stateCode == null) {
                return 0;
            }

            List<String> stateLocations = locationService.getLocationsByState(stateCode);
            long count = 0;

            for (String location : stateLocations) {
                if (!location.equals(user.getLocation())) {
                    if (includeResolved != null) {
                        PostStatus status = includeResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                        count += postRepository.countByLocationAndStatus(location, status);
                    } else {
                        count += postRepository.countByLocation(location);
                    }
                }
            }

            return count;
        } catch (Exception e) {
            log.error("Failed to count department state posts for user: {}",
                    user != null ? user.getUsername() : "null", e);
            return 0;
        }
    }

    private long countDepartmentCountryPosts(User user, Boolean includeResolved) {
        try {
            if (user == null || user.getLocation() == null) {
                return 0;
            }

            String userState = locationService.extractStateCodeFromLocation(user.getLocation());

            if (includeResolved != null) {
                PostStatus status = includeResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                long totalCountryPosts = postRepository.countByStatus(status);
                long sameStatePosts = countDepartmentStatePosts(user, includeResolved);
                long sameLocationPosts = countDepartmentLocationPosts(user, includeResolved);

                return totalCountryPosts - sameStatePosts - sameLocationPosts;
            } else {
                long totalPosts = postRepository.count();
                long sameStatePosts = countDepartmentStatePosts(user, null);
                long sameLocationPosts = countDepartmentLocationPosts(user, null);

                return totalPosts - sameStatePosts - sameLocationPosts;
            }
        } catch (Exception e) {
            log.error("Failed to count department country posts for user: {}",
                    user != null ? user.getUsername() : "null", e);
            return 0;
        }
    }

    // Helper methods for post classification
    private boolean isDepartmentPost(Post post) {
        try {
            return post != null &&
                    post.getUser() != null &&
                    post.getUser().getRole() != null &&
                    Constant.ROLE_DEPARTMENT.equals(post.getUser().getRole().getName());
        } catch (Exception e) {
            log.warn("Error checking if post is department post: {}", post != null ? post.getId() : "null", e);
            return false;
        }
    }

    private boolean isUserTaggedInPost(Post post, User user) {
        try {
            if (post == null || user == null) {
                return false;
            }
            return userTaggingService.isUserTaggedInPost(post, user);
        } catch (Exception e) {
            log.warn("Error checking if user is tagged in post: {}", post != null ? post.getId() : "null", e);
            return false;
        }
    }

    private boolean isPostLikedByUser(Post post, User user) {
        try {
            if (post == null || user == null) {
                return false;
            }
            // Use existing PostLike repository
            return postLikeRepository.existsByPostAndUser(post, user);
        } catch (Exception e) {
            log.warn("Error checking if post is liked by user: {}", post != null ? post.getId() : "null", e);
            return false;
        }
    }

    private boolean isPostViewedByUser(Post post, User user) {
        try {
            if (post == null || user == null) {
                return false;
            }
            // Use PostView repository if you want to track views
            return postViewRepository != null &&
                    postViewRepository.findRecentViewByUserAndPost(user, post,
                            new Timestamp(System.currentTimeMillis() - 24*60*60*1000)).isPresent();
        } catch (Exception e) {
            log.warn("Error checking if post is viewed by user: {}", post != null ? post.getId() : "null", e);
            return false;
        }
    }
}