package com.tccc.aggregator.service.upstream;

import com.tccc.aggregator.domain.AvailabilityInfo;

public interface AvailabilityService {

    AvailabilityInfo getAvailability(String productId, String market);
}
