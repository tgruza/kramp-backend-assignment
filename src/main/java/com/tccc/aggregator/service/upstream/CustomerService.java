package com.tccc.aggregator.service.upstream;

import com.tccc.aggregator.domain.CustomerInfo;

public interface CustomerService {

    CustomerInfo getCustomer(String customerId, String market);
}
