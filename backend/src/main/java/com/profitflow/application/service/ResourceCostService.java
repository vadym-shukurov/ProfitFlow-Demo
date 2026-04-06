package com.profitflow.application.service;

import com.profitflow.application.exception.InvalidInputException;
import com.profitflow.application.port.in.ResourceCostUseCase;
import com.profitflow.application.port.out.ResourceCostRepositoryPort;
import com.profitflow.domain.Money;
import com.profitflow.domain.ResourceCost;
import com.profitflow.application.port.out.BusinessMetricsPort;
import com.profitflow.application.cache.CacheNames;
import com.profitflow.application.audit.AuditedOperation;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/**
 * Application service that manages the raw cost ledger.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Enforce label/amount business rules and generate identifiers.</li>
 *   <li>Delegate persistence to {@link ResourceCostRepositoryPort}.</li>
 *   <li>Apply CSV injection defence during bulk import.</li>
 *   <li>Cache the full cost list to reduce database round-trips under high traffic.</li>
 *   <li>Record business metrics for SLI/SLO tracking.</li>
 * </ul>
 *
 * <h2>Cache strategy</h2>
 * {@link #listCosts()} is cached under {@value CacheNames#RESOURCE_COSTS}.
 * Any write operation ({@code createCost}, {@code importCostsFromCsv}) evicts the
 * cache so the next {@code listCosts} call fetches fresh data.
 */
@Service
public class ResourceCostService implements ResourceCostUseCase {

    private final ResourceCostRepositoryPort resourceCosts;
    private final BusinessMetricsPort        metrics;

    /**
     * Self-reference injected lazily to enable Spring AOP proxy-through-self calls
     * from {@link #importCostsFromCsv} to {@link #createCost}.
     *
     * <p>Without this, calling {@code createCost(...)} directly inside
     * {@code importCostsFromCsv} bypasses the Spring proxy, meaning the
     * {@code @AuditedOperation} and {@code @CacheEvict} on {@code createCost}
     * would not fire for each imported row. By routing the call through the
     * injected proxy, all per-row annotations execute normally.
     *
     * <p>The outer {@code importCostsFromCsv} already carries its own
     * {@code @AuditedOperation(critical=true)} and {@code @CacheEvict},
     * so the per-row auditing is an additional fine-grained trail suitable for
     * compliance requirements.
     */
    @Autowired
    @Lazy
    private ResourceCostService self;

    public ResourceCostService(
            ResourceCostRepositoryPort resourceCosts,
            BusinessMetricsPort metrics) {
        this.resourceCosts = resourceCosts;
        this.metrics       = metrics;
    }

    /**
     * Returns all persisted resource costs.
     * The result is served from the {@value CacheNames#RESOURCE_COSTS} Caffeine
     * cache for up to 60 seconds, limiting database queries under concurrent read load.
     */
    @Override
    @Cacheable(CacheNames.RESOURCE_COSTS)
    public List<ResourceCost> listCosts() {
        return resourceCosts.findAll();
    }

    /**
     * Creates and persists a single resource cost.
     * Evicts the {@value CacheNames#RESOURCE_COSTS} cache so subsequent list
     * requests reflect the new entry.
     *
     * @param label        human-readable GL line description; must not be blank
     * @param amount       non-negative monetary value
     * @param currencyCode ISO 4217 currency code; defaults to {@code "USD"} if null/blank
     * @return the saved {@link ResourceCost} with its generated ID
     * @throws InvalidInputException if {@code label} is blank or {@code amount} is negative
     */
    @Override
    @AuditedOperation(action = "RESOURCE_COST_CREATED", entityType = "ResourceCost",
                      entityIdSpEL = "id()", critical = true)
    @CacheEvict(value = CacheNames.RESOURCE_COSTS, allEntries = true)
    public ResourceCost createCost(String label, BigDecimal amount, String currencyCode) {
        if (label == null || label.isBlank()) {
            throw new InvalidInputException("Resource cost label must not be blank");
        }
        Currency currency = parseCurrency(currencyCode);
        Money money = new Money(amount, currency);
        ResourceCost rc = new ResourceCost(UUID.randomUUID().toString(), label.strip(), money);
        ResourceCost saved = resourceCosts.save(rc);
        metrics.recordCostCreated();
        return saved;
    }

