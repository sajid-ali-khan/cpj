package com.arena.cpj.judge0;

import com.arena.cpj.submission.Verdict;

public final class Judge0StatusMapper {

    private Judge0StatusMapper() {
    }

    public static Verdict toVerdict(int statusId) {
        return switch (statusId) {
            case 3 -> Verdict.ACCEPTED;
            case 4 -> Verdict.WRONG_ANSWER;
            case 5 -> Verdict.TIME_LIMIT_EXCEEDED;
            case 6 -> Verdict.COMPILATION_ERROR;
            case 14 -> Verdict.MEMORY_LIMIT_EXCEEDED;
            case 7, 8, 9, 10, 11, 12, 13 -> Verdict.RUNTIME_ERROR;
            default -> Verdict.RUNTIME_ERROR;
        };
    }
}
