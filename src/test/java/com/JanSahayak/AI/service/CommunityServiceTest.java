package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.CommunityDto.CommunitySummaryResponse;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.repository.CommunityMemberRepo;
import com.JanSahayak.AI.repository.CommunityRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private CommunityRepo communityRepo;

    @Mock
    private CommunityMemberRepo memberRepo;

    @InjectMocks
    private CommunityService communityService;

    @Test
    void testToSummaryResponse_AnonymousRequester() {
        Community c = Community.builder()
                .id(10L)
                .name("Test Comm")
                .slug("test-comm")
                .description("Desc")
                .memberCount(5)
                .postCount(2)
                .feedEligible(true)
                .build();

        CommunitySummaryResponse response = ReflectionTestUtils.invokeMethod(
                communityService,
                "toSummaryResponse",
                c,
                null, // requesterId = null
                null  // memberCommunityIds = null
        );

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertFalse(response.isMember());
        verifyNoInteractions(memberRepo);
    }

    @Test
    void testToSummaryResponse_WithPreFetchedMembership_Member() {
        Community c = Community.builder()
                .id(10L)
                .name("Test Comm")
                .slug("test-comm")
                .description("Desc")
                .memberCount(5)
                .postCount(2)
                .feedEligible(true)
                .build();

        Set<Long> preFetched = Set.of(10L, 20L);

        CommunitySummaryResponse response = ReflectionTestUtils.invokeMethod(
                communityService,
                "toSummaryResponse",
                c,
                1L, // requesterId
                preFetched
        );

        assertNotNull(response);
        assertTrue(response.isMember());
        // Verify no database interaction because memberCommunityIds was used
        verifyNoInteractions(memberRepo);
    }

    @Test
    void testToSummaryResponse_WithPreFetchedMembership_NonMember() {
        Community c = Community.builder()
                .id(10L)
                .name("Test Comm")
                .slug("test-comm")
                .description("Desc")
                .memberCount(5)
                .postCount(2)
                .feedEligible(true)
                .build();

        Set<Long> preFetched = Set.of(20L, 30L);

        CommunitySummaryResponse response = ReflectionTestUtils.invokeMethod(
                communityService,
                "toSummaryResponse",
                c,
                1L, // requesterId
                preFetched
        );

        assertNotNull(response);
        assertFalse(response.isMember());
        // Verify no database interaction because memberCommunityIds was used
        verifyNoInteractions(memberRepo);
    }

    @Test
    void testToSummaryResponse_WithoutPreFetchedMembership_FallbackToDb_IsMember() {
        Community c = Community.builder()
                .id(10L)
                .name("Test Comm")
                .slug("test-comm")
                .description("Desc")
                .memberCount(5)
                .postCount(2)
                .feedEligible(true)
                .build();

        when(memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(10L, 1L)).thenReturn(true);

        CommunitySummaryResponse response = ReflectionTestUtils.invokeMethod(
                communityService,
                "toSummaryResponse",
                c,
                1L, // requesterId
                null // memberCommunityIds = null
        );

        assertNotNull(response);
        assertTrue(response.isMember());
        verify(memberRepo, times(1)).existsByCommunityIdAndUserIdAndIsActiveTrue(10L, 1L);
    }

    @Test
    void testToSummaryResponse_WithoutPreFetchedMembership_FallbackToDb_IsNotMember() {
        Community c = Community.builder()
                .id(10L)
                .name("Test Comm")
                .slug("test-comm")
                .description("Desc")
                .memberCount(5)
                .postCount(2)
                .feedEligible(true)
                .build();

        when(memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(10L, 1L)).thenReturn(false);

        CommunitySummaryResponse response = ReflectionTestUtils.invokeMethod(
                communityService,
                "toSummaryResponse",
                c,
                1L, // requesterId
                null // memberCommunityIds = null
        );

        assertNotNull(response);
        assertFalse(response.isMember());
        verify(memberRepo, times(1)).existsByCommunityIdAndUserIdAndIsActiveTrue(10L, 1L);
    }
}
