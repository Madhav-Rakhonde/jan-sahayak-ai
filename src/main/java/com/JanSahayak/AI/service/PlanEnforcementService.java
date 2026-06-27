package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.PassTier;
import com.JanSahayak.AI.exception.PlanLimitExceededException;
import com.JanSahayak.AI.model.UserPass;
import com.JanSahayak.AI.repository.ChatSessionAuditRepo;
import com.JanSahayak.AI.repository.UserPassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;

@Service
@RequiredArgsConstructor
public class PlanEnforcementService {

    private final UserPassRepository userPassRepository;
    private final ChatSessionAuditRepo chatSessionAuditRepo;

    @Cacheable(value = "userTiers", key = "#userId")
    public PassTier getUserTier(Long userId) {
        return userPassRepository.findActivePassByUserId(userId)
                .map(UserPass::getTier)
                .orElse(PassTier.GOVLYX_FREE);
    }

    public boolean canCreateExclusiveCommunity(Long userId) {
        Optional<UserPass> passOpt = userPassRepository.findActivePassByUserId(userId);
        return passOpt.isPresent() && passOpt.get().getPrivateCommunityQuota() > 0;
    }

    public boolean canSendChatMedia(Long userId) {
        PassTier tier = getUserTier(userId);
        return tier == PassTier.GOVLYX_PRO || tier == PassTier.GOVLYX_VIP;
    }

    public boolean canUseMatchFilters(Long userId) {
        PassTier tier = getUserTier(userId);
        return tier == PassTier.GOVLYX_PRO || tier == PassTier.GOVLYX_VIP;
    }

    public boolean canSetDisappearingMessages(Long userId) {
        return getUserTier(userId) == PassTier.GOVLYX_VIP;
    }

    public boolean canPinMessages(Long userId) {
        return getUserTier(userId) == PassTier.GOVLYX_VIP;
    }

    public void enforceDailyMatchmakingLimit(Long userId) {
        PassTier tier = getUserTier(userId);
        if (tier == PassTier.GOVLYX_FREE) {
            Date since = Date.from(Instant.now().minus(24, ChronoUnit.HOURS));
            int matchCount = chatSessionAuditRepo.countSessionsForUserSince(userId, since);
            if (matchCount >= 3) {
                throw new PlanLimitExceededException("You have reached your daily matchmaking limit of 3. Upgrade to Govlyx Pro for unlimited matches.");
            }
        }
    }

    // A secret community is frozen if the owner doesn't have an active pass
    public boolean isCommunityFrozen(Long ownerId) {
        PassTier tier = getUserTier(ownerId);
        return tier == PassTier.GOVLYX_FREE; // For now, if owner drops to FREE, frozen
    }
}
