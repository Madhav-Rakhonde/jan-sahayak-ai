package com.JanSahayak.AI.controller;


import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.DTO.PostResolutionDto;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.service.EnhancedFeedService;
import com.JanSahayak.AI.service.PostService;
import com.JanSahayak.AI.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Slf4j
public class EnhancedFeedController {

    private final EnhancedFeedService enhancedFeedService;
    private final PostService postService;
    private final UserService userService;

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getPersonalizedFeed(
            @RequestParam(defaultValue = "false") Boolean includeResolved,
            @RequestParam(defaultValue = "20") Integer limit,
            Authentication authentication) {

        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            List<PostResponse> feed = enhancedFeedService.getUniversalFeed(currentUser, includeResolved, limit);

            log.info("Retrieved {} posts for universal feed for user: {} with role: {}",
                    feed.size(), currentUser.getUsername(), currentUser.getRole().getName());

            return ResponseEntity.ok(ApiResponse.success("Personalized feed retrieved successfully", feed));
        } catch (Exception e) {
            log.error("Error retrieving universal feed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve feed"));
        }
    }

    @GetMapping("/feed/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFeedStats(Authentication authentication) {
        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            Map<String, Object> stats = enhancedFeedService.getFeedStatistics(currentUser);

            return ResponseEntity.ok(ApiResponse.success("Feed statistics retrieved successfully", stats));
        } catch (Exception e) {
            log.error("Error retrieving feed statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve statistics"));
        }
    }

    @GetMapping("/feed/user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getNormalUserFeed(
            @RequestParam(defaultValue = "false") Boolean includeResolved,
            @RequestParam(defaultValue = "20") Integer limit,
            Authentication authentication) {

        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            List<PostResponse> feed = enhancedFeedService.getNormalUserFeed(currentUser, includeResolved, limit);

            return ResponseEntity.ok(ApiResponse.success("User feed retrieved successfully", feed));
        } catch (Exception e) {
            log.error("Error retrieving normal user feed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve user feed"));
        }
    }

    @GetMapping("/feed/department")
    @PreAuthorize("hasRole('DEPARTMENT')")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getDepartmentFeed(
            @RequestParam(defaultValue = "false") Boolean includeResolved,
            @RequestParam(defaultValue = "20") Integer limit,
            Authentication authentication) {

        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            List<PostResponse> feed = enhancedFeedService.getDepartmentFeed(currentUser, includeResolved, limit);

            return ResponseEntity.ok(ApiResponse.success("Department feed retrieved successfully", feed));
        } catch (Exception e) {
            log.error("Error retrieving department feed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve department feed"));
        }
    }

    @GetMapping("/department/tagged-posts")
    @PreAuthorize("hasRole('DEPARTMENT')")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getDepartmentTaggedPosts(
            @RequestParam(defaultValue = "false") Boolean includeResolved,
            @RequestParam(defaultValue = "20") Integer limit,
            Authentication authentication) {

        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            List<PostResponse> posts = enhancedFeedService.getDepartmentTaggedPosts(currentUser, includeResolved, limit);

            log.info("Retrieved {} tagged posts for department: {}", posts.size(), currentUser.getUsername());
            return ResponseEntity.ok(ApiResponse.success("Tagged posts retrieved successfully", posts));
        } catch (Exception e) {
            log.error("Error retrieving department tagged posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve tagged posts"));
        }
    }

    @GetMapping("/department/resolution-posts")
    @PreAuthorize("hasRole('DEPARTMENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDepartmentResolutionPosts(
            @RequestParam(defaultValue = "20") Integer limit,
            Authentication authentication) {

        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            Map<String, Object> resolutionData = enhancedFeedService.getDepartmentResolutionPosts(currentUser, limit);

            return ResponseEntity.ok(ApiResponse.success("Resolution posts retrieved successfully", resolutionData));
        } catch (Exception e) {
            log.error("Error retrieving department resolution posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve resolution posts"));
        }
    }

    @PutMapping("/{postId}/resolve")
    @PreAuthorize("hasRole('DEPARTMENT') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> markPostAsResolved(
            @PathVariable Long postId,
            @RequestBody @Valid PostResolutionDto resolutionDto,
            Authentication authentication) {

        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            Post resolvedPost = postService.updatePostResolution(postId, true, currentUser, resolutionDto.getResolutionMessage());

            PostResponse response = enhancedFeedService.convertToPostResponse(resolvedPost, currentUser);

            log.info("Post {} marked as resolved by department user: {}", postId, currentUser.getUsername());
            return ResponseEntity.ok(ApiResponse.success("Post marked as resolved successfully", response));
        } catch (SecurityException e) {
            log.error("Unauthorized attempt to resolve post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied"));
        } catch (Exception e) {
            log.error("Error resolving post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to resolve post"));
        }
    }

    @PutMapping("/{postId}/unresolve")
    @PreAuthorize("hasRole('DEPARTMENT') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> markPostAsUnresolved(
            @PathVariable Long postId,
            @RequestBody(required = false) PostResolutionDto resolutionDto,
            Authentication authentication) {

        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            String message = resolutionDto != null ? resolutionDto.getResolutionMessage() : "Post reopened for further review";
            Post unresolvedPost = postService.updatePostResolution(postId, false, currentUser, message);

            PostResponse response = enhancedFeedService.convertToPostResponse(unresolvedPost, currentUser);

            log.info("Post {} marked as unresolved by department user: {}", postId, currentUser.getUsername());
            return ResponseEntity.ok(ApiResponse.success("Post marked as unresolved successfully", response));
        } catch (SecurityException e) {
            log.error("Unauthorized attempt to unresolve post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied"));
        } catch (Exception e) {
            log.error("Error unresolving post: {}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to unresolve post"));
        }
    }

    @GetMapping("/department/dashboard")
    @PreAuthorize("hasRole('DEPARTMENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDepartmentDashboard(
            @RequestParam(defaultValue = "false") Boolean includeResolved,
            Authentication authentication) {

        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            Map<String, Object> dashboard = enhancedFeedService.getDepartmentDashboard(currentUser, includeResolved);

            return ResponseEntity.ok(ApiResponse.success("Department dashboard retrieved successfully", dashboard));
        } catch (Exception e) {
            log.error("Error retrieving department dashboard", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve dashboard"));
        }
    }

    @GetMapping("/department/stats")
    @PreAuthorize("hasRole('DEPARTMENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDepartmentStats(Authentication authentication) {
        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            Map<String, Object> stats = enhancedFeedService.getDepartmentStatistics(currentUser);

            return ResponseEntity.ok(ApiResponse.success("Department statistics retrieved successfully", stats));
        } catch (Exception e) {
            log.error("Error retrieving department statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve statistics"));
        }
    }

    @GetMapping("/department/location-posts")
    @PreAuthorize("hasRole('DEPARTMENT')")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getDepartmentLocationPosts(
            @RequestParam(defaultValue = "false") Boolean includeResolved,
            @RequestParam(defaultValue = "20") Integer limit,
            Authentication authentication) {

        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            List<PostResponse> posts = enhancedFeedService.getDepartmentLocationPosts(currentUser, includeResolved, limit);

            return ResponseEntity.ok(ApiResponse.success("Location posts retrieved successfully", posts));
        } catch (Exception e) {
            log.error("Error retrieving department location posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve location posts"));
        }
    }

    @GetMapping("/department/state-posts")
    @PreAuthorize("hasRole('DEPARTMENT')")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getDepartmentStatePosts(
            @RequestParam(defaultValue = "false") Boolean includeResolved,
            @RequestParam(defaultValue = "30") Integer limit,
            Authentication authentication) {

        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            List<PostResponse> posts = enhancedFeedService.getDepartmentStatePosts(currentUser, includeResolved, limit);

            return ResponseEntity.ok(ApiResponse.success("State posts retrieved successfully", posts));
        } catch (Exception e) {
            log.error("Error retrieving department state posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve state posts"));
        }
    }

    @GetMapping("/department/country-posts")
    @PreAuthorize("hasRole('DEPARTMENT')")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getDepartmentCountryPosts(
            @RequestParam(defaultValue = "false") Boolean includeResolved,
            @RequestParam(defaultValue = "50") Integer limit,
            Authentication authentication) {

        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            List<PostResponse> posts = enhancedFeedService.getDepartmentCountryPosts(currentUser, includeResolved, limit);

            return ResponseEntity.ok(ApiResponse.success("Country posts retrieved successfully", posts));
        } catch (Exception e) {
            log.error("Error retrieving department country posts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve country posts"));
        }
    }

    @GetMapping("/feed/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getAdminFeed(
            @RequestParam(defaultValue = "false") Boolean includeResolved,
            @RequestParam(defaultValue = "50") Integer limit,
            Authentication authentication) {

        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            List<PostResponse> feed = enhancedFeedService.getAdminFeed(currentUser, includeResolved, limit);

            return ResponseEntity.ok(ApiResponse.success("Admin feed retrieved successfully", feed));
        } catch (Exception e) {
            log.error("Error retrieving admin feed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve admin feed"));
        }
    }

    @GetMapping("/feed/location")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getLocationBasedFeed(
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "200") Double maxDistanceKm,
            @RequestParam(defaultValue = "false") Boolean includeResolved,
            @RequestParam(defaultValue = "20") Integer limit,
            Authentication authentication) {

        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            String targetLocation = location != null ? location : currentUser.getLocation();

            List<PostResponse> feed = enhancedFeedService.getLocationBasedFeed(
                    currentUser, targetLocation, maxDistanceKm, includeResolved, limit);

            return ResponseEntity.ok(ApiResponse.success("Location-based feed retrieved successfully", feed));
        } catch (Exception e) {
            log.error("Error retrieving location-based feed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve location-based feed"));
        }
    }
}