package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.UserTagSuggestionDto;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.UserNotFoundException;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final UserRepo    userRepository;

    // ===== User Lookup Methods =====

    /**
     * Get the currently authenticated user's own profile.
     *
     * @Transactional(readOnly=true) keeps the Hibernate session open through Jackson
     * serialization, preventing LazyInitializationException on lazy collections.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<User>> getCurrentUserProfile() {
        try {
            User user = getCurrentUser();
            return ResponseEntity.ok(ApiResponse.success("Current user retrieved successfully", user));

        } catch (ValidationException e) {
            log.warn("Validation error in getCurrentUserProfile: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.error("Unauthorized", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getCurrentUserProfile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving current user"));
        }
    }

    @GetMapping("/username/{username}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<User>> findByUsername(
            @PathVariable @Size(min = 4, max = 100, message = "Username must be between 4 and 100 characters") String username) {

        try {
            User user = userService.findByUsername(username);
            return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user));

        } catch (UserNotFoundException e) {
            log.warn("User not found by username: {}", username);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("User not found", e.getMessage()));
        } catch (ValidationException e) {
            log.warn("Validation error in findByUsername: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in findByUsername for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving user"));
        }
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<User>> findById(
            @PathVariable @Min(value = 1, message = "User ID must be positive") Long userId) {

        try {
            User user = userService.findById(userId);
            return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user));

        } catch (UserNotFoundException e) {
            log.warn("User not found by ID: {}", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("User not found", e.getMessage()));
        } catch (ValidationException e) {
            log.warn("Validation error in findById: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in findById for userId: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving user"));
        }
    }

    // ===== Department User Search Methods =====

    @GetMapping("/departments/by-pincode/{pincode}")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> findDepartmentUsersByPincode(
            @PathVariable @Pattern(regexp = "^[1-9]\\d{5}$", message = "Invalid Indian pincode format") String pincode,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<User> response = userService.findDepartmentUsersByPincode(pincode, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success(
                    "Department users by pincode retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in findDepartmentUsersByPincode: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in findDepartmentUsersByPincode: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in findDepartmentUsersByPincode for pincode: {}", pincode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving department users"));
        }
    }

    @GetMapping("/departments/by-state/{state}")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> findDepartmentUsersByState(
            @PathVariable @Size(min = 2, max = 50, message = "State name must be between 2 and 50 characters") String state,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<User> response = userService.findDepartmentUsersByState(state, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success(
                    "Department users by state retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in findDepartmentUsersByState: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in findDepartmentUsersByState: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in findDepartmentUsersByState for state: {}", state, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving department users"));
        }
    }

    @GetMapping("/departments/by-district")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> findDepartmentUsersByDistrict(
            @RequestParam @Size(min = 2, max = 50, message = "State name must be between 2 and 50 characters") String state,
            @RequestParam @Size(min = 2, max = 50, message = "District name must be between 2 and 50 characters") String district,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<User> response = userService.findDepartmentUsersByDistrict(state, district, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success(
                    "Department users by district retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in findDepartmentUsersByDistrict: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in findDepartmentUsersByDistrict: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in findDepartmentUsersByDistrict for state: {} district: {}", state, district, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving department users"));
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> searchUsers(
            @RequestParam @Size(min = 2, max = 50, message = "Query must be between 2 and 50 characters") String query,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<User> response = userService.searchUsers(query, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in searchUsers: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in searchUsers for query: {}", query, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while searching users"));
        }
    }

    // ===== User Search and Tagging Methods =====

    @GetMapping("/search/tagging")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PaginatedResponse<UserTagSuggestionDto>>> searchUsersForTagging(
            @RequestParam @Size(min = 2, max = 50, message = "Query must be between 2 and 50 characters") String query,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<UserTagSuggestionDto> response = userService.searchUsersForTagging(query, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Users for tagging retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in searchUsersForTagging: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in searchUsersForTagging: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in searchUsersForTagging for query: {}", query, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while searching users for tagging"));
        }
    }

    // ===== Geographic Distribution Methods =====

    @GetMapping("/distribution/by-pincode")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUserDistributionByPincode() {
        try {
            Map<String, Long> distribution = userService.getUserDistributionByPincode();
            return ResponseEntity.ok(ApiResponse.success(
                    "User distribution by pincode retrieved successfully", distribution));

        } catch (ServiceException e) {
            log.error("Service error in getUserDistributionByPincode: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getUserDistributionByPincode", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving user distribution by pincode"));
        }
    }

    @GetMapping("/distribution/by-state")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUserDistributionByState() {
        try {
            Map<String, Long> distribution = userService.getUserDistributionByState();
            return ResponseEntity.ok(ApiResponse.success(
                    "User distribution by state retrieved successfully", distribution));

        } catch (ServiceException e) {
            log.error("Service error in getUserDistributionByState: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getUserDistributionByState", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving user distribution by state"));
        }
    }

    // ===== Permission Check Methods =====

    @GetMapping("/permissions/resolve-posts/{pincode}")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Boolean>> canUserResolvePostsInPincode(
            @PathVariable @Pattern(regexp = "^[1-9]\\d{5}$", message = "Invalid Indian pincode format") String pincode) {

        try {
            User currentUser = getCurrentUser();
            boolean canResolve = userService.canUserResolvePostsInPincode(currentUser, pincode);
            return ResponseEntity.ok(ApiResponse.success("Permission check completed successfully", canResolve));

        } catch (ValidationException e) {
            log.warn("Validation error in canUserResolvePostsInPincode: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in canUserResolvePostsInPincode: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in canUserResolvePostsInPincode for pincode: {}", pincode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while checking permissions"));
        }
    }

    // ===== User Update Methods =====

    @PutMapping("/profile")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<User>> updateUserProfile(@Valid @RequestBody UserUpdateRequest updateRequest) {
        try {
            User currentUser = getCurrentUser();

            User userToUpdate = User.builder()
                    .id(currentUser.getId())
                    .email(updateRequest.getEmail())
                    .bio(updateRequest.getBio())
                    .pincode(updateRequest.getPincode())
                    .build();

            User updatedUser = userService.updateUser(userToUpdate);
            return ResponseEntity.ok(ApiResponse.success("User profile updated successfully", updatedUser));

        } catch (ValidationException e) {
            log.warn("Validation error in updateUserProfile: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in updateUserProfile: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in updateUserProfile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while updating user profile"));
        }
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<User>> updateUser(
            @PathVariable @Min(value = 1, message = "User ID must be positive") Long userId,
            @Valid @RequestBody UserUpdateRequest updateRequest) {

        try {
            User userToUpdate = User.builder()
                    .id(userId)
                    .email(updateRequest.getEmail())
                    .bio(updateRequest.getBio())
                    .pincode(updateRequest.getPincode())
                    .build();

            User updatedUser = userService.updateUser(userToUpdate);
            return ResponseEntity.ok(ApiResponse.success("User updated successfully", updatedUser));

        } catch (UserNotFoundException e) {
            log.warn("User not found for update: {}", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("User not found", e.getMessage()));
        } catch (ValidationException e) {
            log.warn("Validation error in updateUser: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in updateUser: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in updateUser for userId: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while updating user"));
        }
    }

    // ===== User Listing Methods =====

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> getAllActiveUsers(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<User> response = userService.getAllActiveUsers(beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Active users retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getAllActiveUsers: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getAllActiveUsers: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getAllActiveUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving active users"));
        }
    }

    @GetMapping("/by-role/{roleName}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> getUsersByRole(
            @PathVariable @Size(min = 3, max = 20, message = "Role name must be between 3 and 20 characters") String roleName,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<User> response = userService.getUsersByRole(roleName, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Users by role retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getUsersByRole: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getUsersByRole: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getUsersByRole for role: {}", roleName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving users by role"));
        }
    }

    @GetMapping("/recent")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> getRecentlyCreatedUsers(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<User> response = userService.getRecentlyCreatedUsers(beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Recently created users retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getRecentlyCreatedUsers: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getRecentlyCreatedUsers: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getRecentlyCreatedUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving recently created users"));
        }
    }

    // ===== Helper Methods =====

    /**
     * FIX: Changed from userRepository.findByEmail() to userRepository.findByEmailWithRole().
     *
     * User.role is now FetchType.LAZY. The original findByEmail() returned a user with
     * an uninitialized role proxy — any subsequent call to user.isAdmin() or
     * user.isDepartment() (inside security checks or service calls) would trigger a
     * second SELECT to load the role.
     *
     * findByEmailWithRole() uses JOIN FETCH to load both User and Role in ONE query,
     * so the controller has the fully-hydrated user ready for any permission check
     * without an extra DB round-trip.
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ValidationException("User not authenticated");
        }

        String email = authentication.getName();
        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("Invalid authentication - no email found");
        }

        try {
            // FIX: findByEmailWithRole() instead of findByEmail()
            User user = userRepository.findByEmailWithRole(email)
                    .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

            if (user.getIsActive() == null || !user.getIsActive()) {
                throw new ValidationException("User account is inactive");
            }

            return user;

        } catch (UserNotFoundException e) {
            log.warn("User not found with email: {}", email);
            throw new ValidationException("User not found: " + email);
        } catch (Exception e) {
            log.error("Error retrieving current user with email: {}", email, e);
            throw new ValidationException("Error retrieving user information");
        }
    }

    public static class UserUpdateRequest {
        @jakarta.validation.constraints.Email(message = "Invalid email format")
        private String email;

        @Size(max = 1000, message = "Bio cannot exceed 1000 characters")
        private String bio;

        @Pattern(regexp = "^[1-9]\\d{5}$", message = "Invalid Indian pincode format")
        private String pincode;

        public String getEmail()   { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getBio()     { return bio; }
        public void setBio(String bio) { this.bio = bio; }

        public String getPincode() { return pincode; }
        public void setPincode(String pincode) { this.pincode = pincode; }
    }
}