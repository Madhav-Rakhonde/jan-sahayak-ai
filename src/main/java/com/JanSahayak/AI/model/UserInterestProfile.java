package com.JanSahayak.AI.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "user_interest_profiles",
        uniqueConstraints = @UniqueConstraint(
                name   = "uk_uip_user_topic",
                columnNames = {"user_id", "topic"}
        ),
        indexes = {
                @Index(name = "idx_uip_user_weight",  columnList = "user_id, weight DESC"),
                @Index(name = "idx_uip_topic",        columnList = "topic"),
                @Index(name = "idx_uip_last_engaged", columnList = "last_engaged_at"),
                @Index(name = "idx_uip_signals",      columnList = "user_id, total_signals_cache")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserInterestProfile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Canonical topic key (lowercase, ASCII-safe, max 100 chars).
     * Examples: "roads", "cricket", "bollywood", "water_supply",
     *           "ipl", "local_politics", "gaming", "crypto"
     */
    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    /**
     * Accumulated weight (decayed separately in batch).
     * Zone thresholds:
     *   CORE     ≥ 8.0  → strong interest, show liberally
     *   CASUAL   ≥ 2.0  → moderate interest
     *   EMERGING  < 2.0 → newly forming, use for diversity
     */
    @Column(name = "weight", nullable = false, columnDefinition = "numeric(10,4) DEFAULT 0")
    @Builder.Default
    private Double weight = 0.0;

    /**
     * Total raw signal count for this topic (undecayed).
     * > 200 → bubble_risk = true → diversity injection doubles.
     */
    @Column(name = "interaction_count", nullable = false)
    @Builder.Default
    private Integer interactionCount = 0;

    /**
     * SUM of all interactionCounts across all topics for this user,
     * cached here and updated atomically. Used for phase detection
     * without a separate COUNT(*) query per feed request.
     *
     * Phase:
     *   0       → COLD    (popularity fallback)
     *   1–30    → WARMING (50% interest / 50% popularity blend)
     *   31+     → WARM    (85% HLIG + 15% diversity)
     */
    @Column(name = "total_signals_cache", nullable = false)
    @Builder.Default
    private Integer totalSignalsCache = 0;

    /**
     * True when interactionCount > 200 — signals echo-chamber risk.
     * Feed service doubles diversity injection for this user.
     */
    @Column(name = "bubble_risk_flag", nullable = false)
    @Builder.Default
    private Boolean bubbleRiskFlag = false;

    @Column(name = "last_engaged_at")
    private Instant lastEngagedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;  // set in @PrePersist — do NOT use @Builder.Default with Instant.now()

    // ── Constants ────────────────────────────────────────────────────────────

    private static final double LAMBDA           = 0.015; // 46-day half-life (India pace)
    private static final double CORE_THRESHOLD   = 8.0;
    private static final double CASUAL_THRESHOLD = 2.0;
    private static final int    BUBBLE_THRESHOLD = 200;

    // ── Business Logic ────────────────────────────────────────────────────────

    /**
     * In-memory decay — does NOT write to DB.
     * Called at scoring time so the DB row is only written during nightly batch.
     */
    public double decayedWeight() {
        if (lastEngagedAt == null || weight == null || weight <= 0) return 0.0;
        long days = ChronoUnit.DAYS.between(lastEngagedAt, Instant.now());
        if (days <= 0) return weight;
        return weight * Math.exp(-LAMBDA * days);
    }

    public Zone zone() {
        double w = decayedWeight();
        if (w >= CORE_THRESHOLD)   return Zone.CORE;
        if (w >= CASUAL_THRESHOLD) return Zone.CASUAL;
        return Zone.EMERGING;
    }

    /**
     * Named accessor for stream collectors and Spring projections.
     * Returns in-memory decayed weight (does NOT write to DB).
     */
    public double getDecayedWeight() {
        return decayedWeight();
    }

    /** User has over-consumed this topic — inject more diversity. */
    public boolean isBubbleRisk() {
        return Boolean.TRUE.equals(bubbleRiskFlag);
    }

    public enum Zone { CORE, CASUAL, EMERGING }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        clampWeight();
    }

    @PreUpdate
    private void preUpdate() {
        clampWeight();
        bubbleRiskFlag = interactionCount != null && interactionCount > BUBBLE_THRESHOLD;
    }

    private void clampWeight() {
        if (weight == null || weight < 0) weight = 0.0;
    }
}