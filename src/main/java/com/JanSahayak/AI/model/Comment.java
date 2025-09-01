package com.JanSahayak.AI.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String text;

    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();

    // Existing Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    // ✅ ADDED: Self-referential relationship for reply functionality
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private Comment parentComment;

    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Comment> replies = new ArrayList<>();

    // ===== Helper Methods =====

    /**
     * Check if this is a top-level comment (not a reply)
     */
    public boolean isTopLevelComment() {
        return parentComment == null;
    }

    /**
     * Check if this is a reply to another comment
     */
    public boolean isReply() {
        return parentComment != null;
    }

    /**
     * Get total reply count
     */
    public int getReplyCount() {
        return replies != null ? replies.size() : 0;
    }

    /**
     * Add a reply to this comment
     */
    public void addReply(Comment reply) {
        if (replies == null) {
            replies = new ArrayList<>();
        }
        replies.add(reply);
        reply.setParentComment(this);
    }

    /**
     * Remove a reply from this comment
     */
    public void removeReply(Comment reply) {
        if (replies != null) {
            replies.remove(reply);
            reply.setParentComment(null);
        }
    }

    /**
     * Get the depth level of this comment (0 for top-level, 1+ for replies)
     */
    public int getDepthLevel() {
        if (parentComment == null) {
            return 0;
        }
        return parentComment.getDepthLevel() + 1;
    }

    /**
     * Get the root comment of this comment thread
     */
    public Comment getRootComment() {
        Comment current = this;
        while (current.getParentComment() != null) {
            current = current.getParentComment();
        }
        return current;
    }

    /**
     * Check if this comment has any replies
     */
    public boolean hasReplies() {
        return replies != null && !replies.isEmpty();
    }

    // ===== Combined Helper Methods =====

    /**
     * Get total engagement count (just replies now)
     */
    public int getTotalEngagementCount() {
        return getReplyCount();
    }

    /**
     * Check if this comment has any activity
     */
    public boolean hasActivity() {
        return hasReplies();
    }
}