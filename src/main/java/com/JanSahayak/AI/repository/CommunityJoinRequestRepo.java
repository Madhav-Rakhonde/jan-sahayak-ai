package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.CommunityJoinRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityJoinRequestRepo extends JpaRepository<CommunityJoinRequest, Long> {

    Optional<CommunityJoinRequest> findByCommunityIdAndUserId(Long communityId, Long userId);

    boolean existsByCommunityIdAndUserIdAndStatus(
            Long communityId, Long userId, CommunityJoinRequest.RequestStatus status);

    // ── Cursor-based pending requests (PaginationUtils.createPageable(limit+1)) ──

    @Query("""
            SELECT jr FROM CommunityJoinRequest jr
            WHERE jr.community.id = :communityId
              AND jr.status       = 'PENDING'
              AND (:cursor IS NULL OR jr.id < :cursor)
            ORDER BY jr.requestedAt DESC, jr.id DESC
            """)
    List<CommunityJoinRequest> findPendingRequestsCursor(
            @Param("communityId") Long communityId,
            @Param("cursor")      Long cursor,
            Pageable pageable);
}