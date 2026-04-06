package com.profitflow.application.port.in;

import com.profitflow.domain.Activity;

import java.util.List;

/**
 * Inbound port for managing the activity catalogue.
 *
 * <p>Activities are the intermediate cost objects in Activity-Based Costing:
 * resources are allocated <em>to</em> activities, and activities are allocated
 * <em>to</em> products.
 */
public interface ActivityCatalogUseCase {

    /** Returns all activities ordered by the repository's default sort. */
    List<Activity> listActivities();

    /**
     * Creates a new activity.
     *
     * @param name human-readable activity name, must not be blank
     * @return the persisted activity with a generated ID
     */
    Activity createActivity(String name);
}
