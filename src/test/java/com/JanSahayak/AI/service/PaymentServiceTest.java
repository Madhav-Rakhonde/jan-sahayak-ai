package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.PassTier;
import com.JanSahayak.AI.enums.UserPassStatus;
import com.JanSahayak.AI.model.UserPass;
import com.JanSahayak.AI.repository.UserPassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private UserPassRepository userPassRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        // Inject dummy keys for signature verification tests
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "dummy_secret");
    }

    // Note: createOrder relies on RazorpayClient which makes an actual network call.
    // In a real isolated unit test, we would mock RazorpayClient or abstract it.
    // We will test the verification logic here which does not make external calls.

    @Test
    void testVerifySignatureFailureWithInvalidSignature() {
        // Act
        boolean result = paymentService.verifySignature("order_123", "pay_123", "invalid_signature");

        // Assert
        assertFalse(result, "Signature verification should fail with invalid signature");
        verify(userPassRepository, never()).findByRazorpayOrderId(anyString());
    }

    @Test
    void testVerifySignatureSuccessActivatesPass() {
        // Arrange
        String orderId = "order_123";
        String paymentId = "pay_123";
        
        // We simulate valid signature behavior by directly testing the activatePass logic
        // Since verifySignature depends on Utils.verifyPaymentSignature, we can test activatePass via reflection
        // Or we can just test the activation logic isolated
        
        UserPass pendingPass = UserPass.builder()
                .userId(1L)
                .tier(PassTier.GOVLYX_VIP)
                .razorpayOrderId(orderId)
                .status(UserPassStatus.EXPIRED)
                .privateCommunityQuota(0)
                .build();
                
        // Act
        when(cacheManager.getCache("userTiers")).thenReturn(cache);
        ReflectionTestUtils.invokeMethod(paymentService, "activatePass", pendingPass);

        // Assert
        assertEquals(UserPassStatus.ACTIVE, pendingPass.getStatus());
        assertEquals(5, pendingPass.getPrivateCommunityQuota(), "VIP tier should grant 5 private communities");
        assertNotNull(pendingPass.getValidUntil());
        
        verify(userPassRepository, times(1)).save(pendingPass);
        verify(cacheManager, times(1)).getCache("userTiers");
        verify(cache, times(1)).evict(1L);
    }

    @Test
    void testActivatePassCarriesOverQuota() {
        // Arrange
        String orderId = "order_456";
        
        UserPass pendingPass = UserPass.builder()
                .userId(2L)
                .tier(PassTier.GOVLYX_VIP)
                .razorpayOrderId(orderId)
                .status(UserPassStatus.EXPIRED)
                .privateCommunityQuota(0)
                .build();
                
        UserPass activePass = UserPass.builder()
                .userId(2L)
                .tier(PassTier.GOVLYX_PRO)
                .status(UserPassStatus.ACTIVE)
                .privateCommunityQuota(4) // 4 remaining quota
                .build();
                
        when(userPassRepository.findActivePassByUserId(2L)).thenReturn(Optional.of(activePass));
        when(cacheManager.getCache("userTiers")).thenReturn(cache);
        
        // Act
        ReflectionTestUtils.invokeMethod(paymentService, "activatePass", pendingPass);

        // Assert
        assertEquals(UserPassStatus.ACTIVE, pendingPass.getStatus());
        // 5 from VIP + 4 from previous active pass = 9
        assertEquals(9, pendingPass.getPrivateCommunityQuota(), "VIP tier should grant 5 + previous 4 = 9");
        assertEquals(UserPassStatus.EXPIRED, activePass.getStatus(), "Previous pass should be expired");
        
        verify(userPassRepository, times(1)).save(activePass);
        verify(userPassRepository, times(1)).save(pendingPass);
    }
}
