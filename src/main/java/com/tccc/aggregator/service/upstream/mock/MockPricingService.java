package com.tccc.aggregator.service.upstream.mock;

import com.tccc.aggregator.domain.PricingInfo;
import com.tccc.aggregator.service.upstream.PricingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Slf4j
@Service
class MockPricingService implements PricingService {

    private static final long TYPICAL_LATENCY_MS = 80;
    private static final double RELIABILITY_PCT = 99.5;

    private static final Map<String, BigDecimal> BASE_PRICES = Map.of(
            "PART-001", new BigDecimal("245.00"),
            "PART-002", new BigDecimal("32.50"),
            "PART-003", new BigDecimal("18.75")
    );

    private static final Map<String, CurrencyConfig> MARKET_CURRENCIES = Map.of(
            "nl-NL", new CurrencyConfig("EUR", BigDecimal.ONE),
            "de-DE", new CurrencyConfig("EUR", BigDecimal.ONE),
            "pl-PL", new CurrencyConfig("PLN", new BigDecimal("4.32"))
    );

    private static final Map<String, BigDecimal> CUSTOMER_DISCOUNTS = Map.of(
            "CUST-GOLD", new BigDecimal("0.15"),    // 15% discount
            "CUST-SILVER", new BigDecimal("0.10"),   // 10% discount
            "CUST-BRONZE", new BigDecimal("0.05")    // 5% discount
    );

    private record CurrencyConfig(String code, BigDecimal multiplier) {
    }

    @Override
    public PricingInfo getPricing(String productId, String market, String customerId) {
        log.info("Fetching pricing for product={} market={} customer={}", productId, market, customerId);
        MockLatencySimulator.simulate("PricingService", TYPICAL_LATENCY_MS, RELIABILITY_PCT);

        BigDecimal eurBasePrice = BASE_PRICES.getOrDefault(productId, new BigDecimal("99.99"));
        CurrencyConfig currency = MARKET_CURRENCIES.getOrDefault(market,
                new CurrencyConfig("EUR", BigDecimal.ONE));

        BigDecimal basePrice = eurBasePrice.multiply(currency.multiplier())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal discountRate = BigDecimal.ZERO;
        if (customerId != null && !customerId.isBlank()) {
            discountRate = CUSTOMER_DISCOUNTS.getOrDefault(customerId, BigDecimal.ZERO);
        }

        BigDecimal discountAmount = basePrice.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalPrice = basePrice.subtract(discountAmount);

        return new PricingInfo(basePrice, discountAmount, finalPrice, currency.code());
    }
}
