package com.arena.cpj.judge0;

import com.arena.cpj.config.Judge0Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class Judge0Client {

    private final Judge0Properties properties;
    private final RestClient restClient = RestClient.create();

    /**
     * Submits and blocks until Judge0 finishes (no callback required).
     * Used from {@code @Async} threads so the HTTP submit endpoint returns immediately.
     */
    public Judge0CallbackPayload submitAndWait(Judge0SubmissionRequest request) {
        Judge0CallbackPayload response = restClient.post()
                .uri(properties.getBaseUrl() + "/submissions?base64_encoded=false&wait=true")
                .body(request)
                .retrieve()
                .body(Judge0CallbackPayload.class);
        if (response == null || response.getStatus() == null) {
            throw new IllegalStateException("Judge0 returned no result");
        }
        return response;
    }

    /** Fire-and-forget submit; optional callback URL for deployments where Docker can reach the backend. */
    public String submitAsync(Judge0SubmissionRequest request) {
        Judge0SubmissionResponse response = restClient.post()
                .uri(properties.getBaseUrl() + "/submissions?base64_encoded=false&wait=false")
                .body(request)
                .retrieve()
                .body(Judge0SubmissionResponse.class);
        if (response == null || response.getToken() == null) {
            throw new IllegalStateException("Judge0 returned no token");
        }
        return response.getToken();
    }

    public String buildCallbackUrl(Long submissionId, int testCaseIndex) {
        return properties.getCallbackBaseUrl()
                + "/internal/callback?submissionId=" + submissionId
                + "&testCaseIndex=" + testCaseIndex;
    }
}
