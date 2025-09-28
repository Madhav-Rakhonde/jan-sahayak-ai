package com.JanSahayak.AI.DTO;

import com.JanSahayak.AI.model.Comment;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentDto implements Serializable {
    private Long id;
    private String text;
    private Date createdAt;
    private Date updatedAt;
    private AuthorDto author;
    private Long postId;
    private Long parentCommentId;
    private Integer replyCount;
    private Boolean isReply;
    private Integer depthLevel;

    /**
     * Convert Comment entity to CommentDto
     */
    public static CommentDto fromComment(Comment comment) {
        if (comment == null) {
            return null;
        }

        CommentDto dto = new CommentDto();
        dto.setId(comment.getId());
        dto.setText(comment.getText());
        dto.setCreatedAt(comment.getCreatedAt());

        // Convert the user to AuthorDto to prevent LazyInitializationException
        dto.setAuthor(AuthorDto.fromUser(comment.getUser()));

        // Set post ID safely
        if (comment.getPost() != null) {
            dto.setPostId(comment.getPost().getId());
        }

        // Set parent comment ID safely and determine if it's a reply
        if (comment.getParentComment() != null) {
            dto.setParentCommentId(comment.getParentComment().getId());
            dto.setIsReply(true);
        } else {
            dto.setParentCommentId(null);
            dto.setIsReply(false);
        }

        // Set reply count and depth level from Comment entity methods
        dto.setReplyCount(comment.getReplyCount());
        dto.setDepthLevel(comment.getDepthLevel());

        return dto;
    }

    /**
     * Convert Comment entity to CommentDto with custom reply count
     */
    public static CommentDto fromComment(Comment comment, Integer customReplyCount) {
        CommentDto dto = fromComment(comment);
        if (dto != null) {
            dto.setReplyCount(customReplyCount != null ? customReplyCount : 0);
        }
        return dto;
    }

    /**
     * Check if this is a top-level comment (not a reply)
     */
    public boolean isTopLevelComment() {
        return parentCommentId == null || !Boolean.TRUE.equals(isReply);
    }

    /**
     * Check if this comment has replies
     */
    public boolean hasReplies() {
        return replyCount != null && replyCount > 0;
    }

    /**
     * Get safe reply count (never null)
     */
    public Integer getSafeReplyCount() {
        return replyCount != null ? replyCount : 0;
    }

    /**
     * Get safe depth level (never null)
     */
    public Integer getSafeDepthLevel() {
        return depthLevel != null ? depthLevel : 0;
    }

    /**
     * Get author username safely
     */
    public String getAuthorUsername() {
        return author != null ? author.getUsername() : "Unknown";
    }

    /**
     * Get author display name safely
     */
    public String getAuthorDisplayName() {
        return author != null ? author.getDisplayName() : "Unknown";
    }

    /**
     * Check if author is admin
     */
    public boolean isAuthorAdmin() {
        return author != null && author.isAdmin();
    }

    /**
     * Check if author is department
     */
    public boolean isAuthorDepartment() {
        return author != null && author.isDepartment();
    }

    /**
     * Check if author is normal user
     */
    public boolean isAuthorNormalUser() {
        return author != null && author.isNormalUser();
    }

    /**
     * Get author profile image URL
     */
    public String getAuthorProfileImageUrl() {
        return author != null ? author.getProfilePictureUrl() : null;
    }

    /**
     * Check if author has profile image
     */
    public boolean authorHasProfileImage() {
        return author != null && author.hasProfileImage();
    }

    /**
     * Get author pincode
     */
    public String getAuthorPincode() {
        return author != null ? author.getPincode() : null;
    }

    /**
     * Check if author has valid pincode
     */
    public boolean authorHasPincode() {
        return author != null && author.hasPincode();
    }

    /**
     * Get total engagement count (just replies for now)
     */
    public int getTotalEngagementCount() {
        return getSafeReplyCount();
    }

    /**
     * Check if this comment has any activity
     */
    public boolean hasActivity() {
        return hasReplies();
    }

    /**
     * Check if comment text is not empty
     */
    public boolean hasText() {
        return text != null && !text.trim().isEmpty();
    }

    /**
     * Get truncated text for preview
     */
    public String getPreviewText(int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Get time ago string
     */
    public String getTimeAgo() {
        if (createdAt == null) {
            return "Unknown";
        }

        long diffInMillies = new Date().getTime() - createdAt.getTime();
        long diffInMinutes = diffInMillies / (60 * 1000);
        long diffInHours = diffInMillies / (60 * 60 * 1000);
        long diffInDays = diffInMillies / (24 * 60 * 60 * 1000);

        if (diffInMinutes < 1) {
            return "Just now";
        } else if (diffInMinutes < 60) {
            return diffInMinutes + " minute" + (diffInMinutes == 1 ? "" : "s") + " ago";
        } else if (diffInHours < 24) {
            return diffInHours + " hour" + (diffInHours == 1 ? "" : "s") + " ago";
        } else if (diffInDays < 7) {
            return diffInDays + " day" + (diffInDays == 1 ? "" : "s") + " ago";
        } else {
            long weeks = diffInDays / 7;
            return weeks + " week" + (weeks == 1 ? "" : "s") + " ago";
        }
    }

    /**
     * Check if comment was created recently (within last 24 hours)
     */
    public boolean isRecent() {
        if (createdAt == null) return false;
        long diffInMillies = new Date().getTime() - createdAt.getTime();
        return diffInMillies < (24 * 60 * 60 * 1000); // 24 hours
    }

    /**
     * Check if comment was updated (has updatedAt timestamp)
     */
    public boolean wasUpdated() {
        return updatedAt != null &&
                createdAt != null &&
                updatedAt.after(createdAt);
    }
}