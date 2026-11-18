package com.semanticbase.cache.redis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSemanticCacheTest {

    @Test
    void cosineOfIdenticalVectorsIsOne() {
        float[] v = {1f, 0f, 0f, 0f};
        assertThat(RedisSemanticCache.cosine(v, v)).isCloseTo(1.0d, within(1e-6));
    }

    @Test
    void cosineOfOrthogonalVectorsIsZero() {
        float[] a = {1f, 0f, 0f};
        float[] b = {0f, 1f, 0f};
        assertThat(RedisSemanticCache.cosine(a, b)).isCloseTo(0d, within(1e-6));
    }

    @Test
    void cosineOfOppositeVectorsIsMinusOne() {
        float[] a = {1f, 1f};
        float[] b = {-1f, -1f};
        assertThat(RedisSemanticCache.cosine(a, b)).isCloseTo(-1.0d, within(1e-6));
    }

    @Test
    void cosineSentinelOnNullOrLengthMismatch() {
        assertThat(RedisSemanticCache.cosine(null, new float[]{1f})).isEqualTo(-1d);
        assertThat(RedisSemanticCache.cosine(new float[]{1f}, null)).isEqualTo(-1d);
        assertThat(RedisSemanticCache.cosine(new float[]{1f}, new float[]{1f, 2f})).isEqualTo(-1d);
    }

    @Test
    void cosineSentinelOnZeroVector() {
        assertThat(RedisSemanticCache.cosine(new float[]{0f, 0f}, new float[]{1f, 1f})).isEqualTo(-1d);
    }

    @Test
    void normalizeCollapsesWhitespace() {
        assertThat(CacheKeys.normalize("  Hello   World\t!  ")).isEqualTo("hello world !");
        assertThat(CacheKeys.normalize(null)).isEmpty();
    }

    @Test
    void exactKeyVariesByTenant() {
        String a = CacheKeys.exactKey("t1", "what is X?");
        String b = CacheKeys.exactKey("t2", "what is X?");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void exactKeyStableForEquivalentNormalizedQueries() {
        String a = CacheKeys.exactKey("t1", "What is X?");
        String b = CacheKeys.exactKey("t1", "  what  is  x?  ");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void purgePatternDoesNotMatchTenantsSharingAPrefix() {
        String pattern = CacheKeys.exactKeyPattern("t1");
        String t10Key = CacheKeys.exactKey("t10", "q");
        // Redis glob '*' == regex '.*' here; no other glob chars in these keys.
        assertThat(t10Key.matches(pattern.replace("*", ".*"))).isFalse();
        assertThat(CacheKeys.exactKey("t1", "q").matches(pattern.replace("*", ".*"))).isTrue();
    }

    private static org.assertj.core.data.Offset<Double> within(double v) {
        return org.assertj.core.data.Offset.offset(v);
    }
}
