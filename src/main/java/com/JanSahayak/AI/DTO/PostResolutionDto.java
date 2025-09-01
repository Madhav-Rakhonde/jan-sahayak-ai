package com.JanSahayak.AI.DTO;

import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResolutionDto {

    @Size(max = 500, message = "Resolution message cannot exceed 500 characters")
    private String resolutionMessage;


}