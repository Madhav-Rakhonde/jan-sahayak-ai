package com.JanSahayak.AI.config;


import java.util.Arrays;
import java.util.List;
import java.util.Map;
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


    // Location service constants
    public static final Pattern LOCATION_PATTERN = Pattern.compile("^[A-Za-z\\s]+ [A-Z]{2}$");
    public static final int LOCATION_SEARCH_LIMIT = 20;
    public static final int LOCATION_SUGGESTION_LIMIT = 10;
    public static final int LOCATION_SUGGESTION_MIN_LENGTH = 2;
    public static final int LOCATION_PROXIMITY_SAME = 100;
    public static final int LOCATION_PROXIMITY_SAME_STATE = 60;
    public static final int LOCATION_PROXIMITY_DIFFERENT_STATE = 20;
    public static final double LOCATION_DISTANCE_SAME = 0.0;
    public static final double LOCATION_DISTANCE_SAME_STATE = 25.0;
    public static final double LOCATION_DISTANCE_DIFFERENT_STATE = 200.0;

    // State code to state name mapping
    public static final Map<String, String> STATE_CODES = Map.ofEntries(
            Map.entry("MH", "Maharashtra"),
            Map.entry("DL", "Delhi"),
            Map.entry("HR", "Haryana"),
            Map.entry("UP", "Uttar Pradesh"),
            Map.entry("KA", "Karnataka"),
            Map.entry("TN", "Tamil Nadu"),
            Map.entry("TG", "Telangana"),
            Map.entry("WB", "West Bengal"),
            Map.entry("GJ", "Gujarat"),
            Map.entry("RJ", "Rajasthan"),
            Map.entry("AP", "Andhra Pradesh"),
            Map.entry("KL", "Kerala"),
            Map.entry("OR", "Odisha"),
            Map.entry("JH", "Jharkhand"),
            Map.entry("AS", "Assam"),
            Map.entry("PB", "Punjab"),
            Map.entry("BR", "Bihar"),
            Map.entry("UK", "Uttarakhand"),
            Map.entry("HP", "Himachal Pradesh"),
            Map.entry("JK", "Jammu and Kashmir")
    );
}