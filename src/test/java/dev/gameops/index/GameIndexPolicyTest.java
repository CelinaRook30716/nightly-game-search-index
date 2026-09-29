package dev.gameops.index;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class GameIndexPolicyTest {
    @Test
    void indexes_public_live_and_escalated_records_only() {
        List<IndexCandidate> input = List.of(
                candidate("asset-public", IndexCandidate.Kind.PLAYER_ASSET, IndexCandidate.State.PUBLISHED),
                candidate("asset-draft", IndexCandidate.Kind.PLAYER_ASSET, IndexCandidate.State.DRAFT),
                candidate("event-live", IndexCandidate.Kind.LIVE_EVENT, IndexCandidate.State.LIVE),
                candidate("event-closed", IndexCandidate.Kind.LIVE_EVENT, IndexCandidate.State.CLOSED),
                candidate("case-escalated", IndexCandidate.Kind.MODERATION_QUEUE, IndexCandidate.State.ESCALATED),
                candidate("case-resolved", IndexCandidate.Kind.MODERATION_QUEUE, IndexCandidate.State.RESOLVED));

        List<String> ids = new GameIndexPolicy().eligible(input).stream().map(IndexCandidate::id).toList();

        assertThat(ids).containsExactly("asset-public", "event-live", "case-escalated");
    }

    private static IndexCandidate candidate(String id, IndexCandidate.Kind kind, IndexCandidate.State state) {
        return new IndexCandidate(id, kind, state, URI.create("https://game.example/content/" + id));
    }
}
