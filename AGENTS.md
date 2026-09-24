# AGENTS.md

This repository's agent instructions live in [`CLAUDE.md`](CLAUDE.md) - they apply to any AI coding agent
(Claude Code, Codex, Cursor, etc.). Reusable task playbooks are in [`.claude/skills/`](.claude/skills).

Quick summary: build with `mvn clean verify`; keep controller -> service -> repository layering; row-lock seats
(ordered by id) before mutating them; publish notifications as after-commit async events; add a test for every behaviour change.
