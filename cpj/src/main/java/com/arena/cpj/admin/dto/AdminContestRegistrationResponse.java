package com.arena.cpj.admin.dto;

import com.arena.cpj.leaderboard.ParticipantStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class AdminContestRegistrationResponse {
    private final Long id;
    private final String name;
    private final String rollNumber;
    private final String email;
    private final String branch;
    private final ParticipantStatus participantStatus;
}
