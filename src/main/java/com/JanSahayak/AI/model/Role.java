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
    private String name; // e.g. "ROLE_ADMIN", "ROLE_USER", "ROLE_DEPARTMENT"

    /**
     * FIX: Removed CascadeType.ALL.
     *
     * CascadeType.ALL on a Role → Users relationship is a silent data-loss bomb:
     * deleting a Role row would cascade DELETE to every User with that role,
     * wiping out all user accounts assigned to it.
     *
     * Roles and Users have independent lifecycles — role deletion (an admin-only,
     * extremely rare operation) must never silently remove user accounts.
     * The service layer is responsible for re-assigning users before any role deletion.
     *
     * FetchType is explicitly LAZY (default for OneToMany, stated here for clarity).
     */
    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<User> users = new HashSet<>();

    // ===== Helper Methods =====

    public String getSimpleName() {
        return name != null && name.startsWith("ROLE_") ?
                name.substring(5) : name;
    }

    public boolean isAdminRole() {
        return "ROLE_ADMIN".equals(name);
    }

    public boolean isUserRole() {
        return "ROLE_USER".equals(name);
    }

    public int getUserCount() {
        return users != null ? users.size() : 0;
    }
}