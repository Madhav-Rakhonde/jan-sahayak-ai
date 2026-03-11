package com.JanSahayak.AI.service;

import com.JanSahayak.AI.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentValidationService {

    private final BadWordService badWordService;

    /**
     * Validates content for bad words before allowing post/comment creation.
     *
     * @param content Text content to validate
     * @throws ValidationException if bad word is found
     */
    public void validateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return; // Empty content validation handled elsewhere
        }

        BadWordService.BadWordCheckResult result = badWordService.checkText(content);

        if (!result.isAllowed()) {
            log.warn("Content validation failed - bad word detected: {}", result.getBadWord());
            throw new ValidationException(result.getMessage());
        }

        log.debug("Content validation passed - no bad words found");
    }

    public boolean isContentClean(String content) {
        try {
            validateContent(content);
            return true;
        } catch (ValidationException e) {
            return false;
        }
    }

    public BadWordService.BadWordCheckResult checkContent(String content) {
        return badWordService.checkText(content);
    }
}