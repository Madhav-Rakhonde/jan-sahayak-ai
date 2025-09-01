package com.JanSahayak.AI.model;

import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name = "district_lookup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DistrictLookup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_name", nullable = false, length = 100)
    private String locationName;
}
