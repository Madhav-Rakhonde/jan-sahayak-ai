package com.JanSahayak.AI.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;


/**
 * SavedPost — allows users to bookmark/save a SocialPost OR a government broadcast Post.
 *
 * Business rules:
 *  - SocialPosts can always be saved.
 *  - Regular (issue) Posts CANNOT be saved — only government broadcast Posts can.
 *    A government broadcast Post is one where Post.isGovernmentBroadcast() returns true
 *    (i.e. created by ROLE_ADMIN or ROLE_DEPARTMENT with a broadcastScope set).
 *  - Each user can save any SocialPost / broadcast Post at most once — enforced by DB unique constraints.
 *  - Saving increments the target's saveCount; un-saving decrements it.
 *  - Exactly ONE of [socialPost, post] must be non-null — enforced by @PrePersist XOR check.
 *  - When a SocialPost is soft-deleted the save records are preserved for audit;
 *    the UI hides them because SocialPost.isEligibleForDisplay() returns false.
 *
 * DB migrations required:
 *   ALTER TABLE saved_posts ADD COLUMN post_id BIGINT NULL;
 *   ALTER TABLE saved_posts ADD CONSTRAINT fk_saved_post_post
 *       FOREIGN KEY (post_id) REFERENCES posts(id);
 *   ALTER TABLE saved_posts ADD CONSTRAINT uk_saved_post_user_post
 *       UNIQUE (user_id, post_id);
 *   ALTER TABLE saved_posts MODIFY COLUMN social_post_id BIGINT NULL;  -- was NOT NULL
 */
@Entity
@Table(
        name = "saved_posts",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"user_id", "social_post_id"},
                        name = "uk_saved_post_user_social_post"
                ),
                @UniqueConstraint(
                        columnNames = {"user_id", "post_id"},
                        name = "uk_saved_post_user_post"
                )
        },
        indexes = {
                @Index(name = "idx_saved_post_user",        columnList = "user_id"),
                @Index(name = "idx_saved_post_social_post", columnList = "social_post_id"),
                @Index(name = "idx_saved_post_post",        columnList = "post_id"),
                @Index(name = "idx_saved_post_saved_at",    columnList = "saved_at"),
                @Index(name = "idx_saved_post_user_date",   columnList = "user_id, saved_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== Relationships =====

    /** The user who bookmarked the post. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_saved_post_user"))
    private User user;

    /**
     * The SocialPost that was bookmarked.
     * Null when this record refers to a government broadcast Post instead.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "social_post_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_saved_post_social_post"))
    private SocialPost socialPost;

    /**
     * The government broadcast Post that was bookmarked.
     * Null when this record refers to a SocialPost instead.
     * Only Posts where Post.isGovernmentBroadcast() == true may be saved.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_saved_post_post"))
    private Post post;

    // ===== Timestamps =====

    @Column(name = "saved_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Builder.Default
    private Date savedAt = new Date();

    // ===== Helper Methods =====

    public Long getUserId()       { return user       != null ? user.getId()       : null; }
    public Long getSocialPostId() { return socialPost != null ? socialPost.getId() : null; }
    public Long getPostId()       { return post       != null ? post.getId()       : null; }

    public boolean isSocialPostSave()    { return socialPost != null; }
    public boolean isBroadcastPostSave() { return post != null; }

    // ===== Lifecycle Validation =====

    /**
     * Ensures exactly ONE of [socialPost, post] is set before every INSERT.
     * Saves against a plain issue Post (non-broadcast) are also rejected here as
     * an extra safety net — the service layer is the primary enforcement point.
     */
    @PrePersist
    private void prePersist() {
        boolean hasSocialPost = socialPost != null;
        boolean hasPost       = post       != null;
        if (hasSocialPost == hasPost) {   // both true OR both false
            throw new IllegalStateException(
                    "SavedPost must reference exactly ONE of [SocialPost, Post], not both or neither.");
        }
        if (hasPost && !post.isGovernmentBroadcast()) {
            throw new IllegalStateException(
                    "Only government broadcast Posts may be saved. postId=" + post.getId());
        }
        if (savedAt == null) {
            savedAt = new Date();
        }
    }

    @Override
    public String toString() {
        if (isSocialPostSave()) {
            return String.format("SavedPost{id=%d, userId=%d, socialPostId=%d}",
                    id, getUserId(), getSocialPostId());
        }
        return String.format("SavedPost{id=%d, userId=%d, broadcastPostId=%d}",
                id, getUserId(), getPostId());
    }
}