package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.entity.DomainEventOutboxEntity;
import com.profitflow.adapter.out.persistence.jpa.DomainEventOutboxEntityRepository;
import com.profitflow.application.port.out.DomainEventDispatchPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls unpublished outbox rows, dispatches them, then marks {@code published_at}.
 *
 * <p>Uses pessimistic locking on the batch query so concurrent instances serialise on
 * the same rows instead of double-delivering.
 */
@Component
@ConditionalOnProperty(
        name = "profitflow.outbox.dispatch.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DomainEventOutboxProcessor {

    private static final int BATCH_SIZE = 50;

    private final DomainEventOutboxEntityRepository outboxRepository;
    private final DomainEventDispatchPort             dispatch;

    public DomainEventOutboxProcessor(
            DomainEventOutboxEntityRepository outboxRepository,
            DomainEventDispatchPort dispatch) {
        this.outboxRepository = outboxRepository;
        this.dispatch         = dispatch;
    }

    /**
     * Fixed delay so the next run starts after the previous completes (no overlap pile-up).
     */
    @Scheduled(fixedDelayString = "${profitflow.outbox.poll-interval-ms:5000}")
    @Transactional
    public void processPendingBatch() {
        List<DomainEventOutboxEntity> pending = outboxRepository.findPendingLocked(
                PageRequest.of(0, BATCH_SIZE));
        Instant now = Instant.now();
        for (DomainEventOutboxEntity row : pending) {
            dispatch.dispatch(row.getEventType(), row.getPayloadJson());
            row.setPublishedAt(now);
            outboxRepository.save(row);
        }
    }
}
