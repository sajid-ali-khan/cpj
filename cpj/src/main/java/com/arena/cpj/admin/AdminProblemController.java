package com.arena.cpj.admin;

import com.arena.cpj.admin.dto.CreateProblemRequest;
import com.arena.cpj.admin.dto.ProblemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/problems")
@RequiredArgsConstructor
public class AdminProblemController {

    private final AdminProblemService adminProblemService;

    @PostMapping
    public ProblemResponse create(@RequestBody CreateProblemRequest request) {
        return adminProblemService.create(request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @RequestParam(required = false, defaultValue = "false") boolean force) {
        adminProblemService.delete(id, force);
    }

    @GetMapping
    public List<ProblemResponse> list() {
        return adminProblemService.list();
    }

    @GetMapping("/{id}")
    public ProblemResponse get(@PathVariable Long id) {
        return adminProblemService.get(id);
    }

    @PutMapping("/{id}")
    public ProblemResponse update(@PathVariable Long id, @RequestBody CreateProblemRequest request) {
        return adminProblemService.update(id, request);
    }

    @PostMapping("/{id}/calibrate-limits")
    public com.arena.cpj.admin.dto.CalibrateLimitsResponse calibrateLimits(@PathVariable Long id,
                                                                           @RequestBody com.arena.cpj.admin.dto.CalibrateLimitsRequest request) {
        return adminProblemService.calibrateLimits(id, request);
    }
}

