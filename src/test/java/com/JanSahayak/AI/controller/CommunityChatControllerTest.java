package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommunityRepo;
import com.JanSahayak.AI.service.CommunityChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommunityChatControllerTest {

    @Mock
    private CommunityChatService communityChatService;

    @Mock
    private CommunityRepo communityRepo;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private CommunityChatController communityChatController;

    private User testUser;
    private Community testCommunity;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);

        testCommunity = new Community();
        testCommunity.setId(100L);
    }

    @Test
    void testGetChatSettings_Success_WithValues() {
        testCommunity.setIsGroupChatEnabled(false);
        testCommunity.setChatRetentionDays(15);
        
        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = communityChatController.getChatSettings(100L, testUser);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().getData().get("isGroupChatEnabled"));
        assertEquals(15, response.getBody().getData().get("chatRetentionDays"));
    }

    @Test
    void testGetChatSettings_Success_WithNullValuesFallback() {
        testCommunity.setIsGroupChatEnabled(null);
        testCommunity.setChatRetentionDays(null);

        when(communityRepo.findById(100L)).thenReturn(Optional.of(testCommunity));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = communityChatController.getChatSettings(100L, testUser);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().getData().get("isGroupChatEnabled")); // default fallback
        assertEquals(0, response.getBody().getData().get("chatRetentionDays"));     // default fallback
    }

    @Test
    void testGetChatSettings_CommunityNotFound() {
        when(communityRepo.findById(100L)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Map<String, Object>>> response = communityChatController.getChatSettings(100L, testUser);

        assertEquals(400, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getError().contains("Community not found"));
    }

    @Test
    void testUpdateChatSettings_Success() {
        CommunityChatController.SettingsRequest request = new CommunityChatController.SettingsRequest();
        request.setIsGroupChatEnabled(true);
        request.setChatRetentionDays(7);

        doNothing().when(communityChatService).updateChatSettings(100L, 1L, true, 7);

        ResponseEntity<ApiResponse<Void>> response = communityChatController.updateChatSettings(100L, testUser, request);

        assertEquals(200, response.getStatusCodeValue());
        verify(communityChatService, times(1)).updateChatSettings(100L, 1L, true, 7);
    }

    @Test
    void testUpdateChatSettings_PartialUpdate_Success() {
        CommunityChatController.SettingsRequest request = new CommunityChatController.SettingsRequest();
        request.setChatRetentionDays(14); // isGroupChatEnabled is null

        doNothing().when(communityChatService).updateChatSettings(100L, 1L, null, 14);

        ResponseEntity<ApiResponse<Void>> response = communityChatController.updateChatSettings(100L, testUser, request);

        assertEquals(200, response.getStatusCodeValue());
        verify(communityChatService, times(1)).updateChatSettings(100L, 1L, null, 14);
    }

    @Test
    void testUpdateChatSettings_AccessDenied() {
        CommunityChatController.SettingsRequest request = new CommunityChatController.SettingsRequest();
        request.setChatRetentionDays(7);

        doThrow(new SecurityException("Only community admins can perform this action."))
                .when(communityChatService).updateChatSettings(100L, 1L, null, 7);

        ResponseEntity<ApiResponse<Void>> response = communityChatController.updateChatSettings(100L, testUser, request);

        assertEquals(403, response.getStatusCodeValue());
        assertEquals("Access Denied", response.getBody().getMessage());
    }
}
