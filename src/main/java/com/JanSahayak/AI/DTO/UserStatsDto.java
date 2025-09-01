package com.JanSahayak.AI.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsDto {
    private Long userId;
    private String username;
    private String location;
    private Date joinDate; // Changed from LocalDateTime to Date
    private Boolean isActive;
    private String role;

    // Statistics from entity relationships
    private Integer postCount; // From posts relationship
    private Integer commentCount; // From comments relationship
    private Integer likeCount; // From likes relationship
    private Integer interactionCount; // From interactions relationship
    private Integer viewCount; // From postViews relationship

    // Tagged-related statistics
    private Integer taggedInPostsCount; // Posts where user is tagged
    private Integer createdTagsCount; // Tags created by this user
    private Integer receivedTagsCount; // Tags received by this user

    // Recommendation statistics
    private Integer recommendationsCount; // Total recommendations received
    private Integer recommendationsShown; // Recommendations shown to user
    private Integer recommendationsClicked; // Recommendations clicked by user

    // Activity statistics
    private Date lastLoginDate; // Last activity timestamp
    private Integer activePostsCount; // Active posts by user
    private Integer resolvedPostsCount; // Resolved posts by user
    private Double averageInteractionScore; // Average interaction score
}
