package com.semanticbase.cache;

import com.semanticbase.retrieval.api.dto.Citation;

import java.util.List;
import java.util.Optional;

public interface SemanticCache {

    Optional<CachedAnswer> lookup(String tenantId, String query);

    void store(String tenantId, String query, String answer, List<Citation> citations);

    long purge(String tenantId);
}
