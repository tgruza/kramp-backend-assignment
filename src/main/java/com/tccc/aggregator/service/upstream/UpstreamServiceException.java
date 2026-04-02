package com.tccc.aggregator.service.upstream;

public class UpstreamServiceException extends RuntimeException {

    private final String serviceName;

    public UpstreamServiceException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

}
