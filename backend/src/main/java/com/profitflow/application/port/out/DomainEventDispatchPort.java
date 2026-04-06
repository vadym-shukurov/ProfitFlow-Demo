package com.profitflow.application.port.out;

/**
 * Delivers a transactional-outbox event to downstream consumers (log, message bus, webhook).
 *
 * <p>Implementations must be idempotent where possible; the processor retries the same
 * row if dispatch fails before {@code published_at} is committed.
 */
public interface DomainEventDispatchPort {

    /**
     * Handles one outbox event. Implementations should not throw for transient external
     * failures unless the event must be retried intact — throwing rolls back the mark
     * as published for this batch.
     *
     * @param eventType    stable type label (e.g. {@code ALLOCATION_RUN_COMPLETED})
     * @param payloadJson  JSON payload as stored in the outbox
     */
    void dispatch(String eventType, String payloadJson);
}
