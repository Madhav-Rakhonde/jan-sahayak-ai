package com.JanSahayak.AI.DTO;

import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SavedPost;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import lombok.Builder;
import lombok.Getter;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * SavedPostDto
 *
 * Flat, serialization-safe response object returned by the "get saved posts" API.
 * Avoids returning raw JPA entities (which carry lazy proxies and circular references).
 *
 * Each SavedPost row references exactly one of [SocialPost, Post (broadcast)].
 * This DTO normalises both into the same shape so the frontend only needs to
 * handle one object type:
 *
 *   postType = "SOCIAL_POST"   → content came from a SocialPost
 *   postType = "BROADCAST_POST" → content came from a government broadcast Post
 *
 * Static factory methods:
 *   SavedPostDto.fromSocialPost(savedPost)
 *   SavedPostDto.fromBroadcastPost(savedPost)
 *   SavedPostDto.from(savedPost)   ← auto-selects the right factory
 */
@Getter
@Builder
public class SavedPostDto {

    // ── SavedPost metadata ────────────────────────────────────────────────────

    /** SavedPost row id */
    private Long   savedPostId;

    /** When the user saved this post */
    private Date   savedAt;

    // ── Post identity ─────────────────────────────────────────────────────────

    /**
     * The actual post id (SocialPost.id or Post.id).
     * Use together with postType to build deep-link URLs on the frontend.
     */
    private Long   postId;

    /** "SOCIAL_POST" or "BROADCAST_POST" */
    private String postType;

    // ── Post content ─────────────────────────────────────────────────────────

    private String content;

    /**
     * For SocialPost: comma-separated media URLs stored in SocialPost.mediaUrls.
     * For broadcast Post: single imageName (or null).
     * Frontend should split by comma for SocialPost, use as-is for broadcast.
     */
    private String mediaUrls;

    /** Number of media items. 0 for broadcast posts with no image. */
    private int    mediaCount;

    private Date   createdAt;

    private String status;

    // ── Author ────────────────────────────────────────────────────────────────

    private Long   authorId;
    private String authorUsername;
    private String authorProfileImage;

    /**
     * "ADMIN", "DEPARTMENT", or "USER".
     * Lets the frontend badge government broadcasts differently.
     */
    private String authorRole;

    // ── Engagement counts ─────────────────────────────────────────────────────

    private int likeCount;
    private int dislikeCount;
    private int commentCount;
    private int shareCount;
    private int saveCount;
    private int viewCount;

    // ── SocialPost-only extras (null for broadcast posts) ────────────────────

    private String  hashtags;
    private Boolean allowComments;
    private String  locationName;

    // ── Broadcast-only extras (null for social posts) ────────────────────────

    /**
     * Human-readable broadcast scope: "COUNTRY", "STATE", "DISTRICT", "AREA".
     * Null for SocialPosts.
     */
    private String broadcastScope;

    /** Whether the broadcast post is a country-wide government broadcast. */
    private Boolean countryWideBroadcast;

    // =========================================================================
    // STATIC FACTORIES
    // =========================================================================

    /**
     * Auto-select the correct factory based on which side of the SavedPost is populated.
     */
    public static SavedPostDto from(SavedPost savedPost) {
        if (savedPost.isSocialPostSave()) {
            return fromSocialPost(savedPost);
        }
        return fromBroadcastPost(savedPost);
    }

    /**
     * Build a DTO from a SavedPost that references a SocialPost.
     */
    public static SavedPostDto fromSocialPost(SavedPost savedPost) {
        SocialPost sp   = savedPost.getSocialPost();
        User       author = sp.getUser();

        return SavedPostDto.builder()
                // SavedPost metadata
                .savedPostId(savedPost.getId())
                .savedAt(savedPost.getSavedAt())
                // Identity
                .postId(sp.getId())
                .postType("SOCIAL_POST")
                // Content
                .content(sp.getContent())
                .mediaUrls(sp.getMediaUrls())
                .mediaCount(sp.getMediaCount() != null ? sp.getMediaCount() : 0)
                .createdAt(sp.getCreatedAt())
                .status(sp.getStatus() != null ? sp.getStatus().name() : null)
                // Author
                .authorId(author != null ? author.getId() : null)
                .authorUsername(author != null ? author.getActualUsername() : null)
                .authorProfileImage(author != null ? author.getProfileImage() : null)
                .authorRole(resolveRole(author))
                // Engagement
                .likeCount(sp.getLikeCount()    != null ? sp.getLikeCount()    : 0)
                .dislikeCount(sp.getDislikeCount() != null ? sp.getDislikeCount() : 0)
                .commentCount(sp.getCommentCount() != null ? sp.getCommentCount() : 0)
                .shareCount(sp.getShareCount()   != null ? sp.getShareCount()   : 0)
                .saveCount(sp.getSaveCount()    != null ? sp.getSaveCount()    : 0)
                .viewCount(sp.getViewCount()    != null ? sp.getViewCount()    : 0)
                // SocialPost-only extras
                .hashtags(sp.getHashtags())
                .allowComments(sp.getAllowComments())
                .locationName(Boolean.TRUE.equals(sp.getShowLocation()) ? sp.getLocationName() : null)
                // Broadcast-only extras — null for social posts
                .broadcastScope(null)
                .countryWideBroadcast(null)
                .build();
    }

    /**
     * Build a DTO from a SavedPost that references a government broadcast Post.
     */
    public static SavedPostDto fromBroadcastPost(SavedPost savedPost) {
        Post   post   = savedPost.getPost();
        User   author = post.getUser();

        return SavedPostDto.builder()
                // SavedPost metadata
                .savedPostId(savedPost.getId())
                .savedAt(savedPost.getSavedAt())
                // Identity
                .postId(post.getId())
                .postType("BROADCAST_POST")
                // Content
                .content(post.getContent())
                .mediaUrls(post.getImageName())   // single image for broadcast
                .mediaCount(post.getImageName() != null && !post.getImageName().isBlank() ? 1 : 0)
                .createdAt(post.getCreatedAt())
                .status(post.getStatus() != null ? post.getStatus().name() : null)
                // Author
                .authorId(author != null ? author.getId() : null)
                .authorUsername(author != null ? author.getActualUsername() : null)
                .authorProfileImage(author != null ? author.getProfileImage() : null)
                .authorRole(resolveRole(author))
                // Engagement
                .likeCount(post.getLikeCount()    != null ? post.getLikeCount()    : 0)
                .dislikeCount(post.getDislikeCount() != null ? post.getDislikeCount() : 0)
                .commentCount(post.getCommentCount() != null ? post.getCommentCount() : 0)
                .shareCount(post.getShareCount()   != null ? post.getShareCount()   : 0)
                .saveCount(post.getSaveCount()    != null ? post.getSaveCount()    : 0)
                .viewCount(post.getViewCount()    != null ? post.getViewCount()    : 0)
                // SocialPost-only extras — null for broadcast posts
                .hashtags(null)
                .allowComments(null)
                .locationName(null)
                // Broadcast-only extras
                .broadcastScope(post.getBroadcastScope() != null
                        ? post.getBroadcastScope().name() : null)
                .countryWideBroadcast(post.isCountryWideBroadcast())
                .build();
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private static String resolveRole(User user) {
        if (user == null) return null;
        if (user.isAdmin())      return "ADMIN";
        if (user.isDepartment()) return "DEPARTMENT";
        return "USER";
    }
}