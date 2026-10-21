# Operational monitoring

Two Cloud Monitoring dashboards over the `cwfgw4k` Cloud Run service and the
`cwfgw4k-prod` Cloud SQL instance:

- **Operational Health** — is it up, is it slow, is it erroring? Powered by
  Cloud Run + Cloud SQL built-in metrics plus two log-based counters.
- **API Usage** — per-route traffic, latency, and error breakdowns, plus the
  response cache's events. Powered by three log-based metrics:
  `cwfgw4k_request_count` and `cwfgw4k_requests` over the request line
  `installRequestLogging` writes, and `cwfgw4k_cache` over the cache's event
  lines.

Nothing writes custom Cloud Monitoring metrics, so there's no per-metric
ingestion cost — the charts read from:

- **Cloud Run built-ins** (free): `request_count`, `request_latencies`,
  `instance_count`, `container/cpu/utilizations`, `container/memory/utilizations`,
  `container/startup_latencies`.
- **Cloud SQL built-ins** (free): `database/cpu/utilization`,
  `database/postgresql/num_backends`.
- **Five user log-based metrics** (free under the 20-metric/project quota):
  `cwfgw4k_unhandled_errors`, `cwfgw4k_espn_failures`, `cwfgw4k_requests`,
  `cwfgw4k_request_count`, `cwfgw4k_cache`. All but `cwfgw4k_requests` are
  counters; `cwfgw4k_requests` is a duration distribution (used for P95
  latency), and `cwfgw4k_request_count` is its counter sibling over the same
  log line — xyCharts need a scalar metric to rank series for top-N plotting,
  which distributions can't provide. `cwfgw4k_cache` counts the cache events
  emitted by `RequestCache` and `clearResponseCacheOnWrite` (hit, miss, put,
  discarded, sweep, clear and the rest its `event` label lists) and is labeled
  by event type and normalized route.

## One-time setup

Run the steps below against the project that hosts the service
(`cwfgw4k`, the `GCP_PROJECT_ID` repository variable). Adjust `--project` if you're
targeting a different environment.

### 1. Create the `unhandled_errors` log-based metric

Counts unhandled exceptions surfaced by `installErrorHandling`, which logs
`Unhandled error on {method} {path}` at ERROR severity for anything that escapes
the route handlers.

```bash
gcloud logging metrics create cwfgw4k_unhandled_errors \
  --project=cwfgw4k \
  --description='cwfgw4k unhandled exceptions from installErrorHandling' \
  --log-filter='resource.type="cloud_run_revision"
    AND resource.labels.service_name="cwfgw4k"
    AND severity>=ERROR
    AND jsonPayload.message:"Unhandled error on"'
```

### 2. Create the `espn_failures` log-based metric

Counts ESPN integration warnings/errors — calendar and scoreboard fetches —
logged by `EspnClient`, `EspnService`, and `AdminService`.
`message` holds only the line's text, so the logger and `severity>=ERROR`
clauses stand in for the logger name and level prefix a text line carried. A
stack trace is part of its entry, so one failure counts once, not once per line.

```bash
gcloud logging metrics create cwfgw4k_espn_failures \
  --project=cwfgw4k \
  --description='cwfgw4k ESPN integration failures' \
  --log-filter='resource.type="cloud_run_revision"
    AND resource.labels.service_name="cwfgw4k"
    AND severity>=WARNING
    AND (jsonPayload.message:"espn" OR jsonPayload.logger:"espn")
    AND (jsonPayload.message:"failed" OR jsonPayload.message:"error" OR jsonPayload.message:"returned"
      OR severity>=ERROR)'
```

### 3. Create the `cwfgw4k_requests` distribution metric

Powers the API Usage dashboard. The regex extractors map each
`cwfgw4k.request …` log line emitted by `installRequestLogging` to a
distribution point labeled by method, normalized route, and status class.

```bash
gcloud logging metrics create cwfgw4k_requests \
  --project=cwfgw4k \
  --config-from-file=ops/metrics/cwfgw4k_requests.yaml
```

### 4. Create the `cwfgw4k_request_count` counter metric

Sibling of `cwfgw4k_requests` against the same log line. Scalar counter typed,
used by the dashboard's xyChart panels (requests by status / method / route)
because distribution-typed metrics can't be ranked by Cloud Monitoring's
top-N picker on xyCharts.

```bash
gcloud logging metrics create cwfgw4k_request_count \
  --project=cwfgw4k \
  --config-from-file=ops/metrics/cwfgw4k_request_count.yaml
```

### 5. Create the `cwfgw4k_cache` counter metric

