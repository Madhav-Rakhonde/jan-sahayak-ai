package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.PostCreateDto;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.service.PostService;
import com.JanSahayak.AI.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;
    private final UserService userService;

    @GetMapping("/media-constraints")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMediaConstraints() {
        try {
            log.debug("Retrieving media upload constraints");
            Map<String, Object> constraints = postService.getMediaConstraints();

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("constraints", constraints);
            responseData.put("description", "Media upload specifications");

            return ResponseEntity.ok(
                    ApiResponse.success("Media constraints retrieved successfully", responseData)
            );
        } catch (Exception e) {
            log.error("Error retrieving media constraints: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to retrieve media constraints", "An unexpected error occurred")
            );
        }
    }

    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPost(
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "media", required = false) MultipartFile mediaFile,
            @Valid @RequestBody(required = false) PostCreateDto postDto,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            log.debug("Creating post for user: {}", userDetails.getUsername());
            User user = userService.findByUsername(userDetails.getUsername());

            // Handle both JSON and form data inputs
            PostCreateDto finalPostDto;
            if (postDto != null) {
                // JSON request body provided
                finalPostDto = postDto;
            } else {
                // Form data provided
                if (content == null || location == null) {
                    throw new IllegalArgumentException("Content and location are required");
                }
                finalPostDto = new PostCreateDto();
                finalPostDto.setContent(content);
                finalPostDto.setLocation(location);
            }

            // Create post with or without media
            Post createdPost = postService.createPost(finalPostDto, user, mediaFile);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("post", createdPost);

            // Add media info if present
            if (createdPost.hasImage()) {
                Map<String, Object> mediaInfo = new HashMap<>();
                mediaInfo.put("fileName", createdPost.getImageName());
                mediaInfo.put("mediaType", postService.getMediaType(createdPost.getImageName()));
                mediaInfo.put("isImage", postService.isImageFile(createdPost.getImageName()));
                mediaInfo.put("isVideo", postService.isVideoFile(createdPost.getImageName()));
                mediaInfo.put("mediaUrl", "/api/posts/" + createdPost.getId() + "/media");
                responseData.put("mediaInfo", mediaInfo);
            }

            String successMessage = createdPost.hasImage() ?
                    "Post with media created successfully" : "Post created successfully";

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.success(successMessage, responseData)
            );
        } catch (IllegalArgumentException e) {
            log.error("Invalid post data for user {}: {}", userDetails.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.error("Invalid post data", e.getMessage())
            );
        } catch (RuntimeException e) {
            log.error("Error creating post for user {}: {}", userDetails.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.error("Failed to create post", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error creating post for user {}: {}", userDetails.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to create post", "An unexpected error occurred")
            );
        }
    }

    @PutMapping("/{postId}/media")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePostMedia(
            @PathVariable Long postId,
            @RequestParam("media") MultipartFile mediaFile,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            log.debug("Updating media for post {} by user {}", postId, userDetails.getUsername());
            User user = userService.findByUsername(userDetails.getUsername());
            Post updatedPost = postService.updatePostMedia(postId, mediaFile, user);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("post", updatedPost);
            responseData.put("mediaUrl", "/api/posts/" + postId + "/media");

            return ResponseEntity.ok(
                    ApiResponse.success("Post media updated successfully", responseData)
            );
        } catch (SecurityException e) {
            log.warn("Unauthorized attempt to update media for post {} by user {}: {}",
                    postId, userDetails.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.error("Access denied", e.getMessage())
            );
        } catch (IllegalArgumentException e) {
            log.error("Invalid media file for post {}: {}", postId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.error("Invalid media file", e.getMessage())
            );
        } catch (RuntimeException e) {
            log.error("Error updating media for post {}: {}", postId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.error("Failed to update post media", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error updating media for post {}: {}", postId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to update post media", "An unexpected error occurred")
            );
        }
    }

    @DeleteMapping("/{postId}/media")
    public ResponseEntity<ApiResponse<Post>> removePostMedia(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            log.debug("Removing media from post {} by user {}", postId, userDetails.getUsername());
            User user = userService.findByUsername(userDetails.getUsername());
            Post updatedPost = postService.removePostMedia(postId, user);

            return ResponseEntity.ok(
                    ApiResponse.success("Post media removed successfully", updatedPost)
            );
        } catch (SecurityException e) {
            log.warn("Unauthorized attempt to remove media from post {} by user {}: {}",
                    postId, userDetails.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.error("Access denied", e.getMessage())
            );
        } catch (RuntimeException e) {
            log.error("Error removing media from post {}: {}", postId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.error("Failed to remove post media", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error removing media from post {}: {}", postId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to remove post media", "An unexpected error occurred")
            );
        }
    }

    @GetMapping("/{postId}/media")
    public ResponseEntity<?> getPostMedia(@PathVariable Long postId) {
        try {
            log.debug("Retrieving media for post: {}", postId);
            Post post = postService.findById(postId);

            if (!post.hasImage()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        ApiResponse.error("Media not found", "This post does not have any media attached")
                );
            }

            String filePath = postService.getMediaFilePath(post.getImageName());
            if (filePath == null) {
                log.warn("Media file path not found for post {}", postId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        ApiResponse.error("Media file not found", "The media file has been deleted or moved")
                );
            }

            File file = new File(filePath);
            if (!file.exists()) {
                log.warn("Media file not found for post {}: {}", postId, filePath);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        ApiResponse.error("Media file not found", "The media file has been deleted or moved")
                );
            }

            Resource resource = new FileSystemResource(file);

            // Determine content type
            String contentType = determineContentType(post.getImageName());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + post.getImageName() + "\"")
                    .body(resource);

        } catch (RuntimeException e) {
            log.error("Error retrieving media for post {}: {}", postId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("Post not found", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error retrieving media for post {}: {}", postId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to retrieve media", "An unexpected error occurred")
            );
        }
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPost(@PathVariable Long postId) {
        try {
            log.debug("Retrieving post: {}", postId);
            Post post = postService.findById(postId);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("post", post);

            // Add media info if present
            if (post.hasImage()) {
                Map<String, Object> mediaInfo = new HashMap<>();
                mediaInfo.put("fileName", post.getImageName());
                mediaInfo.put("mediaType", postService.getMediaType(post.getImageName()));
                mediaInfo.put("isImage", postService.isImageFile(post.getImageName()));
                mediaInfo.put("isVideo", postService.isVideoFile(post.getImageName()));
                mediaInfo.put("mediaUrl", "/api/posts/" + postId + "/media");
                responseData.put("mediaInfo", mediaInfo);
            }

            return ResponseEntity.ok(
                    ApiResponse.success("Post retrieved successfully", responseData)
            );
        } catch (RuntimeException e) {
            log.error("Error retrieving post {}: {}", postId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("Post not found", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error retrieving post {}: {}", postId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to retrieve post", "An unexpected error occurred")
            );
        }
    }

    private String determineContentType(String fileName) {
        String lowerFileName = fileName.toLowerCase();

        if (postService.isImageFile(fileName)) {
            if (lowerFileName.endsWith(".png")) {
                return "image/png";
            } else if (lowerFileName.endsWith(".webp")) {
                return "image/webp";
            } else {
                return "image/jpeg"; // Default for jpg/jpeg
            }
        } else if (postService.isVideoFile(fileName)) {
            if (lowerFileName.endsWith(".mp4")) {
                return "video/mp4";
            } else if (lowerFileName.endsWith(".mov")) {
                return "video/quicktime";
            }
        }

        return "application/octet-stream";
    }
}