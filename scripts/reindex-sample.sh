#!/usr/bin/env sh
set -eu

curl --fail-with-body --request POST "${SERVICE_URL:-http://localhost:8080}/internal/reindex" \
  --header 'Content-Type: application/json' \
  --data '{
    "candidates": [
      {"id":"ugc-map-2048","kind":"PLAYER_ASSET","state":"PUBLISHED","sourceUrl":"https://game.example/assets/ugc-map-2048"},
      {"id":"ugc-map-draft-9","kind":"PLAYER_ASSET","state":"DRAFT","sourceUrl":"https://game.example/assets/ugc-map-draft-9"},
      {"id":"event-summer-cup","kind":"LIVE_EVENT","state":"LIVE","sourceUrl":"https://game.example/events/summer-cup"},
      {"id":"mod-case-778","kind":"MODERATION_QUEUE","state":"ESCALATED","sourceUrl":"https://game.example/moderation/cases/778"}
    ]
  }'
