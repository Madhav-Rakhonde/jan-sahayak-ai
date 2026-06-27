package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.CacheConfig;
import com.JanSahayak.AI.enums.PassTier;
import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.UserPass;
import com.JanSahayak.AI.repository.UserPassRepository;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.security.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration test to verify Spring AOP cache annotations (@Cacheable, @CacheEvict)
 * are working correctly by spinning up a lightweight Spring context.
 */
@SpringBootTest(classes = {
        CacheConfig.class,
        CustomUserDetailsService.class,
        PlanEnforcementService.class,
        TranslationService.class,
        UserService.class
})
@EnableCaching(proxyTargetClass = true)
public class CachingIntegrationTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private PlanEnforcementService planEnforcementService;

    @Autowired
    private TranslationService translationService;

    @Autowired
    private UserService userService;

    @MockBean
    private UserRepo userRepo;

    @MockBean
    private UserPassRepository userPassRepository;

    @MockBean
    private RestTemplate restTemplate;

    // Need to mock other dependencies of UserService that are not relevant to caching
    @MockBean
    private com.JanSahayak.AI.repository.PostRepo postRepository;
    
    @MockBean
    private com.JanSahayak.AI.repository.UserTagRepo userTagRepository;
    
    @MockBean
    private PinCodeLookupService pincodeLookupService;
    
    @MockBean
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    
    @MockBean
    private PostInteractionService postInteractionService;
    
    @MockBean
    private RateLimitingService rateLimitingService;
    
    @MockBean
    private com.JanSahayak.AI.repository.ChatSessionAuditRepo chatSessionAuditRepo;

    @MockBean
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @MockBean
    private com.JanSahayak.AI.repository.PostTranslationRepository postTranslationRepository;

    @BeforeEach
    void setUp() {
        // Clear all caches before each test
        cacheManager.getCacheNames().forEach(cacheName -> cacheManager.getCache(cacheName).clear());
    }

    @Test
    void testAuthUserDetailsCachingAndEviction() {
        Long userId = 1L;
        User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setUsername("testuser");
        mockUser.setEmail("test@example.com");
        mockUser.setPassword("password");
        mockUser.setIsActive(true);
        Role role = new Role();
        role.setName("ROLE_USER");
        mockUser.setRole(role);

        // Setup mock
        when(userRepo.findByIdWithRole(userId)).thenReturn(Optional.of(mockUser));
        when(userRepo.findById(userId)).thenReturn(Optional.of(mockUser));
        when(userRepo.save(any(User.class))).thenReturn(mockUser);

        // 1. First call should hit the repository (Cache Miss)
        UserDetails userDetails1 = customUserDetailsService.loadUserById(userId);
        assertNotNull(userDetails1);
        verify(userRepo, times(1)).findByIdWithRole(userId);

        // 2. Second call should hit the cache (Cache Hit)
        UserDetails userDetails2 = customUserDetailsService.loadUserById(userId);
        assertNotNull(userDetails2);
        verify(userRepo, times(1)).findByIdWithRole(userId); // Still 1!

        // 3. Deactivate user should trigger @CacheEvict
        // We use admin as currentUser to bypass security checks
        userService.deactivateUser(userId, mockUser);

        // 4. Third call should hit the repository again (Cache Evicted)
        // Since the user is deactivated, loadUserById throws DisabledException
        org.junit.jupiter.api.Assertions.assertThrows(DisabledException.class, () -> {
            customUserDetailsService.loadUserById(userId);
        });
        verify(userRepo, times(2)).findByIdWithRole(userId); // Now 2!
    }

    @Test
    void testUserTiersCaching() {
        Long userId = 2L;
        UserPass mockPass = new UserPass();
        mockPass.setUserId(userId);
        mockPass.setTier(PassTier.GOVLYX_VIP);
        
        when(userPassRepository.findActivePassByUserId(userId)).thenReturn(Optional.of(mockPass));

        // 1. First call (Cache Miss)
        PassTier tier1 = planEnforcementService.getUserTier(userId);
        assertNotNull(tier1);
        verify(userPassRepository, times(1)).findActivePassByUserId(userId);

        // 2. Second call (Cache Hit)
        PassTier tier2 = planEnforcementService.getUserTier(userId);
        assertNotNull(tier2);
        verify(userPassRepository, times(1)).findActivePassByUserId(userId); // Still 1!
    }
}
