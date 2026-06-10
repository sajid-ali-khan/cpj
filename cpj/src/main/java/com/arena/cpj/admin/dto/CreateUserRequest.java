package com.arena.cpj.admin.dto;

import com.arena.cpj.user.UserRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateUserRequest {

    private String name;
    private String rollNo;
    private String branch;
    private UserRole role;
}
