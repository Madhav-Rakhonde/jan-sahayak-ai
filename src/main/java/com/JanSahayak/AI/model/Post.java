package com.JanSahayak.AI.model;

import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.enums.PostStatus;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Hibernate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_post_user",             columnList = "user_id"),
        @Index(name = "idx_post_status",           columnList = "status"),
        @Index(name = "idx_post_created_at",       columnList = "created_at"),
        @Index(name = "idx_post_resolved",         columnList = "is_resolved"),
        @Index(name = "idx_post_broadcast_scope",  columnList = "broadcast_scope"),
        @Index(name = "idx_post_broadcast_active", columnList = "broadcast_scope, status, created_at"),
        @Index(name = "idx_post_country_broadcast",columnList = "broadcast_scope, target_country, status"),
        // FIX: Added composite index for user + status + created_at — used by getPostsByUser(),
        // getActivePostsByUser(), getResolvedPostsByUser() which filter by both user and status.
        @Index(name = "idx_post_user_status",      columnList = "user_id, status, created_at"),
        // FIX: Added B-tree indexes on target geographic columns.
        // These are VARCHAR columns queried with LIKE '%value%' in every local feed request.
        // Without indexes, every feed page for every user performs a full-table scan.
        // NOTE: For PostgreSQL, GIN trigram indexes (pg_trgm extension) provide far better
        // performance for LIKE '%...%' queries than B-tree. Run the SQL script in the
        // performance report to add the GIN indexes — these B-tree indexes serve as a
        // fallback for prefix-only LIKE 'value%' patterns.
        @Index(name = "idx_post_target_pincodes",  columnList = "target_pincodes"),
        @Index(name = "idx_post_target_districts", columnList = "target_districts"),
        @Index(name = "idx_post_target_states",    columnList = "target_states")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 5000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_post_user"))
    private User user;

    @Column(name = "image_name", length = 500)
    private String imageName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status = PostStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    // ── Resolution ────────────────────────────────────────────────────────────

    @Column(name = "is_resolved", nullable = false)
    private boolean isResolved = false;

    @Column(name = "resolved_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date resolvedAt;

    @Column(name = "resolution_message", length = 1000)
    private String resolutionMessage;

    // ── Broadcasting ──────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "broadcast_scope", length = 20)
    private BroadcastScope broadcastScope;

    @Column(name = "target_country", length = 10)
    private String targetCountry;

    @Column(name = "target_states", length = 1000)
    private String targetStates;

    @Column(name = "target_districts", length = 2000)
    private String targetDistricts;

    @Column(name = "target_pincodes", length = 5000)
    private String targetPincodes;

    // ── Counters (denormalized for fast reads) ────────────────────────────────

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Column(name = "dislike_count", nullable = false)
    private int dislikeCount = 0;

    @Column(name = "comment_count", nullable = false)
    private int commentCount = 0;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(name = "share_count", nullable = false)
    private int shareCount = 0;

    @Column(name = "save_count", nullable = false)
    private int saveCount = 0;

    // ── Relationships ─────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PostLike> likes = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PostView> views = new ArrayList<>();

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<UserTag> userTags = new ArrayList<>();

    // ── Business helpers ──────────────────────────────────────────────────────

    public boolean hasImage() {
        return imageName != null && !imageName.trim().isEmpty();
    }

    public boolean isResolved() {
        return isResolved;
    }

    public boolean isBroadcastPost() {
        return broadcastScope != null;
    }

    public boolean isGovernmentBroadcast() {
        return isBroadcastPost() && user != null && (user.isAdmin() || user.isDepartment());
    }

    public boolean isCountryWideBroadcast() {
        return BroadcastScope.COUNTRY.equals(broadcastScope);
    }

    public boolean isCountryWideGovernmentBroadcast() {
        return isCountryWideBroadcast() && isGovernmentBroadcast();
    }

    public boolean isEligibleForDisplay() {
        return status == PostStatus.ACTIVE || status == PostStatus.RESOLVED;
    }

    public String getBroadcastScopeDescription() {
        return broadcastScope != null ? broadcastScope.getDescription() : null;
    }

    public List<String> getTargetStatesList() {
        if (targetStates == null || targetStates.isBlank()) return List.of();
        return List.of(targetStates.split(","));
    }

    public List<String> getTargetDistrictsList() {
        if (targetDistricts == null || targetDistricts.isBlank()) return List.of();
        return List.of(targetDistricts.split(","));
    }

    public List<String> getTargetPincodesList() {
        if (targetPincodes == null || targetPincodes.isBlank()) return List.of();
        return List.of(targetPincodes.split(","));
    }

    public int getTaggedUserCount() {
        return (userTags != null && Hibernate.isInitialized(userTags)) ? userTags.size() : 0;
    }

    public void incrementLikeCount()    { this.likeCount    = Math.max(0, likeCount + 1); }
    public void decrementLikeCount()    { this.likeCount    = Math.max(0, likeCount - 1); }
    public void incrementDislikeCount() { this.dislikeCount = Math.max(0, dislikeCount + 1); }
    public void decrementDislikeCount() { this.dislikeCount = Math.max(0, dislikeCount - 1); }
    public void incrementCommentCount() { this.commentCount = Math.max(0, commentCount + 1); }
    public void decrementCommentCount() { this.commentCount = Math.max(0, commentCount - 1); }
    public void incrementViewCount()    { this.viewCount    = Math.max(0, viewCount + 1); }
    public void incrementShareCount()   { this.shareCount   = Math.max(0, shareCount + 1); }
    public void incrementSaveCount()    { this.saveCount    = Math.max(0, saveCount + 1); }
    public void decrementSaveCount()    { this.saveCount    = Math.max(0, saveCount - 1); }

    public void markAsResolved(String message) {
        this.isResolved       = true;
        this.status           = PostStatus.RESOLVED;
        this.resolvedAt       = new Date();
        this.resolutionMessage = message;
        this.updatedAt        = new Date();
    }

    public void markAsUnresolved() {
        this.isResolved        = false;
        this.status            = PostStatus.ACTIVE;
        this.resolvedAt        = null;
        this.resolutionMessage = null;
        this.updatedAt         = new Date();
    }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = new Date();
        if (status    == null) status    = PostStatus.ACTIVE;
        if (targetCountry == null) targetCountry = "IN";
    }
}