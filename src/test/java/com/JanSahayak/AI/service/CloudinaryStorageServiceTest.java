package com.JanSahayak.AI.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.JanSahayak.AI.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CloudinaryStorageServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @InjectMocks
    private CloudinaryStorageService cloudinaryStorageService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> paramsCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cloudinaryStorageService, "folderPosts", "jansahayak/posts");
        ReflectionTestUtils.setField(cloudinaryStorageService, "folderSocialPosts", "jansahayak/social-posts");
    }

    @Test
    void testUploadFile_Success_NoAwsRekognitionFlag() throws Exception {
        // Arrange
        MultipartFile mockFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test image data".getBytes());
        Long userId = 1L;

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/v1234/jansahayak/posts/test.jpg"));

        // Act
        String url = cloudinaryStorageService.uploadFile(mockFile, userId, CloudinaryStorageService.FOLDER_POSTS);

        // Assert
        assertNotNull(url);
        assertEquals("https://res.cloudinary.com/demo/image/upload/v1234/jansahayak/posts/test.jpg", url);

        verify(uploader).upload(any(byte[].class), paramsCaptor.capture());
        Map<String, Object> capturedParams = paramsCaptor.getValue();
        
        assertEquals("jansahayak/posts", capturedParams.get("folder"));
        assertEquals("image", capturedParams.get("resource_type"));
        
        // CRITICAL CHECK: Verify that "moderation" flag with "aws_rek" is NOT present to prevent upload failure on free tier
        assertFalse(capturedParams.containsKey("moderation"), "Moderation flag should be completely removed to fix upload error");
    }

    @Test
    void testUploadFile_WhenCloudinaryNull_ThrowsException() {
        // Arrange
        CloudinaryStorageService unconfiguredService = new CloudinaryStorageService();
        MultipartFile mockFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test image data".getBytes());

        // Act & Assert
        ServiceException exception = assertThrows(ServiceException.class, () -> {
            unconfiguredService.uploadFile(mockFile, 1L, CloudinaryStorageService.FOLDER_POSTS);
        });

        assertTrue(exception.getMessage().contains("Cloudinary is not configured"));
    }
}
