package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.UserTagSuggestionDto;
import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.RoleRepo;
import com.JanSahayak.AI.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
public class UserTaggingServiceIntegrationTest {

    @Autowired
    private UserTaggingService userTaggingService;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate.execute(status -> {
            // Setup a department user
            Role deptRole = roleRepo.findByName("ROLE_DEPARTMENT").orElseGet(() -> {
                Role r = new Role();
                r.setName("ROLE_DEPARTMENT");
                return roleRepo.save(r);
            });

            // Ensure the user doesn't already exist or clean up
            if (userRepo.findByUsername("pune_police").isEmpty()) {
                User deptUser = new User();
                deptUser.setUsername("pune_police");
                deptUser.setEmail("pune_police@example.com");
                deptUser.setPassword("password123");
                deptUser.setRole(deptRole);
                deptUser.setPincode("411001");
                deptUser.setIsEmailVerified(true);
                deptUser.setIsActive(true);
                userRepo.save(deptUser);
            }

            return null;
        });
    }

    @Test
    @DisplayName("Verify that fetching user tag suggestions eagerly loads the Role and avoids LazyInitializationException")
    void testGetUserTagSuggestions_AvoidsLazyInitializationException() {
        // We run the service method OUTSIDE of a transaction.
        // If the query in UserRepo doesn't use JOIN FETCH u.role r,
        // mapping to UserTagSuggestionDto will throw LazyInitializationException 
        // when calling user.getRole().getName().

        PaginatedResponse<UserTagSuggestionDto> response = assertDoesNotThrow(() -> {
            return userTaggingService.getUserTagSuggestions("pune_police", null, 10);
        });

        assertThat(response).isNotNull();
        List<UserTagSuggestionDto> suggestions = response.getData();
        assertThat(suggestions).isNotEmpty();
        
        // Find our specific user
        UserTagSuggestionDto matchedUser = suggestions.stream()
                .filter(u -> "pune_police".equals(u.getUsername()))
                .findFirst()
                .orElse(null);

        assertThat(matchedUser).isNotNull();
        assertThat(matchedUser.getRole()).isEqualTo("ROLE_DEPARTMENT");
    }
}
