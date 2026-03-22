package com.JanSahayak.AI.DTO;

import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SavedPost;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import lombok.Builder;
import lombok.Getter;

import java.util.Date;

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
 *   postType = "SOCIAL_POST"    → content came from a SocialPost
 *   postType = "BROADCAST_POST" → content came from a government broadcast Post
 *
 * Static factory methods:
 *   SavedPostDto.fromSocialPost(savedPost)
 *   SavedPostDto.fromBroadcastPost(savedPost)
 *   SavedPostDto.from(savedPost)   ← auto-selects the right factory
 *
 * ── FIX: int → Integer for nullable count fields ──────────────────────────────
 * The engagement count getters on Post (getLikeCount, getDislikeCount, etc.) return
 * primitive {@code int}.  Comparing a primitive to {@code null} is a compile error:
 *   "bad operand types for binary operator '!=' — first type: int, second type: <nulltype>"
 *
 * Root cause: the DTO builder's .likeCount(...) etc. methods were being called with
 * ternary expressions like {@code post.getLikeCount() != null ? post.getLikeCount() : 0}
 * but since {@code getLikeCount()} returns primitive {@code int} the {@code != null}
 * check is illegal.
 *
 * Fix applied: the six engagement-count fields are declared as {@code Integer}
 * (boxed wrapper) instead of {@code int}.  This means:
 *  - The null checks in fromBroadcastPost() are simply removed — primitives are
 *    used directly (no NPE risk since they are value types).
 *  - The null checks in fromSocialPost() remain valid because SocialPost's getters
 *    return {@code Integer} (boxed), which CAN be null.
 *  - The DTO serialises to JSON identically: Jackson maps both {@code int} and
 *    {@code Integer} to a JSON number; null Integer fields serialise to JSON null.
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
    private Integer mediaCount;

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
    // FIX: declared as Integer (boxed) instead of int (primitive).
    // Reason: fromBroadcastPost() calls post.getLikeCount() etc. which return
    // primitive int.  Comparing a primitive to null is a compile error.
    // With Integer, null-safety is preserved for SocialPost (whose getters return
    // Integer) and the Post path is used directly without a null check.

    private Integer likeCount;
    private Integer dislikeCount;
    private Integer commentCount;
    private Integer shareCount;
    private Integer saveCount;
    private Integer viewCount;

    // ── SocialPost-only extras (null for broadcast posts) ────────────────────

    private String  hashtags;
    private Boolean allowComments;
    private String  locationName;

    // ── Broadcast-only extras (null for social posts) ────────────────────────

    /**
     * Human-readable broadcast scope: "COUNTRY", "STATE", "DISTRICT", "AREA".
     * Null for SocialPosts.
     */
    private String  broadcastScope;

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
     *
     * SocialPost getters (getLikeCount, etc.) return Integer (boxed) so null checks
     * are valid and the existing ternary fallback-to-zero pattern is preserved.
     */
    public static SavedPostDto fromSocialPost(SavedPost savedPost) {
        SocialPost sp     = savedPost.getSocialPost();
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
                // Engagement — SocialPost getters return Integer (nullable), keep null-safe ternary
                .likeCount(sp.getLikeCount()       != null ? sp.getLikeCount()       : 0)
                .dislikeCount(sp.getDislikeCount() != null ? sp.getDislikeCount()    : 0)
                .commentCount(sp.getCommentCount() != null ? sp.getCommentCount()    : 0)
                .shareCount(sp.getShareCount()     != null ? sp.getShareCount()      : 0)
                .saveCount(sp.getSaveCount()       != null ? sp.getSaveCount()       : 0)
                .viewCount(sp.getViewCount()       != null ? sp.getViewCount()       : 0)
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
     *
     * FIX: Post getters (getLikeCount, etc.) return primitive int, NOT Integer.
     * The original code had {@code post.getLikeCount() != null ? ... : 0} which
     * fails to compile because int cannot be compared to null.
     *
     * Fix: use the primitive values directly — no null check needed for primitives.
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
                // Engagement — Post getters return primitive int; use directly (no null check)
                .likeCount(post.getLikeCount())
                .dislikeCount(post.getDislikeCount())
                .commentCount(post.getCommentCount())
                .shareCount(post.getShareCount())
                .saveCount(post.getSaveCount())
                .viewCount(post.getViewCount())
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