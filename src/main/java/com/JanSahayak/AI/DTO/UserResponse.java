package com.JanSahayak.AI.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String contactNumber;
    private String profileImage;
    private String bio;
    private String website;
    private String address;
    private String location; // Format: "City StateCode"
    private Boolean isActive;
    private Date createdAt; // Changed from LocalDateTime to Date to match entity
    private String role; // Role name from Role entity

    // Additional fields from entity
    private String displayName; // From getDisplayName() method
    private String taggableName; // From getTaggableName() method (@username)
    private Boolean hasLocation; // From hasLocation() method

    // Statistics
    private Long postCount; // Total posts by user
    private Long commentCount; // Total comments by user
    private Long taggedPostsCount; // Posts where user is tagged
    private Long createdUsersCount; // Users created by this user (for admin)
}
