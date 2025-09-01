package com.JanSahayak.AI.model;

import com.JanSahayak.AI.enums.PostStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_post_user", columnList = "user_id"),
        @Index(name = "idx_post_status", columnList = "status"),
        @Index(name = "idx_post_created_at", columnList = "created_at"),
        @Index(name = "idx_post_location", columnList = "location"),
        @Index(name = "idx_post_resolved", columnList = "is_resolved")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
    private PostStatus status = PostStatus.ACTIVE;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt = new Date();

    @Column(length = 100)
    @Size(max = 100, message = "Location cannot exceed 100 characters")
    private String location; // Format: "City StateCode" (e.g., "Pune MH")

    @Column(name = "image_name", length = 255)
    private String imageName;

    @Column(name = "is_resolved", nullable = false)
    private Boolean isResolved = false;

    @Column(name = "resolved_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date resolvedAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    // ===== Relationships =====
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_post_user"))
    private User user;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PostLike> likes = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PostView> views = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserTag> userTags = new ArrayList<>();

    // ===== Helper Methods for User Tags =====
    public List<User> getTaggedUsers() {
        return userTags.stream()
                .filter(UserTag::getIsActive)
                .map(UserTag::getTaggedUser)
                .collect(Collectors.toList());
    }

    public List<String> getTaggedUsernames() {
        return userTags.stream()
                .filter(UserTag::getIsActive)
                .map(UserTag::getTaggedUsername)
                .collect(Collectors.toList());
    }

    public boolean hasActiveUserTag(User user) {
        return userTags.stream()
                .anyMatch(tag -> tag.getIsActive() &&
                        tag.getTaggedUser().getId().equals(user.getId()));
    }

    public void addUserTag(User taggedUser, User taggedBy) {
        UserTag userTag = UserTag.builder()
                .post(this)
                .taggedUser(taggedUser)
                .taggedBy(taggedBy)
                .build();
        userTags.add(userTag);
    }

    public void removeUserTag(User user, User deactivatedBy, String reason) {
        userTags.stream()
                .filter(tag -> tag.getIsActive() &&
                        tag.getTaggedUser().getId().equals(user.getId()))
                .findFirst()
                .ifPresent(tag -> tag.deactivate(deactivatedBy, reason));
    }

    // ===== Other Helper Methods =====
    public void markAsResolved(String resolutionMessage) {
        this.isResolved = true;
        this.resolvedAt = new Date();
        this.updatedAt = new Date();
    }

    public void markAsUnresolved() {
        this.isResolved = false;
        this.resolvedAt = null;
        this.updatedAt = new Date();
    }

    public int getLikeCount() {
        return likes != null ? likes.size() : 0;
    }

    public int getCommentCount() {
        return comments != null ? comments.size() : 0;
    }

    public int getViewCount() {
        return views != null ? views.size() : 0;
    }

    public int getTaggedUserCount() {
        return (int) userTags.stream().filter(UserTag::getIsActive).count();
    }

    public boolean hasLocation() {
        return location != null && !location.trim().isEmpty();
    }

    public boolean hasImage() {
        return imageName != null && !imageName.trim().isEmpty();
    }

    public boolean isResolved() {
        return isResolved != null && isResolved;
    }

    // ===== Lifecycle Callbacks =====
    @PreUpdate
    private void preUpdate() {
        this.updatedAt = new Date();
    }
}