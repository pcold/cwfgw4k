# Fence checklist

Collisions a footprint map misses and brief wording that has misfired. Phase 1 of
[`SKILL.md`](SKILL.md) reads the first list; Phase 3 reads the second.

## Phase 1 — footprints and fences

- Diff a branch against its merge-base (`git diff --name-only $(git merge-base HEAD origin/main)..HEAD`),
  never against `origin/main`: behind main, the branch shows main's new files as its own.
- Backlog does not mean free. Check `gh pr list --json headRefName` and the remote branches for
  `cwf-<num>`; a peer session may claim a ticket without moving it, so treat a freshly filed cluster as taken.
- Adding a service edits its package's `*Support.kt` and `AppServices.kt`; adding a repository or other
  leaf also edits `Main.kt` and `testing/ApiTest.kt`; adding a route edits `App.kt`. One such ticket per wave.
- Wire types live in each domain's `XModels.kt`, and `ui/src/shared/api/types.ts` mirrors them all by
  hand in one file. A ticket that changes a response shape takes both, and the domain's
  `shared/api/client/<domain>.ts` slice when it adds a call (`client/index.ts` too if it adds a slice).
- `docs/ARCHITECTURE.md` lists every endpoint in one HTTP table, so a ticket that adds or changes a
  route has it in its fence. Landing the code without it turns that doc false.
- A file that holds one entry per case of a type is taken by every ticket adding a case, whatever it
  references today.
- Grant a subsystem's `src/test/**` to one agent per wave and name files inside it for the others. A
  fence around a sealed type also names its exhaustive-`when` sites, tests included.
- A repository interface drags in its `Fake`, which sits beside the domain's specs. Name it, in or out.
- A wave that will not widen usually shares one file. Run `sort <footprints> | uniq -c | sort -rn` and
  file that file's split as its own ticket.
- To close a construction path, land the enabling idiom first, convert callers per domain next, and seal
  last. Sealing first forces every domain into one diff.
- A doc that pre-describes the ticket's work is in its fence: landing the code turns that plan prose
  false. A new doc file never collides.
- When a ticket adds a table's first writer, consumer or column, grep that table's migration comments
  for deferrals it now meets. File each as a sub-issue, not as scope in this diff.

## Phase 3 — the brief and rulings

- Assign each agent's Flyway version in its brief, or serialize migrations:
  concurrent agents pick the same next number. `ls <worktrees>/*/src/main/resources/db/migration/`.
- Before briefing a CHECK widening, grep the constraint suffix across `db/migration/` and put the count
  and names in the brief: one rule is often several CHECKs.
- A deletion ticket `git grep`s each deleted name across `src/`, `ui/src/` and `docs/` and grants the
  citing files, doc-only. Prose cites by name, and the graph does not index comments.
- Put fence carve-outs inside the STOP sentence, not after it; agents act on the first rule they read.
  Check an "outside my fence" claim against the do-not-modify list before ruling on it.
- A brief that declares an enumeration authoritative maps every arm, including absent/null and the
  idempotent no-op. An undefined arm comes back as a late reversal.
- Pin the value the brief's own required change produces. A "stop if this count moves" computed before
  that change fires on the correct diff.
- Before framing "choose between these precedents", quote both on the ticket's axis. If they agree, the
  brief asks for the convergent fix.
- Before ruling for the in-fence option, check its inputs can tell the broken state from the healthy one.
  A new field on the input type usually costs more out-of-fence surface than the option avoided.
- Before overturning an agent's claim, chase the value to the write: a `= null` parameter default says
  what the caller may omit, not what gets stored.
