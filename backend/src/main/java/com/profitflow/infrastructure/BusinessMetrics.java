package com.profitflow.infrastructure;

import com.profitflow.application.port.out.BusinessMetricsPort;

/**
 * Deprecated bridge kept for backward compatibility.
 *
 * <p>All callers should migrate to {@link BusinessMetricsPort} (application layer port).
 * This interface will be removed in a future release.
 *
 * @deprecated Use {@link BusinessMetricsPort} — the canonical outbound port for metrics.
 *             This alias exists only to avoid a single large-refactor commit; application
 *             services now declare {@code BusinessMetricsPort} as their dependency type.
 */
@Deprecated(since = "1.1", forRemoval = true)
public interface BusinessMetrics extends BusinessMetricsPort {
}
