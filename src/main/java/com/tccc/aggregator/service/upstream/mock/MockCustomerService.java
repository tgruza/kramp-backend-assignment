package com.tccc.aggregator.service.upstream.mock;

import com.tccc.aggregator.domain.CustomerInfo;
import com.tccc.aggregator.service.upstream.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
class MockCustomerService implements CustomerService {

    private static final Logger log = LoggerFactory.getLogger(MockCustomerService.class);

    private static final long TYPICAL_LATENCY_MS = 60;
    private static final double RELIABILITY_PCT = 99.0;

    private static final Map<String, CustomerData> CUSTOMERS = Map.of(
            "CUST-GOLD", new CustomerData(
                    "Gold", List.of("hydraulics", "heavy-machinery"), "Rotterdam, NL"
            ),
            "CUST-SILVER", new CustomerData(
                    "Silver", List.of("filters", "maintenance-kits"), "Hamburg, DE"
            ),
            "CUST-BRONZE", new CustomerData(
                    "Bronze", List.of("belts", "general-parts"), "Wrocław, PL"
            )
    );

    private record CustomerData(String segment, List<String> preferences, String preferredWarehouse) {
    }

    @Override
    public CustomerInfo getCustomer(String customerId, String market) {
        log.info("Fetching customer info for customer={} market={}", customerId, market);
        MockLatencySimulator.simulate("CustomerService", TYPICAL_LATENCY_MS, RELIABILITY_PCT);

        CustomerData data = CUSTOMERS.get(customerId);
        if (data == null) {
            return new CustomerInfo(customerId, "Standard", List.of(), null);
        }

        return new CustomerInfo(
                customerId,
                data.segment(),
                data.preferences(),
                data.preferredWarehouse()
        );
    }
}
