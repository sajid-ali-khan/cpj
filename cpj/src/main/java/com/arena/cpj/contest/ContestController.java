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
import com.arena.cpj.leaderboard.dto.LeaderboardEntryDto;
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

    @GetMapping("/{contestId}")
    public ContestSummaryResponse getContest(@PathVariable Long contestId) {
        return contestService.getContest(contestId);
    }

    @GetMapping("/{contestId}/leaderboard")
    public List<LeaderboardEntryDto> getLeaderboard(@PathVariable Long contestId) {
        return leaderboardService.getLeaderboard(contestId);
    }

    @GetMapping
    public org.springframework.data.domain.Page<ContestSummaryResponse> getAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return contestService.getEligibleContests(page, size);
    }

    @GetMapping("/{contestId}/problems")
    public List<ContestProblemSummaryResponse> getProblems(@PathVariable Long contestId) {
        return contestService.getContestProblems(contestId);
    }

    @PostMapping("/{contestId}/register")
    public org.springframework.http.ResponseEntity<?> register(
         @PathVariable Long contestId) {
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



    @PutMapping("/{contestId}/violations")
    public ResponseEntity<?> recordViolation(@PathVariable Long contestId) {
        User currentUser = UserContext.get();
        if (currentUser == null) {
            throw new UnauthorizedException("Session invalid or expired.");
        }
        if (currentUser.getRole() != UserRole.STUDENT) {
            throw new ForbiddenException("Only students can record violations.");
        }

        Leaderboard entry = leaderboardRepository.findByContestIdAndUserId(contestId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Student is not registered for this contest"));

        if (entry.getStatus() == ParticipantStatus.LOCKED) {
            throw new ForbiddenException("Student is already locked out of this contest.");
        }

        int newViolations = entry.getViolations() + 1;
        entry.setViolations(newViolations);
        if (newViolations >= 3) {
            entry.setStatus(ParticipantStatus.LOCKED);
        }
        leaderboardRepository.save(entry);

        sseService.broadcastLeaderboard(leaderboardService.getLeaderboard(contestId));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "violations", newViolations,
                "locked", newViolations >= 3
        ));
    }
}
