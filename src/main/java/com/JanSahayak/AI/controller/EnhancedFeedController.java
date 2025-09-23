package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.exception.UserNotFoundException;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.service.EnhancedFeedService;
import com.JanSahayak.AI.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

@RestController
@RequestMapping("/api/feeds/enhanced")
@RequiredArgsConstructor
@Slf4j
public class EnhancedFeedController {

    private final EnhancedFeedService enhancedFeedService;
    private final UserService userService;
    private final UserRepo userRepository;

    // ===== Country-Wide Broadcast Feed =====

    /**
     * Get all active country-wide broadcasts visible to all users in India
     * Accessible by all authenticated users
     */
    @GetMapping("/country")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getCountryWideBroadcastFeed(
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            User currentUser = getCurrentUser();

            PaginatedResponse<PostResponse> response = enhancedFeedService.getCountryWideBroadcastFeed(
                    currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Country-wide broadcasts retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getCountryWideBroadcastFeed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getCountryWideBroadcastFeed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getCountryWideBroadcastFeed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving country-wide broadcasts"));
        }
    }

    // ===== State-Level Broadcast Feed =====

    /**
     * Get state-level broadcasts for user's state (based on pincode prefix)
     * Accessible by all authenticated users
     */
    @GetMapping("/state")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getStateLevelBroadcastFeed(
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            User currentUser = getCurrentUser();

            PaginatedResponse<PostResponse> response = enhancedFeedService.getStateLevelBroadcastFeed(
                    currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "State-level broadcasts retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getStateLevelBroadcastFeed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getStateLevelBroadcastFeed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getStateLevelBroadcastFeed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving state-level broadcasts"));
        }
    }

    // ===== District-Level Broadcast Feed =====

    /**
     * Get district-level broadcasts for user's district (based on pincode prefix)
     * Accessible by all authenticated users
     */
    @GetMapping("/district")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getDistrictLevelBroadcastFeed(
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            User currentUser = getCurrentUser();

            PaginatedResponse<PostResponse> response = enhancedFeedService.getDistrictLevelBroadcastFeed(
                    currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "District-level broadcasts retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getDistrictLevelBroadcastFeed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getDistrictLevelBroadcastFeed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getDistrictLevelBroadcastFeed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving district-level broadcasts"));
        }
    }

    // ===== Area/Pincode-Level Broadcast Feed =====

    /**
     * Get area-level broadcasts for user's specific pincode
     * Also includes active normal posts from other users in the same pincode/area
     * Accessible by all authenticated users
     */
    @GetMapping("/area")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getAreaLevelBroadcastFeed(
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            User currentUser = getCurrentUser();

            PaginatedResponse<PostResponse> response = enhancedFeedService.getAreaLevelBroadcastFeed(
                    currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Area-level broadcasts retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getAreaLevelBroadcastFeed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getAreaLevelBroadcastFeed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getAreaLevelBroadcastFeed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving area-level broadcasts"));
        }
    }

    // ===== Mixed Geographic Feed =====

    /**
     * Get a mixed feed combining posts from all geographic levels in random order
     * Distribution: Country (25%) + State (25%) + District (25%) + Area+Local (25%)
     * Posts are shuffled randomly to provide diverse, unpredictable content mix
     * Accessible by all authenticated users
     */
    @GetMapping("/mixed")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getMixedGeographicFeed(
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            User currentUser = getCurrentUser();

            PaginatedResponse<PostResponse> response = enhancedFeedService.getMixedGeographicFeed(
                    currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Mixed geographic feed retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getMixedGeographicFeed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getMixedGeographicFeed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getMixedGeographicFeed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving mixed geographic feed"));
        }
    }

    // ===== Combined Geographic Broadcast Feed =====

    /**
     * Get all relevant broadcasts for user based on their location
     * Includes: Country-wide + State + District + Area broadcasts
     * Accessible by all authenticated users
     */
    @GetMapping("/all-relevant")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getAllRelevantBroadcastFeed(
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            User currentUser = getCurrentUser();

            PaginatedResponse<PostResponse> response = enhancedFeedService.getAllRelevantBroadcastFeed(
                    currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "All relevant broadcasts retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getAllRelevantBroadcastFeed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getAllRelevantBroadcastFeed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getAllRelevantBroadcastFeed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving all relevant broadcasts"));
        }
    }

    // ===== Geographic Feed by Specific Location =====

    /**
     * Get broadcasts for a specific pincode (useful for location-based queries)
     * Accessible by all authenticated users
     */
    @GetMapping("/by-pincode/{pincode}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getBroadcastFeedForPincode(
            @PathVariable @Pattern(regexp = "^[1-9]\\d{5}$", message = "Invalid Indian pincode format") String pincode,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            User currentUser = getCurrentUser();

            PaginatedResponse<PostResponse> response = enhancedFeedService.getBroadcastFeedForPincode(
                    pincode, currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Broadcasts for pincode " + pincode + " retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getBroadcastFeedForPincode for pincode {}: {}", pincode, e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getBroadcastFeedForPincode for pincode {}: {}", pincode, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getBroadcastFeedForPincode for pincode {}", pincode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving broadcasts for pincode"));
        }
    }

    /**
     * Get broadcasts for a specific state (by state name)
     * Accessible by all authenticated users
     */
    @GetMapping("/by-state/{stateName}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getBroadcastFeedForState(
            @PathVariable @Size(min = 2, max = 50, message = "State name must be between 2 and 50 characters") String stateName,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            User currentUser = getCurrentUser();

            PaginatedResponse<PostResponse> response = enhancedFeedService.getBroadcastFeedForState(
                    stateName, currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Broadcasts for state " + stateName + " retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getBroadcastFeedForState for state {}: {}", stateName, e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getBroadcastFeedForState for state {}: {}", stateName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getBroadcastFeedForState for state {}", stateName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving broadcasts for state"));
        }
    }

    // ===== Feed Statistics =====

    /**
     * Get broadcast feed statistics for current user
     * Accessible by all authenticated users
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBroadcastFeedStatistics() {

        try {
            User currentUser = getCurrentUser();

            Map<String, Object> statistics = enhancedFeedService.getBroadcastFeedStatistics(currentUser);

            return ResponseEntity.ok(ApiResponse.success(
                    "Broadcast feed statistics retrieved successfully", statistics));

        } catch (ValidationException e) {
            log.warn("Validation error in getBroadcastFeedStatistics: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getBroadcastFeedStatistics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getBroadcastFeedStatistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving broadcast feed statistics"));
        }
    }

    // ===== Helper Methods =====

    /**
     * Get current authenticated user
     * @return Current user from security context
     * @throws ValidationException if user not found or not authenticated
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ValidationException("User not authenticated");
        }

        String email = authentication.getName(); // Email is used as username for authentication
        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("Invalid authentication - no email found");
        }

        try {
            // Note: Authentication.getName() returns email (used as username for Spring Security)
            // But UserService.findByUsername() searches by actual username field, not email
            // We need to find user by email since that's what's stored in the security context

            // Since your UserService doesn't have findByEmail method, we need to use repository directly
            // Or you should add findByEmail method to UserService
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

            if (user == null) {
                throw new UserNotFoundException("User not found with email: " + email);
            }

            if (user.getIsActive() == null || !user.getIsActive()) {
                throw new ValidationException("User account is inactive");
            }

            return user;

        } catch (UserNotFoundException e) {
            log.warn("User not found: {}", email);
            throw new ValidationException("User not found: " + email);
        } catch (Exception e) {
            log.error("Error retrieving current user with email: {}", email, e);
            throw new ValidationException("Error retrieving user information");
        }
    }
}