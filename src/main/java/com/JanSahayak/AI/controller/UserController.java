package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.DTO.UserResponse;
import com.JanSahayak.AI.DTO.UserStatsDto;
import com.JanSahayak.AI.DTO.UserTagSuggestionDto;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.service.PostService;
import com.JanSahayak.AI.service.UserService;
import com.JanSahayak.AI.service.UserTaggingService;
import com.JanSahayak.AI.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final PostService postService;
    private final UserTaggingService userTaggingService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        try {
            log.debug("Admin retrieving all users");
            List<User> adminUsers = userService.findAdminUsers();
            List<User> deptUsers = userService.findAllDepartmentUsers();
            adminUsers.addAll(deptUsers);

            List<UserResponse> userResponses = adminUsers.stream()
                    .map(this::convertToUserResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    ApiResponse.success(
                            String.format("Retrieved %d users successfully", adminUsers.size()),
                            userResponses
                    )
            );
        } catch (Exception e) {
            log.error("Error retrieving all users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to retrieve users", "An unexpected error occurred")
            );
        }
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllActiveUsers() {
        try {
            log.debug("Admin retrieving all active users");
            List<User> adminUsers = userService.findAdminUsers();
            List<User> deptUsers = userService.findAllDepartmentUsers();
            adminUsers.addAll(deptUsers);

            // Filter active users
            List<User> activeUsers = adminUsers.stream()
                    .filter(User::getIsActive)
                    .collect(Collectors.toList());

            List<UserResponse> userResponses = activeUsers.stream()
                    .map(this::convertToUserResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    ApiResponse.success(
                            String.format("Retrieved %d active users successfully", activeUsers.size()),
                            userResponses
                    )
            );
        } catch (Exception e) {
            log.error("Error retrieving active users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to retrieve active users", "An unexpected error occurred")
            );
        }
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<UserTagSuggestionDto>>> searchUsersForTagging(@RequestParam String query) {
        try {
            if (query == null || query.trim().length() < 2) {
                log.warn("Invalid search query: {}", query);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                        ApiResponse.error("Invalid search query", "Query must be at least 2 characters long")
                );
            }

            log.debug("Searching users for tagging with query: {}", query);
            List<UserTagSuggestionDto> suggestions = userService.searchUsersForTagging(query.trim());

            return ResponseEntity.ok(
                    ApiResponse.success(
                            String.format("Found %d user suggestions", suggestions.size()),
                            suggestions
                    )
            );
        } catch (Exception e) {
            log.error("Error searching users for tagging with query '{}': {}", query, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to search users", "An unexpected error occurred")
            );
        }
    }

    @GetMapping("/by-location/{location}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsersByLocation(@PathVariable String location) {
        try {
            log.debug("Admin retrieving users by location: {}", location);
            List<User> users = userService.findNormalUsersByLocation(location);
            List<UserResponse> userResponses = users.stream()
                    .map(this::convertToUserResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    ApiResponse.success(
                            String.format("Retrieved %d users from location '%s'", users.size(), location),
                            userResponses
                    )
            );
        } catch (Exception e) {
            log.error("Error retrieving users by location '{}': {}", location, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to retrieve users by location", "An unexpected error occurred")
            );
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserDetails(@PathVariable Long userId) {
        try {
            log.debug("Retrieving user details for ID: {}", userId);
            User user = userService.findById(userId);
            UserResponse userResponse = convertToUserResponse(user);

            return ResponseEntity.ok(
                    ApiResponse.success("User details retrieved successfully", userResponse)
            );
        } catch (RuntimeException e) {
            log.error("User not found with ID {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("User not found", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error retrieving user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to retrieve user details", "An unexpected error occurred")
            );
        }
    }


    @PutMapping("/{userId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> activateUser(
            @PathVariable Long userId,
            @CurrentUser UserPrincipal currentUser) {

        try {
            log.debug("Admin {} activating user: {}", currentUser.getId(), userId);
            User user = userService.findById(userId);
            user.setIsActive(true);
            User activatedUser = userService.updateUser(user);
            UserResponse userResponse = convertToUserResponse(activatedUser);

            return ResponseEntity.ok(
                    ApiResponse.success("User activated successfully", userResponse)
            );
        } catch (SecurityException e) {
            log.warn("Access denied for admin {} trying to activate user {}: {}",
                    currentUser.getId(), userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.error("Access denied", e.getMessage())
            );
        } catch (RuntimeException e) {
            log.error("Error activating user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.error("Activation failed", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error activating user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to activate user", "An unexpected error occurred")
            );
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@CurrentUser UserPrincipal currentUser) {
        try {
            log.debug("Retrieving current user info for: {}", currentUser.getId());
            User user = userService.findById(currentUser.getId());
            UserResponse userResponse = convertToUserResponse(user);

            return ResponseEntity.ok(
                    ApiResponse.success("Current user information retrieved successfully", userResponse)
            );
        } catch (RuntimeException e) {
            log.error("Current user not found with ID {}: {}", currentUser.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("Current user not found", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error retrieving current user {}: {}", currentUser.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to retrieve current user information", "An unexpected error occurred")
            );
        }
    }

    @GetMapping("/created-by-me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsersCreatedByMe(@CurrentUser UserPrincipal currentUser) {
        try {
            log.debug("Admin {} retrieving users created by them", currentUser.getId());
            // Since the getUsersCreatedByAdmin method doesn't exist in UserService,
            // we'll need to get the admin user and access their created users relationship
            User admin = userService.findById(currentUser.getId());
            List<User> createdUsers = admin.getCreatedUsers() != null ? admin.getCreatedUsers() : Collections.emptyList();

            List<UserResponse> userResponses = createdUsers.stream()
                    .map(this::convertToUserResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    ApiResponse.success(
                            String.format("Retrieved %d users created by you", createdUsers.size()),
                            userResponses
                    )
            );
        } catch (RuntimeException e) {
            log.error("Admin not found with ID {}: {}", currentUser.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("Admin not found", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error retrieving users created by admin {}: {}", currentUser.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to retrieve created users", "An unexpected error occurred")
            );
        }
    }

    // ===== Helper Methods for Response Conversion =====

    private UserResponse convertToUserResponse(User user) {
        try {
            UserResponse response = new UserResponse();
            response.setId(user.getId());
            response.setUsername(user.getUsername());
            response.setEmail(user.getEmail());
            response.setContactNumber(user.getContactNumber());
            response.setProfileImage(user.getProfileImage());
            response.setBio(user.getBio());
            response.setWebsite(user.getWebsite());
            response.setAddress(user.getAddress());
            response.setLocation(user.getLocation());
            response.setIsActive(user.getIsActive());
            response.setCreatedAt(user.getCreatedAt());
            response.setRole(user.getRole() != null ? user.getRole().getName() : null);

            return response;
        } catch (Exception e) {
            log.error("Error converting user to response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert user data", e);
        }
    }
}