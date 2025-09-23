package com.JanSahayak.AI.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String contactNumber;
    private String profileImage;
    private String bio;
    private String website;
    private String address;
    private String pincode;
    private Boolean isActive;
    private Date createdAt;
    private Date updatedAt;
    private String role;
}