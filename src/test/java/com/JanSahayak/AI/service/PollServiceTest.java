package com.JanSahayak.AI.service;

import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.payload.request.CreatePollRequest;
import com.JanSahayak.AI.payload.request.PollResponse;
import com.JanSahayak.AI.repository.*;
import com.JanSahayak.AI.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PollServiceTest {

    @Mock
    private PollRepository pollRepository;

    @Mock
    private PollOptionRepository pollOptionRepository;

    @Mock
    private PollVoteRepository pollVoteRepository;

    @Mock
    private SocialPostRepo socialPostRepository;

    @Mock
    private UserRepo userRepository;

    @Mock
    private SocialPostMediaService mediaService;

    @Mock
    private CommunityService communityService;

    @InjectMocks
    private PollService pollService;

    @BeforeEach
    void setUp() {
        // Manually inject the field-injected @Lazy dependency
        ReflectionTestUtils.setField(pollService, "communityService", communityService);
    }

    @Test
    void testCreatePollPostWithMedia_Success_WithMediaFiles() throws Exception {
        // Arrange
        User creator = new User();
        creator.setId(1L);
        creator.setUsername("testuser");

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Which color do you like?");
        request.setOptions(List.of("Red", "Blue", "Green"));
        request.setExpiresIn("3d");
        request.setAllowMultipleVotes(true);

        MultipartFile file1 = mock(MultipartFile.class);
        when(file1.getOriginalFilename()).thenReturn("test1.jpg");
        when(file1.getSize()).thenReturn(1024L);
        when(file1.isEmpty()).thenReturn(false);

        List<MultipartFile> mediaFiles = List.of(file1);
        List<String> mockUrls = List.of("https://cloudinary.com/test1.jpg");

        when(mediaService.uploadMediaFiles(mediaFiles, 1L)).thenReturn(mockUrls);

        SocialPost savedPost = SocialPost.builder()
                .content(request.getQuestion())
                .user(creator)
                .status(PostStatus.ACTIVE)
                .allowComments(true)
                .build();
        savedPost.setId(100L);

        when(socialPostRepository.save(any(SocialPost.class))).thenAnswer(invocation -> {
            SocialPost sp = invocation.getArgument(0);
            sp.setId(100L);
            return sp;
        });

        Poll savedPoll = Poll.builder()
                .question(request.getQuestion())
                .createdBy(creator)
                .socialPost(savedPost)
                .build();
        savedPoll.setId(200L);

        when(pollRepository.save(any(Poll.class))).thenAnswer(invocation -> {
            Poll p = invocation.getArgument(0);
            p.setId(200L);
            return p;
        });

        // Act
        PollResponse response = pollService.createPollPostWithMedia(request, mediaFiles, creator);

        // Assert
        assertNotNull(response);
        assertEquals(200L, response.getPollId());
        assertEquals(100L, response.getSocialPostId());
        assertEquals("Which color do you like?", response.getQuestion());

        verify(mediaService, times(1)).uploadMediaFiles(mediaFiles, 1L);
        verify(socialPostRepository, times(1)).save(any(SocialPost.class));
        verify(pollRepository, times(1)).save(any(Poll.class));
        verify(pollOptionRepository, times(1)).saveAll(anyList());
    }

    @Test
    void testCreatePollPostWithMedia_Success_NoMediaFiles() {
        // Arrange
        User creator = new User();
        creator.setId(1L);

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Which color do you like?");
        request.setOptions(List.of("Red", "Blue"));

        when(socialPostRepository.save(any(SocialPost.class))).thenAnswer(invocation -> {
            SocialPost sp = invocation.getArgument(0);
            sp.setId(101L);
            return sp;
        });

        when(pollRepository.save(any(Poll.class))).thenAnswer(invocation -> {
            Poll p = invocation.getArgument(0);
            p.setId(201L);
            return p;
        });

        // Act
        PollResponse response = pollService.createPollPostWithMedia(request, Collections.emptyList(), creator);

        // Assert
        assertNotNull(response);
        assertEquals(201L, response.getPollId());
        assertEquals(101L, response.getSocialPostId());

        verifyNoInteractions(mediaService);
        verify(socialPostRepository, times(1)).save(any(SocialPost.class));
        verify(pollRepository, times(1)).save(any(Poll.class));
        verify(pollOptionRepository, times(1)).saveAll(anyList());
    }

    @Test
    void testCreatePollPostWithMedia_ValidationFailure_BlankOption() {
        // Arrange
        User creator = new User();
        creator.setId(1L);

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Question");
        request.setOptions(List.of("Red", ""));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            pollService.createPollPostWithMedia(request, null, creator);
        });

        verifyNoInteractions(mediaService, socialPostRepository, pollRepository);
    }

    @Test
    void testCreatePollPostWithMedia_UploadFailure_ThrowsServiceException() throws Exception {
        // Arrange
        User creator = new User();
        creator.setId(1L);

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Which color?");
        request.setOptions(List.of("Red", "Blue"));

        MultipartFile file1 = mock(MultipartFile.class);
        when(file1.getOriginalFilename()).thenReturn("test1.jpg");
        when(file1.getSize()).thenReturn(1024L);
        when(file1.isEmpty()).thenReturn(false);

        List<MultipartFile> mediaFiles = List.of(file1);

        when(mediaService.uploadMediaFiles(mediaFiles, 1L))
                .thenThrow(new RuntimeException("Cloudinary error"));

        // Act & Assert
        assertThrows(ServiceException.class, () -> {
            pollService.createPollPostWithMedia(request, mediaFiles, creator);
        });

        verify(socialPostRepository, never()).save(any(SocialPost.class));
        verify(pollRepository, never()).save(any(Poll.class));
    }

    @Test
    void testCreatePollPostWithMedia_ValidationFailure_QuestionTooLong() {
        // Arrange
        User creator = new User();
        creator.setId(1L);

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("a".repeat(501)); // Exceeds 500 characters
        request.setOptions(List.of("Red", "Blue"));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pollService.createPollPostWithMedia(request, null, creator);
        });
        assertEquals("Poll question cannot exceed 500 characters.", exception.getMessage());
    }

    @Test
    void testCreatePollPostWithMedia_ValidationFailure_QuestionEmpty() {
        // Arrange
        User creator = new User();
        creator.setId(1L);

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion(""); // Empty
        request.setOptions(List.of("Red", "Blue"));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pollService.createPollPostWithMedia(request, null, creator);
        });
        assertEquals("Poll question cannot be empty.", exception.getMessage());
    }

    @Test
    void testCreatePollPostWithMedia_ValidationFailure_TooFewOptions() {
        // Arrange
        User creator = new User();
        creator.setId(1L);

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Question?");
        request.setOptions(List.of("Red")); // Less than 2 options

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pollService.createPollPostWithMedia(request, null, creator);
        });
        assertEquals("A poll must have between 2 and 4 options.", exception.getMessage());
    }

    @Test
    void testCreatePollPostWithMedia_ValidationFailure_TooManyOptions() {
        // Arrange
        User creator = new User();
        creator.setId(1L);

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Question?");
        request.setOptions(List.of("Red", "Blue", "Green", "Yellow", "Orange")); // More than 4 options

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pollService.createPollPostWithMedia(request, null, creator);
        });
        assertEquals("A poll must have between 2 and 4 options.", exception.getMessage());
    }

    @Test
    void testCreatePollPostWithMedia_ValidationFailure_OptionTooLong() {
        // Arrange
        User creator = new User();
        creator.setId(1L);

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Question?");
        request.setOptions(List.of("Red", "b".repeat(201))); // Option exceeds 200 chars

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pollService.createPollPostWithMedia(request, null, creator);
        });
        assertEquals("Option text cannot exceed 200 characters.", exception.getMessage());
    }

    @Test
    void testCreatePollPostWithMedia_ExpiresIn_Never() {
        // Arrange
        User creator = new User();
        creator.setId(1L);

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Which color?");
        request.setOptions(List.of("Red", "Blue"));
        request.setExpiresIn("never");

        when(socialPostRepository.save(any(SocialPost.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Poll> pollCaptor = ArgumentCaptor.forClass(Poll.class);
        when(pollRepository.save(pollCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        pollService.createPollPostWithMedia(request, null, creator);

        // Assert
        assertNull(pollCaptor.getValue().getExpiresAt());
    }

    @Test
    void testCreatePollPostWithMedia_ExpiresIn_1d() {
        // Arrange
        User creator = new User();
        creator.setId(1L);

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Which color?");
        request.setOptions(List.of("Red", "Blue"));
        request.setExpiresIn("1d");

        when(socialPostRepository.save(any(SocialPost.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Poll> pollCaptor = ArgumentCaptor.forClass(Poll.class);
        when(pollRepository.save(pollCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        pollService.createPollPostWithMedia(request, null, creator);

        // Assert
        assertNotNull(pollCaptor.getValue().getExpiresAt());
        long diff = pollCaptor.getValue().getExpiresAt().getTime() - System.currentTimeMillis();
        assertTrue(diff > 23 * 60 * 60 * 1000L && diff < 25 * 60 * 60 * 1000L);
    }

    @Test
    void testCreatePollPostWithMedia_ExpiresIn_7d() {
        // Arrange
        User creator = new User();
        creator.setId(1L);

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Which color?");
        request.setOptions(List.of("Red", "Blue"));
        request.setExpiresIn("7d");

        when(socialPostRepository.save(any(SocialPost.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Poll> pollCaptor = ArgumentCaptor.forClass(Poll.class);
        when(pollRepository.save(pollCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        pollService.createPollPostWithMedia(request, null, creator);

        // Assert
        assertNotNull(pollCaptor.getValue().getExpiresAt());
        long diff = pollCaptor.getValue().getExpiresAt().getTime() - System.currentTimeMillis();
        assertTrue(diff > 7 * 23 * 60 * 60 * 1000L && diff < 7 * 25 * 60 * 60 * 1000L);
    }

    @Test
    void testCreatePollPostWithMedia_CommunityPublishedEvent() {
        // Arrange
        User creator = new User();
        creator.setId(1L);

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Which color?");
        request.setOptions(List.of("Red", "Blue"));

        Community community = new Community();
        community.setId(10L);

        SocialPost returnedPost = SocialPost.builder()
                .content(request.getQuestion())
                .user(creator)
                .status(PostStatus.ACTIVE)
                .allowComments(true)
                .community(community)
                .build();
        returnedPost.setId(100L);

        when(socialPostRepository.save(any(SocialPost.class))).thenReturn(returnedPost);

        when(pollRepository.save(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        pollService.createPollPostWithMedia(request, null, creator);

        // Assert
        verify(communityService, times(1)).onPostPublished(returnedPost, 10L);
    }

    @Test
    void testCreatePollPostWithMedia_CommunityPublishedEvent_ExceptionHandled() {
        // Arrange
        User creator = new User();
        creator.setId(1L);

        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Which color?");
        request.setOptions(List.of("Red", "Blue"));

        Community community = new Community();
        community.setId(10L);

        SocialPost returnedPost = SocialPost.builder()
                .content(request.getQuestion())
                .user(creator)
                .status(PostStatus.ACTIVE)
                .allowComments(true)
                .community(community)
                .build();
        returnedPost.setId(100L);

        when(socialPostRepository.save(any(SocialPost.class))).thenReturn(returnedPost);

        when(pollRepository.save(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Simulate community published event throwing exception
        doThrow(new RuntimeException("Community sync failed"))
                .when(communityService).onPostPublished(returnedPost, 10L);

        // Act & Assert
        // The exception should be caught and logged, not rethrown, so the flow completes.
        assertDoesNotThrow(() -> {
            pollService.createPollPostWithMedia(request, null, creator);
        });

        verify(communityService, times(1)).onPostPublished(returnedPost, 10L);
        verify(pollRepository, times(1)).save(any(Poll.class));
    }
}
