package com.profitflow.application.service;

import com.profitflow.application.exception.InvalidInputException;
import com.profitflow.application.port.in.ActivityCatalogUseCase;
import com.profitflow.application.port.out.ActivityRepositoryPort;
import com.profitflow.domain.Activity;
import com.profitflow.application.cache.CacheNames;
import com.profitflow.application.audit.AuditedOperation;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Application service for the Activity reference catalogue.
 *
 * Activities are reference data that rarely change; reads are cached under
 * {@value CacheNames#ACTIVITIES} with a 60-second TTL.
 */
@Service
public class ActivityCatalogService implements ActivityCatalogUseCase {

    private final ActivityRepositoryPort activities;

    public ActivityCatalogService(ActivityRepositoryPort activities) {
        this.activities = activities;
    }

    /**
     * Returns all activities, served from cache when possible.
     * The cache is invalidated whenever a new activity is created.
     */
    @Override
    @Cacheable(CacheNames.ACTIVITIES)
    public List<Activity> listActivities() {
        return activities.findAll();
    }

    /**
     * Creates and persists a new activity. Evicts the activities cache.
     *
     * @param name non-blank activity name
     * @return the saved {@link Activity} with its generated ID
     * @throws InvalidInputException if {@code name} is blank
     */
    @Override
    @AuditedOperation(action = "ACTIVITY_CREATED", entityType = "Activity",
                      entityIdSpEL = "#result.id()")
    @CacheEvict(value = CacheNames.ACTIVITIES, allEntries = true)
    public Activity createActivity(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("Activity name is required");
        }
        Activity activity = new Activity(UUID.randomUUID().toString(), name.strip());
        return activities.save(activity);
    }
}
