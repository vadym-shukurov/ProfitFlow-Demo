package com.profitflow.adapter.in.web;

import com.profitflow.adapter.in.web.dto.ActivityResponse;
import com.profitflow.adapter.in.web.dto.CreateNamedEntityRequest;
import com.profitflow.application.port.in.ActivityCatalogUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST adapter for the activity (cost-pool) catalogue.
 *
 * <p>Activities are the intermediate stage in the ABC model: resource costs flow
 * <em>into</em> activities, and then activity costs flow <em>out</em> to products.
 */
@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {

    private final ActivityCatalogUseCase activities;

    public ActivityController(ActivityCatalogUseCase activities) {
        this.activities = activities;
    }

    /** Returns all registered activities. */
    @GetMapping
    public List<ActivityResponse> list() {
        return activities.listActivities().stream()
                .map(WebMapper::toResponse)
                .toList();
    }

    /**
     * Creates a new activity.
     *
     * @return {@code 201 Created} with the persisted activity
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityResponse create(@Valid @RequestBody CreateNamedEntityRequest request) {
        return WebMapper.toResponse(activities.createActivity(request.name()));
    }
}
