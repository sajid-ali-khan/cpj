package com.arena.cpj.contest;

import com.arena.cpj.problem.Problem;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "contest_problems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestProblem {

    @EmbeddedId
    private ContestProblemId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("contestId")
    @JoinColumn(name = "contest_id")
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("problemId")
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @Column(nullable = false)
    private Integer points;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
