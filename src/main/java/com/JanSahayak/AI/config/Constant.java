package com.JanSahayak.AI.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

public class Constant {

    // Media file extensions
    public static final List<String> ALLOWED_IMAGE_EXTENSIONS =
            Arrays.asList(".jpg", ".jpeg", ".png", ".webp");
    public static final List<String> ALLOWED_VIDEO_EXTENSIONS =
            Arrays.asList(".mp4", ".mov");

    // Enhanced media specifications
    public static final int MAX_VIDEO_DURATION_SECONDS = 140; // 2 minutes 20 seconds
    public static final int MIN_VIDEO_RESOLUTION = 32;
    public static final int MAX_VIDEO_RESOLUTION = 1920;
    public static final double MAX_VIDEO_FRAME_RATE = 40.0;
    public static final int MAX_VIDEO_BITRATE = 25000000; // 25 Mbps
    public static final int MAX_IMAGE_WIDTH = 4096;
    public static final int MAX_IMAGE_HEIGHT = 4096;
    public static final int MIN_IMAGE_DIMENSION = 1;

    // Content length limits
    public static final int MAX_POST_CONTENT_LENGTH = 2000;
    public static final int MAX_LOCATION_LENGTH = 200;
    public static final int MAX_COMMENT_LENGTH = 1000;

    // Role constants
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_DEPARTMENT = "ROLE_DEPARTMENT";
    public static final String ROLE_USER = "ROLE_USER";

    // Feed service constants
    public static final int DEFAULT_FEED_LIMIT = 20;
    public static final int MAX_FEED_LIMIT = 100;

    // Location-based feed distance constants (in kilometers)
    public static final double STATE_DISTANCE_KM = 25.0;
    public static final double COUNTRY_DISTANCE_KM = 200.0;

    // User tagging constants
    public static final Pattern USERNAME_TAG_PATTERN = Pattern.compile("@([a-zA-Z0-9_]+)");
    public static final int MAX_TAGS_PER_POST = 10;
    public static final int MAX_TAG_CONTEXT_LENGTH = 100;

    // User interaction service constants
    public static final int VIEW_DUPLICATE_PREVENTION_HOURS = 1;
    public static final long VIEW_DUPLICATE_PREVENTION_MILLIS = VIEW_DUPLICATE_PREVENTION_HOURS * 60 * 60 * 1000;
    public static final int RECENT_ACTIVITY_DAYS = 7;
    public static final long RECENT_ACTIVITY_MILLIS = RECENT_ACTIVITY_DAYS * 24L * 60 * 60 * 1000;
    public static final int RECENT_INTERACTIONS_HOURS = 24;
    public static final long RECENT_INTERACTIONS_MILLIS = RECENT_INTERACTIONS_HOURS * 60 * 60 * 1000;
    public static final int TOP_ENGAGING_POSTS_LIMIT = 5;
    public static final int POST_CONTENT_PREVIEW_LENGTH = 100;
    public static final String POST_CONTENT_TRUNCATION_SUFFIX = "...";

    // ===== INDIA-ONLY APPLICATION CONSTANTS =====
    public static final String DEFAULT_TARGET_COUNTRY = "IN";
    public static final String APP_COUNTRY_CODE = "IN";
    public static final String COUNTRY_NAME_INDIA = "India";
    public static final boolean FORCE_INDIA_ONLY = true;
    public static final String INVALID_COUNTRY_MESSAGE = "This application only supports India (IN)";

    // Broadcasting visibility constants - CRITICAL for India-only app
    public static final boolean COUNTRY_BROADCASTS_VISIBLE_TO_ALL = true; // Country-wide broadcasts visible to all users
    public static final boolean GOVERNMENT_BROADCASTS_PRIORITY = true; // Government broadcasts get higher priority

    // Role-based broadcasting permissions
    public static final Set<String> COUNTRY_BROADCAST_ROLES = new HashSet<>(Arrays.asList("ROLE_ADMIN", "ROLE_DEPARTMENT"));
    public static final Set<String> STATE_BROADCAST_ROLES = new HashSet<>(Arrays.asList("ROLE_ADMIN", "ROLE_DEPARTMENT"));
    public static final Set<String> DISTRICT_BROADCAST_ROLES = new HashSet<>(Arrays.asList("ROLE_ADMIN", "ROLE_DEPARTMENT"));
    public static final Set<String> AREA_BROADCAST_ROLES = new HashSet<>(Arrays.asList("ROLE_ADMIN", "ROLE_DEPARTMENT", "ROLE_USER"));

    // Broadcasting limits for India
    public static final int MAX_TARGET_STATES = 10;
    public static final int MAX_TARGET_DISTRICTS = 25;
    public static final int MAX_TARGET_PINCODES = 100;

