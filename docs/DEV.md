# Local Development

## Prerequisites

- **JDK 25** — install it yourself where Gradle can detect it (`JAVA_HOME` or a standard install location); no toolchain resolver is configured, so nothing downloads one
- **Docker** — used by docker-compose for Postgres, and by Testcontainers during the test suite
- **Node** — *not* required directly; the `node-gradle` plugin downloads its own copy on first build

Everything else (Kotlin compiler, Vite, Postgres image) is fetched on demand.

## One-time setup

```sh
cp dev.properties.example dev.properties
echo "AUTH_SESSION_SECRET=$(openssl rand -hex 32)" >> dev.properties
```

`dev.properties` is gitignored. The Gradle `run` and `seed` tasks read it and inject each entry as an environment variable on the JVM, mirroring how Cloud Run injects config in production. Pick a stable session secret and leave it alone — regenerating it invalidates every logged-in session.

If you want a bootstrapped admin user on first boot (so you can log in to the UI), set `AUTH_ADMIN_USERNAME` and `AUTH_ADMIN_PASSWORD` in `dev.properties`. The seeder only fires when the users table is empty; once an admin exists, it ignores them. Set both or neither: the app refuses to boot with only one, even after an admin exists.

## Database

```sh
docker compose up -d postgres
```

Postgres 18 listens on `localhost:5432` with user/db/password all set to `cwfgw4k` (matches the defaults in `application.yaml`). Flyway migrations run automatically the first time the app boots against an empty database.

