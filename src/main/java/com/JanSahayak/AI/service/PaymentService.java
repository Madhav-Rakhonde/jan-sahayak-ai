package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.PassTier;
import com.JanSahayak.AI.enums.UserPassStatus;
import com.JanSahayak.AI.model.UserPass;
import com.JanSahayak.AI.repository.UserPassRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final UserPassRepository userPassRepository;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    private RazorpayClient razorpayClient;

    public String getRazorpayKeyId() {
        return razorpayKeyId;
    }

    @PostConstruct
    public void init() {
        try {
            this.razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
        } catch (RazorpayException e) {
            log.error("Failed to initialize Razorpay Client", e);
        }
    }

    @Transactional
    public String createOrder(Long userId, PassTier targetTier, int amount) throws RazorpayException {
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amount * 100); // Amount in paise
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "receipt_user_" + userId);

        Order order = razorpayClient.orders.create(orderRequest);
        String orderId = order.get("id");

        // Save pending order
        UserPass userPass = UserPass.builder()
                .userId(userId)
                .tier(targetTier)
                .razorpayOrderId(orderId)
                .status(UserPassStatus.EXPIRED) // Expired until verified
                .validUntil(LocalDateTime.now()) // Just a placeholder
                .privateCommunityQuota(0)
                .build();
        userPassRepository.save(userPass);

        return orderId;
    }

    @Transactional
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);

            boolean isValid = Utils.verifyPaymentSignature(options, razorpayKeySecret);

            if (isValid) {
                Optional<UserPass> passOpt = userPassRepository.findByRazorpayOrderId(orderId);
                if (passOpt.isPresent()) {
                    UserPass userPass = passOpt.get();
                    userPass.setRazorpayPaymentId(paymentId);
                    activatePass(userPass);
                } else {
                    log.error("Order ID {} verified but UserPass record not found", orderId);
                    return false;
                }
            }
            return isValid;
        } catch (RazorpayException e) {
            log.error("Error verifying signature for order {}", orderId, e);
            return false;
        }
    }

    private void activatePass(UserPass userPass) {
        userPass.setStatus(UserPassStatus.ACTIVE);
        userPass.setValidUntil(LocalDateTime.now().plusMonths(1)); // 1 month validity default

        if (userPass.getTier() == PassTier.GOVLYX_PRO) {
            userPass.setPrivateCommunityQuota(userPass.getPrivateCommunityQuota() + 3);
        } else if (userPass.getTier() == PassTier.GOVLYX_VIP) {
            userPass.setPrivateCommunityQuota(userPass.getPrivateCommunityQuota() + 5);
        }

        userPassRepository.save(userPass);
        log.info("Pass activated for user {} with tier {}", userPass.getUserId(), userPass.getTier());
    }
}
