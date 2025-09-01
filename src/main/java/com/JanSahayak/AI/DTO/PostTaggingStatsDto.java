package com.JanSahayak.AI.DTO;

import lombok.Builder;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostTaggingStatsDto {
    private Long totalPosts;
    private Long taggedPosts;
    private Long untaggedPosts;
    private Long multipleTaggedPosts;
    private Double averageTagsPerPost;
    private Double taggedPostsPercentage;
    private Double untaggedPostsPercentage;
    private Double multipleTaggedPostsPercentage;

    // Additional stats based on entity relationships
    private Long resolvedTaggedPosts; // Resolved posts that are tagged
    private Long unresolvedTaggedPosts; // Unresolved posts that are tagged
    private Double averageResolutionTimeHours; // Average time to resolve tagged posts
    private Long activeTags; // Total active user tags
    private Long inactiveTags; // Total inactive user tags

    // Calculate percentages after setting values
    public void calculatePercentages() {
        if (totalPosts != null && totalPosts > 0) {
            this.taggedPostsPercentage = taggedPosts != null ?
                    (taggedPosts.doubleValue() / totalPosts.doubleValue()) * 100 : 0.0;
            this.untaggedPostsPercentage = untaggedPosts != null ?
                    (untaggedPosts.doubleValue() / totalPosts.doubleValue()) * 100 : 0.0;
            this.multipleTaggedPostsPercentage = multipleTaggedPosts != null ?
                    (multipleTaggedPosts.doubleValue() / totalPosts.doubleValue()) * 100 : 0.0;
        }
    }
}
