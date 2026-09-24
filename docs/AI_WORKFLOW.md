# AI workflow

> Edit this file so it reflects exactly how *you* worked - the reviewers will ask about it in the video.

## Tools
Claude (chat) generated the initial implementation from the assignment text; `CLAUDE.md` / `AGENTS.md` and the skills in
`.claude/skills/` capture the conventions the agent was asked to follow and that any future AI session should follow.

## Process
1. **Requirement analysis (human + AI)** - turned the open-ended brief into scoped entities, APIs and edge cases
   (see README section 9 "Assumptions"). Human decisions: pessimistic row locking on seats, hold-as-pending-booking model,
   price snapshot per show seat, mock payment gateway port, async after-commit notifications.
2. **Scaffold** - AI generated pom, entities, repositories, DTOs, services, controllers, security config, seeder.
3. **Invariants first** - before tests, the locking/ordering rules were written down (CLAUDE.md "Non-negotiable invariants").
4. **Tests** - AI wrote unit tests for pure logic (pricing, refunds, discounts), integration tests for each flow and a
   latch-based concurrency suite.
5. **Human review** - read every service method for lock order and transaction boundaries, ran `mvn clean verify`,
   inspected failures, tried the API by hand with the curl walkthrough.

## What the human verified / changed
- [ ] Ran the test-suite locally and fixed anything environment specific
- [ ] Reviewed lock ordering and `noRollbackFor` choices
- [ ] Exercised the curl walkthrough against a running instance
- [ ] (add your own notes: prompts you iterated on, bugs the AI introduced and how you caught them)

## Prompts (raw)
Keep the raw prompts you used in `docs/prompts/` and commit them with the code.
