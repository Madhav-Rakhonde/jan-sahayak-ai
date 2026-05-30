package com.JanSahayak.AI.DTO;

import com.JanSahayak.AI.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserMeResponse {
    private Long id;
    private String username;
    private String actualUsername;
    private String email;
    private String contactNumber;
    private String profileImage;
    private String bio;
    private String website;
    private String address;
    private String pincode;
    private Boolean hasInvalidPincode;
    private String preferredLanguage;
    private Boolean autoTranslate;
    private String profanityFilterLevel;
    private String mutedWords;
    private Boolean isEmailVerified;
    private Boolean isActive;
    private Date createdAt;
    private Date updatedAt;
    private List<String> authorities;
    
    public UserMeResponse(User user) {
        this.id = user.getId();
        this.username = user.getUsername(); // returns email due to UserDetails
        this.actualUsername = user.getActualUsername(); // true username
        this.email = user.getEmail();
        this.contactNumber = user.getContactNumber();
        this.profileImage = user.getProfileImage();
        this.bio = user.getBio();
        this.website = user.getWebsite();
        this.address = user.getAddress();
        this.pincode = user.getPincode();
        this.hasInvalidPincode = user.getHasInvalidPincode();
        this.preferredLanguage = user.getPreferredLanguage();
        this.autoTranslate = user.getAutoTranslate();
        this.profanityFilterLevel = user.getProfanityFilterLevel();
        this.mutedWords = user.getMutedWords();
        this.isEmailVerified = user.getIsEmailVerified();
        this.isActive = user.getIsActive();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
        
        if (user.getAuthorities() != null) {
            this.authorities = user.getAuthorities().stream()
                    .map(auth -> auth.getAuthority())
                    .collect(Collectors.toList());
        }
    }
}
