package com.arena.cpj.contest;

import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.contest.dto.ContestProblemSummaryResponse;
import com.arena.cpj.contest.dto.ContestSummaryResponse;
import com.arena.cpj.user.User;
import com.arena.cpj.user.UserRepository;
import com.arena.cpj.user.UserRole;
import com.arena.cpj.user.dto.StudentWorkstationDto;
import com.arena.cpj.auth.UserContext;
import com.arena.cpj.auth.ForbiddenException;
import com.arena.cpj.auth.UnauthorizedException;
import com.arena.cpj.leaderboard.Leaderboard;
import com.arena.cpj.leaderboard.LeaderboardRepository;
import com.arena.cpj.leaderboard.LeaderboardService;
import com.arena.cpj.leaderboard.ParticipantStatus;
import com.arena.cpj.event.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contests")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;
    private final LeaderboardRepository leaderboardRepository;
    private final LeaderboardService leaderboardService;
    private final UserRepository userRepository;
    private final SseService sseService;

    @GetMapping("/current")
    public List<ContestSummaryResponse> getCurrent() {
        return contestService.getCurrentContest();
    }

    @GetMapping
    public List<ContestSummaryResponse> getAll() {
        return contestService.getAllContests();
    }

    @GetMapping("/{contestId}/problems")
    public List<ContestProblemSummaryResponse> getProblems(@PathVariable Long contestId) {
        return contestService.getContestProblems(contestId);
    }

    @PostMapping("/{contestId}/register")
    public org.springframework.http.ResponseEntity<?> register(
            @PathVariable Long contestId,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        contestService.registerUserForContest(com.arena.cpj.auth.UserContext.get(), contestId);
        return org.springframework.http.ResponseEntity.ok(java.util.Map.of("success", true, "message", "Registration successful"));
    }

    @PostMapping("/{contestId}/submit")
    public org.springframework.http.ResponseEntity<?> submit(
            @PathVariable Long contestId,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        contestService.submitContest(contestId, com.arena.cpj.auth.UserContext.get());
        return org.springframework.http.ResponseEntity.ok(java.util.Map.of("success", true, "message", "Contest submitted successfully"));
    }

    @GetMapping("/{contestId}/workstations")
    public ResponseEntity<List<StudentWorkstationDto>> getWorkstations(@PathVariable Long contestId) {
        User currentUser = UserContext.get();
        if (currentUser == null) {
            throw new UnauthorizedException("Session invalid or expired.");
        }
        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Admin access required");
        }

        List<StudentWorkstationDto> list = leaderboardRepository.findByContestIdOrderByScoreDescLastAcTimeAsc(contestId).stream()
                .map(entry -> StudentWorkstationDto.builder()
                        .name(entry.getUser().getName())
                        .rollNumber(entry.getUser().getRollNo())
                        .contestId(entry.getContest().getId())
                        .violations(entry.getViolations())
                        .status(entry.getViolations() >= 3 ? "Locked" : "Active")
                        .build())
                .toList();

        return ResponseEntity.ok(list);
    }

    @GetMapping("/workstations")
    public ResponseEntity<List<StudentWorkstationDto>> getAllWorkstations() {
        User currentUser = UserContext.get();
        if (currentUser == null) {
            throw new UnauthorizedException("Session invalid or expired.");
        }
        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Admin access required");
        }

        List<StudentWorkstationDto> list = leaderboardRepository.findAll().stream()
                .map(entry -> StudentWorkstationDto.builder()
                        .name(entry.getUser().getName())
                        .rollNumber(entry.getUser().getRollNo())
                        .contestId(entry.getContest().getId())
                        .violations(entry.getViolations())
                        .status(entry.getViolations() >= 3 ? "Locked" : "Active")
                        .build())
                .toList();

        return ResponseEntity.ok(list);
    }

    @PostMapping("/{contestId}/students/{rollNumber}/violation")
    public ResponseEntity<?> recordViolation(
            @PathVariable Long contestId,
            @PathVariable String rollNumber,
            @RequestBody Map<String, Integer> body) {
        User currentUser = UserContext.get();
        if (currentUser == null) {
            throw new UnauthorizedException("Session invalid or expired.");
        }
        if (currentUser.getRole() == UserRole.STUDENT && !currentUser.getRollNo().equalsIgnoreCase(rollNumber.trim())) {
            throw new ForbiddenException("Access denied. You cannot modify other students' violations.");
        }

        User user = userRepository.findByRollNo(rollNumber.trim())
                .orElseThrow(() -> new NotFoundException("User not found for roll number: " + rollNumber));

        Leaderboard entry = leaderboardRepository.findByContestIdAndUserId(contestId, user.getId())
                .orElseThrow(() -> new NotFoundException("Student is not registered for this contest"));

        int violationCount = body.getOrDefault("violations", 0);
        entry.setViolations(violationCount);
        if (violationCount >= 3) {
            entry.setStatus(ParticipantStatus.FINISHED);
        }
        leaderboardRepository.save(entry);

        sseService.broadcastLeaderboard(leaderboardService.getLeaderboard(contestId));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "violations", violationCount,
                "status", violationCount >= 3 ? "Locked" : "Active"
        ));
    }
}
