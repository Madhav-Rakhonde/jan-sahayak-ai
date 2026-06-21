package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.DTO.SocialPostDto;
import com.JanSahayak.AI.model.PostTranslation;
import com.JanSahayak.AI.repository.PostTranslationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Collections;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationService {

    // Limit to exactly 1 concurrent translation request to avoid 429s from free API
    private final java.util.concurrent.Semaphore googleApiSemaphore = new java.util.concurrent.Semaphore(1);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PostTranslationRepository postTranslationRepository;

    private TranslationService self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy TranslationService self) {
        this.self = self;
    }

    /**
     * Translates a batch of SocialPostDto.
     */
    public void translateSocialPosts(List<SocialPostDto> dtos, String targetLanguage) {
        if (dtos == null || dtos.isEmpty() || targetLanguage == null || targetLanguage.equalsIgnoreCase("en")) {
            return;
        }

        List<Long> postIds = dtos.stream().map(SocialPostDto::getId).collect(Collectors.toList());
        
        // Fetch cached translations
        List<PostTranslation> cachedTranslations = postTranslationRepository
                .findByReferenceIdInAndReferenceTypeAndTargetLanguage(postIds, "SOCIAL_POST", targetLanguage);
        
        Map<Long, String> cacheMap = cachedTranslations.stream()
                .collect(Collectors.toMap(PostTranslation::getReferenceId, PostTranslation::getTranslatedText, (a, b) -> a));

        List<PostTranslation> newTranslations = Collections.synchronizedList(new ArrayList<>());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (SocialPostDto dto : dtos) {
                if (dto.getContent() == null || dto.getContent().trim().isEmpty()) continue;

                if (cacheMap.containsKey(dto.getId())) {
                    dto.setTranslatedContent(cacheMap.get(dto.getId()));
                    dto.setIsTranslated(true);
                } else {
                    futures.add(executor.submit(() -> {
                        try {
                            String translated = translateText(dto.getContent(), targetLanguage);
                            if (translated != null && !translated.equals(dto.getContent())) {
                                dto.setTranslatedContent(translated);
                                dto.setIsTranslated(true);
                                
                                PostTranslation pt = PostTranslation.builder()
                                        .referenceId(dto.getId())
                                        .referenceType("SOCIAL_POST")
                                        .targetLanguage(targetLanguage)
                                        .translatedText(translated)
                                        .build();
                                newTranslations.add(pt);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to translate social post {}: {}", dto.getId(), e.getMessage());
                        }
                    }));
                }
            }
            
            // Wait for all tasks to complete or timeout (total timeout of 5 seconds)
            long startTime = System.currentTimeMillis();
            for (Future<?> future : futures) {
                long timeLeft = 5000 - (System.currentTimeMillis() - startTime);
                if (timeLeft <= 0) {
                    future.cancel(true);
                    continue;
                }
                try {
                    future.get(timeLeft, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                } catch (Exception e) {
                    log.warn("Translation future failed: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Virtual thread execution failed for social post translations", e);
        }

        // Save all new translations in batch via isolated transaction
        if (!newTranslations.isEmpty()) {
            try {
                self.saveTranslationsInNewTransaction(newTranslations);
            } catch (Exception e) {
                log.warn("[Translation] Failed to save translation batch in isolated transaction: {}", e.getMessage());
            }
        }
    }

    /**
     * Translates a batch of PostResponse (issue posts / official broadcasts).
     */
    public void translatePosts(List<PostResponse> dtos, String targetLanguage) {
        if (dtos == null || dtos.isEmpty() || targetLanguage == null || targetLanguage.equalsIgnoreCase("en")) {
            return;
        }

        List<Long> postIds = dtos.stream().map(PostResponse::getId).collect(Collectors.toList());
        
        // Fetch cached translations
        List<PostTranslation> cachedTranslations = postTranslationRepository
                .findByReferenceIdInAndReferenceTypeAndTargetLanguage(postIds, "POST", targetLanguage);
        
        Map<Long, String> cacheMap = cachedTranslations.stream()
                .collect(Collectors.toMap(PostTranslation::getReferenceId, PostTranslation::getTranslatedText, (a, b) -> a));

        List<PostTranslation> newTranslations = Collections.synchronizedList(new ArrayList<>());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (PostResponse dto : dtos) {
                if (dto.getContent() == null || dto.getContent().trim().isEmpty()) continue;

                if (cacheMap.containsKey(dto.getId())) {
                    dto.setTranslatedContent(cacheMap.get(dto.getId()));
                    dto.setIsTranslated(true);
                } else {
                    futures.add(executor.submit(() -> {
                        try {
                            String translated = translateText(dto.getContent(), targetLanguage);
                            if (translated != null && !translated.equals(dto.getContent())) {
                                dto.setTranslatedContent(translated);
                                dto.setIsTranslated(true);
                                
                                PostTranslation pt = PostTranslation.builder()
                                        .referenceId(dto.getId())
                                        .referenceType("POST")
                                        .targetLanguage(targetLanguage)
                                        .translatedText(translated)
                                        .build();
                                newTranslations.add(pt);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to translate broadcast post {}: {}", dto.getId(), e.getMessage());
                        }
                    }));
                }
            }
            
            // Wait for all tasks to complete or timeout (total timeout of 2 seconds)
            long startTime = System.currentTimeMillis();
            for (Future<?> future : futures) {
                long timeLeft = 5000 - (System.currentTimeMillis() - startTime);
                if (timeLeft <= 0) {
                    future.cancel(true);
                    continue;
                }
                try {
                    future.get(timeLeft, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                } catch (Exception e) {
                    log.warn("Translation future failed: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Virtual thread execution failed for broadcast post translations", e);
        }

        // Save all new translations in batch via isolated transaction
        if (!newTranslations.isEmpty()) {
            try {
                self.saveTranslationsInNewTransaction(newTranslations);
            } catch (Exception e) {
                log.warn("[Translation] Failed to save translation batch in isolated transaction: {}", e.getMessage());
            }
        }
    }

    /**
     * Translates text using the free unofficial Google Translate API (GTX client).
     */
    public String translateText(String text, String targetLanguage) {
        if (text == null || text.trim().isEmpty() || targetLanguage == null || targetLanguage.equalsIgnoreCase("en")) {
            return text;
        }

        try {
            googleApiSemaphore.acquire();
            Thread.sleep(300);

            String url = UriComponentsBuilder.fromHttpUrl("https://translate.googleapis.com/translate_a/single")
                    .queryParam("client", "gtx")
                    .queryParam("sl", "auto")
                    .queryParam("tl", targetLanguage)
                    .queryParam("dt", "t")
                    .queryParam("q", text)
                    .build().toUriString();

            String response = restTemplate.getForObject(url, String.class);

            if (response != null) {
                JsonNode rootNode = objectMapper.readTree(response);
                if (rootNode.isArray() && rootNode.size() > 0) {
                    JsonNode sentencesNode = rootNode.get(0);
                    StringBuilder translatedStringBuilder = new StringBuilder();
                    
                    if (sentencesNode.isArray()) {
                        for (JsonNode sentenceNode : sentencesNode) {
                            if (sentenceNode.isArray() && sentenceNode.size() > 0) {
                                JsonNode translatedPartNode = sentenceNode.get(0);
                                if (translatedPartNode != null && !translatedPartNode.isNull() && translatedPartNode.isTextual()) {
                                    translatedStringBuilder.append(translatedPartNode.asText());
                                }
                            }
                        }
                        String translatedText = translatedStringBuilder.toString().trim();
                        if (!translatedText.isEmpty()) {
                            return translatedText;
                        }
                    }
                }
            }
            log.warn("Translation returned empty or invalid structure for target language: {}", targetLanguage);
        } catch (Exception e) {
            log.error("Failed to translate text to language: {}. Error: {}", targetLanguage, e.getMessage());
        } finally {
            googleApiSemaphore.release();
        }

        return text;
    }

    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class
    )
    public void saveTranslationsInNewTransaction(List<PostTranslation> translations) {
        if (translations == null || translations.isEmpty()) return;
        try {
            postTranslationRepository.saveAll(translations);
        } catch (Exception e) {
            log.warn("[Translation] Failed to save translation batch in isolated transaction (likely duplicate key/race): {}", e.getMessage());
        }
    }
}
