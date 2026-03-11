package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.UserTagSuggestionDto;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.*;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.repository.UserTagRepo;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.payload.PostUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
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
    private final PinCodeLookupService pincodeLookupService;
    private final PasswordEncoder passwordEncoder;
    private final PostInteractionService postInteractionService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

            if (!user.getIsActive()) {
                throw new UsernameNotFoundException("User account is inactive: " + email);
            }

            return buildUserDetails(user);
        } catch (Exception e) {
            log.error("Failed to load user: {}", email, e);
            throw new UsernameNotFoundException("Failed to load user: " + email);
        }
    }


    public User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ValidationException("User not authenticated");
        }

        String email = authentication.getName();
        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("Invalid authentication - no email found");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        if (user.getIsActive() == null || !user.getIsActive()) {
            throw new ValidationException("User account is inactive");
        }

        return user;
    }

    // ===== User Lookup Methods =====

    public User findByUsername(String username) {
        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

            return user;
        } catch (Exception e) {
            log.error("Failed to find user by username: {}", username, e);
            throw new UserNotFoundException("Failed to find user: " + e.getMessage(), e);
        }
    }

    public User findById(Long userId) {
        try {
            PostUtility.validateUserId(userId);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));

            return user;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to find user by ID: {}", userId, e);
            throw new UserNotFoundException("Failed to find user: " + e.getMessage(), e);
        }
    }

    // ===== Department User Methods Using Pincode Prefix Logic =====

    public PaginatedResponse<User> findDepartmentUsersByPincode(String pincode, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupGeographicPagination(
                    "findDepartmentUsersByPincode", beforeId, limit, "pincode");

            if (!Constant.isValidIndianPincode(pincode)) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            Pageable pageable = PaginationUtils.createPageable(setup);

            List<User> users;
            if (setup.hasCursor()) {
                users = userRepository.findByRoleNameAndPincodeAndIsActiveTrueAndIdLessThanOrderByIdDesc(
                        Constant.ROLE_DEPARTMENT, pincode, setup.getSanitizedCursor(), pageable);
            } else {
                users = userRepository.findByRoleNameAndPincodeAndIsActiveTrueOrderByIdDesc(
                        Constant.ROLE_DEPARTMENT, pincode, pageable);
            }

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(
                    users != null ? users : List.of(), setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("findDepartmentUsersByPincode",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to find department users by pincode: {}", pincode, e);
            return PaginationUtils.handlePaginationError("findDepartmentUsersByPincode", e,
                    PaginationUtils.validateGeographicSearchLimit(limit, "pincode"));
        }
    }

    public PaginatedResponse<User> findDepartmentUsersByState(String state, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupGeographicPagination(
                    "findDepartmentUsersByState", beforeId, limit, "state");

            if (state == null || state.trim().isEmpty()) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<String> statePrefixes = PostUtility.convertStatesToPincodePrefixes(List.of(state.trim()));
            if (statePrefixes.isEmpty()) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<User> departmentUsers = new ArrayList<>();
            for (String prefix : statePrefixes) {
                Pageable pageable = PaginationUtils.createPageable(setup);
                List<User> users;
                if (setup.hasCursor()) {
                    users = userRepository.findByRoleNameAndPincodeStartingWithAndIsActiveTrueAndIdLessThanOrderByIdDesc(
                            Constant.ROLE_DEPARTMENT, prefix, setup.getSanitizedCursor(), pageable);
                } else {
                    users = userRepository.findByRoleNameAndPincodeStartingWithAndIsActiveTrueOrderByIdDesc(
                            Constant.ROLE_DEPARTMENT, prefix, pageable);
                }
                if (users != null) {
                    departmentUsers.addAll(users);
                }
            }

            List<User> distinctUsers = departmentUsers.stream()
                    .distinct()
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(distinctUsers, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("findDepartmentUsersByState",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to find department users by state: {}", state, e);
            return PaginationUtils.handlePaginationError("findDepartmentUsersByState", e,
                    PaginationUtils.validateGeographicSearchLimit(limit, "state"));
        }
    }

    public PaginatedResponse<User> findDepartmentUsersByDistrict(String state, String district, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupGeographicPagination(
                    "findDepartmentUsersByDistrict", beforeId, limit, "district");

            if (state == null || district == null || state.trim().isEmpty() || district.trim().isEmpty()) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<String> districtPrefixes = PostUtility.convertDistrictsToPincodePrefixes(
                    List.of(district.trim()), pincodeLookupService);
            if (districtPrefixes.isEmpty()) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<User> departmentUsers = new ArrayList<>();
            for (String prefix : districtPrefixes) {
                Pageable pageable = PaginationUtils.createPageable(setup);
                List<User> users;
                if (setup.hasCursor()) {
                    users = userRepository.findByRoleNameAndPincodeStartingWithAndIsActiveTrueAndIdLessThanOrderByIdDesc(
                            Constant.ROLE_DEPARTMENT, prefix, setup.getSanitizedCursor(), pageable);
                } else {
                    users = userRepository.findByRoleNameAndPincodeStartingWithAndIsActiveTrueOrderByIdDesc(
                            Constant.ROLE_DEPARTMENT, prefix, pageable);
                }
                if (users != null) {
                    departmentUsers.addAll(users);
                }
            }

            List<String> statePrefixes = PostUtility.convertStatesToPincodePrefixes(List.of(state.trim()));
            if (!statePrefixes.isEmpty()) {
                String statePrefix = statePrefixes.get(0);
                departmentUsers = departmentUsers.stream()
                        .filter(user -> user.hasPincode() && user.getPincode().startsWith(statePrefix))
                        .collect(Collectors.toList());
            }

            List<User> distinctUsers = departmentUsers.stream()
                    .distinct()
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(distinctUsers, setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("findDepartmentUsersByDistrict",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to find department users by district: {} - {}", state, district, e);
            return PaginationUtils.handlePaginationError("findDepartmentUsersByDistrict", e,
                    PaginationUtils.validateGeographicSearchLimit(limit, "district"));
        }
    }

    // ===== User Search and Tagging Methods =====

    public PaginatedResponse<UserTagSuggestionDto> searchUsersForTagging(String query, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupTaggingPagination(
                    "searchUsersForTagging", beforeId, limit);

            if (query == null || query.trim().length() < 2) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            String cleanQuery = query.startsWith("@") ? query.substring(1) : query;
            if (cleanQuery.trim().length() < 2) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            Pageable pageable = PaginationUtils.createPageable(setup);

            List<User> users;
            if (setup.hasCursor()) {
                users = userRepository.searchUsersForTaggingWithCursor(cleanQuery.trim(),
                        setup.getSanitizedCursor(), pageable);
            } else {
                users = userRepository.searchUsersForTagging(cleanQuery.trim(), pageable);
            }

            List<UserTagSuggestionDto> userDtos = users.stream()
                    .map(this::convertToUserTagSuggestion)
                    .collect(Collectors.toList());

            PaginatedResponse<UserTagSuggestionDto> response = PaginationUtils.createIdBasedResponse(
                    userDtos, setup.getValidatedLimit(), UserTagSuggestionDto::getId);

            PaginationUtils.logPaginationResults("searchUsersForTagging",
                    userDtos, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to search users for tagging with query: {}", query, e);
            return PaginationUtils.handlePaginationError("searchUsersForTagging", e,
                    PaginationUtils.validateTaggingLimit(limit));
        }
    }

    // ===== Geographic Distribution Methods Using Pincode Prefix Logic =====

    @Cacheable(value = "user-distribution-pincode")
    public Map<String, Long> getUserDistributionByPincode() {
        try {
            List<Object[]> stats = userRepository.getUserDistributionByPincode();

            Map<String, Long> result = new LinkedHashMap<>();
            for (Object[] stat : stats) {
                String pincode = (String) stat[0];
                Long count = (Long) stat[1];

                if (Constant.isValidIndianPincode(pincode)) {
                    result.put(pincode, count);
                }
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to get user distribution by pincode", e);
            throw new ServiceException("Failed to get user distribution by pincode: " + e.getMessage(), e);
        }
    }

    public Map<String, Long> getUserDistributionByState() {
        try {
            Map<String, Long> result = new LinkedHashMap<>();

            List<User> usersWithPincodes = userRepository.findByPincodeIsNotNullAndIsActiveTrueAndPincodeMatches("\\d{6}");

            List<User> validIndianUsers = usersWithPincodes.stream()
                    .filter(user -> user.hasPincode() && Constant.isValidIndianPincode(user.getPincode()))
                    .collect(Collectors.toList());

            Map<String, Long> prefixCounts = validIndianUsers.stream()
                    .collect(Collectors.groupingBy(
                            user -> user.getStatePrefix(),
                            Collectors.counting()
                    ));

            for (Map.Entry<String, Long> entry : prefixCounts.entrySet()) {
                String statePrefix = entry.getKey();
                Long count = entry.getValue();

                if (Constant.isValidIndianStatePrefix(statePrefix)) {
                    List<com.JanSahayak.AI.model.PincodeLookup> samplePincodes =
                            pincodeLookupService.findByStatePrefix(statePrefix);

                    String stateName = samplePincodes.isEmpty() ?
                            "State-" + statePrefix : samplePincodes.get(0).getState();

                    result.put(stateName, count);
                }
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to get user distribution by state", e);
            throw new ServiceException("Failed to get user distribution by state: " + e.getMessage(), e);
        }
    }

    // ===== Permission Methods Using Pincode Prefix Logic =====

    public boolean canUserResolvePostsInPincode(User user, String pincode) {
        return PostUtility.canUserResolvePostsInPincode(user, pincode);
    }

    // ===== User Update Methods =====

    @Transactional
    public User updateUser(User user) {
        try {
            User existingUser = findById(user.getId());

            if (user.getEmail() != null) {
                existingUser.setEmail(user.getEmail());
            }
            if (user.getBio() != null) {
                existingUser.setBio(user.getBio());
            }
            if (user.getPincode() != null) {
                PostUtility.validateTargetPincodeForUser(user.getPincode());

                if (!pincodeLookupService.isValidPincode(user.getPincode())) {
                    throw new ValidationException("Indian pincode not found in system: " + user.getPincode());
                }
                existingUser.setPincode(user.getPincode());
            }

            User updatedUser = userRepository.save(existingUser);

            log.info("Updated user: {} with role: {}, pincode: {}",
                    updatedUser.getUsername(), updatedUser.getRole().getName(),
                    updatedUser.getPincode() != null ? updatedUser.getPincode() : "none");

            return updatedUser;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update user: {}", user.getId(), e);
            throw new ServiceException("Failed to update user: " + e.getMessage(), e);
        }
    }

    // ===== New Paginated User Listing Methods =====

    public PaginatedResponse<User> getAllActiveUsers(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getAllActiveUsers", beforeId, limit,
                    Constant.DEFAULT_ACTIVE_USER_LIMIT, Constant.MAX_ACTIVE_USER_LIMIT);

            Pageable pageable = PaginationUtils.createPageable(setup);

            List<User> users;
            if (setup.hasCursor()) {
                users = userRepository.findByIsActiveTrueAndIdLessThanOrderByIdDesc(
                        setup.getSanitizedCursor(), pageable);
            } else {
                users = userRepository.findByIsActiveTrueOrderByIdDesc(pageable);
            }

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(
                    users != null ? users : List.of(), setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getAllActiveUsers",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get active users with pagination", e);
            return PaginationUtils.handlePaginationError("getAllActiveUsers", e,
                    PaginationUtils.validateLimit(limit, Constant.DEFAULT_ACTIVE_USER_LIMIT, Constant.MAX_ACTIVE_USER_LIMIT));
        }
    }

    public PaginatedResponse<User> getUsersByRole(String roleName, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getUsersByRole", beforeId, limit,
                    Constant.DEFAULT_USER_SEARCH_LIMIT, Constant.MAX_USER_SEARCH_LIMIT);

            Pageable pageable = PaginationUtils.createPageable(setup);

            List<User> users;
            if (setup.hasCursor()) {
                users = userRepository.findByRoleNameAndIdLessThanOrderByIdDesc(roleName,
                        setup.getSanitizedCursor(), pageable);
            } else {
                users = userRepository.findByRoleNameOrderByIdDesc(roleName, pageable);
            }

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(
                    users != null ? users : List.of(), setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getUsersByRole",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get users by role: {}", roleName, e);
            return PaginationUtils.handlePaginationError("getUsersByRole", e,
                    PaginationUtils.validateUserSearchLimit(limit));
        }
    }

    public PaginatedResponse<User> getRecentlyCreatedUsers(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getRecentlyCreatedUsers", beforeId, limit,
                    Constant.DEFAULT_USER_SEARCH_LIMIT, Constant.MAX_USER_SEARCH_LIMIT);

            Pageable pageable = PaginationUtils.createPageable(setup);

            Timestamp recentCreationThreshold = new Timestamp(
                    System.currentTimeMillis() - Constant.RECENT_ACTIVITY_MILLIS);

            List<User> users;
            if (setup.hasCursor()) {
                users = userRepository.findByIsActiveTrueAndCreatedAtAfterAndIdLessThanOrderByCreatedAtDesc(
                        recentCreationThreshold, setup.getSanitizedCursor(), pageable);
            } else {
                users = userRepository.findByIsActiveTrueAndCreatedAtAfterOrderByCreatedAtDesc(
                        recentCreationThreshold, pageable);
            }

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(
                    users != null ? users : List.of(), setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getRecentlyCreatedUsers",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get recently created users", e);
            return PaginationUtils.handlePaginationError("getRecentlyCreatedUsers", e,
                    PaginationUtils.validateUserSearchLimit(limit));
        }
    }

    // ===== Account Deletion =====

    /**
     * Soft-deactivate a user account and clean up all their interaction data
     * (saved posts, shares). Hard deletion of posts/comments is handled by a
     * separate admin flow or scheduled job.
     *
     * @param userId      ID of the account to deactivate
     * @param currentUser authenticated user performing the action (must be self or admin)
     */
    @Transactional
    public void deactivateUser(Long userId, User currentUser) {
        try {
            PostUtility.validateUserId(userId);
            PostUtility.validateUser(currentUser);

            boolean isSelf  = currentUser.getId().equals(userId);
            boolean isAdmin = PostUtility.isAdmin(currentUser);
            if (!isSelf && !isAdmin) {
                throw new SecurityException("Only the account owner or an admin can deactivate this account.");
            }

            User user = findById(userId);
            if (user.getIsActive() == null || !user.getIsActive()) {
                throw new ValidationException("Account is already inactive.");
            }

            // Clean up all saves and shares before deactivating
            try {
                postInteractionService.cleanupForUserDeletion(user);
            } catch (Exception e) {
                log.warn("Failed to clean up interactions for user={}: {}", userId, e.getMessage());
            }

            user.setIsActive(false);
            userRepository.save(user);
            log.info("User account deactivated: id={} by user={}", userId, currentUser.getActualUsername());

        } catch (SecurityException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to deactivate user: id={}", userId, e);
            throw new ServiceException("Failed to deactivate user: " + e.getMessage(), e);
        }
    }

    // ===== Helper Methods =====

    private UserDetails buildUserDetails(User user) {
        return user;
    }

    private UserTagSuggestionDto convertToUserTagSuggestion(User user) {
        return UserTagSuggestionDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                .profileImage(user.getProfileImage())
                .isActive(user.getIsActive())
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .bio(PostUtility.truncateText(user.getBio(), Constant.POST_CONTENT_PREVIEW_LENGTH,
                        Constant.POST_CONTENT_TRUNCATION_SUFFIX))
                .taggableName("@" + user.getUsername())
                .pincode(user.getPincode())
                .totalTaggedPosts(userTagRepository.countByTaggedUser(user))
                .resolutionRate(calculateUserResolutionRate(user))
                .hasLocation(user.hasLocation())
                .build();
    }

    private double calculateUserResolutionRate(User user) {
        try {
            if (!PostUtility.isDepartment(user)) {
                return 0.0;
            }

            long totalTagged = userTagRepository.countByTaggedUser(user);
            long resolved = userTagRepository.countByTaggedUserAndPostStatus(user, PostStatus.RESOLVED);

            return PostUtility.calculateUserResolutionRate(user, totalTagged, resolved);
        } catch (Exception e) {
            log.warn("Failed to calculate resolution rate for user: {}", user.getUsername(), e);
            return 0.0;
        }
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

        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new ValidationException("Username already exists: " + user.getUsername());
        }

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new ValidationException("Email already exists: " + user.getEmail());
        }

        if (user.getPincode() != null && !user.getPincode().trim().isEmpty()) {
            PostUtility.validateTargetPincodeForUser(user.getPincode().trim());
        }
    }

    public PaginatedResponse<User> searchUsersByRoleAndQuery(String roleName, String query, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "searchUsersByRoleAndQuery", beforeId, limit);

            String searchQuery = (query == null) ? "" : query.trim();

            List<User> users = userRepository.searchUsersByRoleAndQueryWithCursor(
                    roleName, searchQuery, setup.getSanitizedCursor(), setup.toPageable());

            return PaginationUtils.createUserResponse(users, setup.getValidatedLimit());
        } catch (Exception e) {
            log.error("Failed to search users for role '{}' and query '{}'", roleName, query, e);
            throw new ServiceException("Failed to search users: " + e.getMessage(), e);
        }
    }

    public long countTotalUsers() {
        try {
            return userRepository.count();
        } catch (Exception e) {
            log.error("Failed to count total users", e);
            throw new ServiceException("Could not retrieve user count.", e);
        }
    }
}