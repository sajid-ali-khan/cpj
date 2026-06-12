package com.arena.cpj.problem;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "problems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    // 'constraints' is a non-reserved keyword in PostgreSQL — safe as column name
    @Column(name = "constraints", columnDefinition = "TEXT")
    private String constraints;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Difficulty difficulty;

    @Column(name = "media_link", length = 500)
    private String mediaLink;

    @Column(name = "input_structure", columnDefinition = "TEXT")
    private String inputStructure;

    @Column(name = "output_structure", columnDefinition = "TEXT")
    private String outputStructure;
}
