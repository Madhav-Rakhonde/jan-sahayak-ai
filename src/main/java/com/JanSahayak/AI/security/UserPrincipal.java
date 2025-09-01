package com.JanSahayak.AI.security;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.Role;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;

@Data
public class UserPrincipal implements UserDetails {
    private Long id;
    private String username;
    private String email;

    @JsonIgnore
    private String password;

    private String location;
    private Boolean isActive;
    private Role role;
    private Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id, String username, String email, String password,
                         String location, Boolean isActive, Role role,
                         Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.location = location;
        this.isActive = isActive;
        this.role = role;
        this.authorities = authorities;
    }

    // ✅ FIXED: Proper role authority creation for single role per user (no enum)
    public static UserPrincipal create(User user) {
        // Single role - get role name directly from Role entity
        String roleName = user.getRole().getName(); // Assuming Role entity has String name field
        String authorityName = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;

        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(authorityName)
        );

        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                user.getLocation(),
                user.getIsActive(),
                user.getRole(),
                authorities
        );
    }

    @Override
    public String getUsername() {
        // ✅ FIXED: Return email as username for Spring Security
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
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

    // ✅ FIXED: Consistent role checking for single role per user
    public boolean hasRole(String roleName) {
        String expectedRole = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        return authorities.stream()
                .anyMatch(authority -> authority.getAuthority().equals(expectedRole));
    }

    // ✅ Convenience methods for your 3 roles
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isUser() {
        return hasRole("USER");
    }

    public boolean isCitizen() {
        return hasRole("CITIZEN");
    }

    // Get the single role name without ROLE_ prefix
    public String getRoleName() {
        return authorities.stream()
                .findFirst()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .orElse("UNKNOWN");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPrincipal that = (UserPrincipal) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UserPrincipal{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", location='" + location + '\'' +
                ", isActive=" + isActive +
                ", role=" + (role != null ? role.getName() : null) +
                ", loginEmail='" + getUsername() + '\'' + // Shows email used for login
                '}';
    }
}