Counts cache events (hit, miss, put, discarded, sweep, clear, and the rest the
metric's `event` label lists) emitted by `RequestCache` and
`clearResponseCacheOnWrite`.
Labeled by `event` and normalized `route` so dashboards can compute hit
rate per endpoint.

```bash
gcloud logging metrics create cwfgw4k_cache \
  --project=cwfgw4k \
  --config-from-file=ops/metrics/cwfgw4k_cache.yaml
```

Suggested dashboard panels:

- **Cache hit rate by route** — `count(event=hit) / (count(event=hit) + count(event=miss))`
  per `route`. Healthy: ≥ 0.7 on hot endpoints; surprisingly low values usually
  mean the URI carries a high-cardinality query param the key doesn't normalize.
- **Cache writes per minute** — `count(event=put)` over time. Maps to upstream
  load (every put is a miss that called the underlying service / repo).
- **Sweep size** — `count(event=sweep)` is one per run; pair with the same log
  line's `deleted=N` if you want a row-count panel (regex-extract a separate
  metric for that).

Updating any of the five metrics later — `create` refuses an existing one, and
a metric counts only entries ingested after its filter changes, so apply an edit
in the same window as the deploy that changes the log line:

```bash
gcloud logging metrics update cwfgw4k_cache \
  --project=cwfgw4k \
  --config-from-file=ops/metrics/cwfgw4k_cache.yaml
```

The two filter-only metrics take `--log-filter='…'` in place of `--config-from-file`.

### 6. Import the dashboards

```bash
gcloud monitoring dashboards create \
  --project=cwfgw4k \
  --config-from-file=ops/dashboards/operational-health.json

gcloud monitoring dashboards create \
  --project=cwfgw4k \
  --config-from-file=ops/dashboards/api-usage.json
```

Each `create` mints a new dashboard id, so after the first import edits go
through `update`, which keeps the id and every bookmark to it. The live
dashboards are:

- `ops/dashboards/operational-health.json` — `projects/368240674777/dashboards/01067d35-ef50-4bef-9ae2-9dfd2780e90f`
- `ops/dashboards/api-usage.json` — `projects/368240674777/dashboards/cb4c689b-6a0d-4e4b-86de-f3aea0b9688b`

If a dashboard is ever recreated, look its new name up and replace it above:

```bash
gcloud monitoring dashboards list --project=cwfgw4k --format='value(name,displayName)'
```

Updating later — set the pair for the dashboard you edited, then check for
drift before applying. `update` replaces the whole dashboard, so any line the
diff shows besides your edit (`<` live, `>` repo) is a console change it would
overwrite; copy that into the repo file first.

```bash
name=projects/368240674777/dashboards/cb4c689b-6a0d-4e4b-86de-f3aea0b9688b
file=ops/dashboards/api-usage.json

norm='del(.etag, .name) | walk(if type == "object" then with_entries(select(.value | IN(0, false, "", {}, []) | not)) else . end)'
diff <(gcloud monitoring dashboards describe "$name" --project=cwfgw4k --format=json | jq -S "$norm") \
  <(jq -S "$norm" "$file")
```

The `norm` filter drops zero, `false`, empty-string and empty-collection fields
from both sides, because the server omits those defaults (and adds `style: {}`
to a text tile); without it every `xPos: 0` reads as drift.

`update` refuses a config without the live etag, which changes on every write,
so the repo file carries none; inject it into a temp copy:

```bash
etag=$(gcloud monitoring dashboards describe "$name" --project=cwfgw4k --format='value(etag)')
config=$(mktemp)
jq --arg etag "$etag" '. + {etag: $etag}' "$file" >| "$config"
gcloud monitoring dashboards update "$name" --project=cwfgw4k --config-from-file="$config"
rm -f "$config"
```

`>|` because `mktemp` has already created the file, and zsh's `noclobber`
refuses a plain `>` onto it.

### 7. Enable Cloud SQL Query Insights

Turns on per-statement latency + plan capture for the `cwfgw4k-prod`
instance. Query string length is pinned at the 4500-byte max so normalized
queries aren't truncated; client address + application tags are on so Cloud
Run traffic is distinguishable from local/psql sessions.

```bash
gcloud sql instances patch cwfgw4k-prod \
  --project=cwfgw4k \
  --insights-config-query-insights-enabled \
  --insights-config-query-string-length=4500 \
  --insights-config-record-application-tags \
  --insights-config-record-client-address
```

View at:
<https://console.cloud.google.com/sql/instances/cwfgw4k-prod/insights?project=cwfgw4k>

### 8. Configure alerting policies

Three policies in `ops/alerts/` cover the user-visible failure modes
without crying wolf on transient blips:

