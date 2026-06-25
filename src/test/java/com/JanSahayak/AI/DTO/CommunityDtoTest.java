package com.JanSahayak.AI.DTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommunityDtoTest {

    @Test
    void testCommunityPostResponse_Serialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        CommunityDto.CommunityPostResponse response = CommunityDto.CommunityPostResponse.builder()
                .id(1L)
                .isLikedByMe(true)
                .isSavedByMe(false)
                .build();

        String json = mapper.writeValueAsString(response);

        // Verify that the keys are exactly what we specified in @JsonProperty
        assertTrue(json.contains("\"isLikedByMe\":true"), "JSON should contain exactly \"isLikedByMe\":true");
        assertTrue(json.contains("\"isSavedByMe\":false"), "JSON should contain exactly \"isSavedByMe\":false");
    }
}
