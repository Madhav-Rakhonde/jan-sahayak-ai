package com.JanSahayak.AI.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CommentCreateDto {
    @NotBlank(message = "Comment text is required")
    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    private String text;

    private Long postId;
    private Long parentCommentId;
}