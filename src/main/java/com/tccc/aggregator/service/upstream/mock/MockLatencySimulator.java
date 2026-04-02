package com.tccc.aggregator.service.upstream.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

final class MockLatencySimulator {

    private static final Logger log = LoggerFactory.getLogger(MockLatencySimulator.class);

    private MockLatencySimulator() {
    }

    static void simulate(String serviceName, long typicalLatencyMs, double reliabilityPct) {
        long minLatency = typicalLatencyMs / 2;
        long maxLatency = typicalLatencyMs * 2;
        long actualLatency = ThreadLocalRandom.current().nextLong(minLatency, maxLatency + 1);

        try {
            Thread.sleep(actualLatency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during simulated latency", e);
        }

        double roll = ThreadLocalRandom.current().nextDouble(100.0);
        if (roll >= reliabilityPct) {
            log.warn("[MOCK] {} failed (roll={}, reliability={}%)", serviceName, roll, reliabilityPct);
            throw new com.tccc.aggregator.service.upstream.UpstreamServiceException(
                    serviceName,
                    serviceName + " is temporarily unavailable (simulated failure)"
            );
        }

        log.debug("[MOCK] {} responded in {}ms", serviceName, actualLatency);
    }
}
