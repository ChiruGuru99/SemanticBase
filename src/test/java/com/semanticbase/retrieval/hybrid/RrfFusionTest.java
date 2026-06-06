package com.semanticbase.retrieval.hybrid;

import com.semanticbase.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionTest {

    private final RrfFusion fusion = new RrfFusion();

    private static RetrievedChunk c(String tag) {
        return new RetrievedChunk(UUID.nameUUIDFromBytes(tag.getBytes()),
                tag, "doc-" + tag, "src", 0, 0d);
    }

    @Test
    void emptyInputsYieldEmpty() {
        assertThat(fusion.fuse(List.of(), List.of(), 5)).isEmpty();
    }

    @Test
    void singleListPassthrough() {
        var list = List.of(c("a"), c("b"), c("c"));
        var fused = fusion.fuse(list, List.of(), 5);
        assertThat(fused).hasSize(3);
        assertThat(fused.get(0).id()).isEqualTo(c("a").id());
        assertThat(fused.get(1).id()).isEqualTo(c("b").id());
    }

    @Test
    void itemAppearingInBothListsRanksHigher() {
        var vector = List.of(c("a"), c("b"), c("c"));
        var lexical = List.of(c("d"), c("a"), c("e"));
        var fused = fusion.fuse(vector, lexical, 5);
        assertThat(fused.get(0).id()).isEqualTo(c("a").id());
    }

    @Test
    void respectsTopKLimit() {
        var vector = List.of(c("a"), c("b"), c("c"), c("d"));
        var lexical = List.of(c("e"), c("f"));
        var fused = fusion.fuse(vector, lexical, 3);
        assertThat(fused).hasSize(3);
    }

    @Test
    void rrfScoreIsSumOfReciprocalRanks() {
        var vector = List.of(c("a"));
        var lexical = List.of(c("a"));
        var fused = fusion.fuse(vector, lexical, 5, 60);
        // a is rank 1 in both -> 1/(60+1) + 1/(60+1) = 2/61
        assertThat(fused.get(0).score()).isEqualTo(2.0 / 61.0);
    }

    @Test
    void preservesChunkContent() {
        var vector = List.of(c("hello"));
        var fused = fusion.fuse(vector, List.of(), 5);
        assertThat(fused.get(0).content()).isEqualTo("hello");
        assertThat(fused.get(0).documentId()).isEqualTo("doc-hello");
    }
}
