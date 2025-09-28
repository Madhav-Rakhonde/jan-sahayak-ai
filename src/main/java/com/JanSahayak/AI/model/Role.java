package com.JanSahayak.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name; // e.g. "ROLE_ADMIN", "ROLE_USER" ,"ROLE_DEPARTMENT"

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL)
    @JsonIgnore
    private Set<User> users = new HashSet<>();

    // ===== Helper Methods =====

    /**
     * Get the role name without "ROLE_" prefix
     */
    public String getSimpleName() {
        return name != null && name.startsWith("ROLE_") ?
                name.substring(5) : name;
    }

    /**
     * Check if this is an admin role
     */
    public boolean isAdminRole() {
        return "ROLE_ADMIN".equals(name);
    }

    /**
     * Check if this is a user role
     */
    public boolean isUserRole() {
        return "ROLE_USER".equals(name);
    }

    /**
     * Get count of users with this role
     */
    public int getUserCount() {
        return users != null ? users.size() : 0;
    }



}
