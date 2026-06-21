package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.DTO.SearchDto;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.repository.CommunityRepo;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SearchServiceTest {

    @Mock
    private PostRepo postRepo;

    @Mock
    private SocialPostRepo socialPostRepo;

    @Mock
    private CommunityRepo communityRepo;

    @Mock
    private PostService postService;

    @InjectMocks
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        // Default lenient mocks to return empty lists unless stubbed
        lenient().when(postRepo.searchPage(any(), any(), any())).thenReturn(Collections.emptyList());
        lenient().when(socialPostRepo.searchPage(any(), any(), any())).thenReturn(Collections.emptyList());
        lenient().when(communityRepo.searchPage(any(), any())).thenReturn(Collections.emptyList());
        lenient().when(socialPostRepo.findTopHashtags(any(), anyInt())).thenReturn(Collections.emptyList());
    }

    @Test
    void testSearchInterleavesResultsCorrectlyWithOffsetPagination() {
        // Arrange
        String query = "water";
        int limit = 5;
        
        SearchDto.Request request = new SearchDto.Request();
        request.setQuery(query);
        request.setLimit(limit);
        request.setPage(0); // First page
        
        // Mock 3 Posts
        List<Post> posts = new ArrayList<>();
        for (long i = 1; i <= 3; i++) {
            Post p = new Post();
            p.setId(i);
            posts.add(p);
        }
        
        // Mock 2 SocialPosts
        List<SocialPost> socialPosts = new ArrayList<>();
        for (long i = 1; i <= 2; i++) {
            SocialPost sp = new SocialPost();
            sp.setId(i + 100);
            socialPosts.add(sp);
        }
        
        // Mock 1 Community
        List<Community> communities = new ArrayList<>();
        Community c = new Community();
        c.setId(201L);
        c.setName("Water Savers");
        communities.add(c);
        
        when(postRepo.searchPage(eq("water"), eq(PostStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(posts);
        when(socialPostRepo.searchPage(eq("water"), eq(PostStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(socialPosts);
        when(communityRepo.searchPage(eq("water"), any(Pageable.class)))
                .thenReturn(communities);
                
        // Mock mappings
        when(postService.convertToPostResponse(any(Post.class), isNull())).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            PostResponse pr = new PostResponse();
            pr.setId(p.getId());
            return pr;
        });

        // Act
        SearchDto.Response response = searchService.search(request);

        // Assert
        assertNotNull(response);
        assertEquals("water", response.getQuery());
        assertEquals(0, response.getCurrentPage());
        assertEquals(5, response.getLimit());
        
        // Total found items is 3 + 2 + 1 = 6. 
        // Interleaving order (Post, SocialPost, Community):
        // 1: Post(1)
        // 2: SocialPost(101)
        // 3: Community(201)
        // 4: Post(2)
        // 5: SocialPost(102)
        // --- Limit of 5 reached here ---
        // The remaining item Post(3) makes hasMore = true and triggers nextPage = 1.
        
        assertTrue(response.isHasMore());
        assertEquals(1, response.getNextPage()); // Since hasMore is true, nextPage is page + 1
        assertEquals(5, response.getData().size()); // Capped at limit of 5
        
        // Verify Interleaving Pattern
        assertEquals("POST", response.getData().get(0).getResultType());
        assertEquals("SOCIAL_POST", response.getData().get(1).getResultType());
        assertEquals("COMMUNITY", response.getData().get(2).getResultType());
        assertEquals("POST", response.getData().get(3).getResultType());
        assertEquals("SOCIAL_POST", response.getData().get(4).getResultType());
    }

    @Test
    void testSearchHashtagOnly() {
        // Arrange
        SearchDto.Request request = new SearchDto.Request();
        request.setQuery("#environment");
        request.setLimit(20);
        request.setPage(1); // Page 2
        request.setTypes(Collections.singleton("HASHTAG"));
        
        // Return 1 hashtag aggregation result
        List<Object[]> hashtagRows = new ArrayList<>();
        hashtagRows.add(new Object[]{"environment", 45L});
        
        when(socialPostRepo.findTopHashtags(eq("environment"), eq(20)))
                .thenReturn(hashtagRows);

        // Act
        SearchDto.Response response = searchService.search(request);

        // Assert
        assertEquals(1, response.getData().size());
        assertEquals("HASHTAG", response.getData().get(0).getResultType());
        assertEquals("#environment", response.getData().get(0).getHashtag());
        assertEquals(45L, response.getData().get(0).getPostCount());
        
        assertFalse(response.isHasMore());
        assertNull(response.getNextPage());
    }

    @Test
    void testSearchByTypePagination() {
        // Arrange
        String query = "test";
        String type = "POST";
        String pincode = null;
        Integer page = 2;
        int limit = 2; // Very small limit to test pagination calculation
        
        List<Post> posts = new ArrayList<>();
        for (long i = 1; i <= 3; i++) { // Fetching 3 items (limit + 1)
            Post p = new Post();
            p.setId(i);
            posts.add(p);
        }
        
        when(postRepo.searchPage(eq("test"), eq(PostStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(posts);
                
        when(postService.convertToPostResponse(any(Post.class), isNull())).thenAnswer(inv -> new PostResponse());

        // Act
        var response = searchService.searchByType(query, type, pincode, page, limit);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getData().size()); // Capped to 2
        assertTrue(response.isHasMore());
        assertEquals(3L, response.getNextCursor()); // nextCursor stores nextPage (2+1 = 3)
    }
}
