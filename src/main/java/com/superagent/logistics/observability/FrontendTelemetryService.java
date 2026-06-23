package com.superagent.logistics.observability;

import com.superagent.logistics.api.dto.FrontendEventRequest;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class FrontendTelemetryService {

    private static final Logger log = LoggerFactory.getLogger(FrontendTelemetryService.class);
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "WINDOW_ERROR", "UNHANDLED_REJECTION", "VUE_ERROR", "API_FAILURE", "API_TIMING",
            "ROUTE_TIMING", "WEB_VITAL_LCP", "WEB_VITAL_CLS"
    );

    private final MeterRegistry meterRegistry;

    public FrontendTelemetryService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(FrontendEventRequest event) {
        String type = normalizeType(event.type());
        String route = sanitize(event.route(), 160);
        String message = sanitize(event.message(), 500);
        String status = event.status() == null ? "none" : String.valueOf(event.status());
        meterRegistry.counter("agent.frontend.events", "type", type, "status", status).increment();
        if (event.durationMs() != null) {
            DistributionSummary.builder("agent.frontend.duration")
                    .baseUnit("milliseconds")
                    .tag("type", type)
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(event.durationMs());
        }
        log.info("frontend.event type={} route={} status={} durationMs={} clientTraceId={} message={}",
                type, route, status, event.durationMs(), sanitize(event.traceId(), 80), message);
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "UNKNOWN" : type.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_TYPES.contains(normalized) ? normalized : "UNKNOWN";
    }

    private String sanitize(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String clean = value.replaceAll("[\\r\\n\\t]", " ").replaceAll("\\s{2,}", " ").trim();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
    }
}
