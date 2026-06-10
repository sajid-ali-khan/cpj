package com.arena.cpj.admin;

import com.arena.cpj.admin.dto.ContestResponse;
import com.arena.cpj.admin.dto.CreateContestRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/contests")
@RequiredArgsConstructor
public class AdminContestController {

    private final AdminContestService adminContestService;

    @PostMapping
    public ContestResponse create(@RequestBody CreateContestRequest request) {
        return adminContestService.create(request);
    }

    @GetMapping
    public List<ContestResponse> list() {
        return adminContestService.list();
    }

    @GetMapping("/{id}")
    public ContestResponse get(@PathVariable Long id) {
        return adminContestService.get(id);
    }

    @PostMapping("/{id}/start")
    public ContestResponse start(@PathVariable Long id) {
        return adminContestService.start(id);
    }

    @PostMapping("/{id}/end")
    public ContestResponse end(@PathVariable Long id) {
        return adminContestService.end(id);
    }
}
