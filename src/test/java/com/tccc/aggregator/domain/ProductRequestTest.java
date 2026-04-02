package com.tccc.aggregator.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRequestTest {

    @Test
    void shouldHaveCustomerIdWhenProvided() {
        var request = new ProductRequest("PART-001", "nl-NL", "CUST-GOLD");
        assertThat(request.hasCustomerId()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void shouldNotHaveCustomerIdWhenNullOrBlank(String customerId) {
        var request = new ProductRequest("PART-001", "nl-NL", customerId);
        assertThat(request.hasCustomerId()).isFalse();
    }

    @Test
    void shouldExposeAllFields() {
        var request = new ProductRequest("PART-001", "de-DE", "CUST-SILVER");
        assertThat(request.productId()).isEqualTo("PART-001");
        assertThat(request.market()).isEqualTo("de-DE");
        assertThat(request.customerId()).isEqualTo("CUST-SILVER");
    }
}
