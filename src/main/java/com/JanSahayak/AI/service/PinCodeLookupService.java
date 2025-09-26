package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.PincodeLookup;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.PincodeLookupRepo;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.payload.PostUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PinCodeLookupService {

    private final PincodeLookupRepo pincodeLookupRepository;

    // ===== Basic CRUD Operations =====

    public Optional<PincodeLookup> findByPincode(String pincode) {
        if (!Constant.isValidIndianPincode(pincode)) {
            return Optional.empty();
        }
        return pincodeLookupRepository.findById(pincode);
    }

    public PaginatedResponse<PincodeLookup> findAll(String beforePincode, Integer limit) {
        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findAll",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);

        log.debug("Finding all pincodes with cursor: {}, limit: {}", beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            // Repository method needed: findByPincodeLessThanOrderByPincodeDesc
            // pincodes = pincodeLookupRepository.findByPincodeLessThanOrderByPincodeDesc(beforePincode, PageRequest.of(0, validatedLimit));
            pincodes = pincodeLookupRepository.findAll();
            pincodes = pincodes.stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            // Repository method needed: findAllByOrderByPincodeDesc
            // pincodes = pincodeLookupRepository.findAllByOrderByPincodeDesc(PageRequest.of(0, validatedLimit));
            pincodes = pincodeLookupRepository.findAll().stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> findActiveOnly(String beforePincode, Integer limit) {
        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findActiveOnly",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);

        log.debug("Finding active pincodes with cursor: {}, limit: {}", beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            // Repository method needed: findByIsActiveTrueAndPincodeLessThanOrderByPincodeDesc
            // pincodes = pincodeLookupRepository.findByIsActiveTrueAndPincodeLessThanOrderByPincodeDesc(beforePincode, PageRequest.of(0, validatedLimit));
            pincodes = pincodeLookupRepository.findByIsActiveTrue();
            pincodes = pincodes.stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            // Repository method needed: findByIsActiveTrueOrderByPincodeDesc
            // pincodes = pincodeLookupRepository.findByIsActiveTrueOrderByPincodeDesc(PageRequest.of(0, validatedLimit));
            pincodes = pincodeLookupRepository.findByIsActiveTrue().stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    // ===== Search Operations =====

    public PaginatedResponse<PincodeLookup> searchByAreaName(String areaName, String beforePincode, Integer limit) {
        if (areaName == null || areaName.trim().isEmpty()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("searchByAreaName",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        String searchTerm = areaName.trim();

        log.debug("Searching by area name: '{}', cursor: {}, limit: {}", searchTerm, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            // Repository method needed: findByAreaNameContainingIgnoreCaseAndIsActiveTrueAndPincodeLessThanOrderByPincodeDesc
            pincodes = pincodeLookupRepository.findByAreaNameContainingIgnoreCaseAndIsActiveTrue(searchTerm);
            pincodes = pincodes.stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository.findByAreaNameContainingIgnoreCaseAndIsActiveTrue(searchTerm)
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> searchByCity(String city, String beforePincode, Integer limit) {
        if (city == null || city.trim().isEmpty()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("searchByCity",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        String searchTerm = city.trim();

        log.debug("Searching by city: '{}', cursor: {}, limit: {}", searchTerm, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository.findByCityContainingIgnoreCaseAndIsActiveTrue(searchTerm);
            pincodes = pincodes.stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository.findByCityContainingIgnoreCaseAndIsActiveTrue(searchTerm)
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> findByDistrict(String district, String beforePincode, Integer limit) {
        if (district == null || district.trim().isEmpty()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findByDistrict",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        String searchTerm = district.trim();

        log.debug("Finding by district: '{}', cursor: {}, limit: {}", searchTerm, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository.findByDistrictIgnoreCaseAndIsActiveTrue(searchTerm);
            pincodes = pincodes.stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository.findByDistrictIgnoreCaseAndIsActiveTrue(searchTerm)
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> findByState(String state, String beforePincode, Integer limit) {
        if (state == null || state.trim().isEmpty()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findByState",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        String searchTerm = state.trim();

        log.debug("Finding by state: '{}', cursor: {}, limit: {}", searchTerm, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository.findByStateIgnoreCaseAndIsActiveTrue(searchTerm);
            pincodes = pincodes.stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository.findByStateIgnoreCaseAndIsActiveTrue(searchTerm)
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> searchGeneral(String query, String beforePincode, Integer limit) {
        if (query == null || query.trim().isEmpty()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("searchGeneral",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        String searchTerm = query.trim();

        log.debug("General search for: '{}', cursor: {}, limit: {}", searchTerm, beforePincode, setup.getValidatedLimit());

        // If it's a 6-digit pincode, search by pincode first
        if (Constant.isValidIndianPincode(searchTerm)) {
            Optional<PincodeLookup> byPincode = findByPincode(searchTerm);
            if (byPincode.isPresent()) {
                List<PincodeLookup> result = List.of(byPincode.get());
                return PaginationUtils.createCustomResponse(result, false, null, setup.getValidatedLimit());
            }
        }

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository.findBySearchableTextContainingIgnoreCaseAndIsActiveTrue(searchTerm);
            pincodes = pincodes.stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository.findBySearchableTextContainingIgnoreCaseAndIsActiveTrue(searchTerm)
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    // ===== Validation Operations =====

    public boolean isValidPincode(String pincode) {
        if (!Constant.isValidIndianPincode(pincode)) {
            return false;
        }
        return pincodeLookupRepository.existsByPincodeAndIsActiveTrue(pincode);
    }

    public boolean isPincodeActive(String pincode) {
        return findByPincode(pincode)
                .map(PincodeLookup::getIsActive)
                .orElse(false);
    }

    // ===== Geographic Operations (Using Pincode Prefix Logic) =====

    public PaginatedResponse<PincodeLookup> findNearbyPincodes(String pincode, double radiusKm, String beforePincode, Integer limit) {
        Optional<PincodeLookup> centerPincode = findByPincode(pincode);
        if (centerPincode.isEmpty() || !centerPincode.get().hasCoordinates()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findNearbyPincodes",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        PincodeLookup center = centerPincode.get();

        log.debug("Finding nearby pincodes for: {}, radius: {}km, cursor: {}, limit: {}",
                pincode, radiusKm, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> nearbyPincodes = findActiveOnly(null, Integer.MAX_VALUE).getData().stream()
                .filter(p -> p.hasCoordinates() && !p.getPincode().equals(pincode))
                .filter(p -> center.calculateDistanceTo(p) <= radiusKm)
                .filter(p -> beforePincode == null || p.getPincode().compareTo(beforePincode) < 0)
                .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                .limit(setup.getValidatedLimit())
                .collect(Collectors.toList());

        return PaginationUtils.createIdBasedResponse(nearbyPincodes, setup.getValidatedLimit(),
                pincodeObj -> Long.valueOf(pincodeObj.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> findByStateAndDistrict(String state, String district, String beforePincode, Integer limit) {
        if (state == null || district == null) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findByStateAndDistrict",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        String stateSearch = state.trim();
        String districtSearch = district.trim();

        log.debug("Finding by state: '{}' and district: '{}', cursor: {}, limit: {}",
                stateSearch, districtSearch, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository.findByStateIgnoreCaseAndDistrictIgnoreCaseAndIsActiveTrue(
                    stateSearch, districtSearch);
            pincodes = pincodes.stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository.findByStateIgnoreCaseAndDistrictIgnoreCaseAndIsActiveTrue(
                            stateSearch, districtSearch)
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    // ===== Pincode Prefix-Based Geographic Operations =====

    public PaginatedResponse<PincodeLookup> findByStatePrefix(String statePrefix, String beforePincode, Integer limit) {
        if (!Constant.isValidIndianStatePrefix(statePrefix)) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findByStatePrefix",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);

        log.debug("Finding by state prefix: '{}', cursor: {}, limit: {}", statePrefix, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository.findByPincodeStartingWithAndIsActiveTrue(statePrefix);
            pincodes = pincodes.stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository.findByPincodeStartingWithAndIsActiveTrue(statePrefix)
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> findByDistrictPrefix(String districtPrefix, String beforePincode, Integer limit) {
        if (districtPrefix == null || !districtPrefix.matches(Constant.INDIAN_DISTRICT_PREFIX_PATTERN)) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findByDistrictPrefix",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);

        log.debug("Finding by district prefix: '{}', cursor: {}, limit: {}", districtPrefix, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository.findByPincodeStartingWithAndIsActiveTrue(districtPrefix);
            pincodes = pincodes.stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository.findByPincodeStartingWithAndIsActiveTrue(districtPrefix)
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> findByPincodePrefixes(List<String> prefixes, String beforePincode, Integer limit) {
        if (prefixes == null || prefixes.isEmpty()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findByPincodePrefixes",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);

        log.debug("Finding by pincode prefixes: {}, cursor: {}, limit: {}", prefixes, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> allPincodes = prefixes.stream()
                .filter(prefix -> prefix != null && prefix.matches("\\d{2,6}"))
                .flatMap(prefix -> pincodeLookupRepository.findByPincodeStartingWithAndIsActiveTrue(prefix).stream())
                .distinct()
                .filter(p -> beforePincode == null || p.getPincode().compareTo(beforePincode) < 0)
                .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                .limit(setup.getValidatedLimit())
                .collect(Collectors.toList());

        return PaginationUtils.createIdBasedResponse(allPincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    // ===== Statistics and Analytics =====

    public PincodeStatsDto getStatistics() {
        // Note: Statistics don't need pagination as they return aggregated data
        List<PincodeLookup> allPincodes = pincodeLookupRepository.findAll();
        long activeCount = allPincodes.stream().filter(PincodeLookup::getIsActive).count();

        Map<String, Long> byState = allPincodes.stream()
                .filter(PincodeLookup::getIsActive)
                .collect(Collectors.groupingBy(PincodeLookup::getState, Collectors.counting()));

        Map<String, Long> byDistrict = allPincodes.stream()
                .filter(PincodeLookup::getIsActive)
                .collect(Collectors.groupingBy(p -> p.getState() + " - " + p.getDistrict(),
                        Collectors.counting()));

        return PincodeStatsDto.builder()
                .totalPincodes((long) allPincodes.size())
                .activePincodes(activeCount)
                .inactivePincodes(allPincodes.size() - activeCount)
                .pincodesByState(byState)
                .pincodesByDistrict(byDistrict)
                .totalStates((long) byState.size())
                .totalDistricts((long) byDistrict.size())
                .build();
    }

    public List<String> getAllStates() {
        // Note: State/district lists don't need pagination as they're typically small
        return pincodeLookupRepository.findDistinctStatesByIsActiveTrue();
    }

    public List<String> getAllDistricts() {
        return pincodeLookupRepository.findDistinctDistrictsByIsActiveTrue();
    }

    public List<String> getDistrictsByState(String state) {
        if (state == null || state.trim().isEmpty()) {
            return List.of();
        }
        return pincodeLookupRepository.findDistinctDistrictsByStateIgnoreCaseAndIsActiveTrue(state.trim());
    }
    public List<String> getDistrictsByStates(List<String> states) {
        // ... returns ALL districts at once
        return states.stream()
                .flatMap(state -> getDistrictsByState(state).stream()) // Gets ALL districts
                .distinct()
                .sorted()
                .collect(Collectors.toList()); // Returns complete list
    }

    // ===== User Helper Methods (Using Pincode Prefix Logic) =====

    public void populateUserLocationData(User user) {
        PostUtility.validateUser(user);

        if (!user.hasPincode()) {
            return;
        }

        findByPincode(user.getPincode()).ifPresent(user::setPincodeLookupData);
    }

    public String getUserFullLocation(User user) {
        if (user == null || user.getPincodeLookupData() == null) {
            return user != null && user.hasPincode() ? user.getPincode() : null;
        }

        return user.getPincodeLookupData().getFullLocationName();
    }

    public String getUserDisplayLocation(User user) {
        if (user == null || user.getPincodeLookupData() == null) {
            return user != null && user.hasPincode() ? user.getPincode() : null;
        }

        return user.getPincodeLookupData().getDisplayLocation();
    }

    // ===== Pincode Prefix Conversion Methods =====

    public List<String> convertStatesToPrefixes(List<String> stateNames) {
        return PostUtility.convertStatesToPincodePrefixes(stateNames);
    }

    public List<String> convertDistrictsToPrefixes(List<String> districtNames) {
        return PostUtility.convertDistrictsToPincodePrefixes(districtNames, this);
    }

    // ===== Backward Compatibility Methods (Non-paginated) =====
    // These methods maintain the original signatures for existing code that doesn't need pagination

    public List<PincodeLookup> findByDistrict(String district) {
        return findByDistrict(district, null, 50).getData();
    }

    public List<PincodeLookup> searchByAreaName(String areaName) {
        return searchByAreaName(areaName, null, 50).getData();
    }

    public List<PincodeLookup> searchByCity(String city) {
        return searchByCity(city, null, 50).getData();
    }

    public List<PincodeLookup> findByState(String state) {
        return findByState(state, null, 50).getData();
    }

    public List<PincodeLookup> searchGeneral(String query) {
        return searchGeneral(query, null, 50).getData();
    }

    public List<PincodeLookup> findNearbyPincodes(String pincode, double radiusKm) {
        return findNearbyPincodes(pincode, radiusKm, null, 50).getData();
    }

    public List<PincodeLookup> findByStateAndDistrict(String state, String district) {
        return findByStateAndDistrict(state, district, null, 50).getData();
    }

    public List<PincodeLookup> findByStatePrefix(String statePrefix) {
        return findByStatePrefix(statePrefix, null, 50).getData();
    }

    public List<PincodeLookup> findByDistrictPrefix(String districtPrefix) {
        return findByDistrictPrefix(districtPrefix, null, 50).getData();
    }

    public List<PincodeLookup> findByPincodePrefixes(List<String> prefixes) {
        return findByPincodePrefixes(prefixes, null, 50).getData();
    }

    // ===== Private Helper Methods =====

    private void validateAdminPermission(User user) {
        PostUtility.validateUser(user);

        if (!PostUtility.isAdmin(user)) {
            throw new SecurityException("Only administrators can perform this operation");
        }
    }

    // ===== DTO Classes =====

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PincodeStatsDto {
        private Long totalPincodes;
        private Long activePincodes;
        private Long inactivePincodes;
        private Map<String, Long> pincodesByState;
        private Map<String, Long> pincodesByDistrict;
        private Long totalStates;
        private Long totalDistricts;
    }
}
