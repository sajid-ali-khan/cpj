package com.arena.cpj.user.dto;

import com.arena.cpj.leaderboard.ParticipantStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentRegistrationDto {
    private Long contestId;
    private ParticipantStatus status;
}
