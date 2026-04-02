package com.tccc.aggregator.service.upstream.mock;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@UtilityClass
class MockLatencySimulator {

    void simulate(String serviceName, long typicalLatencyMs, double reliabilityPct) {
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
