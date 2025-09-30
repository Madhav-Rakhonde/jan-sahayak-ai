package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.UserNotFoundException;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.service.PostSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
public class PostSearchController {

    private final PostSearchService postSearchService;

    /**
     * Universal search for posts (authenticated users)
     * GET /api/search/posts
     */
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchPosts(
            @RequestParam String query,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(required = false) @Min(1) @Max(100) Integer limit,
            @CurrentUser User currentUser) {

        try {
            log.info("Search posts request: query='{}', beforePostId={}, limit={}, user='{}'",
                    query, beforePostId, limit, currentUser.getActualUsername());

            PaginatedResponse<PostResponse> result = postSearchService.searchPosts(
                    query, currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Posts retrieved successfully", result));

        } catch (ValidationException e) {
            log.warn("Validation error in search posts: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument in search posts: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Invalid request", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in search posts: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in search posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred"));
        }
    }

    /**
     * Universal search for posts (anonymous users)
     * GET /api/search/posts/anonymous
     */
    @GetMapping("/posts/anonymous")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchPostsAnonymous(
            @RequestParam String query,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {

        try {
            log.info("Anonymous search posts request: query='{}', beforePostId={}, limit={}",
                    query, beforePostId, limit);

            PaginatedResponse<PostResponse> result = postSearchService.searchPostsAnonymous(
                    query, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Posts retrieved successfully", result));

        } catch (ValidationException e) {
            log.warn("Validation error in anonymous search: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in anonymous search: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in anonymous search", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred"));
        }
    }

    /**
     * Advanced search with multiple filters
     * GET /api/search/posts/advanced
     */
    @GetMapping("/posts/advanced")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> advancedSearchPosts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String pincode,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(required = false) @Min(1) @Max(100) Integer limit,
            @CurrentUser User currentUser) {

        try {
            log.info("Advanced search request: query='{}', state='{}', district='{}', pincode='{}', user='{}'",
                    query, state, district, pincode, currentUser.getActualUsername());

            PaginatedResponse<PostResponse> result = postSearchService.advancedSearchPosts(
                    query, state, district, pincode, currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Advanced search completed successfully", result));

        } catch (ValidationException e) {
            log.warn("Validation error in advanced search: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument in advanced search: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Invalid request", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in advanced search: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in advanced search", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred"));
        }
    }

    /**
     * Get trending hashtags
     * GET /api/search/hashtags/trending
     */
    @GetMapping("/hashtags/trending")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<Map<String, Object>>>> getTrendingHashtags(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
            @RequestParam(required = false) @Min(1) @Max(30) Integer days,
            @CurrentUser User currentUser) {

        try {
            log.info("Get trending hashtags request: beforeId={}, limit={}, days={}, user='{}'",
                    beforeId, limit, days, currentUser.getActualUsername());

            PaginatedResponse<Map<String, Object>> result = postSearchService.getTrendingHashtags(
                    currentUser, beforeId, limit, days);

            return ResponseEntity.ok(ApiResponse.success(
                    "Trending hashtags retrieved successfully", result));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument in trending hashtags: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Invalid request", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in trending hashtags: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in trending hashtags", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred"));
        }
    }

    /**
     * Get search suggestions
     * GET /api/search/suggestions
     */
    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSearchSuggestions(
            @RequestParam(required = false) String query,
            @CurrentUser User currentUser) {

        try {
            log.info("Get search suggestions request: query='{}', user='{}'",
                    query, currentUser.getActualUsername());

            Map<String, Object> result = postSearchService.getSearchSuggestions(query, currentUser);

            return ResponseEntity.ok(ApiResponse.success(
                    "Search suggestions retrieved successfully", result));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument in search suggestions: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Invalid request", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in search suggestions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred"));
        }
    }

    /**
     * Search posts by proximity/location
     * GET /api/search/posts/proximity
     */
    @GetMapping("/posts/proximity")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchPostsByProximity(
            @RequestParam(required = false) @DecimalMin("0.1") @DecimalMax("1000.0") Double radiusKm,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(required = false) @Min(1) @Max(100) Integer limit,
            @CurrentUser User currentUser) {

        try {
            log.info("Search posts by proximity request: radiusKm={}, beforePostId={}, limit={}, user='{}'",
                    radiusKm, beforePostId, limit, currentUser.getActualUsername());

            PaginatedResponse<PostResponse> result = postSearchService.searchPostsByProximity(
                    currentUser, radiusKm, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Proximity search completed successfully", result));

        } catch (ValidationException e) {
            log.warn("Validation error in proximity search: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument in proximity search: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Invalid request", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in proximity search: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in proximity search", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred"));
        }
    }

    /**
     * Search posts by user role
     * GET /api/search/posts/role/{roleName}
     */
    @GetMapping("/posts/role/{roleName}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchPostsByUserRole(
            @PathVariable String roleName,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(required = false) @Min(1) @Max(100) Integer limit,
            @CurrentUser User currentUser) {

        try {
            log.info("Search posts by role request: roleName='{}', beforePostId={}, limit={}, user='{}'",
                    roleName, beforePostId, limit, currentUser.getActualUsername());

            PaginatedResponse<PostResponse> result = postSearchService.searchPostsByUserRole(
                    roleName, currentUser, beforePostId, limit);

            return ResponseEntity.ok(ApiResponse.success(
                    "Role-based search completed successfully", result));

        } catch (ValidationException e) {
            log.warn("Validation error in role search: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument in role search: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Invalid request", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in role search: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in role search", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred"));
        }
    }


}