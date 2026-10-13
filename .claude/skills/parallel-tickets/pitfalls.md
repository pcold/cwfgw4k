# Coordinator pitfalls

Signals the watch loop and the coordinator's own checks have misread. Linked from Phase 5 of
[`SKILL.md`](SKILL.md).

## Watch loop and CI

- After writing or editing the monitor's branch filter, run it against a PR known to be open and
  confirm a hit. A filter that matches nothing polls healthily forever.
- `ps` cannot see a `Monitor`'s script. Check `/tasks` or wait for an event before arming a replacement;
  a duplicate shows up as doubled notifications and is one `TaskStop` away.
- A running check has `conclusion: ""`, which jq treats as truthy. Normalize before classifying:
  `map(if ((.conclusion // "") == "") then (.status // .state // "PENDING") else .conclusion end)`.
- Never key a watcher on a PR's `updatedAt`: a finished check or a posted review does not move it.
  `reviewDecision: ""` is truthy in jq too.
- The one gating check is `gradle check`, in the `Check` workflow. On a PR that changes only `.md`
  files it passes in seconds having run nothing, by design.
- `Automated PR review` red at about an hour with a 401 is its job timeout, not the diff:
  `gh run rerun <runId> --failed`.
- A PR editing `.github/workflows/claude-review.yml` goes green with no verdict, because the workflow
  does not review a change to itself. Review that PR by hand.
- A PR that adds a build gate does not gate PRs already open. Once it merges, merge `main` into each
  in-flight branch and re-gate it.
- Right after a merge, every other open PR shows "Checking for the ability to merge" with the button
  greyed out while GitHub rebuilds its test merge. `mergeable: null` is that state, not a conflict.
- Each merge to `main` starts a Deploy run that waits for approval and holds the deploy queue. That is
  the owner's to approve or reject; never approve one, and say when one is waiting.
- After resuming an agent whose PR is open, push its `PR_BODY.md` with `gh pr edit <n> --body-file
  PR_BODY.md`; GitHub's copy does not follow the file.
- A CLEAN or 0-gating verdict means the PR is ready. Report non-gating findings and stop; they get no
  fix round trip.

## Agents, clones and the board

- `completed` can mean suspended. Before relaunching an agent or editing its tree, check ListAgents,
  `pgrep -fl <worktree>` and the transcript's mtime, and `SendMessage` a live agent rather than spawning a rival.
- A `git worktree remove` refusal is evidence the agent is still working, so never `--force` it. After a
  late hand-back, compare the branch head with `gh pr view <n> --json headRefOid`.
- An agent that died on an API error may have finished. `ls` its worktree and output files before
  re-running, and hand over the files instead of re-forking the work.
- `tasks/<id>.output` for an agent is its full JSONL transcript. Never tail it, and its mtime is not
  liveness: read the worktree's commits and status.
- Verify a live agent's fence claim with `git -C <worktree> status --porcelain` in each worktree. Its
  commits lag its real footprint.
- Another session may hold the main clone on its own branch. Don't check out or pull there routinely,
  check `git branch --show-current` before committing, and `gh pr view <n> --json headRefName` before
  triaging a PR as yours.
- Build the graph at the wave's base with `main` checked out. Never park the clone on another commit:
  `head_matches_build` compares against the clone's HEAD and then reports a stale graph as fresh.
- Under squash merge, `git branch --merged` never lists a merged branch. Check `gh pr view <n> --json
  state,mergedAt,mergeCommit`, then that the merge commit is an ancestor of `main`, then diff only the PR's files.
- Unattended, escalate with a PR comment and keep going. Park a ticket that can't proceed and pick up
  another. A ticket whose body holds an open design question is a pause, not a decide-it-yourself.
- Before filing from an agent's report, search Linear for it, re-read the claim against the code,
  and re-check any absence claim. Check each member of a grouping, and list a parent's children before
  decomposing it.
