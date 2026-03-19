package com.JanSahayak.AI.model;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.enums.BroadcastScope;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.Hibernate;
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
                @UniqueConstraint(columnNames = "email",    name = "uk_user_email")
        },
        indexes = {
                @Index(name = "idx_user_username",   columnList = "username"),
                @Index(name = "idx_user_email",      columnList = "email"),
                @Index(name = "idx_user_pincode",    columnList = "pincode"),
                @Index(name = "idx_user_is_active",  columnList = "is_active"),
                @Index(name = "idx_user_created_at", columnList = "created_at"),
                @Index(name = "idx_user_role",       columnList = "role_id")
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
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

    @Column(name = "pincode", length = 6)
    @Size(min = 6, max = 6, message = "Pincode must be exactly 6 digits")
    private String pincode;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date createdAt = new Date();

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    // ===== Relationships =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", foreignKey = @ForeignKey(name = "fk_user_created_by"))
    private User createdBy;

    @OneToMany(mappedBy = "createdBy", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    @JsonIgnore
    private List<User> createdUsers = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Post> posts = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<SocialPost> socialPosts = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<PostLike> likes = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<PostView> postViews = new ArrayList<>();

    /**
     * FIX: Changed from FetchType.EAGER to FetchType.LAZY.
     *
     * EAGER loading caused the Role entity to be fetched alongside EVERY user lookup —
     * including the Spring Security authentication path that fires on every API request.
     * This doubled query count on the hottest path in the application.
     *
     * Role is now loaded lazily. The authentication path in UserService uses
     * UserRepo.findByEmailWithRole() which does a JOIN FETCH in a single query,
     * so there is no extra round-trip for authentication.
     *
     * Any other code that needs the role (e.g. isAdmin(), isDepartment()) must be
     * called within a Hibernate session or via a query that JOIN FETCHes the role.
     * The role is always available within @Transactional service methods.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_role"))
    @JsonIgnore
    private Role role;

    @OneToMany(mappedBy = "taggedUser", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    @JsonIgnore
    private List<UserTag> receivedTags = new ArrayList<>();

    @OneToMany(mappedBy = "taggedBy", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    @JsonIgnore
    private List<UserTag> createdTags = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL,
            fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<Notification> notifications = new ArrayList<>();

    /**
     * PincodeLookup data enriched at runtime — NOT stored in the DB.
     */
    @Transient
    private PincodeLookup pincodeLookupData;

    // ===== Role Helper Methods =====

    public boolean isAdmin() {
        if (role == null || role.getName() == null) {
            return false;
        }
        return "ROLE_ADMIN".equalsIgnoreCase(role.getName());
    }

    public boolean isDepartment() {
        if (role == null || role.getName() == null) {
            return false;
        }
        return "ROLE_DEPARTMENT".equalsIgnoreCase(role.getName());
    }

    public boolean isNormalUser() {
        if (role == null || role.getName() == null) {
            return false;
        }
        return "ROLE_USER".equals(role.getName().toLowerCase());
    }

    public boolean canCreateBroadcast() {
        return isAdmin() || isDepartment();
    }

    // ===== Pincode Helper Methods =====

    public String getStatePrefix() {
        return hasPincode() ? pincode.substring(0, 2) : null;
    }

    public String getDistrictPrefix() {
        return hasPincode() ? pincode.substring(0, 3) : null;
    }

    public boolean isInSameState(String otherPincode) {
        if (!hasPincode() || otherPincode == null || otherPincode.length() < 2) {
            return false;
        }
        return getStatePrefix().equals(otherPincode.substring(0, 2));
    }

    public boolean isInSameDistrict(String otherPincode) {
        if (!hasPincode() || otherPincode == null || otherPincode.length() < 3) {
            return false;
        }
        return getDistrictPrefix().equals(otherPincode.substring(0, 3));
    }

    // ===== Basic Helper Methods =====

    public String getDisplayName() {
        return username;
    }

    public String getTaggableName() {
        return "@" + username;
    }

    public boolean hasProfileImage() {
        return profileImage != null && !profileImage.trim().isEmpty();
    }

    public boolean hasBio() {
        return bio != null && !bio.trim().isEmpty();
    }

    public boolean hasPincode() {
        return pincode != null && pincode.matches("\\d{6}");
    }

    public boolean isValidPincode() {
        return hasPincode();
    }

    public int getPostCount() {
        return (posts != null && Hibernate.isInitialized(posts)) ? posts.size() : 0;
    }

    public int getSocialPostCount() {
        return (socialPosts != null && Hibernate.isInitialized(socialPosts)) ? socialPosts.size() : 0;
    }

    public int getCommentCount() {
        return (comments != null && Hibernate.isInitialized(comments)) ? comments.size() : 0;
    }

    public int getLikeCount() {
        return (likes != null && Hibernate.isInitialized(likes)) ? likes.size() : 0;
    }

    public String getPrimaryLocation() {
        return hasPincode() ? pincode : null;
    }

    public boolean canParticipateInLocalDiscovery() {
        return hasPincode() && isActive != null && isActive;
    }

    // ===== Location Compatibility Methods =====

    public boolean hasLocation() {
        return hasPincode();
    }

    public String getLocation() {
        return hasPincode() ? pincode : null;
    }

    // ===== Spring Security UserDetails Methods =====

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (role != null && role.getName() != null) {
            authorities.add(new SimpleGrantedAuthority(role.getName()));
        }
        return authorities;
    }

    /**
     * Spring Security uses this as the login credential — returns EMAIL.
     * Use getActualUsername() to get the display username.
     */
    @Override
    public String getUsername() {
        return this.email;
    }

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

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = new Date();
    }
}