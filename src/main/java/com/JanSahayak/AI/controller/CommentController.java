package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.CommentUpdateDto;
import com.JanSahayak.AI.DTO.CommentResponse;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.Comment;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.security.UserPrincipal;
import com.JanSahayak.AI.service.CommentService;
import com.JanSahayak.AI.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentService commentService;
    private final UserService userService;

    @GetMapping("/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> getComment(@PathVariable Long commentId) {
        try {
            log.debug("Fetching comment with ID: {}", commentId);
            Comment comment = commentService.findById(commentId);
            CommentResponse response = convertToCommentResponse(comment);

            return ResponseEntity.ok(
                    ApiResponse.success("Comment retrieved successfully", response)
            );
        } catch (RuntimeException e) {
            log.error("Error fetching comment {}: {}", commentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("Comment not found", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error fetching comment {}: {}", commentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to retrieve comment", "An unexpected error occurred")
            );
        }
    }

    @GetMapping("/{commentId}/replies")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getCommentReplies(@PathVariable Long commentId) {
        try {
            log.debug("Fetching replies for comment ID: {}", commentId);
            List<Comment> replies = commentService.getCommentReplies(commentId);
            List<CommentResponse> response = replies.stream()
                    .map(this::convertToCommentResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    ApiResponse.success(
                            String.format("Found %d replies for comment", replies.size()),
                            response
                    )
            );
        } catch (RuntimeException e) {
            log.error("Error fetching replies for comment {}: {}", commentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("Comment not found", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error fetching replies for comment {}: {}", commentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to retrieve comment replies", "An unexpected error occurred")
            );
        }
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateDto commentDto,
            @CurrentUser UserPrincipal currentUser) {

        try {
            log.debug("Updating comment {} by user {}", commentId, currentUser.getId());
            User user = userService.getUserById(currentUser.getId());
            Comment updatedComment = commentService.updateComment(commentId, commentDto, user);

            return ResponseEntity.ok(
                    ApiResponse.success("Comment updated successfully", convertToCommentResponse(updatedComment))
            );
        } catch (SecurityException e) {
            log.warn("Unauthorized attempt to update comment {} by user {}: {}",
                    commentId, currentUser.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.error("Access denied", e.getMessage())
            );
        } catch (RuntimeException e) {
            log.error("Error updating comment {} by user {}: {}",
                    commentId, currentUser.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.error("Failed to update comment", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error updating comment {} by user {}: {}",
                    commentId, currentUser.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to update comment", "An unexpected error occurred")
            );
        }
    }


    private CommentResponse convertToCommentResponse(Comment comment) {
        try {
            // Validate comment and its associations
            if (comment == null) {
                throw new IllegalArgumentException("Comment cannot be null");
            }
            if (comment.getUser() == null) {
                throw new IllegalArgumentException("Comment must have an associated user");
            }
            if (comment.getPost() == null) {
                throw new IllegalArgumentException("Comment must have an associated post");
            }

            return CommentResponse.builder()
                    .id(comment.getId())
                    .text(comment.getText())
                    .createdAt(comment.getCreatedAt())
                    .postId(comment.getPost().getId())
                    .userId(comment.getUser().getId())
                    .username(comment.getUser().getUsername())
                    .parentCommentId(comment.getParentComment() != null ? comment.getParentComment().getId() : null)
                    .replyCount(comment.getReplyCount())
                    .replies(null) // Can be populated if nested structure is needed
                    .build();
        } catch (Exception e) {
            log.error("Error converting comment to response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert comment data", e);
        }
    }
}