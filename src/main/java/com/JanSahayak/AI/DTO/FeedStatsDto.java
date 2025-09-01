package com.JanSahayak.AI.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class FeedStatsDto {
    private String feedType;
    private Long totalPosts;
    private Long taggedPostsCount;
    private Long sameLocationPosts;
    private Long sameStatePosts;
    private Long countryWidePosts;
    private Long resolvedPosts;
    private Long activePosts;
    private Double resolutionRate;
    private String userLocation;
    private String userRole;
    private Date lastUpdated;
}