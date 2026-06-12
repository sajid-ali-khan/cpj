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
    public org.springframework.data.domain.Page<UserResponse> list(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return adminUserService.search(query, page, size);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @RequestBody CreateUserRequest request) {
        return adminUserService.update(id, request);
    }
}
