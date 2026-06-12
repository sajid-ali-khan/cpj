package com.arena.cpj.contest;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "contests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "duration_mins", nullable = false)
    private Integer durationMins;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ContestStatus status = ContestStatus.UPCOMING;

    @Column(name = "problem_count", nullable = false)
    @Builder.Default
    private Integer problemCount = 0;

    @Column(name = "max_score", nullable = false)
    @Builder.Default
    private Integer maxScore = 0;

    /**
     * Check if contest time has expired (current time > start + duration)
     */
    public boolean isExpired() {
        LocalDateTime endTime = startTime.plusMinutes(durationMins);
        return LocalDateTime.now().isAfter(endTime);
    }

    /**
     * Check if contest has started (current time >= start time)
     */
    public boolean hasStarted() {
        return LocalDateTime.now().isAfter(startTime) || LocalDateTime.now().equals(startTime);
    }
}
