package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.DistrictLookup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DistrictLookupRepo extends JpaRepository<DistrictLookup, Long> {

    List<DistrictLookup> findAllByOrderByLocationNameAsc();

    boolean existsByLocationName(String locationName);

    List<DistrictLookup> findByLocationNameContainingIgnoreCase(String locationName);

    @Query("SELECT d FROM DistrictLookup d WHERE d.locationName LIKE %:query% ORDER BY d.locationName ASC")
    List<DistrictLookup> searchByLocationName(@Param("query") String query);


}