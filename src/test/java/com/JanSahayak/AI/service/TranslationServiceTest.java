package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.DTO.SocialPostDto;
import com.JanSahayak.AI.model.PostTranslation;
import com.JanSahayak.AI.repository.PostTranslationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;
import org.mockito.Spy;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TranslationServiceTest {

    @Mock
    private PostTranslationRepository translationRepository;

    @Mock
    private RestTemplate restTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testTranslationAPI() {
        // Mock cache miss (empty list)
        when(translationRepository.findByReferenceIdInAndReferenceTypeAndTargetLanguage(anyList(), eq("POST"), anyString()))
                .thenReturn(Collections.emptyList());

        // Mock RestTemplate response
        String mockGoogleResponse = "[[[\"नमस्ते दुनिया\",\"Hello world\",null,null,1]],null,\"en\",null,null,null,1,[]]";
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(mockGoogleResponse);

        List<PostResponse> posts = new ArrayList<>();
        PostResponse post = new PostResponse();
        post.setId(1L);
        post.setContent("Hello world");
        posts.add(post);

        translationService.translatePosts(posts, "hi");

        assertEquals("नमस्ते दुनिया", posts.get(0).getTranslatedContent());
        assertTrue(posts.get(0).getIsTranslated());
    }

    @Test
    void testTranslatePostsBatchCachedAndMiss() {
        // Post 1 is cached, Post 2 is cache miss
        List<PostTranslation> cachedList = new ArrayList<>();
        cachedList.add(PostTranslation.builder()
                .referenceId(1L)
                .referenceType("POST")
                .targetLanguage("hi")
                .translatedText("नमस्ते")
                .build());

        when(translationRepository.findByReferenceIdInAndReferenceTypeAndTargetLanguage(anyList(), eq("POST"), eq("hi")))
                .thenReturn(cachedList);

        // Mock RestTemplate response for Post 2
        String mockGoogleResponse = "[[[\"अलविदा\",\"Goodbye\",null,null,1]],null,\"en\",null,null,null,1,[]]";
        when(restTemplate.getForObject(contains("Goodbye"), eq(String.class)))
                .thenReturn(mockGoogleResponse);

        List<PostResponse> posts = new ArrayList<>();
        PostResponse p1 = PostResponse.builder().id(1L).content("Hello").build();
        PostResponse p2 = PostResponse.builder().id(2L).content("Goodbye").build();
        posts.add(p1);
        posts.add(p2);

        translationService.translatePosts(posts, "hi");

        // Verify P1 mapped from cache
        assertEquals("नमस्ते", p1.getTranslatedContent());
        assertTrue(p1.getIsTranslated());

        // Verify P2 translated from API
        assertEquals("अलविदा", p2.getTranslatedContent());
        assertTrue(p2.getIsTranslated());

        // Verify saveAll called once with newly translated items
        ArgumentCaptor<List<PostTranslation>> captor = ArgumentCaptor.forClass(List.class);
        verify(translationRepository, times(1)).saveAll(captor.capture());
        List<PostTranslation> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(2L, saved.get(0).getReferenceId());
        assertEquals("POST", saved.get(0).getReferenceType());
        assertEquals("hi", saved.get(0).getTargetLanguage());
        assertEquals("अलविदा", saved.get(0).getTranslatedText());
    }

    @Test
    void testTranslatePostsTimeout() {
        when(translationRepository.findByReferenceIdInAndReferenceTypeAndTargetLanguage(anyList(), eq("POST"), eq("hi")))
                .thenReturn(Collections.emptyList());

        // Mock P1 to hang (take 3 seconds, exceeding the 2-second timeout)
        when(restTemplate.getForObject(contains("SlowPost"), eq(String.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(3000);
                    return "[[[\"धीमा\",\"SlowPost\",null,null,1]],null,\"en\",null,null,null,1,[]]";
                });

        // Mock P2 to return immediately
        when(restTemplate.getForObject(contains("FastPost"), eq(String.class)))
                .thenReturn("[[[\"तेज\",\"FastPost\",null,null,1]],null,\"en\",null,null,null,1,[]]");

        List<PostResponse> posts = new ArrayList<>();
        PostResponse p1 = PostResponse.builder().id(1L).content("SlowPost").build();
        PostResponse p2 = PostResponse.builder().id(2L).content("FastPost").build();
        posts.add(p1);
        posts.add(p2);

        // Run the translation and verify it completes within 2.5 seconds (gracefully handles timeout)
        long startTime = System.currentTimeMillis();
        translationService.translatePosts(posts, "hi");
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < 2500, "Method should complete before 2.5 seconds despite one task taking 3 seconds");

        // Fast post should be successfully translated
        assertEquals("तेज", p2.getTranslatedContent());
        assertTrue(p2.getIsTranslated());

        // Slow post should not be translated (graceful degradation)
        assertNull(p1.getTranslatedContent());
        assertFalse(Boolean.TRUE.equals(p1.getIsTranslated()));
    }
}
