package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PostContentUpdateDto;
import com.JanSahayak.AI.DTO.PostCreateDto;
import com.JanSahayak.AI.DTO.PostTaggingStatsDto;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.*;
import com.JanSahayak.AI.model.Comment;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.*;
import jakarta.transaction.Transactional;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepo postRepository;
    private final UserTaggingService userTaggingService;
    private final UserRepo userRepository;
    private final PostViewRepo postViewRepository;
    private final CommentRepo commentRepository;
    private final LocationService locationService;

    @Value("${app.upload.dir:${user.home}/uploads/posts}")
    private String uploadDir;

    @Value("${app.upload.max-image-size:5242880}") // 5MB for images
    private long maxImageSize;

    @Value("${app.upload.max-video-size:536870912}") // 512MB for videos
    private long maxVideoSize;

    // File cleanup retry queue
    private final Queue<String> fileCleanupQueue = new LinkedList<>();

    @Transactional(rollbackOn = Exception.class)
    public Post createPost(PostCreateDto postDto, User user, MultipartFile mediaFile) {
        log.info("Creating new post by user: {} (ID: {})", user.getActualUsername(), user.getId());

        try {
            // Enhanced validation
            validateUser(user);
            validatePostContent(postDto.getContent());
            validateLocation(postDto.getLocation());

            // Validate and upload media file if provided
            String fileName = null;
            if (mediaFile != null && !mediaFile.isEmpty()) {
                fileName = uploadMediaFile(mediaFile, user.getId());
            }

            Post post = new Post();
            post.setContent(postDto.getContent().trim());
            post.setUser(user);
            post.setLocation(postDto.getLocation().trim());
            post.setImageName(fileName);
            post.setStatus(PostStatus.ACTIVE); // All new posts start as ACTIVE
            post.setCreatedAt(new Date());

            // Save post first
            post = postRepository.save(post);

            // Process user tags in content
            try {
                userTaggingService.processUserTags(post);
            } catch (Exception e) {
                log.warn("Failed to process user tags for post: {}", post.getId(), e);
                // Don't fail the entire operation for tagging issues
            }

            if (fileName != null) {
                log.info("Post with media created successfully with ID: {}, status: {}, media: {}, location: {}",
                        post.getId(), post.getStatus().getDisplayName(), fileName, post.getLocation());
            } else {
                log.info("Text post created successfully with ID: {}, status: {}, location: {}",
                        post.getId(), post.getStatus().getDisplayName(), post.getLocation());
            }

            return post;
        } catch (ValidationException | MediaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create post for user: {} (ID: {})", user.getActualUsername(), user.getId(), e);
            throw new ServiceException("Failed to create post: " + e.getMessage(), e);
        }
    }

    // Overloaded method for text-only posts (for backward compatibility)
    @Transactional(rollbackOn = Exception.class)
    public Post createPost(PostCreateDto postDto, User user) {
        return createPost(postDto, user, null);
    }

    @Transactional(rollbackOn = Exception.class)
    public Post updatePostMedia(Long postId, MultipartFile mediaFile, User currentUser) {
        try {
            validatePostId(postId);
            validateUser(currentUser);

            Post post = findById(postId);

            // Enhanced ownership validation
            if (!isPostOwner(post, currentUser)) {
                throw new SecurityException("Only post creator can update post media");
            }

            // Enhanced status validation with null safety
            if (post.getStatus() == null) {
                throw new ServiceException("Post status is invalid");
            }

            if (!post.getStatus().allowsUpdates()) {
                throw new SecurityException("Cannot update media for posts with status: " + post.getStatus().getDisplayName());
            }

            // Store old file for cleanup
            String oldFileName = post.getImageName();

            // Upload new media file
            String fileName = null;
            if (mediaFile != null && !mediaFile.isEmpty()) {
                fileName = uploadMediaFile(mediaFile, currentUser.getId());
            }

            post.setImageName(fileName);
            post.setUpdatedAt(new Date());

            Post updatedPost = postRepository.save(post);

            // Delete old media file if exists (async for better performance)
            if (oldFileName != null && !oldFileName.trim().isEmpty()) {
                CompletableFuture.runAsync(() -> deleteMediaFile(oldFileName));
            }

            log.info("Updated media for post ID: {}, status: {}, new media: {}",
                    post.getId(), post.getStatus().getDisplayName(), fileName != null ? fileName : "removed");

            return updatedPost;
        } catch (SecurityException | MediaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update post media for post ID: {} by user: {}", postId,
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to update post media: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackOn = Exception.class)
    public Post removePostMedia(Long postId, User currentUser) {
        try {
            validatePostId(postId);
            validateUser(currentUser);

            Post post = findById(postId);

            // Enhanced ownership validation
            if (!isPostOwner(post, currentUser)) {
                throw new SecurityException("Only post creator can remove post media");
            }

            // Enhanced status validation
            if (post.getStatus() == null) {
                throw new ServiceException("Post status is invalid");
            }

            if (!post.getStatus().allowsUpdates()) {
                throw new SecurityException("Cannot remove media for posts with status: " + post.getStatus().getDisplayName());
            }

            // Store old file for cleanup
            String oldFileName = post.getImageName();

            // Remove media file if exists
            if (post.hasImage()) {
                post.setImageName(null);
                post.setUpdatedAt(new Date());
            }

            Post updatedPost = postRepository.save(post);

            // Delete file asynchronously
            if (oldFileName != null && !oldFileName.trim().isEmpty()) {
                CompletableFuture.runAsync(() -> deleteMediaFile(oldFileName));
            }

            log.info("Removed media from post ID: {}, status: {}", post.getId(), post.getStatus().getDisplayName());

            return updatedPost;
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to remove post media for post ID: {} by user: {}", postId,
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to remove post media: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackOn = Exception.class)
    public Post updatePostResolution(Long postId, Boolean isResolved, User user, String updateMessage) {
        try {
            validatePostId(postId);
            validateUser(user);

            Post post = findById(postId);

            // Enhanced user tagging validation using entity method
            if (!post.hasActiveUserTag(user)) {
                throw new SecurityException("Only tagged users can update post resolution status");
            }

            // Enhanced status validation
            if (post.getStatus() == null) {
                throw new ServiceException("Post status is invalid");
            }

            // Determine new status based on isResolved parameter
            PostStatus newStatus = isResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;

            // Check if transition is allowed
            if (!post.getStatus().canTransitionTo(newStatus)) {
                throw new SecurityException("Cannot transition from " + post.getStatus().getDisplayName() +
                        " to " + newStatus.getDisplayName());
            }

            // Update resolution fields using entity methods to ensure consistency
            if (isResolved) {
                post.markAsResolved(updateMessage != null ? updateMessage.trim() : null);
                log.info("Post ID: {} marked as resolved by user: {} (ID: {})",
                        postId, user.getActualUsername(), user.getId());
            } else {
                post.markAsUnresolved();
                log.info("Post ID: {} marked as active by user: {} (ID: {})",
                        postId, user.getActualUsername(), user.getId());
            }

            // Create status update comment if message provided
            if (updateMessage != null && !updateMessage.trim().isEmpty()) {
                try {
                    Comment statusComment = new Comment();
                    statusComment.setText("Status Update (" + post.getStatus().getDisplayName() + "): " + updateMessage.trim());
                    statusComment.setUser(user);
                    statusComment.setPost(post);
                    statusComment.setCreatedAt(new Date());
                    commentRepository.save(statusComment);
                } catch (Exception e) {
                    log.warn("Failed to create status update comment for post: {}", postId, e);
                    // Don't fail the entire operation for comment creation issues
                }
            }

            return postRepository.save(post);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update post resolution for post ID: {} by user: {}",
                    postId, user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to update post resolution: " + e.getMessage(), e);
        }
    }

    public List<Post> getPostsTaggedWithUser(Long userId) {
        try {
            validateUserId(userId);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));

            List<Post> posts = postRepository.findPostsTaggedWithUser(user);
            return posts != null ? posts : Collections.emptyList();
        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get posts tagged with user: {}", userId, e);
            throw new ServiceException("Failed to get posts tagged with user: " + e.getMessage(), e);
        }
    }

    public Post findById(Long postId) {
        try {
            validatePostId(postId);
            return postRepository.findById(postId)
                    .orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));
        } catch (PostNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to find post by ID: {}", postId, e);
            throw new ServiceException("Failed to find post: " + e.getMessage(), e);
        }
    }

    public List<Post> getAllPosts() {
        try {
            List<Post> posts = postRepository.findAll();
            return posts != null ? posts : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get all posts", e);
            throw new ServiceException("Failed to get all posts: " + e.getMessage(), e);
        }
    }

    public List<Post> getAllActivePosts() {
        try {
            List<Post> posts = postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE);
            return posts != null ? posts : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get all active posts", e);
            throw new ServiceException("Failed to get all active posts: " + e.getMessage(), e);
        }
    }

    public List<Post> getAllResolvedPosts() {
        try {
            List<Post> posts = postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.RESOLVED);
            return posts != null ? posts : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get all resolved posts", e);
            throw new ServiceException("Failed to get all resolved posts: " + e.getMessage(), e);
        }
    }

    public List<Post> getPostsByUser(User user) {
        try {
            validateUser(user);
            List<Post> posts = postRepository.findByUserOrderByCreatedAtDesc(user);
            return posts != null ? posts : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get posts by user: {}", user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to get posts by user: " + e.getMessage(), e);
        }
    }

    public List<Post> getActivePostsByUser(User user) {
        try {
            validateUser(user);
            List<Post> posts = postRepository.findByUserAndStatusOrderByCreatedAtDesc(user, PostStatus.ACTIVE);
            return posts != null ? posts : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get active posts by user: {}", user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to get active posts by user: " + e.getMessage(), e);
        }
    }

    public List<Post> getResolvedPostsByUser(User user) {
        try {
            validateUser(user);
            List<Post> posts = postRepository.findByUserAndStatusOrderByCreatedAtDesc(user, PostStatus.RESOLVED);
            return posts != null ? posts : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get resolved posts by user: {}", user != null ? user.getActualUsername() : "null", e);
            throw new ServiceException("Failed to get resolved posts by user: " + e.getMessage(), e);
        }
    }



    public Long countActivePosts() {
        try {
            Long count = postRepository.countByStatus(PostStatus.ACTIVE);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("Failed to count active posts", e);
            throw new ServiceException("Failed to count active posts: " + e.getMessage(), e);
        }
    }

    public Long countResolvedPosts() {
        try {
            Long count = postRepository.countByStatus(PostStatus.RESOLVED);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("Failed to count resolved posts", e);
            throw new ServiceException("Failed to count resolved posts: " + e.getMessage(), e);
        }
    }

    public List<Post> getPostsWithMultipleUserTags() {
        try {
            List<Post> posts = postRepository.findPostsWithMultipleUserTags();
            return posts != null ? posts : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get posts with multiple user tags", e);
            throw new ServiceException("Failed to get posts with multiple user tags: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackOn = Exception.class)
    public Post updatePostContent(Long postId, String newContent, User currentUser) {
        try {
            validatePostId(postId);
            validateUser(currentUser);
            validatePostContent(newContent);

            Post post = findById(postId);

            // Enhanced ownership validation
            if (!isPostOwner(post, currentUser)) {
                throw new SecurityException("Only post creator can update post content");
            }

            // Enhanced status validation
            if (post.getStatus() == null) {
                throw new ServiceException("Post status is invalid");
            }

            if (!post.getStatus().allowsUpdates()) {
                throw new SecurityException("Cannot update content for posts with status: " + post.getStatus().getDisplayName());
            }

            String oldContent = post.getContent();
            post.setContent(newContent.trim());
            post.setUpdatedAt(new Date());

            // Reprocess tags with new content
            try {
                userTaggingService.updatePostTags(post, newContent.trim());
            } catch (Exception e) {
                log.warn("Failed to update tags for post: {}", postId, e);
                // Don't fail the entire operation for tagging issues
            }

            Post updatedPost = postRepository.save(post);
            log.info("Updated content for post ID: {}, status: {}, content changed: {}",
                    post.getId(), post.getStatus().getDisplayName(), !Objects.equals(oldContent, newContent));

            return updatedPost;
        } catch (PostNotFoundException | SecurityException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update post content: {} by user: {}",
                    postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to update post content: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackOn = Exception.class)
    public Post updatePostContent(Long postId, PostContentUpdateDto contentUpdateDto, User currentUser) {
        try {
            if (contentUpdateDto == null) {
                throw new ValidationException("Content update data cannot be null");
            }
            return updatePostContent(postId, contentUpdateDto.getContent(), currentUser);
        } catch (Exception e) {
            log.error("Failed to update post content via DTO: {} by user: {}",
                    postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw e;
        }
    }

    public List<Post> getTrendingPosts(int days, int limit) {
        try {
            if (days <= 0) {
                throw new ValidationException("Days must be positive");
            }
            if (limit <= 0 || limit > 1000) {
                throw new ValidationException("Limit must be between 1 and 1000");
            }

            LocalDateTime startDate = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
            List<Post> posts = postRepository.findTrendingPosts(
                    Timestamp.valueOf(startDate),
                    PageRequest.of(0, limit)
            );
            return posts != null ? posts : Collections.emptyList();
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get trending posts (days: {}, limit: {})", days, limit, e);
            throw new ServiceException("Failed to get trending posts: " + e.getMessage(), e);
        }
    }


    private List<Post> getPostsByLocationAndStatus(String location, Boolean includeResolved) {
        try {
            if (location == null || location.trim().isEmpty()) {
                return Collections.emptyList();
            }

            List<Post> posts;
            if (includeResolved != null) {
                PostStatus status = includeResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                posts = postRepository.findByLocationAndStatus(location, status);
            } else {
                posts = postRepository.findByLocationOrderByCreatedAtDesc(location);
            }
            return posts != null ? posts : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get posts by location and status: {}", location, e);
            return Collections.emptyList();
        }
    }

    @Transactional(rollbackOn = Exception.class)
    public Post tagUsersToPost(Long postId, List<Long> userIds, User currentUser) {
        try {
            validatePostId(postId);
            validateUser(currentUser);

            if (userIds == null || userIds.isEmpty()) {
                throw new ValidationException("User IDs list cannot be empty");
            }

            // Validate user IDs
            for (Long userId : userIds) {
                validateUserId(userId);
            }

            Post post = findById(postId);

            // Enhanced status validation
            if (post.getStatus() == null) {
                throw new ServiceException("Post status is invalid");
            }

            if (!post.getStatus().allowsUpdates()) {
                throw new SecurityException("Cannot add tags to posts with status: " + post.getStatus().getDisplayName());
            }

            // Enhanced permission check
            if (!canUserModifyPostTags(post, currentUser)) {
                throw new SecurityException("Only post creator, department users, or admin can add user tags");
            }

            List<User> usersToTag = userRepository.findAllById(userIds);
            if (usersToTag.size() != userIds.size()) {
                List<Long> foundIds = usersToTag.stream().map(User::getId).collect(Collectors.toList());
                List<Long> missingIds = userIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
                throw new ValidationException("Users not found with IDs: " + missingIds);
            }

            int successCount = 0;
            for (User userToTag : usersToTag) {
                try {
                    userTaggingService.addUserTag(post, userToTag);
                    successCount++;
                } catch (Exception e) {
                    log.warn("Failed to tag user: {} to post: {}", userToTag.getActualUsername(), postId, e);
                }
            }

            log.info("Added {} user tags to post ID: {} (status: {}), attempted: {}",
                    successCount, post.getId(), post.getStatus().getDisplayName(), usersToTag.size());

            return post;
        } catch (PostNotFoundException | SecurityException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to tag users to post: {} by user: {}",
                    postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to tag users to post: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackOn = Exception.class)
    public Post removeUserTagFromPost(Long postId, Long userId, User currentUser) {
        try {
            validatePostId(postId);
            validateUserId(userId);
            validateUser(currentUser);

            Post post = findById(postId);
            User userToRemove = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));

            // Enhanced status validation
            if (post.getStatus() == null) {
                throw new ServiceException("Post status is invalid");
            }

            if (!post.getStatus().allowsUpdates()) {
                throw new SecurityException("Cannot remove tags from posts with status: " + post.getStatus().getDisplayName());
            }

            // Enhanced permission check
            if (!canUserRemovePostTag(post, currentUser, userId)) {
                throw new SecurityException("Insufficient permissions to remove user tag");
            }

            try {
                userTaggingService.removeUserTag(post, userToRemove);

                log.info("Removed user tag for user: {} (ID: {}) from post ID: {} (status: {}) by user: {}",
                        userToRemove.getActualUsername(), userId, post.getId(),
                        post.getStatus().getDisplayName(), currentUser.getActualUsername());
            } catch (Exception e) {
                log.warn("Failed to remove user tag: {} from post: {}", userId, postId, e);
                throw new ServiceException("Failed to remove user tag: " + e.getMessage(), e);
            }

            return post;
        } catch (PostNotFoundException | UserNotFoundException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to remove user tag from post: {} by user: {}",
                    postId, currentUser != null ? currentUser.getActualUsername() : "null", e);
            throw new ServiceException("Failed to remove user tag from post: " + e.getMessage(), e);
        }
    }

    // Enhanced media file handling methods with comprehensive validation and security
    private String uploadMediaFile(MultipartFile file, Long userId) {
        try {
            validateUserId(userId);
            validateMediaFile(file);

            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("Created upload directory: {}", uploadPath);
            }

            // Generate unique filename with enhanced security
            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null || originalFileName.trim().isEmpty()) {
                throw new MediaValidationException("Invalid file name");
            }

            String fileExtension = getFileExtension(originalFileName);
            String sanitizedFileName = sanitizeFileName(originalFileName);

            // Generate secure unique filename
            String uniqueFileName = String.format("%d_%s_%d_%s%s",
                    userId,
                    generateSecureRandomString(8),
                    System.currentTimeMillis(),
                    sanitizedFileName.substring(0, Math.min(sanitizedFileName.length(), 20)),
                    fileExtension);

            // Save file
            Path filePath = uploadPath.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Verify file was saved correctly
            if (!Files.exists(filePath) || Files.size(filePath) != file.getSize()) {
                throw new FileUploadException("File upload verification failed");
            }

            log.info("Media file uploaded successfully: {} (size: {} bytes)",
                    uniqueFileName, file.getSize());
            return uniqueFileName;

        } catch (IOException e) {
            log.error("Failed to upload media file for user: {}", userId, e);
            throw new FileUploadException("Failed to upload media file: " + e.getMessage(), e);
        }
    }

    private void validateMediaFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MediaValidationException("File cannot be empty");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new MediaValidationException("Invalid file name");
        }

        String extension = getFileExtension(fileName).toLowerCase();
        String contentType = file.getContentType();

        // Enhanced security: Check file content matches extension
        validateFileContent(file, extension);

        // Validate file type and size
        if (Constant.ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            validateImageFile(file, extension, contentType);
        } else if (Constant.ALLOWED_VIDEO_EXTENSIONS.contains(extension)) {
            validateVideoFile(file, extension, contentType);
        } else {
            throw new MediaValidationException("File type not supported. Allowed types: " +
                    "Images: " + String.join(", ", Constant.ALLOWED_IMAGE_EXTENSIONS) +
                    ", Videos: " + String.join(", ", Constant.ALLOWED_VIDEO_EXTENSIONS));
        }
    }

    private void validateFileContent(MultipartFile file, String extension) {
        try {
            byte[] header = new byte[Math.min(512, (int) file.getSize())];
            file.getInputStream().read(header, 0, header.length);
            file.getInputStream().reset();

            // Basic file signature validation
            String hexHeader = bytesToHex(header).toUpperCase();

            switch (extension.toLowerCase()) {
                case ".jpg":
                case ".jpeg":
                    if (!hexHeader.startsWith("FFD8FF")) {
                        throw new MediaValidationException("File content doesn't match JPEG format");
                    }
                    break;
                case ".png":
                    if (!hexHeader.startsWith("89504E47")) {
                        throw new MediaValidationException("File content doesn't match PNG format");
                    }
                    break;
                case ".mp4":
                    // MP4 files can have various signatures, basic check for common ones
                    if (!hexHeader.contains("667479") && !hexHeader.contains("6D6F6F76")) {
                        log.warn("MP4 file signature validation inconclusive for file: {}", file.getOriginalFilename());
                    }
                    break;
                default:
                    log.debug("No content validation implemented for extension: {}", extension);
            }
        } catch (IOException e) {
            throw new MediaValidationException("Failed to validate file content: " + e.getMessage());
        }
    }

    private void validateImageFile(MultipartFile file, String extension, String contentType) {
        // Check image file size
        if (file.getSize() > maxImageSize) {
            throw new MediaValidationException("Image size exceeds maximum allowed size of " +
                    (maxImageSize / 1024 / 1024) + "MB");
        }

        // Validate content type with enhanced security
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new MediaValidationException("Invalid image content type: " + contentType);
        }

        // Additional content type validation
        Map<String, String> extensionToContentType = Map.of(
                ".jpg", "image/jpeg",
                ".jpeg", "image/jpeg",
                ".png", "image/png",
                ".webp", "image/webp"
        );

        String expectedContentType = extensionToContentType.get(extension.toLowerCase());
        if (expectedContentType != null && !expectedContentType.equals(contentType)) {
            log.warn("Content type mismatch for file: {} (expected: {}, actual: {})",
                    file.getOriginalFilename(), expectedContentType, contentType);
        }

        log.debug("Image validation passed: {} ({:.2f}MB, type: {})",
                file.getOriginalFilename(), file.getSize() / 1024.0 / 1024.0, contentType);
    }

    private void validateVideoFile(MultipartFile file, String extension, String contentType) {
        // Check video file size
        if (file.getSize() > maxVideoSize) {
            throw new MediaValidationException("Video size exceeds maximum allowed size of " +
                    (maxVideoSize / 1024 / 1024) + "MB");
        }

        // Validate content type
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new MediaValidationException("Invalid video content type: " + contentType);
        }

        // Support MP4 and MOV formats with content type validation
        if (extension.equals(".mp4") && !contentType.equals("video/mp4")) {
            log.warn("Content type mismatch for MP4 file: {} (type: {})", file.getOriginalFilename(), contentType);
        }
        if (extension.equals(".mov") && !contentType.contains("quicktime") && !contentType.contains("mov")) {
            log.warn("Content type mismatch for MOV file: {} (type: {})", file.getOriginalFilename(), contentType);
        }

        log.info("Video validation passed: {} ({:.2f}MB, type: {})",
                file.getOriginalFilename(), file.getSize() / 1024.0 / 1024.0, contentType);
    }

    // Enhanced validation methods with comprehensive null safety
    private void validatePostContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ValidationException("Post content cannot be empty");
        }
        if (content.length() > Constant.MAX_POST_CONTENT_LENGTH) {
            throw new ValidationException("Post content cannot exceed " + Constant.MAX_POST_CONTENT_LENGTH + " characters");
        }
        // Additional content validation
        if (content.trim().length() < 3) {
            throw new ValidationException("Post content must be at least 3 characters long");
        }
    }

    private void validateLocation(String location) {
        if (location == null || location.trim().isEmpty()) {
            throw new ValidationException("Post location cannot be empty");
        }
        if (location.length() > Constant.MAX_LOCATION_LENGTH) {
            throw new ValidationException("Location cannot exceed " + Constant.MAX_LOCATION_LENGTH + " characters");
        }
    }

    private void validateLocationString(String location) {
        if (location == null || location.trim().isEmpty()) {
            throw new ValidationException("Location cannot be empty");
        }
    }

    private void validateUser(User user) {
        if (user == null) {
            throw new UserNotFoundException("User cannot be null");
        }
        if (user.getId() == null) {
            throw new UserNotFoundException("User ID cannot be null");
        }
        if (user.getActualUsername() == null || user.getActualUsername().trim().isEmpty()) {
            throw new UserNotFoundException("User username cannot be null or empty");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ValidationException("User ID must be a positive number");
        }
    }

    private void validatePostId(Long postId) {
        if (postId == null || postId <= 0) {
            throw new ValidationException("Post ID must be a positive number");
        }
    }

    private boolean isPostOwner(Post post, User user) {
        return post != null &&
                post.getUser() != null &&
                post.getUser().getId() != null &&
                user != null &&
                user.getId() != null &&
                post.getUser().getId().equals(user.getId());
    }

    private boolean canUserModifyPostTags(Post post, User currentUser) {
        if (post == null || currentUser == null) {
            return false;
        }

        return isPostOwner(post, currentUser) ||
                hasRole(currentUser, Constant.ROLE_ADMIN) ||
                hasRole(currentUser, Constant.ROLE_DEPARTMENT);
    }

    private boolean canUserRemovePostTag(Post post, User currentUser, Long taggedUserId) {
        if (post == null || currentUser == null || taggedUserId == null) {
            return false;
        }
        return isPostOwner(post, currentUser) ||
                hasRole(currentUser, Constant.ROLE_ADMIN);
        // Department users and tagged users can no longer remove tags
    }

    private boolean hasRole(User user, String roleName) {
        try {
            return user != null &&
                    user.getRole() != null &&
                    user.getRole().getName() != null &&
                    roleName.equals(user.getRole().getName());
        } catch (Exception e) {
            log.debug("Error checking role for user: {}", user != null ? user.getActualUsername() : "null", e);
            return false;
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        // Remove file extension and dangerous characters
        String nameWithoutExt = fileName.contains(".") ?
                fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
        return nameWithoutExt.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String generateSecureRandomString(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        return sb.toString();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private void deleteMediaFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return;
        }

        try {
            Path filePath = Paths.get(uploadDir, fileName);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Media file deleted successfully: {}", fileName);
            } else {
                log.warn("Attempted to delete non-existent file: {}", fileName);
            }
        } catch (IOException e) {
            log.error("Failed to delete media file: {}", fileName, e);
            // Add to cleanup queue for retry
            scheduleFileCleanupRetry(fileName);
        } catch (Exception e) {
            log.error("Unexpected error while deleting media file: {}", fileName, e);
            scheduleFileCleanupRetry(fileName);
        }
    }

    private void scheduleFileCleanupRetry(String fileName) {
        if (fileName != null && !fileName.trim().isEmpty()) {
            fileCleanupQueue.offer(fileName);
            if (fileCleanupQueue.size() > 1000) { // Prevent memory issues
                String oldFile = fileCleanupQueue.poll();
                log.warn("Cleanup queue full, removing oldest entry: {}", oldFile);
            }
            log.debug("Added file to cleanup retry queue: {}", fileName);
        }
    }

    // Enhanced media utility methods
    public String getMediaFilePath(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        Path filePath = Paths.get(uploadDir, fileName);
        return Files.exists(filePath) ? filePath.toString() : null;
    }

    public boolean isImageFile(String fileName) {
        if (fileName == null) return false;
        String extension = getFileExtension(fileName);
        return Constant.ALLOWED_IMAGE_EXTENSIONS.contains(extension);
    }

    public boolean isVideoFile(String fileName) {
        if (fileName == null) return false;
        String extension = getFileExtension(fileName);
        return Constant.ALLOWED_VIDEO_EXTENSIONS.contains(extension);
    }

    public String getMediaType(String fileName) {
        if (fileName == null) return "unknown";
        if (isImageFile(fileName)) return "image";
        if (isVideoFile(fileName)) return "video";
        return "unknown";
    }

    public Map<String, Object> getMediaConstraints() {
        Map<String, Object> constraints = new HashMap<>();

        // Image constraints
        Map<String, Object> imageConstraints = new HashMap<>();
        imageConstraints.put("maxSize", maxImageSize);
        imageConstraints.put("maxSizeMB", Math.round(maxImageSize / 1024.0 / 1024.0 * 100.0) / 100.0);
        imageConstraints.put("allowedFormats", new ArrayList<>(Constant.ALLOWED_IMAGE_EXTENSIONS));
        imageConstraints.put("maxWidth", Constant.MAX_IMAGE_WIDTH);
        imageConstraints.put("maxHeight", Constant.MAX_IMAGE_HEIGHT);
        imageConstraints.put("minDimension", Constant.MIN_IMAGE_DIMENSION);

        // Video constraints
        Map<String, Object> videoConstraints = new HashMap<>();
        videoConstraints.put("maxSize", maxVideoSize);
        videoConstraints.put("maxSizeMB", Math.round(maxVideoSize / 1024.0 / 1024.0 * 100.0) / 100.0);
        videoConstraints.put("allowedFormats", new ArrayList<>(Constant.ALLOWED_VIDEO_EXTENSIONS));
        videoConstraints.put("maxDurationSeconds", Constant.MAX_VIDEO_DURATION_SECONDS);
        videoConstraints.put("minResolution", Constant.MIN_VIDEO_RESOLUTION);
        videoConstraints.put("maxResolution", Constant.MAX_VIDEO_RESOLUTION);
        videoConstraints.put("maxFrameRate", Constant.MAX_VIDEO_FRAME_RATE);
        videoConstraints.put("maxBitrate", Constant.MAX_VIDEO_BITRATE);

        // General constraints
        Map<String, Object> generalConstraints = new HashMap<>();
        generalConstraints.put("maxContentLength", Constant.MAX_POST_CONTENT_LENGTH);
        generalConstraints.put("maxLocationLength", Constant.MAX_LOCATION_LENGTH);

        constraints.put("image", imageConstraints);
        constraints.put("video", videoConstraints);
        constraints.put("general", generalConstraints);

        return constraints;
    }

    // Cleanup utility method
    public void processFileCleanupQueue() {
        int processedCount = 0;
        int maxRetries = 10;

        while (!fileCleanupQueue.isEmpty() && processedCount < maxRetries) {
            String fileName = fileCleanupQueue.poll();
            if (fileName != null) {
                try {
                    Path filePath = Paths.get(uploadDir, fileName);
                    if (Files.exists(filePath)) {
                        Files.delete(filePath);
                        log.info("Successfully cleaned up file from retry queue: {}", fileName);
                    }
                    processedCount++;
                } catch (Exception e) {
                    log.warn("Failed to cleanup file from retry queue: {}", fileName, e);
                    // Don't re-add to queue to prevent infinite loops
                }
            }
        }

        if (processedCount > 0) {
            log.info("Processed {} files from cleanup queue, {} remaining",
                    processedCount, fileCleanupQueue.size());
        }
    }
}