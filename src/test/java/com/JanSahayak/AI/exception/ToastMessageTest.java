package com.JanSahayak.AI.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ToastMessageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void test404NotFoundToastMessage() throws Exception {
        // Trigger a 404 by accessing a non-existent public endpoint or using mock user
        mockMvc.perform(get("/api/public/this-endpoint-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.toastMessage").value(org.hamcrest.Matchers.containsString("We looked everywhere")));
    }

    @Test
    public void test400BadRequestToastMessage() throws Exception {
        // Trigger a 400 Bad Request by missing required parameters on a public endpoint
        mockMvc.perform(post("/api/auth/register/citizen")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")) // Empty JSON triggers ValidationException/MethodArgumentNotValidException
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.toastMessage").value(org.hamcrest.Matchers.containsString("Bro, what was that request?")));
    }

    @Test
    public void test401UnauthorizedToastMessage() throws Exception {
        // Trigger a 401 Unauthorized by accessing a secured endpoint without a token
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.toastMessage").value(org.hamcrest.Matchers.containsString("Nice try. Log in first.")));
    }
}
