package com.JanSahayak.AI.enums;

import lombok.Getter;

@Getter
public enum PostStatus {

    ACTIVE("Active", true, true),
    RESOLVED("Resolved", false, true);

    private final String displayName;
    private final boolean allowsUpdates;
    private final boolean isVisible;

    PostStatus(String displayName, boolean allowsUpdates, boolean isVisible) {
        this.displayName = displayName;
        this.allowsUpdates = allowsUpdates;
        this.isVisible = isVisible;
    }

    public boolean isAllowsUpdates() {
        return allowsUpdates;
    }

    // Alternative method names for boolean fields (more conventional)
    public boolean allowsUpdates() {
        return allowsUpdates;
    }

    /**
     * Check if this status can transition to another status
     *
     * @param targetStatus the target status to transition to
     * @return true if transition is allowed, false otherwise
     */
    public boolean canTransitionTo(PostStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }

        // Same status transitions are always allowed (no-op)
        if (this == targetStatus) {
            return true;
        }

        switch (this) {
            case ACTIVE:
                // ACTIVE posts can be transitioned to RESOLVED
                return targetStatus == RESOLVED;

            case RESOLVED:
                // RESOLVED posts can be transitioned back to ACTIVE
                return targetStatus == ACTIVE;

            default:
                return false;
        }
    }

    /**
     * Get all possible transition targets from current status
     *
     * @return array of possible target statuses
     */
    public PostStatus[] getPossibleTransitions() {
        switch (this) {
            case ACTIVE:
                return new PostStatus[]{RESOLVED};
            case RESOLVED:
                return new PostStatus[]{ACTIVE};
            default:
                return new PostStatus[0];
        }
    }

    /**
     * Check if this status allows any transitions
     *
     * @return true if transitions are possible, false otherwise
     */
    public boolean allowsTransitions() {
        return getPossibleTransitions().length > 0;
    }
    /**
     * Check if this status allows interactions (comments, likes, etc.)
     *
     * @return true if interactions are allowed, false otherwise
     */
    public boolean isInteractable() {
        switch (this) {
            case ACTIVE:
                return true;  // Active posts allow comments and interactions
            case RESOLVED:
                return false; // Resolved posts don't allow new interactions
            default:
                return false;
        }
    }

}