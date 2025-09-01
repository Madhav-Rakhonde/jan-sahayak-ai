package com.JanSahayak.AI.model;

import com.JanSahayak.AI.enums.PostStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "username", name = "uk_user_username"),
                @UniqueConstraint(columnNames = "email", name = "uk_user_email")
        },
        indexes = {
                @Index(name = "idx_user_username", columnList = "username"),
                @Index(name = "idx_user_email", columnList = "email"),
                @Index(name = "idx_user_location", columnList = "location"),
                @Index(name = "idx_user_is_active", columnList = "is_active"),
                @Index(name = "idx_user_created_at", columnList = "created_at")
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    @Size(min = 4, max = 100, message = "Username must be between 4 and 100 characters")
    private String username;

    @Column(nullable = false, length = 255)
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;

    @Column(length = 100, nullable = false, unique = true)
    @Email(message = "Invalid email format")
    @NotEmpty(message = "Email is required")
    private String email;

    @Column(name = "contact_number", length = 15)
    @Size(max = 15, message = "Contact number cannot exceed 15 characters")
    private String contactNumber;

    @Column(name = "profile_image", length = 255)
    private String profileImage;

    @Column(length = 1000)
    @Size(max = 1000, message = "Bio cannot exceed 1000 characters")
    private String bio;

    @Column(length = 255)
    @Size(max = 255, message = "Website URL cannot exceed 255 characters")
    private String website;

    @Column(length = 500)
    @Size(max = 500, message = "Address cannot exceed 500 characters")
    private String address;

    @Column(length = 100)
    @Size(max = 100, message = "Location cannot exceed 100 characters")
    private String location; // Format: "City StateCode" (e.g., "Pune MH")

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    // ===== Self-referential relationship =====
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", foreignKey = @ForeignKey(name = "fk_user_created_by"))
    private User createdBy;

    @OneToMany(mappedBy = "createdBy", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<User> createdUsers = new ArrayList<>();

    // ===== Existing Relationships =====
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Post> posts = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PostLike> likes = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PostView> postViews = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_role"))
    private Role role;

    // ===== UserTag relationships =====
    @OneToMany(mappedBy = "taggedUser", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<UserTag> receivedTags = new ArrayList<>();

    @OneToMany(mappedBy = "taggedBy", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<UserTag> createdTags = new ArrayList<>();

    // ===== Helper Methods =====

    public List<Post> getTaggedPosts() {
        return receivedTags.stream()
                .filter(UserTag::getIsActive)
                .map(UserTag::getPost)
                .collect(Collectors.toList());
    }

    public List<Post> getTaggedPostsByStatus(PostStatus status) {
        return receivedTags.stream()
                .filter(UserTag::getIsActive)
                .map(UserTag::getPost)
                .filter(post -> post.getStatus() == status)
                .collect(Collectors.toList());
    }

    public Long getTaggedPostCount() {
        return receivedTags.stream()
                .filter(UserTag::getIsActive)
                .count();
    }

    public Long getCreatedTagsCount() {
        return createdTags.stream()
                .filter(UserTag::getIsActive)
                .count();
    }

    public String getDisplayName() {
        return username;
    }

    public String getTaggableName() {
        return "@" + username;
    }

    public boolean hasLocation() {
        return location != null && !location.trim().isEmpty();
    }

    public boolean hasProfileImage() {
        return profileImage != null && !profileImage.trim().isEmpty();
    }

    public boolean hasBio() {
        return bio != null && !bio.trim().isEmpty();
    }

    public int getPostCount() {
        return posts != null ? posts.size() : 0;
    }

    public int getCommentCount() {
        return comments != null ? comments.size() : 0;
    }

    public int getLikeCount() {
        return likes != null ? likes.size() : 0;
    }

    // ===== Spring Security UserDetails Methods =====
    // Note: Using email for login as per your requirement

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getName()));
    }

    @Override
    public String getUsername() {
        // ✅ CONFIRMED: Returning email for login (correct implementation)
        return this.email;
    }

    // Custom method to get the actual username field
    public String getActualUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive != null && isActive;
    }

    // ===== Lifecycle Callbacks =====
    @PreUpdate
    private void preUpdate() {
        this.updatedAt = new Date();
    }
}