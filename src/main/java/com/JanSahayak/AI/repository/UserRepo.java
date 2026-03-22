package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Page;
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
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.email = :email")
    Optional<User> findByEmailWithRole(@Param("email") String email);

    /**
     * FIX: JOIN FETCH version of findByUsername for cases where the role is
     * needed immediately (e.g., admin permission checks in the same request).
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.username = :username")
    Optional<User> findByUsernameWithRole(@Param("username") String username);

    /**
     * FIX — CommunityInviteService.sendInvite() calls findByActualUsername().
     *
     * In the User entity, getActualUsername() returns the `username` column.
     * There is no separate "actualUsername" DB column — the JPA field is `username`.
     * This method delegates to the existing findByUsername() contract so the
     * service can look up users by the display username (@handle).
     *
     * Named findByActualUsername to match the call-site in CommunityInviteService
     * without changing the service code.
     */
    @Query("SELECT u FROM User u WHERE u.username = :username AND u.isActive = true")
    Optional<User> findByActualUsername(@Param("username") String username);

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
    // CURSOR-BASED USER FEED (UserService — new users feed)
    //
    // FIX: UserService.java calls these two methods which were missing from
    // UserRepo.  They power a cursor-paginated "new users" feed filtered to
    // active users created after a given timestamp, optionally with an id
    // cursor for stable pagination.
    // =========================================================================

    /**
     * Active users created after {@code createdAt}, with id less than {@code cursor}
     * (i.e. older in the result set), ordered newest-first.
     *
     * Used by UserService for cursor-paginated new-user feeds when a cursor is present.
     *
     * @param createdAt  only users created after this timestamp are returned
     * @param cursor     only users with id < cursor are returned (pagination anchor)
     * @param pageable   page size (limit + 1 for has-next detection)
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.createdAt > :createdAt " +
            "AND u.id < :cursor " +
            "ORDER BY u.createdAt DESC")
    Page<User> findByIsActiveTrueAndCreatedAtAfterAndIdLessThanOrderByCreatedAtDesc(
            @Param("createdAt") Timestamp createdAt,
            @Param("cursor") Long cursor,
            Pageable pageable);

    /**
     * Active users created after {@code createdAt}, ordered newest-first.
     * No id cursor — used for the first page of the new-user feed.
     *
     * @param createdAt  only users created after this timestamp are returned
     * @param pageable   page size (limit + 1 for has-next detection)
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.createdAt > :createdAt " +
            "ORDER BY u.createdAt DESC")
    Page<User> findByIsActiveTrueAndCreatedAtAfterOrderByCreatedAtDesc(
            @Param("createdAt") Timestamp createdAt,
            Pageable pageable);

    // =========================================================================
    // ROLE + SEARCH WITH CURSOR (UserService — searchUsersByRoleAndQueryWithCursor)
    //
    // FIX: UserService.java calls searchUsersByRoleAndQueryWithCursor() which
    // was missing.  Powers a cursor-paginated search filtered by role name and
    // a text query matched against username or email.
    // =========================================================================

    /**
     * Cursor-paginated search across active users filtered by role name and a
     * text query (matched against username OR email, case-insensitive).
     * Returns only users with id < {@code cursor}, ordered by id DESC.
     *
     * @param role     role name to filter by, e.g. "ROLE_USER", "ROLE_DEPARTMENT"
     * @param query    search string matched against username and email
     * @param cursor   only users with id < cursor are returned (pagination anchor)
     * @param pageable page size (limit + 1 for has-next detection)
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :role " +
            "AND u.isActive = true " +
            "AND u.id < :cursor " +
            "AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY u.id DESC")
    Page<User> searchUsersByRoleAndQueryWithCursor(
            @Param("role") String role,
            @Param("query") String query,
            @Param("cursor") Long cursor,
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