| Policy file | Fires when | Why |
| --- | --- | --- |
| `cwfgw4k-5xx-rate.yaml` | More than 5 5xx in any 5-min window | Catches sustained user-facing errors (wedges, deploy failures, ESPN cascades). Single 5xx is ignored. |
| `cwfgw4k-unhandled-errors.yaml` | Any unhandled exception in 5 min | Application bugs that escape `installErrorHandling`. Single occurrence fires — these are usually real. |
| `cwfgw4k-espn-failures.yaml` | More than 2 ESPN warnings/errors in 10 min | Sustained ESPN integration trouble (vs. the routine flake the live overlay already swallows). |

Notification routes through one SMS channel — recreate it if needed:

```bash
gcloud beta monitoring channels create \
  --project=cwfgw4k \
  --type=sms \
  --display-name="cwfgw4k oncall" \
  --channel-labels="number=+1XXXXXXXXXX"
```

The channel ID returned by that command needs to be substituted into
each policy's `notificationChannels` field before re-applying. The
ones currently in the YAMLs reference the channel from the May 4
setup.

Apply each policy:

```bash
gcloud monitoring policies create --project=cwfgw4k --policy-from-file=ops/alerts/cwfgw4k-5xx-rate.yaml
gcloud monitoring policies create --project=cwfgw4k --policy-from-file=ops/alerts/cwfgw4k-unhandled-errors.yaml
gcloud monitoring policies create --project=cwfgw4k --policy-from-file=ops/alerts/cwfgw4k-espn-failures.yaml
```

List installed policies:

```bash
gcloud monitoring policies list --project=cwfgw4k --format='table(displayName,name)'
```

## What's on the Operational Health dashboard

| Tile | Source | Reading it |
| --- | --- | --- |
| Active instances | `container/instance_count` | Should be 0 when idle (min-instances=0), scales up to max-instances=6. |
| 5xx rate (last 5 min) | `request_count`, `response_code_class=5xx` | Scorecard turns yellow at 0.1/s, red at 1.0/s. |
| P95 request latency | `request_latencies` | Scorecard turns yellow at 500 ms, red at 2000 ms. |
| Request rate by response class | `request_count`, grouped by class | Stacked area — compare 2xx/3xx/4xx/5xx shape. |
| Request latency P50 / P95 / P99 | `request_latencies` | Three lines; P99 up while P95 flat = tail-heavy pathology. |
| CPU / Memory utilization | `container/cpu/utilizations`, `container/memory/utilizations` | P95 per instance. Sustained >80% memory with max-instances=6 → OOM risk. |
| Container startup latency | `container/startup_latencies` | Spikes correspond to cold starts; empty when warm. |
| Cloud SQL CPU / connections | `cloudsql.googleapis.com/database/...` | Connection spikes without traffic spikes suggest a pool leak. |
| Unhandled errors | `cwfgw4k_unhandled_errors` | Any non-zero value means something escaped `installErrorHandling`. |
| ESPN integration failures | `cwfgw4k_espn_failures` | Sustained rate = ESPN API sick; one-off spikes are normal. |

## What's on the API Usage dashboard

| Tile | Source | Reading it |
| --- | --- | --- |
| Requests / min (last 5 min avg) | `cwfgw4k_request_count` | Running traffic baseline. |
| 4xx / 5xx rate (last 5 min) | `cwfgw4k_request_count`, filtered by `status_class` | Thresholds colored like operational-health. |
| Top routes by request rate | `cwfgw4k_request_count` grouped by method + route | Sorted table — top talkers. |
| P95 latency by route | `cwfgw4k_requests` as a distribution | Which endpoint is slow, not just the service aggregate. |
| Requests by status class | `cwfgw4k_request_count` grouped by `status_class` | Stacked area over time. |
| Requests by HTTP method | `cwfgw4k_request_count` grouped by `method` | GET vs POST vs PUT traffic split. |
| 4xx / 5xx responses by route | `cwfgw4k_request_count` filtered + grouped by route | Pinpoint the failing endpoint. |
| Cache events over time | `cwfgw4k_cache` grouped by `event` | Stacked area, one band per `event`. |
| Cache hit rate by route | `cwfgw4k_cache`, `hit` and `miss` per route | Low on a hot route usually means a high-cardinality query param in the URI. |
| Cache writes (put) by route | `cwfgw4k_cache` filtered to `put`, grouped by route | Every put is a miss that ran the report build. |

The `route` label is normalized by the top-level `normalizeRoute` in
`http/RequestLogging.kt`:
UUIDs become `:id`, pure-numeric segments become `:n`, and Vite hashed assets
under `/assets/` collapse to one bucket. That keeps cardinality bounded across
deploys.

## Load testing against an ephemeral staging clone

`ops/load-test/spin-staging.sh` clones the prod Cloud SQL instance into a
fresh, timestamped staging instance, deploys an ephemeral Cloud Run
revision pointing at the clone, runs `ops/load-test/run.sh` against it,
and tears everything down on exit (trap-based, so Ctrl-C and failures
clean up too). Use it to test config or code changes against realistic
data without touching the live service.

