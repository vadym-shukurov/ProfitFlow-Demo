package com.profitflow.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Allocation rule from an activity cost pool to a product/service. */
public record ActivityProductRuleDto(
        @NotBlank @Size(max = 36) String activityId,
        @NotBlank @Size(max = 36) String productId,

        /** Driver weight — must be strictly positive; zero weights are meaningless. */
        @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 10, fraction = 6)
        BigDecimal driverWeight
) {
}
