package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.enums.FeedbackCategory;
import com.JanSahayak.AI.payload.FeedbackRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FeedbackCategoryParsingTest {

    @Test
    void testCaseInsensitiveParsing() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        
        String json1 = "{\"rating\":5, \"category\":\"Bug\"}";
        FeedbackRequest req1 = mapper.readValue(json1, FeedbackRequest.class);
        assertEquals(FeedbackCategory.BUG, req1.getCategory());

        String json2 = "{\"rating\":4, \"category\":\"feature_request\"}";
        FeedbackRequest req2 = mapper.readValue(json2, FeedbackRequest.class);
        assertEquals(FeedbackCategory.FEATURE_REQUEST, req2.getCategory());
        
        String json3 = "{\"rating\":3, \"category\":\"UI_UX\"}";
        FeedbackRequest req3 = mapper.readValue(json3, FeedbackRequest.class);
        assertEquals(FeedbackCategory.UI_UX, req3.getCategory());
    }
}
