package com.arena.cpj.submission;
 
import com.arena.cpj.judge0.Judge0CallbackPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
 
@Slf4j
@RestController
@RequestMapping("/internal/callback")
@RequiredArgsConstructor
public class CallbackController {
 
    private final SubmissionService submissionService;
    private final ObjectMapper objectMapper;
 
    @PostMapping
    public void callback(@RequestParam Long submissionId,
                         @RequestParam int testCaseIndex,
                         @RequestBody String rawPayload) {
        log.info("=== Callback Received ===");
        log.info("Submission ID: {}", submissionId);
        log.info("Test Case Index: {}", testCaseIndex);
        log.info("Callback Raw Payload: {}", rawPayload);
        try {
            Judge0CallbackPayload payload = objectMapper.readValue(rawPayload, Judge0CallbackPayload.class);
            payload.setStdout(decodeBase64(payload.getStdout()));
            payload.setStderr(decodeBase64(payload.getStderr()));
            payload.setCompileOutput(decodeBase64(payload.getCompileOutput()));
            payload.setMessage(decodeBase64(payload.getMessage()));
            submissionService.handleCallback(submissionId, testCaseIndex, payload);
        } catch (Exception e) {
            log.error("Failed to parse callback payload for submission ID: {}", submissionId, e);
        }
        log.info("=========================");
    }
 
    private String decodeBase64(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new String(java.util.Base64.getDecoder().decode(value.trim()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
