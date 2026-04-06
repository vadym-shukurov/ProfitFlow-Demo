package com.profitflow.infrastructure;

import com.profitflow.application.port.out.BusinessMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Micrometer-backed implementation of {@link BusinessMetricsPort}.
 *
 * <h2>Prometheus metrics exposed</h2>
 * <ul>
 *   <li>{@code profitflow_allocation_run_total} — counter, labels: {@code result=(success|failure)}</li>
 *   <li>{@code profitflow_allocation_run_duration_seconds} — histogram with P50/P95/P99</li>
 *   <li>{@code profitflow_cost_import_total} — counter, labels: {@code result}</li>
 *   <li>{@code profitflow_cost_created_total} — counter</li>
 *   <li>{@code profitflow_auth_login_total} — counter, labels: {@code result=(success|failure|locked)}</li>
 * </ul>
 *
 * All metrics are scraped at {@code GET /actuator/prometheus} by the Prometheus instance
 * defined in {@code docker-compose.yml}. SLO alert rules live in
 * {@code monitoring/prometheus/alert-rules.yml}.
 */
@Component
public class DefaultBusinessMetrics implements BusinessMetricsPort {

    private final MeterRegistry registry;

    private final Counter allocationRunSuccessCounter;
    private final Counter allocationRunFailureCounter;
    private final Timer   allocationRunTimer;

    private final Counter costImportSuccessCounter;
    private final Counter costImportFailureCounter;
    private final Counter costCreatedCounter;

    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter loginLockedCounter;

    private final Counter refreshTokenReuseCounter;

    private final Timer   aiSuggestTimer;
    private final Counter aiSuggestTimeoutCounter;
    private final Counter aiSuggestErrorCounter;

    public DefaultBusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
        allocationRunSuccessCounter = Counter.builder("profitflow.allocation.run")
                .description("Total allocation runs")
                .tag("result", "success")
                .register(registry);

        allocationRunFailureCounter = Counter.builder("profitflow.allocation.run")
                .description("Total allocation runs")
                .tag("result", "failure")
                .register(registry);

        allocationRunTimer = Timer.builder("profitflow.allocation.run.duration")
                .description("Allocation run execution time")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        costImportSuccessCounter = Counter.builder("profitflow.cost.import")
                .description("CSV cost import attempts")
                .tag("result", "success")
                .register(registry);

        costImportFailureCounter = Counter.builder("profitflow.cost.import")
                .description("CSV cost import attempts")
                .tag("result", "failure")
                .register(registry);

        costCreatedCounter = Counter.builder("profitflow.cost.created")
                .description("Individual cost records created via POST")
                .register(registry);

        loginSuccessCounter = Counter.builder("profitflow.auth.login")
                .description("Authentication attempts")
                .tag("result", "success")
                .register(registry);

        loginFailureCounter = Counter.builder("profitflow.auth.login")
                .description("Authentication attempts")
                .tag("result", "failure")
                .register(registry);

        loginLockedCounter = Counter.builder("profitflow.auth.login")
                .description("Authentication attempts")
                .tag("result", "locked")
                .register(registry);

        refreshTokenReuseCounter = Counter.builder("profitflow.auth.refresh.reuse")
                .description("Rotated refresh token presented again — possible theft; all sessions revoked")
                .register(registry);

        aiSuggestTimer = Timer.builder("profitflow.ai.suggest.duration")
                .description("AI expense suggestion latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        aiSuggestTimeoutCounter = Counter.builder("profitflow.ai.suggest")
                .description("AI suggestion outcomes")
                .tag("result", "timeout")
                .register(registry);

        aiSuggestErrorCounter = Counter.builder("profitflow.ai.suggest")
                .description("AI suggestion outcomes")
                .tag("result", "error")
                .register(registry);
    }

    @Override
    public void recordAllocationSuccess(long durationNanos) {
        allocationRunSuccessCounter.increment();
        allocationRunTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordAllocationFailure() {
        allocationRunFailureCounter.increment();
    }

    @Override
    public void recordCostImportSuccess() {
        costImportSuccessCounter.increment();
    }

    @Override
    public void recordCostImportFailure() {
        costImportFailureCounter.increment();
    }

    @Override
    public void recordCostCreated() {
        costCreatedCounter.increment();
    }

    @Override
    public void recordLoginSuccess() {
        loginSuccessCounter.increment();
    }

    @Override
    public void recordLoginFailure() {
        loginFailureCounter.increment();
    }

    @Override
    public void recordLoginLocked() {
        loginLockedCounter.increment();
    }

    @Override
    public void recordRefreshTokenReuseDetected() {
        refreshTokenReuseCounter.increment();
    }

    @Override
    public void recordAiSuggestionSuccess(long durationNanos) {
        aiSuggestTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordAiSuggestionTimeout() {
        aiSuggestTimeoutCounter.increment();
    }

    @Override
    public void recordAiSuggestionError() {
        aiSuggestErrorCounter.increment();
    }

    @Override
    public void recordOutboxEventDispatched(String eventType) {
        registry.counter("profitflow.outbox.dispatched", "type", eventType).increment();
    }
}
