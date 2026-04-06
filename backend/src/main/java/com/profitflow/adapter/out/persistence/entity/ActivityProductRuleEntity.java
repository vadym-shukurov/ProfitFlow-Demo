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
        name = "activity_product_rule",
        uniqueConstraints = @UniqueConstraint(columnNames = {"activity_id", "product_id"})
)
public class ActivityProductRuleEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private ActivityEntity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "driver_weight", nullable = false, precision = 19, scale = 4)
    private BigDecimal driverWeight;

    protected ActivityProductRuleEntity() {
    }

    public ActivityProductRuleEntity(UUID id, ActivityEntity activity, ProductEntity product, BigDecimal driverWeight) {
        this.id = id;
        this.activity = activity;
        this.product = product;
        this.driverWeight = driverWeight;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ActivityEntity getActivity() {
        return activity;
    }

    public void setActivity(ActivityEntity activity) {
        this.activity = activity;
    }

    public ProductEntity getProduct() {
        return product;
    }

    public void setProduct(ProductEntity product) {
        this.product = product;
    }

    public BigDecimal getDriverWeight() {
        return driverWeight;
    }

    public void setDriverWeight(BigDecimal driverWeight) {
        this.driverWeight = driverWeight;
    }
}
