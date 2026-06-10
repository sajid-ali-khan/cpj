package com.arena.cpj.event;

import com.arena.cpj.leaderboard.dto.LeaderboardEntryDto;
import com.arena.cpj.submission.dto.VerdictEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseService {

    private static final long EMITTER_TIMEOUT_MS = 30L * 60 * 1000;

    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        register(userId, emitter);
        return emitter;
    }

    private void register(Long userId, SseEmitter emitter) {
        emitters.compute(userId, (id, existing) -> {
            if (existing != null) {
                existing.complete();
            }
            return emitter;
        });

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(ex -> remove(userId, emitter));
    }

    private void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, current) -> current == emitter ? null : current);
    }

    public void sendVerdict(Long userId, VerdictEventDto event) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("verdict").data(event));
        } catch (IOException ex) {
            log.warn("Failed to send verdict SSE to user {}", userId, ex);
            remove(userId, emitter);
        }
    }

    public void broadcastLeaderboard(List<LeaderboardEntryDto> entries) {
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("leaderboard").data(entries));
            } catch (IOException ex) {
                log.warn("Failed to broadcast leaderboard SSE to user {}", userId, ex);
                remove(userId, emitter);
            }
        });
    }

    public void broadcastContestEvent(Object event) {
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("contest").data(event));
            } catch (IOException ex) {
                log.warn("Failed to broadcast contest SSE to user {}", userId, ex);
                remove(userId, emitter);
            }
        });
    }
}
