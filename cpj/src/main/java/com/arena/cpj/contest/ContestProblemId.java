package com.arena.cpj.contest;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ContestProblemId implements Serializable {

    @Column(name = "contest_id")
    private Long contestId;

    @Column(name = "problem_id")
    private Long problemId;
}
