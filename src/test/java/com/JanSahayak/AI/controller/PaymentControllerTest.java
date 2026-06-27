package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.enums.PassTier;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.UserPassRepository;
import com.JanSahayak.AI.service.PaymentService;
import com.JanSahayak.AI.service.PlanEnforcementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private PlanEnforcementService planEnforcementService;

    @Mock
    private UserPassRepository userPassRepository;

    @InjectMocks
    private PaymentController paymentController;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
    }

    @Test
    void testCreateOrder_Success_CaseInsensitive() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("targetTier", "govlyx_vip");

        when(paymentService.createOrder(1L, PassTier.GOVLYX_VIP, 149)).thenReturn("order_123");

        ResponseEntity<?> response = paymentController.createOrder(testUser, payload);

        assertEquals(200, response.getStatusCodeValue());
        verify(paymentService, times(1)).createOrder(1L, PassTier.GOVLYX_VIP, 149);
    }

    @Test
    void testCreateOrder_InvalidTier() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("targetTier", "invalid_tier");

        ResponseEntity<?> response = paymentController.createOrder(testUser, payload);

        assertEquals(400, response.getStatusCodeValue());
        assertEquals("Invalid target tier: invalid_tier", response.getBody());
    }
}
