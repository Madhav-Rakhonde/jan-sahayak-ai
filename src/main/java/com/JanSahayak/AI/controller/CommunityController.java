package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.CommunityDto.CommunityInviteDto.*;
import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.CommunityDto.*;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.service.CommunityInviteService;
import com.JanSahayak.AI.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService       communityService;
    private final CommunityInviteService inviteService;   // ← NEW

    // =========================================================================
    // CRUD
    // =========================================================================

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommunityDetailResponse>> createCommunity(
            @Valid @RequestBody CreateCommunityRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Community created successfully.",
                        communityService.createCommunity(currentUser.getId(), req)));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<CommunityDetailResponse>> getCommunity(
            @PathVariable String slug,
            @AuthenticationPrincipal User currentUser) {
        Long uid = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(ApiResponse.success(communityService.getCommunityDetail(slug, uid)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommunityDetailResponse>> updateCommunity(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommunityRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Community updated.",
                communityService.updateCommunity(id, currentUser.getId(), req)));
    }

    @DeleteMapping("/{id}/archive")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> archiveCommunity(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        communityService.archiveCommunity(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Community archived.", null));
    }

    // =========================================================================
    // FEED-ELIGIBLE TOGGLE
    // =========================================================================

    @PutMapping("/{id}/feed-eligible")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommunityDetailResponse>> setFeedEligible(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            @AuthenticationPrincipal User currentUser) {
        UpdateCommunityRequest req = UpdateCommunityRequest.builder().feedEligible(enabled).build();
        CommunityDetailResponse result = communityService.updateCommunity(id, currentUser.getId(), req);
        String msg = enabled
                ? "Feed surfacing enabled."
                : "Feed surfacing disabled.";
        return ResponseEntity.ok(ApiResponse.success(msg, result));
    }

    // =========================================================================
    // DISCOVERY & SEARCH
    // =========================================================================

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<CommunitySummaryResponse>>> discover(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal User currentUser) {
        Long uid = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(ApiResponse.success(
                communityService.discoverCommunities(uid, cursor, limit)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommunitySummaryResponse>>> search(
            @RequestParam String q,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal User currentUser) {
        Long uid = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(ApiResponse.success(
                communityService.searchCommunities(q, uid, cursor, limit)));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommunitySummaryResponse>>> byCategory(
            @PathVariable String category,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal User currentUser) {
        Long uid = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(ApiResponse.success(
                communityService.getCommunityByCategory(category, uid, cursor, limit)));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommunitySummaryResponse>>> myCommunities(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                communityService.getMyCommunities(currentUser.getId(), cursor, limit)));
    }

    @GetMapping("/owned")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CommunitySummaryResponse>>> ownedCommunities(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                communityService.getOwnedCommunities(currentUser.getId())));
    }

    // =========================================================================
    // COMMUNITY POSTS
    // =========================================================================

    /**
     * GET /api/communities/{id}/posts
     *
     * <p>Returns posts in this community sorted newest-first (default "New" tab).</p>
     *
     * <p>Access:
     * <ul>
     *   <li>PUBLIC community  — open to everyone, including unauthenticated callers</li>
     *   <li>PRIVATE community — authenticated members only (403 otherwise)</li>
     *   <li>SECRET  community — authenticated members only (403 otherwise)</li>
     * </ul>
     * </p>
     *
     * <p>Pagination: cursor-based on post {@code id}. Pass the {@code nextCursor}
     * value from the previous page as the {@code cursor} query param.</p>
     *
     * @param id          community PK
     * @param cursor      exclusive lower-bound post id for the next page (optional)
     * @param limit       page size — clamped to [1, MAX_FEED_LIMIT] server-side
     * @param currentUser injected from JWT; null for unauthenticated requests
     */
    @GetMapping("/{id}/posts")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommunityPostResponse>>> communityPosts(
            @PathVariable Long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal User currentUser) {
        Long uid = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(ApiResponse.success(
                communityService.getCommunityPosts(id, uid, cursor, limit)));
    }

    /**
     * GET /api/communities/{id}/posts/top
     *
     * <p>Returns posts in this community sorted by engagement score ("Hot / Top" tab).</p>
     *
     * <p>Pagination uses a composite cursor of {@code (id, score)} to keep pages
     * stable as scores change. Pass both {@code cursor} and {@code cursorScore}
     * from the previous page's {@code nextCursor} / {@code nextCursorScore} fields.</p>
     *
     * <p>Same access rules as {@code GET /{id}/posts}.</p>
     *
     * @param id          community PK
     * @param cursor      id of the last post on the previous page (optional)
     * @param cursorScore engagement score of the last post on the previous page (optional)
     * @param limit       page size — clamped server-side
     * @param currentUser injected from JWT; null for unauthenticated requests
     */
    @GetMapping("/{id}/posts/top")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommunityPostResponse>>> communityTopPosts(
            @PathVariable Long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Double cursorScore,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal User currentUser) {
        Long uid = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(ApiResponse.success(
                communityService.getCommunityTopPosts(id, uid, cursor, cursorScore, limit)));
    }

    // =========================================================================
    // JOIN / LEAVE
    // =========================================================================

    @PostMapping("/{id}/join")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> join(
            @PathVariable Long id,
            @RequestBody(required = false) JoinCommunityRequest req,
            @AuthenticationPrincipal User currentUser) {
        Map<String, Object> result = communityService.joinCommunity(id, currentUser.getId(), req);
        return ResponseEntity.ok(ApiResponse.success((String) result.get("message"), result));
    }

    @DeleteMapping("/{id}/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> leave(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        communityService.leaveCommunity(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("You have left the community.", null));
    }

    // =========================================================================
    // JOIN REQUESTS
    // =========================================================================

    @GetMapping("/{id}/join-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaginatedResponse<JoinRequestResponse>>> listJoinRequests(
            @PathVariable Long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                communityService.getPendingJoinRequests(id, currentUser.getId(), cursor, limit)));
    }

    @PutMapping("/{communityId}/join-requests/{requestId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> reviewJoinRequest(
            @PathVariable Long communityId,
            @PathVariable Long requestId,
            @RequestBody ReviewJoinRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Request reviewed.",
                communityService.reviewJoinRequest(communityId, requestId, currentUser.getId(), req)));
    }

    @DeleteMapping("/{id}/join-requests/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> cancelJoinRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        communityService.cancelJoinRequest(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Join request cancelled.", null));
    }

    // =========================================================================
    // MEMBERS
    // =========================================================================

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommunityMemberResponse>>> getMembers(
            @PathVariable Long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal User currentUser) {
        Long uid = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(ApiResponse.success(
                communityService.getMembers(id, uid, cursor, limit)));
    }

    @DeleteMapping("/{communityId}/members/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long communityId, @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {
        communityService.removeMember(communityId, userId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Member removed.", null));
    }

    @PutMapping("/{communityId}/members/{userId}/role")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommunityMemberResponse>> updateRole(
            @PathVariable Long communityId, @PathVariable Long userId,
            @RequestBody UpdateMemberRoleRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Role updated.",
                communityService.updateMemberRole(communityId, userId, currentUser.getId(), req)));
    }

    @PutMapping("/{communityId}/members/{userId}/mute")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> mute(
            @PathVariable Long communityId, @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {
        communityService.muteMember(communityId, userId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Member muted.", null));
    }

    @DeleteMapping("/{communityId}/members/{userId}/mute")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> unmute(
            @PathVariable Long communityId, @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {
        communityService.unmuteMember(communityId, userId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Member unmuted.", null));
    }

    @PutMapping("/{communityId}/members/{userId}/ban")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> ban(
            @PathVariable Long communityId, @PathVariable Long userId,
            @RequestBody(required = false) BanMemberRequest req,
            @AuthenticationPrincipal User currentUser) {
        communityService.banMember(communityId, userId, currentUser.getId(), req);
        return ResponseEntity.ok(ApiResponse.success("Member banned.", null));
    }

    @DeleteMapping("/{communityId}/members/{userId}/ban")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> unban(
            @PathVariable Long communityId, @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {
        communityService.unbanMember(communityId, userId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Member unbanned.", null));
    }

    // =========================================================================
    // HEALTH SCORE
    // =========================================================================

    @GetMapping("/{id}/insights")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<HealthInsightResponse>> insights(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                communityService.getHealthInsights(id, currentUser.getId())));
    }

    @PostMapping("/{id}/health/recalculate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> recalculateHealth(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        communityService.triggerHealthRecalculation(id);
        return ResponseEntity.ok(ApiResponse.success("Health score recalculation triggered.", null));
    }

    @PostMapping("/{id}/invites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<InviteResponse>> sendInvite(
            @PathVariable Long id,
            @RequestBody(required = false) SendInviteRequest req,
            @AuthenticationPrincipal User currentUser) {

        // req can be null if admin just wants a blank shareable link
        SendInviteRequest safeReq = req != null ? req : new SendInviteRequest();

        InviteResponse result = inviteService.sendInvite(id, currentUser.getId(), safeReq);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invite created successfully.", result));
    }

    /**
     * GET /api/communities/{id}/invites?cursor=&limit=
     *
     * Lists all PENDING invites for this community.
     * Admin/owner only.
     */
    @GetMapping("/{id}/invites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaginatedResponse<InviteResponse>>> listInvites(
            @PathVariable Long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(ApiResponse.success(
                inviteService.listPendingInvites(id, currentUser.getId(), cursor, limit)));
    }

    /**
     * DELETE /api/communities/{id}/invites/{inviteId}
     *
     * Revoke a pending invite.
     * Admin/owner only.
     */
    @DeleteMapping("/{id}/invites/{inviteId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> revokeInvite(
            @PathVariable Long id,
            @PathVariable Long inviteId,
            @AuthenticationPrincipal User currentUser) {

        inviteService.revokeInvite(id, inviteId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Invite revoked.", null));
    }

    /**
     * GET /api/communities/invites/preview/{token}
     *
     * PUBLIC endpoint — no auth required.
     * Returns community name, description, member count, and whether the token is still valid.
     * Called by the frontend AcceptInvitePage BEFORE the user logs in,
     * so they can see what community they are joining.
     *
     * NOTE: This endpoint is intentionally placed BEFORE /{slug} in the class
     * so Spring does not mistake "invites" for a slug value.
     * The path /api/communities/invites/preview/{token} is unambiguous.
     */
    @GetMapping("/invites/preview/{token}")
    public ResponseEntity<ApiResponse<InvitePreviewResponse>> previewInvite(
            @PathVariable String token) {

        return ResponseEntity.ok(ApiResponse.success(
                inviteService.previewInvite(token)));
    }

    @PostMapping("/invites/accept/{token}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AcceptInviteResponse>> acceptInvite(
            @PathVariable String token,
            @AuthenticationPrincipal User currentUser) {

        AcceptInviteResponse result = inviteService.acceptInvite(token, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(result.getMessage(), result));
    }
}