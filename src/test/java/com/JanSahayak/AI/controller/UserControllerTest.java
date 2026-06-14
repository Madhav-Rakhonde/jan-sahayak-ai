package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.UserMeResponse;
import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.ratelimit.ClientIpResolver;
import com.JanSahayak.AI.ratelimit.RateLimiterService;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.security.CustomUserDetailsService;
import com.JanSahayak.AI.security.JwtUtil;
import com.JanSahayak.AI.service.CloudinaryStorageService;
import com.JanSahayak.AI.service.PincodeValidationService;
import com.JanSahayak.AI.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private UserRepo userRepository;

    @MockBean
    private CloudinaryStorageService cloudinaryStorageService;

    @MockBean
    private PincodeValidationService pincodeValidationService;

    @MockBean
    private RateLimiterService rateLimiterService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    private User mockUser;

    @BeforeEach
    void setUp() {
        Role role = new Role();
        role.setId(1L);
        role.setName("ROLE_USER");

        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@example.com");
        mockUser.setUsername("testuser");
        mockUser.setIsActive(true);
        mockUser.setRole(role);
        mockUser.setInterfaceLanguage("en");

        Mockito.when(rateLimiterService.tryConsume(any())).thenReturn(true);
    }

    @Test
    @DisplayName("GET /api/users/me returns interfaceLanguage preference")
    void getCurrentUserProfile_shouldReturnInterfaceLanguage() throws Exception {
        // Arrange
        mockUser.setInterfaceLanguage("te");
        Mockito.when(userService.getUserFromAuthentication(any())).thenReturn(mockUser);

        // Act & Assert
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.interfaceLanguage").value("te"));
    }

    @Test
    @DisplayName("PUT /api/users/profile updates and returns updated interfaceLanguage")
    void updateUserProfile_shouldUpdateAndReturnInterfaceLanguage() throws Exception {
        // Arrange
        UserController.UserUpdateRequest request = new UserController.UserUpdateRequest();
        request.setEmail("user@example.com");
        request.setInterfaceLanguage("hi");

        User updatedUser = new User();
        updatedUser.setId(1L);
        updatedUser.setEmail("user@example.com");
        updatedUser.setUsername("testuser");
        updatedUser.setIsActive(true);
        updatedUser.setRole(mockUser.getRole());
        updatedUser.setInterfaceLanguage("hi");

        Mockito.when(userService.getUserFromAuthentication(any())).thenReturn(mockUser);
        Mockito.when(userService.updateUser(any(User.class))).thenReturn(updatedUser);

        // Act & Assert
        mockMvc.perform(put("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.interfaceLanguage").value("hi"));
    }
}
