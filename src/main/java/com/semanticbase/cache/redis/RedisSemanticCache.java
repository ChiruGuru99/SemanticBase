package com.semanticbase.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semanticbase.cache.CacheProperties;
import com.semanticbase.cache.CachedAnswer;
import com.semanticbase.cache.SemanticCache;
import com.semanticbase.retrieval.api.dto.Citation;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisSemanticCache implements SemanticCache {

    private static final Logger log = LoggerFactory.getLogger(RedisSemanticCache.class);

    private final StringRedisTemplate redis;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final CacheProperties props;
    private final MeterRegistry meterRegistry;

    public RedisSemanticCache(StringRedisTemplate redis,
                              EmbeddingModel embeddingModel,
                              ObjectMapper objectMapper,
                              CacheProperties props,
                              MeterRegistry meterRegistry) {
        this.redis = redis;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.props = props;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Optional<CachedAnswer> lookup(String tenantId, String query) {
        if (!props.enabled() || query == null || query.isBlank()) {
            return Optional.empty();
        }

        try {
            String exact = redis.opsForValue().get(CacheKeys.exactKey(tenantId, query));
            if (exact != null) {
                CachedRecord rec = objectMapper.readValue(exact, CachedRecord.class);
                meterRegistry.counter("semanticbase.cache.hit", "tier", "exact").increment();
                return Optional.of(new CachedAnswer(rec.answer(), rec.citations(), CachedAnswer.Tier.EXACT));
            }

            float[] qEmb = embeddingModel.embed(query);
            String listKey = CacheKeys.semanticListKey(tenantId);
            List<String> entries = redis.opsForList().range(listKey, 0, -1);
            if (entries == null || entries.isEmpty()) {
                meterRegistry.counter("semanticbase.cache.miss").increment();
                return Optional.empty();
            }

            double bestSim = -1d;
            CachedRecord best = null;
            double threshold = props.semantic().threshold();
            for (String entry : entries) {
                CachedRecord rec = objectMapper.readValue(entry, CachedRecord.class);
                double sim = cosine(qEmb, rec.embedding());
                if (sim >= threshold && sim > bestSim) {
                    bestSim = sim;
                    best = rec;
                }
            }

            if (best != null) {
                meterRegistry.counter("semanticbase.cache.hit", "tier", "semantic").increment();
                log.debug("Semantic cache hit: similarity={} threshold={}", bestSim, threshold);
                return Optional.of(new CachedAnswer(best.answer(), best.citations(), CachedAnswer.Tier.SEMANTIC));
            }

            meterRegistry.counter("semanticbase.cache.miss").increment();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Cache lookup failed; treating as miss", e);
            meterRegistry.counter("semanticbase.cache.error", "op", "lookup").increment();
            return Optional.empty();
        }
    }

    @Override
    public void store(String tenantId, String query, String answer, List<Citation> citations) {
        if (!props.enabled() || query == null || query.isBlank() || answer == null || answer.isBlank()) {
            return;
        }
        try {
            float[] qEmb = embeddingModel.embed(query);
            CachedRecord record = new CachedRecord(query, answer, citations, qEmb, Instant.now());
            String json = objectMapper.writeValueAsString(record);

            redis.opsForValue().set(CacheKeys.exactKey(tenantId, query), json, props.ttl());

            String listKey = CacheKeys.semanticListKey(tenantId);
            redis.opsForList().leftPush(listKey, json);
            redis.opsForList().trim(listKey, 0, props.semantic().maxEntriesPerTenant() - 1);
            redis.expire(listKey, props.ttl());
        } catch (JsonProcessingException e) {
            log.warn("Cache store serialization failed", e);
            meterRegistry.counter("semanticbase.cache.error", "op", "store").increment();
        } catch (Exception e) {
            log.warn("Cache store failed", e);
            meterRegistry.counter("semanticbase.cache.error", "op", "store").increment();
        }
    }

    @Override
    public long purge(String tenantId) {
        Set<String> keys = redis.keys(CacheKeys.tenantPattern(tenantId));
        if (keys == null || keys.isEmpty()) return 0L;
        Long deleted = redis.delete(keys);
        return deleted == null ? 0L : deleted;
    }

    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return -1d;
        double dot = 0d;
        double na = 0d;
        double nb = 0d;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0d || nb == 0d) return -1d;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
