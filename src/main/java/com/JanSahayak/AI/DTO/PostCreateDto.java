package com.JanSahayak.AI.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostCreateDto {
    @NotBlank(message = "Post content is required")
    @Size(max = 500, message = "Content must not exceed 500 characters")
    private String content;

    private MultipartFile image; // For imageName in Post entity

    @NotBlank(message = "Location is required")
    private String location; // Format: "City StateCode" (e.g., "Pune MH")

    // Optional: Support for tagging users at creation
    private List<String> taggedUsernames; // List of @usernames to tag
}
