package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.enums.PassTier;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.UserPass;
import com.JanSahayak.AI.repository.UserPassRepository;
import com.JanSahayak.AI.service.PaymentService;
import com.JanSahayak.AI.service.PlanEnforcementService;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PlanEnforcementService planEnforcementService;
    private final UserPassRepository userPassRepository;
    private final com.JanSahayak.AI.repository.UserRepo userRepository;
    private final org.springframework.cache.CacheManager cacheManager;

    @GetMapping("/config")
    public ResponseEntity<java.util.Map<String, String>> getBillingConfig() {
        return ResponseEntity.ok(java.util.Map.of("keyId", paymentService.getRazorpayKeyId()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyBilling(@AuthenticationPrincipal User user) {
        Map<String, Object> response = new HashMap<>();
        PassTier tier = planEnforcementService.getUserTier(user.getId());
        response.put("currentTier", tier.name());
        
        userPassRepository.findActivePassByUserId(user.getId()).ifPresent(pass -> {
            response.put("validUntil", pass.getValidUntil().toString());
            response.put("privateCommunityQuota", pass.getPrivateCommunityQuota());
        });
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> payload) {
        try {
            String targetTierStr = (String) payload.get("targetTier");
            if (targetTierStr == null) {
                return ResponseEntity.badRequest().body("Missing targetTier in payload");
            }

            PassTier targetTier;
            try {
                targetTier = PassTier.valueOf(targetTierStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body("Invalid target tier: " + targetTierStr);
            }

            int amount = targetTier == PassTier.GOVLYX_VIP ? 149 : 49;

            String orderId = paymentService.createOrder(user.getId(), targetTier, amount);
            Map<String, String> response = new HashMap<>();
            response.put("orderId", orderId);
            return ResponseEntity.ok(response);
        } catch (RazorpayException e) {
            return ResponseEntity.internalServerError().body("Error creating order: " + e.getMessage());
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> payload) {
        String orderId = payload.get("razorpay_order_id");
        String paymentId = payload.get("razorpay_payment_id");
        String signature = payload.get("razorpay_signature");

        boolean verified = paymentService.verifySignature(orderId, paymentId, signature);
        if (verified) {
            return ResponseEntity.ok().body(Map.of("message", "Payment verified and pass activated"));
        } else {
            return ResponseEntity.badRequest().body("Signature verification failed");
        }
    }

    /**
     * Testing / Admin utility to instantly grant a pass without payment.
     * Overrides any existing passes and instantly clears the cache.
     */
    @PostMapping("/grant-pass")
    public ResponseEntity<?> grantPass(
            @RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String targetTierStr = payload.get("targetTier");

        if (email == null || email.trim().isEmpty() || targetTierStr == null || targetTierStr.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Missing email or targetTier in payload");
        }

        PassTier targetTier;
        try {
            targetTier = PassTier.valueOf(targetTierStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid target tier: " + targetTierStr);
        }

        User targetUser = userRepository.findByEmail(email).orElse(null);
        if (targetUser == null) {
            return ResponseEntity.badRequest().body("User not found with email: " + email);
        }

        // We simulate a verified payment by just creating the active pass
        // using a manual transaction block. Note: PaymentService.activatePass is private,
        // so we replicate the activation logic directly here (which triggers cache evict).
        UserPass userPass = UserPass.builder()
                .userId(targetUser.getId())
                .tier(targetTier)
                .razorpayOrderId("MANUAL_" + System.currentTimeMillis())
                .razorpayPaymentId("MANUAL_GRANT")
                .status(com.JanSahayak.AI.enums.UserPassStatus.ACTIVE)
                .validUntil(java.time.LocalDateTime.now().plusMonths(1))
                .privateCommunityQuota(targetTier == PassTier.GOVLYX_VIP ? 5 : 3)
                .build();

        // Carry over remaining quota from previous active pass
        userPassRepository.findActivePassByUserId(targetUser.getId()).ifPresent(oldPass -> {
            userPass.setPrivateCommunityQuota(userPass.getPrivateCommunityQuota() + oldPass.getPrivateCommunityQuota());
            oldPass.setStatus(com.JanSahayak.AI.enums.UserPassStatus.EXPIRED);
            userPassRepository.save(oldPass);
        });

        userPassRepository.save(userPass);

        // Evict the user's tier cache manually to ensure instantaneous propagation
        org.springframework.cache.Cache cache = cacheManager.getCache("userTiers");
        if (cache != null) {
            cache.evict(targetUser.getId());
        }

        return ResponseEntity.ok().body(Map.of(
            "message", "Successfully granted " + targetTier.name() + " to " + email,
            "userId", targetUser.getId()
        ));
    }
}
