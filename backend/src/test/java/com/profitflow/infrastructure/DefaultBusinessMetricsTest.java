package com.profitflow.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultBusinessMetricsTest {

    @Test
    void recordsExpectedCountersAndTimers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DefaultBusinessMetrics metrics = new DefaultBusinessMetrics(registry);

        metrics.recordAllocationSuccess(1_000_000);
        metrics.recordAllocationFailure();
        metrics.recordCostImportSuccess();
        metrics.recordCostImportFailure();
        metrics.recordCostCreated();
        metrics.recordLoginSuccess();
        metrics.recordLoginFailure();
        metrics.recordLoginLocked();
        metrics.recordRefreshTokenReuseDetected();
        metrics.recordAiSuggestionSuccess(2_000_000);
        metrics.recordAiSuggestionTimeout();
        metrics.recordAiSuggestionError();
        metrics.recordOutboxEventDispatched("UserLoggedIn");

        Counter allocationSuccess = registry.find("profitflow.allocation.run").tag("result", "success").counter();
        Counter allocationFailure = registry.find("profitflow.allocation.run").tag("result", "failure").counter();
        Timer allocationTimer = registry.find("profitflow.allocation.run.duration").timer();
        assertThat(allocationSuccess).isNotNull();
        assertThat(allocationFailure).isNotNull();
        assertThat(allocationTimer).isNotNull();
        assertThat(allocationSuccess.count()).isEqualTo(1.0);
        assertThat(allocationFailure.count()).isEqualTo(1.0);
        assertThat(allocationTimer.count()).isEqualTo(1);

        assertThat(registry.find("profitflow.cost.import").tag("result", "success").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("profitflow.cost.import").tag("result", "failure").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("profitflow.cost.created").counter().count()).isEqualTo(1.0);

        assertThat(registry.find("profitflow.auth.login").tag("result", "success").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("profitflow.auth.login").tag("result", "failure").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("profitflow.auth.login").tag("result", "locked").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("profitflow.auth.refresh.reuse").counter().count()).isEqualTo(1.0);

        Timer aiTimer = registry.find("profitflow.ai.suggest.duration").timer();
        assertThat(aiTimer).isNotNull();
        assertThat(aiTimer.count()).isEqualTo(1);
        assertThat(registry.find("profitflow.ai.suggest").tag("result", "timeout").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("profitflow.ai.suggest").tag("result", "error").counter().count()).isEqualTo(1.0);

        assertThat(registry.find("profitflow.outbox.dispatched").tag("type", "UserLoggedIn").counter().count())
                .isEqualTo(1.0);
    }
}

