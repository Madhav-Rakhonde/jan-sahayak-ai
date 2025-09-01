package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.service.DistrictLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/districts")
public class DistrictLookupController {

    @Autowired
    private DistrictLookupService service;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<String>>> searchDistricts(@RequestParam String prefix) {
        try {
            List<String> districts = service.searchDistricts(prefix);
            return ResponseEntity.ok(ApiResponse.success("Districts retrieved successfully", districts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve districts"));
        }
    }
}

