package com.JanSahayak.AI.DTO;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.enums.BroadcastScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {

    // ===== Basic Post Information =====
    private Long id;
    private String content;
    private PostStatus status;
    private Date createdAt;
    private Date updatedAt;

    // ===== Media Information =====
    private String imageName;
    private Boolean hasImage;
    private String mediaType;

    // ===== Resolution Information =====
    private Boolean isResolved;
    private Date resolvedAt;

    // ===== User Information =====
    private Long userId;
    private String username;
    private String userDisplayName;
    private String userProfileImage;
    private String userPincode;

    // ===== Broadcasting Information =====
    private BroadcastScope broadcastScope;
    private String broadcastScopeDescription;
    private Boolean isBroadcastPost;
    private String targetCountry;
    private List<String> targetStates;
    private List<String> targetDistricts;
    private List<String> targetPincodes;

    // ===== Engagement Metrics =====
    private Integer likeCount;
    private Integer commentCount;
    private Integer viewCount;
    private Integer taggedUserCount;

    // ===== User Interaction Status =====
    private Boolean isLikedByCurrentUser;
    private Boolean isViewedByCurrentUser;

    // ===== Status Information =====
    private String statusDisplayName;
    private Boolean canBeResolved;
    private Boolean allowsUpdates;
    private Boolean isEligibleForDisplay;

    // ===== Tagged Users Information =====
    private List<String> taggedUsernames;
    private List<TaggedUserInfo> taggedUsers;

    // ===== Additional Metadata =====
    private String timeAgo;
    private Boolean isVisibleToCurrentUser;

    // ===== Helper Methods =====
    public boolean getHasImage() {
        return hasImage != null ? hasImage : false;
    }

    public boolean getIsResolved() {
        return isResolved != null ? isResolved : false;
    }

    public boolean getIsBroadcastPost() {
        return isBroadcastPost != null ? isBroadcastPost : false;
    }

    public boolean getIsLikedByCurrentUser() {
        return isLikedByCurrentUser != null ? isLikedByCurrentUser : false;
    }

    public boolean getIsViewedByCurrentUser() {
        return isViewedByCurrentUser != null ? isViewedByCurrentUser : false;
    }

    public int getLikeCount() {
        return likeCount != null ? likeCount : 0;
    }

    public int getCommentCount() {
        return commentCount != null ? commentCount : 0;
    }

    public int getViewCount() {
        return viewCount != null ? viewCount : 0;
    }

    public int getTaggedUserCount() {
        return taggedUserCount != null ? taggedUserCount : 0;
    }

    // ===== Nested Class for Tagged User Information =====
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaggedUserInfo {
        private Long tagId;
        private Long userId;
        private String username;
        private String displayName;
        private String profileImage;
        private String pincode;
        private Boolean isActive;
        private Date taggedAt;
        private Date deactivatedAt;
        private String deactivationReason;
        private Long taggedByUserId;
        private String taggedByUsername;

        public boolean isActiveTag() {
            return isActive != null && isActive;
        }
    }
}