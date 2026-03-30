package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.CommunityDto.*;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.payload.CommunityValidationUtil;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.payload.PaginationUtils.PaginationSetup;
import com.JanSahayak.AI.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommunityService {

    private final CommunityRepo                 communityRepo;
    private final CommunityMemberRepo           memberRepo;
    private final CommunityJoinRequestRepo      joinRequestRepo;
    private final UserRepo                      userRepo;
    private final SocialPostRepo                socialPostRepo;
    private final CommunityHealthScoreService   healthScoreService;
    private final HyperlocalSeedService         hyperlocalSeedService;   // ← LOCATION PATCH

    // ── HLIG v2: Interest profile service ────────────────────────────────────
    // @Lazy breaks circular dependency:
    //   CommunityService → InterestProfileService → UserInterestProfileRepo
    //   InterestProfileService does NOT inject CommunityService, so this is safe.
    @Lazy
    @Autowired
    private InterestProfileService interestProfileService;

    // ── Feed surfacing thresholds (used by feed query callers) ────────────────

    // =========================================================================
    // 1. CREATE / UPDATE / ARCHIVE
    // =========================================================================

    public CommunityDetailResponse createCommunity(Long creatorId, CreateCommunityRequest req) {
        CommunityValidationUtil.validateUserId(creatorId);
        CommunityValidationUtil.validateCommunityName(req.getName());
        CommunityValidationUtil.validateCommunityDescription(req.getDescription());

        User creator = findUserOrThrow(creatorId);
        CommunityValidationUtil.validateUser(creator);

        if (communityRepo.existsByName(req.getName())) {
            throw new IllegalArgumentException("A community named '" + req.getName() + "' already exists.");
        }

        Community.CommunityPrivacy privacy =
                req.getPrivacy() != null ? req.getPrivacy() : Community.CommunityPrivacy.PUBLIC;

        boolean defaultFeedEligible = Community.CommunityPrivacy.PUBLIC.equals(privacy);
        boolean feedEligible;
        if (req.getFeedEligible() != null) {
            if (Boolean.TRUE.equals(req.getFeedEligible()) && !Community.CommunityPrivacy.PUBLIC.equals(privacy)) {
                throw new ValidationException("Only PUBLIC communities can have feed surfacing enabled.");
            }
            feedEligible = req.getFeedEligible();
        } else {
            feedEligible = defaultFeedEligible;
        }

        Community community = Community.builder()
                .name(req.getName())
                .description(req.getDescription())
                .category(req.getCategory())
                .tags(CommunityValidationUtil.normalizeTags(req.getTags()))
                .privacy(privacy)
                .feedEligible(feedEligible)
                .locationRestricted(Boolean.TRUE.equals(req.getLocationRestricted()))
                .allowMemberPosts(req.getAllowMemberPosts() == null || req.getAllowMemberPosts())
                .requirePostApproval(Boolean.TRUE.equals(req.getRequirePostApproval()))
                .allowAnonymousPosts(true)
                .owner(creator)
                .memberCount(1)
                .build();

        // ── LOCATION PATCH ────────────────────────────────────────────────────
        if (Boolean.TRUE.equals(req.getLocationRestricted()) && creator.hasPincode()) {
            community.inheritLocationFromUser(creator);
            HyperlocalSeedService.LocationData loc =
                    hyperlocalSeedService.resolveLocationData(creator.getPincode());
            if (loc != null) {
                community.setLocationName(loc.shortLabel());
                log.info("Community '{}' location enriched from pincode_lookup: {}",
                        community.getName(), loc.displayLocation());
            } else {
                log.warn("Pincode {} not found in pincode_lookup — locationName not set for community",
                        creator.getPincode());
            }
        }
        // ── END LOCATION PATCH ────────────────────────────────────────────────

        communityRepo.save(community);

        memberRepo.save(CommunityMember.builder()
                .community(community).user(creator)
                .memberRole(CommunityMember.MemberRole.ADMIN)
                .build());

        log.info("Community '{}' (id={}) created by user {} — feedEligible={}",
                community.getName(), community.getId(), creatorId, community.isFeedEligible());

        return toDetailResponse(community, creatorId);
    }

    public CommunityDetailResponse updateCommunity(Long communityId, Long requesterId,
                                                   UpdateCommunityRequest req) {
        CommunityValidationUtil.validateCommunityId(communityId);
        CommunityValidationUtil.validateUserId(requesterId);

        Community community = findCommunityOrThrow(communityId);
        assertAdminOrOwner(community, requesterId);

        Community.CommunityPrivacy oldPrivacy     = community.getPrivacy();
        boolean                    oldFeedEligible = Boolean.TRUE.equals(community.getFeedEligible());

        // F7: Handle community name update — guard against duplicate names
        if (req.getName() != null && !req.getName().equals(community.getName())) {
            if (communityRepo.existsByName(req.getName())) {
                throw new IllegalArgumentException("A community named '" + req.getName() + "' already exists.");
            }
            community.setName(req.getName());
        }

        if (req.getDescription()         != null) community.setDescription(req.getDescription());
        if (req.getCategory()            != null) community.setCategory(req.getCategory());
        if (req.getTags()                != null) community.setTags(CommunityValidationUtil.normalizeTags(req.getTags()));
        if (req.getCoverImageUrl()       != null) community.setCoverImageUrl(req.getCoverImageUrl());
        if (req.getAvatarUrl()           != null) community.setAvatarUrl(req.getAvatarUrl());
        if (req.getPrivacy()             != null) community.setPrivacy(req.getPrivacy());
        if (req.getRequirePostApproval() != null) community.setRequirePostApproval(req.getRequirePostApproval());

        // ── LOCATION PATCH (Step 3) ───────────────────────────────────────────
        if (req.getLocationRestricted() != null) {
            community.setLocationRestricted(req.getLocationRestricted());
            if (Boolean.TRUE.equals(req.getLocationRestricted()) && community.getLocationName() == null) {
                User owner = community.getOwner();
                if (owner != null && owner.hasPincode()) {
                    HyperlocalSeedService.LocationData loc =
                            hyperlocalSeedService.resolveLocationData(owner.getPincode());
                    if (loc != null) {
                        community.setLocationName(loc.shortLabel());
                        log.info("Community '{}' locationName enriched on update: {}",
                                community.getName(), loc.displayLocation());
                    }
                }
            }
        }
        // ── END LOCATION PATCH ────────────────────────────────────────────────

        if (req.getFeedEligible() != null) {
            if (!community.isPublic() && Boolean.TRUE.equals(req.getFeedEligible())) {
                throw new ValidationException("Only PUBLIC communities can have feed surfacing enabled.");
            }
            community.setFeedEligible(req.getFeedEligible());
        }

        communityRepo.save(community);

        boolean privacyChanged     = !community.getPrivacy().equals(oldPrivacy);
        boolean eligibilityChanged = Boolean.TRUE.equals(community.getFeedEligible()) != oldFeedEligible;
        if (privacyChanged || eligibilityChanged) {
            syncPostDenormalizedFields(communityId, community);
        }

        return toDetailResponse(community, requesterId);
    }

    public void archiveCommunity(Long communityId, Long requesterId) {
        Community community = findCommunityOrThrow(communityId);
        if (!community.isOwnedBy(requesterId)) {
            throw new SecurityException("Only the community owner can archive it.");
        }
        community.setStatus(Community.CommunityStatus.ARCHIVED);
        communityRepo.save(community);
        socialPostRepo.removeCommunityPostsFromFeed(communityId);
        log.info("Community {} archived — posts removed from main feed", communityId);
    }

    // =========================================================================
    // 2. JOIN / LEAVE
    // =========================================================================

    public Map<String, Object> joinCommunity(Long communityId, Long userId, JoinCommunityRequest req) {
        CommunityValidationUtil.validateCommunityId(communityId);
        CommunityValidationUtil.validateUserId(userId);

        Community community = findCommunityOrThrow(communityId);
        CommunityValidationUtil.assertCommunityActive(community);

        if (community.isSecret()) {
            throw new ValidationException("SECRET communities require an invitation to join.");
        }
        if (memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(communityId, userId)) {
            throw new ValidationException("You are already a member of this community.");
        }
        if (joinRequestRepo.existsByCommunityIdAndUserIdAndStatus(
                communityId, userId, CommunityJoinRequest.RequestStatus.PENDING)) {
            throw new ValidationException("You already have a pending join request.");
        }

        User user = findUserOrThrow(userId);
        CommunityValidationUtil.validateUser(user);

        if (community.isPublic()) {
            CommunityMember member = CommunityMember.builder()
                    .community(community).user(user)
                    .memberRole(CommunityMember.MemberRole.MEMBER).build();
            memberRepo.save(member);
            communityRepo.incrementMemberCount(communityId);
            communityRepo.incrementNewMembersLast7d(communityId);

            // ── HLIG v2: Seed interest profile from community category ─────────
            // Joining a community is a strong onboarding signal — seed the user's
            // interest profile so they skip cold-start and see relevant posts
            // immediately. @Async in InterestProfileService — never blocks here.
            if (community.getCategory() != null) {
                try {
                    interestProfileService.seedFromOnboarding(userId, List.of(community.getCategory()));
                    log.debug("[HLIG] seedFromOnboarding: userId={} category={}", userId, community.getCategory());
                } catch (Exception e) {
                    log.warn("[HLIG] seedFromOnboarding failed: userId={} category={} reason={}",
                            userId, community.getCategory(), e.getMessage());
                }
            }
            // ── END HLIG v2 ───────────────────────────────────────────────────

            return Map.of("joined", true, "message", "Joined successfully.");
        } else {
            // PRIVATE community → create join request
            CommunityJoinRequest jr = CommunityJoinRequest.builder()
                    .community(community).user(user)
                    .message(req != null ? req.getMessage() : null).build();
            joinRequestRepo.save(jr);
            return Map.of("joined", false,
                    "message", "Join request submitted. Awaiting moderator approval.");
        }
    }

    public void leaveCommunity(Long communityId, Long userId) {
        Community community = findCommunityOrThrow(communityId);
        if (community.isOwnedBy(userId)) {
            throw new ValidationException("Owner cannot leave. Archive the community first.");
        }
        memberRepo.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ValidationException("You are not a member of this community."));
        memberRepo.deactivateMember(communityId, userId);
        communityRepo.decrementMemberCount(communityId);
    }

    // =========================================================================
    // 3. JOIN REQUEST MANAGEMENT
    // =========================================================================

    public JoinRequestResponse reviewJoinRequest(Long communityId, Long requestId,
                                                 Long reviewerId, ReviewJoinRequest req) {
        Community community = findCommunityOrThrow(communityId);
        assertModeratorOrAbove(community, reviewerId);

        CommunityJoinRequest jr = joinRequestRepo.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Join request not found: " + requestId));
        if (!jr.getCommunity().getId().equals(communityId)) {
            throw new IllegalArgumentException("Request does not belong to this community.");
        }
        if (!jr.isPending()) throw new ValidationException("This request has already been reviewed.");

        if (req.isApprove()) {
            jr.approve(reviewerId);
            joinRequestRepo.save(jr);
            memberRepo.save(CommunityMember.builder()
                    .community(community).user(jr.getUser())
                    .memberRole(CommunityMember.MemberRole.MEMBER).build());
            communityRepo.incrementMemberCount(communityId);
            communityRepo.incrementNewMembersLast7d(communityId);

            // HLIG v2: also seed when a join REQUEST is approved (PRIVATE community path)
            if (community.getCategory() != null && jr.getUser() != null) {
                try {
                    interestProfileService.seedFromOnboarding(
                            jr.getUser().getId(), List.of(community.getCategory()));
                } catch (Exception e) {
                    log.warn("[HLIG] seedFromOnboarding (approved) failed: userId={} reason={}",
                            jr.getUser().getId(), e.getMessage());
                }
            }
        } else {
            jr.reject(reviewerId, req.getRejectionReason());
            joinRequestRepo.save(jr);
        }
        return toJoinRequestResponse(jr);
    }

    public void cancelJoinRequest(Long communityId, Long userId) {
        CommunityJoinRequest jr = joinRequestRepo.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new NoSuchElementException("No join request found for this community."));
        if (!jr.isPending()) throw new ValidationException("Only PENDING requests can be cancelled.");
        jr.cancel();
        joinRequestRepo.save(jr);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<JoinRequestResponse> getPendingJoinRequests(
            Long communityId, Long requesterId, Long cursor, Integer limit) {
        assertModeratorOrAbove(findCommunityOrThrow(communityId), requesterId);

        PaginationSetup setup = PaginationUtils.setupPagination("getPendingJoinRequests", cursor, limit,
                Constant.DEFAULT_JOIN_REQUEST_LIMIT,
                Constant.MAX_JOIN_REQUEST_LIMIT);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);

        List<CommunityJoinRequest> raw = joinRequestRepo.findPendingRequestsCursor(
                communityId, setup.getSanitizedCursor(), pageable);
        List<JoinRequestResponse> mapped = raw.stream()
                .map(this::toJoinRequestResponse).collect(Collectors.toList());

        return PaginationUtils.createIdBasedResponse(mapped, setup.getValidatedLimit(),
                jr -> raw.get(mapped.indexOf(jr)).getId());
    }

    // =========================================================================
    // 4. MEMBER MANAGEMENT
    // =========================================================================

    public CommunityMemberResponse updateMemberRole(Long communityId, Long targetUserId,
                                                    Long requesterId, UpdateMemberRoleRequest req) {
        Community community = findCommunityOrThrow(communityId);
        assertAdminOrOwner(community, requesterId);
        if (community.isOwnedBy(targetUserId)) throw new ValidationException("Cannot change the owner's role.");
        CommunityMember m = findMemberOrThrow(communityId, targetUserId);
        m.setMemberRole(req.getNewRole());
        m.setRoleUpdatedAt(new Date());
        memberRepo.save(m);
        return toMemberResponse(m);
    }

    public void muteMember(Long communityId, Long targetUserId, Long requesterId) {
        assertModeratorOrAbove(findCommunityOrThrow(communityId), requesterId);
        CommunityMember m = findMemberOrThrow(communityId, targetUserId);
        m.setIsMuted(true); memberRepo.save(m);
    }

    public void unmuteMember(Long communityId, Long targetUserId, Long requesterId) {
        assertModeratorOrAbove(findCommunityOrThrow(communityId), requesterId);
        CommunityMember m = findMemberOrThrow(communityId, targetUserId);
        m.setIsMuted(false); memberRepo.save(m);
    }

    public void banMember(Long communityId, Long targetUserId, Long requesterId, BanMemberRequest req) {
        Community community = findCommunityOrThrow(communityId);
        assertModeratorOrAbove(community, requesterId);
        if (community.isOwnedBy(targetUserId)) throw new ValidationException("Cannot ban the community owner.");
        CommunityMember m = findMemberOrThrow(communityId, targetUserId);
        m.ban(req != null ? req.getReason() : null);
        memberRepo.save(m);
        communityRepo.decrementMemberCount(communityId);
    }

    public void unbanMember(Long communityId, Long targetUserId, Long requesterId) {
        assertAdminOrOwner(findCommunityOrThrow(communityId), requesterId);
        CommunityMember m = findMemberOrThrow(communityId, targetUserId);
        m.unban(); memberRepo.save(m);
        communityRepo.incrementMemberCount(communityId);
    }

    public void removeMember(Long communityId, Long targetUserId, Long requesterId) {
        Community community = findCommunityOrThrow(communityId);
        assertModeratorOrAbove(community, requesterId);
        if (community.isOwnedBy(targetUserId)) throw new ValidationException("Cannot remove the community owner.");
        memberRepo.deactivateMember(communityId, targetUserId);
        communityRepo.decrementMemberCount(communityId);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityMemberResponse> getMembers(
            Long communityId, Long requesterId, Long cursor, Integer limit) {
        Community community = findCommunityOrThrow(communityId);
        if (!community.isPublic() && !isMember(communityId, requesterId)) {
            throw new SecurityException("You must be a member to view the member list.");
        }
        PaginationSetup setup = PaginationUtils.setupPagination("getMembers", cursor, limit,
                Constant.DEFAULT_MEMBER_LIST_LIMIT,
                Constant.MAX_MEMBER_LIST_LIMIT);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);

        List<CommunityMember> raw = memberRepo.findActiveMembersCursor(
                communityId, setup.getSanitizedCursor(), pageable);
        List<CommunityMemberResponse> mapped = raw.stream()
                .map(this::toMemberResponse).collect(Collectors.toList());

        return PaginationUtils.createIdBasedResponse(mapped, setup.getValidatedLimit(),
                mr -> raw.get(mapped.indexOf(mr)).getId());
    }

    // =========================================================================
    // 5. COMMUNITY POST FEEDS
    // =========================================================================

    /**
     * Returns the newest-first post feed for a community (the default "New" tab).
     *
     * <p>Access rules:
     * <ul>
     *   <li>PUBLIC  → open to everyone, including unauthenticated visitors</li>
     *   <li>PRIVATE → members only (non-members get 403)</li>
     *   <li>SECRET  → members only (non-members get 403; community existence is
     *                 already hidden from discovery, so 403 is safe here)</li>
     * </ul>
     * </p>
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityPostResponse> getCommunityPosts(
            Long communityId, Long requesterId, Long cursor, Integer limit) {

        Community community = findCommunityOrThrow(communityId);
        assertPostReadAccess(community, requesterId);

        PaginationSetup setup = PaginationUtils.setupSocialPostFeedPagination(
                "getCommunityPosts", cursor, limit);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);

        List<SocialPost> raw = socialPostRepo.findCommunityPostsCursor(
                communityId, setup.getSanitizedCursor(), pageable);

        List<CommunityPostResponse> mapped = raw.stream()
                .map(p -> toPostResponse(p, requesterId))
                .collect(Collectors.toList());

        return PaginationUtils.createIdBasedResponse(
                mapped, setup.getValidatedLimit(),
                dto -> raw.get(mapped.indexOf(dto)).getId());
    }

    /**
     * Returns community posts sorted by engagement score — the "Top / Hot" sort.
     *
     * <p>Uses a composite cursor of {@code (id, engagementScore)} so that
     * pages are stable even as scores change between requests.</p>
     *
     * <p>Same access rules as {@link #getCommunityPosts}.</p>
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<CommunityPostResponse> getCommunityTopPosts(
            Long communityId, Long requesterId, Long cursor, Double cursorScore, Integer limit) {

        Community community = findCommunityOrThrow(communityId);
        assertPostReadAccess(community, requesterId);

        PaginationSetup setup = PaginationUtils.setupSocialPostFeedPagination(
                "getCommunityTopPosts", cursor, limit);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);

        List<SocialPost> raw = socialPostRepo.findCommunityPostsByEngagement(
                communityId,
                setup.getSanitizedCursor(),
                cursorScore != null ? cursorScore : Double.MAX_VALUE,
                pageable);

        List<CommunityPostResponse> mapped = raw.stream()
                .map(p -> toPostResponse(p, requesterId))
                .collect(Collectors.toList());

        return PaginationUtils.createIdBasedResponse(
                mapped, setup.getValidatedLimit(),
                dto -> raw.get(mapped.indexOf(dto)).getId());
    }

    // =========================================================================
    // 6. DISCOVERY & SEARCH
    // =========================================================================

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunitySummaryResponse> discoverCommunities(
            Long requesterId, Long cursor, Integer limit) {
        PaginationSetup setup = PaginationUtils.setupPagination("discoverCommunities", cursor, limit,
                Constant.DEFAULT_COMMUNITY_LIST_LIMIT,
                Constant.MAX_COMMUNITY_LIST_LIMIT);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);

        User user = (requesterId != null) ? userRepo.findById(requesterId).orElse(null) : null;
        List<Community> raw = (user != null && user.hasPincode())
                ? communityRepo.findDiscoverableForUser(
                user.getPincode(), user.getDistrictPrefix(), user.getStatePrefix(),
                setup.getSanitizedCursor(), pageable)
                : communityRepo.findDiscoverable(setup.getSanitizedCursor(), pageable);

        List<CommunitySummaryResponse> mapped = raw.stream()
                .map(c -> toSummaryResponse(c, requesterId)).collect(Collectors.toList());
        return PaginationUtils.createIdBasedResponse(mapped, setup.getValidatedLimit(),
                cr -> raw.get(mapped.indexOf(cr)).getId());
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunitySummaryResponse> searchCommunities(
            String query, Long requesterId, Long cursor, Integer limit) {
        if (query == null || query.isBlank()) throw new ValidationException("Search query cannot be empty.");
        PaginationSetup setup = PaginationUtils.setupPagination("searchCommunities", cursor, limit,
                Constant.DEFAULT_COMMUNITY_LIST_LIMIT,
                Constant.MAX_COMMUNITY_LIST_LIMIT);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
        List<Community> raw = communityRepo.searchCommunities(query.trim(), setup.getSanitizedCursor(), pageable);
        List<CommunitySummaryResponse> mapped = raw.stream()
                .map(c -> toSummaryResponse(c, requesterId)).collect(Collectors.toList());
        return PaginationUtils.createIdBasedResponse(mapped, setup.getValidatedLimit(),
                cr -> raw.get(mapped.indexOf(cr)).getId());
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunitySummaryResponse> getCommunityByCategory(
            String category, Long requesterId, Long cursor, Integer limit) {
        PaginationSetup setup = PaginationUtils.setupPagination("getCommunityByCategory", cursor, limit,
                Constant.DEFAULT_COMMUNITY_LIST_LIMIT,
                Constant.MAX_COMMUNITY_LIST_LIMIT);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
        List<Community> raw = communityRepo.findByCategory(category, setup.getSanitizedCursor(), pageable);
        List<CommunitySummaryResponse> mapped = raw.stream()
                .map(c -> toSummaryResponse(c, requesterId)).collect(Collectors.toList());
        return PaginationUtils.createIdBasedResponse(mapped, setup.getValidatedLimit(),
                cr -> raw.get(mapped.indexOf(cr)).getId());
    }

    @Transactional(readOnly = true)
    public CommunityDetailResponse getCommunityDetail(String slug, Long requesterId) {
        Community community = communityRepo.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("Community not found: " + slug));
        if (community.isSecret() && !isMember(community.getId(), requesterId)) {
            throw new SecurityException("This is a secret community. You must be invited to view it.");
        }
        return toDetailResponse(community, requesterId);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CommunitySummaryResponse> getMyCommunities(
            Long userId, Long cursor, Integer limit) {
        CommunityValidationUtil.validateUserId(userId);
        PaginationSetup setup = PaginationUtils.setupPagination("getMyCommunities", cursor, limit,
                Constant.DEFAULT_COMMUNITY_LIST_LIMIT,
                Constant.MAX_COMMUNITY_LIST_LIMIT);
        Pageable pageable = PaginationUtils.createPageable(setup.getValidatedLimit() + 1);
        List<CommunityMember> raw = memberRepo.findUserCommunitiesCursor(
                userId, setup.getSanitizedCursor(), pageable);
        List<CommunitySummaryResponse> mapped = raw.stream()
                .map(cm -> toSummaryResponse(cm.getCommunity(), userId)).collect(Collectors.toList());
        return PaginationUtils.createIdBasedResponse(mapped, setup.getValidatedLimit(),
                cr -> raw.get(mapped.indexOf(cr)).getId());
    }

    @Transactional(readOnly = true)
    public List<CommunitySummaryResponse> getOwnedCommunities(Long ownerId) {
        CommunityValidationUtil.validateUserId(ownerId);
        return communityRepo.findByOwnerIdAndStatusNot(ownerId, Community.CommunityStatus.DELETED)
                .stream().map(c -> toSummaryResponse(c, ownerId)).collect(Collectors.toList());
    }

    // =========================================================================
    // 7. EVENT HOOKS (called by other services)
    // =========================================================================

    /**
     * Looks up a Community for use during post creation and validates that the
     * requesting user is allowed to post in it.
     *
     * Rules enforced:
     *  - Community must exist and be ACTIVE.
     *  - Community must allow member posts (allowMemberPosts = true).
     *  - User must be an active member of the community.
     *
     * Returns an Optional so the caller can use ifPresent() without a try/catch.
     * Throws ValidationException / SecurityException directly so the error message
     * reaches the client instead of being swallowed.
     */
    public Optional<Community> findCommunityForPost(Long communityId, User user) {
        CommunityValidationUtil.validateCommunityId(communityId);

        Community community = communityRepo.findById(communityId)
                .orElseThrow(() -> new ValidationException(
                        "Community not found with id: " + communityId));

        CommunityValidationUtil.assertCommunityActive(community);

        if (!Boolean.TRUE.equals(community.getAllowMemberPosts())) {
            throw new ValidationException(
                    "This community does not allow member posts.");
        }

        if (!isMember(communityId, user.getId())) {
            throw new SecurityException(
                    "You must be a member of this community to post in it.");
        }

        return Optional.of(community);
    }

    /**
     * Called by SocialPostService.createPost() after saving a community post.
     *
     * What this does:
     *  1. Copies community.privacy + community.feedEligible into the post's
     *     denormalized columns (so feed query needs no JOIN)
     *  2. Increments community post counter + weekly stats
     */
    public void onPostPublished(SocialPost post, Long communityId) {
        // NOTE: syncCommunityDenormalizedFields() is now called inside
        // SocialPostService.buildSocialPost() BEFORE the initial save, so there is
        // no need to sync + re-save here. This method only updates community counters.
        if (communityRepo.existsById(communityId)) {
            communityRepo.incrementPostCount(communityId);
            communityRepo.incrementPostsLast7d(communityId);
            communityRepo.incrementActivePostersLast7d(communityId);
        } else {
            log.warn("[Community] onPostPublished: communityId={} not found — counters not updated", communityId);
        }
    }

    /** Called by SocialPostService.deletePost() for community posts. */
    public void onPostDeleted(Long communityId) {
        communityRepo.decrementPostCount(communityId);
    }

    /** Called by CommentService.addComment() when comment is on a community post. */
    public void onCommentAdded(Long communityId) {
        communityRepo.incrementTotalCommentCount(communityId);
    }

    /** Called by PostInteractionService.likePost() when like is on a community post. */
    public void onLikeAdded(Long communityId) {
        communityRepo.incrementTotalLikeCount(communityId);
    }

    // =========================================================================
    // 8. HEALTH SCORE
    // =========================================================================

    public void triggerHealthRecalculation(Long communityId) {
        healthScoreService.recalculateNow(communityId);
    }

    @Transactional(readOnly = true)
    public HealthInsightResponse getHealthInsights(Long communityId, Long requesterId) {
        assertAdminOrOwner(findCommunityOrThrow(communityId), requesterId);
        return healthScoreService.getInsightsForOwner(communityId);
    }

    // =========================================================================
    // AUTHORIZATION
    // =========================================================================

    public boolean isMember(Long communityId, Long userId) {
        return userId != null && memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(communityId, userId);
    }

    private void assertAdminOrOwner(Community community, Long userId) {
        if (community.isOwnedBy(userId)) return;
        CommunityMember m = memberRepo.findByCommunityIdAndUserId(community.getId(), userId)
                .orElseThrow(() -> new SecurityException("Access denied: you are not a member of this community."));
        if (!m.isAdmin())
            throw new SecurityException("Only community admins can perform this action.");
    }

    private void assertModeratorOrAbove(Community community, Long userId) {
        if (community.isOwnedBy(userId)) return;
        CommunityMember m = memberRepo.findByCommunityIdAndUserId(community.getId(), userId)
                .orElseThrow(() -> new SecurityException("Access denied: you are not a member of this community."));
        if (!m.canModerate())
            throw new SecurityException("Only moderators or admins can perform this action.");
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    /**
     * Enforces read-access rules for community post feeds.
     *
     * <ul>
     *   <li>PUBLIC  — anyone (requesterId may be null for anonymous visitors)</li>
     *   <li>PRIVATE — authenticated members only</li>
     *   <li>SECRET  — authenticated members only</li>
     * </ul>
     */
    private void assertPostReadAccess(Community community, Long requesterId) {
        if (community.isPublic()) return;
        // PRIVATE and SECRET both require membership
        if (requesterId == null || !isMember(community.getId(), requesterId)) {
            throw new SecurityException("You must be a member to view this community's posts.");
        }
    }

    /**
     * Maps a {@link SocialPost} entity to the API-safe {@link CommunityPostResponse} DTO.
     *
     * <p>Anonymity is enforced here: when {@code post.isAnonymous()} is true, author
     * identity fields are intentionally left null so they are never serialised.</p>
     *
     * @param post        the entity fetched from the database
     * @param requesterId the authenticated caller's user-id (null for anonymous visitors)
     */
    private CommunityPostResponse toPostResponse(SocialPost post, Long requesterId) {
        User author = post.getUser();

        // Build the lightweight community attribution badge
        CommunityPostAttributionInfo attribution = null;
        if (post.getCommunity() != null) {
            Community c = post.getCommunity();
            attribution = CommunityPostAttributionInfo.builder()
                    .communityId(c.getId())
                    .communityName(c.getName())
                    .communitySlug(c.getSlug())
                    .communityAvatarUrl(c.getAvatarUrl())
                    .healthTier(c.getHealthTier())
                    .healthTierEmoji(c.getHealthTierEmoji())
                    .isSystemSeeded(Boolean.TRUE.equals(c.getIsSystemSeeded()))
                    .wardName(c.getWardName())
                    .build();
        }

        return CommunityPostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .imageUrl(post.getMediaUrls() != null && !post.getMediaUrls().isBlank()
                        ? post.getMediaUrls().split(",")[0].trim() : null)
                .postType(post.hasMedia() ? "IMAGE" : "TEXT")
                .isAnonymous(false)
                // Author — always available (SocialPost has no anonymous flag)
                .authorId(author == null ? null : author.getId())
                .authorUsername(author == null ? null : author.getActualUsername())
                .authorProfileImage(author == null ? null : author.getProfileImage())
                // Engagement
                .likeCount(post.getLikeCount() != null ? post.getLikeCount() : 0)
                .commentCount(post.getCommentCount() != null ? post.getCommentCount() : 0)
                .shareCount(post.getShareCount() != null ? post.getShareCount() : 0)
                // Viewer-context
                .isLikedByMe(false)
                .isPendingApproval(false)
                .isMyPost(requesterId != null
                        && author != null && requesterId.equals(author.getId()))
                // Feed reach — map viralTier to feed reach label
                .feedReach(post.getViralTier() != null ? post.getViralTier() : "COMMUNITY_ONLY")
                // Attribution badge
                .community(attribution)
                // Timestamps
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private void syncPostDenormalizedFields(Long communityId, Community community) {
        String  newPrivacy      = community.getPrivacy() != null ? community.getPrivacy().name() : null;
        boolean newFeedEligible = community.isFeedEligible();
        socialPostRepo.syncCommunityDenormalizedFields(communityId, newPrivacy, newFeedEligible);
        log.info("Synced feed denormalized fields on posts — community={} privacy={} feedEligible={}",
                communityId, newPrivacy, newFeedEligible);
    }

    // =========================================================================
    // MAPPERS
    // =========================================================================

    private CommunitySummaryResponse toSummaryResponse(Community c, Long requesterId) {
        return CommunitySummaryResponse.builder()
                .id(c.getId()).name(c.getName()).slug(c.getSlug())
                .description(CommunityValidationUtil.truncate(c.getDescription(), 200))
                .category(c.getCategory()).avatarUrl(c.getAvatarUrl()).coverImageUrl(c.getCoverImageUrl())
                .privacy(c.getPrivacy() != null ? c.getPrivacy().name() : null)
                .locationName(c.getLocationName())
                .memberCount(c.getMemberCount()).postCount(c.getPostCount())
                .isMember(requesterId != null && isMember(c.getId(), requesterId))
                .isOwner(requesterId != null && c.isOwnedBy(requesterId))
                .createdAt(c.getCreatedAt())
                .feedEligible(c.isFeedEligible())
                .feedSurfaceCount(c.getFeedSurfaceCount())
                .healthScore(c.getHealthScore()).healthTier(c.getHealthTier()).healthTierEmoji(c.getHealthTierEmoji())
                .isSystemSeeded(Boolean.TRUE.equals(c.getIsSystemSeeded())).wardName(c.getWardName())
                .build();
    }

    private CommunityDetailResponse toDetailResponse(Community c, Long requesterId) {
        CommunityMember cm = (requesterId != null)
                ? memberRepo.findByCommunityIdAndUserId(c.getId(), requesterId).orElse(null) : null;
        boolean hasPending = (requesterId != null)
                && joinRequestRepo.existsByCommunityIdAndUserIdAndStatus(
                c.getId(), requesterId, CommunityJoinRequest.RequestStatus.PENDING);
        UserBriefResponse ownerResp = null;
        if (c.getOwner() != null) {
            User o = c.getOwner();
            ownerResp = UserBriefResponse.builder()
                    .id(o.getId())
                    .username(o.getActualUsername())
                    .profileImage(o.getProfileImage())
                    .build();
        }
        return CommunityDetailResponse.builder()
                .id(c.getId()).name(c.getName()).slug(c.getSlug())
                .description(c.getDescription()).category(c.getCategory()).tags(c.getTags())
                .avatarUrl(c.getAvatarUrl()).coverImageUrl(c.getCoverImageUrl())
                .privacy(c.getPrivacy() != null ? c.getPrivacy().name() : null)
                .status(c.getStatus() != null ? c.getStatus().name() : null)
                .locationName(c.getLocationName())
                .locationRestricted(Boolean.TRUE.equals(c.getLocationRestricted()))
                .allowMemberPosts(Boolean.TRUE.equals(c.getAllowMemberPosts()))
                .requirePostApproval(Boolean.TRUE.equals(c.getRequirePostApproval()))
                .allowAnonymousPosts(true)
                .memberCount(c.getMemberCount()).postCount(c.getPostCount())
                .feedEligible(c.isFeedEligible())
                .feedSurfaceCount(c.getFeedSurfaceCount())
                .createdAt(c.getCreatedAt()).lastActiveAt(c.getLastActiveAt())
                .isMember(cm != null && Boolean.TRUE.equals(cm.getIsActive()))
                .isOwner(requesterId != null && c.isOwnedBy(requesterId))
                .isModerator(cm != null && cm.isModerator())
                .currentUserRole(cm != null && cm.getMemberRole() != null ? cm.getMemberRole().name() : null)
                .hasPendingRequest(hasPending).owner(ownerResp)
                .healthScore(c.getHealthScore()).healthTier(c.getHealthTier()).healthTierEmoji(c.getHealthTierEmoji())
                .isSystemSeeded(Boolean.TRUE.equals(c.getIsSystemSeeded())).wardName(c.getWardName())
                .build();
    }

    private CommunityMemberResponse toMemberResponse(CommunityMember cm) {
        User u = cm.getUser();
        return CommunityMemberResponse.builder()
                .id(cm.getId()).userId(u != null ? u.getId() : null)
                .username(u != null ? u.getActualUsername() : null)
                .profileImage(u != null ? u.getProfileImage() : null)
                .memberRole(cm.getMemberRole() != null ? cm.getMemberRole().name() : null)
                .isMuted(Boolean.TRUE.equals(cm.getIsMuted()))
                .isBanned(Boolean.TRUE.equals(cm.getIsBanned()))
                .joinedAt(cm.getJoinedAt())
                .build();
    }

    private JoinRequestResponse toJoinRequestResponse(CommunityJoinRequest jr) {
        User u = jr.getUser();
        return JoinRequestResponse.builder()
                .id(jr.getId()).userId(u != null ? u.getId() : null)
                .username(u != null ? u.getActualUsername() : null)
                .profileImage(u != null ? u.getProfileImage() : null)
                .message(jr.getMessage())
                .status(jr.getStatus() != null ? jr.getStatus().name() : null)
                .requestedAt(jr.getRequestedAt()).reviewedAt(jr.getReviewedAt())
                .build();
    }

    // ── Entity finders ────────────────────────────────────────────────────────

    private Community findCommunityOrThrow(Long id) {
        return communityRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Community not found: " + id));
    }

    private User findUserOrThrow(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + id));
    }

    private CommunityMember findMemberOrThrow(Long communityId, Long userId) {
        return memberRepo.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new NoSuchElementException("User is not a member of this community."));
    }
}