package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.SocialPostDto;
import com.JanSahayak.AI.model.PostTranslation;
import com.JanSahayak.AI.repository.PostTranslationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TranslationServiceIntegrationTest {

    @Autowired
    private TranslationService translationService;

    @Autowired
    private PostTranslationRepository postTranslationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        postTranslationRepository.deleteAll();
    }

    @Test
    void testSaveTranslationsInNewTransaction_ThrowsExceptionOnCommit() {
        // 1. Insert an initial translation
        PostTranslation pt1 = PostTranslation.builder()
                .referenceId(999L)
                .referenceType("SOCIAL_POST")
                .targetLanguage("hi")
                .translatedText("नमस्ते")
                .build();
        postTranslationRepository.save(pt1);

        // 2. Direct call to saveTranslationsInNewTransaction via the Spring proxy should throw UnexpectedRollbackException
        // because the transaction manager tries to commit a transaction marked rollback-only due to SQL constraint violation
        PostTranslation duplicatePt = PostTranslation.builder()
                .referenceId(999L)
                .referenceType("SOCIAL_POST")
                .targetLanguage("hi")
                .translatedText("नमस्ते दोबारा")
                .build();

        assertThrows(UnexpectedRollbackException.class, () -> 
            translationService.saveTranslationsInNewTransaction(List.of(duplicatePt))
        );
    }

    @Test
    void testTranslateSocialPosts_SwallowsSaveExceptionAndDoesNotPoisonOuterTransaction() {
        // 1. Insert an initial translation
        PostTranslation pt1 = PostTranslation.builder()
                .referenceId(999L)
                .referenceType("SOCIAL_POST")
                .targetLanguage("hi")
                .translatedText("नमस्ते")
                .build();
        postTranslationRepository.save(pt1);

        // 2. Start an outer transaction
        transactionTemplate.execute(status -> {
            // 3. Prepare a social post DTO that will trigger translation and try to save a duplicate
            SocialPostDto dto = new SocialPostDto();
            dto.setId(999L);
            dto.setContent("Hello world"); // different content so it triggers translation API call (mocked or actual)
            
            // We use translateSocialPosts which has the try-catch block wrapping the save call
            assertDoesNotThrow(() -> 
                translationService.translateSocialPosts(List.of(dto), "hi")
            );

            // 4. Verify that the outer transaction remains active and NOT marked rollback-only
            assertFalse(status.isRollbackOnly(), "Outer transaction should NOT be marked rollback-only");
            
            return null;
        });

        // 5. Verify database state: the original one is still there, duplicate was not saved
        List<PostTranslation> all = postTranslationRepository.findAll();
        assertEquals(1, all.size());
        assertEquals("नमस्ते", all.get(0).getTranslatedText());
    }
}
