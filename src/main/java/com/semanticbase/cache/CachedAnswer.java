package com.semanticbase.cache;

import com.semanticbase.retrieval.api.dto.Citation;

import java.util.List;

public record CachedAnswer(
        String answer,
        List<Citation> citations,
        Tier tier
) {
    public enum Tier { EXACT, SEMANTIC }
}
