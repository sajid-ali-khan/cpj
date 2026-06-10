package com.arena.cpj.admin.dto;

import com.arena.cpj.user.UserRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {

    private final Long id;
    private final String name;
    private final String rollNo;
    private final String branch;
    private final UserRole role;
}
