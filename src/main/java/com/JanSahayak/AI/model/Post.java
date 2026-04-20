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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status = PostStatus.ACTIVE;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    // ── Resolution ────────────────────────────────────────────────────────────

    @Builder.Default
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

    @Builder.Default
    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Builder.Default
    @Column(name = "dislike_count", nullable = false)
    private int dislikeCount = 0;

    @Builder.Default
    @Column(name = "comment_count", nullable = false)
    private int commentCount = 0;

    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Builder.Default
    @Column(name = "share_count", nullable = false)
    private int shareCount = 0;

    @Builder.Default
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

    // ── PostUtility compatibility helpers ─────────────────────────────────────
    // PostUtility calls these three methods on Post instances.
    // They are derived from existing fields — no new DB columns required.

    /**
     * FIX: PostUtility.isPostVisibleToUser(), isPostGeographicallyRelevantToUser(),
     * and filterPostsForIndianUser() all call post.isVisibleToUser(user).
     *
     * For a broadcast post, visibility depends on whether the user's pincode falls
     * within the post's targeted geographic scope.
     * For a non-broadcast post, it is visible to everyone (geographic relevance
     * is checked separately by PostUtility using hasPincodeLocation / getPostPincode).
     */
    public boolean isVisibleToUser(User user) {
        if (user == null) return false;

        // Admins see everything
        if (user.isAdmin()) return true;

        // Post must be in a displayable status
        if (status == null || !status.isVisible()) return false;

        // Non-broadcast posts are visible to all authenticated users
        if (!isBroadcastPost()) return true;

        // Country-wide broadcast: visible to everyone in India
        if (BroadcastScope.COUNTRY.equals(broadcastScope)) return true;

        // State-level: matching on 2-digit state prefix
        if (BroadcastScope.STATE.equals(broadcastScope)) {
            if (targetStates == null || targetStates.isBlank()) return false;
            if (!user.hasPincode()) return false;
            String userState = user.getPincode().substring(0, 2);
            for (String prefix : targetStates.split(",")) {
                if (userState.equals(prefix.trim())) return true;
            }
            return false;
        }

        // District-level: matching on 3-digit district prefix
        if (BroadcastScope.DISTRICT.equals(broadcastScope)) {
            if (targetDistricts == null || targetDistricts.isBlank()) return false;
            if (!user.hasPincode()) return false;
            String userDistrict = user.getPincode().substring(0, 3);
            for (String prefix : targetDistricts.split(",")) {
                if (userDistrict.equals(prefix.trim())) return true;
            }
            return false;
        }

        // Area-level: matching on full 6-digit pincode
        if (BroadcastScope.AREA.equals(broadcastScope)) {
            if (targetPincodes == null || targetPincodes.isBlank()) return false;
            if (!user.hasPincode()) return false;
            for (String pc : targetPincodes.split(",")) {
                if (user.getPincode().equals(pc.trim())) return true;
            }
            return false;
        }

        return false;
    }

    /**
     * FIX: PostUtility calls post.hasPincodeLocation() to decide whether
     * geographic proximity checks are meaningful for a regular (non-broadcast) post.
     *
     * A post "has a pincode location" when its author has a valid 6-digit pincode,
     * which is the only location field on a regular Post (broadcast posts use
     * targetPincodes / targetDistricts / targetStates instead).
     */
    public boolean hasPincodeLocation() {
        return user != null && user.hasPincode();
    }

    /**
     * FIX: PostUtility calls post.getPostPincode() to get the pincode that
     * represents the geographic origin of a regular (non-broadcast) post,
     * then passes it to user.isInSameState() / user.isInSameDistrict().
     *
     * The post's location is the author's pincode — regular Posts do not have
     * their own pincode column; the author's pincode is the geographic anchor.
     */
    public String getPostPincode() {
        return (user != null) ? user.getPincode() : null;
    }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = new Date();
        if (status    == null) status    = PostStatus.ACTIVE;
        // Remove targetCountry default logic - rely strictly on broadcastScope
    }
}