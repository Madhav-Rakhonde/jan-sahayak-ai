package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.service.SchemeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Min;


@RestController
@RequestMapping("/api/schemes")
@RequiredArgsConstructor
@Slf4j
public class SchemeController {

    private final SchemeService schemeService;

    /**
     * Search agriculture-related posts by wildcard patterns
     * GET /api/schemes/agriculture/posts
     */
    @GetMapping("/agriculture/posts")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchAgriculturePosts(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(defaultValue = "20") @Min(1) Integer limit) {

        try {
            log.info("Searching agriculture posts for user: {} with beforePostId: {}, limit: {}",
                    currentUser.getEmail(), beforePostId, limit);

            PaginatedResponse<PostResponse> result = schemeService.searchAgriculturePostsByWildcard(
                    currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Agriculture posts retrieved successfully", result));

        } catch (ValidationException e) {
            log.warn("Validation error while searching agriculture posts: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Invalid request parameters", e.getMessage()));

        } catch (ServiceException e) {
            log.error("Service error while searching agriculture posts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error while searching agriculture posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error",
                            "An unexpected error occurred while searching agriculture posts"));
        }
    }



    /**
     * Search scheme-related posts by government departments
     * GET /api/schemes/department/posts
     */
    @GetMapping("/department/posts")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchSchemePostsByDepartment(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(defaultValue = "20") @Min(1) Integer limit) {

        try {
            log.info("Searching scheme posts by department for user: {} with beforePostId: {}, limit: {}",
                    currentUser.getEmail(), beforePostId, limit);

            PaginatedResponse<PostResponse> result = schemeService.searchSchemePostsByDepartment(
                    currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Scheme posts by department retrieved successfully", result));

        } catch (ValidationException e) {
            log.warn("Validation error while searching scheme posts: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Invalid request parameters", e.getMessage()));

        } catch (ServiceException e) {
            log.error("Service error while searching scheme posts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error while searching scheme posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error",
                            "An unexpected error occurred while searching scheme posts"));
        }
    }

    /**
     * Search health-related posts by government departments
     * GET /api/schemes/health/posts
     */
    @GetMapping("/health/posts")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchHealthPostsByDepartment(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(defaultValue = "20") @Min(1) Integer limit) {

        try {
            log.info("Searching health posts by department for user: {} with beforePostId: {}, limit: {}",
                    currentUser.getEmail(), beforePostId, limit);

            PaginatedResponse<PostResponse> result = schemeService.searchHealthPostsByDepartment(
                    currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Health posts by department retrieved successfully", result));

        } catch (ValidationException e) {
            log.warn("Validation error while searching health posts: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Invalid request parameters", e.getMessage()));

        } catch (ServiceException e) {
            log.error("Service error while searching health posts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error while searching health posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error",
                            "An unexpected error occurred while searching health posts"));
        }
    }

    /**
     * Search finance-related posts by wildcard patterns
     * GET /api/schemes/finance/posts
     */
    @GetMapping("/finance/posts")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchFinancePostsByWildcard(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(defaultValue = "20") @Min(1) Integer limit) {

        try {
            log.info("Searching finance posts by wildcard for user: {} with beforePostId: {}, limit: {}",
                    currentUser.getEmail(), beforePostId, limit);

            PaginatedResponse<PostResponse> result = schemeService.searchFinancePostsByWildcard(
                    currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Finance posts retrieved successfully", result));

        } catch (ValidationException e) {
            log.warn("Validation error while searching finance posts: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Invalid request parameters", e.getMessage()));

        } catch (ServiceException e) {
            log.error("Service error while searching finance posts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error while searching finance posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error",
                            "An unexpected error occurred while searching finance posts"));
        }
    }

    /**
     * Search education and employment-related posts by wildcard patterns
     * GET /api/schemes/education-employment/posts
     */
    @GetMapping("/education-employment/posts")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchEducationEmploymentPostsByWildcard(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(defaultValue = "20") @Min(1) Integer limit) {

        try {
            log.info("Searching education-employment posts by wildcard for user: {} with beforePostId: {}, limit: {}",
                    currentUser.getEmail(), beforePostId, limit);

            PaginatedResponse<PostResponse> result = schemeService.searchEducationEmploymentPostsByWildcard(
                    currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Education and employment posts retrieved successfully", result));

        } catch (ValidationException e) {
            log.warn("Validation error while searching education-employment posts: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Invalid request parameters", e.getMessage()));

        } catch (ServiceException e) {
            log.error("Service error while searching education-employment posts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error while searching education-employment posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error",
                            "An unexpected error occurred while searching education-employment posts"));
        }
    }

    /**
     * Search infrastructure and transportation-related posts by wildcard patterns
     * GET /api/schemes/infrastructure/posts
     */
    @GetMapping("/infrastructure/posts")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchInfrastructurePostsByWildcard(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(defaultValue = "20") @Min(1) Integer limit) {

        try {
            log.info("Searching infrastructure posts by wildcard for user: {} with beforePostId: {}, limit: {}",
                    currentUser.getEmail(), beforePostId, limit);

            PaginatedResponse<PostResponse> result = schemeService.searchInfrastructurePostsByWildcard(
                    currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Infrastructure posts retrieved successfully", result));

        } catch (ValidationException e) {
            log.warn("Validation error while searching infrastructure posts: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Invalid request parameters", e.getMessage()));

        } catch (ServiceException e) {
            log.error("Service error while searching infrastructure posts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error while searching infrastructure posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error",
                            "An unexpected error occurred while searching infrastructure posts"));
        }
    }
    /**
     * Get high priority emergency posts (weather alerts, traffic updates, etc.)
     * Only returns broadcasting posts visible to the user based on location
     */
    @GetMapping("/emergency/high-priority")
    @PreAuthorize("hasRole('ROLE_USER') or hasRole('ROLE_DEPARTMENT') or hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getHighPriorityEmergencyPosts(
            @RequestParam(value = "beforePostId", required = false) Long beforePostId,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit,
            @CurrentUser User currentUser) {

        try {
            log.info("Fetching high priority emergency posts for user: {} (ID: {}), beforePostId: {}, limit: {}",
                    currentUser.getActualUsername(), currentUser.getId(), beforePostId, limit);

            PaginatedResponse<PostResponse> emergencyPosts = schemeService.searchHighPriorityEmergencyPosts(
                    currentUser, beforePostId, limit);

            log.info("Retrieved {} high priority emergency posts for user: {}",
                    emergencyPosts.getData().size(), currentUser.getActualUsername());

            return ResponseEntity.ok(ApiResponse.success(
                    "High priority emergency posts retrieved successfully", emergencyPosts));

        } catch (ValidationException e) {
            log.warn("Validation error while fetching emergency posts: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation error", e.getMessage()));

        } catch (SecurityException e) {
            log.warn("Security error while fetching emergency posts: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.error("Access denied", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error while fetching high priority emergency posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Internal server error", "Failed to retrieve emergency posts"));
        }
    }

}