package com.profitflow.domain.allocation;

import com.profitflow.domain.DriverShare;
import com.profitflow.domain.Money;
import com.profitflow.domain.exception.AllocationDomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Proportional (driver-based) splitter for monetary amounts.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Compute the sum of all driver weights ({@code sumW}).</li>
 *   <li>For each share except the last, allocate
 *       {@code floor(total × weight / sumW, scale + 4)} rounded to the currency's
 *       default fraction digits using {@link RoundingMode#HALF_UP}.</li>
 *   <li>The <em>last</em> share receives the mathematical remainder
 *       ({@code total − Σ already-allocated}) to guarantee the output sums to
 *       {@code total} regardless of intermediate rounding.</li>
 * </ol>
 *
 * <h2>Invariants enforced</h2>
 * <ul>
 *   <li>All target IDs must be unique within a single call.</li>
 *   <li>The sum of weights must be strictly positive.</li>
 *   <li>A zero {@code total} results in a zero allocation for every target
 *       (no exception).</li>
 * </ul>
 *
 * <p>This class is stateless; all methods are static. It may be called safely
 * from multiple threads simultaneously.
 */
public final class ProportionalAllocator {

    private ProportionalAllocator() {
        // utility class — no instances
    }

    /**
     * Splits {@code total} proportionally across all {@code shares}.
     *
     * @param total  the monetary amount to split; must be non-negative
     * @param shares ordered list of {@link DriverShare}; must be non-empty with
     *               unique target IDs and a positive weight sum
     * @return a {@link LinkedHashMap} preserving insertion order, mapping each
     *         target ID to its allocated {@link Money} slice; guaranteed to sum
     *         to {@code total}
     * @throws AllocationDomainException if the weight sum is not positive or
     *                                   duplicate target IDs are present
     * @throws NullPointerException      if {@code total} or {@code shares} is null
     */
    public static Map<String, Money> split(Money total, List<DriverShare> shares) {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(shares, "shares");
        if (shares.isEmpty()) {
            throw new AllocationDomainException("Driver shares list must not be empty");
        }

        validateUniqueTargetIds(shares);

        BigDecimal sumW = shares.stream()
                .map(DriverShare::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sumW.signum() <= 0) {
            throw new AllocationDomainException(
                    "Sum of driver weights must be positive but was: " + sumW);
        }

        if (total.isZero()) {
            return buildZeroMap(total, shares);
        }

        return buildProportionalMap(total, shares, sumW);
    }

    // --- private helpers ---

    private static void validateUniqueTargetIds(List<DriverShare> shares) {
        Set<String> seen = new HashSet<>(shares.size());
        for (DriverShare s : shares) {
            if (!seen.add(s.targetId())) {
                throw new AllocationDomainException("Duplicate driver-share targetId: " + s.targetId());
            }
        }
    }

    private static Map<String, Money> buildZeroMap(Money total, List<DriverShare> shares) {
        Money zero = new Money(BigDecimal.ZERO, total.currency());
        LinkedHashMap<String, Money> out = new LinkedHashMap<>(shares.size());
        for (DriverShare s : shares) {
            out.put(s.targetId(), zero);
        }
        return out;
    }

    private static Map<String, Money> buildProportionalMap(
            Money total,
            List<DriverShare> shares,
            BigDecimal sumW) {

        var currency = total.currency();
        int scale = Math.max(currency.getDefaultFractionDigits(), 0);

        BigDecimal totalAmt = total.amount();
        int n = shares.size();
        BigDecimal allocatedSoFar = BigDecimal.ZERO;
        LinkedHashMap<String, Money> out = new LinkedHashMap<>(n);

        for (int i = 0; i < n - 1; i++) {
            DriverShare s = shares.get(i);
            // Compute at higher precision then round to currency scale (HALF_UP)
            BigDecimal raw = totalAmt.multiply(s.weight())
                    .divide(sumW, scale + 4, RoundingMode.HALF_UP);
            BigDecimal rounded = raw.setScale(scale, RoundingMode.HALF_UP);
            out.put(s.targetId(), new Money(rounded, currency));
            allocatedSoFar = allocatedSoFar.add(rounded);
        }

        // Last share receives the mathematical remainder to avoid penny drift
        DriverShare last = shares.getLast();
        BigDecimal remainder = totalAmt.subtract(allocatedSoFar).setScale(scale, RoundingMode.HALF_UP);
        out.put(last.targetId(), new Money(remainder, currency));
        return out;
    }
}