```bash
# Default 10 concurrency, 30s duration. Defaults match prod provisioning
# except min-instances=1 to remove cold-start variance from measurements.
ops/load-test/spin-staging.sh

# Heavier burst.
ops/load-test/spin-staging.sh -c 25 -d 60s

# Skip teardown for poking at the staging revision afterward.
ops/load-test/spin-staging.sh --keep

# List every staging env still alive in the project.
ops/load-test/spin-staging.sh --list

# Tear one down by its YYYYMMDD-HHMMSS suffix (full instance name also accepted).
ops/load-test/spin-staging.sh --teardown 20260503-141500
```

What it creates (deleted on exit unless `--keep`):
- Cloud SQL instance: `staging-YYYYMMDD-HHMMSS` (clone of `cwfgw4k-prod`)
- Cloud Run service: `cwfgw4k-staging-YYYYMMDD-HHMMSS`

Cost: roughly $1–2 per run. The clone bring-up is the dominant ~5–10 min
the staging instance is alive; teardown happens promptly after the burst.

Requires the caller to have these IAM roles on the cwfgw4k project:
`Cloud SQL Admin`, `Cloud Run Admin`, `Secret Manager Secret Accessor`.

### Reproducing a wedge with `stress.sh`

`run.sh` walks endpoints sequentially against a single tournament_id, so
the access pattern that wedged prod on May 3 (multi-tournament parallel
fan-out, every non-completed tournament's `report/<tid>?live=true`
rebuilding at once) never shows up. `ops/load-test/stress.sh` is the
alternate driver — it auto-discovers every non-completed tournament in
the season and fires one
parallel `hey` process per URL plus the cheap uncached endpoints
(`/leagues`, `/tournaments`) so we can see whether they starve while
the heavy paths are inflight.

```bash
# Spin up a staging clone and keep it.
ops/load-test/spin-staging.sh --keep

# Run stress against it (URL printed by spin-staging).
ops/load-test/stress.sh https://cwfgw4k-staging-... <SEASON_UUID> -c 10 -d 300s

# Tear down when done.
ops/load-test/spin-staging.sh --teardown <SUFFIX>
```

What to watch in Cloud Logging during the run:
- `cwfgw4k.db.pool waiting=N` sustained > 0 — pool exhaustion in progress
- 504s on `/api/v1/leagues` or `/api/v1/tournaments` — wedge reproduced
- `cwfgw4k.espn event=hit` count vs `event=miss` — ESPN cache effectiveness

### Reproducing the idle-then-hit wedge with `soak.sh`

`stress.sh` keeps an instance pegged so connections never get a chance
to age out. The May 4 prod wedge was the opposite shape: instance alive
but quiet, pool slowly fills with stale sockets, then the first new
request after the idle window hangs for 90s while Hikari refills (and
occasionally hits Broken Pipe during the TLS handshake to Cloud SQL via
the in-process socket factory). `ops/load-test/soak.sh` reproduces
that timeline end-to-end.

Cloud Run throttles an idle instance's CPU, so the pool keeps no
connection idle for more than 30s and pings none, and the cache sweep
runs only on an API request. An idle instance should log
`cwfgw4k.db.pool … total=0` within about a minute of its last request,
and its first canary opens a fresh connection on request CPU: no
`cwfgw4k.db.slow_acquire` line, no Broken Pipe.

For the same reason every Cloud SQL JDBC URL sets `cloudSqlRefreshStrategy=lazy`:
the connector's default refreshes its certificate on a background thread, which
throttling starves. Any other value silently falls back to that default.

```bash
# Default: 30-min idle, 5-min canary window @ 30s interval.
ops/load-test/soak.sh

# Longer idle, longer canary window:
ops/load-test/soak.sh -i 60 -w 600 -c 60

# Leave staging up afterward to poke at:
ops/load-test/soak.sh --keep
```

A wedge looks like one row in the canary table at >5s flanked by
sub-second rows. A healthy run shows every canary <1s. Staging
instances created by soak.sh use `min-instances=1`, where production
runs `min-instances=0`: it keeps one instance alive and throttled for
the whole idle window, the shape a production instance has between
its last request and scale-in. With 0, the instance could scale in
and the first canary would measure a cold start instead.

## Cost expectations

At this app's traffic, everything on this dashboard is under GCP's free tier:

- Cloud Monitoring charges only for non-GCP metrics (the ones we're using are
  all Google-supplied).
- User log-based metrics are free up to 20 per project; we use 5.
- Log ingestion's free tier is 50 GiB/project/month; a low-traffic Cloud Run
  service logs a tiny fraction of that.

If custom metrics (OpenTelemetry, Prometheus) are added later, those count
against the 150 MB/billing-account/month free tier before billing kicks in at
$0.258/MB.
