package com.arena.cpj.judge0;

import com.arena.cpj.config.Judge0Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class Judge0Client {

    private final Judge0Properties properties;

    /**
     * RestClient with explicit timeouts.
     * Read timeout is kept short because all calls are either
     * fire-and-forget (submit) or quick status polls (GET token/batch).
     */
    private final RestClient restClient = buildRestClient();

    private static RestClient buildRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);   // 5 s — fail fast if Judge0 is unreachable
        factory.setReadTimeout(10_000);     // 10 s — polls are fast; no wait=true
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    // ─── Poll constants ──────────────────────────────────────────────────────

    /** Milliseconds between each poll cycle. */
    private static final int POLL_INTERVAL_MS = 1_500;

    /**
     * Maximum number of poll attempts before giving up.
     * 40 × 1.5 s = 60 s ceiling — well above WALL_TIME_LIMIT (20 s) in judge0.conf.
     */
    private static final int MAX_POLLS = 40;

    /** Judge0 status IDs that mean the job is still in flight. */
    private static final int STATUS_IN_QUEUE   = 1;
    private static final int STATUS_PROCESSING = 2;

    // ─── Batch API ───────────────────────────────────────────────────────────

    /**
     * Submits all testcase requests to Judge0 in a single batch call and polls
     * {@code GET /submissions/batch} until every token has a final status.
     *
     * <p>One HTTP call to submit, one polling loop for all results — regardless
     * of how many testcases there are.
     *
     * @return list of final results in the same order as {@code requests}
     */
    public List<Judge0CallbackPayload> submitBatchAndWait(List<Judge0SubmissionRequest> requests) {
        // Step 1: send all testcases in one POST
        List<String> tokens = submitBatch(requests);
        log.debug("Batch submitted {} testcase(s). Tokens: {}", tokens.size(), tokens);

        // Step 2: poll until every token is resolved
        String tokenParam = String.join(",", tokens);
        for (int attempt = 0; attempt < MAX_POLLS; attempt++) {
            sleep(POLL_INTERVAL_MS);

            List<Judge0CallbackPayload> results = getBatch(tokenParam);

            boolean allDone = results.stream().allMatch(r -> {
                int id = r.getStatus() != null ? r.getStatus().getId() : 0;
                return id != STATUS_IN_QUEUE && id != STATUS_PROCESSING;
            });

            if (allDone) {
                log.debug("Batch resolved after {} poll(s)", attempt + 1);
                return results;
            }

            long pending = results.stream().filter(r -> {
                int id = r.getStatus() != null ? r.getStatus().getId() : 0;
                return id == STATUS_IN_QUEUE || id == STATUS_PROCESSING;
            }).count();
            log.debug("Batch poll {}/{}: {}/{} token(s) still pending",
                    attempt + 1, MAX_POLLS, pending, tokens.size());
        }

        throw new IllegalStateException(
                "Batch did not finish within the poll budget (" + MAX_POLLS + " attempts)");
    }

    /**
     * {@code POST /submissions/batch} — sends all requests at once, returns tokens.
     * The POST body is {@code {"submissions": [...]}} per the Judge0 API.
     */
    public List<String> submitBatch(List<Judge0SubmissionRequest> requests) {
        Judge0BatchRequest body = Judge0BatchRequest.builder()
                .submissions(requests)
                .build();

        // The response is a flat JSON array: [{token: "..."}, {token: "..."}, ...]
        // We use Judge0SubmissionResponse[] to deserialise it.
        Judge0SubmissionResponse[] response = restClient.post()
                .uri(properties.getBaseUrl() + "/submissions/batch?base64_encoded=false")
                .body(body)
                .retrieve()
                .body(Judge0SubmissionResponse[].class);

        if (response == null || response.length == 0) {
            throw new IllegalStateException("Judge0 batch submit returned no tokens");
        }
        return java.util.Arrays.stream(response)
                .map(Judge0SubmissionResponse::getToken)
                .collect(Collectors.toList());
    }

    /**
     * {@code GET /submissions/batch?tokens=t1,t2,...} — fetches current state of all tokens.
     */
    public List<Judge0CallbackPayload> getBatch(String commaSeparatedTokens) {
        Judge0BatchResponse response = restClient.get()
                .uri(properties.getBaseUrl()
                        + "/submissions/batch?tokens=" + commaSeparatedTokens
                        + "&base64_encoded=false")
                .retrieve()
                .body(Judge0BatchResponse.class);

        if (response == null || response.getSubmissions() == null) {
            throw new IllegalStateException("Judge0 batch GET returned null");
        }
        return response.getSubmissions();
    }

    // ─── Single-submission API (kept for reference / future use) ────────────

    /** Submit a single submission with {@code wait=false}, returns token. */
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

    /** Fetches the current state of a single submission by token. */
    public Judge0CallbackPayload getSubmission(String token) {
        Judge0CallbackPayload result = restClient.get()
                .uri(properties.getBaseUrl() + "/submissions/" + token + "?base64_encoded=false")
                .retrieve()
                .body(Judge0CallbackPayload.class);
        if (result == null) {
            throw new IllegalStateException("Judge0 returned null for token " + token);
        }
        return result;
    }

    public String buildCallbackUrl(Long submissionId, int testCaseIndex) {
        return properties.getCallbackBaseUrl()
                + "/internal/callback?submissionId=" + submissionId
                + "&testCaseIndex=" + testCaseIndex;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while polling Judge0", e);
        }
    }
}
