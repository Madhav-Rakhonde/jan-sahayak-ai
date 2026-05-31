package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.model.PostTranslation;
import com.JanSahayak.AI.repository.PostTranslationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class TranslationServiceTest {

    @Mock
    private PostTranslationRepository translationRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testTranslationAPI() {
        // Mock DB cache miss
        when(translationRepository.findByReferenceIdAndReferenceTypeAndTargetLanguage(any(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Mock RestTemplate response from Google API format
        String mockGoogleResponse = "[[[\"नमस्ते दुनिया\",\"Hello world\",null,null,1]],null,\"en\",null,null,null,1,[]]";
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockGoogleResponse, HttpStatus.OK));

        // Mock DB save
        when(translationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PostResponse> posts = new ArrayList<>();
        PostResponse post = new PostResponse();
        post.setId(1L);
        post.setContent("Hello world");
        posts.add(post);

        translationService.translatePosts(posts, "hi");

        assertEquals("नमस्ते दुनिया", posts.get(0).getTranslatedContent());
        assertTrue(posts.get(0).getIsTranslated());
        System.out.println("Test successful! Translation received: " + posts.get(0).getTranslatedContent());
    }
}
