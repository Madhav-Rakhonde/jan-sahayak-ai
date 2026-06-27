package com.JanSahayak.AI.config;

import com.JanSahayak.AI.util.IdempotencyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyInterceptorTest {

    private IdempotencyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new IdempotencyInterceptor();
        IdempotencyContext.clear();
    }

    @AfterEach
    void tearDown() {
        IdempotencyContext.clear();
    }

    @Test
    void testPreHandle_WithIdempotencyKey_SetsContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "test-key-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result, "preHandle should return true");
        assertEquals("test-key-123", IdempotencyContext.getKey(), "Idempotency key should be set in context");
    }

    @Test
    void testPreHandle_WithoutIdempotencyKey_LeavesContextEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result, "preHandle should return true");
        assertNull(IdempotencyContext.getKey(), "Idempotency key should be null in context");
    }

    @Test
    void testAfterCompletion_ClearsContext() throws Exception {
        IdempotencyContext.setKey("test-key-123");
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(IdempotencyContext.getKey(), "Idempotency key should be cleared after completion");
    }
}
