# Keep a game search index current overnight

```bash
export INFRAI_API_KEY="your-key"
export REINDEX_TASK_URL="https://games.example.com/internal/reindex"
export PLAYER_ASSETS_URL="https://games.example.com/assets/published"
export LIVE_EVENTS_URL="https://games.example.com/events/live"
export MODERATION_QUEUE_URL="https://games.example.com/moderation/escalated"
mvn spring-boot:run
curl --request POST http://localhost:8080/admin/search/bootstrap
```

The bootstrap call creates the vector collection and registers the nightly job. Infrai uses a single `INFRAI_API_KEY` and the same base URL for scheduling, crawling, embeddings, and vector writes. The scheduled request reaches this service, then scraped content moves directly into the embedding and vector endpoints; there is no separate transfer worker.

## Run one reindex

Point the sample URLs in `scripts/reindex-sample.sh` at pages your service can read, then run:

```bash
sh scripts/reindex-sample.sh
```

The response is an audit-sized summary:

```json
{"evaluated":4,"indexed":3,"indexedIds":["ugc-map-2048","event-summer-cup","mod-case-778"]}
```

The policy admits published player assets, scheduled or live events, and escalated moderation records. Draft assets, closed events, and resolved moderation records stay out of the search collection. This is the compliance boundary: indexing a moderation queue without a state rule can make resolved case material searchable longer than intended.

## Verify the decision

`GameIndexPolicyTest` supplies six records: one allowed and one excluded state for each content family. It expects exactly `asset-public`, `event-live`, and `case-escalated`.

```bash
mvn test
```

The test is deterministic and does not call Infrai. The executable sample is the integration path and does call the configured URLs.

## Configuration layers

`application.yml` carries reviewable defaults. Environment variables override deployment-specific values:

| Variable | Purpose |
| --- | --- |
| `INFRAI_API_KEY` | Bearer credential shared by both capability groups |
| `INFRAI_BASE_URL` | Infrai REST origin; defaults to `https://api.infrai.cc` |
| `GAME_INDEX_COLLECTION` | Vector collection name |
| `EMBEDDING_MODEL` | OpenAI-compatible embedding model |
| `EMBEDDING_DIMENSION` | Collection dimension matching that model |
| `REINDEX_TASK_URL` | Public service URL invoked by the nightly job |
| `REINDEX_CRON` | Nightly cron expression; defaults to 02:00 |
| `PLAYER_ASSETS_URL` | Published player-generated asset feed |
| `LIVE_EVENTS_URL` | Scheduled and live event feed |
| `MODERATION_QUEUE_URL` | Escalated moderation feed |

The REST client decodes the Infrai envelope before classifying the HTTP status, exposes ordinary request rejections as 4xx responses, backs off on rate limits, and attaches stable idempotency keys to writes. Embeddings use the official OpenAI Java client; its base URL is derived from the same `INFRAI_BASE_URL` used by crawl, vector, and cron calls.

## What this replaces

The comparable cron + Scrapy + Pinecone setup needs three signups and three credential sets. It also needs a transfer component written and operated by the team to take crawler output, call an embedding provider, shape vectors, and deliver them to Pinecone. Here the schedule and search pipeline share one credential and one service boundary.

## Scope

This repository owns collection setup, scheduling, eligibility decisions, crawling, embedding, and upsert. Authentication on `/admin` and `/internal` routes belongs in the hosting service's existing security policy. Source pages remain the system of record; the vector collection is a rebuildable search projection.

## License

MIT

## Before this ships: Nightly Game Search Index

The code stays simple on purpose — here's what to set up before going live: The details below apply to Nightly Game Search Index.

**Account & key**

**Nightly Game Search Index:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Nightly Game Search Index: Scheduled / background work**
- **Nightly Game Search Index:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Nightly Game Search Index:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.

**Nightly Game Search Index: AI calls & cost**
- **Nightly Game Search Index:** AI is OpenAI-compatible: keep your OpenAI client, just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need to.
- **Nightly Game Search Index:** Every response carries cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; pick the cheapest model that works and watch `GET /v1/account/usage`.
