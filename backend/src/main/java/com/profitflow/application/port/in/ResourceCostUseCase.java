package com.profitflow.application.port.in;

import com.profitflow.domain.ResourceCost;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inbound port for managing the resource cost catalogue.
 *
 * <p>Resource costs are the raw monetary inputs to the ABC allocation engine.
 * Each cost has a human-readable label (e.g. "Direct Labour"), a monetary
 * amount, and a currency code.
 */
public interface ResourceCostUseCase {

    /** Returns all resource costs ordered by the repository's default sort. */
    List<ResourceCost> listCosts();

    /**
     * Creates a single resource cost entry.
     *
     * @param label        human-readable name, must not be blank
     * @param amount       positive monetary amount
     * @param currencyCode ISO 4217 currency code (e.g. {@code "USD"})
     * @return the persisted resource cost with a generated ID
     */
    ResourceCost createCost(String label, BigDecimal amount, String currencyCode);

    /**
     * Deletes a resource cost entry.
     *
     * @param id resource cost ID
     * @throws com.profitflow.application.exception.ResourceNotFoundException
     *         if the resource cost does not exist
     * @throws com.profitflow.application.exception.ResourceConflictException
     *         if the resource cost is referenced by other records
     */
    void deleteCost(String id);

    /**
     * Bulk-imports resource costs from CSV content.
     *
     * <p>Rows with leading formula-injection characters ({@code =}, {@code +},
     * {@code -}, {@code @}) are sanitised before persistence.
     *
     * @param csvContent raw CSV text (header row: {@code label,amount,currency})
     * @return list of successfully imported resource costs
     */
    List<ResourceCost> importCostsFromCsv(String csvContent);
}
