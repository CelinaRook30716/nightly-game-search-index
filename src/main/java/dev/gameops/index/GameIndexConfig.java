package dev.gameops.index;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "game-index")
public record GameIndexConfig(
        String infraiBaseUrl,
        String collection,
        String embeddingModel,
        int embeddingDimension,
        String reindexTaskUrl,
        String cronExpression,
        String playerAssetsUrl,
        String liveEventsUrl,
        String moderationQueueUrl) {
}
