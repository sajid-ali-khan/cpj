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
    public ResponseEntity<List<com.arena.cpj.user.dto.StudentRegistrationDto>> getRegistrations(@PathVariable String rollNumber) {
        User user = userRepository.findByRollNo(rollNumber.trim())
                .orElseThrow(() -> new NotFoundException("User not found for roll number: " + rollNumber));

        List<com.arena.cpj.user.dto.StudentRegistrationDto> registrations = leaderboardRepository.findByUserId(user.getId()).stream()
                .map(l -> com.arena.cpj.user.dto.StudentRegistrationDto.builder()
                        .contestId(l.getContest().getId())
                        .status(l.getStatus())
                        .build())
                .toList();

        return ResponseEntity.ok(registrations);
    }
}
