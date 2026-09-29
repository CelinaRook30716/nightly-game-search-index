package dev.gameops.index;

import java.net.URI;

public record IndexCandidate(String id, Kind kind, State state, URI sourceUrl) {
    public enum Kind { PLAYER_ASSET, LIVE_EVENT, MODERATION_QUEUE }
    public enum State { PUBLISHED, DRAFT, SCHEDULED, LIVE, CLOSED, ESCALATED, RESOLVED }
}
