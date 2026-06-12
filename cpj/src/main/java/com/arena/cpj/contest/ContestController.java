package com.arena.cpj.contest;

import com.arena.cpj.contest.dto.ContestProblemSummaryResponse;
import com.arena.cpj.contest.dto.ContestSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contests")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;

    @GetMapping("/current")
    public ContestSummaryResponse getCurrent() {
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
}
