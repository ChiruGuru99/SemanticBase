package com.semanticbase.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "semanticbase.cache")
public record CacheProperties(
        boolean enabled,
        Duration ttl,
        Semantic semantic
) {
    public CacheProperties {
        if (ttl == null) ttl = Duration.ofHours(24);
        if (semantic == null) semantic = new Semantic(0.97d, 200);
    }

    public record Semantic(double threshold, int maxEntriesPerTenant) {
        public Semantic {
            if (threshold <= 0 || threshold > 1) threshold = 0.97d;
            if (maxEntriesPerTenant <= 0) maxEntriesPerTenant = 200;
        }
    }
}
