package com.arena.cpj.judge0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Judge0SubmissionRequest {

    @JsonProperty("source_code")
    private final String sourceCode;

    @JsonProperty("language_id")
    private final int languageId;

    private final String stdin;

    @JsonProperty("expected_output")
    private final String expectedOutput;

    @JsonProperty("callback_url")
    private final String callbackUrl;

    @JsonProperty("cpu_time_limit")
    private final double cpuTimeLimit;

    @JsonProperty("memory_limit")
    private final int memoryLimitKb;
}
