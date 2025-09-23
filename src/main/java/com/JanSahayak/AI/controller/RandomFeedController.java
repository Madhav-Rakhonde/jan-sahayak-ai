package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.service.RandomFeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/public/feed")
@RequiredArgsConstructor
@Slf4j
public class RandomFeedController {

    private final RandomFeedService randomFeedService;

    /**
     * Get random active posts from across all locations in India for anonymous/non-logged-in users
     * Shows only ACTIVE posts from any state, district, or pincode with cursor-based pagination
     * No authentication required - designed for anonymous browsing with infinite scroll
     *
     * GET /api/public/feed/random
     *
     * @param beforeId Cursor for pagination (post ID to start from) - optional
     * @param limit Maximum number of posts to return (optional, defaults to 20, max 50)
     * @return PaginatedResponse of random active posts from various locations across India
     */
    @GetMapping("/random")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> getRandomActiveFeedForAnonymous(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer limit) {

        try {
            log.info("Getting random active feed for anonymous user with beforeId: {}, limit: {}",
                    beforeId, limit);

            PaginatedResponse<PostResponse> result = randomFeedService.getRandomActiveFeedForAnonymous(
                    beforeId, limit);

            log.info("Successfully retrieved {} random posts for anonymous user, hasMore: {}",
                    result.getData().size(), result.isHasMore());

            return ResponseEntity.ok(ApiResponse.success(
                    "Random feed retrieved successfully", result));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid arguments for anonymous random feed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Invalid request parameters", e.getMessage()));

        } catch (ServiceException e) {
            log.error("Service error while getting anonymous random feed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Service error", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error while getting anonymous random feed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error",
                            "An unexpected error occurred while retrieving the feed"));
        }
    }
}