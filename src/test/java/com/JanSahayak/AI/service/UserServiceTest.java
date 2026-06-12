package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.repository.UserTagRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepo userRepository;
    @Mock
    private PostRepo postRepository;
    @Mock
    private UserTagRepo userTagRepository;
    @Mock
    private PinCodeLookupService pincodeLookupService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PostInteractionService postInteractionService;
    @Mock
    private RateLimitingService rateLimitingService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private Role userRole;

    @BeforeEach
    void setUp() {
        userRole = new Role();
        userRole.setId(1L);
        userRole.setName("ROLE_USER");

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setUsername("testuser123");
        testUser.setIsActive(true);
        testUser.setRole(userRole);
    }

    @Test
    void deactivateUser_ShouldAppendSuffixToEmailAndUsername() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        long beforeDeactivation = System.currentTimeMillis();

        // Act
        userService.deactivateUser(1L, testUser);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        
        assertFalse(savedUser.getIsActive(), "User should be deactivated");
        
        // Check email modification
        assertNotEquals("test@example.com", savedUser.getEmail());
        assertTrue(savedUser.getEmail().contains("_del_"), "Email should contain '_del_' suffix");
        assertTrue(savedUser.getEmail().endsWith("@example.com"), "Email domain should be preserved");
        
        // Check username modification
        assertNotEquals("testuser123", savedUser.getActualUsername());
        assertTrue(savedUser.getActualUsername().contains("_del_"), "Username should contain '_del_' suffix");
        assertTrue(savedUser.getActualUsername().startsWith("testuser123"), "Username should start with original username");
        
        verify(postInteractionService, times(1)).cleanupForUserDeletion(savedUser);
    }
    
    @Test
    void deactivateUser_ShouldTruncateLongEmail() {
        // Arrange
        String longLocalPart = "very.long.local.part.that.will.exceed.the.one.hundred.character.limit.when.combined.with.suffix.and.domain";
        testUser.setEmail(longLocalPart + "@example.com");
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        userService.deactivateUser(1L, testUser);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        
        assertFalse(savedUser.getIsActive());
        assertTrue(savedUser.getEmail().length() <= 100, "Email length should not exceed 100 characters");
        assertTrue(savedUser.getEmail().contains("_del_"));
        assertTrue(savedUser.getEmail().endsWith("@example.com"));
    }
    
    @Test
    void deactivateUser_ShouldTruncateLongUsername() {
        // Arrange
        String longUsername = "very_long_username_that_will_exceed_one_hundred_characters_when_suffix_is_appended_to_it_which_is_bad";
        testUser.setUsername(longUsername);
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        userService.deactivateUser(1L, testUser);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        
        assertFalse(savedUser.getIsActive());
        assertTrue(savedUser.getActualUsername().length() <= 100, "Username length should not exceed 100 characters");
        assertTrue(savedUser.getActualUsername().contains("_del_"));
    }
}
