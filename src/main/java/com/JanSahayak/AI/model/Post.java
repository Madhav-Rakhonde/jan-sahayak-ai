package com.JanSahayak.AI.model;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.payload.PostUtility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import com.JanSahayak.AI.exception.ValidationException;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_post_user",             columnList = "user_id"),
        @Index(name = "idx_post_status",           columnList = "status"),
        @Index(name = "idx_post_created_at",       columnList = "created_at"),
        @Index(name = "idx_post_resolved",         columnList = "is_resolved"),
        @Index(name = "idx_post_broadcast_scope",  columnList = "broadcast_scope"),
        @Index(name = "idx_post_broadcast_active", columnList = "broadcast_scope, status, created_at"),
        @Index(name = "idx_post_country_broadcast",columnList = "broadcast_scope, target_country, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    @NotBlank(message = "Post content cannot be empty")
    @Size(max = 2000, message = "Post content cannot exceed 2000 characters")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PostStatus status = PostStatus.ACTIVE;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Date createdAt = new Date();

    @Column(name = "image_name", length = 255)
    private String imageName;

    @Column(name = "is_resolved", nullable = false)
    @Builder.Default
    private Boolean isResolved = false;

    @Column(name = "resolved_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date resolvedAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    // ===== Denormalized Engagement Counters =====

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private Integer likeCount = 0;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private Integer commentCount = 0;

    @Column(name = "share_count", nullable = false)
    @Builder.Default
    private Integer shareCount = 0;

    @Column(name = "dislike_count", nullable = false)
    @Builder.Default
    private Integer dislikeCount = 0;

    @Column(name = "save_count", nullable = false)
    @Builder.Default
    private Integer saveCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "broadcast_scope")
    private BroadcastScope broadcastScope;

    @Column(name = "target_country", length = 50)
    @Builder.Default
    private String targetCountry = Constant.DEFAULT_TARGET_COUNTRY;

    @Column(name = "target_states", length = 1000)
    private String targetStates;

    @Column(name = "target_districts", length = 2000)
    private String targetDistricts;

    @Column(name = "target_pincodes", length = 5000)
    private String targetPincodes;

    // ===== Relationships =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_post_user"))
    private User user;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonManagedReference
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PostLike> likes = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PostView> views = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<UserTag> userTags = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<PostShare> shares = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<SavedPost> savedByUsers = new ArrayList<>();

    // ===== Broadcasting Helper Methods =====

    public boolean isBroadcastPost() {
        return broadcastScope != null;
    }

    public boolean isBroadcastActive() {
        return isBroadcastPost() && status == PostStatus.ACTIVE;
    }

    public boolean isCountryWideBroadcast() {
        return isBroadcastPost() &&
                broadcastScope == BroadcastScope.COUNTRY &&
                Constant.DEFAULT_TARGET_COUNTRY.equals(targetCountry);
    }

    public boolean isGovernmentBroadcast() {
        return isBroadcastPost() &&
                user != null &&
                (user.isDepartment() || user.isAdmin());
    }

    public boolean isCountryWideGovernmentBroadcast() {
        return isCountryWideBroadcast() && isGovernmentBroadcast() && isBroadcastActive();
    }

    public List<String> getTargetStatesList() {
        return targetStates != null ?
                Arrays.stream(targetStates.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList()) : new ArrayList<>();
    }

    public List<String> getTargetDistrictsList() {
        return targetDistricts != null ?
                Arrays.stream(targetDistricts.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList()) : new ArrayList<>();
    }

    public List<String> getTargetPincodesList() {
        return targetPincodes != null ?
                Arrays.stream(targetPincodes.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList()) : new ArrayList<>();
    }

    public void setTargetStatesList(List<String> states) {
        this.targetStates = states != null && !states.isEmpty() ?
                String.join(",", states) : null;
    }

    public void setTargetDistrictsList(List<String> districts) {
        this.targetDistricts = districts != null && !districts.isEmpty() ?
                String.join(",", districts) : null;
    }

    public void setTargetPincodesList(List<String> pincodes) {
        this.targetPincodes = pincodes != null && !pincodes.isEmpty() ?
                String.join(",", pincodes) : null;
    }

    public boolean isVisibleToUser(User user) {
        if (isCountryWideGovernmentBroadcast()) {
            return true;
        }

        if (!isBroadcastPost()) {
            return isEligibleForLocalDiscovery();
        }

        if (!isBroadcastActive()) {
            return false;
        }

        if (isCountryWideBroadcast()) {
            return true;
        }

        return isVisibleToUserByPincodePrefix(user);
    }

    public boolean isVisibleToUserByPincodePrefix(User user) {
        if (user == null || broadcastScope == null) {
            return broadcastScope == BroadcastScope.COUNTRY &&
                    Constant.DEFAULT_TARGET_COUNTRY.equals(targetCountry);
        }

        if (broadcastScope == BroadcastScope.COUNTRY &&
                Constant.DEFAULT_TARGET_COUNTRY.equals(targetCountry)) {
            return true;
        }

        if (!user.hasPincode() || user.getPincode().length() < 6) {
            return false;
        }

        String userPincode        = user.getPincode();
        String userStatePrefix    = userPincode.substring(0, 2);
        String userDistrictPrefix = userPincode.substring(0, 3);

        switch (broadcastScope) {
            case STATE:
                List<String> targetStatePrefixes = getTargetStatesList();
                return targetStatePrefixes.contains(userStatePrefix);

            case DISTRICT:
                List<String> targetDistrictPrefixes = getTargetDistrictsList();
                return targetDistrictPrefixes.contains(userDistrictPrefix);

            case AREA:
                List<String> targetPincodesList = getTargetPincodesList();
                return targetPincodesList.contains(userPincode);

            default:
                return false;
        }
    }

    public String getBroadcastScopeDescription() {
        if (broadcastScope == null) {
            return "Regular Post";
        }

        if (isCountryWideGovernmentBroadcast()) {
            return "Government " + broadcastScope.getDescription() + " (All India)";
        }

        if (isCountryWideBroadcast()) {
            return broadcastScope.getDescription() + " (All India)";
        }

        return broadcastScope.getDescription();
    }

    // ===== Pincode Helper Methods =====

    public String getPostPincode() {
        return user != null ? user.getPincode() : null;
    }

    public boolean hasLocation() {
        return user != null && user.hasPincode();
    }

    public boolean hasPincodeLocation() {
        return user != null && user.hasPincode();
    }

    public String getUserPrimaryLocation() {
        return user != null ? user.getPrimaryLocation() : null;
    }

    public String getPostStatePrefix() {
        return user != null ? user.getStatePrefix() : null;
    }

    public String getPostDistrictPrefix() {
        return user != null ? user.getDistrictPrefix() : null;
    }

    public boolean isFromSameState(User user) {
        if (this.user == null || user == null) return false;
        return this.user.isInSameState(user.getPincode());
    }

    public boolean isFromSameDistrict(User user) {
        if (this.user == null || user == null) return false;
        return this.user.isInSameDistrict(user.getPincode());
    }

    // ===== Post Management Methods =====

    public void markAsResolved(String resolutionMessage) {
        this.status     = PostStatus.RESOLVED;
        this.isResolved = true;
        this.resolvedAt = new Date();
        this.updatedAt  = new Date();
    }

    public void markAsUnresolved() {
        this.status     = PostStatus.ACTIVE;
        this.isResolved = false;
        this.resolvedAt = null;
        this.updatedAt  = new Date();
    }

    // ===== Count Getter Methods =====

    public Integer getLikeCount() {
        return likeCount != null ? likeCount : 0;
    }

    public Integer getViewCount() {
        return viewCount != null ? viewCount : 0;
    }

    public Integer getCommentCount() {
        return commentCount != null ? commentCount : 0;
    }

    public Integer getShareCount() {
        return shareCount != null ? shareCount : 0;
    }

    public Integer getDislikeCount() {
        return dislikeCount != null ? dislikeCount : 0;
    }

    public Integer getSaveCount() {
        return saveCount != null ? saveCount : 0;
    }

    public int getActualLikeCount() {
        return likes != null ? likes.size() : 0;
    }

    public int getActualCommentCount() {
        return comments != null ? comments.size() : 0;
    }

    public int getActualViewCount() {
        return views != null ? views.size() : 0;
    }

    public int getActualShareCount() {
        return shares != null ? shares.size() : 0;
    }

    // ===== Counter Mutation Methods =====

    public void incrementCommentCount() {
        this.commentCount = (this.commentCount != null ? this.commentCount : 0) + 1;
    }

    public void decrementCommentCount() {
        this.commentCount = Math.max(0, (this.commentCount != null ? this.commentCount : 0) - 1);
    }

    public void incrementViewCount() {
        this.viewCount = (this.viewCount != null ? this.viewCount : 0) + 1;
    }

    public void incrementLikeCount() {
        this.likeCount = (this.likeCount != null ? this.likeCount : 0) + 1;
    }

    public void decrementLikeCount() {
        this.likeCount = Math.max(0, (this.likeCount != null ? this.likeCount : 0) - 1);
    }

    public void incrementDislikeCount() {
        this.dislikeCount = (this.dislikeCount != null ? this.dislikeCount : 0) + 1;
    }

    public void decrementDislikeCount() {
        this.dislikeCount = Math.max(0, (this.dislikeCount != null ? this.dislikeCount : 0) - 1);
    }

    public void incrementShareCount() {
        this.shareCount = (this.shareCount != null ? this.shareCount : 0) + 1;
    }

    public void decrementShareCount() {
        this.shareCount = Math.max(0, (this.shareCount != null ? this.shareCount : 0) - 1);
    }

    public void incrementSaveCount() {
        this.saveCount = (this.saveCount != null ? this.saveCount : 0) + 1;
    }

    public void decrementSaveCount() {
        this.saveCount = Math.max(0, (this.saveCount != null ? this.saveCount : 0) - 1);
    }

    /**
     * FIX: LazyInitializationException — Post.userTags is a lazy collection.
     * Accessing it outside a Hibernate session throws an exception.
     *
     * Safe approach:
     * - If userTags is null or not yet initialized by Hibernate, return 0.
     * - Only count when the collection is actually in memory.
     *
     * The actual tagged user data is loaded separately by UserTaggingService
     * when needed — this count is just a display hint.
     */
    public int getTaggedUserCount() {
        if (userTags == null || !org.hibernate.Hibernate.isInitialized(userTags)) {
            return 0;
        }
        return (int) userTags.stream()
                .filter(tag -> tag != null && Boolean.TRUE.equals(tag.getIsActive()))
                .count();
    }

    // ===== Validation Methods =====

    public boolean hasImage() {
        return imageName != null && !imageName.trim().isEmpty();
    }

    public boolean isResolved() {
        return isResolved != null && isResolved;
    }

    public boolean isEligibleForDisplay() {
        return status == PostStatus.ACTIVE &&
                user != null &&
                user.getIsActive() != null &&
                user.getIsActive();
    }

    public boolean isEligibleForLocalDiscovery() {
        return isEligibleForDisplay() && user.canParticipateInLocalDiscovery();
    }

    // ===== STATIC VALIDATION METHODS =====

    public static void validateBroadcastScope(BroadcastScope scope, String targetCountry,
                                              List<String> targetStates, List<String> targetDistricts,
                                              List<String> targetPincodes) {
        if (scope == null) {
            throw new ValidationException("Broadcast scope cannot be null");
        }

        String validatedCountry = normalizeTargetCountry(targetCountry);

        switch (scope) {
            case COUNTRY:
                if (!Constant.DEFAULT_TARGET_COUNTRY.equals(validatedCountry)) {
                    log.warn("Overriding target country {} to {} for India-only app",
                            targetCountry, Constant.DEFAULT_TARGET_COUNTRY);
                }
                break;
            case STATE:
                if (targetStates == null || targetStates.isEmpty()) {
                    throw new ValidationException("Target states are required for state-level broadcasts");
                }
                validateTargetStates(targetStates);
                break;
            case DISTRICT:
                if (targetDistricts == null || targetDistricts.isEmpty()) {
                    throw new ValidationException("Target districts are required for district-level broadcasts");
                }
                validateTargetDistricts(targetDistricts);
                break;
            case AREA:
                if (targetPincodes == null || targetPincodes.isEmpty()) {
                    throw new ValidationException("Target pincodes are required for area-level broadcasts");
                }
                validateTargetPincodes(targetPincodes);
                break;
        }
    }

    public static String normalizeTargetCountry(String inputCountry) {
        if (inputCountry != null && !Constant.DEFAULT_TARGET_COUNTRY.equals(inputCountry)) {
            log.info("Normalizing target country from {} to {}", inputCountry, Constant.DEFAULT_TARGET_COUNTRY);
        }
        return Constant.DEFAULT_TARGET_COUNTRY;
    }

    public static void validateTargetStates(List<String> targetStates) {
        if (targetStates == null || targetStates.isEmpty()) {
            throw new ValidationException("Target states cannot be empty");
        }
        for (String state : targetStates) {
            if (state == null || state.trim().isEmpty()) {
                throw new ValidationException("State name cannot be empty");
            }
        }
    }

    public static void validateTargetDistricts(List<String> targetDistricts) {
        if (targetDistricts == null || targetDistricts.isEmpty()) {
            throw new ValidationException("Target districts cannot be empty");
        }
        for (String district : targetDistricts) {
            if (district == null || district.trim().isEmpty()) {
                throw new ValidationException("District name cannot be empty");
            }
        }
    }

    public static void validateTargetPincodes(List<String> targetPincodes) {
        if (targetPincodes == null || targetPincodes.isEmpty()) {
            throw new ValidationException("Target pincodes cannot be empty");
        }
        for (String pincode : targetPincodes) {
            if (!Constant.isValidIndianPincode(pincode)) {
                throw new ValidationException("Invalid Indian pincode format: " + pincode);
            }
        }
    }

    public static boolean isCountryWideBroadcastForIndia(Post post) {
        return post != null &&
                post.isBroadcastPost() &&
                post.getBroadcastScope() == BroadcastScope.COUNTRY &&
                Constant.DEFAULT_TARGET_COUNTRY.equals(post.getTargetCountry());
    }

    public static boolean isAllIndiaGovernmentBroadcast(Post post) {
        return post != null &&
                post.isCountryWideGovernmentBroadcast() &&
                post.isBroadcastActive();
    }

    // ===== Lifecycle Callbacks =====

    @PrePersist
    private void prePersist() {
        if (isBroadcastPost()) {
            this.targetCountry = Constant.DEFAULT_TARGET_COUNTRY;
        }
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = new Date();
        if (isBroadcastPost()) {
            this.targetCountry = Constant.DEFAULT_TARGET_COUNTRY;
        }
    }
}