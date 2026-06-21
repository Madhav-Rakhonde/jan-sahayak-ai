package com.JanSahayak.AI.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class IpUtilsTest {

    @BeforeEach
    void setUp() {
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testExtractIp_FromXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "192.168.1.1, 10.0.0.1");
        
        String ip = IpUtils.extractIp(request);
        
        assertEquals("192.168.1.1", ip, "Should extract first IP from X-Forwarded-For");
    }

    @Test
    void testExtractIp_FromXRealIP() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "10.0.0.2");
        
        String ip = IpUtils.extractIp(request);
        
        assertEquals("10.0.0.2", ip, "Should extract from X-Real-IP");
    }

    @Test
    void testExtractIp_FromRemoteAddrFallback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        
        String ip = IpUtils.extractIp(request);
        
        assertEquals("127.0.0.1", ip, "Should fallback to remote address");
    }

    @Test
    void testGetClientIpFromContext_WithRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "192.168.2.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String ip = IpUtils.getClientIpFromContext();
        
        assertEquals("192.168.2.2", ip, "Should extract IP from RequestContextHolder");
    }

    @Test
    void testGetClientIpFromContext_NoRequest() {
        String ip = IpUtils.getClientIpFromContext();
        
        assertNull(ip, "Should return null if no request attributes are present");
    }
}
