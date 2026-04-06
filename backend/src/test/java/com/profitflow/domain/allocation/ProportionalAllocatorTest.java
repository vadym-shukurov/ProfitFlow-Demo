package com.profitflow.domain.allocation;

import com.profitflow.domain.DriverShare;
import com.profitflow.domain.Money;
import com.profitflow.domain.exception.AllocationDomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ProportionalAllocator}.
 *
 * <p>Coverage targets:
 * <ul>
 *   <li>Proportional splits at common ratios (50/50, 1:2, 1:1:1)</li>
 *   <li>Penny-safe rounding guarantee (sum = total)</li>
 *   <li>Zero total short-circuit</li>
 *   <li>All validation guards</li>
 * </ul>
 */
class ProportionalAllocatorTest {

    @Test
    void splitsFiftyFifty() {
        Money total = Money.usd(new BigDecimal("100.00"));
        var shares = List.of(
                new DriverShare("a", BigDecimal.ONE),
                new DriverShare("b", BigDecimal.ONE));

        Map<String, Money> out = ProportionalAllocator.split(total, shares);

        assertThat(out.get("a").amount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(out.get("b").amount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertTotalConserved(out, total);
    }

    @Test
    void splitsOneToTwoRatio() {
        Money total = Money.usd(new BigDecimal("300.00"));
        var shares = List.of(
                new DriverShare("x", BigDecimal.ONE),
                new DriverShare("y", new BigDecimal("2")));

        Map<String, Money> out = ProportionalAllocator.split(total, shares);

        assertThat(out.get("x").amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(out.get("y").amount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertTotalConserved(out, total);
    }

    @Test
    void threeWayEqualSplitAbsorbsPennyInLastShare() {
        Money total = Money.usd(new BigDecimal("100.00"));
        var shares = List.of(
                new DriverShare("a", BigDecimal.ONE),
                new DriverShare("b", BigDecimal.ONE),
                new DriverShare("c", BigDecimal.ONE));

        Map<String, Money> out = ProportionalAllocator.split(total, shares);

        assertThat(out.get("a").amount()).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(out.get("b").amount()).isEqualByComparingTo(new BigDecimal("33.33"));
        // last share absorbs the rounding remainder: 100 - 33.33 - 33.33 = 33.34
        assertThat(out.get("c").amount()).isEqualByComparingTo(new BigDecimal("33.34"));
        assertTotalConserved(out, total);
    }

    @Test
    void singleShareReceivesFullAmount() {
        Money total = Money.usd(new BigDecimal("42.17"));
        var shares = List.of(new DriverShare("only", new BigDecimal("99")));

        Map<String, Money> out = ProportionalAllocator.split(total, shares);

        assertThat(out.get("only").amount()).isEqualByComparingTo(total.amount());
    }

    @Test
    void zeroTotalYieldsZeroPerTarget() {
        Money total = Money.zeroUsd();
        var shares = List.of(
                new DriverShare("a", BigDecimal.ONE),
                new DriverShare("b", BigDecimal.ONE));

        Map<String, Money> out = ProportionalAllocator.split(total, shares);

        assertThat(out.get("a").isZero()).isTrue();
        assertThat(out.get("b").isZero()).isTrue();
    }

    @Test
    void outputPreservesInsertionOrder() {
        var shares = List.of(
                new DriverShare("first", new BigDecimal("3")),
                new DriverShare("second", new BigDecimal("1")),
                new DriverShare("third", new BigDecimal("2")));

        Map<String, Money> out = ProportionalAllocator.split(
                Money.usd(new BigDecimal("600.00")), shares);

        assertThat(out.keySet()).containsExactly("first", "second", "third");
    }

    @Test
    void emptySharesRejected() {
        assertThatThrownBy(() ->
                ProportionalAllocator.split(Money.usd(BigDecimal.ONE), List.of()))
                .isInstanceOf(AllocationDomainException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void allZeroWeightSumRejected() {
        assertThatThrownBy(() ->
                ProportionalAllocator.split(Money.usd(BigDecimal.TEN), List.of(
                        new DriverShare("a", BigDecimal.ZERO),
                        new DriverShare("b", BigDecimal.ZERO))))
                .isInstanceOf(AllocationDomainException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void duplicateTargetIdsRejected() {
        assertThatThrownBy(() ->
                ProportionalAllocator.split(Money.usd(BigDecimal.TEN), List.of(
                        new DriverShare("a", BigDecimal.ONE),
                        new DriverShare("a", BigDecimal.ONE))))
                .isInstanceOf(AllocationDomainException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void nullTotalRejected() {
        assertThatThrownBy(() ->
                ProportionalAllocator.split(null, List.of(new DriverShare("x", BigDecimal.ONE))))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullSharesRejected() {
        assertThatThrownBy(() ->
                ProportionalAllocator.split(Money.zeroUsd(), null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- helpers ---

    private static void assertTotalConserved(Map<String, Money> parts, Money expected) {
        BigDecimal sum = parts.values().stream()
                .map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(expected.amount());
    }
}
