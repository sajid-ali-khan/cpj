package com.arena.cpj.leaderboard;

import com.arena.cpj.contest.Contest;
import com.arena.cpj.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "leaderboard",
    uniqueConstraints = @UniqueConstraint(columnNames = {"contest_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Leaderboard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private Integer score = 0;

    /** Timestamp of the most recent accepted submission; null until first AC */
    @Column(name = "last_ac_time")
    private LocalDateTime lastAcTime;
}
