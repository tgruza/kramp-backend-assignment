package com.tccc.aggregator.service.upstream.mock;

import com.tccc.aggregator.domain.AvailabilityInfo;
import com.tccc.aggregator.service.upstream.AvailabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
class MockAvailabilityService implements AvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(MockAvailabilityService.class);

    private static final long TYPICAL_LATENCY_MS = 100;
    private static final double RELIABILITY_PCT = 98.0;

    private static final Map<String, String> MARKET_WAREHOUSES = Map.of(
            "nl-NL", "Rotterdam, NL",
            "de-DE", "Hamburg, DE",
            "pl-PL", "Wrocław, PL"
    );

    private static final Map<String, Integer> DELIVERY_DAYS = Map.of(
            "nl-NL", 1,
            "de-DE", 2,
            "pl-PL", 3
    );

    @Override
    public AvailabilityInfo getAvailability(String productId, String market) {
        log.info("Fetching availability for product={} market={}", productId, market);
        MockLatencySimulator.simulate("AvailabilityService", TYPICAL_LATENCY_MS, RELIABILITY_PCT);

        String warehouse = MARKET_WAREHOUSES.getOrDefault(market, "Central EU Warehouse");
        int deliveryDays = DELIVERY_DAYS.getOrDefault(market, 5);

        int stockLevel = ThreadLocalRandom.current().nextInt(0, 150);
        boolean inStock = stockLevel > 0;

        LocalDate expectedDelivery = inStock
                ? LocalDate.now().plusDays(deliveryDays)
                : LocalDate.now().plusDays(deliveryDays + 7); // backordered

        return new AvailabilityInfo(stockLevel, warehouse, expectedDelivery, inStock);
    }
}