    /**
     * Bulk-imports resource costs from a CSV string using an RFC 4180 compliant parser.
     *
     * <h2>Accepted formats</h2>
     * <pre>
     *   label,amount            ← two columns, USD default
     *   label,amount,currency   ← three columns with ISO 4217 code
     * </pre>
     *
     * <p>An optional header row is detected by checking if the first record's first
     * value contains the word {@code "label"} (case-insensitive).
     *
     * <p>The entire import runs inside a single transaction so that a malformed row
     * rolls back all preceding rows (atomicity). The cache is evicted on success.
     *
     * <p>Uses Apache Commons CSV for proper RFC 4180 handling — correctly parses
     * quoted fields, embedded commas, and escaped quotes that naive {@code split(",")}
     * implementations would mangle.
     *
     * @param csvContent raw CSV text; blank input returns an empty list
     * @return list of persisted {@link ResourceCost} objects in CSV row order
     * @throws InvalidInputException if any row is malformed or has a non-numeric amount
     */
    @Override
    @Transactional
    @AuditedOperation(action = "RESOURCE_COSTS_IMPORTED", entityType = "ResourceCost",
                      critical = true)
    @CacheEvict(value = CacheNames.RESOURCE_COSTS, allEntries = true)
    public List<ResourceCost> importCostsFromCsv(String csvContent) {
        if (csvContent == null || csvContent.isBlank()) {
            return List.of();
        }

        List<ResourceCost> created = new ArrayList<>();
        try {
            CSVFormat format = CSVFormat.RFC4180.builder()
                    .setIgnoreSurroundingSpaces(true)
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .build();

            try (CSVParser parser = format.parse(new StringReader(csvContent))) {
                int rowNumber = 0;
                for (CSVRecord record : parser) {
                    rowNumber++;

                    // Skip header row (first row whose first value looks like a column name)
                    if (rowNumber == 1 && isHeaderRecord(record)) {
                        continue;
                    }

                    if (record.size() < 2) {
                        throw new InvalidInputException(
                                "CSV row " + rowNumber + " must have at least 2 columns "
                                + "(label,amount) but had " + record.size());
                    }

                    String label = stripCsvInjectionChars(record.get(0));
                    BigDecimal amount;
                    try {
                        amount = new BigDecimal(record.get(1));
                    } catch (NumberFormatException e) {
                        throw new InvalidInputException(
                                "CSV row " + rowNumber + " has non-numeric amount: '"
                                + record.get(1) + "'", e);
                    }
                    String currency = record.size() >= 3 ? record.get(2) : "USD";
                    // Route through self-proxy so @AuditedOperation and @CacheEvict on
                    // createCost fire for each row (Spring AOP does not intercept
                    // direct same-class calls).
                    created.add(self.createCost(label, amount, currency));
                }
            }
            metrics.recordCostImportSuccess();
        } catch (InvalidInputException e) {
            metrics.recordCostImportFailure();
            throw e;
        } catch (IllegalArgumentException e) {
            metrics.recordCostImportFailure();
            throw new InvalidInputException(e.getMessage(), e);
        } catch (IOException e) {
            metrics.recordCostImportFailure();
            throw new InvalidInputException("Failed to parse CSV content: " + e.getMessage(), e);
        }
        return created;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Currency parseCurrency(String code) {
        if (code == null || code.isBlank()) {
            return Currency.getInstance("USD");
        }
        try {
            return Currency.getInstance(code.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown currency code: '" + code + "'", e);
        }
    }

    /**
     * Removes leading characters that spreadsheet applications (Excel, Google Sheets)
     * interpret as formula entry points ({@code = + - @ \t \r}).
     *
     * <p>Without this defence, an attacker can craft a label like
     * {@code =HYPERLINK("https://example.com","Click me")} that becomes an active
     * formula when the exported CSV is opened in a spreadsheet.
     */
    static String stripCsvInjectionChars(String value) {
        if (value == null) {
            return null;
        }
        int i = 0;
        while (i < value.length() && "=+-@\t\r".indexOf(value.charAt(i)) >= 0) {
            i++;
        }
        return value.substring(i).stripLeading();
    }

    /** Returns {@code true} if the record looks like a CSV header row. */
    private static boolean isHeaderRecord(CSVRecord record) {
        return record.size() > 0 && record.get(0).toLowerCase().contains("label");
    }
}
