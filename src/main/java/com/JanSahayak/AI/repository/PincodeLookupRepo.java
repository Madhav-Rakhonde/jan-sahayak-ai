package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.PincodeLookup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PincodeLookupRepo extends JpaRepository<PincodeLookup, String> {

    // ===== Basic Active/Inactive Queries =====

    List<PincodeLookup> findByIsActiveTrue();

    List<PincodeLookup> findByIsActiveFalse();

    boolean existsByPincodeAndIsActiveTrue(String pincode);

    // ===== Area Name Search =====

    List<PincodeLookup> findByAreaNameContainingIgnoreCaseAndIsActiveTrue(String areaName);


    // ===== City Search =====

    List<PincodeLookup> findByCityContainingIgnoreCaseAndIsActiveTrue(String city);


    // ===== District Search =====

    List<PincodeLookup> findByDistrictIgnoreCaseAndIsActiveTrue(String district);


    // ===== State Search =====

    List<PincodeLookup> findByStateIgnoreCaseAndIsActiveTrue(String state);

    // ===== Combined State and District Search =====

    List<PincodeLookup> findByStateIgnoreCaseAndDistrictIgnoreCaseAndIsActiveTrue(String state, String district);

    // ===== General Text Search =====

    @Query("SELECT p FROM PincodeLookup p WHERE p.isActive = true AND " +
            "(LOWER(p.pincode) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.areaName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.city) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.district) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.state) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<PincodeLookup> findBySearchableTextContainingIgnoreCaseAndIsActiveTrue(@Param("searchTerm") String searchTerm);

    // ===== Distinct Values for Dropdowns =====

    @Query("SELECT DISTINCT p.state FROM PincodeLookup p WHERE p.isActive = true ORDER BY p.state")
    List<String> findDistinctStatesByIsActiveTrue();

    @Query("SELECT DISTINCT p.district FROM PincodeLookup p WHERE p.isActive = true ORDER BY p.district")
    List<String> findDistinctDistrictsByIsActiveTrue();

    @Query("SELECT DISTINCT p.district FROM PincodeLookup p WHERE p.isActive = true AND LOWER(p.state) = LOWER(:state) ORDER BY p.district")
    List<String> findDistinctDistrictsByStateIgnoreCaseAndIsActiveTrue(@Param("state") String state);

    /**
     * Find pincodes starting with given prefix (for state/district prefix searches)
     */
    List<PincodeLookup> findByPincodeStartingWithAndIsActiveTrue(String prefix);

// ===== Prefix Extraction Methods =====

    /**
     * Get distinct state prefixes (first N digits of pincode) by state name
     */
    @Query("SELECT DISTINCT SUBSTRING(p.pincode, 1, :prefixLength) FROM PincodeLookup p " +
            "WHERE p.isActive = true AND LOWER(p.state) = LOWER(:state)")
    List<String> findDistinctPrefixesByStateIgnoreCase(@Param("state") String state,
                                                       @Param("prefixLength") int prefixLength);

    /**
     * Get distinct district prefixes (first N digits of pincode) by district name
     */
    @Query("SELECT DISTINCT SUBSTRING(p.pincode, 1, :prefixLength) FROM PincodeLookup p " +
            "WHERE p.isActive = true AND LOWER(p.district) = LOWER(:district)")
    List<String> findDistinctPrefixesByDistrictIgnoreCase(@Param("district") String district,
                                                          @Param("prefixLength") int prefixLength);

}