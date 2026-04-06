package com.profitflow.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Money} — the core value object used throughout the ABC engine.
 */
class MoneyTest {

    @Test
    void usdFactoryCreatesNonNegativeMoney() {
        Money m = Money.usd(new BigDecimal("99.99"));
        assertThat(m.amount()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(m.currency().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void zeroUsdFactoryCreatesZero() {
        assertThat(Money.zeroUsd().isZero()).isTrue();
    }

    @Test
    void amountIsNormalizedToCurrencyScale() {
        Money m = Money.usd(new BigDecimal("10"));
        assertThat(m.amount().scale()).isEqualTo(2);
        assertThat(m.amount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void negativeAmountRejected() {
        assertThatThrownBy(() -> Money.usd(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void nullAmountRejected() {
        assertThatThrownBy(() -> new Money(null, Currency.getInstance("USD")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullCurrencyRejected() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addReturnsSumWithSameCurrency() {
        Money a = Money.usd(new BigDecimal("100.00"));
        Money b = Money.usd(new BigDecimal("50.25"));
        assertThat(a.add(b).amount()).isEqualByComparingTo(new BigDecimal("150.25"));
    }

    @Test
    void addWithMismatchedCurrencyThrows() {
        Money usd = Money.usd(new BigDecimal("100.00"));
        Money eur = new Money(new BigDecimal("100.00"), Currency.getInstance("EUR"));
        assertThatThrownBy(() -> usd.add(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void subtractWithMismatchedCurrencyThrows() {
        Money usd = Money.usd(new BigDecimal("100.00"));
        Money eur = new Money(new BigDecimal("50.00"), Currency.getInstance("EUR"));
        assertThatThrownBy(() -> usd.subtract(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "0", "0.000"})
    void isZeroForVariousZeroRepresentations(String value) {
        assertThat(Money.usd(new BigDecimal(value)).isZero()).isTrue();
    }

    @Test
    void isNotZeroForPositiveAmount() {
        assertThat(Money.usd(new BigDecimal("0.01")).isZero()).isFalse();
    }

    @Test
    void equalityIsValueBased() {
        Money a = Money.usd(new BigDecimal("10.00"));
        Money b = Money.usd(new BigDecimal("10.00"));
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void differentAmountsAreNotEqual() {
        assertThat(Money.usd(BigDecimal.ONE)).isNotEqualTo(Money.usd(BigDecimal.TEN));
    }
}
