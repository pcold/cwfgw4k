# Castlewood Fantasy Golf (CWFGW4K)

A fantasy PGA Tour league tracker. Configurable number of teams draft real PGA players and compete for weekly payouts based on tournament results. Kotlin/Ktor backend, React/Vite frontend, single-fatJar deploy.

> **[Architecture](docs/ARCHITECTURE.md)** — domain, persistence, tournament lifecycle, the league's payout and side-bet rules, ESPN integration, and every HTTP endpoint.
>
> **[Local Development Setup](docs/DEV.md)** — prerequisites, dev.properties, docker-compose, seed data, dev vs bundled UI workflows.

## League Rules

The draft happens outside the app; an admin uploads the resulting rosters. Each tournament's top finishers pay the teams that own them, ties and shared ownership split the payout, and the weekly ledger is zero-sum. The rules, with the code and specs that enforce them, are in [Architecture § Scoring and Payouts](docs/ARCHITECTURE.md#7-scoring-and-payouts).
