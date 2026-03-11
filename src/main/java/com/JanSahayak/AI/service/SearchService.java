package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.DTO.SocialPostDto;
import com.JanSahayak.AI.DTO.SearchDto;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.repository.CommunityRepo;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SearchService {

    private final PostRepo       postRepo;
    private final SocialPostRepo socialPostRepo;
    private final CommunityRepo  communityRepo;
    private final PostService    postService;

    // =========================================================================
    // Public API
    // =========================================================================

    public SearchDto.Response search(SearchDto.Request req) {
        String   query  = req.getQuery().trim();
        Long     cursor = req.getCursor();
        int      limit  = req.safeLimit();
        Pageable probe  = PageRequest.of(0, limit + 1);

        List<SearchDto.Result> posts       = Collections.emptyList();
        List<SearchDto.Result> socialPosts = Collections.emptyList();
        List<SearchDto.Result> communities = Collections.emptyList();
        List<SearchDto.Result> hashtags    = Collections.emptyList();

        if (req.isHashtagSearch()) {
            String tag = req.normalizedHashtag().replaceFirst("^#+", "");
            if (req.includesType("SOCIAL_POST")) socialPosts = fetchSocialPostsByHashtag(tag, cursor, probe);
            if (req.includesType("HASHTAG"))     hashtags    = fetchHashtagRows(tag, limit);
        } else {
            if (req.includesType("POST"))        posts       = fetchPosts(query, req.getPincode(), cursor, probe);
            if (req.includesType("SOCIAL_POST")) socialPosts = fetchSocialPosts(query, req.getPincode(), cursor, probe);
            if (req.includesType("COMMUNITY"))   communities = fetchCommunities(query, req.getPincode(), cursor, probe);
            if (req.includesType("HASHTAG"))     hashtags    = fetchHashtagRows(query, limit);
        }

        List<SearchDto.Result> flat = interleave(posts, socialPosts, communities, hashtags);

        boolean hasMore = flat.size() > limit;
        if (hasMore) flat = new ArrayList<>(flat.subList(0, limit));
        Long nextCursor = hasMore && !flat.isEmpty() ? flat.get(flat.size() - 1).getId() : null;

        Map<String, List<SearchDto.Result>> grouped = new LinkedHashMap<>();
        if (!posts.isEmpty())       grouped.put("POST",        cap(posts, limit));
        if (!socialPosts.isEmpty()) grouped.put("SOCIAL_POST", cap(socialPosts, limit));
        if (!communities.isEmpty()) grouped.put("COMMUNITY",   cap(communities, limit));
        if (!hashtags.isEmpty())    grouped.put("HASHTAG",     hashtags);

        Map<String, Long> countByType = new LinkedHashMap<>();
        countByType.put("POST",        (long) Math.min(posts.size(),       limit));
        countByType.put("SOCIAL_POST", (long) Math.min(socialPosts.size(), limit));
        countByType.put("COMMUNITY",   (long) Math.min(communities.size(), limit));
        countByType.put("HASHTAG",     (long) hashtags.size());

        return SearchDto.Response.builder()
                .query(query)
                .currentCursor(cursor)
                .nextCursor(nextCursor)
                .count(flat.size())
                .limit(limit)
                .hasMore(hasMore)
                .data(flat)
                .grouped(grouped)
                .countByType(countByType)
                .build();
    }

    public PaginatedResponse<SearchDto.Result> searchByType(
            String query, String type, String pincode, Long cursor, int limit) {

        int      safeLimit = Math.min(Math.max(limit, 1), 50);
        Pageable probe     = PageRequest.of(0, safeLimit + 1);
        String   q         = query.trim();

        List<SearchDto.Result> results = switch (type.toUpperCase()) {
            case "POST"        -> fetchPosts(q, pincode, cursor, probe);
            case "SOCIAL_POST" -> fetchSocialPosts(q, pincode, cursor, probe);
            case "COMMUNITY"   -> fetchCommunities(q, pincode, cursor, probe);
            case "HASHTAG"     -> fetchHashtagRows(q, safeLimit);
            default            -> Collections.emptyList();
        };

        boolean hasMore = results.size() > safeLimit;
        if (hasMore) results = new ArrayList<>(results.subList(0, safeLimit));
        Long nextCursor = hasMore && !results.isEmpty() ? results.get(results.size() - 1).getId() : null;

        return PaginatedResponse.of(results, hasMore, nextCursor, safeLimit);
    }

    // =========================================================================
    // Fetchers
    // =========================================================================

    private List<SearchDto.Result> fetchPosts(String q, String pincode, Long cursor, Pageable probe) {
        try {
            boolean hasPincode = pincode != null && pincode.length() == 6;
            List<Post> rows;
            if (hasPincode) {
                rows = (cursor == null)
                        ? postRepo.searchFirstPageByPincode(q, PostStatus.ACTIVE, pincode, probe)
                        : postRepo.searchNextPageByPincode(q, PostStatus.ACTIVE, pincode, cursor, probe);
            } else {
                rows = (cursor == null)
                        ? postRepo.searchFirstPage(q, PostStatus.ACTIVE, probe)
                        : postRepo.searchNextPage(q, PostStatus.ACTIVE, cursor, probe);
            }
            return rows.stream().map(this::mapPost).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Post search error q='{}': {}", q, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SearchDto.Result> fetchSocialPosts(String q, String pincode, Long cursor, Pageable probe) {
        try {
            boolean hasPincode = pincode != null && pincode.length() == 6;
            List<SocialPost> rows;
            if (hasPincode) {
                rows = (cursor == null)
                        ? socialPostRepo.searchFirstPageByPincode(q, PostStatus.ACTIVE, pincode, probe)
                        : socialPostRepo.searchNextPageByPincode(q, PostStatus.ACTIVE, pincode, cursor, probe);
            } else {
                rows = (cursor == null)
                        ? socialPostRepo.searchFirstPage(q, PostStatus.ACTIVE, probe)
                        : socialPostRepo.searchNextPage(q, PostStatus.ACTIVE, cursor, probe);
            }
            return rows.stream().map(this::mapSocialPost).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("SocialPost search error q='{}': {}", q, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SearchDto.Result> fetchSocialPostsByHashtag(String hashtag, Long cursor, Pageable probe) {
        try {
            List<SocialPost> rows = (cursor == null)
                    ? socialPostRepo.searchByHashtagFirstPage(hashtag, PostStatus.ACTIVE, probe)
                    : socialPostRepo.searchByHashtagNextPage(hashtag, PostStatus.ACTIVE, cursor, probe);
            return rows.stream().map(this::mapSocialPost).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Hashtag post search error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SearchDto.Result> fetchCommunities(String q, String pincode, Long cursor, Pageable probe) {
        try {
            List<Community> rows = Collections.emptyList();

            // Try location-scoped search first when pincode is valid
            if (pincode != null && pincode.length() == 6) {
                String dist  = pincode.substring(0, 3);
                String state = pincode.substring(0, 2);
                rows = (cursor == null)
                        ? communityRepo.searchFirstPageByLocation(q, pincode, dist, state, probe)
                        : communityRepo.searchNextPageByLocation(q, pincode, dist, state, cursor, probe);
            }

            // Fall back to global search if location search returned nothing
            if (rows.isEmpty()) {
                rows = (cursor == null)
                        ? communityRepo.searchFirstPage(q, probe)
                        : communityRepo.searchNextPage(q, cursor, probe);
            }

            return rows.stream().map(this::mapCommunity).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Community search error q='{}' pincode='{}': {}", q, pincode, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SearchDto.Result> fetchHashtagRows(String q, int limit) {
        try {
            return socialPostRepo.findTopHashtags(q.replaceFirst("^#+", ""), limit)
                    .stream().map(this::mapHashtagRow).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Hashtag aggregation error (needs MySQL 8+ JSON_TABLE): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<SearchDto.Result> interleave(List<SearchDto.Result> posts,
                                              List<SearchDto.Result> socialPosts,
                                              List<SearchDto.Result> communities,
                                              List<SearchDto.Result> hashtags) {
        List<List<SearchDto.Result>> active = new ArrayList<>();
        for (List<SearchDto.Result> src : List.of(posts, socialPosts, communities, hashtags))
            if (!src.isEmpty()) active.add(new ArrayList<>(src));

        List<SearchDto.Result> out = new ArrayList<>();
        int maxLen = active.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < maxLen; i++)
            for (List<SearchDto.Result> src : active)
                if (i < src.size()) out.add(src.get(i));
        return out;
    }

    private <T> List<T> cap(List<T> list, int limit) {
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    // =========================================================================
    // Mappers
    // =========================================================================

    private SearchDto.Result mapPost(Post p) {
        PostResponse pr = postService.convertToPostResponse(p, null);
        return SearchDto.Result.builder()
                .resultType("POST")
                .id(p.getId())
                .post(pr)
                .build();
    }

    private SearchDto.Result mapSocialPost(SocialPost sp) {
        SocialPostDto dto = SocialPostDto.fromSocialPost(sp);
        return SearchDto.Result.builder()
                .resultType("SOCIAL_POST")
                .id(sp.getId())
                .socialPost(dto)
                .build();
    }

    private SearchDto.Result mapCommunity(Community c) {
        return SearchDto.Result.builder()
                .resultType("COMMUNITY")
                .id(c.getId())
                .communityName(c.getName())
                .communitySlug(c.getSlug())
                .communityAvatarUrl(c.getAvatarUrl())
                .communityDescription(truncate(c.getDescription(), 200))
                .memberCount(c.getMemberCount())
                .privacy(c.getPrivacy() != null ? c.getPrivacy().name() : null)
                .healthScore(c.getHealthScore())
                .pincode(c.getPincode())
                .locationName(c.getLocationName())
                .build();
    }

    private SearchDto.Result mapHashtagRow(Object[] row) {
        String token   = row[0] != null ? row[0].toString() : "";
        long   postCnt = row[1] != null ? ((Number) row[1]).longValue() : 0L;
        String display = token.startsWith("#") ? token : "#" + token;
        return SearchDto.Result.builder()
                .resultType("HASHTAG")
                .hashtag(display)
                .postCount(postCnt)
                .build();
    }

    private String truncate(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) + "..." : s);
    }
}