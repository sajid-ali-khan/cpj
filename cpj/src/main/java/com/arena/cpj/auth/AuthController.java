package com.arena.cpj.auth;

import com.arena.cpj.user.User;
import com.arena.cpj.user.UserRepository;
import com.arena.cpj.user.UserRole;
import lombok.Getter;
import lombok.Setter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    @Value("${cpj.admin.password}")
    private String adminPassword;

    @PostMapping("/student/login")
    public ResponseEntity<?> studentLogin(@RequestBody StudentLoginRequest request) {
        if (request.getRollNumber() == null || request.getRollNumber().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Roll number is required"));
        }
        String rollNo = request.getRollNumber().trim();
        User user = userRepository.findByRollNo(rollNo)
                .orElseThrow(() -> new UnauthorizedException("User not found for roll number: " + rollNo));

        if (user.getRole() != UserRole.STUDENT) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied. Student role required."));
        }

        String token = "STU-SESSION-" + java.util.UUID.randomUUID().toString();
        user.setActiveSessionToken(token);
        userRepository.save(user);

        Map<String, Object> response = Map.of(
            "token", token,
            "user", Map.of(
                "rollNumber", user.getRollNo(),
                "name", user.getName(),
                "contestId", request.getContestId() != null ? request.getContestId() : "",
                "branch", user.getBranch() != null ? user.getBranch() : ""
            ),
            "userType", "student"
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@RequestBody AdminLoginRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
        }
        if (request.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
        }

        String username = request.getUsername().trim();
        User user = userRepository.findByRollNo(username)
                .orElseThrow(() -> new UnauthorizedException("Admin user not found: " + username));

        if (user.getRole() != UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied. Admin role required."));
        }

        if (!adminPassword.equals(request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid admin credentials"));
        }

        // Generate and save active session token
        String token = "ADM-SESSION-" + java.util.UUID.randomUUID().toString();
        user.setActiveSessionToken(token);
        userRepository.save(user);

        Map<String, Object> response = Map.of(
            "token", token,
            "user", Map.of(
                "username", user.getRollNo(),
                "name", user.getName(),
                "role", "ADMIN"
            ),
            "userType", "admin"
        );
        return ResponseEntity.ok(response);
    }

    @Getter
    @Setter
    public static class StudentLoginRequest {
        private String contestId;
        private String rollNumber;
        private String password;
    }

    @Getter
    @Setter
    public static class AdminLoginRequest {
        private String username;
        private String password;
    }
}
