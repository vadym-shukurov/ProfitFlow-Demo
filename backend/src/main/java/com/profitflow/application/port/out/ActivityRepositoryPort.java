package com.profitflow.application.port.out;

import com.profitflow.domain.Activity;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link Activity} reference data.
 *
 * <p>Implementations are provided by the {@code adapter.out.persistence} package
 * and wired into the application services via Spring's dependency injection.
 */
public interface ActivityRepositoryPort {

    /** Returns all persisted activities ordered by the repository's default sort. */
    List<Activity> findAll();

    /**
     * Looks up a single activity by its string ID.
     *
     * <p>Used by persistence adapters to resolve foreign-key references in
     * allocation rule rows. Not exposed at the HTTP API level.
     *
     * @param id UUID string of the activity
     * @return the activity, or {@link Optional#empty()} if not found
     */
    Optional<Activity> findById(String id);

    /**
     * Persists a new or updated {@link Activity}.
     *
     * @param activity domain object to save
     * @return the saved activity (may be a new instance with a generated ID)
     */
    Activity save(Activity activity);
}
