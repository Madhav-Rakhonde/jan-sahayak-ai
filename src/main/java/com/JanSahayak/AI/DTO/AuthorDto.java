package com.JanSahayak.AI.DTO;

import com.JanSahayak.AI.model.User;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthorDto implements Serializable {
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
    private String roleName;
    private Date createdAt;

    /**
     * Convert User entity to AuthorDto
     */
    public static AuthorDto fromUser(User user) {
        if (user == null) {
            return null;
        }

        AuthorDto dto = new AuthorDto();
        dto.setId(user.getId());
        dto.setUsername(user.getActualUsername());
        dto.setEmail(user.getEmail());
        dto.setContactNumber(user.getContactNumber());
        dto.setProfileImage(user.getProfileImage());
        dto.setBio(user.getBio());
        dto.setWebsite(user.getWebsite());
        dto.setAddress(user.getAddress());
        dto.setPincode(user.getPincode());
        dto.setIsActive(user.getIsActive());
        dto.setCreatedAt(user.getCreatedAt());

        // Safely extract role name
        if (user.getRole() != null && user.getRole().getName() != null) {
            dto.setRoleName(user.getRole().getName());
        }

        return dto;
    }

    /**
     * Get profile picture URL with fallback
     */
    public String getProfilePictureUrl() {
        return profileImage;
    }

    /**
     * Check if user has profile image
     */
    public boolean hasProfileImage() {
        return profileImage != null && !profileImage.trim().isEmpty();
    }

    /**
     * Get display name for UI
     */
    public String getDisplayName() {
        return username;
    }

    /**
     * Check if user is admin
     */
    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(roleName);
    }

    /**
     * Check if user is department
     */
    public boolean isDepartment() {
        return "ROLE_DEPARTMENT".equals(roleName);
    }

    /**
     * Check if user is normal user
     */
    public boolean isNormalUser() {
        return "ROLE_USER".equals(roleName);
    }

    /**
     * Check if user has valid pincode
     */
    public boolean hasPincode() {
        return pincode != null && pincode.matches("\\d{6}");
    }

    /**
     * Get state prefix from pincode (first 2 digits)
     */
    public String getStatePrefix() {
        return hasPincode() ? pincode.substring(0, 2) : null;
    }

    /**
     * Get district prefix from pincode (first 3 digits)
     */
    public String getDistrictPrefix() {
        return hasPincode() ? pincode.substring(0, 3) : null;
    }

    /**
     * Check if user is in the same state as given pincode
     */
    public boolean isInSameState(String otherPincode) {
        if (!hasPincode() || otherPincode == null || otherPincode.length() < 2) {
            return false;
        }
        return getStatePrefix().equals(otherPincode.substring(0, 2));
    }

    /**
     * Check if user is in the same district as given pincode
     */
    public boolean isInSameDistrict(String otherPincode) {
        if (!hasPincode() || otherPincode == null || otherPincode.length() < 3) {
            return false;
        }
        return getDistrictPrefix().equals(otherPincode.substring(0, 3));
    }
}