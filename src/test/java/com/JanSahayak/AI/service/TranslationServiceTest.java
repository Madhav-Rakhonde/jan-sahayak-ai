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
        translationService.setSelf(translationService);
    }

    @Test
    void testTranslationAPI() {
        // Mock cache miss (empty list)
        when(translationRepository.findByReferenceIdInAndReferenceTypeAndTargetLanguage(anyList(), eq("POST"), anyString()))
                .thenReturn(Collections.emptyList());

        // Mock RestTemplate response for Lingva API
        String mockLingvaResponse = "{\"translation\":\"नमस्ते दुनिया\"}";
        when(restTemplate.getForObject(eq("https://lingva.ml/api/v1/auto/{target}/{query}"), eq(String.class), anyString(), anyString()))
                .thenReturn(mockLingvaResponse);

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

        // Mock RestTemplate response for Post 2 (Lingva API)
        String mockLingvaResponse = "{\"translation\":\"अलविदा\"}";
        when(restTemplate.getForObject(eq("https://lingva.ml/api/v1/auto/{target}/{query}"), eq(String.class), eq("hi"), eq("Goodbye")))
                .thenReturn(mockLingvaResponse);

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

        // P1 takes 3000ms
        when(restTemplate.getForObject(eq("https://lingva.ml/api/v1/auto/{target}/{query}"), eq(String.class), eq("hi"), eq("Post1")))
                .thenAnswer(invocation -> {
                    Thread.sleep(3000);
                    return "{\"translation\":\"पोस्ट1\"}";
                });

        // P2 takes 3000ms
        when(restTemplate.getForObject(eq("https://lingva.ml/api/v1/auto/{target}/{query}"), eq(String.class), eq("hi"), eq("Post2")))
                .thenAnswer(invocation -> {
                    Thread.sleep(3000);
                    return "{\"translation\":\"पोस्ट2\"}";
                });

        List<PostResponse> posts = new ArrayList<>();
        PostResponse p1 = PostResponse.builder().id(1L).content("Post1").build();
        PostResponse p2 = PostResponse.builder().id(2L).content("Post2").build();
        posts.add(p1);
        posts.add(p2);

        long startTime = System.currentTimeMillis();
        translationService.translatePosts(posts, "hi");
        long duration = System.currentTimeMillis() - startTime;

        // Total timeout is 5000ms, so it should finish in around 5000ms
        assertTrue(duration < 5500, "Method should complete before 5.5 seconds due to timeout");
        assertTrue(duration >= 4900, "Method should take around 5 seconds due to timeout");

        // P1 should be translated (completes in 3s < 5s)
        assertEquals("पोस्ट1", p1.getTranslatedContent());
        assertTrue(p1.getIsTranslated());

        // P2 should not be translated (started at 3s, takes 3s, times out at 2s remaining)
        assertNull(p2.getTranslatedContent());
        assertFalse(Boolean.TRUE.equals(p2.getIsTranslated()));
    }

    @Test
    void testSaveTranslationsInNewTransaction_HandlesDatabaseErrorGracefully() {
        // Arrange
        PostTranslation pt1 = PostTranslation.builder()
                .referenceId(1L)
                .referenceType("POST")
                .targetLanguage("hi")
                .translatedText("नमस्ते")
                .build();
        List<PostTranslation> list = List.of(pt1);
        
        when(translationRepository.saveAll(anyList()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key violation"));
        
        // Act & Assert
        assertDoesNotThrow(() -> translationService.saveTranslationsInNewTransaction(list));
        verify(translationRepository, times(1)).saveAll(list);
    }

    @Test
    void testTranslateText_LingvaSuccess() {
        String mockLingvaResponse = "{\"translation\":\"नमस्ते\"}";
        when(restTemplate.getForObject(eq("https://lingva.ml/api/v1/auto/{target}/{query}"), eq(String.class), eq("hi"), eq("Hello")))
                .thenReturn(mockLingvaResponse);

        String result = translationService.translateText("Hello", "hi");

        assertEquals("नमस्ते", result);
        verify(restTemplate, times(1)).getForObject(contains("lingva.ml"), any(), anyString(), anyString());
        verify(restTemplate, never()).getForObject(contains("mymemory"), any(), anyString(), anyString());
    }

    @Test
    void testTranslateText_LingvaFails_MyMemorySuccess() {
        // Lingva fails
        when(restTemplate.getForObject(eq("https://lingva.ml/api/v1/auto/{target}/{query}"), eq(String.class), eq("hi"), eq("Hello")))
                .thenThrow(new RuntimeException("Lingva is down"));

        // Google dict-chrome-ex fails
        when(restTemplate.getForObject(eq("https://translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl=auto&tl={target}&dt=t&q={query}"), eq(String.class), eq("hi"), eq("Hello")))
                .thenThrow(new RuntimeException("Google is down"));

        // MyMemory succeeds
        String mockMyMemoryResponse = "{\"responseData\":{\"translatedText\":\"नमस्ते\"}}";
        when(restTemplate.getForObject(eq("https://api.mymemory.translated.net/get?q={query}&langpair=Autodetect|{target}&de=support@example.com"), eq(String.class), eq("Hello"), eq("hi")))
                .thenReturn(mockMyMemoryResponse);

        String result = translationService.translateText("Hello", "hi");

        assertEquals("नमस्ते", result);
        verify(restTemplate, times(1)).getForObject(contains("lingva.ml"), any(), anyString(), anyString());
        verify(restTemplate, times(1)).getForObject(contains("translate.googleapis.com"), any(), anyString(), anyString());
        verify(restTemplate, times(1)).getForObject(contains("mymemory"), any(), anyString(), anyString());
    }

    @Test
    void testTranslateText_LingvaFails_GoogleSuccess() {
        // Lingva fails
        when(restTemplate.getForObject(eq("https://lingva.ml/api/v1/auto/{target}/{query}"), eq(String.class), eq("hi"), eq("Hello")))
                .thenThrow(new RuntimeException("Lingva is down"));

        // Google dict-chrome-ex succeeds
        String mockGoogleResponse = "[[[\"नमस्ते\",\"Hello\",null,null,10]]]";
        when(restTemplate.getForObject(eq("https://translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl=auto&tl={target}&dt=t&q={query}"), eq(String.class), eq("hi"), eq("Hello")))
                .thenReturn(mockGoogleResponse);

        String result = translationService.translateText("Hello", "hi");

        assertEquals("नमस्ते", result);
        verify(restTemplate, times(1)).getForObject(contains("lingva.ml"), any(), anyString(), anyString());
        verify(restTemplate, times(1)).getForObject(contains("translate.googleapis.com"), any(), anyString(), anyString());
        verify(restTemplate, never()).getForObject(contains("mymemory"), any(), anyString(), anyString());
    }

    @Test
    void testTranslateText_AllFail_ReturnsOriginalText() {
        // Lingva fails
        when(restTemplate.getForObject(eq("https://lingva.ml/api/v1/auto/{target}/{query}"), eq(String.class), eq("hi"), eq("Hello")))
                .thenThrow(new RuntimeException("Lingva is down"));

        // Google dict-chrome-ex fails
        when(restTemplate.getForObject(eq("https://translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl=auto&tl={target}&dt=t&q={query}"), eq(String.class), eq("hi"), eq("Hello")))
                .thenThrow(new RuntimeException("Google is down"));

        // MyMemory fails
        when(restTemplate.getForObject(eq("https://api.mymemory.translated.net/get?q={query}&langpair=Autodetect|{target}&de=support@example.com"), eq(String.class), eq("Hello"), eq("hi")))
                .thenThrow(new RuntimeException("MyMemory is down"));

        String result = translationService.translateText("Hello", "hi");

        assertEquals("Hello", result); // Original text fallback
        verify(restTemplate, times(1)).getForObject(contains("lingva.ml"), any(), anyString(), anyString());
        verify(restTemplate, times(1)).getForObject(contains("translate.googleapis.com"), any(), anyString(), anyString());
        verify(restTemplate, times(1)).getForObject(contains("mymemory"), any(), anyString(), anyString());
    }
}
