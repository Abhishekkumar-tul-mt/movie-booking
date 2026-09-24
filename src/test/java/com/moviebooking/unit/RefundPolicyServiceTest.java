package com.moviebooking.unit;

import com.moviebooking.domain.RefundRule;
import com.moviebooking.service.RefundPolicyService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefundPolicyServiceTest {

    private final RefundPolicyService service = new RefundPolicyService(null);

    private final List<RefundRule> rules = List.of(
            new RefundRule(6, 50), new RefundRule(48, 100), new RefundRule(24, 75), new RefundRule(2, 25));

    private static long hours(double h) {
        return Math.round(h * 60);
    }

    @Test
    void picksTheMostGenerousRuleThatIsStillSatisfied() {
        assertThat(service.computePercent(rules, hours(72))).isEqualTo(100);
        assertThat(service.computePercent(rules, hours(48))).isEqualTo(100); // boundary is inclusive
        assertThat(service.computePercent(rules, hours(47.9))).isEqualTo(75);
        assertThat(service.computePercent(rules, hours(30))).isEqualTo(75);
        assertThat(service.computePercent(rules, hours(10))).isEqualTo(50);
        assertThat(service.computePercent(rules, hours(3))).isEqualTo(25);
    }

    @Test
    void noRefundWhenNoRuleMatches() {
        assertThat(service.computePercent(rules, hours(1))).isZero();
        assertThat(service.computePercent(rules, 0)).isZero();
        assertThat(service.computePercent(List.of(), hours(100))).isZero();
    }

    @Test
    void appliesPercentageWithTwoDecimalRounding() {
        assertThat(service.applyPercent(new BigDecimal("500.00"), 75)).isEqualByComparingTo("375.00");
        assertThat(service.applyPercent(new BigDecimal("333.33"), 50)).isEqualByComparingTo("166.67");
        assertThat(service.applyPercent(new BigDecimal("400.00"), 0)).isEqualByComparingTo("0.00");
    }
}
