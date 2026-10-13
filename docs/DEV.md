# Local Development

## Prerequisites

- **JDK 21** — the Gradle toolchain provisions one if your system version is wrong
- **Docker** — used by docker-compose for Postgres, and by Testcontainers during the test suite
- **Node** — *not* required directly; the `node-gradle` plugin downloads its own copy on first build

Everything else (Kotlin compiler, Vite, Postgres image) is fetched on demand.

## One-time setup

```sh
cp dev.properties.example dev.properties
echo "AUTH_SESSION_SECRET=$(openssl rand -hex 32)" >> dev.properties
```

`dev.properties` is gitignored. The Gradle `run` and `seed` tasks read it and inject each entry as an environment variable on the JVM, mirroring how Cloud Run injects config in production. Pick a stable session secret and leave it alone — regenerating it invalidates every logged-in session.

If you want a bootstrapped admin user on first boot (so you can log in to the UI), set `AUTH_ADMIN_USERNAME` and `AUTH_ADMIN_PASSWORD` in `dev.properties`. The seeder only fires when the users table is empty; once an admin exists, those values are ignored.

## Database

```sh
docker compose up -d postgres
```

Postgres 18 listens on `localhost:5432` with user/db/password all set to `cwfgw4k` (matches the defaults in `application.yaml`). Flyway migrations run automatically the first time the app boots against an empty database.

## Populate seed data

```sh
./gradlew seed
```

Resets the docker-compose Postgres to a clean state and runs `SeedMain`, which creates a league plus the 2026 Spring (CSV) and 2026 Summer (TSV) seasons, pulls the real ESPN tournament calendar for each date range, imports the seed roster of full-name PGA players, and finalizes every tournament ESPN already reports as completed. Drops the named volume first, so this is destructive — only use it on the local docker-compose database.

## Run the backend

```sh
./gradlew run
```

Boots Ktor on `http://localhost:8080`. Serves the bundled React UI at `/` and the JSON API under `/api/v1/*`. The first run takes a minute (npm install + Vite build); subsequent runs are incremental.

## UI dev mode (hot reload)

In one terminal, run the backend as above. In another:

```sh
cd ui
npm run dev
```

Vite serves the UI at `http://localhost:5173` with hot module reload, proxying `/api/*` to the Ktor backend on `:8080`. Use this for any non-trivial UI work — the bundled-UI mode (`./gradlew run` only) is for verifying the production-shaped path before deploy.

## Verification

```sh
./gradlew check
```

Runs the full gate: backend tests + ktlint + detekt + Kover coverage verify + UI unit tests. This is the canonical command — `./gradlew test` skips lint, so don't trust it alone.

GitHub Actions runs the same gate on every pull request and on `main` after a merge (`.github/workflows/check.yml`, job `gradle check`). It adds `--rerun-tasks`, because the build cache is on and a cached `:test` verifies nothing, and then asserts from the log that `:test` and `:detekt` really ran. A pull request that changes only `.md` files reports the check without running it.

`scripts/setup-branch-protection.sh` records the protection `main` is meant to carry and applies it.

## Useful tasks

| Task | Description |
|------|-------------|
| `./gradlew run` | Run the backend locally, serving the bundled UI. |
| `./gradlew seed` | Reset Postgres and populate the 2026 Spring + Summer seasons end-to-end. |
| `./gradlew check` | Full verification gate (tests + lint + coverage + UI tests). |
| `./gradlew uiBuild` | Build the production UI bundle into `ui/dist/`. |
| `./gradlew uiTest` | Run UI unit tests with coverage. |
| `./gradlew buildFatJar` | Build the deployable fatJar (`build/libs/cwfgw4k-all.jar`). |
| `./gradlew generateJooq` | Regenerate jOOQ classes against a throwaway Postgres migrated by Flyway. |

## Deploying

`.github/workflows/deploy.yml` ships `main` to Cloud Run. It starts when the `Check` workflow passes for a push to `main`. It builds the fat jar and the image, tags the image with the commit SHA, runs `gcloud run deploy`, and then calls `/api/v1/health` on the new revision.

- **Approval.** The job runs in the `production` environment. While that environment has a required reviewer, each deploy waits in the Actions tab until it is approved or rejected.
- **By hand.** `gh workflow run deploy.yml` deploys the tip of `main`. Add `-f sha=<commit>` for an earlier commit; it must be on `main`.
- **Rolling back.** The quickest way is to route traffic to the previous revision: `gcloud run services update-traffic cwfgw4k --region us-west1 --to-revisions <revision>=100`. Traffic then stays pinned there, so run the same command with `--to-latest` once a fixed revision is out.
- **One-time setup.** `deploy/setup-github-deploy.sh` creates the service account and the Workload Identity Federation the workflow authenticates with, and prints the repository variables to set.

## Claude Code settings

`.claude/settings.json` is checked in, so a Claude Code session in a fresh clone behaves like one in any other. It applies once the folder is trusted. It sets:

- **Permissions.** An allow list of the specific git, gh, npm and Gradle subcommands the workflow uses, so they run without a prompt, and a deny list for force-push, `--amend`, `reset --hard` and `clean -fd`. The path rules cover the worktree root `~/code/cwfgw4k-wt` that the `parallel-tickets` skill uses, sibling repos under `~/code` for reading, and the session scratch directory.
- **Auto-compact at 200k tokens**, through `CLAUDE_CODE_AUTO_COMPACT_WINDOW`.
- **The status line**, drawn by `.claude/statusline.sh` (it needs `jq`): model and effort level, directory, branch, account, context used against the compact window, and rate-limit usage.
- **The code-review-graph MCP server**, enabled without a prompt; see the next section.

Personal overrides go in `.claude/settings.local.json`, which is gitignored. A clone kept somewhere other than `~/code` needs its own path rules there.

## Code-review graph (for Claude Code sessions)

The repo is wired for [code-review-graph](https://pypi.org/project/code-review-graph/), a local knowledge graph of the code that Claude Code queries for callers, callees, tests and the impact of a change. It is optional: every hook checks for the tool and does nothing when it is missing.

```sh
pipx install code-review-graph         # once per machine; the hooks call this binary
code-review-graph build                # once per clone; about two seconds
git config core.hooksPath .githooks    # once per clone
```

- `.mcp.json` starts the graph's MCP server for a Claude Code session through `uvx`, so [uv](https://docs.astral.sh/uv/) needs to be installed too. `.claude/settings.json` allows the server's read-only tools.
- The same settings file keeps the graph current: it runs an incremental update after each edit and prints the graph's status at session start. `.githooks/pre-commit` updates it before a commit.
- The graph lives in `.code-review-graph/`, which is gitignored. Delete the directory and rebuild if it ever looks wrong.
