package com.arena.cpj.user;

import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.leaderboard.Leaderboard;
import com.arena.cpj.leaderboard.LeaderboardRepository;
import com.arena.cpj.leaderboard.LeaderboardService;
import com.arena.cpj.leaderboard.ParticipantStatus;
import com.arena.cpj.auth.UserContext;
import com.arena.cpj.auth.ForbiddenException;
import com.arena.cpj.auth.UnauthorizedException;
import com.arena.cpj.event.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final UserRepository userRepository;
    private final LeaderboardRepository leaderboardRepository;
    private final LeaderboardService leaderboardService;
    private final SseService sseService;

    @GetMapping("/{rollNumber}/registrations")
    public ResponseEntity<List<com.arena.cpj.user.dto.StudentRegistrationDto>> getRegistrations(@PathVariable String rollNumber) {
        User currentUser = UserContext.get();
        if (currentUser == null) {
            throw new UnauthorizedException("Session invalid or expired.");
        }
        if (currentUser.getRole() == UserRole.STUDENT && !currentUser.getRollNo().equalsIgnoreCase(rollNumber.trim())) {
            throw new ForbiddenException("Access denied. You cannot view other students' registrations.");
        }

        User user = userRepository.findByRollNo(rollNumber.trim())
                .orElseThrow(() -> new NotFoundException("User not found for roll number: " + rollNumber));

        if (user.isDeleted()) {
            throw new NotFoundException("User not found for roll number: " + rollNumber);
        }

        List<com.arena.cpj.user.dto.StudentRegistrationDto> registrations = leaderboardRepository.findByUserId(user.getId()).stream()
                .map(l -> com.arena.cpj.user.dto.StudentRegistrationDto.builder()
                        .contestId(l.getContest().getId())
                        .status(l.getStatus())
                        .violations(l.getViolations())
                        .build())
                .toList();

        return ResponseEntity.ok(registrations);
    }

    @PostMapping("/{rollNumber}/reset-violations")
    public ResponseEntity<?> resetViolations(@PathVariable String rollNumber) {
        User currentUser = UserContext.get();
        if (currentUser == null) {
            throw new UnauthorizedException("Session invalid or expired.");
        }
        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Admin access required");
        }

        User user = userRepository.findByRollNo(rollNumber.trim())
                .orElseThrow(() -> new NotFoundException("User not found for roll number: " + rollNumber));

        if (user.isDeleted()) {
            throw new NotFoundException("User not found for roll number: " + rollNumber);
        }

        List<Leaderboard> entries = leaderboardRepository.findByUserId(user.getId());
        for (Leaderboard entry : entries) {
            if (entry.getStatus() == ParticipantStatus.LOCKED) {
                entry.setStatus(ParticipantStatus.WRITING);
            }
            entry.setViolations(0);
            leaderboardRepository.save(entry);
            sseService.broadcastLeaderboard(leaderboardService.getLeaderboard(entry.getContest().getId()));
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Violations reset successfully"));
    }
}
