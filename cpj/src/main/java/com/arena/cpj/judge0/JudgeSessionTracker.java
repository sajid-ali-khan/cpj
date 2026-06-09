package com.arena.cpj.judge0;

import com.arena.cpj.problem.TestCase;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory state for sequential multi-test-case judging.
 * A submission stays PENDING if the server restarts mid-judge.
 */
@Component
public class JudgeSessionTracker {

    private final ConcurrentHashMap<Long, JudgeSession> sessions = new ConcurrentHashMap<>();

    public void start(Long submissionId, List<TestCase> testCases) {
        sessions.put(submissionId, new JudgeSession(testCases, 0));
    }

    public JudgeSession get(Long submissionId) {
        return sessions.get(submissionId);
    }

    public void advance(Long submissionId) {
        sessions.computeIfPresent(submissionId, (id, session) -> session.advance());
    }

    public void remove(Long submissionId) {
        sessions.remove(submissionId);
    }

    public record JudgeSession(List<TestCase> testCases, int currentIndex) {

        public TestCase currentTestCase() {
            return testCases.get(currentIndex);
        }

        public boolean hasMoreAfterCurrent() {
            return currentIndex + 1 < testCases.size();
        }

        public JudgeSession advance() {
            return new JudgeSession(testCases, currentIndex + 1);
        }
    }
}
