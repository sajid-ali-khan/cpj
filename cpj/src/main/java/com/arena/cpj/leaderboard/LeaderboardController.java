package com.arena.cpj.leaderboard;

import com.arena.cpj.leaderboard.dto.LeaderboardEntryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping
    public List<LeaderboardEntryDto> getLeaderboard(@RequestParam Long contestId) {
        return leaderboardService.getLeaderboard(contestId);
    }
}
