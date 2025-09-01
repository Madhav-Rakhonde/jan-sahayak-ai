package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.DistrictLookup;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.DistrictLookupRepo;
import com.JanSahayak.AI.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistrictLookupService {
    @Autowired
    private DistrictLookupRepo districtLookupRepository;
    @Autowired
    private LocationService locationService;

    public List<String> getAllDistricts() {
        return districtLookupRepository.findAllByOrderByLocationNameAsc()
                .stream()
                .map(DistrictLookup::getLocationName)
                .collect(Collectors.toList());
    }

    public List<String> searchDistricts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllDistricts();
        }

        return districtLookupRepository.searchByLocationName(query.trim())
                .stream()
                .map(DistrictLookup::getLocationName)
                .collect(Collectors.toList());
    }

    @Transactional
    public DistrictLookup addDistrict(String locationName, User admin) {
        // Verify admin permissions
        if (!hasAdminRole(admin)) {
            throw new SecurityException("Only administrators can add districts");
        }

        // Validate location format
        if (!locationService.isValidLocationFormat(locationName)) {
            throw new IllegalArgumentException(
                    "Invalid location format. Expected format: 'City StateCode' (e.g., 'Pune MH')"
            );
        }

        // Normalize location name
        String normalizedLocation = locationService.normalizeLocation(locationName);

        // Check if district already exists
        if (districtLookupRepository.existsByLocationName(normalizedLocation)) {
            throw new IllegalArgumentException("District already exists: " + normalizedLocation);
        }

        DistrictLookup district = new DistrictLookup();
        district.setLocationName(normalizedLocation);

        DistrictLookup savedDistrict = districtLookupRepository.save(district);

        log.info("Admin {} added new district: {}", admin.getUsername(), normalizedLocation);

        return savedDistrict;
    }

    public boolean isValidLocation(String location) {
        if (location == null || location.trim().isEmpty()) {
            return false;
        }

        String normalizedLocation = locationService.normalizeLocation(location);
        return districtLookupRepository.existsByLocationName(normalizedLocation);
    }

    public List<String> getDistrictsByStateCode(String stateCode) {
        if (stateCode == null || stateCode.trim().isEmpty()) {
            return getAllDistricts();
        }

        String upperStateCode = stateCode.trim().toUpperCase();

        return getAllDistricts().stream()
                .filter(location -> location.endsWith(" " + upperStateCode))
                .collect(Collectors.toList());
    }

    public List<String> getDistrictsByStateName(String stateName) {
        String stateCode = locationService.getStateCodeByName(stateName);
        if (stateCode == null) {
            return List.of();
        }

        return getDistrictsByStateCode(stateCode);
    }

    @Transactional
    public void removeDistrict(Long districtId, User admin) {
        // Verify admin permissions
        if (!hasAdminRole(admin)) {
            throw new SecurityException("Only administrators can remove districts");
        }

        DistrictLookup district = districtLookupRepository.findById(districtId)
                .orElseThrow(() -> new RuntimeException("District not found with ID: " + districtId));

        // Check if district is being used (this would require additional repository queries)
        // For now, we'll allow removal but in production you'd want to check if any users/posts use this location

        districtLookupRepository.delete(district);

        log.info("Admin {} removed district: {}", admin.getUsername(), district.getLocationName());
    }

    public List<DistrictLookup> getAllDistrictEntities() {
        return districtLookupRepository.findAllByOrderByLocationNameAsc();
    }

    public DistrictLookup findDistrictById(Long id) {
        return districtLookupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("District not found with ID: " + id));
    }

    public DistrictLookup findDistrictByName(String locationName) {
        String normalizedLocation = locationService.normalizeLocation(locationName);

        return districtLookupRepository.findByLocationNameContainingIgnoreCase(normalizedLocation)
                .stream()
                .filter(district -> district.getLocationName().equalsIgnoreCase(normalizedLocation))
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public void bulkAddDistricts(List<String> locationNames, User admin) {
        // Verify admin permissions
        if (!hasAdminRole(admin)) {
            throw new SecurityException("Only administrators can add districts");
        }

        List<DistrictLookup> districtsToAdd = locationNames.stream()
                .map(locationService::normalizeLocation)
                .filter(location -> {
                    // Validate format
                    if (!locationService.isValidLocationFormat(location)) {
                        log.warn("Skipping invalid location format: {}", location);
                        return false;
                    }

                    // Skip if already exists
                    if (districtLookupRepository.existsByLocationName(location)) {
                        log.warn("District already exists, skipping: {}", location);
                        return false;
                    }

                    return true;
                })
                .map(location -> {
                    DistrictLookup district = new DistrictLookup();
                    district.setLocationName(location);
                    return district;
                })
                .collect(Collectors.toList());

        if (!districtsToAdd.isEmpty()) {
            districtLookupRepository.saveAll(districtsToAdd);
            log.info("Admin {} bulk added {} districts", admin.getUsername(), districtsToAdd.size());
        }
    }

    public List<String> validateDistrictList(List<String> locationNames) {
        return locationNames.stream()
                .map(locationService::normalizeLocation)
                .filter(location -> !locationService.isValidLocationFormat(location))
                .collect(Collectors.toList());
    }

    public DistrictStatsDto getDistrictStatistics() {
        List<DistrictLookup> allDistricts = districtLookupRepository.findAll();

        // Group by state code
        var districtsByState = allDistricts.stream()
                .collect(Collectors.groupingBy(
                        district -> locationService.extractStateCodeFromLocation(district.getLocationName()),
                        Collectors.counting()
                ));

        return DistrictStatsDto.builder()
                .totalDistricts((long) allDistricts.size())
                .districtsByState(districtsByState)
                .totalStates((long) districtsByState.size())
                .build();
    }

    public List<String> getPopularDistricts(int limit) {
        // This would ideally be based on usage statistics from posts/users
        // For now, return alphabetically sorted districts
        return getAllDistricts().stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<String> suggestSimilarDistricts(String location) {
        if (location == null || location.trim().isEmpty()) {
            return List.of();
        }

        String stateCode = locationService.extractStateCodeFromLocation(location);
        if (stateCode == null) {
            return List.of();
        }

        // Return other districts from the same state
        return getDistrictsByStateCode(stateCode).stream()
                .filter(district -> !district.equals(location))
                .limit(5)
                .collect(Collectors.toList());
    }

    private boolean hasAdminRole(User user) {
        return user.getRole() != null &&
                "ROLE_ADMIN".equals(user.getRole().getName());
    }




    // DTO class for district statistics
    public static class DistrictStatsDto {
        private Long totalDistricts;
        private java.util.Map<String, Long> districtsByState;
        private Long totalStates;

        public static DistrictStatsDto.Builder builder() {
            return new DistrictStatsDto.Builder();
        }

        // Getters
        public Long getTotalDistricts() {
            return totalDistricts;
        }

        public java.util.Map<String, Long> getDistrictsByState() {
            return districtsByState;
        }

        public Long getTotalStates() {
            return totalStates;
        }

        // Builder class
        public static class Builder {
            private DistrictStatsDto dto = new DistrictStatsDto();

            public Builder totalDistricts(Long totalDistricts) {
                dto.totalDistricts = totalDistricts;
                return this;
            }

            public Builder districtsByState(java.util.Map<String, Long> districtsByState) {
                dto.districtsByState = districtsByState;
                return this;
            }

            public Builder totalStates(Long totalStates) {
                dto.totalStates = totalStates;
                return this;
            }

            public DistrictStatsDto build() {
                return dto;
            }
        }
    }
}
