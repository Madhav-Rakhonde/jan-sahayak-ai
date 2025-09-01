package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.UserTagSuggestionDto;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.*;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.repository.UserTagRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.ValidationException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepo userRepository;
    private final PostRepo postRepository;
    private final UserTagRepo userTagRepository;
    private final LocationService locationService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

            if (!user.getIsActive()) {
                throw new UsernameNotFoundException("User account is inactive: " + username);
            }

            return buildUserDetails(user);
        } catch (Exception e) {
            log.error("Failed to load user: {}", username, e);
            throw new UsernameNotFoundException("Failed to load user: " + username);
        }
    }

    /**
     * Find user by username - Primary method for username-based lookups
     * Used by controllers and business logic when working with authenticated users
     */
    public User findByUsername(String username) {
        try {
            return userRepository.findByUsername(username)
                    .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        } catch (Exception e) {
            log.error("Failed to find user by username: {}", username, e);
            throw new UserNotFoundException("Failed to find user: " + e.getMessage(), e);
        }
    }

    /**
     * Get user by username - Alias for findByUsername to maintain backward compatibility
     * @deprecated Use findByUsername() instead for consistency
     */
    @Deprecated
    public User getUserByUsername(String username) {
        return findByUsername(username);
    }

    /**
     * Find user by ID - Primary method for ID-based lookups
     */
    public User findById(Long userId) {
        try {
            return userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
        } catch (Exception e) {
            log.error("Failed to find user by ID: {}", userId, e);
            throw new UserNotFoundException("Failed to find user: " + e.getMessage(), e);
        }
    }

    /**
     * Get user by ID - Alias for findById to maintain backward compatibility
     * @deprecated Use findById() instead for consistency
     */
    @Deprecated
    public User getUserById(Long userId) {
        return findById(userId);
    }

    /**
     * Find users by role name
     */
    @Cacheable(value = "users-by-role", key = "#roleName")
    public List<User> findByRole(String roleName) {
        try {
            return userRepository.findByRoleNameAndIsActiveTrue(roleName);
        } catch (Exception e) {
            log.error("Failed to find users by role: {}", roleName, e);
            throw new ServiceException("Failed to find users by role: " + e.getMessage(), e);
        }
    }

    /**
     * Get users by role name - Alias for findByRole to maintain backward compatibility
     * @deprecated Use findByRole() instead for consistency
     */
    @Deprecated
    public List<User> getUsersByRole(String roleName) {
        return findByRole(roleName);
    }

    /**
     * Find department users by location
     */
    @Cacheable(value = "department-users", key = "#location")
    public List<User> findDepartmentUsersByLocation(String location) {
        try {
            return userRepository.findByRoleNameAndLocationAndIsActiveTrue("ROLE_DEPARTMENT", location);
        } catch (Exception e) {
            log.error("Failed to find department users by location: {}", location, e);
            throw new ServiceException("Failed to find department users: " + e.getMessage(), e);
        }
    }

    /**
     * Get department users by location - Alias for findDepartmentUsersByLocation
     * @deprecated Use findDepartmentUsersByLocation() instead for consistency
     */
    @Deprecated
    public List<User> getDepartmentUsersByLocation(String location) {
        return findDepartmentUsersByLocation(location);
    }

    /**
     * Find department users by state code
     */
    @Cacheable(value = "department-users-state", key = "#stateCode")
    public List<User> findDepartmentUsersByState(String stateCode) {
        try {
            return userRepository.findDepartmentUsersByState(stateCode);
        } catch (Exception e) {
            log.error("Failed to find department users by state: {}", stateCode, e);
            throw new ServiceException("Failed to find department users by state: " + e.getMessage(), e);
        }
    }

    /**
     * Get department users by state - Alias for findDepartmentUsersByState
     * @deprecated Use findDepartmentUsersByState() instead for consistency
     */
    @Deprecated
    public List<User> getDepartmentUsersByState(String stateCode) {
        return findDepartmentUsersByState(stateCode);
    }

    /**
     * Find all department users
     */
    @Cacheable(value = "all-department-users")
    public List<User> findAllDepartmentUsers() {
        try {
            return userRepository.findAllDepartmentUsers();
        } catch (Exception e) {
            log.error("Failed to find all department users", e);
            throw new ServiceException("Failed to find all department users: " + e.getMessage(), e);
        }
    }

    /**
     * Get all department users - Alias for findAllDepartmentUsers
     * @deprecated Use findAllDepartmentUsers() instead for consistency
     */
    @Deprecated
    public List<User> getAllDepartmentUsers() {
        return findAllDepartmentUsers();
    }

    /**
     * Find admin users
     */
    @Cacheable(value = "admin-users")
    public List<User> findAdminUsers() {
        try {
            return userRepository.findAdminUsers();
        } catch (Exception e) {
            log.error("Failed to find admin users", e);
            throw new ServiceException("Failed to find admin users: " + e.getMessage(), e);
        }
    }

    /**
     * Get admin users - Alias for findAdminUsers
     * @deprecated Use findAdminUsers() instead for consistency
     */
    @Deprecated
    public List<User> getAdminUsers() {
        return findAdminUsers();
    }

    /**
     * Find normal users (citizens) by location
     */
    public List<User> findNormalUsersByLocation(String location) {
        try {
            return userRepository.findNormalUsersByLocation(location);
        } catch (Exception e) {
            log.error("Failed to find normal users by location: {}", location, e);
            throw new ServiceException("Failed to find normal users by location: " + e.getMessage(), e);
        }
    }

    /**
     * Get normal users by location - Alias for findNormalUsersByLocation
     * @deprecated Use findNormalUsersByLocation() instead for consistency
     */
    @Deprecated
    public List<User> getNormalUsersByLocation(String location) {
        return findNormalUsersByLocation(location);
    }

    /**
     * Search users for tagging
     */
    public List<UserTagSuggestionDto> searchUsersForTagging(String query) {
        try {
            if (query == null || query.trim().length() < 2) {
                return Collections.emptyList();
            }

            String cleanQuery = query.startsWith("@") ? query.substring(1) : query;
            if (cleanQuery.trim().length() < 2) {
                return Collections.emptyList();
            }

            List<User> users = userRepository.searchUsersForTagging(cleanQuery.trim());

            return users.stream()
                    .limit(20)
                    .map(this::convertToUserTagSuggestion)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to search users for tagging with query: {}", query, e);
            throw new ServiceException("Failed to search users for tagging: " + e.getMessage(), e);
        }
    }

    /**
     * Find users by location proximity
     */
    public List<User> findUsersByLocationProximity(String location, Long excludeUserId, int limit) {
        try {
            String stateCode = locationService.extractStateCodeFromLocation(location);
            if (stateCode == null) {
                return Collections.emptyList();
            }

            Pageable pageable = PageRequest.of(0, limit);
            return userRepository.findUsersByLocationProximity(location, stateCode, excludeUserId, pageable);
        } catch (Exception e) {
            log.error("Failed to find users by location proximity: {}", location, e);
            throw new ServiceException("Failed to find users by location proximity: " + e.getMessage(), e);
        }
    }

    /**
     * Get users by location proximity - Alias for findUsersByLocationProximity
     * @deprecated Use findUsersByLocationProximity() instead for consistency
     */
    @Deprecated
    public List<User> getUsersByLocationProximity(String location, Long excludeUserId, int limit) {
        return findUsersByLocationProximity(location, excludeUserId, limit);
    }

    /**
     * Find users who can resolve posts in location
     */
    public List<User> findResolversForLocation(String location) {
        try {
            return userRepository.findResolversForLocation(location);
        } catch (Exception e) {
            log.error("Failed to find resolvers for location: {}", location, e);
            throw new ServiceException("Failed to find resolvers for location: " + e.getMessage(), e);
        }
    }

    /**
     * Get resolvers for location - Alias for findResolversForLocation
     * @deprecated Use findResolversForLocation() instead for consistency
     */
    @Deprecated
    public List<User> getResolversForLocation(String location) {
        return findResolversForLocation(location);
    }

    /**
     * Find users who can resolve posts in state
     */
    public List<User> findResolversForState(String stateCode) {
        try {
            return userRepository.findResolversForState(stateCode);
        } catch (Exception e) {
            log.error("Failed to find resolvers for state: {}", stateCode, e);
            throw new ServiceException("Failed to find resolvers for state: " + e.getMessage(), e);
        }
    }

    /**
     * Get resolvers for state - Alias for findResolversForState
     * @deprecated Use findResolversForState() instead for consistency
     */
    @Deprecated
    public List<User> getResolversForState(String stateCode) {
        return findResolversForState(stateCode);
    }

    /**
     * Find most active users (by posting)
     */
    public List<User> findMostActiveUsers(int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit);
            return userRepository.findMostActiveUsers(pageable);
        } catch (Exception e) {
            log.error("Failed to find most active users", e);
            throw new ServiceException("Failed to find most active users: " + e.getMessage(), e);
        }
    }

    /**
     * Get most active users - Alias for findMostActiveUsers
     * @deprecated Use findMostActiveUsers() instead for consistency
     */
    @Deprecated
    public List<User> getMostActiveUsers(int limit) {
        return findMostActiveUsers(limit);
    }

    /**
     * Find most tagged users
     */
    public List<User> findMostTaggedUsers(int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit);
            return userRepository.findMostTaggedUsers(pageable);
        } catch (Exception e) {
            log.error("Failed to find most tagged users", e);
            throw new ServiceException("Failed to find most tagged users: " + e.getMessage(), e);
        }
    }

    /**
     * Get most tagged users - Alias for findMostTaggedUsers
     * @deprecated Use findMostTaggedUsers() instead for consistency
     */
    @Deprecated
    public List<User> getMostTaggedUsers(int limit) {
        return findMostTaggedUsers(limit);
    }

    /**
     * Find users with incomplete profiles
     */
    public List<User> findUsersWithIncompleteProfiles() {
        try {
            return userRepository.findUsersWithIncompleteProfiles();
        } catch (Exception e) {
            log.error("Failed to find users with incomplete profiles", e);
            throw new ServiceException("Failed to find users with incomplete profiles: " + e.getMessage(), e);
        }
    }

    /**
     * Get users with incomplete profiles - Alias for findUsersWithIncompleteProfiles
     * @deprecated Use findUsersWithIncompleteProfiles() instead for consistency
     */
    @Deprecated
    public List<User> getUsersWithIncompleteProfiles() {
        return findUsersWithIncompleteProfiles();
    }

    /**
     * Find inactive posters (users who haven't posted recently)
     */
    public List<User> findInactivePosters(int daysInactive, int limit) {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysInactive);
            Timestamp cutoffTimestamp = Timestamp.valueOf(cutoffDate);
            Pageable pageable = PageRequest.of(0, limit);

            return userRepository.findInactivePosters(cutoffTimestamp, pageable);
        } catch (Exception e) {
            log.error("Failed to find inactive posters", e);
            throw new ServiceException("Failed to find inactive posters: " + e.getMessage(), e);
        }
    }

    /**
     * Get inactive posters - Alias for findInactivePosters
     * @deprecated Use findInactivePosters() instead for consistency
     */
    @Deprecated
    public List<User> getInactivePosters(int daysInactive, int limit) {
        return findInactivePosters(daysInactive, limit);
    }

    /**
     * Find never tagged department users
     */
    public List<User> findNeverTaggedDepartmentUsers() {
        try {
            return userRepository.findNeverTaggedDepartmentUsers();
        } catch (Exception e) {
            log.error("Failed to find never tagged department users", e);
            throw new ServiceException("Failed to find never tagged department users: " + e.getMessage(), e);
        }
    }

    /**
     * Get never tagged department users - Alias for findNeverTaggedDepartmentUsers
     * @deprecated Use findNeverTaggedDepartmentUsers() instead for consistency
     */
    @Deprecated
    public List<User> getNeverTaggedDepartmentUsers() {
        return findNeverTaggedDepartmentUsers();
    }

    /**
     * Find high engagement department users
     */
    public List<User> findHighEngagementDepartmentUsers() {
        try {
            return userRepository.findHighEngagementDepartmentUsers();
        } catch (Exception e) {
            log.error("Failed to find high engagement department users", e);
            throw new ServiceException("Failed to find high engagement department users: " + e.getMessage(), e);
        }
    }

    /**
     * Get high engagement department users - Alias for findHighEngagementDepartmentUsers
     * @deprecated Use findHighEngagementDepartmentUsers() instead for consistency
     */
    @Deprecated
    public List<User> getHighEngagementDepartmentUsers() {
        return findHighEngagementDepartmentUsers();
    }

    /**
     * Search users by multiple criteria
     */
    public List<User> searchUsers(String roleName, String location, String query, int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit);
            return userRepository.searchUsers(roleName, location, query, pageable);
        } catch (Exception e) {
            log.error("Failed to search users with criteria", e);
            throw new ServiceException("Failed to search users: " + e.getMessage(), e);
        }
    }

    /**
     * Get user statistics by role
     */
    @Cacheable(value = "user-stats-by-role")
    public Map<String, Long> getUserCountByRole() {
        try {
            List<Object[]> stats = userRepository.getUserCountByRole();

            Map<String, Long> result = new HashMap<>();
            for (Object[] stat : stats) {
                String roleName = (String) stat[0];
                Long count = (Long) stat[1];
                result.put(roleName, count);
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to get user count by role", e);
            throw new ServiceException("Failed to get user count by role: " + e.getMessage(), e);
        }
    }

    /**
     * Get user distribution by location
     */
    @Cacheable(value = "user-distribution-location")
    public Map<String, Long> getUserDistributionByLocation() {
        try {
            List<Object[]> stats = userRepository.getUserDistributionByLocation();

            Map<String, Long> result = new LinkedHashMap<>(); // Maintain order
            for (Object[] stat : stats) {
                String location = (String) stat[0];
                Long count = (Long) stat[1];
                result.put(location, count);
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to get user distribution by location", e);
            throw new ServiceException("Failed to get user distribution by location: " + e.getMessage(), e);
        }
    }

    public Long countActiveUsers() {
        try {
            return userRepository.countActiveUsers();
        } catch (Exception e) {
            log.error("Failed to count active users", e);
            throw new ServiceException("Failed to count active users: " + e.getMessage(), e);
        }
    }

    /**
     * Count users by role name
     */
    public Long countUsersByRole(String roleName) {
        try {
            return userRepository.countByRoleName(roleName);
        } catch (Exception e) {
            log.error("Failed to count users by role: {}", roleName, e);
            throw new ServiceException("Failed to count users by role: " + e.getMessage(), e);
        }
    }

    /**
     * Validate if user can resolve posts in location
     */
    public boolean canUserResolvePostsInLocation(User user, String location) {
        try {
            String userRole = user.getRole().getName();

            // Admins can resolve anywhere
            if ("ROLE_ADMIN".equals(userRole)) {
                return true;
            }

            // Department users can resolve in their location
            if ("ROLE_DEPARTMENT".equals(userRole)) {
                return user.getLocation().equals(location);
            }

            return false;
        } catch (Exception e) {
            log.warn("Failed to validate user resolution permissions", e);
            return false;
        }
    }

    /**
     * Check if user has department role
     */
    public boolean isDepartmentUser(User user) {
        try {
            return user != null && user.getRole() != null &&
                    "ROLE_DEPARTMENT".equals(user.getRole().getName());
        } catch (Exception e) {
            log.warn("Failed to check if user is department user", e);
            return false;
        }
    }

    /**
     * Check if user has admin role
     */
    public boolean isAdminUser(User user) {
        try {
            return user != null && user.getRole() != null &&
                    "ROLE_ADMIN".equals(user.getRole().getName());
        } catch (Exception e) {
            log.warn("Failed to check if user is admin user", e);
            return false;
        }
    }

    /**
     * Check if user has normal user role
     */
    public boolean isNormalUser(User user) {
        try {
            return user != null && user.getRole() != null &&
                    "ROLE_USER".equals(user.getRole().getName());
        } catch (Exception e) {
            log.warn("Failed to check if user is normal user", e);
            return false;
        }
    }

    /**
     * Create new user account
     */
    @Transactional
    public User createUser(User user, String rawPassword) {
        try {
            validateNewUser(user);

            // Encrypt password
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setIsActive(true);
            user.setCreatedAt(new Date());

            // Validate and normalize location
            if (user.getLocation() != null) {
                LocationService.LocationValidationResult validation =
                        locationService.validateAndSuggestLocation(user.getLocation());

                if (validation.isValid()) {
                    user.setLocation(validation.getNormalizedLocation());
                } else {
                    throw new ValidationException("Invalid location: " + validation.getErrorMessage());
                }
            }

            User savedUser = userRepository.save(user);
            log.info("Created new user: {} with role: {}", savedUser.getUsername(), savedUser.getRole().getName());

            return savedUser;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create user: {}", user.getUsername(), e);
            throw new ServiceException("Failed to create user: " + e.getMessage(), e);
        }
    }

    /**
     * Update user profile
     */
    @Transactional
    public User updateUser(User user) {
        try {
            User existingUser = findById(user.getId());

            // Update allowed fields
            if (user.getEmail() != null) {
                existingUser.setEmail(user.getEmail());
            }
            if (user.getBio() != null) {
                existingUser.setBio(user.getBio());
            }
            if (user.getLocation() != null) {
                LocationService.LocationValidationResult validation =
                        locationService.validateAndSuggestLocation(user.getLocation());

                if (validation.isValid()) {
                    existingUser.setLocation(validation.getNormalizedLocation());
                } else {
                    throw new ValidationException("Invalid location: " + validation.getErrorMessage());
                }
            }

            User updatedUser = userRepository.save(existingUser);
            log.info("Updated user: {} with role: {}", updatedUser.getUsername(), updatedUser.getRole().getName());

            return updatedUser;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update user: {}", user.getId(), e);
            throw new ServiceException("Failed to update user: " + e.getMessage(), e);
        }
    }

    /**
     * Deactivate user account
     */
    @Transactional
    public void deactivateUser(Long userId, String reason) {
        try {
            User user = findById(userId);
            user.setIsActive(false);

            userRepository.save(user);
            log.info("Deactivated user: {} (role: {}) for reason: {}",
                    user.getUsername(), user.getRole().getName(), reason);
        } catch (Exception e) {
            log.error("Failed to deactivate user: {}", userId, e);
            throw new ServiceException("Failed to deactivate user: " + e.getMessage(), e);
        }
    }

    /**
     * Get user performance statistics (includes post status information)
     */
    public Map<String, Object> getUserPerformanceStats(User user) {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Basic user info
            stats.put("userId", user.getId());
            stats.put("username", user.getUsername());
            stats.put("role", user.getRole().getName());
            stats.put("location", user.getLocation());
            stats.put("isActive", user.getIsActive());

            // Post statistics by status
            Map<String, Long> postsByStatus = new HashMap<>();
            for (PostStatus status : PostStatus.values()) {
                long count = postRepository.countByUserAndStatus(user, status);
                postsByStatus.put(status.getDisplayName(), count);
            }
            stats.put("postsByStatus", postsByStatus);

            // Total posts
            long totalPosts = postRepository.countByUser(user);
            stats.put("totalPosts", totalPosts);

            // Tagged posts statistics (if department user)
            if (isDepartmentUser(user)) {
                long totalTaggedPosts = userTagRepository.countByTaggedUser(user);
                long activeTaggedPosts = userTagRepository.countByTaggedUserAndPostStatus(user, PostStatus.ACTIVE);
                long resolvedTaggedPosts = userTagRepository.countByTaggedUserAndPostStatus(user, PostStatus.RESOLVED);

                stats.put("totalTaggedPosts", totalTaggedPosts);
                stats.put("activeTaggedPosts", activeTaggedPosts);
                stats.put("resolvedTaggedPosts", resolvedTaggedPosts);

                // Resolution rate
                double resolutionRate = totalTaggedPosts > 0 ?
                        (double) resolvedTaggedPosts / totalTaggedPosts * 100 : 0;
                stats.put("resolutionRate", Math.round(resolutionRate * 100.0) / 100.0);
            }

            return stats;
        } catch (Exception e) {
            log.error("Failed to get user performance stats for user: {}", user.getUsername(), e);
            throw new ServiceException("Failed to get user performance stats: " + e.getMessage(), e);
        }
    }

    /**
     * Get comprehensive user analytics
     */
    public Map<String, Object> getUserAnalytics(User user) {
        try {
            Map<String, Object> analytics = new HashMap<>();

            // Include performance stats
            analytics.putAll(getUserPerformanceStats(user));

            // Post status breakdown with additional info
            Map<String, Object> statusBreakdown = new HashMap<>();
            for (PostStatus status : PostStatus.values()) {
                long count = postRepository.countByUserAndStatus(user, status);
                Map<String, Object> statusInfo = new HashMap<>();
                statusInfo.put("count", count);
                statusInfo.put("displayName", status.getDisplayName());
                statusInfo.put("icon", status.getIcon());
                statusInfo.put("description", status.getDescription());
                statusInfo.put("isVisible", status.isVisible());
                statusInfo.put("allowsUpdates", status.allowsUpdates());
                statusBreakdown.put(status.name(), statusInfo);
            }
            analytics.put("detailedStatusBreakdown", statusBreakdown);

            // Recent activity (last 30 days)
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            Timestamp cutoff = Timestamp.valueOf(thirtyDaysAgo);
            long recentPosts = postRepository.countByUserAndCreatedAtAfter(user, cutoff);
            analytics.put("recentPosts30Days", recentPosts);

            return analytics;
        } catch (Exception e) {
            log.error("Failed to get user analytics for user: {}", user.getUsername(), e);
            throw new ServiceException("Failed to get user analytics: " + e.getMessage(), e);
        }
    }

    // Helper Methods

    private UserDetails buildUserDetails(User user) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(user.getRole().getName())
                .accountExpired(false)
                .accountLocked(!user.getIsActive())
                .credentialsExpired(false)
                .disabled(!user.getIsActive())
                .build();
    }

    private UserTagSuggestionDto convertToUserTagSuggestion(User user) {
        return UserTagSuggestionDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                .location(user.getLocation())
                .profileImage(user.getProfileImage())
                .isActive(user.getIsActive())
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .bio(truncateText(user.getBio(), 100))
                .taggableName("@" + user.getUsername())
                .hasLocation(user.hasLocation())
                .totalTaggedPosts(userTagRepository.countByTaggedUser(user)) // Use actual count
                .resolutionRate(calculateUserResolutionRate(user)) // Calculate actual resolution rate
                .build();
    }

    private double calculateUserResolutionRate(User user) {
        try {
            if (!isDepartmentUser(user)) {
                return 0.0;
            }

            long totalTagged = userTagRepository.countByTaggedUser(user);
            if (totalTagged == 0) {
                return 0.0;
            }

            long resolved = userTagRepository.countByTaggedUserAndPostStatus(user, PostStatus.RESOLVED);
            return Math.round((double) resolved / totalTagged * 100.0 * 100.0) / 100.0;
        } catch (Exception e) {
            log.warn("Failed to calculate resolution rate for user: {}", user.getUsername(), e);
            return 0.0;
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private void validateNewUser(User user) {
        if (user == null) {
            throw new ValidationException("User cannot be null");
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new ValidationException("Username is required");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new ValidationException("Email is required");
        }
        if (user.getRole() == null) {
            throw new ValidationException("User role is required");
        }

        // Check username uniqueness
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new ValidationException("Username already exists: " + user.getUsername());
        }

        // Check email uniqueness
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new ValidationException("Email already exists: " + user.getEmail());
        }
    }
}