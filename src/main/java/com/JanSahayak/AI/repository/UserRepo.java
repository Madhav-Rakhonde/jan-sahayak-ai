package com.JanSahayak.AI.repository;

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
     * JOIN FETCH version of findByEmail for the Spring Security authentication path.
     * User.role is FetchType.LAZY — this loads both in ONE query, eliminating the
     * extra round-trip on every authenticated API request.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.email = :email")
    Optional<User> findByEmailWithRole(@Param("email") String email);

    /**
     * JOIN FETCH version of findByUsername for cases where the role is
     * needed immediately (e.g., admin permission checks).
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.username = :username")
    Optional<User> findByUsernameWithRole(@Param("username") String username);

    /**
     * FIX — CommunityInviteService.sendInvite() calls findByActualUsername().
     * In User.java, getActualUsername() returns the `username` column — there is
     * no separate DB column.  This query searches by u.username under the name
     * the service expects, so the service needs no changes.
     */
    @Query("SELECT u FROM User u WHERE u.username = :username AND u.isActive = true")
    Optional<User> findByActualUsername(@Param("username") String username);

    // =========================================================================
    // ACTIVE USER LISTING — cursor-paginated (getAllActiveUsers / getUsersByRole)
    //
    // FIX: UserService.getAllActiveUsers() and getUsersByRole() call these four
    // methods which were missing from the repo entirely.
    // =========================================================================

    /**
     * Active users with id < cursor, newest-first.
     * Used by getAllActiveUsers() when a pagination cursor is present.
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.id < :beforeId ORDER BY u.id DESC")
    List<User> findByIsActiveTrueAndIdLessThanOrderByIdDesc(
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    /**
     * All active users, newest-first.
     * Used by getAllActiveUsers() for the first page (no cursor).
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true ORDER BY u.id DESC")
    List<User> findByIsActiveTrueOrderByIdDesc(Pageable pageable);

    /**
     * Active users with a specific role and id < cursor, newest-first.
     * Used by getUsersByRole() when a pagination cursor is present.
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.isActive = true AND u.id < :beforeId " +
            "ORDER BY u.id DESC")
    List<User> findByRoleNameAndIdLessThanOrderByIdDesc(
            @Param("roleName") String roleName,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    /**
     * All active users with a specific role, newest-first.
     * Used by getUsersByRole() for the first page (no cursor).
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.isActive = true " +
            "ORDER BY u.id DESC")
    List<User> findByRoleNameOrderByIdDesc(
            @Param("roleName") String roleName,
            Pageable pageable);

    // =========================================================================
    // RECENTLY CREATED USERS — cursor-paginated (getRecentlyCreatedUsers)
    //
    // FIX: Return type changed from Page<User> to List<User>.
    // UserService assigns the result directly to List<User> — returning Page<User>
    // causes "incompatible types" compile error.  All other similar methods in
    // this repo already return List<User>; these two are consistent with that.
    // =========================================================================

    /**
     * Active users created after {@code createdAt} with id < cursor, newest-first.
     * Used by getRecentlyCreatedUsers() when a pagination cursor is present.
     *
     * @param createdAt  only users created after this timestamp
     * @param cursor     only users with id < cursor (pagination anchor)
     * @param pageable   page size (limit + 1 for has-next detection)
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.createdAt > :createdAt " +
            "AND u.id < :cursor " +
            "ORDER BY u.createdAt DESC")
    List<User> findByIsActiveTrueAndCreatedAtAfterAndIdLessThanOrderByCreatedAtDesc(
            @Param("createdAt") Timestamp createdAt,
            @Param("cursor") Long cursor,
            Pageable pageable);

    /**
     * Active users created after {@code createdAt}, newest-first.
     * Used by getRecentlyCreatedUsers() for the first page (no cursor).
     *
     * @param createdAt  only users created after this timestamp
     * @param pageable   page size (limit + 1 for has-next detection)
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.createdAt > :createdAt " +
            "ORDER BY u.createdAt DESC")
    List<User> findByIsActiveTrueAndCreatedAtAfterOrderByCreatedAtDesc(
            @Param("createdAt") Timestamp createdAt,
            Pageable pageable);

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
    // USER SEARCH / TAGGING (searchUsersForTagging)
    //
    // FIX: UserService.searchUsersForTagging() calls searchUsersForTagging() and
    // searchUsersForTaggingWithCursor() which were missing from the repo.
    // These match against username (the display handle) case-insensitively and
    // are limited to active users only.
    // =========================================================================

    /**
     * Search active users whose username contains {@code query} (case-insensitive),
     * ordered by username ascending.
     * Used by searchUsersForTagging() for the first page (no cursor).
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "ORDER BY u.username ASC")
    List<User> searchUsersForTagging(
            @Param("query") String query,
            Pageable pageable);

    /**
     * Cursor-paginated version of searchUsersForTagging.
     * Returns only users with id < {@code beforeId}.
     * Used by searchUsersForTagging() when a pagination cursor is present.
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "AND u.id < :beforeId " +
            "ORDER BY u.username ASC")
    List<User> searchUsersForTaggingWithCursor(
            @Param("query") String query,
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
    // SEARCH / SUGGESTION (searchByUsername — existing)
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
    // ROLE + SEARCH WITH CURSOR (searchUsersByRoleAndQuery)
    //
    // FIX: Return type changed from Page<User> to List<User>.
    // UserService assigns the result to List<User> — Page<User> caused a
    // "incompatible types" compile error.
    // =========================================================================

    /**
     * Cursor-paginated search across active users filtered by role name and a
     * text query matched against username OR email (case-insensitive).
     * Returns only users with id < {@code cursor}, ordered by id DESC.
     *
     * @param role     role name, e.g. "ROLE_USER", "ROLE_DEPARTMENT"
     * @param query    search string matched against username and email
     * @param cursor   only users with id < cursor (pagination anchor)
     * @param pageable page size
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :role " +
            "AND u.isActive = true " +
            "AND u.id < :cursor " +
            "AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY u.id DESC")
    List<User> searchUsersByRoleAndQueryWithCursor(
            @Param("role") String role,
            @Param("query") String query,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // =========================================================================
    // GEOGRAPHIC DISTRIBUTION (getUserDistributionByPincode)
    //
    // FIX: UserService.getUserDistributionByPincode() calls this method which
    // was missing.  It returns aggregate counts grouped by pincode — much more
    // efficient than loading all users and grouping in Java.
    // =========================================================================

    /**
     * Returns pincode → user count pairs for all active users with a valid pincode.
     * Each Object[] element is { pincode (String), count (Long) }.
     * Used by getUserDistributionByPincode() and rolled up by getUserDistributionByState().
     */
    @Query("SELECT u.pincode, COUNT(u) FROM User u " +
            "WHERE u.isActive = true " +
            "AND u.pincode IS NOT NULL " +
            "AND LENGTH(u.pincode) = 6 " +
            "GROUP BY u.pincode " +
            "ORDER BY COUNT(u) DESC")
    List<Object[]> getUserDistributionByPincode();

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