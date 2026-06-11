package com.arena.cpj.judge0;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response from {@code GET /submissions/batch?tokens=...}.
 * Judge0 returns {@code {"submissions": [...full submission objects...]}}
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Judge0BatchResponse {

    @JsonProperty("submissions")
    private List<Judge0CallbackPayload> submissions;
}
