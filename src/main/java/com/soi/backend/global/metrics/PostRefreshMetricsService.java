package com.soi.backend.global.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class PostRefreshMetricsService {

    private static final String METRIC_NAME = "soi.post.refresh.delay";

    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final ConcurrentMap<Long, PendingPostCreation> pendingPostCreations = new ConcurrentHashMap<>();

    public void recordPostCreated(Long userId, String postType) {
        if (userId == null) {
            return;
        }

        pendingPostCreations.put(userId, new PendingPostCreation(clock.instant(), normalizeTagValue(postType)));
    }

    public void recordPostRefresh(Long userId, String source) {
        if (userId == null) {
            return;
        }

        PendingPostCreation pending = pendingPostCreations.remove(userId);
        if (pending == null) {
            return;
        }

        Duration delay = Duration.between(pending.createdAt(), clock.instant());
        if (delay.isNegative()) {
            return;
        }

        Timer.builder(METRIC_NAME)
                .description("Delay between creating a post and the same user's next post list refresh")
                .tag("source", normalizeTagValue(source))
                .tag("post_type", pending.postType())
                .serviceLevelObjectives(
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(60)
                )
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(delay);
    }

    private String normalizeTagValue(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value;
    }

    private record PendingPostCreation(Instant createdAt, String postType) {
    }
}
