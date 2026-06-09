package com.arena.cpj.submission;

import com.arena.cpj.judge0.Judge0CallbackPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/callback")
@RequiredArgsConstructor
public class CallbackController {

    private final SubmissionService submissionService;

    @PostMapping
    public void callback(@RequestParam Long submissionId,
                         @RequestParam int testCaseIndex,
                         @RequestBody Judge0CallbackPayload payload) {
        submissionService.handleCallback(submissionId, testCaseIndex, payload);
    }
}
