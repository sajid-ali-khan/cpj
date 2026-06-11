package com.arena.cpj.admin;

import com.arena.cpj.admin.dto.CreateUserRequest;
import com.arena.cpj.admin.dto.UserResponse;
import com.arena.cpj.common.BadRequestException;
import com.arena.cpj.user.User;
import com.arena.cpj.user.UserRepository;
import com.arena.cpj.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        validate(request);

        if (userRepository.findByRollNo(request.getRollNo().trim()).isPresent()) {
            throw new BadRequestException("Roll number already exists: " + request.getRollNo());
        }

        User user = User.builder()
                .name(request.getName().trim())
                .rollNo(request.getRollNo().trim())
                .branch(request.getBranch() != null ? request.getBranch().trim() : null)
                .role(request.getRole() != null ? request.getRole() : UserRole.STUDENT)
                .build();

        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<UserResponse> search(String query, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page, size, org.springframework.data.domain.Sort.by("rollNo").ascending());

        org.springframework.data.domain.Page<User> userPage;
        if (query == null || query.isBlank()) {
            userPage = userRepository.findAll(pageable);
        } else {
            userPage = userRepository.searchUsers(query.trim(), pageable);
        }

        return userPage.map(this::toResponse);
    }

    private void validate(CreateUserRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("name is required");
        }
        if (request.getRollNo() == null || request.getRollNo().isBlank()) {
            throw new BadRequestException("rollNo is required");
        }
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .rollNo(user.getRollNo())
                .branch(user.getBranch())
                .role(user.getRole())
                .build();
    }
}
