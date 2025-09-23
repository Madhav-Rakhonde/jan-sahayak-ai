package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.*;
import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.exception.*;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;

    // ===== Regular Post Creation Endpoints =====

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @Valid @RequestBody PostCreateDto postDto,
            @CurrentUser User user) {
        try {
            Post post = postService.createPost(postDto, user);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Post created successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (MediaValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Media validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create post", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create post", "Internal server error"));
        }
    }

    @PostMapping("/with-media")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createPostWithMedia(
            @Valid @ModelAttribute PostCreateDto postDto,
            @RequestParam(value = "media", required = false) MultipartFile mediaFile,
            @CurrentUser User user) {
        try {
            Post post = postService.createPost(postDto, user, mediaFile);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Post with media created successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (MediaValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Media validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create post with media", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create post with media", "Internal server error"));
        }
    }

    // ===== Broadcasting Post Creation Endpoints =====

    @PostMapping("/broadcast")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createBroadcastPost(
            @Valid @RequestBody PostCreateDto postDto,
            @RequestParam BroadcastScope broadcastScope,
            @RequestParam(defaultValue = "IN") String targetCountry,
            @RequestParam(required = false) List<String> targetStates,
            @RequestParam(required = false) List<String> targetDistricts,
            @RequestParam(required = false) List<String> targetPincodes,
            @CurrentUser User user) {
        try {
            Post post = postService.createBroadcastPost(postDto, user, broadcastScope, targetCountry,
                    targetStates, targetDistricts, targetPincodes, null);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Broadcast post created successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create broadcast post", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create broadcast post", "Internal server error"));
        }
    }

    @PostMapping("/broadcast/with-media")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createBroadcastPostWithMedia(
            @Valid @ModelAttribute PostCreateDto postDto,
            @RequestParam BroadcastScope broadcastScope,
            @RequestParam(defaultValue = "IN") String targetCountry,
            @RequestParam(required = false) List<String> targetStates,
            @RequestParam(required = false) List<String> targetDistricts,
            @RequestParam(required = false) List<String> targetPincodes,
            @RequestParam(value = "media", required = false) MultipartFile mediaFile,
            @CurrentUser User user) {
        try {
            Post post = postService.createBroadcastPost(postDto, user, broadcastScope, targetCountry,
                    targetStates, targetDistricts, targetPincodes, mediaFile);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Broadcast post with media created successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (MediaValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Media validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create broadcast post with media", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create broadcast post with media", "Internal server error"));
        }
    }

    @PostMapping("/broadcast/country")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createCountryWideBroadcast(
            @Valid @RequestBody PostCreateDto postDto,
            @CurrentUser User user) {
        try {
            Post post = postService.createCountryWideBroadcast(postDto, user, null);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Country-wide broadcast created successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create country-wide broadcast", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create country-wide broadcast", "Internal server error"));
        }
    }

    @PostMapping("/broadcast/country/with-media")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createCountryWideBroadcastWithMedia(
            @Valid @ModelAttribute PostCreateDto postDto,
            @RequestParam(value = "media", required = false) MultipartFile mediaFile,
            @CurrentUser User user) {
        try {
            Post post = postService.createCountryWideBroadcast(postDto, user, mediaFile);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Country-wide broadcast with media created successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (MediaValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Media validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create country-wide broadcast with media", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create country-wide broadcast with media", "Internal server error"));
        }
    }

    @PostMapping("/broadcast/state")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createStateLevelBroadcast(
            @Valid @RequestBody PostCreateDto postDto,
            @RequestParam List<String> targetStates,
            @CurrentUser User user) {
        try {
            Post post = postService.createStateLevelBroadcast(postDto, user, targetStates, null);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("State-level broadcast created successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create state-level broadcast", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create state-level broadcast", "Internal server error"));
        }
    }

    @PostMapping("/broadcast/state/with-media")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createStateLevelBroadcastWithMedia(
            @Valid @ModelAttribute PostCreateDto postDto,
            @RequestParam List<String> targetStates,
            @RequestParam(value = "media", required = false) MultipartFile mediaFile,
            @CurrentUser User user) {
        try {
            Post post = postService.createStateLevelBroadcast(postDto, user, targetStates, mediaFile);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("State-level broadcast with media created successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (MediaValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Media validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create state-level broadcast with media", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create state-level broadcast with media", "Internal server error"));
        }
    }

    @PostMapping("/broadcast/district")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createDistrictLevelBroadcast(
            @Valid @RequestBody PostCreateDto postDto,
            @RequestParam(required = false) List<String> targetStates,
            @RequestParam List<String> targetDistricts,
            @CurrentUser User user) {
        try {
            Post post = postService.createDistrictLevelBroadcast(postDto, user, targetStates, targetDistricts, null);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("District-level broadcast created successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create district-level broadcast", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create district-level broadcast", "Internal server error"));
        }
    }

    @PostMapping("/broadcast/district/with-media")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createDistrictLevelBroadcastWithMedia(
            @Valid @ModelAttribute PostCreateDto postDto,
            @RequestParam(required = false) List<String> targetStates,
            @RequestParam List<String> targetDistricts,
            @RequestParam(value = "media", required = false) MultipartFile mediaFile,
            @CurrentUser User user) {
        try {
            Post post = postService.createDistrictLevelBroadcast(postDto, user, targetStates, targetDistricts, mediaFile);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("District-level broadcast with media created successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (MediaValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Media validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create district-level broadcast with media", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create district-level broadcast with media", "Internal server error"));
        }
    }

    @PostMapping("/broadcast/area")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createAreaLevelBroadcast(
            @Valid @RequestBody PostCreateDto postDto,
            @RequestParam List<String> targetPincodes,
            @CurrentUser User user) {
        try {
            Post post = postService.createAreaLevelBroadcast(postDto, user, targetPincodes, null);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Area-level broadcast created successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create area-level broadcast", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create area-level broadcast", "Internal server error"));
        }
    }

    @PostMapping("/broadcast/area/with-media")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createAreaLevelBroadcastWithMedia(
            @Valid @ModelAttribute PostCreateDto postDto,
            @RequestParam List<String> targetPincodes,
            @RequestParam(value = "media", required = false) MultipartFile mediaFile,
            @CurrentUser User user) {
        try {
            Post post = postService.createAreaLevelBroadcast(postDto, user, targetPincodes, mediaFile);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Area-level broadcast with media created successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (MediaValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Media validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create area-level broadcast with media", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create area-level broadcast with media", "Internal server error"));
        }
    }

    // ===== Broadcasting Query Endpoints =====

    @GetMapping("/broadcast")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getAllBroadcastPosts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getAllBroadcastPosts(beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Broadcast posts retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get all broadcast posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get broadcast posts", "Internal server error"));
        }
    }

    @GetMapping("/broadcast/active")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getActiveBroadcastPosts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getActiveBroadcastPosts(beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Active broadcast posts retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get active broadcast posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get active broadcast posts", "Internal server error"));
        }
    }

    @GetMapping("/broadcast/scope/{scope}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getBroadcastPostsByScope(
            @PathVariable BroadcastScope scope,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getBroadcastPostsByScope(scope, beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Broadcast posts by scope retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get broadcast posts by scope: {}", scope, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get broadcast posts by scope", "Internal server error"));
        }
    }

    @GetMapping("/visible")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getVisiblePostsForUser(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getVisiblePostsForUser(user, beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Visible posts retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get visible posts for user: {}", user.getActualUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get visible posts", "Internal server error"));
        }
    }

    @GetMapping("/broadcast/visible")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getBroadcastPostsVisibleToUser(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getBroadcastPostsVisibleToUser(user, beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Visible broadcast posts retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get visible broadcast posts for user: {}", user.getActualUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get visible broadcast posts", "Internal server error"));
        }
    }

    @GetMapping("/broadcast/country/all")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getAllCountryWideBroadcasts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getAllCountryWideBroadcasts(beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("All country-wide broadcasts retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get all country-wide broadcasts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get country-wide broadcasts", "Internal server error"));
        }
    }

    @GetMapping("/broadcast/country/active")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getActiveCountryWideBroadcasts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getActiveCountryWideBroadcasts(beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Active country-wide broadcasts retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get active country-wide broadcasts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get active country-wide broadcasts", "Internal server error"));
        }
    }

    @GetMapping("/broadcast/country")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getCountryWideBroadcasts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getCountryWideBroadcasts(beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Country-wide broadcasts retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get country-wide broadcasts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get country-wide broadcasts", "Internal server error"));
        }
    }

    @GetMapping("/broadcast/state/{state}")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getStateLevelBroadcasts(
            @PathVariable String state,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getStateLevelBroadcasts(state, beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("State-level broadcasts retrieved successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get state-level broadcasts for state: {}", state, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get state-level broadcasts", "Internal server error"));
        }
    }

    @GetMapping("/broadcast/district/{district}")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getDistrictLevelBroadcasts(
            @PathVariable String district,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getDistrictLevelBroadcasts(district, beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("District-level broadcasts retrieved successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get district-level broadcasts for district: {}", district, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get district-level broadcasts", "Internal server error"));
        }
    }

    @GetMapping("/broadcast/area/{pincode}")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getAreaLevelBroadcasts(
            @PathVariable String pincode,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getAreaLevelBroadcasts(pincode, beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Area-level broadcasts retrieved successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get area-level broadcasts for pincode: {}", pincode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get area-level broadcasts", "Internal server error"));
        }
    }

    // ===== Broadcasting Statistics Endpoints =====

    @GetMapping("/broadcast/statistics")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getBroadcastStatistics() {
        try {
            Map<String, Long> statistics = postService.getBroadcastStatistics();
            return ResponseEntity.ok(ApiResponse.success("Broadcast statistics retrieved successfully", statistics));
        } catch (Exception e) {
            log.error("Failed to get broadcast statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get broadcast statistics", "Internal server error"));
        }
    }

    @GetMapping("/broadcast/analytics")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBroadcastAnalytics(
            @RequestParam(defaultValue = "30") int days,
            @CurrentUser User user) {
        try {
            Map<String, Object> analytics = postService.getBroadcastAnalytics(user, days);
            return ResponseEntity.ok(ApiResponse.success("Broadcast analytics retrieved successfully", analytics));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get broadcast analytics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get broadcast analytics", "Internal server error"));
        }
    }

    // ===== Broadcasting Update Endpoints =====

    @PutMapping("/{postId}/broadcast/targets")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> updateBroadcastTargets(
            @PathVariable Long postId,
            @RequestParam BroadcastScope newScope,
            @RequestParam(required = false) List<String> targetStates,
            @RequestParam(required = false) List<String> targetDistricts,
            @RequestParam(required = false) List<String> targetPincodes,
            @CurrentUser User user) {
        try {
            Post updatedPost = postService.updateBroadcastTargets(postId, newScope, targetStates, targetDistricts, targetPincodes, user);
            PostResponse response = postService.convertToPostResponse(updatedPost, user);
            return ResponseEntity.ok(ApiResponse.success("Broadcast targets updated successfully", response));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update broadcast targets for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update broadcast targets", "Internal server error"));
        }
    }

    // ===== Post Management Endpoints =====

    @PutMapping("/{postId}/media")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> updatePostMedia(
            @PathVariable Long postId,
            @RequestParam(value = "media", required = false) MultipartFile mediaFile,
            @CurrentUser User user) {
        try {
            Post updatedPost = postService.updatePostMedia(postId, mediaFile, user);
            PostResponse response = postService.convertToPostResponse(updatedPost, user);
            return ResponseEntity.ok(ApiResponse.success("Post media updated successfully", response));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (MediaValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Media validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update post media for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update post media", "Internal server error"));
        }
    }

    @DeleteMapping("/{postId}/media")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> removePostMedia(
            @PathVariable Long postId,
            @CurrentUser User user) {
        try {
            Post updatedPost = postService.removePostMedia(postId, user);
            PostResponse response = postService.convertToPostResponse(updatedPost, user);
            return ResponseEntity.ok(ApiResponse.success("Post media removed successfully", response));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to remove post media for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to remove post media", "Internal server error"));
        }
    }

    @PutMapping("/{postId}/content")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> updatePostContent(
            @PathVariable Long postId,
            @RequestParam String newContent,
            @CurrentUser User user) {
        try {
            Post updatedPost = postService.updatePostContent(postId, newContent, user);
            PostResponse response = postService.convertToPostResponse(updatedPost, user);
            return ResponseEntity.ok(ApiResponse.success("Post content updated successfully", response));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update post content for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update post content", "Internal server error"));
        }
    }

    @PutMapping("/{postId}/content/dto")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> updatePostContentWithDto(
            @PathVariable Long postId,
            @Valid @RequestBody PostContentUpdateDto contentUpdateDto,
            @CurrentUser User user) {
        try {
            Post updatedPost = postService.updatePostContent(postId, contentUpdateDto, user);
            PostResponse response = postService.convertToPostResponse(updatedPost, user);
            return ResponseEntity.ok(ApiResponse.success("Post content updated successfully", response));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update post content with DTO for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update post content", "Internal server error"));
        }
    }

    @PutMapping("/{postId}/resolution")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> updatePostResolution(
            @PathVariable Long postId,
            @RequestParam Boolean isResolved,
            @RequestParam(required = false) String updateMessage,
            @CurrentUser User user) {
        try {
            Post updatedPost = postService.updatePostResolution(postId, isResolved, user, updateMessage);
            PostResponse response = postService.convertToPostResponse(updatedPost, user);
            return ResponseEntity.ok(ApiResponse.success("Post resolution updated successfully", response));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update post resolution for post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update post resolution", "Internal server error"));
        }
    }

    // ===== Query Endpoints =====

    @GetMapping("/tagged-with-user/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getPostsTaggedWithUser(
            @PathVariable Long userId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getPostsTaggedWithUser(userId, beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Posts tagged with user retrieved successfully", response));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get posts tagged with user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get posts tagged with user", "Internal server error"));
        }
    }

    @GetMapping("/{postId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> getPostById(
            @PathVariable Long postId,
            @CurrentUser User user) {
        try {
            Post post = postService.findById(postId);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.ok(ApiResponse.success("Post retrieved successfully", response));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get post by ID: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get post", "Internal server error"));
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getAllPosts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getAllPosts(beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("All posts retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get all posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get all posts", "Internal server error"));
        }
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getAllActivePosts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getAllActivePosts(beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Active posts retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get all active posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get active posts", "Internal server error"));
        }
    }

    @GetMapping("/resolved")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getAllResolvedPosts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getAllResolvedPosts(beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Resolved posts retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get all resolved posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get resolved posts", "Internal server error"));
        }
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getPostsByUser(
            @PathVariable Long userId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User currentUser) {
        try {
            // For security, we could validate if the user can access another user's posts
            // For now, allowing any authenticated user to view posts by user ID
            // In production, you might want to restrict this based on business rules

            // We need to get the target user, but the service method expects a User object
            // This is a limitation - the service should probably accept userId instead
            // For now, we'll need to modify this based on your UserService availability

            // This endpoint might need UserService to fetch the user first
            // Placeholder implementation:
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(ApiResponse.error("Endpoint requires UserService integration", "Not implemented"));

        } catch (Exception e) {
            log.error("Failed to get posts by user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get posts by user", "Internal server error"));
        }
    }

    @GetMapping("/my-posts")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getMyPosts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getPostsByUser(user, beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Your posts retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get posts by user: {}", user.getActualUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get your posts", "Internal server error"));
        }
    }

    @GetMapping("/my-posts/active")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getMyActivePosts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getActivePostsByUser(user, beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Your active posts retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get active posts by user: {}", user.getActualUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get your active posts", "Internal server error"));
        }
    }

    @GetMapping("/my-posts/resolved")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getMyResolvedPosts(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getResolvedPostsByUser(user, beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Your resolved posts retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get resolved posts by user: {}", user.getActualUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get your resolved posts", "Internal server error"));
        }
    }

    @GetMapping("/count/active")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Long>> countActivePosts() {
        try {
            Long count = postService.countActivePosts();
            return ResponseEntity.ok(ApiResponse.success("Active posts count retrieved successfully", count));
        } catch (Exception e) {
            log.error("Failed to count active posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to count active posts", "Internal server error"));
        }
    }

    @GetMapping("/count/resolved")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Long>> countResolvedPosts() {
        try {
            Long count = postService.countResolvedPosts();
            return ResponseEntity.ok(ApiResponse.success("Resolved posts count retrieved successfully", count));
        } catch (Exception e) {
            log.error("Failed to count resolved posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to count resolved posts", "Internal server error"));
        }
    }

    @GetMapping("/multiple-user-tags")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getPostsWithMultipleUserTags(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getPostsWithMultipleUserTags(beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Posts with multiple user tags retrieved successfully", response));
        } catch (Exception e) {
            log.error("Failed to get posts with multiple user tags", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get posts with multiple user tags", "Internal server error"));
        }
    }

    @GetMapping("/trending")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getTrendingPosts(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser User user) {
        try {
            PaginatedResponse<Post> posts = postService.getTrendingPosts(days, beforeId, limit);
            PaginatedResponse<PostResponse> response = postService.convertPaginatedPostsToResponses(posts, user);
            return ResponseEntity.ok(ApiResponse.success("Trending posts retrieved successfully", response));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get trending posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get trending posts", "Internal server error"));
        }
    }

    // ===== User Tagging Endpoints =====

    @PostMapping("/{postId}/tags/users")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> tagUsersToPost(
            @PathVariable Long postId,
            @RequestBody List<Long> userIds,
            @CurrentUser User user) {
        try {
            Post post = postService.tagUsersToPost(postId, userIds, user);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.ok(ApiResponse.success("Users tagged to post successfully", response));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to tag users to post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to tag users to post", "Internal server error"));
        }
    }

    @DeleteMapping("/{postId}/tags/users/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> removeUserTagFromPost(
            @PathVariable Long postId,
            @PathVariable Long userId,
            @CurrentUser User user) {
        try {
            Post post = postService.removeUserTagFromPost(postId, userId, user);
            PostResponse response = postService.convertToPostResponse(post, user);
            return ResponseEntity.ok(ApiResponse.success("User tag removed from post successfully", response));
        } catch (PostNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Post not found", e.getMessage()));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to remove user tag from post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to remove user tag from post", "Internal server error"));
        }
    }

    // ===== Media Utility Endpoints =====

    @GetMapping("/media/constraints")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMediaConstraints() {
        try {
            Map<String, Object> constraints = postService.getMediaConstraints();
            return ResponseEntity.ok(ApiResponse.success("Media constraints retrieved successfully", constraints));
        } catch (Exception e) {
            log.error("Failed to get media constraints", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get media constraints", "Internal server error"));
        }
    }

    @GetMapping("/media/{fileName}/path")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> getMediaFilePath(
            @PathVariable String fileName) {
        try {
            String filePath = postService.getMediaFilePath(fileName);
            if (filePath == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Media file not found", "File does not exist"));
            }
            return ResponseEntity.ok(ApiResponse.success("Media file path retrieved successfully", filePath));
        } catch (Exception e) {
            log.error("Failed to get media file path: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get media file path", "Internal server error"));
        }
    }

    @GetMapping("/media/{fileName}/type")
    public ResponseEntity<ApiResponse<String>> getMediaType(
            @PathVariable String fileName) {
        try {
            String mediaType = postService.getMediaType(fileName);
            return ResponseEntity.ok(ApiResponse.success("Media type retrieved successfully", mediaType));
        } catch (Exception e) {
            log.error("Failed to get media type for file: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get media type", "Internal server error"));
        }
    }

    @GetMapping("/media/{fileName}/is-image")
    public ResponseEntity<ApiResponse<Boolean>> isImageFile(
            @PathVariable String fileName) {
        try {
            boolean isImage = postService.isImageFile(fileName);
            return ResponseEntity.ok(ApiResponse.success("Image file check completed", isImage));
        } catch (Exception e) {
            log.error("Failed to check if file is image: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to check image file", "Internal server error"));
        }
    }

    @GetMapping("/media/{fileName}/is-video")
    public ResponseEntity<ApiResponse<Boolean>> isVideoFile(
            @PathVariable String fileName) {
        try {
            boolean isVideo = postService.isVideoFile(fileName);
            return ResponseEntity.ok(ApiResponse.success("Video file check completed", isVideo));
        } catch (Exception e) {
            log.error("Failed to check if file is video: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to check video file", "Internal server error"));
        }
    }

    // ===== File Cleanup Endpoints =====

    @PostMapping("/cleanup/files")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> processFileCleanupQueue() {
        try {
            postService.processFileCleanupQueue();
            return ResponseEntity.ok(ApiResponse.success("File cleanup queue processed successfully", "Cleanup completed"));
        } catch (Exception e) {
            log.error("Failed to process file cleanup queue", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process file cleanup queue", "Internal server error"));
        }
    }
}