package dev.gameops.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public final class InfraiGateway {
    private static final int MAX_ATTEMPTS = 4;
    private final GameIndexConfig config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final OpenAIClient embeddings;
    private final String apiKey;

    public InfraiGateway(GameIndexConfig config, ObjectMapper json) {
        this.config = config;
        this.json = json;
        this.apiKey = requiredEnvironment("INFRAI_API_KEY");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.embeddings = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(config.infraiBaseUrl() + "/v1")
                .build();
    }

    public void createCollection() {
        post("/v1/vector/collection/create", Map.of(
                "collection", config.collection(),
                "dimension", config.embeddingDimension(),
                "metric", "cosine",
                "metadata", Map.of("owner", "game-search", "data_class", "mixed-player-content")),
                idempotencyKey("collection", config.collection()));
    }

    public String scrape(URI source) {
        Map<String, Object> data = post("/v1/web/scrape", Map.of(
                "url", source.toString(),
                "format", "markdown"), null);
        Object content = data.get("content");
        return content == null ? data.toString() : content.toString();
    }

    public List<Float> embed(String text) {
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .input(text)
                .model(config.embeddingModel())
                .build();
        return embeddings.embeddings().create(params).data().getFirst().embedding();
    }

    public void upsert(IndexCandidate candidate, String content, List<Float> embedding) {
        Map<String, Object> vector = Map.of(
                "id", candidate.id(),
                "values", embedding,
                "metadata", Map.of(
                        "kind", candidate.kind().name(),
                        "state", candidate.state().name(),
                        "source_url", candidate.sourceUrl().toString(),
                        "content", content));
        post("/v1/vector/upsert", Map.of(
                "collection", config.collection(),
                "vectors", List.of(vector)), idempotencyKey("vector", candidate.id()));
    }

    public String scheduleNightlyReindex() {
        Map<String, Object> data = post("/v1/cron/create", Map.of(
                "cron_expr", config.cronExpression(),
                "task", config.reindexTaskUrl()), idempotencyKey("schedule", config.reindexTaskUrl()));
        return String.valueOf(data.get("job_id"));
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String idempotencyKey) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.infraiBaseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(writeJson(body)));
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);

            HttpResponse<String> response = send(builder.build());
            Envelope envelope = readEnvelope(response.body());
            if (!envelope.ok()) {
                if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                    pause(retryDelayMillis(response, attempt));
                    continue;
                }
                throw envelope.toException(response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Infrai transport status " + response.statusCode());
            }
            return envelope.data();
        }
        throw new IllegalStateException("Retry loop ended unexpectedly");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Infrai transport request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Infrai transport request interrupted", e);
        }
    }

    private Envelope readEnvelope(String body) {
        try {
            Map<String, Object> raw = json.readValue(body, new TypeReference<>() {});
            boolean ok = Boolean.TRUE.equals(raw.get("ok"));
            Map<String, Object> data = asMap(raw.get("data"));
            Map<String, Object> error = asMap(raw.get("error"));
            return new Envelope(ok, data, error);
        } catch (IOException e) {
            throw new IllegalStateException("Infrai returned an unreadable envelope", e);
        }
    }

    private String writeJson(Map<String, Object> body) {
        try {
            return json.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalArgumentException("Request body cannot be encoded", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private static String idempotencyKey(String operation, String resource) {
        return UUID.nameUUIDFromBytes((operation + ":" + resource).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(InfraiGateway::secondsToMillis)
                .orElse(250L * (1L << attempt));
    }

    private static long secondsToMillis(String value) {
        try {
            return Math.max(0, Long.parseLong(value.trim()) * 1000L);
        } catch (NumberFormatException ignored) {
            return 1000L;
        }
    }

    private static void pause(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", e);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    private record Envelope(boolean ok, Map<String, Object> data, Map<String, Object> error) {
        InfraiException toException(int status) {
            return new InfraiException(String.valueOf(error.getOrDefault("code", "INFRAI_REQUEST_REJECTED")), error, status);
        }
    }
}