[`db/schema.sql`](../db/schema.sql) is the schema those migrations add up to: `pg_dump --schema-only`, run inside a throwaway Postgres the chain has migrated. It is generated, never edited; a correction goes in the next migration. After adding a migration, run `./gradlew updateSchemaDump` and commit the result. `check` runs `checkSchemaDump`, which fails when the two disagree.

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
./gradlew check --rerun-tasks
```

Runs the full gate: backend tests + ktlint + detekt + Kover coverage verify + the schema-dump check (`checkSchemaDump`) + UI lint (`uiLint`) + UI unit tests. This is the canonical command — `./gradlew test` skips coverage and the UI tests, so don't trust it alone. `--rerun-tasks` is part of it: the build cache is on, and a cached or up-to-date `:test` verifies nothing. [`.claude/CLAUDE.md`](../.claude/CLAUDE.md) → Verification says how to read the result.

GitHub Actions runs the same gate on every pull request and on `main` after a merge (`.github/workflows/check.yml`, job `gradle check`), and then asserts from the log that `:test` and `:detekt` really ran. It reports the check without running it for a pull request that changes only `.md` files, and for a push to `main` whose exact tree an earlier run already passed.

`scripts/setup-branch-protection.sh` records the protection `main` is meant to carry and applies it.

## Useful tasks

| Task | Description |
|------|-------------|
| `./gradlew run` | Run the backend locally, serving the bundled UI. |
| `./gradlew seed` | Reset Postgres and populate the 2026 Spring + Summer seasons end-to-end. |
| `./gradlew check --rerun-tasks` | Full verification gate (tests + lint + coverage + schema dump + UI lint + UI tests). |
| `./gradlew uiBuild` | Build the production UI bundle into `ui/dist/`. |
| `./gradlew uiTest` | Run UI unit tests with coverage. |
| `./gradlew buildFatJar` | Build the deployable fatJar (`build/libs/cwfgw4k-all.jar`). |
| `./gradlew generateJooq` | Regenerate jOOQ classes against a throwaway Postgres migrated by Flyway. |
| `./gradlew updateSchemaDump` | Regenerate the tracked `db/schema.sql` from the migrations. |

## Deploying

`.github/workflows/deploy.yml` ships `main` to Cloud Run. It starts when the `Check` workflow passes for a push to `main`. It builds the fat jar and the image, tags the image with the commit SHA, runs `gcloud run deploy`, and then calls `/api/v1/health` on the service URL, which serves the new revision unless traffic is pinned to another.

- **Approval.** The job runs in the `production` environment. While that environment has a required reviewer, each deploy waits in the Actions tab until it is approved or rejected.
- **By hand.** `gh workflow run deploy.yml` deploys the tip of `main`. Add `-f sha=<commit>` for an earlier commit; it must be on `main`.
- **Rolling back.** The quickest way is to route traffic to the previous revision: `gcloud run services update-traffic cwfgw4k --region us-west1 --to-revisions <revision>=100`. Traffic then stays pinned there, so run the same command with `--to-latest` once a fixed revision is out.
- **One-time setup.** `deploy/setup-github-deploy.sh` creates the service account and the Workload Identity Federation the workflow authenticates with, and prints the repository variables to set.

## Claude Code settings

`.claude/settings.json` is checked in, so a Claude Code session in a fresh clone behaves like one in any other. The deny list applies at once; the rest applies once the folder is trusted. It sets:

- **Permissions, allowed.** The git, gh, npm and npx subcommands the workflow uses; every `./gradlew` task; `sed -n` and `mkdir -p`; a handful of exact commands for checking on and stopping a locally run jar; read-only `gcloud run` and `gcloud logging` queries; `rtk`'s own reporting commands; the code-review graph's read-only tools; and the Linear tools for reading issues and saving issues, comments and projects. The path rules allow reading anywhere under `~/code`, `~/.claude` and the temp directories, and editing in the worktree root `~/code/cwfgw4k-wt` that the `parallel-tickets` skill uses and under `/private/tmp/claude-501`, which holds each session's scratch directory.
- **Permissions, denied.** Force-push in its common spellings (`--force`, `-f`, a `+` refspec, behind `git -C`), `commit --amend`, `reset --hard`, `clean -fd`, `rtk proxy` (which would run any of those past the deny list), and reading the Gradle wrapper jar.
- **No rule for a bare command.** Plain `ls`, `cat`, `head`, `tail`, `wc`, `sort`, `grep`, `find` and `echo` are in Claude Code's built-in read-only set and run without a prompt. Write-capable forms (`find -exec`, a redirect outside the editable paths) and commands such as `cp`, `mv`, `tee` and `awk` are not pre-approved. A `Bash(<verb> *)` rule would pre-approve every spelling of the verb, including the ones a deny rule's text misses, so [`.claude/CLAUDE.md`](../.claude/CLAUDE.md) → Agent permissions forbids one. `./gradlew` is the one whole-tool rule, since no deny rule guards it.
- **Auto-compact at 200k tokens**, through `CLAUDE_CODE_AUTO_COMPACT_WINDOW`.
- **The status line**, drawn by `.claude/statusline.sh` (it needs `jq`): model and effort level, directory, branch, account, context used against the compact window, and rate-limit usage.
- **The code-review-graph MCP server**, enabled without a prompt; see the next section.

Personal overrides go in `.claude/settings.local.json`, which is gitignored. A clone kept somewhere other than `~/code` needs its own path rules there.

## Code-review graph (for Claude Code sessions)

The repo is wired for [code-review-graph](https://pypi.org/project/code-review-graph/), a local knowledge graph of the code that Claude Code queries for callers, callees, tests and the impact of a change. It is optional: each of its hooks checks for the tool and does nothing when it is missing.

```sh
pipx install code-review-graph         # once per machine; the hooks call this binary
code-review-graph build                # once per clone; about two seconds
git config core.hooksPath .githooks    # once per clone
```

- `.mcp.json` starts the graph's MCP server for a Claude Code session through `uvx`, so [uv](https://docs.astral.sh/uv/) needs to be installed too. `.claude/settings.json` allows the server's read-only tools.
- The same settings file keeps the graph current: it runs an incremental update after each edit and prints the graph's status at session start. `.githooks/pre-commit` updates it before a commit.
- The graph lives in `.code-review-graph/`, which is gitignored. Delete the directory and rebuild if it ever looks wrong.
- `core.hooksPath` switches on every hook in `.githooks`, and `pre-push` is not one of the graph's: it refuses to push a branch other than `main` that is not named `<user>/cwf-<num>-<slug>`, the name Linear gives an issue. `.github/workflows/branch-name-check.yml` checks the same pattern on a pull request.
