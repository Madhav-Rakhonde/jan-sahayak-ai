package com.JanSahayak.AI.enums;

import lombok.Getter;
import lombok.Setter;

@Getter
public enum PostStatus {

    ACTIVE("Active", "Post is active and awaiting response", "🟢", true, true),
    RESOLVED("Resolved", "Issue has been resolved", "✅", false, true);

    private final String displayName;
    private final String description;
    private final String icon;
    private final boolean allowsUpdates; // Whether status allows further updates
    private final boolean isVisible; // Whether post is visible to users

    PostStatus(String displayName, String description, String icon, boolean allowsUpdates, boolean isVisible) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.allowsUpdates = allowsUpdates;
        this.isVisible = isVisible;
    }

    public boolean allowsUpdates() {
        return allowsUpdates;
    }


    /**
     * Check if post is resolved
     */
    public boolean isResolved() {
        return this == RESOLVED;
    }

    /**
     * Check if post is still active (can receive interactions)
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * Check if post can be interacted with (comments, likes)
     */
    public boolean isInteractable() {
        return isVisible;
    }

    /**
     * Check if post is in a final state (no more updates expected)
     */
    public boolean isFinalState() {
        return this == RESOLVED;
    }

    /**
     * Check if post can be resolved by tagged users
     */
    public boolean canBeResolvedByUsers() {
        return this == ACTIVE;
    }

    /**
     * Get next possible statuses from current status
     */
    public PostStatus[] getPossibleTransitions() {
        switch (this) {
            case ACTIVE:
                return new PostStatus[]{RESOLVED};
            case RESOLVED:
                return new PostStatus[]{ACTIVE}; // Can be reopened
            default:
                return new PostStatus[]{};
        }
    }

    /**
     * Check if transition to another status is allowed
     */
    public boolean canTransitionTo(PostStatus newStatus) {
        PostStatus[] transitions = getPossibleTransitions();
        for (PostStatus transition : transitions) {
            if (transition == newStatus) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get status category for filtering
     */
    public String getCategory() {
        switch (this) {
            case ACTIVE:
                return "Active";
            case RESOLVED:
                return "Resolved";
            default:
                return "Other";
        }
    }

    /**
     * Get priority for sorting (higher number = higher priority)
     */
    public int getPriority() {
        switch (this) {
            case ACTIVE:
                return 2;
            case RESOLVED:
                return 1;
            default:
                return 0;
        }
    }

    /**
     * Check if post should appear in user feeds
     */
    public boolean isInUserFeed() {
        return isVisible;
    }

    /**
     * Check if post should appear in recommendations
     */
    public boolean isRecommendable() {
        return this == ACTIVE;
    }
}