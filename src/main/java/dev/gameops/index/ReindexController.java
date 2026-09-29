package dev.gameops.index;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
public final class ReindexController {
    private final InfraiGateway gateway;
    private final NightlyReindexService reindexService;
    private final GameIndexConfig config;

    public ReindexController(InfraiGateway gateway, NightlyReindexService reindexService, GameIndexConfig config) {
        this.gateway = gateway;
        this.reindexService = reindexService;
        this.config = config;
    }

    @PostMapping("/admin/search/bootstrap")
    public Map<String, Object> bootstrap() {
        gateway.createCollection();
        String jobId = gateway.scheduleNightlyReindex();
        return Map.of("collection", "created", "schedule_job_id", jobId);
    }

    @RequestMapping(path = "/internal/reindex", method = {RequestMethod.GET, RequestMethod.POST})
    public NightlyReindexService.ReindexResult reindex(@RequestBody(required = false) ReindexRequest request) {
        List<IndexCandidate> candidates = request == null ? configuredCandidates() : request.candidates();
        return reindexService.reindex(candidates);
    }

    private List<IndexCandidate> configuredCandidates() {
        return List.of(
                new IndexCandidate("player-assets", IndexCandidate.Kind.PLAYER_ASSET,
                        IndexCandidate.State.PUBLISHED, URI.create(config.playerAssetsUrl())),
                new IndexCandidate("live-events", IndexCandidate.Kind.LIVE_EVENT,
                        IndexCandidate.State.LIVE, URI.create(config.liveEventsUrl())),
                new IndexCandidate("moderation-escalations", IndexCandidate.Kind.MODERATION_QUEUE,
                        IndexCandidate.State.ESCALATED, URI.create(config.moderationQueueUrl())));
    }

    @ExceptionHandler(InfraiException.class)
    public ResponseEntity<Map<String, Object>> rejected(InfraiException exception) {
        HttpStatus status = HttpStatus.resolve(exception.status());
        if (status == null || !status.is4xxClientError()) status = HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Map.of(
                "ok", false,
                "code", exception.code(),
                "detail", exception.detail()));
    }

    public record ReindexRequest(List<IndexCandidate> candidates) { }
}
