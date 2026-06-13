package com.arena.cpj.user;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "roll_no", nullable = false, unique = true, length = 20)
    private String rollNo;

    @Column(length = 50)
    private String branch;

    @Column(unique = true, length = 255)
    private String email;

    @Column(name = "active_session_token", length = 255)
    private String activeSessionToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.STUDENT;
}
