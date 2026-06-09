package com.arena.cpj.submission;

import com.arena.cpj.contest.Contest;
import com.arena.cpj.problem.Problem;
import com.arena.cpj.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String code;

    @Column(name = "language_id", nullable = false)
    private Integer languageId;

    @Column(name = "judge0_token", length = 100)
    private String judge0Token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Verdict verdict = Verdict.PENDING;

    /** Execution time in milliseconds, set by callback */
    @Column(name = "time_ms")
    private Integer timeMs;

    /** Peak memory in kilobytes, set by callback */
    @Column(name = "memory_kb")
    private Integer memoryKb;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;
}
