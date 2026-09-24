package com.moviebooking.payment;

import com.moviebooking.domain.PaymentMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/** Deterministic fake: any token starting with "fail" is declined, everything else succeeds. */
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public GatewayResult charge(BigDecimal amount, PaymentMethod method, String paymentToken) {
        if (paymentToken != null && paymentToken.toLowerCase(Locale.ROOT).startsWith("fail")) {
            return GatewayResult.declined("Card declined by issuer (simulated)");
        }
        return GatewayResult.ok("PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT));
    }

    @Override
    public GatewayResult refund(BigDecimal amount, String originalPaymentReference) {
        return GatewayResult.ok("RFD-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT));
    }
}
