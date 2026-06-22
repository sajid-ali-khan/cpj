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

    @Column(name = "java_time_limit")
    private Double javaTimeLimit;

    @Column(name = "java_memory_limit")
    private Integer javaMemoryLimit;

    @Column(name = "cpp_time_limit")
    private Double cppTimeLimit;

    @Column(name = "cpp_memory_limit")
    private Integer cppMemoryLimit;

    @Column(name = "python_time_limit")
    private Double pythonTimeLimit;

    @Column(name = "python_memory_limit")
    private Integer pythonMemoryLimit;

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;
}

