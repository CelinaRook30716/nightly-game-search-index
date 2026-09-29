package dev.gameops.index;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public final class NightlyReindexService {
    private final InfraiGateway gateway;
    private final GameIndexPolicy policy;

    public NightlyReindexService(InfraiGateway gateway) {
        this.gateway = gateway;
        this.policy = new GameIndexPolicy();
    }

    public ReindexResult reindex(List<IndexCandidate> candidates) {
        List<IndexCandidate> accepted = policy.eligible(candidates);
        for (IndexCandidate candidate : accepted) {
            String content = gateway.scrape(candidate.sourceUrl());
            List<Float> embedding = gateway.embed(content);
            gateway.upsert(candidate, content, embedding);
        }
        return new ReindexResult(candidates.size(), accepted.size(),
                accepted.stream().map(IndexCandidate::id).toList());
    }

    public record ReindexResult(int evaluated, int indexed, List<String> indexedIds) { }
}
