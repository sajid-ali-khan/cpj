package com.arena.cpj.user;

import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.leaderboard.Leaderboard;
import com.arena.cpj.leaderboard.LeaderboardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final UserRepository userRepository;
    private final LeaderboardRepository leaderboardRepository;

    @GetMapping("/{rollNumber}/registrations")
    public ResponseEntity<List<Long>> getRegistrations(@PathVariable String rollNumber) {
        User user = userRepository.findByRollNo(rollNumber.trim())
                .orElseThrow(() -> new NotFoundException("User not found for roll number: " + rollNumber));

        List<Long> contestIds = leaderboardRepository.findByUserId(user.getId()).stream()
                .map(l -> l.getContest().getId())
                .toList();

        return ResponseEntity.ok(contestIds);
    }
}
