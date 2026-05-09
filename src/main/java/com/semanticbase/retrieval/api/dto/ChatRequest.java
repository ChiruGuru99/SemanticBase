package com.semanticbase.retrieval.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String query,
        String tenantId
) {}
