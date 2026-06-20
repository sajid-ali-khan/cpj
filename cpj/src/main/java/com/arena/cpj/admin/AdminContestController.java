package com.arena.cpj.admin;

import com.arena.cpj.admin.dto.AdminContestRegistrationResponse;
import com.arena.cpj.admin.dto.ContestDetailResponse;
import com.arena.cpj.admin.dto.ContestResponse;
import com.arena.cpj.admin.dto.CreateContestRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/contests")
@RequiredArgsConstructor
public class AdminContestController {

    private final AdminContestService adminContestService;

    @PostMapping
    public ContestDetailResponse create(@RequestBody CreateContestRequest request) {
        return adminContestService.create(request);
    }

    @GetMapping
    public List<ContestResponse> list() {
        return adminContestService.list();
    }

    @GetMapping("/{id}")
    public ContestDetailResponse get(@PathVariable Long id) {
        return adminContestService.get(id);
    }

    @PostMapping("/{id}/end")
    public ContestDetailResponse end(@PathVariable Long id) {
        return adminContestService.end(id);
    }

    @PutMapping("/{id}")
    public ContestDetailResponse update(@PathVariable Long id, @RequestBody CreateContestRequest request) {
        return adminContestService.update(id, request);
    }

    @GetMapping("/{id}/submissions")
    public List<com.arena.cpj.admin.dto.AdminSubmissionResponse> getSubmissions(@PathVariable Long id) {
        return adminContestService.getContestSubmissions(id);
    }

    @PostMapping("/{contestId}/registrations")
    public ResponseEntity<?> registerStudentsBulk(
            @PathVariable Long contestId,
            @RequestBody List<String> rollNos) {
        adminContestService.registerStudentsBulk(contestId, rollNos);
        var registrations = adminContestService.getEligibleStudents(contestId);
        log.info("Registered students for contest {}: {}", contestId, registrations);
        return ResponseEntity.ok(Map.of("success", true, "message", "Students registered successfully" , "registrations", registrations));
    }

    @GetMapping("/{contestId}/registrations")
    public List<AdminContestRegistrationResponse> getEligibleStudents(@PathVariable Long contestId) {
        return adminContestService.getEligibleStudents(contestId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        adminContestService.delete(id);
    }

    @DeleteMapping("/{contestId}/registrations/{userId}")
    public ResponseEntity<?> deleteStudentRegistration(@PathVariable Long contestId, @PathVariable Long userId) {
        adminContestService.deleteStudentRegistration(contestId, userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Student registration deleted successfully"));
    }
}
