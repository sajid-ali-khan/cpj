package com.arena.cpj.submission;

import com.arena.cpj.auth.UserContext;
import com.arena.cpj.submission.dto.SubmissionRequest;
import com.arena.cpj.submission.dto.SubmissionResponse;
import com.arena.cpj.submission.dto.SubmitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    public SubmitResponse submit(@RequestBody SubmissionRequest request) {
        return submissionService.submit(
                UserContext.get(),
                request.getContestId(),
                request.getProblemId(),
                request.getCode(),
                request.getLanguageId());
    }

    @GetMapping
    public List<SubmissionResponse> list(@RequestParam Long contestId) {
        return submissionService.getSubmissions(UserContext.get().getId(), contestId);
    }
}
