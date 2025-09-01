package com.JanSahayak.AI.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PostContentUpdateDto {
    @NotBlank(message = "Post content is required")
    @Size(max = 500, message = "Content must not exceed 500 characters")
    private String content;

    // Optional: Include location update capability
    private String location; // Format: "City StateCode" (e.g., "Pune MH")
}
