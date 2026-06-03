package com.soi.backend.global.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PostRefreshMetricsServiceTest {

    @Test
    void recordsDelayBetweenPostCreationAndNextRefreshForSameUser() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MutableClock clock = new MutableClock(Instant.parse("2026-06-04T00:00:00Z"));
        PostRefreshMetricsService service = new PostRefreshMetricsService(registry, clock);

        service.recordPostCreated(1L, "PHOTO");
        clock.advance(Duration.ofSeconds(3));
        service.recordPostRefresh(1L, "feed");

        Timer timer = registry.find("soi.post.refresh.delay")
                .tag("source", "feed")
                .tag("post_type", "PHOTO")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(3.0);
    }

    @Test
    void recordsOnlyFirstRefreshAfterPostCreation() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MutableClock clock = new MutableClock(Instant.parse("2026-06-04T00:00:00Z"));
        PostRefreshMetricsService service = new PostRefreshMetricsService(registry, clock);

        service.recordPostCreated(1L, "VIDEO");
        clock.advance(Duration.ofSeconds(2));
        service.recordPostRefresh(1L, "category");
        clock.advance(Duration.ofSeconds(5));
        service.recordPostRefresh(1L, "category");

        Timer timer = registry.find("soi.post.refresh.delay")
                .tag("source", "category")
                .tag("post_type", "VIDEO")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(2.0);
    }

    @Test
    void ignoresRefreshWhenUserHasNoPendingPostCreation() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PostRefreshMetricsService service = new PostRefreshMetricsService(
                registry,
                Clock.fixed(Instant.parse("2026-06-04T00:00:00Z"), ZoneId.of("UTC"))
        );

        service.recordPostRefresh(1L, "feed");

        assertThat(registry.find("soi.post.refresh.delay").timer()).isNull();
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
