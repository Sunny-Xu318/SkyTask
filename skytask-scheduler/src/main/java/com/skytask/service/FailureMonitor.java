package com.skytask.service;

import com.skytask.config.SchedulerDynamicProperties;
import com.skytask.service.event.TaskFailureEscalationEvent;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FailureMonitor {

    private static final long WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private final SchedulerDynamicProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock = Clock.systemUTC();
    private final Map<String, FailureStats> stats = new ConcurrentHashMap<>();

    public void recordResult(long taskId, Long tenantId, String taskName, boolean success) {
        if (tenantId == null) {
            return;
        }
        long now = clock.millis();
        String key = tenantId + ":" + taskId;
        FailureStats failureStats = stats.computeIfAbsent(key, k -> new FailureStats());
        failureStats.record(success, now);

        long total = failureStats.getTotal();
        if (total < Math.max(1, properties.getAlert().getAutoDegradeMinSamples())) {
            return;
        }
        double failureRate = failureStats.failureRate();
        if (failureRate < properties.getAlert().getFailureRateThreshold()) {
            return;
        }
        long cooldownMs = TimeUnit.MINUTES.toMillis(Math.max(0, properties.getAlerting().getEscalationCooldownMinutes()));
        if (!failureStats.canEscalate(now, cooldownMs)) {
            return;
        }

        log.warn("Task {} in tenant {} failure rate {:.2f}% over {} samples - triggering auto-degrade",
                taskName, tenantId, failureRate, total);
        eventPublisher.publishEvent(
                new TaskFailureEscalationEvent(this, taskId, tenantId, taskName, failureRate, total));
        failureStats.markEscalated(now);
    }

    private static final class FailureStats {
        private long windowStart;
        private long lastEscalation;
        private int successCount;
        private int failureCount;

        synchronized void record(boolean success, long now) {
            if (windowStart == 0 || now - windowStart > WINDOW_MILLIS) {
                reset(now);
            }
            if (success) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        synchronized long getTotal() {
            return successCount + failureCount;
        }

        synchronized double failureRate() {
            long total = getTotal();
            if (total == 0) {
                return 0.0;
            }
            return (failureCount * 100.0) / total;
        }

        synchronized boolean canEscalate(long now, long cooldownMs) {
            return now - lastEscalation >= cooldownMs;
        }

        synchronized void markEscalated(long now) {
            lastEscalation = now;
        }

        private void reset(long now) {
            windowStart = now;
            successCount = 0;
            failureCount = 0;
        }
    }
}
