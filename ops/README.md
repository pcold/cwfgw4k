# Operational monitoring

Two Cloud Monitoring dashboards over the `cwfgw4k` Cloud Run service and the
`cwfgw4k-prod` Cloud SQL instance:

- **Operational Health** — is it up, is it slow, is it erroring? Powered by
  Cloud Run + Cloud SQL built-in metrics plus two log-based counters.
- **API Usage** — per-route traffic, latency, and error breakdowns. Powered by
  a single log-based distribution metric populated by
  `installRequestLogging` in the app.

Nothing writes custom Cloud Monitoring metrics, so there's no per-metric
ingestion cost — the charts read from:

- **Cloud Run built-ins** (free): `request_count`, `request_latencies`,
  `instance_count`, `container/cpu/utilizations`, `container/memory/utilizations`,
  `container/startup_latencies`.
- **Cloud SQL built-ins** (free): `database/cpu/utilization`,
  `database/postgresql/num_backends`.
- **Four user log-based metrics** (free under the 20-metric/project quota):
  `cwfgw4k_unhandled_errors`, `cwfgw4k_espn_failures`, `cwfgw4k_requests`,
  `cwfgw4k_request_count`. The first three are counters; `cwfgw4k_requests` is a
  duration distribution (used for P95 latency), and `cwfgw4k_request_count` is
  its counter sibling over the same log line — xyCharts need a scalar metric
  to rank series for top-N plotting, which distributions can't provide.

## One-time setup

Run these three commands against the project that hosts the service
(`cwfgw4k`, based on `cloudbuild.yaml`). Adjust `--project` if you're
targeting a different environment.

### 1. Create the `unhandled_errors` log-based metric

Counts unhandled exceptions surfaced by `installErrorHandling`, which logs
`Unhandled error on {method} {uri}` at ERROR severity for anything that escapes
the route handlers.

```bash
gcloud logging metrics create cwfgw4k_unhandled_errors \
  --project=cwfgw4k \
  --description='cwfgw4k unhandled exceptions from ErrorHandlingMiddleware' \
  --log-filter='resource.type="cloud_run_revision"
    AND resource.labels.service_name="cwfgw4k"
    AND severity>=ERROR
    AND textPayload:"Unhandled error on"'
```

### 2. Create the `espn_failures` log-based metric

Counts ESPN integration warnings/errors — calendar fetches, scoreboard fetches,
athlete lookups — logged by `EspnClient`, `EspnService`, and `AdminService`.

```bash
gcloud logging metrics create cwfgw4k_espn_failures \
  --project=cwfgw4k \
  --description='cwfgw4k ESPN integration failures' \
  --log-filter='resource.type="cloud_run_revision"
    AND resource.labels.service_name="cwfgw4k"
    AND severity>=WARNING
    AND (textPayload:"ESPN" OR textPayload:"espn")
    AND (textPayload:"failed" OR textPayload:"error" OR textPayload:"returned")'
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

### 5. Import the dashboards

```bash
gcloud monitoring dashboards create \
  --project=cwfgw4k \
  --config-from-file=ops/dashboards/operational-health.json

gcloud monitoring dashboards create \
  --project=cwfgw4k \
  --config-from-file=ops/dashboards/api-usage.json
```

### 6. Enable Cloud SQL Query Insights

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

Updating later:

```bash
# Find the dashboard name (projects/…/dashboards/…)
gcloud monitoring dashboards list --project=cwfgw4k \
  --filter='displayName:"cwfgw4k — Operational Health"' --format='value(name)'

# Apply edits
gcloud monitoring dashboards update <dashboard-name> \
  --project=cwfgw4k \
  --config-from-file=ops/dashboards/operational-health.json
```

## What's on the Operational Health dashboard

| Tile | Source | Reading it |
| --- | --- | --- |
| Active instances | `container/instance_count` | Should be 0 when idle (min-instances=0), scales up to max-instances=2. |
| 5xx rate (last 5 min) | `request_count`, `response_code_class=5xx` | Scorecard turns yellow at 0.1/s, red at 1.0/s. |
| P95 request latency | `request_latencies` | Scorecard turns yellow at 500 ms, red at 2000 ms. |
| Request rate by response class | `request_count`, grouped by class | Stacked area — compare 2xx/3xx/4xx/5xx shape. |
| Request latency P50 / P95 / P99 | `request_latencies` | Three lines; P99 up while P95 flat = tail-heavy pathology. |
| CPU / Memory utilization | `container/cpu/utilizations`, `container/memory/utilizations` | P95 per instance. Sustained >80% memory with max-instances=2 → OOM risk. |
| Container startup latency | `container/startup_latencies` | Spikes correspond to cold starts; empty when warm. |
| Cloud SQL CPU / connections | `cloudsql.googleapis.com/database/...` | Connection spikes without traffic spikes suggest a pool leak. |
| Unhandled errors | `cwfgw4k_unhandled_errors` | Any non-zero value means something escaped `installErrorHandling`. |
| ESPN integration failures | `cwfgw4k_espn_failures` | Sustained rate = ESPN API sick; one-off spikes are normal. |

## What's on the API Usage dashboard

| Tile | Source | Reading it |
| --- | --- | --- |
| Requests / min (last 5 min avg) | `cwfgw4k_requests` count | Running traffic baseline. |
| 4xx / 5xx rate (last 5 min) | `cwfgw4k_requests`, filtered by `status_class` | Thresholds colored like operational-health. |
| Top routes by request rate | `cwfgw4k_requests` grouped by method + route | Sorted table — top talkers. |
| P95 latency by route | `cwfgw4k_requests` as a distribution | Which endpoint is slow, not just the service aggregate. |
| Requests by status class | `cwfgw4k_requests` grouped by `status_class` | Stacked area over time. |
| Requests by HTTP method | `cwfgw4k_requests` grouped by `method` | GET vs POST vs PUT traffic split. |
| 4xx / 5xx responses by route | `cwfgw4k_requests` filtered + grouped by route | Pinpoint the failing endpoint. |

The `route` label is normalized by `installRequestLogging.normalizeRoute`:
UUIDs become `:id`, pure-numeric segments become `:n`, and Vite hashed assets
under `/assets/` collapse to one bucket. That keeps cardinality bounded across
deploys.

## Cost expectations

At this app's traffic, everything on this dashboard is under GCP's free tier:

- Cloud Monitoring charges only for non-GCP metrics (the ones we're using are
  all Google-supplied).
- User log-based metrics are free up to 20 per project; we use 2.
- Log ingestion's free tier is 50 GiB/project/month; a low-traffic Cloud Run
  service logs a tiny fraction of that.

If custom metrics (OpenTelemetry, Prometheus) are added later, those count
against the 150 MB/billing-account/month free tier before billing kicks in at
$0.258/MB.
