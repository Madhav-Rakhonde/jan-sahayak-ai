package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.service.Notice_kumbha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
@Slf4j
public class NoticeController {

    private final Notice_kumbha noticeService;

    @GetMapping("/lost-items")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchLostItems(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(defaultValue = "20") @Min(1) Integer limit) {
        log.info("Searching for lost items for user: {}", currentUser.getEmail());
        PaginatedResponse<PostResponse> result = noticeService.searchLostItemsPostsByWildcard(currentUser, beforePostId, limit);
        return ResponseEntity.ok(ApiResponse.success("Lost item posts retrieved successfully", result));
    }

    @GetMapping("/missing-persons")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchMissingPersons(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(defaultValue = "20") @Min(1) Integer limit) {
        log.info("Searching for missing persons for user: {}", currentUser.getEmail());
        PaginatedResponse<PostResponse> result = noticeService.searchMissingPersonPostsByWildcard(currentUser, beforePostId, limit);
        return ResponseEntity.ok(ApiResponse.success("Missing person posts retrieved successfully", result));
    }

    @GetMapping("/theft-issue")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchTheftIssues(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(defaultValue = "20") @Min(1) Integer limit) {
        log.info("Searching for theft issue posts for user: {}", currentUser.getEmail());
        PaginatedResponse<PostResponse> result = noticeService.searchTheftIssuePostsByWildcard(currentUser, beforePostId, limit);
        return ResponseEntity.ok(ApiResponse.success("Theft issue posts retrieved successfully", result));
    }

    @GetMapping("/free-food")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchFreeFood(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(defaultValue = "20") @Min(1) Integer limit) {
        log.info("Searching for free food posts for user: {}", currentUser.getEmail());
        PaginatedResponse<PostResponse> result = noticeService.searchFreeFoodPostsByWildcard(currentUser, beforePostId, limit);
        return ResponseEntity.ok(ApiResponse.success("Free food posts retrieved successfully", result));
    }

    @GetMapping("/health-camps")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchHealthCamps(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(defaultValue = "20") @Min(1) Integer limit) {
        log.info("Searching for health camp posts for user: {}", currentUser.getEmail());
        PaginatedResponse<PostResponse> result = noticeService.searchHealthCampPostsByWildcard(currentUser, beforePostId, limit);
        return ResponseEntity.ok(ApiResponse.success("Health camp posts retrieved successfully", result));
    }

    @GetMapping("/stamped")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchStamped(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(defaultValue = "20") @Min(1) Integer limit) {
        log.info("Searching for stamped posts for user: {}", currentUser.getEmail());
        PaginatedResponse<PostResponse> result = noticeService.searchStampedPostsByWildcard(currentUser, beforePostId, limit);
        return ResponseEntity.ok(ApiResponse.success("Stamped posts retrieved successfully", result));
    }
    @GetMapping("/weather")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostResponse>>> searchweather(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Long beforePostId,
            @RequestParam(defaultValue = "20") @Min(1) Integer limit) {
        log.info("Searching for stamped posts for user: {}", currentUser.getEmail());
        PaginatedResponse<PostResponse> result = noticeService.searchweather(currentUser, beforePostId, limit);
        return ResponseEntity.ok(ApiResponse.success("Stamped posts retrieved successfully", result));
    }
}

