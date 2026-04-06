package com.profitflow.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Allocation rule from a resource cost centre to an activity cost pool. */
public record ResourceActivityRuleDto(
        @NotBlank @Size(max = 36) String resourceId,
        @NotBlank @Size(max = 36) String activityId,

        /** Driver weight — must be strictly positive; zero weights are meaningless. */
        @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 10, fraction = 6)
        BigDecimal driverWeight
) {
}
