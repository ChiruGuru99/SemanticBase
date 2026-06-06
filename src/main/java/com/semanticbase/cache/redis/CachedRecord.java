package com.semanticbase.cache.redis;

import com.semanticbase.retrieval.api.dto.Citation;

import java.time.Instant;
import java.util.List;

record CachedRecord(
        String query,
        String answer,
        List<Citation> citations,
        float[] embedding,
        Instant cachedAt
) {}
