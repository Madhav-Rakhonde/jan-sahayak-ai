package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.enums.BroadcastScope;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

@Repository
public interface PostRepo extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    // ===== Basic Post Query Methods =====
    List<Post> findByUserOrderByCreatedAtDesc(User user);
    List<Post> findByUserAndStatusOrderByCreatedAtDesc(User user, PostStatus status);

    // ===== Count Methods =====
    Long countByStatus(PostStatus status);
    Long countByUser(User user);
    Long countByUserAndStatus(User user, PostStatus status);
    Long countByUserAndCreatedAtAfter(User user, Timestamp createdAt);

    // ===== Broadcasting Query Methods =====
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeIsNotNullOrderByCreatedAtDesc();

    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.status = :status ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeIsNotNullAndStatusOrderByCreatedAtDesc(@Param("status") PostStatus status);

    List<Post> findByBroadcastScopeOrderByCreatedAtDesc(BroadcastScope scope);

    // ===== Pincode Prefix Broadcasting Query Methods =====
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetStates LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT('%,', :prefix) OR " +
            "p.targetStates = :prefix) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetStatesContainingOrderByCreatedAtDesc(@Param("scope") BroadcastScope scope, @Param("prefix") String prefix);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetDistricts LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT('%,', :prefix) OR " +
            "p.targetDistricts = :prefix) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetDistrictsContainingOrderByCreatedAtDesc(@Param("scope") BroadcastScope scope, @Param("prefix") String prefix);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetPincodesContainingOrderByCreatedAtDesc(@Param("scope") BroadcastScope scope, @Param("pincode") String pincode);

    // ===== Efficient User Visibility Query Methods =====
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetStates LIKE CONCAT('%,', :userStatePrefix, ',%') OR " +
            "p.targetStates LIKE CONCAT(:userStatePrefix, ',%') OR " +
            "p.targetStates LIKE CONCAT('%,', :userStatePrefix) OR " +
            "p.targetStates = :userStatePrefix) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findStateBroadcastsForUser(@Param("scope") BroadcastScope scope,
                                          @Param("status") PostStatus status,
                                          @Param("userStatePrefix") String userStatePrefix);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetDistricts LIKE CONCAT('%,', :userDistrictPrefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT(:userDistrictPrefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT('%,', :userDistrictPrefix) OR " +
            "p.targetDistricts = :userDistrictPrefix) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findDistrictBroadcastsForUser(@Param("scope") BroadcastScope scope,
                                             @Param("status") PostStatus status,
                                             @Param("userDistrictPrefix") String userDistrictPrefix);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :userPincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:userPincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :userPincode) OR " +
            "p.targetPincodes = :userPincode) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findAreaBroadcastsForUser(@Param("scope") BroadcastScope scope,
                                         @Param("status") PostStatus status,
                                         @Param("userPincode") String userPincode);

    // ===== Broadcasting Count Methods =====
    @Query("SELECT COUNT(p) FROM Post p WHERE p.broadcastScope IS NOT NULL")
    Long countByBroadcastScopeIsNotNull();

    @Query("SELECT COUNT(p) FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.status = :status")
    Long countByBroadcastScopeIsNotNullAndStatus(@Param("status") PostStatus status);

    Long countByBroadcastScope(BroadcastScope scope);
    Long countByBroadcastScopeAndStatus(BroadcastScope scope, PostStatus status);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.user = :user AND p.broadcastScope IS NOT NULL")
    Long countByUserAndBroadcastScopeIsNotNull(@Param("user") User user);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.user = :user AND p.broadcastScope IS NOT NULL AND p.createdAt > :timestamp")
    Long countByUserAndBroadcastScopeIsNotNullAndCreatedAtAfter(@Param("user") User user, @Param("timestamp") Timestamp timestamp);

    Long countByUserAndBroadcastScope(User user, BroadcastScope scope);

    @Query("SELECT p FROM Post p WHERE p.user = :user AND p.broadcastScope IS NOT NULL")
    List<Post> findByUserAndBroadcastScopeIsNotNull(@Param("user") User user);

    // ===== Enhanced Feed System Methods =====
    Long countByUserInAndStatus(List<User> users, PostStatus status);

    // ===== Paginated Query Methods (Legacy - keeping for backward compatibility) =====
    @Query("SELECT p FROM Post p WHERE p.status = :status ORDER BY p.createdAt DESC")
    List<Post> findByStatusWithPageable(@Param("status") PostStatus status, Pageable pageable);

    @Query("SELECT p FROM Post p ORDER BY p.createdAt DESC")
    List<Post> findAllOrderByCreatedAtDesc(Pageable pageable);

    // ===== User Tagging Query Methods =====
    @Query("SELECT p FROM Post p WHERE SIZE(p.userTags) > 1")
    List<Post> findPostsWithMultipleUserTags();

    @Query("SELECT DISTINCT p FROM Post p " +
            "JOIN p.userTags ut " +
            "WHERE ut.taggedUser = :user " +
            "AND ut.isActive = true " +
            "ORDER BY p.createdAt DESC")
    List<Post> findPostsTaggedWithUser(@Param("user") User user);

    // ===== Trending and Analytics Methods =====
    @Query("SELECT p FROM Post p WHERE p.createdAt >= :startDate " +
            "ORDER BY (SIZE(p.likes) + SIZE(p.comments) + SIZE(p.views)) DESC")
    List<Post> findTrendingPosts(@Param("startDate") Timestamp startDate, Pageable pageable);

    @Query("SELECT " +
            "SUM(CASE WHEN SIZE(p.userTags) > 0 THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN SIZE(p.userTags) = 0 THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN SIZE(p.userTags) > 1 THEN 1 ELSE 0 END), " +
            "AVG(SIZE(p.userTags)) " +
            "FROM Post p")
    Object[] getTaggedPostsStatistics();

    // ===== Search Methods for PostSearchService =====
    List<Post> findByStatusAndContentContainingIgnoreCaseOrderByCreatedAtDesc(PostStatus status, String content);
    List<Post> findByUserInAndStatusOrderByCreatedAtDesc(List<User> users, PostStatus status);
    List<Post> findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByCreatedAtDesc(BroadcastScope broadcastScope, PostStatus status, String pincode);
    List<Post> findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByCreatedAtDesc(BroadcastScope broadcastScope, PostStatus status, String statePrefix);
    List<Post> findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByCreatedAtDesc(BroadcastScope broadcastScope, PostStatus status, String districtPrefix);
    List<Post> findByStatusAndCreatedAtAfterOrderByCreatedAtDesc(PostStatus status, Date fromDate);
    List<Post> findByStatusOrderByCreatedAtDesc(PostStatus status);
    List<Post> findByUserInOrderByCreatedAtDesc(List<User> users);

    @Query("SELECT p FROM Post p WHERE p.user IN :users AND p.status = :status AND " +
            "LOWER(p.content) LIKE LOWER(CONCAT('%', :content, '%')) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByUserInAndStatusAndContentContainingIgnoreCaseOrderByCreatedAtDesc(
            @Param("users") List<User> users,
            @Param("status") PostStatus status,
            @Param("content") String content);

    @Query("SELECT p FROM Post p WHERE p.status = :status AND " +
            "p.content LIKE :hashtag " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByStatusAndHashtagOrderByCreatedAtDesc(
            @Param("status") PostStatus status,
            @Param("hashtag") String hashtag);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.status = :status AND " +
            "LOWER(p.content) LIKE LOWER(CONCAT('%', :content, '%'))")
    Long countByStatusAndContentContainingIgnoreCase(
            @Param("status") PostStatus status,
            @Param("content") String content);

    @Query("SELECT p FROM Post p WHERE p.user IN :users AND p.status = :status " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByUserInAndStatusOrderByCreatedAtDesc(
            @Param("users") List<User> users,
            @Param("status") PostStatus status,
            Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope IN :scopes AND p.status = :status " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeInAndStatusOrderByCreatedAtDesc(
            @Param("scopes") List<BroadcastScope> scopes,
            @Param("status") PostStatus status);

    @Query("SELECT DISTINCT p FROM Post p WHERE " +
            "(p.broadcastScope IS NULL OR " +
            "(p.broadcastScope = :countryScope AND p.targetCountry = 'IN') OR " +
            "(p.broadcastScope = :stateScope AND p.targetStates LIKE %:statePrefix%) OR " +
            "(p.broadcastScope = :districtScope AND p.targetDistricts LIKE %:districtPrefix%) OR " +
            "(p.broadcastScope = :areaScope AND p.targetPincodes LIKE %:pincode%)) " +
            "AND p.status = :status " +
            "ORDER BY p.createdAt DESC")
    List<Post> findVisiblePostsForUserByLocation(
            @Param("countryScope") BroadcastScope countryScope,
            @Param("stateScope") BroadcastScope stateScope,
            @Param("districtScope") BroadcastScope districtScope,
            @Param("areaScope") BroadcastScope areaScope,
            @Param("statePrefix") String statePrefix,
            @Param("districtPrefix") String districtPrefix,
            @Param("pincode") String pincode,
            @Param("status") PostStatus status,
            Pageable pageable);

    List<Post> findByBroadcastScopeAndTargetCountryOrderByCreatedAtDesc(BroadcastScope scope, String targetCountry);
    List<Post> findByBroadcastScopeAndStatusAndTargetCountryOrderByCreatedAtDesc(BroadcastScope scope, PostStatus status, String targetCountry);
    Long countByBroadcastScopeAndTargetCountry(BroadcastScope scope, String targetCountry);
    Long countByBroadcastScopeAndStatusAndTargetCountry(BroadcastScope scope, PostStatus status, String targetCountry);

    @Query("SELECT DISTINCT p FROM Post p WHERE " +
            "(p.broadcastScope IS NULL OR " +
            "(p.broadcastScope = :countryScope AND p.targetCountry = 'IN') OR " +
            "(p.broadcastScope = :stateScope AND p.targetStates LIKE %:statePrefix%) OR " +
            "(p.broadcastScope = :districtScope AND p.targetDistricts LIKE %:districtPrefix%) OR " +
            "(p.broadcastScope = :areaScope AND p.targetPincodes LIKE %:pincode%)) " +
            "AND p.status = :status " +
            "ORDER BY p.createdAt DESC")
    List<Post> findVisiblePostsForIndianUser(
            @Param("countryScope") BroadcastScope countryScope,
            @Param("stateScope") BroadcastScope stateScope,
            @Param("districtScope") BroadcastScope districtScope,
            @Param("areaScope") BroadcastScope areaScope,
            @Param("statePrefix") String statePrefix,
            @Param("districtPrefix") String districtPrefix,
            @Param("pincode") String pincode,
            @Param("status") PostStatus status);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND " +
            "p.targetCountry = 'IN' ORDER BY p.createdAt DESC")
    List<Post> findAllIndiaBroadcastPosts();

    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND " +
            "p.targetCountry = 'IN' AND p.status = :status ORDER BY p.createdAt DESC")
    List<Post> findActiveIndiaBroadcastPosts(@Param("status") PostStatus status);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.targetCountry = 'IN'")
    Long countIndiaBroadcastPosts();

    @Query("SELECT COUNT(p) FROM Post p WHERE p.broadcastScope IS NOT NULL AND " +
            "p.targetCountry = 'IN' AND p.status = :status")
    Long countActiveIndiaBroadcastPosts(@Param("status") PostStatus status);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :countryScope AND " +
            "p.targetCountry = 'IN' AND p.user.role.name IN ('ROLE_DEPARTMENT', 'ROLE_ADMIN') " +
            "ORDER BY p.createdAt DESC")
    List<Post> findGovernmentCountryBroadcasts(@Param("countryScope") BroadcastScope countryScope);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.broadcastScope = :countryScope AND " +
            "p.targetCountry = 'IN' AND p.user.role.name IN ('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    Long countGovernmentCountryBroadcasts(@Param("countryScope") BroadcastScope countryScope);

    List<Post> findByTargetCountry(String targetCountry);

    @Query("UPDATE Post p SET p.targetCountry = 'IN' WHERE p.targetCountry IS NULL OR p.targetCountry = ''")
    @Modifying
    int updateNullTargetCountryToIndia();

    @Query("SELECT p FROM Post p WHERE p.targetCountry IS NULL OR p.targetCountry = ''")
    List<Post> findPostsWithoutTargetCountry();

    Long countByUserAndBroadcastScopeAndTargetCountry(User user, BroadcastScope scope, String targetCountry);
    List<Post> findByStatusOrderByCreatedAtDesc(PostStatus status, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByStatusAndIdLessThanOrderByCreatedAtDesc(@Param("status") PostStatus status,
                                                             @Param("beforeId") Long beforeId,
                                                             Pageable pageable);

    // ===== CURSOR-BASED PAGINATION METHODS =====
    // These methods support the updated PostService with cursor-based pagination

    // Basic pagination with cursor
    @Query("SELECT p FROM Post p WHERE p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByIdLessThanOrderByCreatedAtDesc(@Param("beforeId") Long beforeId, Pageable pageable);

    // User-specific pagination methods
    @Query("SELECT p FROM Post p WHERE p.user = :user AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByUserAndIdLessThanOrderByCreatedAtDesc(@Param("user") User user,
                                                           @Param("beforeId") Long beforeId,
                                                           Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.user = :user AND p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByUserAndStatusAndIdLessThanOrderByCreatedAtDesc(@Param("user") User user,
                                                                    @Param("status") PostStatus status,
                                                                    @Param("beforeId") Long beforeId,
                                                                    Pageable pageable);

    // Broadcasting pagination methods
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeIsNotNullAndIdLessThanOrderByCreatedAtDesc(@Param("beforeId") Long beforeId,
                                                                              Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeIsNotNullOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeIsNotNullAndStatusAndIdLessThanOrderByCreatedAtDesc(@Param("status") PostStatus status,
                                                                                       @Param("beforeId") Long beforeId,
                                                                                       Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.status = :status ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeIsNotNullAndStatusOrderByCreatedAtDesc(@Param("status") PostStatus status,
                                                                          Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndIdLessThanOrderByCreatedAtDesc(@Param("scope") BroadcastScope scope,
                                                                     @Param("beforeId") Long beforeId,
                                                                     Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeOrderByCreatedAtDesc(@Param("scope") BroadcastScope scope, Pageable pageable);

    // Country-wide broadcasting pagination methods
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.targetCountry = :targetCountry AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetCountryAndIdLessThanOrderByCreatedAtDesc(@Param("scope") BroadcastScope scope,
                                                                                     @Param("targetCountry") String targetCountry,
                                                                                     @Param("beforeId") Long beforeId,
                                                                                     Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.targetCountry = :targetCountry ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetCountryOrderByCreatedAtDesc(@Param("scope") BroadcastScope scope,
                                                                        @Param("targetCountry") String targetCountry,
                                                                        Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND p.targetCountry = :targetCountry AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetCountryAndIdLessThanOrderByCreatedAtDesc(@Param("scope") BroadcastScope scope,
                                                                                              @Param("status") PostStatus status,
                                                                                              @Param("targetCountry") String targetCountry,
                                                                                              @Param("beforeId") Long beforeId,
                                                                                              Pageable pageable);

    // Geographic targeting pagination methods
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetStates LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT('%,', :prefix) OR " +
            "p.targetStates = :prefix) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetStatesContainingAndIdLessThanOrderByCreatedAtDesc(@Param("scope") BroadcastScope scope,
                                                                                              @Param("prefix") String prefix,
                                                                                              @Param("beforeId") Long beforeId,
                                                                                              Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetDistricts LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT('%,', :prefix) OR " +
            "p.targetDistricts = :prefix) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetDistrictsContainingAndIdLessThanOrderByCreatedAtDesc(@Param("scope") BroadcastScope scope,
                                                                                                 @Param("prefix") String prefix,
                                                                                                 @Param("beforeId") Long beforeId,
                                                                                                 Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetPincodesContainingAndIdLessThanOrderByCreatedAtDesc(@Param("scope") BroadcastScope scope,
                                                                                                @Param("pincode") String pincode,
                                                                                                @Param("beforeId") Long beforeId,
                                                                                                Pageable pageable);

    // User tagging pagination methods
    @Query("SELECT DISTINCT p FROM Post p " +
            "JOIN p.userTags ut " +
            "WHERE ut.taggedUser = :user AND ut.isActive = true AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findPostsTaggedWithUserAndIdLessThan(@Param("user") User user,
                                                    @Param("beforeId") Long beforeId,
                                                    Pageable pageable);

    @Query("SELECT p FROM Post p WHERE SIZE(p.userTags) > 1 AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findPostsWithMultipleUserTagsAndIdLessThan(@Param("beforeId") Long beforeId, Pageable pageable);

    // User broadcast pagination methods
    @Query("SELECT p FROM Post p WHERE p.user = :user AND p.broadcastScope IS NOT NULL AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByUserAndBroadcastScopeIsNotNullAndIdLessThanOrderByCreatedAtDesc(@Param("user") User user,
                                                                                     @Param("beforeId") Long beforeId,
                                                                                     Pageable pageable);

    // Additional specialized pagination methods for comprehensive coverage
    @Query("SELECT p FROM Post p WHERE p.user IN :users AND p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findByUserInAndStatusAndIdLessThanOrderByCreatedAtDesc(@Param("users") List<User> users,
                                                                      @Param("status") PostStatus status,
                                                                      @Param("beforeId") Long beforeId,
                                                                      Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.status = :status AND " +
            "LOWER(p.content) LIKE LOWER(CONCAT('%', :content, '%')) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByStatusAndContentContainingIgnoreCaseAndIdLessThanOrderByCreatedAtDesc(@Param("status") PostStatus status,
                                                                                           @Param("content") String content,
                                                                                           @Param("beforeId") Long beforeId,
                                                                                           Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.createdAt >= :startDate AND p.id < :beforeId " +
            "ORDER BY (SIZE(p.likes) + SIZE(p.comments) + SIZE(p.views)) DESC")
    List<Post> findTrendingPostsWithCursor(@Param("startDate") Timestamp startDate,
                                           @Param("beforeId") Long beforeId,
                                           Pageable pageable);

    // ===== ADDITIONAL OPTIMIZED PAGINATION METHODS =====
    // These are additional methods that would enhance performance for infinite scroll feeds

    // Optimized visible posts for user with cursor support
    @Query("SELECT DISTINCT p FROM Post p WHERE " +
            "(p.broadcastScope IS NULL OR " +
            "(p.broadcastScope = :countryScope AND p.targetCountry = 'IN') OR " +
            "(p.broadcastScope = :stateScope AND p.targetStates LIKE %:statePrefix%) OR " +
            "(p.broadcastScope = :districtScope AND p.targetDistricts LIKE %:districtPrefix%) OR " +
            "(p.broadcastScope = :areaScope AND p.targetPincodes LIKE %:pincode%)) " +
            "AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findVisiblePostsForUserByLocationAndIdLessThan(
            @Param("countryScope") BroadcastScope countryScope,
            @Param("stateScope") BroadcastScope stateScope,
            @Param("districtScope") BroadcastScope districtScope,
            @Param("areaScope") BroadcastScope areaScope,
            @Param("statePrefix") String statePrefix,
            @Param("districtPrefix") String districtPrefix,
            @Param("pincode") String pincode,
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // Enhanced search methods with cursor support
    @Query("SELECT p FROM Post p WHERE p.user IN :users AND p.status = :status AND " +
            "LOWER(p.content) LIKE LOWER(CONCAT('%', :content, '%')) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByUserInAndStatusAndContentContainingIgnoreCaseAndIdLessThanOrderByCreatedAtDesc(
            @Param("users") List<User> users,
            @Param("status") PostStatus status,
            @Param("content") String content,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // Hashtag search with cursor support
    @Query("SELECT p FROM Post p WHERE p.status = :status AND " +
            "p.content LIKE :hashtag AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByStatusAndHashtagAndIdLessThanOrderByCreatedAtDesc(
            @Param("status") PostStatus status,
            @Param("hashtag") String hashtag,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // Government broadcasts with cursor support
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :countryScope AND " +
            "p.targetCountry = 'IN' AND p.user.role.name IN ('ROLE_DEPARTMENT', 'ROLE_ADMIN') AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findGovernmentCountryBroadcastsAndIdLessThan(@Param("countryScope") BroadcastScope countryScope,
                                                            @Param("beforeId") Long beforeId,
                                                            Pageable pageable);

    // India broadcast posts with cursor support
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND " +
            "p.targetCountry = 'IN' AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findAllIndiaBroadcastPostsAndIdLessThan(@Param("beforeId") Long beforeId, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND " +
            "p.targetCountry = 'IN' AND p.status = :status AND p.id < :beforeId ORDER BY p.createdAt DESC")
    List<Post> findActiveIndiaBroadcastPostsAndIdLessThan(@Param("status") PostStatus status,
                                                          @Param("beforeId") Long beforeId,
                                                          Pageable pageable);

    // Posts by date range with cursor support
    @Query("SELECT p FROM Post p WHERE p.status = :status AND p.createdAt >= :fromDate AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByStatusAndCreatedAtAfterAndIdLessThanOrderByCreatedAtDesc(
            @Param("status") PostStatus status,
            @Param("fromDate") Date fromDate,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // Broadcast scope filtering with cursor support
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IN :scopes AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeInAndStatusAndIdLessThanOrderByCreatedAtDesc(
            @Param("scopes") List<BroadcastScope> scopes,
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // ===== PERFORMANCE-OPTIMIZED PAGINATION METHODS =====
    // These methods are optimized for high-performance infinite scroll feeds

    // Fast user feed with minimal joins
    @Query(value = "SELECT p.* FROM posts p " +
            "WHERE p.user_id = :userId AND p.id < :beforeId " +
            "ORDER BY p.created_at DESC LIMIT :limit", nativeQuery = true)
    List<Post> findUserPostsFast(@Param("userId") Long userId,
                                 @Param("beforeId") Long beforeId,
                                 @Param("limit") int limit);

    // Fast broadcast feed with minimal processing
    @Query(value = "SELECT p.* FROM posts p " +
            "WHERE p.broadcast_scope IS NOT NULL AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY p.created_at DESC LIMIT :limit", nativeQuery = true)
    List<Post> findBroadcastPostsFast(@Param("status") String status,
                                      @Param("beforeId") Long beforeId,
                                      @Param("limit") int limit);

    // Fast country-wide broadcast feed
    @Query(value = "SELECT p.* FROM posts p " +
            "WHERE p.broadcast_scope = 'COUNTRY' AND p.target_country = 'IN' " +
            "AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY p.created_at DESC LIMIT :limit", nativeQuery = true)
    List<Post> findCountryBroadcastsFast(@Param("status") String status,
                                         @Param("beforeId") Long beforeId,
                                         @Param("limit") int limit);

    // Fast area-specific posts for location-based feeds
    @Query(value = "SELECT p.* FROM posts p " +
            "WHERE ((p.broadcast_scope = 'AREA' AND p.target_pincodes LIKE %:pincode%) " +
            "OR (p.broadcast_scope = 'COUNTRY' AND p.target_country = 'IN')) " +
            "AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY p.created_at DESC LIMIT :limit", nativeQuery = true)
    List<Post> findLocationSpecificPostsFast(@Param("pincode") String pincode,
                                             @Param("status") String status,
                                             @Param("beforeId") Long beforeId,
                                             @Param("limit") int limit);

    // ===== UTILITY METHODS FOR CURSOR-BASED PAGINATION =====

    // Get the maximum post ID for pagination initialization
    @Query("SELECT MAX(p.id) FROM Post p")
    Long findMaxPostId();

    // Get the minimum post ID for pagination bounds
    @Query("SELECT MIN(p.id) FROM Post p")
    Long findMinPostId();

    // Count posts for a specific cursor range (useful for analytics)
    @Query("SELECT COUNT(p) FROM Post p WHERE p.id >= :startId AND p.id <= :endId")
    Long countPostsInIdRange(@Param("startId") Long startId, @Param("endId") Long endId);

    // Get latest post ID for a user (useful for real-time updates)
    @Query("SELECT MAX(p.id) FROM Post p WHERE p.user = :user")
    Long findLatestPostIdForUser(@Param("user") User user);

    // Check if there are more posts after a given cursor
    @Query("SELECT COUNT(p) > 0 FROM Post p WHERE p.id < :beforeId AND p.status = :status")
    Boolean hasMorePostsAfterCursor(@Param("beforeId") Long beforeId, @Param("status") PostStatus status);

    // ===== OPTIMIZED COUNT METHODS FOR PAGINATION METADATA =====

    // These methods help provide accurate pagination metadata without expensive full counts

    // Estimated total count (faster than exact count for large datasets)
    @Query(value = "SELECT TABLE_ROWS FROM information_schema.TABLES " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'posts'", nativeQuery = true)
    Long estimatePostCount();

    // Count posts newer than cursor (for "new posts available" indicators)
    @Query("SELECT COUNT(p) FROM Post p WHERE p.id > :afterId AND p.status = :status")
    Long countPostsNewerThan(@Param("afterId") Long afterId, @Param("status") PostStatus status);

    // Count user's posts newer than cursor
    @Query("SELECT COUNT(p) FROM Post p WHERE p.user = :user AND p.id > :afterId AND p.status = :status")
    Long countUserPostsNewerThan(@Param("user") User user,
                                 @Param("afterId") Long afterId,
                                 @Param("status") PostStatus status);

    // ===== BATCH OPERATIONS FOR PERFORMANCE =====

    // Batch fetch posts by IDs (useful for complex filtering after initial query)
    @Query("SELECT p FROM Post p WHERE p.id IN :ids ORDER BY p.createdAt DESC")
    List<Post> findPostsByIds(@Param("ids") List<Long> ids);

    // Batch fetch posts with their user data (optimized join)
    @Query("SELECT p FROM Post p " +
            "LEFT JOIN FETCH p.user u " +
            "WHERE p.id IN :ids ORDER BY p.createdAt DESC")
    List<Post> findPostsByIdsWithUser(@Param("ids") List<Long> ids);

    // ===== CLEANUP AND MAINTENANCE METHODS =====

    // Find posts that might need cursor index maintenance
    @Query("SELECT p FROM Post p WHERE p.createdAt < :cutoffDate ORDER BY p.id DESC")
    List<Post> findOldPostsForMaintenance(@Param("cutoffDate") Date cutoffDate, Pageable pageable);

    // Find posts with potential data integrity issues
    @Query("SELECT p FROM Post p WHERE p.broadcastScope IS NOT NULL AND p.targetCountry IS NULL")
    List<Post> findBroadcastPostsWithoutTargetCountry();

    // For first page (no cursor)
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND p.targetCountry = :targetCountry ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetCountryOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("targetCountry") String targetCountry,
            Pageable pageable);

    // ===== REQUIRED METHODS FOR POSTSEARCHSERVICE CURSOR-BASED PAGINATION =====

    // Content search with cursor
    List<Post> findByStatusAndContentContainingIgnoreCaseOrderByIdDesc(PostStatus status, String content, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.status = :status AND " +
            "LOWER(p.content) LIKE LOWER(CONCAT('%', :content, '%')) AND p.id < :beforeId " +
            "ORDER BY p.id DESC")
    List<Post> findByStatusAndContentContainingIgnoreCaseAndIdBeforeOrderByIdDesc(
            @Param("status") PostStatus status,
            @Param("content") String content,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // User-based search with cursor (ordering by ID DESC instead of createdAt DESC for consistency)
    List<Post> findByUserInAndStatusOrderByIdDesc(List<User> users, PostStatus status, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.user IN :users AND p.status = :status AND p.id < :beforeId " +
            "ORDER BY p.id DESC")
    List<Post> findByUserInAndStatusAndIdBeforeOrderByIdDesc(
            @Param("users") List<User> users,
            @Param("status") PostStatus status,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // Broadcast search with cursor
    List<Post> findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByIdDesc(
            BroadcastScope scope, PostStatus status, String pincode, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) AND p.id < :beforeId " +
            "ORDER BY p.id DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetPincodesContainingAndIdBeforeOrderByIdDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("pincode") String pincode,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    List<Post> findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByIdDesc(
            BroadcastScope scope, PostStatus status, String statePrefix, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetStates LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT('%,', :prefix) OR " +
            "p.targetStates = :prefix) AND p.id < :beforeId " +
            "ORDER BY p.id DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetStatesContainingAndIdBeforeOrderByIdDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("prefix") String prefix,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    List<Post> findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByIdDesc(
            BroadcastScope scope, PostStatus status, String districtPrefix, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetDistricts LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT('%,', :prefix) OR " +
            "p.targetDistricts = :prefix) AND p.id < :beforeId " +
            "ORDER BY p.id DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetDistrictsContainingAndIdBeforeOrderByIdDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("prefix") String prefix,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // Date-based search with cursor
    List<Post> findByStatusAndCreatedAtAfterOrderByIdDesc(PostStatus status, Date fromDate, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.status = :status AND p.createdAt >= :fromDate AND p.id < :beforeId " +
            "ORDER BY p.id DESC")
    List<Post> findByStatusAndCreatedAtAfterAndIdBeforeOrderByIdDesc(
            @Param("status") PostStatus status,
            @Param("fromDate") Date fromDate,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // Find normal posts (non-broadcast) by users with status filter
    List<Post> findByUserInAndStatusAndBroadcastScopeIsNullOrderByCreatedAtDesc(
            List<User> users, PostStatus status, Pageable pageable);

    // With cursor support
    List<Post> findByUserInAndStatusAndBroadcastScopeIsNullAndIdLessThanOrderByCreatedAtDesc(
            List<User> users, PostStatus status, Long beforeId, Pageable pageable);

    // State-level broadcasts with status filter
    List<Post> findByBroadcastScopeAndStatusAndTargetStatesContainingOrderByCreatedAtDesc(
            BroadcastScope scope, PostStatus status, String statePrefix, Pageable pageable);

    // District-level broadcasts with status filter
    List<Post> findByBroadcastScopeAndStatusAndTargetDistrictsContainingOrderByCreatedAtDesc(
            BroadcastScope scope, PostStatus status, String districtPrefix, Pageable pageable);

    // Area-level broadcasts with status filter
    List<Post> findByBroadcastScopeAndStatusAndTargetPincodesContainingOrderByCreatedAtDesc(
            BroadcastScope scope, PostStatus status, String pincode, Pageable pageable);

    // Count methods with status and geographic targeting
    Long countByBroadcastScopeAndStatusAndTargetStatesContaining(BroadcastScope scope, PostStatus status, String statePrefix);
    Long countByBroadcastScopeAndStatusAndTargetDistrictsContaining(BroadcastScope scope, PostStatus status, String districtPrefix);
    Long countByBroadcastScopeAndStatusAndTargetPincodesContaining(BroadcastScope scope, PostStatus status, String pincode);

    // ===== GEOGRAPHIC BROADCAST METHODS WITH PAGINATION SUPPORT =====

    // State-level broadcasts with Pageable support
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetStates LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT('%,', :prefix) OR " +
            "p.targetStates = :prefix) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetStatesContainingOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("prefix") String prefix,
            Pageable pageable);

    // District-level broadcasts with Pageable support
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetDistricts LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT('%,', :prefix) OR " +
            "p.targetDistricts = :prefix) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetDistrictsContainingOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("prefix") String prefix,
            Pageable pageable);

    // Area-level broadcasts with Pageable support
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndTargetPincodesContainingOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("pincode") String pincode,
            Pageable pageable);

    // ===== CURSOR-BASED GEOGRAPHIC BROADCAST METHODS =====

    // State-level broadcasts with cursor and status filter
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetStates LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetStates LIKE CONCAT('%,', :prefix) OR " +
            "p.targetStates = :prefix) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetStatesContainingAndIdLessThanOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("prefix") String prefix,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // District-level broadcasts with cursor and status filter
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetDistricts LIKE CONCAT('%,', :prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT(:prefix, ',%') OR " +
            "p.targetDistricts LIKE CONCAT('%,', :prefix) OR " +
            "p.targetDistricts = :prefix) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetDistrictsContainingAndIdLessThanOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("prefix") String prefix,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // Area-level broadcasts with cursor and status filter
    @Query("SELECT p FROM Post p WHERE p.broadcastScope = :scope AND p.status = :status AND " +
            "(p.targetPincodes LIKE CONCAT('%,', :pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT(:pincode, ',%') OR " +
            "p.targetPincodes LIKE CONCAT('%,', :pincode) OR " +
            "p.targetPincodes = :pincode) AND p.id < :beforeId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findByBroadcastScopeAndStatusAndTargetPincodesContainingAndIdLessThanOrderByCreatedAtDesc(
            @Param("scope") BroadcastScope scope,
            @Param("status") PostStatus status,
            @Param("pincode") String pincode,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

}