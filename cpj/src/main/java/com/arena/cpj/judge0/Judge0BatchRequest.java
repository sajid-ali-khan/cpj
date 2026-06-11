package com.arena.cpj.judge0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Wrapper sent to {@code POST /submissions/batch}.
 * Judge0 expects {@code {"submissions": [...]}} as the request body.
 */
@Getter
@Builder
public class Judge0BatchRequest {

    @JsonProperty("submissions")
    private final List<Judge0SubmissionRequest> submissions;
}
