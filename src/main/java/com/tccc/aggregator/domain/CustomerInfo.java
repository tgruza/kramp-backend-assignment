package com.tccc.aggregator.domain;

import java.util.List;

public record CustomerInfo(
        String customerId,
        String segment,
        List<String> preferences,
        String preferredWarehouse
) {
}
