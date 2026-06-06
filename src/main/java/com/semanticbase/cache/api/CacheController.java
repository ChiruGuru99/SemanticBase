package com.semanticbase.cache.api;

import com.semanticbase.cache.SemanticCache;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final SemanticCache cache;

    public CacheController(SemanticCache cache) {
        this.cache = cache;
    }

    @DeleteMapping("/{tenantId}")
    public Map<String, Object> purge(@PathVariable String tenantId) {
        long deleted = cache.purge(tenantId);
        return Map.of("tenantId", tenantId, "deleted", deleted);
    }
}
