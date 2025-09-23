package com.JanSahayak.AI.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.*;
import lombok.Setter;

import java.util.Date;

@Entity
@Table(name = "post_views", indexes = {
        @Index(name = "idx_view_post", columnList = "post_id"),
        @Index(name = "idx_view_user", columnList = "user_id"),
        @Index(name = "idx_view_date", columnList = "viewed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔗 The post that was viewed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 🔗 The user who viewed the post
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ⏱️ When the view happened
    @Column(name = "viewed_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date viewedAt = new Date();

    // ⏳ How long the user viewed the post (in seconds)
    @Column(name = "view_duration")
    private Integer viewDuration;

    // ===== Helper Methods =====

    public boolean hasDuration() {
        return viewDuration != null && viewDuration > 0;
    }

    public Double getViewDurationInMinutes() {
        return viewDuration != null ? viewDuration / 60.0 : null;
    }

    public boolean isLongView() {
        return viewDuration != null && viewDuration > 30;
    }

    public boolean isQuickView() {
        return viewDuration != null && viewDuration < 5;
    }
}
