package com.example.monitoring.monitor;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ExternalApiMonitor {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiMonitor.class);
    private static final String REQUESTS_METRIC_NAME = "external.api.requests";
    private static final String DURATION_METRIC_NAME = "external.api.request.duration";

    private final RestClient restClient;
    private final String externalApiUrl;
    private final MeterRegistry meterRegistry;
    private final Timer requestDurationTimer;

    public ExternalApiMonitor(
            @Value("${monitor.external-api.url}") String externalApiUrl,
            MeterRegistry meterRegistry) {
        this.restClient = RestClient.create();
        this.externalApiUrl = externalApiUrl;
        this.meterRegistry = meterRegistry;
        this.requestDurationTimer = Timer.builder(DURATION_METRIC_NAME)
                .description("Duration of external API requests")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${monitor.external-api.fixed-delay}")
    public void checkExternalApi() {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            HttpStatusCode statusCode = restClient.get()
                    .uri(externalApiUrl)
                    .exchange((request, response) -> response.getStatusCode());

            boolean success = statusCode.is2xxSuccessful();
            incrementRequestCounter(success ? "success" : "failure", String.valueOf(statusCode.value()));
            long latency = elapsedMilliseconds(sample.stop(requestDurationTimer));
            log.info(
                    "External API check completed - status={}, success={}, latency={}ms",
                    statusCode.value(),
                    success,
                    latency);
        } catch (RestClientException exception) {
            incrementRequestCounter("failure", "none");
            long latency = elapsedMilliseconds(sample.stop(requestDurationTimer));
            log.error(
                    "External API check failed - success=false, latency={}ms, error={}",
                    latency,
                    exception.getMessage());
        }
    }

    private void incrementRequestCounter(String outcome, String statusCode) {
        Counter requestCounter = meterRegistry.counter(
                REQUESTS_METRIC_NAME,
                "outcome", outcome,
                "status_code", statusCode);
        requestCounter.increment();
    }

    private long elapsedMilliseconds(long elapsedNanoseconds) {
        return TimeUnit.NANOSECONDS.toMillis(elapsedNanoseconds);
    }
}
