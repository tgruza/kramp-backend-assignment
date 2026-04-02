package com.tccc.aggregator.service.aggregator;

public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
