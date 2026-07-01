package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.payload.request.CreatePollRequest;
import com.JanSahayak.AI.payload.request.PollResponse;
import com.JanSahayak.AI.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
    private CommunityRepo communityRepository;
    @Mock
    private CommunityMemberRepo communityMemberRepository;
    @Mock
    private CommunityService communityService;

    @InjectMocks
    private PollService pollService;

    private User testUser;
    private Community testCommunity;
    private CreatePollRequest req;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(100L);
        testUser.setUsername("testuser");

        testCommunity = new Community();
        testCommunity.setId(200L);
        testCommunity.setName("Test Community");
        testCommunity.setPrivacy(Community.CommunityPrivacy.PUBLIC);

        req = new CreatePollRequest();
        req.setQuestion("Which one is better?");
        req.setOptions(List.of("Option 1", "Option 2"));
        req.setAllowMultipleVotes(false);
        req.setShowResultsBeforeExpiry(true);
    }

    @Test
    void testCreatePollPost_InCommunity_Success() {
        req.setCommunityId(testCommunity.getId());

        when(communityRepository.findById(testCommunity.getId())).thenReturn(Optional.of(testCommunity));
        when(communityMemberRepository.existsByCommunityIdAndUserIdAndIsActiveTrue(testCommunity.getId(), testUser.getId()))
                .thenReturn(true);
        when(socialPostRepository.save(any(SocialPost.class))).thenAnswer(i -> {
            SocialPost sp = i.getArgument(0);
            sp.setId(1L);
            return sp;
        });
        when(pollRepository.save(any(Poll.class))).thenAnswer(i -> {
            Poll p = i.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(pollOptionRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        PollResponse response = pollService.createPollPost(req, testUser);

        assertNotNull(response);
        assertEquals("Which one is better?", response.getQuestion());

        ArgumentCaptor<SocialPost> socialPostCaptor = ArgumentCaptor.forClass(SocialPost.class);
        verify(socialPostRepository).save(socialPostCaptor.capture());
        SocialPost savedSp = socialPostCaptor.getValue();
        assertEquals(testCommunity, savedSp.getCommunity());
        assertEquals("PUBLIC", savedSp.getCommunityPrivacy()); // Assuming syncCommunityDenormalizedFields sets this
    }

    @Test
    void testCreatePollPost_InCommunity_NotMember() {
        req.setCommunityId(testCommunity.getId());

        when(communityRepository.findById(testCommunity.getId())).thenReturn(Optional.of(testCommunity));
        when(communityMemberRepository.existsByCommunityIdAndUserIdAndIsActiveTrue(testCommunity.getId(), testUser.getId()))
                .thenReturn(false); // Not an active member

        SecurityException ex = assertThrows(SecurityException.class, () -> {
            pollService.createPollPost(req, testUser);
        });
        assertTrue(ex.getMessage().contains("active member"));

        verify(socialPostRepository, never()).save(any(SocialPost.class));
    }

    @Test
    void testCreatePollPost_InCommunity_NotFound() {
        req.setCommunityId(testCommunity.getId());

        when(communityRepository.findById(testCommunity.getId())).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            pollService.createPollPost(req, testUser);
        });
        assertTrue(ex.getMessage().contains("Community not found"));

        verify(communityMemberRepository, never()).existsByCommunityIdAndUserIdAndIsActiveTrue(anyLong(), anyLong());
        verify(socialPostRepository, never()).save(any(SocialPost.class));
    }
}
