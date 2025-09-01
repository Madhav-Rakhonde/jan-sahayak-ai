package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {
    // ===== Basic User Queries =====
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);

    // ===== User Tagging Queries =====
    @Query("SELECT u FROM User u WHERE u.username IN :usernames AND u.isActive = true")
    List<User> findByUsernameInAndIsActiveTrue(@Param("usernames") List<String> usernames);

    // FIXED: Changed to use proper query since countByRoleName doesn't exist in entity
    @Query("SELECT COUNT(u) FROM User u JOIN u.role r WHERE r.name = :roleName AND u.isActive = true")
    Long countByRoleName(@Param("roleName") String roleName);

    List<User> findByUsernameIn(List<String> usernames);

    /**
     * Find users by role name and location (simplified version)
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.location = :location AND u.isActive = true")
    List<User> findByRoleNameAndLocationAndIsActiveTrue(@Param("roleName") String roleName,
                                                        @Param("location") String location);

    /**
     * Find department users by state code
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND u.location LIKE CONCAT('% ', :stateCode) " +
            "AND u.isActive = true")
    List<User> findDepartmentUsersByState(@Param("stateCode") String stateCode);

    /**
     * Find users by role name
     */
    @Query("SELECT u FROM User u JOIN u.role r WHERE r.name = :roleName AND u.isActive = true")
    List<User> findByRoleNameAndIsActiveTrue(@Param("roleName") String roleName);

    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "ORDER BY u.username ASC")
    List<User> searchUsersForTagging(@Param("query") String query);

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
     * Find department users with resolution statistics
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND u.isActive = true " +
            "ORDER BY u.location ASC, u.username ASC")
    List<User> findAllDepartmentUsers();

    /**
     * Find users by location proximity for recommendations
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND (u.location = :exactLocation " +
            "OR u.location LIKE CONCAT('% ', :stateCode)) " +
            "AND u.id != :excludeUserId " +
            "ORDER BY CASE " +
            "WHEN u.location = :exactLocation THEN 0 " +
            "ELSE 1 END, u.username ASC")
    List<User> findUsersByLocationProximity(@Param("exactLocation") String exactLocation,
                                            @Param("stateCode") String stateCode,
                                            @Param("excludeUserId") Long excludeUserId,
                                            Pageable pageable);

    /**
     * Find users who can resolve posts in specific location
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE (r.name = 'ROLE_DEPARTMENT' OR r.name = 'ROLE_ADMIN') " +
            "AND u.location = :location AND u.isActive = true " +
            "ORDER BY CASE WHEN r.name = 'ROLE_DEPARTMENT' THEN 0 ELSE 1 END, u.username ASC")
    List<User> findResolversForLocation(@Param("location") String location);

    /**
     * Find users who can resolve posts in specific state
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE (r.name = 'ROLE_DEPARTMENT' OR r.name = 'ROLE_ADMIN') " +
            "AND u.location LIKE CONCAT('% ', :stateCode) AND u.isActive = true " +
            "ORDER BY u.location ASC, CASE WHEN r.name = 'ROLE_DEPARTMENT' THEN 0 ELSE 1 END, u.username ASC")
    List<User> findResolversForState(@Param("stateCode") String stateCode);

    /**
     * Get user statistics by role
     */
    @Query("SELECT r.name, COUNT(u) FROM User u JOIN u.role r " +
            "WHERE u.isActive = true GROUP BY r.name")
    List<Object[]> getUserCountByRole();

    /**
     * Get user distribution by location
     */
    @Query("SELECT u.location, COUNT(u) FROM User u " +
            "WHERE u.isActive = true GROUP BY u.location ORDER BY COUNT(u) DESC")
    List<Object[]> getUserDistributionByLocation();

    /**
     * Find admin users
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_ADMIN' AND u.isActive = true " +
            "ORDER BY u.username ASC")
    List<User> findAdminUsers();

    /**
     * Find normal users by location
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_USER' AND u.location = :location AND u.isActive = true " +
            "ORDER BY u.username ASC")
    List<User> findNormalUsersByLocation(@Param("location") String location);

    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND (u.bio IS NULL OR u.location IS NULL)")
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
            "AND (:location IS NULL OR u.location = :location) " +
            "AND (:query IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY u.username ASC")
    List<User> searchUsers(@Param("roleName") String roleName,
                           @Param("location") String location,
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

    // FIXED: Added missing method implementation
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.location IN :locations AND u.isActive = true")
    List<User> findByRoleNameAndLocationInAndIsActiveTrue(@Param("roleName") String roleName,
                                                          @Param("locations") List<String> locations);

    /**
     * Find users who have never been tagged
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = 'ROLE_DEPARTMENT' AND u.isActive = true " +
            "AND u.id NOT IN (SELECT DISTINCT ut.taggedUser.id FROM UserTag ut WHERE ut.isActive = true) " +
            "ORDER BY u.createdAt DESC")
    List<User> findNeverTaggedDepartmentUsers();
}
