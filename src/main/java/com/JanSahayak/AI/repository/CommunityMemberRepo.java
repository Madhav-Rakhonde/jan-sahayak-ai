package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.CommunityMember;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityMemberRepo extends JpaRepository<CommunityMember, Long> {

    Optional<CommunityMember> findByCommunityIdAndUserId(Long communityId, Long userId);

    boolean existsByCommunityIdAndUserIdAndIsActiveTrue(Long communityId, Long userId);

    boolean existsByCommunityIdAndUserIdAndIsActiveTrueAndIsBannedFalse(Long communityId, Long userId);

    /**
     * Batch-loads membership records for a single user across multiple communities.
     * Used by SocialPostService.convertToDtoBatch() to set isMember on community
     * post DTOs without issuing a separate query per post.
     *
     * Only returns active, non-banned memberships.
     */
    @Query("""
            SELECT cm FROM CommunityMember cm
            WHERE cm.user.id       = :userId
              AND cm.community.id IN :communityIds
              AND cm.isActive      = true
              AND cm.isBanned      = false
            """)
    List<CommunityMember> findActiveByUserIdAndCommunityIdIn(
            @Param("userId")       Long userId,
            @Param("communityIds") List<Long> communityIds);



    @Query("""
            SELECT cm FROM CommunityMember cm
            WHERE cm.community.id = :communityId
              AND cm.isActive = true
              AND cm.isBanned = false
              AND (:cursor IS NULL OR cm.id < :cursor)
            ORDER BY cm.joinedAt ASC, cm.id ASC
            """)
    List<CommunityMember> findActiveMembersCursor(
            @Param("communityId") Long communityId,
            @Param("cursor")      Long cursor,
            Pageable pageable);

    // ── User's communities (cursor-based) ─────────────────────────────────────

    @Query("""
            SELECT cm FROM CommunityMember cm
            WHERE cm.user.id   = :userId
              AND cm.isActive  = true
              AND cm.isBanned  = false
              AND (:cursor IS NULL OR cm.id < :cursor)
            ORDER BY cm.joinedAt DESC, cm.id DESC
            """)
    List<CommunityMember> findUserCommunitiesCursor(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // ── Authorization checks ──────────────────────────────────────────────────

    @Query("""
            SELECT CASE WHEN COUNT(cm) > 0 THEN true ELSE false END
            FROM CommunityMember cm
            WHERE cm.community.id = :communityId
              AND cm.user.id      = :userId
              AND cm.isActive     = true
              AND cm.isBanned     = false
              AND cm.memberRole IN ('MODERATOR', 'ADMIN')
            """)
    boolean isModeratorOrAbove(
            @Param("communityId") Long communityId,
            @Param("userId")      Long userId);

    // ── Mutations ─────────────────────────────────────────────────────────────

    @Modifying
    @Query("UPDATE CommunityMember cm SET cm.isActive = false WHERE cm.community.id = :communityId AND cm.user.id = :userId")
    void deactivateMember(@Param("communityId") Long communityId, @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE CommunityMember cm SET cm.isActive = false WHERE cm.community.id = :communityId")
    void deactivateAllByCommunity(@Param("communityId") Long communityId);

    // ── Hyperlocal seed helper ────────────────────────────────────────────────

    @Query("""
            SELECT cm.user.id FROM CommunityMember cm
            WHERE cm.community.id = :communityId AND cm.isActive = true
            """)
    List<Long> findActiveMemberUserIds(@Param("communityId") Long communityId);
    @Query("SELECT cm FROM CommunityMember cm " +
            "WHERE cm.user.id IN :userIds AND cm.community.id IN :communityIds AND cm.isActive = true")
    List<CommunityMember> findActiveByUserIdInAndCommunityIdIn(
            @Param("userIds") List<Long> userIds,
            @Param("communityIds") List<Long> communityIds);
}