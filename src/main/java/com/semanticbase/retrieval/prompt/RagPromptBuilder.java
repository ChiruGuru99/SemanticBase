package com.semanticbase.retrieval.prompt;

import com.semanticbase.retrieval.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagPromptBuilder {

    private static final String SYSTEM = """
            You are SemanticBase, a document-grounded assistant.
            Answer the user's question using ONLY the information in the provided context.
            If the answer is not present in the context, reply exactly: "I don't know based on the available documents."
            Always cite the source(s) you used inline using bracketed numbers like [1], [2] that match the citation numbers in the context.
            Do not invent citation numbers and do not invent facts.
            """;

    public Prompt build(String question, List<RetrievedChunk> chunks) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            String label = c.sourceName() == null ? "unknown" : c.sourceName();
            String idx = c.chunkIndex() == null ? "" : " #" + c.chunkIndex();
            context.append("[").append(i + 1).append("] (")
                    .append(label).append(idx).append("):\n")
                    .append(c.content() == null ? "" : c.content().strip())
                    .append("\n\n");
        }

        String user = """
                Context:
                %s
                Question: %s
                """.formatted(context.toString().strip(), question);

        return new Prompt(SYSTEM, user);
    }

    public record Prompt(String system, String user) {}
}
