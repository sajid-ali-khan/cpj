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

    @PutMapping("/api/admin/test-cases/{id}")
    public void update(@PathVariable Long id,
                                   @RequestBody CreateTestCaseRequest request) {
        adminTestCaseService.update(id, request);
    }

    @GetMapping("/api/admin/test-cases/{id}")
    public TestCaseResponse get(@PathVariable Long id) {
        return adminTestCaseService.get(id);
    }

    @GetMapping("/api/admin/problems/{problemId}/test-cases")
    public List<TestCaseResponse> list(@PathVariable Long problemId) {
        return adminTestCaseService.list(problemId);
    }

    @DeleteMapping("/api/admin/test-cases/{id}")
    public void delete(@PathVariable Long id) {
        adminTestCaseService.delete(id);
    }

    @PostMapping(value = "/api/admin/problems/{problemId}/test-cases/zip", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<TestCaseResponse> uploadZip(@PathVariable Long problemId,
                                            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return adminTestCaseService.uploadZip(problemId, file);
    }


}
