---
name: commit
description: Commit all open changes and push to remote
disable-model-invocation: true
allowed-tools: Bash
---

Commit all open changes and push them to the remote repository.

## Steps

1. Run `git status` to see all changed and untracked files.
2. Run `git diff` to understand the concrete changes.
3. Stage ALL changed and new files (`git add -A`).
4. Write a concise, descriptive commit message in German that summarizes the changes. Format: `<type>: <description>` (e.g. `fix:`, `feat:`, `refactor:`, `build:`, `docs:`).
5. Commit with the message (append Co-Authored-By header).
6. Push to remote (`git push`).
7. Show the result (commit hash, pushed changes).

## Rules

- No interactive git commands (`-i` flag).
- ALWAYS pass commit message via HEREDOC.
- Never force push (`--force`).
- If push fails, report the error and do NOT automatically force push.
- If there are no changes, report that and do nothing.
