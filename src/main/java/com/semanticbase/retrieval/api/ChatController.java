package com.semanticbase.retrieval.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semanticbase.cache.CachedAnswer;
import com.semanticbase.cache.SemanticCache;
import com.semanticbase.retrieval.RetrievalService;
import com.semanticbase.retrieval.RetrievedChunk;
import com.semanticbase.retrieval.api.dto.ChatRequest;
import com.semanticbase.retrieval.api.dto.Citation;
import com.semanticbase.retrieval.prompt.RagPromptBuilder;
import com.semanticbase.retrieval.prompt.RagPromptBuilder.Prompt;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final String DEFAULT_TENANT = "default";

    private final RetrievalService retrieval;
    private final RagPromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final SemanticCache cache;
    private final MeterRegistry meterRegistry;

    public ChatController(RetrievalService retrieval,
                          RagPromptBuilder promptBuilder,
                          ChatClient.Builder chatClientBuilder,
                          ObjectMapper objectMapper,
                          SemanticCache cache,
                          MeterRegistry meterRegistry) {
        this.retrieval = retrieval;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.meterRegistry = meterRegistry;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@Valid @RequestBody ChatRequest request) {
        String tenant = request.tenantId() == null ? DEFAULT_TENANT : request.tenantId();
        Timer.Sample chatSample = Timer.start(meterRegistry);
        AtomicLong startNanos = new AtomicLong(System.nanoTime());

        Optional<CachedAnswer> cached = cache.lookup(tenant, request.query());
        if (cached.isPresent()) {
            CachedAnswer ans = cached.get();
            chatSample.stop(meterRegistry.timer("semanticbase.chat.duration", "outcome", "cache_hit"));
            return Flux.just(
                    sse("citations", toJson(ans.citations() == null ? List.of() : ans.citations())),
                    sse("delta", ans.answer()),
                    sse("done", "")
            );
        }

        List<RetrievedChunk> chunks = retrieval.retrieve(request.query(), tenant);
        meterRegistry.summary("semanticbase.retrieval.results").record(chunks.size());

        if (chunks.isEmpty()) {
            chatSample.stop(meterRegistry.timer("semanticbase.chat.duration", "outcome", "no_context"));
            return Flux.just(
                    sse("citations", "[]"),
                    sse("delta", "I don't know based on the available documents."),
                    sse("done", "")
            );
        }

        List<Citation> citations = renderCitations(chunks);
        Prompt prompt = promptBuilder.build(request.query(), chunks);

        StringBuilder fullAnswer = new StringBuilder();

        Flux<ServerSentEvent<String>> citationEvent = Flux.just(sse("citations", toJson(citations)));
        Flux<ServerSentEvent<String>> tokens = chatClient.prompt()
                .system(prompt.system())
                .user(prompt.user())
                .stream()
                .content()
                .doOnNext(t -> { if (t != null) fullAnswer.append(t); })
                .map(t -> sse("delta", t));

        Flux<ServerSentEvent<String>> doneEvent = Flux.defer(() -> {
            String answer = fullAnswer.toString();
            if (!answer.isBlank()) {
                cache.store(tenant, request.query(), answer, citations);
            }
            chatSample.stop(meterRegistry.timer("semanticbase.chat.duration", "outcome", "live"));
            return Flux.just(sse("done", ""));
        });

        return Flux.concat(citationEvent, tokens, doneEvent)
                .doOnError(err -> chatSample.stop(
                        meterRegistry.timer("semanticbase.chat.duration", "outcome", "error")));
    }

    private List<Citation> renderCitations(List<RetrievedChunk> chunks) {
        List<Citation> out = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            out.add(Citation.of(i + 1, chunks.get(i)));
        }
        return out;
    }

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder(data).event(event).build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
