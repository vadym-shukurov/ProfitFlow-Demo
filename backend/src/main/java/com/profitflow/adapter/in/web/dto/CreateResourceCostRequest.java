package com.profitflow.adapter.in.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for creating a single {@code ResourceCost} via POST.
 *
 * <p>All string fields have explicit {@code @Size} upper bounds to prevent
 * oversized-payload attacks that could exhaust database column storage or
 * cause excessive memory allocation.
 */
public record CreateResourceCostRequest(
        /** Human-readable cost label, e.g. "AWS Infrastructure". */
        @NotBlank @Size(max = 255) String label,

        /** Non-negative monetary amount. Max 15 integer digits, 4 decimal places. */
        @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal amount,

        /** ISO 4217 currency code, e.g. "USD". Defaults to "USD" when omitted. */
        @Size(min = 3, max = 3) String currencyCode
) {
}
