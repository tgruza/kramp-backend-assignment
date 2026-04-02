package com.tccc.aggregator.service.upstream;

import com.tccc.aggregator.domain.PricingInfo;

public interface PricingService {

    PricingInfo getPricing(String productId, String market, String customerId);
}
