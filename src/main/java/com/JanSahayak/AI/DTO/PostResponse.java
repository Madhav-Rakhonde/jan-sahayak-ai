package com.JanSahayak.AI.DTO;


import com.JanSahayak.AI.enums.PostStatus;
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
    // Basic post information
    private Long id;
    private String content;
    private String location;
    private PostStatus status;
    private Date createdAt;
    private Date updatedAt;

    // Image and media information
    private String imageName;
    private Boolean hasImage;
    private Boolean hasLocation;

    // Resolution information
    private Boolean isResolved;
    private Date resolvedAt;
    private String resolutionMessage;

    // User information
    private Long userId;
    private String username;
    private String userDisplayName;
    private String userProfileImage;

    // Engagement metrics (matching service method names)
    private Integer likeCount;
    private Integer commentCount;
    private Integer viewCount;
    private Long shareCount; // Optional, not in current service

    // Tagging information (matching service implementation)
    private Integer taggedDepartmentCount;
    private List<String> taggedUsernames;
    private Integer totalTaggedUsers;
    private List<TaggedUserInfo> taggedUsers; // Optional, for future enhancement

    // Status-related information (from service convertToPostResponse method)
    private String statusDisplayName;
    private String statusDescription;
    private String statusIcon;
    private Boolean canBeResolved;
    private Boolean allowsUpdates;
    private Boolean isInteractable;

    // User interaction status (from service convertToPostResponse method)
    private Boolean isLikedByCurrentUser;
    private Boolean isViewedByCurrentUser;
    private Boolean isSharedByCurrentUser; // Optional
    private Boolean isCommentedByCurrentUser; // Optional

    // Feed-specific information (for enhanced feed functionality)
    private Double distanceKm; // For location-based feeds
    private String feedReason; // Why this post appears in feed
    private Integer priorityScore; // Feed ranking score
    private Boolean isTaggedPost; // If user is tagged in this post
    private Boolean isDepartmentPost; // If posted by department user

    // Additional metadata
    private String timeAgo; // Humanreadable time difference
    private Boolean canResolve; // If current user can resolve this post
    private Boolean canEdit; // If current user can edit this post
    private Boolean canDelete; // If current user can delete this post

    // Media type information
    private Boolean hasVideo; // For future video support
    private Boolean hasGif; // For future GIF support
    private String mediaType; // Type of media attached
    private String mediaUrl; // URL to media file

    // Nested class for tagged user information
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaggedUserInfo {
        private Long userId;
        private String username;
        private String displayName;
        private String role;
        private String location;
        private Boolean isActive;
        private Date taggedAt; // When user was tagged
        private String taggedBy; // Who tagged this user
    }

    // Helper methods for backward compatibility and convenience
    public boolean getHasImage() {
        return hasImage != null ? hasImage : false;
    }

    public boolean getHasLocation() {
        return hasLocation != null ? hasLocation : false;
    }

    public boolean getIsResolved() {
        return isResolved != null ? isResolved : false;
    }

    public boolean getIsLikedByCurrentUser() {
        return isLikedByCurrentUser != null ? isLikedByCurrentUser : false;
    }

    public boolean getIsViewedByCurrentUser() {
        return isViewedByCurrentUser != null ? isViewedByCurrentUser : false;
    }

    public boolean getCanBeResolved() {
        return canBeResolved != null ? canBeResolved : false;
    }

    public boolean getAllowsUpdates() {
        return allowsUpdates != null ? allowsUpdates : false;
    }

    public boolean getIsInteractable() {
        return isInteractable != null ? isInteractable : false;
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

    public int getTaggedDepartmentCount() {
        return taggedDepartmentCount != null ? taggedDepartmentCount : 0;
    }

    public int getTotalTaggedUsers() {
        return totalTaggedUsers != null ? totalTaggedUsers : 0;
    }
}
