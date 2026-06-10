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

    @GetMapping
    public List<ProblemResponse> list() {
        return adminProblemService.list();
    }

    @GetMapping("/{id}")
    public ProblemResponse get(@PathVariable Long id) {
        return adminProblemService.get(id);
    }
}
