package com.profitflow.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "resource_activity_rule",
        uniqueConstraints = @UniqueConstraint(columnNames = {"resource_id", "activity_id"})
)
public class ResourceActivityRuleEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private ResourceCostEntity resource;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private ActivityEntity activity;

    @Column(name = "driver_weight", nullable = false, precision = 19, scale = 4)
    private BigDecimal driverWeight;

    protected ResourceActivityRuleEntity() {
    }

    public ResourceActivityRuleEntity(UUID id, ResourceCostEntity resource,
                                      ActivityEntity activity, BigDecimal driverWeight) {
        this.id = id;
        this.resource = resource;
        this.activity = activity;
        this.driverWeight = driverWeight;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ResourceCostEntity getResource() {
        return resource;
    }

    public void setResource(ResourceCostEntity resource) {
        this.resource = resource;
    }

    public ActivityEntity getActivity() {
        return activity;
    }

    public void setActivity(ActivityEntity activity) {
        this.activity = activity;
    }

    public BigDecimal getDriverWeight() {
        return driverWeight;
    }

    public void setDriverWeight(BigDecimal driverWeight) {
        this.driverWeight = driverWeight;
    }
}
