package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import java.sql.Timestamp;


import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {
    // ===== Basic User Queries =====
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    // ===== MISSING METHODS USED IN PostSearchService =====

    /**
     * Find users by exact pincode and active status
     * Used in PostSearchService.searchByPincode()
     */
    List<User> findByPincodeAndIsActiveTrue(String pincode);

    /**
     * Find users by pincode prefix and active status
     * Used in PostSearchService for state/district prefix searches
     */
    List<User> findByPincodeStartingWithAndIsActiveTrue(String pincodePrefix);

    // ===== User Tagging Queries =====
    @Query("SELECT u FROM User u WHERE u.username IN :usernames AND u.isActive = true")
    List<User> findByUsernameInAndIsActiveTrue(@Param("usernames") List<String> usernames);

    @Query("SELECT COUNT(u) FROM User u JOIN u.role r WHERE r.name = :roleName AND u.isActive = true")
    Long countByRoleName(@Param("roleName") String roleName);

    List<User> findByUsernameIn(List<String> usernames);

    /**
     * Find users by role name and exact pincode
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.pincode = :pincode AND u.isActive = true")
    List<User> findByRoleNameAndPincodeAndIsActiveTrue(@Param("roleName") String roleName,
                                                       @Param("pincode") String pincode);

    /**
     * Find users by role name and pincode starting with prefix (SQL compatible)
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.pincode LIKE CONCAT(:pincodePrefix, '%') AND u.isActive = true")
    List<User> findByRoleNameAndPincodeStartingWithAndIsActiveTrue(@Param("roleName") String roleName,
                                                                   @Param("pincodePrefix") String pincodePrefix);

    /**
     * Find users with valid 6-digit pincodes (SQL compatible)
     */
    @Query("SELECT u FROM User u WHERE u.pincode IS NOT NULL AND u.isActive = true " +
            "AND LENGTH(u.pincode) = 6 " +
            "AND u.pincode NOT LIKE '%[^0-9]%'")
    List<User> findByPincodeIsNotNullAndIsActiveTrueAndPincodeMatches(@Param("pattern") String pattern);

    /**
     * Alternative method for finding users with valid numeric pincodes
     */
    @Query("SELECT u FROM User u WHERE u.pincode IS NOT NULL AND u.isActive = true " +
            "AND LENGTH(u.pincode) = 6 " +
            "AND CAST(u.pincode AS INTEGER) > 0")
    List<User> findByPincodeIsNotNullAndIsActiveTrueAndPincodeMatchesDigits();

    /**
     * Find department users by state - using subquery with PincodeLookup
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND u.isActive = true " +
            "AND u.pincode IN (SELECT p.pincode FROM PincodeLookup p WHERE p.state = :state)")
    List<User> findDepartmentUsersByState(@Param("state") String state);

    /**
     * Find department users by district
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND u.isActive = true " +
            "AND u.pincode IN (SELECT p.pincode FROM PincodeLookup p WHERE p.state = :state AND p.district = :district)")
    List<User> findDepartmentUsersByDistrict(@Param("state") String state, @Param("district") String district);

    /**
     * Find users by role name
     */
    @Query("SELECT u FROM User u JOIN u.role r WHERE r.name = :roleName AND u.isActive = true")
    List<User> findByRoleNameAndIsActiveTrue(@Param("roleName") String roleName);

    /**
     * Enhanced search for user tagging with better ordering
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY " +
            "CASE WHEN LOWER(u.username) = LOWER(:query) THEN 0 " +
            "     WHEN LOWER(u.username) LIKE LOWER(CONCAT(:query, '%')) THEN 1 " +
            "     ELSE 2 END, u.username ASC")
    List<User> searchUsersForTagging(@Param("query") String query, Pageable pageable);

    /**
     * Find users who are most active in posting
     */
    @Query("SELECT u FROM User u LEFT JOIN u.posts p " +
            "WHERE u.isActive = true " +
            "GROUP BY u.id " +
            "ORDER BY COUNT(p) DESC, u.username ASC")
    List<User> findMostActiveUsers(Pageable pageable);

    /**
     * Find users who have been tagged most frequently
     */
    @Query("SELECT u FROM User u " +
            "LEFT JOIN u.receivedTags ut ON ut.isActive = true " +
            "WHERE u.isActive = true " +
            "GROUP BY u.id " +
            "ORDER BY COUNT(ut) DESC, u.username ASC")
    List<User> findMostTaggedUsers(Pageable pageable);

    /**
     * Find all department users
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND u.isActive = true " +
            "ORDER BY u.pincode ASC, u.username ASC")
    List<User> findAllDepartmentUsers();

    /**
     * Find users by pincode proximity (SQL compatible)
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.pincode IN (SELECT p.pincode FROM PincodeLookup p " +
            "WHERE p.pincode = :pincode OR p.district = (SELECT p2.district FROM PincodeLookup p2 WHERE p2.pincode = :pincode)) " +
            "AND u.id != :excludeUserId " +
            "ORDER BY CASE WHEN u.pincode = :pincode THEN 0 ELSE 1 END, u.username ASC")
    List<User> findUsersByPincodeProximity(@Param("pincode") String pincode,
                                           @Param("excludeUserId") Long excludeUserId,
                                           Pageable pageable);

    /**
     * Find users who can resolve posts in specific pincode
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE (r.name = 'ROLE_DEPARTMENT' OR r.name = 'ROLE_ADMIN') " +
            "AND u.isActive = true " +
            "AND (u.pincode = :pincode OR " +
            "     u.pincode IN (SELECT p.pincode FROM PincodeLookup p " +
            "                   WHERE p.district = (SELECT p2.district FROM PincodeLookup p2 WHERE p2.pincode = :pincode))) " +
            "ORDER BY CASE WHEN r.name = 'ROLE_DEPARTMENT' THEN 0 ELSE 1 END, " +
            "         CASE WHEN u.pincode = :pincode THEN 0 ELSE 1 END, u.username ASC")
    List<User> findResolversForPincode(@Param("pincode") String pincode);

    /**
     * Find users who can resolve posts in specific state
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE (r.name = 'ROLE_DEPARTMENT' OR r.name = 'ROLE_ADMIN') " +
            "AND u.isActive = true " +
            "AND u.pincode IN (SELECT p.pincode FROM PincodeLookup p WHERE p.state = :state) " +
            "ORDER BY u.pincode ASC, CASE WHEN r.name = 'ROLE_DEPARTMENT' THEN 0 ELSE 1 END, u.username ASC")
    List<User> findResolversForState(@Param("state") String state);

    /**
     * Get user statistics by role
     */
    @Query("SELECT r.name, COUNT(u) FROM User u JOIN u.role r " +
            "WHERE u.isActive = true GROUP BY r.name")
    List<Object[]> getUserCountByRole();

    /**
     * Get user distribution by pincode
     */
    @Query("SELECT u.pincode, COUNT(u) FROM User u " +
            "WHERE u.isActive = true AND u.pincode IS NOT NULL " +
            "GROUP BY u.pincode ORDER BY COUNT(u) DESC")
    List<Object[]> getUserDistributionByPincode();

    /**
     * Get user distribution by state
     */
    @Query("SELECT p.state, COUNT(u) FROM User u JOIN PincodeLookup p ON u.pincode = p.pincode " +
            "WHERE u.isActive = true GROUP BY p.state ORDER BY COUNT(u) DESC")
    List<Object[]> getUserDistributionByState();

    /**
     * Find admin users
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_ADMIN' AND u.isActive = true " +
            "ORDER BY u.username ASC")
    List<User> findAdminUsers();

    /**
     * Find normal users by pincode
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_USER' AND u.pincode = :pincode AND u.isActive = true " +
            "ORDER BY u.username ASC")
    List<User> findNormalUsersByPincode(@Param("pincode") String pincode);

    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND (u.bio IS NULL OR u.pincode IS NULL)")
    List<User> findUsersWithIncompleteProfiles();

    @Query("SELECT u FROM User u LEFT JOIN u.posts p " +
            "WHERE u.isActive = true " +
            "AND (p.createdAt IS NULL OR p.createdAt < :cutoffDate) " +
            "GROUP BY u.id " +
            "ORDER BY u.username ASC")
    List<User> findInactivePosters(@Param("cutoffDate") java.sql.Timestamp cutoffDate, Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE u.isActive = true " +
            "AND (:roleName IS NULL OR r.name = :roleName) " +
            "AND (:pincode IS NULL OR u.pincode = :pincode) " +
            "AND (:query IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY u.username ASC")
    List<User> searchUsers(@Param("roleName") String roleName,
                           @Param("pincode") String pincode,
                           @Param("query") String query,
                           Pageable pageable);

    /**
     * Count active users
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    Long countActiveUsers();

    /**
     * Find users with high engagement (frequently tagged and responsive)
     */
    @Query("SELECT DISTINCT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND u.isActive = true " +
            "AND u.id IN (SELECT DISTINCT ut.taggedUser.id FROM UserTag ut " +
            "              JOIN ut.post p WHERE ut.isActive = true AND p.status = 'RESOLVED') " +
            "ORDER BY u.username ASC")
    List<User> findHighEngagementDepartmentUsers();

    /**
     * Find users who have never been tagged
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND u.isActive = true " +
            "AND u.id NOT IN (SELECT DISTINCT ut.taggedUser.id FROM UserTag ut WHERE ut.isActive = true) " +
            "ORDER BY u.createdAt DESC")
    List<User> findNeverTaggedDepartmentUsers();

    /**
     * Find users by state prefix (first 2 digits of pincode)
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.pincode IS NOT NULL " +
            "AND LENGTH(u.pincode) = 6 " +
            "AND SUBSTRING(u.pincode, 1, 2) = :statePrefix")
    List<User> findByStatePrefixAndIsActiveTrue(@Param("statePrefix") String statePrefix);

    /**
     * Find users by district prefix (first 3 digits of pincode)
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.pincode IS NOT NULL " +
            "AND LENGTH(u.pincode) = 6 " +
            "AND SUBSTRING(u.pincode, 1, 3) = :districtPrefix")
    List<User> findByDistrictPrefixAndIsActiveTrue(@Param("districtPrefix") String districtPrefix);

    // Cursor-based pagination for department users
    List<User> findByRoleNameAndPincodeAndIsActiveTrueAndIdLessThanOrderByIdDesc(String role, String pincode, Long beforeId, Pageable pageable);
    List<User> findByRoleNameAndPincodeAndIsActiveTrueOrderByIdDesc(String role, String pincode, Pageable pageable);

    // Cursor-based pagination for state/district users
    List<User> findByRoleNameAndPincodeStartingWithAndIsActiveTrueAndIdLessThanOrderByIdDesc(String role, String prefix, Long beforeId, Pageable pageable);
    List<User> findByRoleNameAndPincodeStartingWithAndIsActiveTrueOrderByIdDesc(String role, String prefix, Pageable pageable);

    // Cursor-based pagination for user search
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND u.id < :beforeId " +
            "ORDER BY " +
            "CASE WHEN LOWER(u.username) = LOWER(:query) THEN 0 " +
            "     WHEN LOWER(u.username) LIKE LOWER(CONCAT(:query, '%')) THEN 1 " +
            "     ELSE 2 END, u.id DESC")
    List<User> searchUsersForTaggingWithCursor(@Param("query") String query, @Param("beforeId") Long beforeId, Pageable pageable);

    // General user listing with pagination
    List<User> findByIsActiveTrueAndIdLessThanOrderByIdDesc(Long beforeId, Pageable pageable);
    List<User> findByIsActiveTrueOrderByIdDesc(Pageable pageable);
    List<User> findByRoleNameAndIdLessThanOrderByIdDesc(String roleName, Long beforeId, Pageable pageable);
    List<User> findByRoleNameOrderByIdDesc(String roleName, Pageable pageable);

    // Recent activity with pagination - using createdAt instead of lastActive
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.createdAt > :threshold " +
            "AND u.id < :beforeId " +
            "ORDER BY u.createdAt DESC, u.id DESC")
    List<User> findByIsActiveTrueAndCreatedAtAfterAndIdLessThanOrderByCreatedAtDesc(@Param("threshold") Timestamp threshold, @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.createdAt > :threshold " +
            "ORDER BY u.createdAt DESC, u.id DESC")
    List<User> findByIsActiveTrueAndCreatedAtAfterOrderByCreatedAtDesc(@Param("threshold") Timestamp threshold, Pageable pageable);

    // Add this method to your UserRepo.java
    @Query("SELECT u FROM User u WHERE u.role.name = :roleName AND u.isActive = true ORDER BY u.id DESC")
    List<User> findByRoleNameAndIsActiveTrueOrderByIdDesc(@Param("roleName") String roleName);
}