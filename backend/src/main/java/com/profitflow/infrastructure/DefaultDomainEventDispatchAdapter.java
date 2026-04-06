package com.profitflow.infrastructure;

import com.profitflow.application.port.out.BusinessMetricsPort;
import com.profitflow.application.port.out.DomainEventDispatchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default outbox handler: structured log line plus metrics until a message bus is wired.
 */
@Component
public class DefaultDomainEventDispatchAdapter implements DomainEventDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultDomainEventDispatchAdapter.class);

    private final BusinessMetricsPort metrics;

    public DefaultDomainEventDispatchAdapter(BusinessMetricsPort metrics) {
        this.metrics = metrics;
    }

    @Override
    public void dispatch(String eventType, String payloadJson) {
        log.info("domain_event type={} payload={}", eventType, payloadJson);
        metrics.recordOutboxEventDispatched(eventType);
    }
}
