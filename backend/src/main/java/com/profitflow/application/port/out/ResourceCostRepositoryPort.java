package com.profitflow.application.port.out;

import com.profitflow.domain.ResourceCost;

import java.util.List;

/**
 * Outbound port for persisting and querying {@link ResourceCost} entries.
 *
 * <p>Implementations are provided by the {@code adapter.out.persistence} package
 * and wired into the application services via Spring's dependency injection.
 */
public interface ResourceCostRepositoryPort {

    /** Returns all persisted resource costs ordered by the repository's default sort. */
    List<ResourceCost> findAll();

    /**
     * Persists a new or updated resource cost.
     *
     * @param resourceCost domain object to save
     * @return the saved resource cost (may be a new instance with a generated ID)
     */
    ResourceCost save(ResourceCost resourceCost);
}
