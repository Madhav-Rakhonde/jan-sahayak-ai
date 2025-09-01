package com.JanSahayak.AI.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "user_tags",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"post_id", "tagged_user_id"},
                        name = "uk_post_tagged_user")
        },
        indexes = {
                @Index(name = "idx_user_tag_post", columnList = "post_id"),
                @Index(name = "idx_user_tag_tagged_user", columnList = "tagged_user_id"),
                @Index(name = "idx_user_tag_tagged_by", columnList = "tagged_by_user_id"),
                @Index(name = "idx_user_tag_active", columnList = "is_active")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_tag_post"))
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tagged_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_tag_tagged_user"))
    private User taggedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tagged_by_user_id",
            foreignKey = @ForeignKey(name = "fk_user_tag_tagged_by"))
    private User taggedBy;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "tagged_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date taggedAt = new Date();

    @Column(name = "deactivated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date deactivatedAt;

    @Column(name = "deactivated_by_user_id")
    private Long deactivatedByUserId;

    @Column(name = "deactivation_reason", length = 500)
    private String deactivationReason;

    // ===== Helper Methods =====

    public void deactivate(User deactivatedBy, String reason) {
        this.isActive = false;
        this.deactivatedAt = new Date();
        this.deactivatedByUserId = deactivatedBy != null ? deactivatedBy.getId() : null;
        this.deactivationReason = reason;
    }

    public void reactivate() {
        this.isActive = true;
        this.deactivatedAt = null;
        this.deactivatedByUserId = null;
        this.deactivationReason = null;
    }

    public String getTaggedUsername() {
        return taggedUser != null ? taggedUser.getUsername() : null;
    }

    public String getTaggedByUsername() {
        return taggedBy != null ? taggedBy.getUsername() : null;
    }

    public boolean isActiveTag() {
        return isActive != null && isActive;
    }
}