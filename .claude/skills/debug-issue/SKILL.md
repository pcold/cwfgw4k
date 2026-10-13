---
name: debug-issue
description: Systematically debug issues using graph-powered code navigation
---

## Debug Issue

Use the knowledge graph to systematically trace and debug issues.

### Steps

1. Use `semantic_search_nodes_tool` to find code related to the issue.
2. Use `query_graph_tool` with `callers_of` and `callees_of` to trace call chains.
3. Use `list_flows_tool` to find candidate execution paths through the suspected
   area, then `get_flow_tool(flow_name=<name>)` for the full call path with
   source. If nothing named matches, `traverse_graph_tool(query=<symptom or
   symbol>)` does a free-form BFS/DFS from the best-matching node instead.
4. Run `detect_changes_tool` to check if recent changes caused the issue.
5. Use `get_impact_radius_tool` on suspected files to see what else is affected.

### Tips

- Check both callers and callees to understand the full context.
- Look at affected flows to find the entry point that triggers the bug.
- Recent changes are the most common source of new issues.

## Token Efficiency Rules
- ALWAYS start with `get_minimal_context_tool(task="<your task>")` before any
  other graph tool.
- Pass `detail_level="minimal"` on the tools that accept it (`detect_changes_tool`,
  `get_review_context_tool`, `query_graph_tool`, `get_impact_radius_tool`,
  `semantic_search_nodes_tool`, `get_architecture_overview_tool`,
  `list_communities_tool`, `list_flows_tool`). Most other graph tools don't take
  that parameter, and passing it fails schema validation — check the tool's
  schema first if unsure. Only escalate to `"standard"` when minimal is
  insufficient.
- Target: complete any review/debug/refactor task in ≤5 tool calls and ≤800 total output tokens.
