package com.JanSahayak.AI.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Poll entity — always attached to a SocialPost.
 * One SocialPost can have at most ONE poll.
 *
 * Lifecycle:
 *   User submits poll form → SocialPost is created → Poll is created → PollOptions are created
 *   All handled automatically in PollService.createPollPost()
 */
@Entity
@Table(name = "polls", indexes = {
        @Index(name = "idx_poll_social_post",  columnList = "social_post_id"),
        @Index(name = "idx_poll_created_by",   columnList = "created_by_user_id"),
        @Index(name = "idx_poll_expires_at",   columnList = "expires_at"),
        @Index(name = "idx_poll_is_active",    columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Poll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== Core =====

    /** The poll question — same text as SocialPost.content */
    @Column(name = "question", nullable = false, length = 500)
    private String question;

    /** The SocialPost this poll belongs to */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "social_post_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_poll_social_post"))
    private SocialPost socialPost;

    /** User who created the poll */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_poll_created_by"))
    private User createdBy;

    // ===== Options =====

    /** 2 to 4 options */
    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PollOption> options = new ArrayList<>();

    // ===== Settings =====

    /** When voting closes — null means no expiry */
    @Column(name = "expires_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date expiresAt;

    /** false = one vote per user (default), true = user can select multiple options */
    @Column(name = "allow_multiple_votes", nullable = false)
    @Builder.Default
    private Boolean allowMultipleVotes = false;

    /**
     * true  = everyone sees results while poll is open (default, like Twitter)
     * false = results hidden until user votes or poll expires
     */
    @Column(name = "show_results_before_expiry", nullable = false)
    @Builder.Default
    private Boolean showResultsBeforeExpiry = true;

    // ===== Counters =====

    /** Total votes across all options — denormalized for fast reads */
    @Column(name = "total_votes", nullable = false)
    @Builder.Default
    private Integer totalVotes = 0;

    // ===== Status =====

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

    // ===== Helper Methods =====

    public boolean isExpired() {
        return expiresAt != null && new Date().after(expiresAt);
    }

    public boolean isOpenForVoting() {
        return Boolean.TRUE.equals(isActive) && !isExpired();
    }

    /**
     * Should this user see vote counts right now?
     * @param userHasVoted whether the requesting user has already voted
     */
    public boolean shouldShowResults(boolean userHasVoted) {
        if (isExpired()) return true;                          // always show after expiry
        if (Boolean.TRUE.equals(showResultsBeforeExpiry)) return true;  // open results mode
        return userHasVoted;                                   // only show after voting
    }

    public void incrementTotalVotes() {
        this.totalVotes = (this.totalVotes != null ? this.totalVotes : 0) + 1;
    }

    public void decrementTotalVotes() {
        this.totalVotes = Math.max(0, (this.totalVotes != null ? this.totalVotes : 0) - 1);
    }

    public void deactivate() {
        this.isActive = false;
        this.updatedAt = new Date();
    }

    public boolean hasValidOptionCount() {
        return options != null && options.size() >= 2 && options.size() <= 4;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = new Date();
    }
}