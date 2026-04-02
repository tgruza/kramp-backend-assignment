package com.tccc.aggregator.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnAggregatedProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/PART-001")
                        .param("market", "nl-NL"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.productId").value("PART-001"))
                .andExpect(jsonPath("$.name").isNotEmpty())
                .andExpect(jsonPath("$.market").value("nl-NL"))
                .andExpect(jsonPath("$.metadata").exists())
                .andExpect(jsonPath("$.metadata.totalDurationMs").isNumber());
    }

    @Test
    void shouldReturnWithCustomerInfo() throws Exception {
        mockMvc.perform(get("/api/v1/products/PART-001")
                        .param("market", "de-DE")
                        .param("customerId", "CUST-GOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("PART-001"))
                .andExpect(jsonPath("$.market").value("de-DE"));
    }

    @Test
    void shouldReturnPolishLocalizedProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/PART-002")
                        .param("market", "pl-PL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("PART-002"))
                .andExpect(jsonPath("$.market").value("pl-PL"));
    }

    @Test
    void shouldReturn502ForUnknownProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/UNKNOWN-999")
                        .param("market", "nl-NL"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void shouldReturn400WhenMarketMissing() throws Exception {
        mockMvc.perform(get("/api/v1/products/PART-001"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenMarketFormatInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/products/PART-001")
                        .param("market", "invalid"))
                .andExpect(status().isBadRequest());
    }
}
