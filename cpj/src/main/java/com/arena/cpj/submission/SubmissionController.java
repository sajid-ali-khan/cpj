package com.arena.cpj.submission;

import com.arena.cpj.auth.UserContext;
import com.arena.cpj.submission.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping("/submit")
    public StudentSubmitResponse submit(@RequestBody SubmitRequest request) {
        return submissionService.submitSynchronous(request);
    }

    @PostMapping("/compile")
    public CompileResponse compile(@RequestBody CompileRequest request) {
        return submissionService.compileAndRun(request);
    }

    @GetMapping("/submissions")
    public List<SubmissionResponse> list(@RequestParam Long contestId) {
        return submissionService.getSubmissions(UserContext.get().getId(), contestId);
    }
}