    // Indian pincode validation patterns
    public static final String INDIAN_PINCODE_PATTERN = "^[1-9]\\d{5}$";
    public static final String INDIAN_STATE_PREFIX_PATTERN = "^[1-9]\\d$";
    public static final String INDIAN_DISTRICT_PREFIX_PATTERN = "^[1-9]\\d{2}$";

    // Pattern for valid Indian pincodes (must not start with 0)
    public static final Pattern INDIAN_PINCODE_REGEX = Pattern.compile(INDIAN_PINCODE_PATTERN);

    // Geographic scope constants for India - all valid state prefixes
    public static final Set<String> INDIA_STATE_PREFIXES = new HashSet<>(Arrays.asList(
            "11", "12", "13", "14", "15", "16", "17", "18", "19", // Northern states
            "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", // UP, MP regions
            "30", "31", "32", "33", "34", "35", "36", "37", "38", "39", // Rajasthan, Gujarat
            "40", "41", "42", "43", "44", "45", "46", "47", "48", "49", // Maharashtra, MP, Goa
            "50", "51", "52", "53", "54", "56", "57", "58", "59", // Telangana, AP, Karnataka
            "60", "61", "62", "63", "64", "67", "68", "69", // Tamil Nadu, Kerala
            "70", "71", "72", "73", "74", "75", "76", "77", "78", "79", // West Bengal, Northeast
            "80", "81", "82", "83", "84", "85" // Bihar, Jharkhand
    ));

    // ===== UTILITY METHODS FOR INDIA-ONLY APP =====

    /**
     * Check if a pincode is valid Indian format (6 digits, not starting with 0)
     */
    public static boolean isValidIndianPincode(String pincode) {
        return pincode != null && INDIAN_PINCODE_REGEX.matcher(pincode).matches();
    }

    /**
     * Get state prefix from Indian pincode
     */
    public static String getStatePrefixFromPincode(String pincode) {
        if (!isValidIndianPincode(pincode)) {
            return null;
        }
        return pincode.substring(0, 2);
    }

    /**
     * Get district prefix from Indian pincode
     */
    public static String getDistrictPrefixFromPincode(String pincode) {
        if (!isValidIndianPincode(pincode)) {
            return null;
        }
        return pincode.substring(0, 3);
    }

    /**
     * Check if state prefix is valid for India
     */
    public static boolean isValidIndianStatePrefix(String statePrefix) {
        return statePrefix != null && INDIA_STATE_PREFIXES.contains(statePrefix);
    }

    /**
     * Validate that target country is India
     */
    public static String normalizeTargetCountry(String targetCountry) {
        // Always return India for this India-only application
        return DEFAULT_TARGET_COUNTRY;
    }

    /**
     * Check if user role can create country-wide broadcasts
     */
    public static boolean canCreateCountryBroadcast(String roleName) {
        return roleName != null && COUNTRY_BROADCAST_ROLES.contains(roleName);
    }

    /**
     * Check if this is a government/department role
     */
    public static boolean isGovernmentRole(String roleName) {
        return ROLE_ADMIN.equals(roleName) || ROLE_DEPARTMENT.equals(roleName);
    }

    /**
     * Check if country-wide broadcasts should be visible to all users
     */
    public static boolean shouldCountryBroadcastBeVisibleToAll() {
        return COUNTRY_BROADCASTS_VISIBLE_TO_ALL && FORCE_INDIA_ONLY;
    }
    // ===== PAGINATION CONSTANTS =====

    // Cursor validation constants
    public static final long MIN_VALID_CURSOR = 1L;
    public static final long MAX_VALID_CURSOR = Long.MAX_VALUE;
    public static final long MAX_TIMESTAMP_AGE_MILLIS = 365L * 24 * 60 * 60 * 1000; // 1 year

    // User search and listing limits
    public static final int DEFAULT_USER_SEARCH_LIMIT = 10;
    public static final int MAX_USER_SEARCH_LIMIT = 50;
    public static final int DEFAULT_DEPARTMENT_SEARCH_LIMIT = 15;
    public static final int MAX_DEPARTMENT_SEARCH_LIMIT = 100;

    // Tagging-specific pagination limits
    public static final int MAX_TAGGING_SUGGESTIONS_LIMIT = MAX_TAGS_PER_POST * 4; // 40 suggestions max
    public static final int DEFAULT_TAGGING_SUGGESTIONS_LIMIT = MAX_TAGS_PER_POST * 2; // 20 suggestions default

    // Geographic search limits for India
    public static final int MAX_PINCODE_SEARCH_RESULTS = 200;
    public static final int MAX_STATE_SEARCH_RESULTS = 500;
    public static final int MAX_DISTRICT_SEARCH_RESULTS = 300;

    // User activity pagination
    public static final int DEFAULT_ACTIVE_USER_LIMIT = DEFAULT_FEED_LIMIT;
    public static final int MAX_ACTIVE_USER_LIMIT = MAX_FEED_LIMIT;

    // Debug and logging
    public static final boolean ENABLE_PAGINATION_LOGGING = false; // Set to true for debugging
}