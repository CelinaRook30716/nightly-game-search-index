package dev.gameops.index;

import java.util.List;

public final class GameIndexPolicy {
    public List<IndexCandidate> eligible(List<IndexCandidate> candidates) {
        return candidates.stream().filter(this::isEligible).toList();
    }

    private boolean isEligible(IndexCandidate candidate) {
        return switch (candidate.kind()) {
            case PLAYER_ASSET -> candidate.state() == IndexCandidate.State.PUBLISHED;
            case LIVE_EVENT -> candidate.state() == IndexCandidate.State.SCHEDULED
                    || candidate.state() == IndexCandidate.State.LIVE;
            case MODERATION_QUEUE -> candidate.state() == IndexCandidate.State.ESCALATED;
        };
    }
}
