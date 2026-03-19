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

    Optional<User> findByUsernameAndIsActiveTrue(String username);

    /**
     * FIX: JOIN FETCH version of findByEmail for the Spring Security authentication path.
     *
     * User.role is now FetchType.LAZY. Without this query, loadUserByUsername() would
     * issue a second SELECT to load the Role immediately after loading the User — on
     * every single authenticated API request.
     *
     * This query fetches both User and Role in ONE query with a JOIN, eliminating the
     * extra round-trip on the hottest code path in the application.
     *
     * Usage in UserService.loadUserByUsername():
     *   User user = userRepository.findByEmailWithRole(email).orElseThrow(...);
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.email = :email")
    Optional<User> findByEmailWithRole(@Param("email") String email);

    /**
     * FIX: JOIN FETCH version of findByUsername for cases where the role is
     * needed immediately (e.g., admin permission checks in the same request).
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.username = :username")
    Optional<User> findByUsernameWithRole(@Param("username") String username);

    // =========================================================================
    // PINCODE — EXACT & PREFIX
    // =========================================================================

    List<User> findByPincodeAndIsActiveTrue(String pincode);

    List<User> findByPincodeStartingWithAndIsActiveTrue(String pincodePrefix);

    @Query("SELECT COUNT(u) FROM User u WHERE u.pincode = :pincode AND u.isActive = true")
    long countActiveUsersByPincode(@Param("pincode") String pincode);

    @Query("SELECT u FROM User u WHERE u.pincode = :pincode AND u.isActive = true ORDER BY u.id ASC")
    List<User> findActiveUsersByPincode(@Param("pincode") String pincode, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.pincode IS NOT NULL " +
            "AND LENGTH(u.pincode) = 6 " +
            "AND SUBSTRING(u.pincode, 1, 2) = :statePrefix")
    List<User> findByStatePrefixAndIsActiveTrue(@Param("statePrefix") String statePrefix);

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

    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.pincode = :pincode AND u.isActive = true " +
            "ORDER BY u.id DESC")
    List<User> findByRoleNameAndPincodeAndIsActiveTrueOrderByIdDesc(
            @Param("roleName") String roleName,
            @Param("pincode") String pincode,
            Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.pincode = :pincode AND u.isActive = true " +
            "AND u.id < :beforeId ORDER BY u.id DESC")
    List<User> findByRoleNameAndPincodeAndIsActiveTrueAndIdLessThanOrderByIdDesc(
            @Param("roleName") String roleName,
            @Param("pincode") String pincode,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.pincode LIKE CONCAT(:prefix, '%') AND u.isActive = true " +
            "ORDER BY u.id DESC")
    List<User> findByRoleNameAndPincodeStartingWithAndIsActiveTrueOrderByIdDesc(
            @Param("roleName") String roleName,
            @Param("prefix") String prefix,
            Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.pincode LIKE CONCAT(:prefix, '%') AND u.isActive = true " +
            "AND u.id < :beforeId ORDER BY u.id DESC")
    List<User> findByRoleNameAndPincodeStartingWithAndIsActiveTrueAndIdLessThanOrderByIdDesc(
            @Param("roleName") String roleName,
            @Param("prefix") String prefix,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // =========================================================================
    // BATCH / IN-CLAUSE LOOKUPS
    // =========================================================================

    /**
     * Batch lookup by usernames — used by UserTaggingService.processUserTags()
     * to resolve @mentions in a single query instead of one per username.
     */
    @Query("SELECT u FROM User u WHERE u.username IN :usernames AND u.isActive = true")
    List<User> findByUsernameInAndIsActiveTrue(@Param("usernames") List<String> usernames);

    List<User> findByUsernameIn(List<String> usernames);

    // =========================================================================
    // SEARCH / SUGGESTION
    // =========================================================================

    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "ORDER BY u.username ASC")
    List<User> searchByUsername(@Param("query") String query, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "AND u.id < :beforeId ORDER BY u.username ASC")
    List<User> searchByUsernameWithCursor(
            @Param("query") String query,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // =========================================================================
    // ADMIN / STATS
    // =========================================================================

    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActiveUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt > :since")
    long countUsersCreatedAfter(@Param("since") Timestamp since);

    @Query("SELECT u FROM User u WHERE u.isActive = false ORDER BY u.updatedAt DESC")
    List<User> findInactiveUsers(Pageable pageable);
}