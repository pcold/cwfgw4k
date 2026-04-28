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
