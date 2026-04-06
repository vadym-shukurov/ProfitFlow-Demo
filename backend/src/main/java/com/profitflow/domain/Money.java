package com.profitflow.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable, non-negative monetary amount in a single currency.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><strong>Non-negative invariant</strong>: ABC cost flows are always positive;
 *       negative balances represent a different concept (revenue, adjustment) and
 *       should use a dedicated type. The constructor rejects negative values eagerly.</li>
 *   <li><strong>Scale normalisation</strong>: The amount is scaled to the currency's
 *       {@link Currency#getDefaultFractionDigits()} on construction, so {@code 10} and
 *       {@code 10.00} are stored identically (both as {@code 10.00} for USD).</li>
 *   <li><strong>Currency safety</strong>: Arithmetic methods throw when the two operands
 *       carry different currencies, preventing silent cross-currency bugs.</li>
 * </ul>
 *
 * <p>Using {@link java.math.BigDecimal} (never {@code double}) avoids floating-point
 * rounding errors — critical for financial calculations.
 */
public record Money(BigDecimal amount, Currency currency) {

    /**
     * Compact constructor: validates non-negativity and normalises the scale.
     *
     * @throws NullPointerException     if {@code amount} or {@code currency} is null
     * @throws IllegalArgumentException if {@code amount} is negative
     */
    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "amount must be non-negative, but was: " + amount);
        }
        int scale = currency.getDefaultFractionDigits();
        if (scale < 0) {
            scale = 2; // fall back to 2 decimal places for currencies without a defined scale
        }
        amount = amount.setScale(scale, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a USD-denominated amount from the given {@code BigDecimal}.
     *
     * @param amount non-negative decimal value
     * @return new {@link Money} in USD
     */
    public static Money usd(BigDecimal amount) {
        return new Money(amount, Currency.getInstance("USD"));
    }

    /**
     * Returns a USD zero-value sentinel, useful for initialising accumulators.
     */
    public static Money zeroUsd() {
        return usd(BigDecimal.ZERO);
    }

    // -------------------------------------------------------------------------
    // Arithmetic
    // -------------------------------------------------------------------------

    /**
     * Returns a new {@link Money} representing {@code this + other}.
     *
     * @throws IllegalArgumentException if the currencies differ
     */
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    /**
     * Returns a new {@link Money} representing {@code this − other}.
     *
     * @throws IllegalArgumentException if the currencies differ <em>or</em> if the
     *                                  result would be negative (this class does not
     *                                  model negative balances)
     */
    public Money subtract(Money other) {
        requireSameCurrency(other);
        BigDecimal result = amount.subtract(other.amount);
        if (result.signum() < 0) {
            throw new IllegalArgumentException(
                    "Subtraction would produce a negative amount (" + amount
                    + " − " + other.amount + "). "
                    + "Money cannot represent negative values.");
        }
        return new Money(result, currency);
    }

    // -------------------------------------------------------------------------
    // Predicates
    // -------------------------------------------------------------------------

    /** Returns {@code true} if this amount equals zero (regardless of scale). */
    public boolean isZero() {
        return amount.signum() == 0;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: cannot combine " + currency
                    + " and " + other.currency);
        }
    }
}
