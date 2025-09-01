package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.exception.ServiceException;

import com.JanSahayak.AI.model.DistrictLookup;
import com.JanSahayak.AI.repository.DistrictLookupRepo;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    @Autowired
    private DistrictLookupRepo districtLookupRepository;

    /**
     * Get all available districts from database
     */
    public List<String> getAllDistricts() {
        try {
            return districtLookupRepository.findAllByOrderByLocationNameAsc()
                    .stream()
                    .map(DistrictLookup::getLocationName)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get all districts", e);
            throw new ServiceException("Failed to get all districts: " + e.getMessage(), e);
        }
    }

    /**
     * Get available districts (alias for getAllDistricts for backward compatibility)
     */
    public List<String> getAvailableDistricts() {
        return getAllDistricts();
    }

    /**
     * Search districts by query
     */
    public List<String> searchDistricts(String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return getAvailableDistricts();
            }

            return districtLookupRepository.findByLocationNameContainingIgnoreCase(query.trim())
                    .stream()
                    .map(DistrictLookup::getLocationName)
                    .sorted()
                    .limit(Constant.LOCATION_SEARCH_LIMIT) // Limit results to prevent performance issues
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to search districts with query: {}", query, e);
            throw new ServiceException("Failed to search districts: " + e.getMessage(), e);
        }
    }

    /**
     * Validate location format (City StateCode)
     */
    public boolean isValidLocationFormat(String location) {
        if (location == null || location.trim().isEmpty()) {
            return false;
        }

        return Constant.LOCATION_PATTERN.matcher(location.trim()).matches();
    }

    /**
     * Check if location exists in the database
     */
    public boolean isValidLocation(String location) {
        try {
            if (!isValidLocationFormat(location)) {
                return false;
            }

            String normalizedLocation = normalizeLocation(location);
            return districtLookupRepository.existsByLocationName(normalizedLocation);
        } catch (Exception e) {
            log.error("Failed to validate location: {}", location, e);
            return false;
        }
    }

    /**
     * Extract state name from location string (e.g., "Pune MH" -> "Maharashtra")
     */
    public String extractStateFromLocation(String location) {
        if (location == null || location.trim().isEmpty()) {
            return null;
        }

        String stateCode = extractStateCodeFromLocation(location);
        return Constant.STATE_CODES.getOrDefault(stateCode, stateCode);
    }

    /**
     * Extract state code from location string (e.g., "Pune MH" -> "MH")
     */
    public String extractStateCodeFromLocation(String location) {
        if (location == null || location.trim().isEmpty()) {
            return null;
        }

        String[] parts = location.trim().split("\\s+");
        if (parts.length < 2) {
            return null;
        }

        String stateCode = parts[parts.length - 1];

        // Validate state code format (2 uppercase letters)
        if (stateCode.matches("^[A-Z]{2}$")) {
            return stateCode;
        }

        return null;
    }

    /**
     * Extract city name from location string (e.g., "Pune MH" -> "Pune")
     */
    public String extractCityFromLocation(String location) {
        if (location == null || location.trim().isEmpty()) {
            return null;
        }

        String[] parts = location.trim().split("\\s+");
        if (parts.length < 2) {
            return location;
        }

        // Join all parts except the last one (state code)
        return Arrays.stream(parts)
                .limit(parts.length - 1)
                .collect(Collectors.joining(" "));
    }

    /**
     * Get all locations within the same state
     */
    public List<String> getLocationsByState(String stateCode) {
        try {
            if (stateCode == null || stateCode.trim().isEmpty()) {
                return Collections.emptyList();
            }

            if (!stateCode.matches("^[A-Z]{2}$")) {
                throw new ValidationException("Invalid state code format: " + stateCode);
            }

            return districtLookupRepository.findAll()
                    .stream()
                    .map(DistrictLookup::getLocationName)
                    .filter(location -> location.endsWith(" " + stateCode))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get locations by state: {}", stateCode, e);
            throw new ServiceException("Failed to get locations by state: " + e.getMessage(), e);
        }
    }

    /**
     * Get districts by state code (alias for getLocationsByState)
     */
    public List<String> getDistrictsByStateCode(String stateCode) {
        return getLocationsByState(stateCode);
    }

    /**
     * Get locations by state name
     */
    public List<String> getLocationsByStateName(String stateName) {
        String stateCode = getStateCodeByName(stateName);
        if (stateCode == null) {
            return Collections.emptyList();
        }

        return getLocationsByState(stateCode);
    }

    /**
     * Get districts by state name
     */
    public List<String> getDistrictsByStateName(String stateName) {
        return getLocationsByStateName(stateName);
    }

    /**
     * Get state code by state name
     */
    public String getStateCodeByName(String stateName) {
        return Constant.STATE_CODES.entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(stateName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get state name by state code
     */
    public String getStateNameByCode(String stateCode) {
        return Constant.STATE_CODES.get(stateCode.toUpperCase());
    }

    /**
     * Get all available states
     */
    public List<String> getAllStates() {
        return new ArrayList<>(Constant.STATE_CODES.values());
    }

    /**
     * Get all state codes
     */
    public List<String> getAllStateCodes() {
        return new ArrayList<>(Constant.STATE_CODES.keySet());
    }

    /**
     * Group locations by state
     */
    public Map<String, List<String>> getLocationsByStateGrouped() {
        List<String> allLocations = getAvailableDistricts();

        return allLocations.stream()
                .collect(Collectors.groupingBy(
                        location -> {
                            String stateCode = extractStateCodeFromLocation(location);
                            return getStateNameByCode(stateCode) != null ? getStateNameByCode(stateCode) : stateCode;
                        },
                        Collectors.toList()
                ));
    }

    /**
     * Get location suggestions based on partial input
     */
    public List<String> suggestLocations(String partialLocation) {
        if (partialLocation == null || partialLocation.trim().length() < Constant.LOCATION_SUGGESTION_MIN_LENGTH) {
            return Collections.emptyList();
        }

        String query = partialLocation.trim().toLowerCase();

        return getAvailableDistricts().stream()
                .filter(location -> location.toLowerCase().contains(query))
                .sorted((a, b) -> {
                    // Prioritize locations that start with the query
                    boolean aStarts = a.toLowerCase().startsWith(query);
                    boolean bStarts = b.toLowerCase().startsWith(query);

                    if (aStarts && !bStarts) return -1;
                    if (!aStarts && bStarts) return 1;

                    return a.compareToIgnoreCase(b);
                })
                .limit(Constant.LOCATION_SUGGESTION_LIMIT)
                .collect(Collectors.toList());
    }

    /**
     * Normalize location string to proper format
     */
    public String normalizeLocation(String location) {
        if (location == null || location.trim().isEmpty()) {
            return null;
        }

        String normalized = location.trim();

        // Convert to proper case if needed
        String[] parts = normalized.split("\\s+");
        if (parts.length >= 2) {
            // Capitalize city name parts
            String cityPart = Arrays.stream(parts)
                    .limit(parts.length - 1)
                    .map(this::capitalizeFirstLetter)
                    .collect(Collectors.joining(" "));

            // Keep state code in uppercase
            String stateCode = parts[parts.length - 1].toUpperCase();

            normalized = cityPart + " " + stateCode;
        }

        return normalized;
    }

    /**
     * Validate location and provide suggestions
     */
    public LocationValidationResult validateAndSuggestLocation(String location) {
        LocationValidationResult result = new LocationValidationResult();

        if (location == null || location.trim().isEmpty()) {
            result.setValid(false);
            result.setErrorMessage("Location cannot be empty");
            return result;
        }

        String normalized = normalizeLocation(location);
        result.setNormalizedLocation(normalized);

        if (!isValidLocationFormat(normalized)) {
            result.setValid(false);
            result.setErrorMessage("Location must be in format 'City StateCode' (e.g., 'Pune MH')");
            result.setSuggestions(suggestLocations(location));
            return result;
        }

        if (!isValidLocation(normalized)) {
            result.setValid(false);
            result.setErrorMessage("Location not found in our database");
            result.setSuggestions(suggestLocations(location));
            return result;
        }

        result.setValid(true);
        return result;
    }

    /**
     * Check if two locations are in the same state
     */
    public boolean areLocationsInSameState(String location1, String location2) {
        String state1 = extractStateCodeFromLocation(location1);
        String state2 = extractStateCodeFromLocation(location2);

        return state1 != null && state1.equals(state2);
    }

    /**
     * Calculate location proximity score
     */
    public int calculateLocationProximity(String location1, String location2) {
        if (location1 == null || location2 == null) {
            return 0;
        }

        if (location1.equals(location2)) {
            return Constant.LOCATION_PROXIMITY_SAME; // Same location
        }

        if (areLocationsInSameState(location1, location2)) {
            return Constant.LOCATION_PROXIMITY_SAME_STATE; // Same state
        }

        return Constant.LOCATION_PROXIMITY_DIFFERENT_STATE; // Different states
    }

    /**
     * Calculate distance between locations based on district system
     * Returns 0 for same location, 25 for same state, 200 for different states
     */
    public double calculateDistanceBetweenLocations(String location1, String location2) {
        try {
            if (location1 == null || location2 == null) {
                return Constant.LOCATION_DISTANCE_DIFFERENT_STATE; // Default distance for invalid locations
            }

            if (location1.trim().equals(location2.trim())) {
                return Constant.LOCATION_DISTANCE_SAME; // Same location
            }

            String state1 = extractStateCodeFromLocation(location1);
            String state2 = extractStateCodeFromLocation(location2);

            if (state1 != null && state1.equals(state2)) {
                return Constant.LOCATION_DISTANCE_SAME_STATE; // Same state, different city
            }

            return Constant.LOCATION_DISTANCE_DIFFERENT_STATE; // Different states
        } catch (Exception e) {
            log.warn("Failed to calculate distance between locations: {} and {}", location1, location2, e);
            return Constant.LOCATION_DISTANCE_DIFFERENT_STATE; // Default distance on error
        }
    }

    /**
     * Get nearby locations (same state locations excluding the given location)
     */
    public List<String> getNearbyLocations(String location) {
        try {
            if (location == null || location.trim().isEmpty()) {
                return Collections.emptyList();
            }

            String stateCode = extractStateCodeFromLocation(location);
            if (stateCode == null) {
                return Collections.emptyList();
            }

            return getLocationsByState(stateCode).stream()
                    .filter(loc -> !loc.equals(location.trim()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get nearby locations for: {}", location, e);
            return Collections.emptyList();
        }
    }

    /**
     * Helper method to capitalize first letter of a word
     */
    private String capitalizeFirstLetter(String word) {
        if (word == null || word.isEmpty()) {
            return word;
        }

        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
    }

    // Inner class for location validation result
    public static class LocationValidationResult {
        private boolean valid;
        private String normalizedLocation;
        private String errorMessage;
        private List<String> suggestions;

        public LocationValidationResult() {
            this.suggestions = new ArrayList<>();
        }

        // Getters and setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }

        public String getNormalizedLocation() { return normalizedLocation; }
        public void setNormalizedLocation(String normalizedLocation) { this.normalizedLocation = normalizedLocation; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public List<String> getSuggestions() { return suggestions; }
        public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
    }
}