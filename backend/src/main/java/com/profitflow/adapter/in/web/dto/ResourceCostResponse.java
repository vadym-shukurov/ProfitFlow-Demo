package com.profitflow.adapter.in.web.dto;

import java.math.BigDecimal;

/**
 * API response payload representing a single resource cost ledger entry.
 *
 * <p>Resource costs are the primary input stage of the ABC cost flow:
 * they represent raw GL spend (e.g. salaries, rent, depreciation) before
 * allocation to activities and ultimately products.
 *
 * @param id           unique identifier (UUID string)
 * @param label        human-readable description, e.g. {@code "Engineering Salaries Q1"}
 * @param amount       monetary amount in the associated currency
 * @param currencyCode ISO 4217 currency code, e.g. {@code "USD"}, {@code "EUR"}
 */
public record ResourceCostResponse(String id, String label, BigDecimal amount, String currencyCode) {
}
