package com.arena.cpj.admin;

import com.arena.cpj.admin.dto.CreateTestCaseRequest;
import com.arena.cpj.admin.dto.TestCaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminTestCaseController {

    private final AdminTestCaseService adminTestCaseService;

    @PostMapping("/api/admin/problems/{problemId}/test-cases")
    public TestCaseResponse create(@PathVariable Long problemId,
                                   @RequestBody CreateTestCaseRequest request) {
        return adminTestCaseService.create(problemId, request);
    }

    @GetMapping("/api/admin/problems/{problemId}/test-cases")
    public List<TestCaseResponse> list(@PathVariable Long problemId) {
        return adminTestCaseService.list(problemId);
    }

    @DeleteMapping("/api/admin/test-cases/{id}")
    public void delete(@PathVariable Long id) {
        adminTestCaseService.delete(id);
    }
}
