package com.profitflow.application.port.out;

/**
 * Outbound port for recording application-level business metrics.
 *
 * <h2>Why an outbound port?</h2>
 * Application services need to record metrics but must not depend on
 * Micrometer/Prometheus classes directly — that is infrastructure detail.
 * By depending on this port, the application layer stays clean and tests
 * can inject a simple no-op or counting stub without a {@code MeterRegistry}.
 *
 * <h2>SLO targets tracked through this port</h2>
 * <ul>
 *   <li><b>Availability</b>: ≥ 99.5% of allocation runs succeed.</li>
 *   <li><b>Latency (P99)</b>: allocation run completes in ≤ 5 s.</li>
 *   <li><b>Error rate</b>: API error rate ≤ 0.5%.</li>
 *   <li><b>Import success</b>: ≥ 99% of CSV imports succeed.</li>
 * </ul>
 */
public interface BusinessMetricsPort {

    /** Records a successful allocation run with the elapsed time in nanoseconds. */
    void recordAllocationSuccess(long durationNanos);

    /** Records a failed allocation run. */
    void recordAllocationFailure();

    /** Records a successful CSV import. */
    void recordCostImportSuccess();

    /** Records a failed CSV import. */
    void recordCostImportFailure();

    /** Records a new cost being created via the API. */
    void recordCostCreated();

    /** Records a successful login. */
    void recordLoginSuccess();

    /** Records a failed login attempt (wrong credentials). */
    void recordLoginFailure();

    /** Records a login attempt against a locked account. */
    void recordLoginLocked();

    /**
     * A refresh token that was already rotated was presented again — possible theft.
     * All sessions for that user should be revoked (handled by caller).
     */
    void recordRefreshTokenReuseDetected();

    /** AI suggest completed within the configured timeout. */
    void recordAiSuggestionSuccess(long durationNanos);

    /** AI suggest exceeded the wall-clock timeout (fallback returned). */
    void recordAiSuggestionTimeout();

    /** AI suggest failed with an unexpected error (fallback returned). */
    void recordAiSuggestionError();

    /** One transactional outbox row was handed to {@code DomainEventDispatchPort}. */
    void recordOutboxEventDispatched(String eventType);
}
