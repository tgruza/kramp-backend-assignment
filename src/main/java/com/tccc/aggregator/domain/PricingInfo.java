package com.tccc.aggregator.domain;

import java.math.BigDecimal;

public record PricingInfo(
        BigDecimal basePrice,
        BigDecimal customerDiscount,
        BigDecimal finalPrice,
        String currency
) {
}
