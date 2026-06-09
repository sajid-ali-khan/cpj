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

    public String submit(Judge0SubmissionRequest request) {
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
