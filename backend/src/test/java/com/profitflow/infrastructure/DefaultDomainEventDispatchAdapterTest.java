package com.profitflow.infrastructure;

import com.profitflow.application.port.out.BusinessMetricsPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultDomainEventDispatchAdapterTest {

    @Mock
    private BusinessMetricsPort metrics;

    @Test
    void dispatchRecordsMetricWithEventType() {
        DefaultDomainEventDispatchAdapter adapter = new DefaultDomainEventDispatchAdapter(metrics);
        adapter.dispatch("ALLOCATION_RUN_COMPLETED", "{\"runNumber\":1}");
        verify(metrics).recordOutboxEventDispatched("ALLOCATION_RUN_COMPLETED");
    }
}
