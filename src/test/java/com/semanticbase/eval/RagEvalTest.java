package com.semanticbase.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end RAG eval against a running SemanticBase, so it measures the real stack
 * (the configured embedding and chat providers), not the random-vector stub the integration tests use.
 *
 * <p>Start the app, then: {@code ./mvnw test -Peval}. Settings come from {@code eval/eval.properties}
 * ({@code eval.base-url}, {@code eval.delay-ms}); a {@code -D} system property overrides either.
 *
 * <p>Ingests {@code eval/corpus} into an isolated tenant and asks {@code eval/questions.json}:
 * <ul>
 *   <li>retrieval: rank of the expected source among the returned citations (hit@1, hit@k, MRR)</li>
 *   <li>answer: expected facts present in the answer (fact recall)</li>
 *   <li>abstention: unanswerable questions (source = null) get "I don't know"</li>
 * </ul>
 */
@Tag("eval")
class RagEvalTest {

    private static final String TENANT = "eval";
    private static final String ABSTAIN = "i don't know";

    // ponytail: ~26 questions, so one question moves a rate by 4-5 points; treat smaller deltas as noise.
    private static final double MIN_HIT_AT_1 = 0.75;
    private static final double MIN_MRR = 0.80;
    private static final double MIN_FACT_RECALL = 0.80;
    private static final double MIN_ABSTENTION = 0.75;

    record Case(String question, String source, List<String> facts) {}

    record Answer(List<String> sources, String text) {}

    private final ObjectMapper json = new ObjectMapper();
    private RestClient http;
    private long delayMs;

    @BeforeEach
    void configure() throws Exception {
        Properties config = PropertiesLoaderUtils.loadProperties(new ClassPathResource("eval/eval.properties"));
        http = RestClient.create(System.getProperty("eval.base-url", config.getProperty("eval.base-url")));
        delayMs = Long.parseLong(System.getProperty("eval.delay-ms", config.getProperty("eval.delay-ms")));
    }

    @Test
    void ragQualityMeetsThresholds() throws Exception {
        ingestCorpus();
        // Re-runs skip ingestion (duplicate checksum), so clear answers cached by a previous run.
        http.delete().uri("/api/cache/{tenant}", TENANT).retrieve().toBodilessEntity();

        List<Case> cases = json.readValue(new ClassPathResource("eval/questions.json").getInputStream(),
                new TypeReference<>() {});

        int answerable = 0, hitAt1 = 0, hitAtK = 0, unanswerable = 0, abstained = 0;
        double reciprocalRanks = 0, factRecall = 0;

        for (Case c : cases) {
            Answer a = ask(c.question());
            String text = a.text().replace('’', '\'').toLowerCase(Locale.ROOT);

            if (c.source() == null) {
                unanswerable++;
                boolean ok = text.contains(ABSTAIN);
                if (ok) abstained++;
                System.out.printf("[%s] (unanswerable) %s%n    -> %s%n",
                        ok ? "PASS" : "FAIL", c.question(), oneLine(a.text()));
            } else {
                answerable++;
                int rank = a.sources().indexOf(c.source()) + 1;
                long found = c.facts().stream().filter(f -> text.contains(f.toLowerCase(Locale.ROOT))).count();
                if (rank == 1) hitAt1++;
                if (rank > 0) {
                    hitAtK++;
                    reciprocalRanks += 1.0 / rank;
                }
                factRecall += (double) found / c.facts().size();
                System.out.printf("[%s] rank=%s facts=%d/%d %s%n    -> %s%n",
                        rank == 1 && found == c.facts().size() ? "PASS" : "FAIL",
                        rank == 0 ? "-" : rank, found, c.facts().size(), c.question(), oneLine(a.text()));
            }
            if (delayMs > 0) Thread.sleep(delayMs);
        }

        double h1 = (double) hitAt1 / answerable;
        double mrr = reciprocalRanks / answerable;
        double recall = factRecall / answerable;
        double abstention = (double) abstained / unanswerable;
        System.out.printf("""

                RAG eval (%d answerable, %d unanswerable)
                  hit@1        %.2f  (min %.2f)
                  hit@k        %.2f
                  MRR          %.2f  (min %.2f)
                  fact recall  %.2f  (min %.2f)
                  abstention   %.2f  (min %.2f)
                %n""", answerable, unanswerable, h1, MIN_HIT_AT_1, (double) hitAtK / answerable,
                mrr, MIN_MRR, recall, MIN_FACT_RECALL, abstention, MIN_ABSTENTION);

        assertThat(h1).as("hit@1").isGreaterThanOrEqualTo(MIN_HIT_AT_1);
        assertThat(mrr).as("MRR").isGreaterThanOrEqualTo(MIN_MRR);
        assertThat(recall).as("fact recall").isGreaterThanOrEqualTo(MIN_FACT_RECALL);
        assertThat(abstention).as("abstention").isGreaterThanOrEqualTo(MIN_ABSTENTION);
    }

    private void ingestCorpus() throws Exception {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:eval/corpus/*.md");
        assertThat(files).isNotEmpty();
        for (Resource file : files) {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", file);
            String id = http.post().uri("/api/ingest?tenantId={tenant}", TENANT)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class)
                    .get("documentId").asText();

            Awaitility.await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofMillis(500))
                    .until(() -> status(id), s -> !s.equals("PENDING") && !s.equals("PROCESSING"));
            assertThat(status(id)).as("ingestion of %s", file.getFilename()).isEqualTo("COMPLETED");
        }
    }

    private String status(String documentId) {
        return http.get().uri("/api/documents/{id}", documentId).retrieve().body(JsonNode.class).get("status").asText();
    }

    private Answer ask(String question) throws Exception {
        String body = http.post().uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(Map.of("query", question, "tenantId", TENANT))
                .retrieve()
                .body(String.class);

        List<String> sources = List.of();
        StringBuilder text = new StringBuilder();
        String event = null;
        boolean continuation = false;
        for (String line : body.split("\r?\n")) {
            if (line.isEmpty()) {
                continuation = false;
            } else if (line.startsWith("event:")) {
                event = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                // Same as the UI: no space stripping, tokens carry their own leading spaces.
                String data = line.substring(5);
                if ("citations".equals(event)) {
                    sources = json.readTree(data).findValuesAsText("sourceName");
                } else if ("delta".equals(event)) {
                    if (continuation) text.append('\n'); // multi-line data within one event
                    text.append(data);
                    continuation = true;
                }
            }
        }
        return new Answer(sources, text.toString());
    }

    private static String oneLine(String s) {
        String flat = s.replaceAll("\\s+", " ").strip();
        return flat.length() > 160 ? flat.substring(0, 160) + "…" : flat;
    }
}
