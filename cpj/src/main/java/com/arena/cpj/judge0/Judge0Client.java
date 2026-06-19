package com.arena.cpj.judge0;

import com.arena.cpj.config.Judge0Properties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
public class Judge0Client {

    private final Judge0Properties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Semaphore semaphore;

    public Judge0Client(Judge0Properties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.semaphore = new Semaphore(properties.getMaxConcurrentRequests());

        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
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
    private static final int STATUS_IN_QUEUE = 1;
    private static final int STATUS_PROCESSING = 2;

    // ─── Batch API ───────────────────────────────────────────────────────────

    /**
     * Submits all testcase requests to Judge0 in a single batch call and polls
     * {@code GET /submissions/batch} until every token has a final status.
     *
     * <p>
     * One HTTP call to submit, one polling loop for all results — regardless
     * of how many testcases there are.
     *
     * @return list of final results in the same order as {@code requests}
     */
    public List<Judge0CallbackPayload> submitBatchAndWait(List<Judge0SubmissionRequest> requests) {
        try {
            semaphore.acquire();
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a Judge0 execution slot", e);
        }
        
        try {
            // Step 1: send all testcases in one POST
            List<String> tokens = submitBatch(requests);
            log.info("Batch submitted {} testcase(s). Tokens: {}", tokens.size(), tokens);

            // Step 2: poll only the tokens still pending, until all are resolved
            Map<String, Judge0CallbackPayload> resolved = new LinkedHashMap<>();
            Set<String> pending = new LinkedHashSet<>(tokens);

            for (int attempt = 0; attempt < MAX_POLLS && !pending.isEmpty(); attempt++) {
                sleep(POLL_INTERVAL_MS);

                String tokenParam = String.join(",", pending);
                List<Judge0CallbackPayload> results = getBatch(tokenParam);

                for (Judge0CallbackPayload r : results) {
                    int id = r.getStatus() != null ? r.getStatus().getId() : 0;
                    boolean stillRunning = id == STATUS_IN_QUEUE || id == STATUS_PROCESSING;

                    if (!stillRunning) {
                        resolved.put(r.getToken(), r);
                        pending.remove(r.getToken());
                    }
                }

                if (pending.isEmpty()) {
                    log.info("Batch resolved after {} poll(s)", attempt + 1);
                    break;
                }

                log.info("Batch poll {}/{}: {}/{} token(s) still pending",
                        attempt + 1, MAX_POLLS, pending.size(), tokens.size());
            }

            if (!pending.isEmpty()) {
                throw new IllegalStateException(
                        "Batch did not finish within the poll budget (" + MAX_POLLS + " attempts)");
            }

            // Re-assemble in original submission order
            return tokens.stream()
                    .map(resolved::get)
                    .collect(Collectors.toList());
        } finally {
            semaphore.release();
        }
    }

    /**
     * {@code POST /submissions/batch} — sends all requests at once, returns tokens.
     * The POST body is {@code {"submissions": [...]}} per the Judge0 API.
     */
    public List<String> submitBatch(List<Judge0SubmissionRequest> requests) {
        List<Judge0SubmissionRequest> encodedRequests = requests.stream()
                .map(this::encodeRequest)
                .toList();

        Judge0BatchRequest body = Judge0BatchRequest.builder()
                .submissions(encodedRequests)
                .build();
        String bodyJson = "";
        try {
            bodyJson = objectMapper.writeValueAsString(body);
            log.debug("Sending batch submit request to Judge0: {}", bodyJson);
        } catch (Exception e) {
            log.warn("Failed to log batch submit request payload", e);
        }

        // The response is a flat JSON array: [{token: "..."}, {token: "..."}, ...]
        // We use Judge0SubmissionResponse[] to deserialise it.
        String rawResponse = restClient.post()
                .uri(properties.getBaseUrl() + "/submissions/batch?base64_encoded=true")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(bodyJson)
                .retrieve()
                .body(String.class);

        log.info("Received batch submit raw response from Judge0: {}", rawResponse);

        Judge0SubmissionResponse[] response;
        try {
            response = objectMapper.readValue(rawResponse, Judge0SubmissionResponse[].class);
        } catch (Exception e) {
            log.error("Failed to parse batch submit response", e);
            throw new IllegalStateException("Failed to parse Judge0 batch submit response", e);
        }

        if (response == null || response.length == 0) {
            throw new IllegalStateException("Judge0 batch submit returned no tokens");
        }
        return java.util.Arrays.stream(response)
                .map(Judge0SubmissionResponse::getToken)
                .collect(Collectors.toList());
    }

    /**
     * {@code GET /submissions/batch?tokens=t1,t2,...} — fetches current state of
     * all tokens.
     */
    public List<Judge0CallbackPayload> getBatch(String commaSeparatedTokens) {
        log.info("Polling batch status from Judge0 for tokens: {}", commaSeparatedTokens);
        String rawResponse = restClient.get()
                .uri(properties.getBaseUrl()
                        + "/submissions/batch?tokens=" + commaSeparatedTokens
                        + "&base64_encoded=true")
                .retrieve()
                .body(String.class);

        log.info("Received batch status raw response from Judge0: {}", rawResponse);

        Judge0BatchResponse response;
        try {
            response = objectMapper.readValue(rawResponse, Judge0BatchResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse batch status response", e);
            throw new IllegalStateException("Failed to parse Judge0 batch status response", e);
        }

        if (response == null || response.getSubmissions() == null) {
            throw new IllegalStateException("Judge0 batch GET returned null");
        }
        return decodePayloads(response.getSubmissions());
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

    private String encodeBase64(String value) {
        if (value == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeBase64(String value) {
        if (value == null) {
            return null;
        }
        try {
            // Use MimeDecoder to safely ignore newlines in the Base64 stream
            return new String(Base64.getMimeDecoder().decode(value.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("Value is not valid base64, returning original string: {}", value);
            return value;
        }
    }

    private Judge0SubmissionRequest encodeRequest(Judge0SubmissionRequest req) {
        return Judge0SubmissionRequest.builder()
                .sourceCode(encodeBase64(req.getSourceCode()))
                .languageId(req.getLanguageId())
                .stdin(encodeBase64(req.getStdin()))
                .expectedOutput(encodeBase64(req.getExpectedOutput()))
                .callbackUrl(req.getCallbackUrl())
                .cpuTimeLimit(req.getCpuTimeLimit())
                .memoryLimitKb(req.getMemoryLimitKb())
                .build();
    }

    private Judge0CallbackPayload decodePayload(Judge0CallbackPayload payload) {
        if (payload == null) {
            return null;
        }
        payload.setStdout(decodeBase64(payload.getStdout()));
        payload.setStderr(decodeBase64(payload.getStderr()));
        payload.setCompileOutput(decodeBase64(payload.getCompileOutput()));
        payload.setMessage(decodeBase64(payload.getMessage()));
        return payload;
    }

    private List<Judge0CallbackPayload> decodePayloads(List<Judge0CallbackPayload> payloads) {
        if (payloads == null) {
            return null;
        }
        payloads.forEach(this::decodePayload);
        return payloads;
    }
}
