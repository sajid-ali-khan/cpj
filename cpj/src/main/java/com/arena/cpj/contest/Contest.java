package com.arena.cpj.contest;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
    private Instant startTime;

    @Column(name = "duration_mins", nullable = false)
    private Integer durationMins;

    @Column(name = "problem_count", nullable = false)
    @Builder.Default
    private Integer problemCount = 0;

    @Column(name = "max_score", nullable = false)
    @Builder.Default
    private Integer maxScore = 0;

    /**
     * Get the current contest phase based on startTime and duration
     */
    public ContestPhase getPhase(Instant now) {
        if (now.isBefore(startTime)) {
            return ContestPhase.UPCOMING;
        }
        Instant endTime = startTime.plus(durationMins, ChronoUnit.MINUTES);
        if (now.isBefore(endTime)) {
            return ContestPhase.LIVE;
        }
        return ContestPhase.FINISHED;
    }

    /**
     * Check if contest time has expired (current time > start + duration)
     */
    public boolean isExpired() {
        Instant endTime = startTime.plus(durationMins, ChronoUnit.MINUTES);
        return Instant.now().isAfter(endTime);
    }

    /**
     * Check if contest has started (current time >= start time)
     */
    public boolean hasStarted() {
        return Instant.now().isAfter(startTime) || Instant.now().equals(startTime);
    }
}
