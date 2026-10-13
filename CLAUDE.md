<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes_tool` or `query_graph_tool` instead of Grep
- **Understanding impact**: `get_impact_radius_tool` instead of manually tracing imports
- **Code review**: `detect_changes_tool` + `get_review_context_tool` instead of reading entire files
- **Finding relationships**: `query_graph_tool` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview_tool` + `list_communities_tool`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
| ------ | ---------- |
| `detect_changes_tool` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context_tool` | Need source snippets for review — token-efficient |
| `get_impact_radius_tool` | Understanding blast radius of a change |
| `get_affected_flows_tool` | Finding which execution paths are impacted |
| `query_graph_tool` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes_tool` | Finding functions/classes by name or keyword |
| `get_architecture_overview_tool` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via `.claude/settings.json`'s
   `PostToolUse` hook, `code-review-graph update --skip-flows`) and reports
   status at session start (`SessionStart` hook). A `.githooks/pre-commit` hook
   also runs `code-review-graph update` + `detect-changes --brief` before every
   commit (non-blocking).
2. Use `detect_changes_tool` for code review.
3. Use `get_affected_flows_tool` to understand impact.
4. Use `query_graph_tool` pattern="tests_for" to check coverage.
5. If a graph tool errors (uninitialized graph — a fresh clone before the first
   build, or a rebuild genuinely in progress), fall back to Grep/Glob/Read for
   that call rather than blocking.

### Where this is actually wired in

The `cwf-review` skill (`.claude/skills/`) calls these tools at specific steps
in its procedure, and the CI review in `.github/workflows/claude-review.yml`
builds the graph and uses the same ones. The generic `debug-issue` /
`explore-codebase` / `refactor-safely` skills are standalone entry points for
those specific tasks when invoked directly; they are not part of the review gate.

The code style guide lives in [`.claude/CLAUDE.md`](.claude/CLAUDE.md).
