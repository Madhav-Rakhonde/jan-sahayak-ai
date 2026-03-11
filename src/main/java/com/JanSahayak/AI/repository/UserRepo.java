package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {

    // =========================================================================
    // BASIC USER QUERIES
    // =========================================================================

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    /** Find active user by username. */
    Optional<User> findByUsernameAndIsActiveTrue(String username);

    // =========================================================================
    // PINCODE — EXACT & PREFIX
    // =========================================================================

    /** Find users by exact pincode and active status. Used in PostSearchService. */
    List<User> findByPincodeAndIsActiveTrue(String pincode);

    /** Find users by pincode prefix and active status. Used in PostSearchService. */
    List<User> findByPincodeStartingWithAndIsActiveTrue(String pincodePrefix);

    /**
     * Count active users in a pincode.
     * Used by HyperlocalSeedService as a threshold check before seeding a ward community.
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.pincode = :pincode AND u.isActive = true")
    long countActiveUsersByPincode(@Param("pincode") String pincode);

    /**
     * Fetch active users in a pincode in batches (for bulk enrollment).
     * HyperlocalSeedService processes in batches of 100 using page index.
     */
    @Query("SELECT u FROM User u WHERE u.pincode = :pincode AND u.isActive = true ORDER BY u.id ASC")
    List<User> findActiveUsersByPincode(@Param("pincode") String pincode, Pageable pageable);

    /**
     * Find users by state prefix (first 2 digits of pincode).
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.pincode IS NOT NULL " +
            "AND LENGTH(u.pincode) = 6 " +
            "AND SUBSTRING(u.pincode, 1, 2) = :statePrefix")
    List<User> findByStatePrefixAndIsActiveTrue(@Param("statePrefix") String statePrefix);

    /**
     * Find users by district prefix (first 3 digits of pincode).
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.pincode IS NOT NULL " +
            "AND LENGTH(u.pincode) = 6 " +
            "AND SUBSTRING(u.pincode, 1, 3) = :districtPrefix")
    List<User> findByDistrictPrefixAndIsActiveTrue(@Param("districtPrefix") String districtPrefix);

    // =========================================================================
    // ROLE-BASED QUERIES
    // =========================================================================



    @Query("SELECT u FROM User u JOIN u.role r WHERE r.name = :roleName AND u.isActive = true")
    List<User> findByRoleNameAndIsActiveTrue(@Param("roleName") String roleName);

    @Query("SELECT u FROM User u WHERE u.role.name = :roleName AND u.isActive = true ORDER BY u.id DESC")
    List<User> findByRoleNameAndIsActiveTrueOrderByIdDesc(@Param("roleName") String roleName);

    @Query("SELECT COUNT(u) FROM User u JOIN u.role r WHERE r.name = :roleName AND u.isActive = true")
    Long countByRoleName(@Param("roleName") String roleName);

    /** Find all department users ordered by pincode then username. */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND u.isActive = true " +
            "ORDER BY u.pincode ASC, u.username ASC")
    List<User> findAllDepartmentUsers();

    /** Find admin users. */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_ADMIN' AND u.isActive = true " +
            "ORDER BY u.username ASC")
    List<User> findAdminUsers();

    /** Find department users by state. */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND u.isActive = true " +
            "AND u.pincode IN (SELECT p.pincode FROM PincodeLookup p WHERE p.state = :state)")
    List<User> findDepartmentUsersByState(@Param("state") String state);

    /** Find department users by district. */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND u.isActive = true " +
            "AND u.pincode IN (SELECT p.pincode FROM PincodeLookup p WHERE p.state = :state AND p.district = :district)")
    List<User> findDepartmentUsersByDistrict(@Param("state") String state, @Param("district") String district);

    /** Find department users with high engagement (tagged on resolved posts). */
    @Query("SELECT DISTINCT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND u.isActive = true " +
            "AND u.id IN (SELECT DISTINCT ut.taggedUser.id FROM UserTag ut " +
            "              JOIN ut.post p WHERE ut.isActive = true AND p.status = 'RESOLVED') " +
            "ORDER BY u.username ASC")
    List<User> findHighEngagementDepartmentUsers();

    /** Find department users who have never been tagged. */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND u.isActive = true " +
            "AND u.id NOT IN (SELECT DISTINCT ut.taggedUser.id FROM UserTag ut WHERE ut.isActive = true) " +
            "ORDER BY u.createdAt DESC")
    List<User> findNeverTaggedDepartmentUsers();

    /** Find normal (ROLE_USER) users by exact pincode. */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_USER' AND u.pincode = :pincode AND u.isActive = true " +
            "ORDER BY u.username ASC")
    List<User> findNormalUsersByPincode(@Param("pincode") String pincode);

    /** Find users by role name and exact pincode. */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.pincode = :pincode AND u.isActive = true")
    List<User> findByRoleNameAndPincodeAndIsActiveTrue(@Param("roleName") String roleName,
                                                       @Param("pincode") String pincode);

    /** Find users by role name and pincode prefix. */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.pincode LIKE CONCAT(:pincodePrefix, '%') AND u.isActive = true")
    List<User> findByRoleNameAndPincodeStartingWithAndIsActiveTrue(@Param("roleName") String roleName,
                                                                   @Param("pincodePrefix") String pincodePrefix);

    // =========================================================================
    // USER TAGGING
    // =========================================================================

    @Query("SELECT u FROM User u WHERE u.username IN :usernames AND u.isActive = true")
    List<User> findByUsernameInAndIsActiveTrue(@Param("usernames") List<String> usernames);

    List<User> findByUsernameIn(List<String> usernames);

    @Query("""
            SELECT u FROM User u
            WHERE u.isActive = true
              AND u.role.name = 'ROLE_DEPARTMENT'
              AND (
                    LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
                  )
              AND u.id < :beforeId
            ORDER BY
                CASE
                    WHEN LOWER(u.username) = LOWER(:query) THEN 0
                    WHEN LOWER(u.username) LIKE LOWER(CONCAT(:query, '%')) THEN 1
                    ELSE 2
                END,
                u.id DESC
            """)
    List<User> searchUsersForTaggingWithCursor(@Param("query") String query,
                                               @Param("beforeId") Long beforeId,
                                               Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.isActive = true
              AND u.role.name = 'ROLE_DEPARTMENT'
              AND (
                    LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
                  )
            ORDER BY
                CASE
                    WHEN LOWER(u.username) = LOWER(:query) THEN 0
                    WHEN LOWER(u.username) LIKE LOWER(CONCAT(:query, '%')) THEN 1
                    ELSE 2
                END,
                u.username ASC
            """)
    List<User> searchUsersForTagging(@Param("query") String query, Pageable pageable);

    // =========================================================================
    // RESOLVER QUERIES
    // =========================================================================

    /** Find department/admin users who can resolve posts in a specific pincode. */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE (r.name = 'ROLE_DEPARTMENT' OR r.name = 'ROLE_ADMIN') " +
            "AND u.isActive = true " +
            "AND (u.pincode = :pincode OR " +
            "     u.pincode IN (SELECT p.pincode FROM PincodeLookup p " +
            "                   WHERE p.district = (SELECT p2.district FROM PincodeLookup p2 WHERE p2.pincode = :pincode))) " +
            "ORDER BY CASE WHEN r.name = 'ROLE_DEPARTMENT' THEN 0 ELSE 1 END, " +
            "         CASE WHEN u.pincode = :pincode THEN 0 ELSE 1 END, u.username ASC")
    List<User> findResolversForPincode(@Param("pincode") String pincode);

    /** Find department/admin users who can resolve posts in a specific state. */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE (r.name = 'ROLE_DEPARTMENT' OR r.name = 'ROLE_ADMIN') " +
            "AND u.isActive = true " +
            "AND u.pincode IN (SELECT p.pincode FROM PincodeLookup p WHERE p.state = :state) " +
            "ORDER BY u.pincode ASC, CASE WHEN r.name = 'ROLE_DEPARTMENT' THEN 0 ELSE 1 END, u.username ASC")
    List<User> findResolversForState(@Param("state") String state);

    // =========================================================================
    // VALID PINCODE QUERIES
    // =========================================================================

    @Query("SELECT u FROM User u WHERE u.pincode IS NOT NULL AND u.isActive = true " +
            "AND LENGTH(u.pincode) = 6 " +
            "AND u.pincode NOT LIKE '%[^0-9]%'")
    List<User> findByPincodeIsNotNullAndIsActiveTrueAndPincodeMatches(@Param("pattern") String pattern);

    @Query("SELECT u FROM User u WHERE u.pincode IS NOT NULL AND u.isActive = true " +
            "AND LENGTH(u.pincode) = 6 " +
            "AND CAST(u.pincode AS INTEGER) > 0")
    List<User> findByPincodeIsNotNullAndIsActiveTrueAndPincodeMatchesDigits();

    // =========================================================================
    // PROXIMITY & RECOMMENDATION
    // =========================================================================

    /** Find users near a pincode — same pincode first, then same district. */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.pincode IN (SELECT p.pincode FROM PincodeLookup p " +
            "WHERE p.pincode = :pincode OR p.district = (SELECT p2.district FROM PincodeLookup p2 WHERE p2.pincode = :pincode)) " +
            "AND u.id != :excludeUserId " +
            "ORDER BY CASE WHEN u.pincode = :pincode THEN 0 ELSE 1 END, u.username ASC")
    List<User> findUsersByPincodeProximity(@Param("pincode") String pincode,
                                           @Param("excludeUserId") Long excludeUserId,
                                           Pageable pageable);

    // =========================================================================
    // ACTIVITY & PROFILE COMPLETENESS
    // =========================================================================

    @Query("SELECT u FROM User u LEFT JOIN u.posts p " +
            "WHERE u.isActive = true " +
            "GROUP BY u.id " +
            "ORDER BY COUNT(p) DESC, u.username ASC")
    List<User> findMostActiveUsers(Pageable pageable);

    @Query("SELECT u FROM User u " +
            "LEFT JOIN u.receivedTags ut ON ut.isActive = true " +
            "WHERE u.isActive = true " +
            "GROUP BY u.id " +
            "ORDER BY COUNT(ut) DESC, u.username ASC")
    List<User> findMostTaggedUsers(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND (u.bio IS NULL OR u.pincode IS NULL)")
    List<User> findUsersWithIncompleteProfiles();

    @Query("SELECT u FROM User u LEFT JOIN u.posts p " +
            "WHERE u.isActive = true " +
            "AND (p.createdAt IS NULL OR p.createdAt < :cutoffDate) " +
            "GROUP BY u.id " +
            "ORDER BY u.username ASC")
    List<User> findInactivePosters(@Param("cutoffDate") Timestamp cutoffDate, Pageable pageable);

    // =========================================================================
    // STATISTICS & ANALYTICS
    // =========================================================================

    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    Long countActiveUsers();

    @Query("SELECT r.name, COUNT(u) FROM User u JOIN u.role r " +
            "WHERE u.isActive = true GROUP BY r.name")
    List<Object[]> getUserCountByRole();

    @Query("SELECT u.pincode, COUNT(u) FROM User u " +
            "WHERE u.isActive = true AND u.pincode IS NOT NULL " +
            "GROUP BY u.pincode ORDER BY COUNT(u) DESC")
    List<Object[]> getUserDistributionByPincode();

    @Query("SELECT p.state, COUNT(u) FROM User u JOIN PincodeLookup p ON u.pincode = p.pincode " +
            "WHERE u.isActive = true GROUP BY p.state ORDER BY COUNT(u) DESC")
    List<Object[]> getUserDistributionByState();

    // =========================================================================
    // GENERAL SEARCH
    // =========================================================================

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

    @Query("SELECT u FROM User u " +
            "WHERE u.role.name = :roleName " +
            "AND u.isActive = true " +
            "AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND (:beforeId IS NULL OR u.id < :beforeId) " +
            "ORDER BY u.id DESC")
    List<User> searchUsersByRoleAndQueryWithCursor(@Param("roleName") String roleName,
                                                   @Param("query") String query,
                                                   @Param("beforeId") Long beforeId,
                                                   Pageable pageable);

    // =========================================================================
    // CURSOR-BASED PAGINATION
    // =========================================================================

    // Department users by pincode
    List<User> findByRoleNameAndPincodeAndIsActiveTrueAndIdLessThanOrderByIdDesc(
            String role, String pincode, Long beforeId, Pageable pageable);
    List<User> findByRoleNameAndPincodeAndIsActiveTrueOrderByIdDesc(
            String role, String pincode, Pageable pageable);

    // Department users by pincode prefix (state/district scope)
    List<User> findByRoleNameAndPincodeStartingWithAndIsActiveTrueAndIdLessThanOrderByIdDesc(
            String role, String prefix, Long beforeId, Pageable pageable);
    List<User> findByRoleNameAndPincodeStartingWithAndIsActiveTrueOrderByIdDesc(
            String role, String prefix, Pageable pageable);

    // General user listing
    List<User> findByIsActiveTrueAndIdLessThanOrderByIdDesc(Long beforeId, Pageable pageable);
    List<User> findByIsActiveTrueOrderByIdDesc(Pageable pageable);
    List<User> findByRoleNameAndIdLessThanOrderByIdDesc(String roleName, Long beforeId, Pageable pageable);
    List<User> findByRoleNameOrderByIdDesc(String roleName, Pageable pageable);

    // Recent activity
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.createdAt > :threshold " +
            "AND u.id < :beforeId " +
            "ORDER BY u.createdAt DESC, u.id DESC")
    List<User> findByIsActiveTrueAndCreatedAtAfterAndIdLessThanOrderByCreatedAtDesc(
            @Param("threshold") Timestamp threshold,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.createdAt > :threshold " +
            "ORDER BY u.createdAt DESC, u.id DESC")
    List<User> findByIsActiveTrueAndCreatedAtAfterOrderByCreatedAtDesc(
            @Param("threshold") Timestamp threshold,
            Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role.name = :roleName AND u.isActive = true ORDER BY u.id ASC")
    Optional<User> findFirstByRoleName(@Param("roleName") String roleName);
    @Query("""
        SELECT u FROM User u
        WHERE u.username LIKE CONCAT('%', :q, '%')
          AND u.isActive = true
        ORDER BY u.username ASC
    """)
    java.util.List<User> searchByUsername(
            @Param("q") String q,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.username = :username AND u.isActive = true")
    Optional<User> findByActualUsername(@Param("username") String username);
}