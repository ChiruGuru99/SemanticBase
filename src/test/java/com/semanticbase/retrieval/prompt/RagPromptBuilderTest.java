package com.semanticbase.retrieval.prompt;

import com.semanticbase.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RagPromptBuilderTest {

    private final RagPromptBuilder builder = new RagPromptBuilder();

    @Test
    void numbersChunksStartingAtOne() {
        var chunks = List.of(
                new RetrievedChunk(UUID.randomUUID(), "alpha content", "d1", "alpha.txt", 0, 0d),
                new RetrievedChunk(UUID.randomUUID(), "beta content",  "d2", "beta.txt",  3, 0d)
        );
        var prompt = builder.build("what is alpha?", chunks);
        assertThat(prompt.user())
                .contains("[1] (alpha.txt #0):")
                .contains("alpha content")
                .contains("[2] (beta.txt #3):")
                .contains("beta content");
    }

    @Test
    void includesQuestionVerbatim() {
        var chunks = List.of(new RetrievedChunk(UUID.randomUUID(), "x", "d", "s", 0, 0d));
        var prompt = builder.build("Why is the sky blue?", chunks);
        assertThat(prompt.user()).contains("Question: Why is the sky blue?");
    }

    @Test
    void systemPromptForbidsHallucination() {
        var prompt = builder.build("q", List.of());
        assertThat(prompt.system())
                .contains("ONLY")
                .contains("I don't know based on the available documents.");
    }

    @Test
    void handlesNullSourceName() {
        var chunks = List.of(
                new RetrievedChunk(UUID.randomUUID(), "content", "d1", null, null, 0d));
        var prompt = builder.build("q", chunks);
        assertThat(prompt.user()).contains("[1] (unknown):");
    }
}
