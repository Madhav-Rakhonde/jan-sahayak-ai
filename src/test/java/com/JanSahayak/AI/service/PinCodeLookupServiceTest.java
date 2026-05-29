package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.model.PincodeLookup;
import com.JanSahayak.AI.repository.PincodeLookupRepo;
import org.junit.jupiter.api.BeforeEach;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PinCodeLookupServiceTest {

    @Mock
    private PincodeLookupRepo pincodeLookupRepository;

    @InjectMocks
    private PinCodeLookupService pinCodeLookupService;

    private PincodeLookup center;
    private PincodeLookup near;
    private PincodeLookup far;

    @BeforeEach
    void setUp() {
        center = new PincodeLookup();
        center.setPincode("110001");
        center.setLatitude(BigDecimal.valueOf(28.6139));
        center.setLongitude(BigDecimal.valueOf(77.2090));
        center.setIsActive(true);

        near = new PincodeLookup();
        near.setPincode("110002");
        near.setLatitude(BigDecimal.valueOf(28.6140));
        near.setLongitude(BigDecimal.valueOf(77.2100));
        near.setIsActive(true);

        far = new PincodeLookup();
        far.setPincode("400001");
        far.setLatitude(BigDecimal.valueOf(18.9220)); // Mumbai
        far.setLongitude(BigDecimal.valueOf(72.8347));
        far.setIsActive(true);
    }

    @Test
    void testFindNearbyPincodes_RadiusMath() {
        when(pincodeLookupRepository.findById("110001")).thenReturn(Optional.of(center));
        // Mock the prefix fetch
        when(pincodeLookupRepository.findByPincodeStartingWithAndIsActiveTrue(anyString(), any(PageRequest.class)))
                .thenReturn(Arrays.asList(center, near, far));

        // 20km radius
        PaginatedResponse<PincodeLookup> result = pinCodeLookupService.findNearbyPincodes("110001", 20.0, null, 10);
        
        List<PincodeLookup> data = result.getData();
        assertEquals(1, data.size(), "Should only return 'near', since 'center' is excluded and 'far' is outside radius");
        assertEquals("110002", data.get(0).getPincode());
    }

    @Test
    void testGetNearbyPincodeStrings() {
        when(pincodeLookupRepository.findById("110001")).thenReturn(Optional.of(center));
        when(pincodeLookupRepository.findByPincodeStartingWithAndIsActiveTrue(anyString(), any(PageRequest.class)))
                .thenReturn(Arrays.asList(center, near, far));

        Set<String> nearby = pinCodeLookupService.getNearbyPincodeStrings("110001");
        
        assertEquals(1, nearby.size());
        assertTrue(nearby.contains("110002"));
    }
}
