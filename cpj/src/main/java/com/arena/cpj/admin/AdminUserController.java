package com.arena.cpj.admin;

import com.arena.cpj.admin.dto.CreateUserRequest;
import com.arena.cpj.admin.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping
    public UserResponse create(@RequestBody CreateUserRequest request) {
        return adminUserService.create(request);
    }

    @GetMapping
    public List<UserResponse> list() {
        return adminUserService.list();
    }
}
